package com.kemai.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kemai.app.model.AppData
import com.kemai.app.model.AppSettings
import com.kemai.app.model.Customer
import com.kemai.app.model.FontScale
import com.kemai.app.model.Product
import com.kemai.app.model.ReminderSettings
import com.kemai.app.model.ThemeMode
import com.kemai.app.model.VisitItem
import com.kemai.app.model.VisitList
import com.kemai.app.model.VisitRecord
import com.kemai.app.model.BUILTIN_LIST_ID
import com.kemai.app.model.BUILTIN_LIST_NAME
import com.kemai.app.model.autoListCustomers
import com.kemai.app.model.builtinList
import com.kemai.app.model.descendantsOf
import com.kemai.app.model.newId
import com.kemai.app.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 删除有下级的客户时的处理方式 */
enum class DeleteMode {
    /** 下级上提为根客户（推荐） */
    PROMOTE_CHILDREN,

    /** 连同整棵子树一起删除 */
    DELETE_SUBTREE,
}

/**
 * 单一数据源。所有修改都经过这里，改完自动防抖落盘（需求：任何编辑 0.3 秒内保存）。
 * 数据只写在本机应用私有目录，不联网。
 */
class AppRepository(private val context: Context) {

    var data by mutableStateOf(AppData())
        private set

    /** 上一次操作产生的提示，供界面显示 */
    var toast by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val dataFile: File get() = File(context.filesDir, "kemai_data.json")
    private val rollbackDir: File get() = File(context.filesDir, "rollback")

    // ---------------- 读写 ----------------

    fun load() {
        val f = dataFile
        data = if (f.exists()) {
            runCatching { JsonCodec.decode(f.readText()) }.getOrElse {
                // 主数据损坏：尝试回滚备份，再不行就用空数据（绝不崩溃）
                loadRollback() ?: AppData()
            }
        } else {
            AppData()
        }
        // 保证系统清单存在，并处理「换周」重置
        ensureBuiltinList()
        rolloverIfNeeded()
    }

    private fun loadRollback(): AppData? {
        val f = File(rollbackDir, "rollback.json")
        if (!f.exists()) return null
        return runCatching { JsonCodec.decode(f.readText()) }.getOrNull()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            runCatching { dataFile.writeText(JsonCodec.encode(data)) }
        }
    }

    /** 立即落盘（导出前、退出前调用） */
    fun saveNow() {
        saveJob?.cancel()
        runCatching { dataFile.writeText(JsonCodec.encode(data)) }
    }

    private fun mutate(block: (AppData) -> AppData) {
        data = block(data)
        scheduleSave()
    }

    // ---------------- 客户 ----------------

    fun addCustomer(
        name: String,
        referrerId: String? = null,
        firstCardDate: String? = null,
    ): Customer {
        val c = Customer(
            id = newId("c"),
            name = name.trim(),
            referrerId = referrerId,
            firstCardDate = firstCardDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        mutate { it.copy(customers = it.customers + c) }
        syncAutoList()
        return c
    }

    fun updateCustomer(updated: Customer) {
        mutate { d ->
            d.copy(
                customers = d.customers.map {
                    if (it.id == updated.id) updated.copy(updatedAt = System.currentTimeMillis()) else it
                }
            )
        }
    }

    /** 是否会成环 */
    fun canSetReferrer(customerId: String, referrerId: String?): Boolean {
        if (referrerId == null) return true
        if (referrerId == customerId) return false
        return data.descendantsOf(customerId).none { it.id == referrerId }
    }

    fun setReferrer(customerId: String, referrerId: String?) {
        if (!canSetReferrer(customerId, referrerId)) {
            toast = "不能把客户挂到自己的下级下面"
            return
        }
        mutate { d ->
            d.copy(
                customers = d.customers.map {
                    if (it.id == customerId) it.copy(referrerId = referrerId, updatedAt = System.currentTimeMillis()) else it
                }
            )
        }
    }

    fun toggleCollapse(customerId: String) {
        mutate { d ->
            d.copy(
                customers = d.customers.map {
                    if (it.id == customerId) it.copy(collapsed = !it.collapsed) else it
                }
            )
        }
    }

    fun setAllCollapsed(collapsed: Boolean) {
        mutate { d -> d.copy(customers = d.customers.map { it.copy(collapsed = collapsed) }) }
    }

    fun deleteCustomer(customerId: String, mode: DeleteMode) {
        mutate { d ->
            when (mode) {
                DeleteMode.DELETE_SUBTREE -> {
                    val doomed = (d.descendantsOf(customerId).map { it.id } + customerId).toHashSet()
                    d.copy(customers = d.customers.filter { it.id !in doomed })
                }
                DeleteMode.PROMOTE_CHILDREN -> {
                    // 直接下级上提为根客户；其余子孙跟着自己的父节点走
                    val kept = d.customers
                        .filter { it.id != customerId }
                        .map { if (it.referrerId == customerId) it.copy(referrerId = null) else it }
                    d.copy(customers = kept)
                }
            }
        }
        // 清掉指向已删除客户的清单条目
        syncAutoList()
    }

    // ---------------- 产品 ----------------

    fun addProduct(name: String): Product? {
        val n = name.trim()
        if (n.isEmpty()) return null
        data.products.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return it }
        val p = Product(
            id = newId("p"),
            name = n,
            order = (data.products.maxOfOrNull { it.order } ?: -1) + 1,
        )
        mutate { it.copy(products = it.products + p) }
        return p
    }

    fun renameProduct(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { d -> d.copy(products = d.products.map { if (it.id == id) it.copy(name = n) else it }) }
    }

    fun setProductArchived(id: String, archived: Boolean) {
        mutate { d -> d.copy(products = d.products.map { if (it.id == id) it.copy(archived = archived) else it }) }
    }

    /**
     * 删除产品：把它从所有客户身上摘掉，同时把名称作为一条自定义条目留在客户身上，
     * 保证「已经记录过的数据不会丢」。
     */
    fun deleteProduct(id: String) {
        val p = data.products.firstOrNull { it.id == id } ?: return
        mutate { d ->
            val customers = d.customers.map { c ->
                if (id !in c.productIds) c
                else {
                    val entries = c.customEntries + com.kemai.app.model.CustomEntry(
                        id = newId("e"),
                        label = "曾用产品",
                        value = p.name,
                    )
                    c.copy(productIds = c.productIds - id, customEntries = entries)
                }
            }
            d.copy(
                products = d.products.filter { it.id != id },
                customers = customers,
            )
        }
    }

    fun reorderProducts(ordered: List<Product>) {
        mutate { d ->
            d.copy(products = ordered.mapIndexed { i, p -> p.copy(order = i) })
        }
    }

    fun productUsageCount(productId: String): Int =
        data.customers.count { productId in it.productIds }

    /** 历史上用过的所有自定义条目标签，按使用频次排序（用于输入补全） */
    fun knownLabels(): List<String> =
        data.customers
            .flatMap { it.customEntries }
            .map { it.label.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    // ---------------- 设置 ----------------

    fun setTheme(mode: ThemeMode) {
        mutate { it.copy(settings = it.settings.copy(theme = mode)) }
    }

    fun setFontScale(scale: FontScale) {
        mutate { it.copy(settings = it.settings.copy(fontScale = scale)) }
    }

    /** 改三个内置字段的显示名称 */
    fun setFieldLabel(which: Int, value: String) {
        val v = value.trim().ifEmpty { return }
        mutate { d ->
            val fl = d.settings.fieldLabels
            val next = when (which) {
                0 -> fl.copy(name = v)
                1 -> fl.copy(cardDate = v)
                else -> fl.copy(product = v)
            }
            d.copy(settings = d.settings.copy(fieldLabels = next))
        }
    }

    /** 竖向脉络 / 横向脉络 */
    fun setLayoutMode(mode: com.kemai.app.model.LayoutMode) {
        mutate { it.copy(settings = it.settings.copy(layoutMode = mode)) }
    }

    /** 按脉络深度分色 / 按客户家族分色 */
    fun setColorMode(mode: com.kemai.app.model.ColorMode) {
        mutate { it.copy(settings = it.settings.copy(colorMode = mode)) }
    }

    /** 界面语言（简体中文 / English） */
    fun setLanguage(lang: com.kemai.app.model.AppLanguage) {
        mutate { it.copy(settings = it.settings.copy(language = lang)) }
    }

    /** 记下"首次进入的指南提示已经给过了"，不再打扰 */
    fun markGuidePromptDone() {
        if (data.settings.guidePromptDone) return
        mutate { it.copy(settings = it.settings.copy(guidePromptDone = true)) }
    }

    fun settings(): AppSettings = data.settings

    // ---------------- 拜访清单（提醒版） ----------------

    /**
     * 首次运行时创建系统清单「新客户每周拜访」。
     * 注意 cycleStart 故意留空 —— 这样紧随其后的 rolloverIfNeeded() 会认为
     * 「还没建立过周期」，从而把名单真正填上。
     */
    fun ensureBuiltinList() {
        if (data.visitLists.any { it.builtin }) return
        mutate { d ->
            d.copy(
                visitLists = listOf(
                    VisitList(
                        id = BUILTIN_LIST_ID,
                        name = BUILTIN_LIST_NAME,
                        builtin = true,
                        order = -1,
                        cycleStart = null,
                    )
                ) + d.visitLists
            )
        }
    }

    /**
     * 换周了就重置「本周已拜访」状态，然后把名单同步一次。
     * 每次打开应用调用一次即可。
     */
    fun rolloverIfNeeded() {
        val builtin = data.builtinList()
        if (builtin == null) {
            ensureBuiltinList()
            syncAutoList()
            return
        }
        val week = Dates.currentWeekStartIso()
        when {
            builtin.cycleStart == null -> {
                mutate { d ->
                    d.copy(
                        visitLists = d.visitLists.map {
                            if (it.id == builtin.id) it.copy(cycleStart = week) else it
                        }
                    )
                }
            }
            builtin.cycleStart != week -> {
                // 换周：已拜访状态清零，重新开始新一轮
                mutate { d ->
                    d.copy(
                        visitLists = d.visitLists.map {
                            if (it.id == builtin.id) it.copy(cycleStart = week) else it
                        },
                        visitItems = d.visitItems.map {
                            if (it.listId == BUILTIN_LIST_ID && it.done) {
                                it.copy(done = false, completedDate = null)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }
        syncAutoList()
    }

    /**
     * 让自动清单的成员与「最近 N 个月办卡 + 手动加入」保持一致。
     * 已有条目的计划时间、回访内容、已拜访状态都会保留；指向已删除客户的条目会被清掉。
     */
    fun syncAutoList() {
        val builtin = data.builtinList() ?: return
        mutate { d ->
            val months = d.settings.reminder.newCustomerMonths
            val autoIds = d.autoListCustomers(months, builtin.excludedIds).map { it.id }
            val manualAlive = builtin.manualIds.filter { id -> d.customers.any { it.id == id } }
            val shouldHave = (autoIds + manualAlive).distinct()

            val existing = d.visitItems.filter { it.listId == BUILTIN_LIST_ID }
            val byCustomer = existing.filter { it.customerId != null }.associateBy { it.customerId!! }

            val rebuilt = shouldHave.mapNotNull { cid ->
                val c = d.customers.firstOrNull { it.id == cid } ?: return@mapNotNull null
                val old = byCustomer[cid]
                if (old != null) old.copy(customerName = c.name)
                else VisitItem(listId = BUILTIN_LIST_ID, customerId = cid, customerName = c.name)
            }
            val others = d.visitItems.filter { it.listId != BUILTIN_LIST_ID }
            d.copy(visitItems = others + rebuilt)
        }
    }

    fun addVisitList(name: String): VisitList? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val l = VisitList(
            id = newId("vl"),
            name = n,
            order = (data.visitLists.maxOfOrNull { it.order } ?: 0) + 1,
        )
        mutate { it.copy(visitLists = it.visitLists + l) }
        return l
    }

    fun renameVisitList(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { d ->
            d.copy(visitLists = d.visitLists.map { if (it.id == id) it.copy(name = n) else it })
        }
    }

    /** 删除自建清单（系统清单不可删） */
    fun deleteVisitList(id: String) {
        mutate { d ->
            val l = d.visitLists.firstOrNull { it.id == id }
            if (l == null || l.builtin) d
            else d.copy(
                visitLists = d.visitLists.filter { it.id != id },
                visitItems = d.visitItems.filter { it.listId != id },
            )
        }
    }

    fun reorderVisitLists(ordered: List<VisitList>) {
        mutate { d -> d.copy(visitLists = ordered.mapIndexed { i, l -> l.copy(order = i) }) }
    }

    fun addVisitItem(
        listId: String,
        customerId: String?,
        customerName: String,
        plannedDate: String?,
        content: String,
    ) {
        val item = VisitItem(
            listId = listId,
            customerId = customerId,
            customerName = customerName.trim(),
            plannedDate = plannedDate,
            content = content,
        )
        mutate { it.copy(visitItems = it.visitItems + item) }
    }

    fun updateVisitItem(item: VisitItem) {
        mutate { d -> d.copy(visitItems = d.visitItems.map { if (it.id == item.id) item else it }) }
    }

    fun deleteVisitItem(id: String) {
        mutate { d -> d.copy(visitItems = d.visitItems.filter { it.id != id }) }
    }

    /** 标记已拜访：写入实际完成日期与内容，并留一条拜访历史 */
    fun markVisited(itemId: String, completedDate: String, content: String) {
        mutate { d ->
            val item = d.visitItems.firstOrNull { it.id == itemId } ?: return@mutate d
            val updated = item.copy(done = true, completedDate = completedDate, content = content)
            val record = VisitRecord(
                customerId = item.customerId,
                customerName = item.customerName,
                itemId = item.id,
                plannedDate = item.plannedDate,
                completedDate = completedDate,
                content = content,
            )
            d.copy(
                visitItems = d.visitItems.map { if (it.id == itemId) updated else it },
                visitRecords = d.visitRecords + record,
            )
        }
    }

    /** 取消「已拜访」，同时撤回对应的历史记录 */
    fun unmarkVisited(itemId: String) {
        mutate { d ->
            d.copy(
                visitItems = d.visitItems.map {
                    if (it.id == itemId) it.copy(done = false, completedDate = null) else it
                },
                visitRecords = d.visitRecords.filter { it.itemId != itemId },
            )
        }
    }

    /** 从自动清单移出（出差、暂停跟进等） */
    fun excludeFromAuto(customerId: String) {
        mutate { d ->
            d.copy(
                visitLists = d.visitLists.map {
                    if (it.builtin) it.copy(
                        excludedIds = (it.excludedIds + customerId).distinct(),
                        manualIds = it.manualIds - customerId,
                    ) else it
                },
                visitItems = d.visitItems.filterNot {
                    it.listId == BUILTIN_LIST_ID && it.customerId == customerId
                },
            )
        }
    }

    /** 把客户加进自动清单（即使不符合办卡时间条件） */
    fun includeToAuto(customerId: String) {
        mutate { d ->
            val c = d.customers.firstOrNull { it.id == customerId } ?: return@mutate d
            val exists = d.visitItems.any {
                it.listId == BUILTIN_LIST_ID && it.customerId == customerId
            }
            d.copy(
                visitLists = d.visitLists.map {
                    if (it.builtin) it.copy(
                        manualIds = (it.manualIds + customerId).distinct(),
                        excludedIds = it.excludedIds - customerId,
                    ) else it
                },
                visitItems = if (exists) d.visitItems
                else d.visitItems + VisitItem(
                    listId = BUILTIN_LIST_ID,
                    customerId = customerId,
                    customerName = c.name,
                ),
            )
        }
    }

    /** 关掉本周的应用内横幅，避免反复打扰 */
    fun dismissBannerForThisCycle() {
        mutate { d ->
            d.copy(
                settings = d.settings.copy(
                    reminder = d.settings.reminder.copy(
                        bannerDismissedCycle = Dates.currentWeekStartIso()
                    )
                )
            )
        }
    }

    fun setReminder(patch: (ReminderSettings) -> ReminderSettings) {
        mutate { d -> d.copy(settings = d.settings.copy(reminder = patch(d.settings.reminder))) }
    }

    // ---------------- 备份 ----------------

    fun exportJson(): String {
        saveNow()
        return JsonCodec.encode(data)
    }

    /** 导入前先把当前数据存一份回滚备份，保证「导入失败不破坏现有数据」 */
    fun backupForRollback() {
        runCatching {
            rollbackDir.mkdirs()
            File(rollbackDir, "rollback.json").writeText(JsonCodec.encode(data))
        }
    }

    /** @return 导入是否成功 */
    fun importJson(text: String): Boolean {
        val parsed = runCatching { JsonCodec.decode(text) }.getOrElse {
            toast = "导入失败：${it.message ?: "文件格式不正确"}"
            return false
        }
        backupForRollback()
        data = parsed
        saveNow()
        toast = "已导入 ${parsed.customers.size} 位客户"
        return true
    }

    fun clearAll() {
        backupForRollback()
        data = AppData(settings = data.settings)
        saveNow()
    }
}
