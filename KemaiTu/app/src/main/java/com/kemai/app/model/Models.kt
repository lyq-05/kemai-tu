package com.kemai.app.model

import java.util.UUID

/** 自定义条目：标签 + 内容，每个客户可加任意多条 */
data class CustomEntry(
    val id: String = newId("e"),
    val label: String = "",
    val value: String = ""
)

/** 客户：导图上的一个节点 */
data class Customer(
    val id: String = newId("c"),
    val name: String = "",
    /** 首次办卡时间，格式 yyyy-MM-dd，可空 */
    val firstCardDate: String? = null,
    /** 使用过的产品 id 列表 */
    val productIds: List<String> = emptyList(),
    /** 备注（长文本，不用起标签） */
    val note: String = "",
    /** 自定义条目，不设上限 */
    val customEntries: List<CustomEntry> = emptyList(),
    /** 介绍人 id；null 表示根客户 */
    val referrerId: String? = null,
    /** 子树是否折叠 */
    val collapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 产品：来自可维护的产品库 */
data class Product(
    val id: String = newId("p"),
    val name: String = "",
    val order: Int = 0,
    /** 停用：不再出现在选择列表，但历史数据保留 */
    val archived: Boolean = false
)

// ==================== 拜访清单（提醒版） ====================

/** 拜访清单：一张系统自动生成的「新客户每周拜访」+ 任意多张自建清单 */
data class VisitList(
    val id: String = newId("vl"),
    val name: String = "",
    /** true = 系统自动生成的「新客户每周拜访」，不可删除 */
    val builtin: Boolean = false,
    val order: Int = 0,
    /** 手动移出自动清单的客户（出差、客户明确说别来了等情况） */
    val excludedIds: List<String> = emptyList(),
    /** 手动加进自动清单的客户（即使不符合办卡时间条件） */
    val manualIds: List<String> = emptyList(),
    /** 当前周期的起始日（周一，yyyy-MM-dd）；换周时据此重置 */
    val cycleStart: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** 清单里的一项待拜访 */
data class VisitItem(
    val id: String = newId("vi"),
    val listId: String,
    /** 关联的导图客户；手输的客户为 null */
    val customerId: String? = null,
    val customerName: String = "",
    /** 计划回访时间 */
    val plannedDate: String? = null,
    /** 回访内容 */
    val content: String = "",
    val done: Boolean = false,
    /** 实际完成日期 */
    val completedDate: String? = null,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 拜访历史：标记已拜访时留档，客户详情里可查 */
data class VisitRecord(
    val id: String = newId("vr"),
    val customerId: String? = null,
    val customerName: String = "",
    /** 对应的清单项 id，取消「已拜访」时据此撤回这条记录 */
    val itemId: String? = null,
    val plannedDate: String? = null,
    val completedDate: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class ReminderFrequency(val label: String) {
    DAILY("每天"),
    WEEKLY("每周"),
    BIWEEKLY("每两周");

    companion object {
        fun fromName(n: String?): ReminderFrequency =
            entries.firstOrNull { it.name == n } ?: WEEKLY
    }
}

data class ReminderSettings(
    val enabled: Boolean = true,
    /** 提醒时间（整点，0-23） */
    val notifyHour: Int = 9,
    /** 1 = 周一 … 7 = 周日 */
    val notifyDayOfWeek: Int = 1,
    /** 新客户判定：首次办卡时间在最近几个月内（3 / 6 / 12） */
    val newCustomerMonths: Int = 6,
    val frequency: ReminderFrequency = ReminderFrequency.WEEKLY,
    /** 应用内横幅已在本周期被关掉的标记，避免反复打扰 */
    val bannerDismissedCycle: String? = null
)

// ==================== 主题与字号 ====================

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FontScale(val factor: Float, val label: String) {
    SMALL(0.85f, "小"),
    STANDARD(1.0f, "标准"),
    LARGE(1.15f, "大"),
    XLARGE(1.30f, "特大");

    companion object {
        fun fromName(n: String?): FontScale =
            entries.firstOrNull { it.name == n } ?: STANDARD
    }
}

// ==================== 可自定义的字段名称 ====================

/**
 * 三个内置字段的显示名称。
 * 默认是"客户 / 办卡 / 产品"这套叫法，但允许改成任何行业的说法 ——
 * 比如「学员 / 入学时间 / 报读课程」「房源 / 带看时间 / 意向户型」。
 */
data class FieldLabels(
    val name: String = "客户名称",
    val cardDate: String = "首次办卡时间",
    val product: String = "使用过的产品",
)

/** 脉络方向 */
enum class LayoutMode(val label: String, val desc: String) {
    /** 竖向脉络：上级在上、下级在下，同级左右并排 */
    VERTICAL("竖向脉络", "上级在上、下级在下，同级左右并排"),

    /** 横向脉络：上级在左、下级在右，同级上下并排 */
    HORIZONTAL("横向脉络", "上级在左、下级在右，同级上下并排");

    companion object {
        fun fromName(n: String?): LayoutMode =
            entries.firstOrNull { it.name == n } ?: VERTICAL
    }
}

/** 卡片配色方式 */
enum class ColorMode(val label: String, val desc: String) {
    /** 按脉络深度：第 1 层一个色、第 2 层一个色……越往下越浅，层级一眼看清 */
    DEPTH("按脉络深度", "第 1 层一个色、第 2 层一个色……越往下越浅，层级一眼看清"),

    /** 按客户家族：同一棵子树同一个色相，用来区分"是哪位客户介绍来的" */
    FAMILY("按客户家族", "同一棵子树一个色相，用来区分是哪位客户介绍来的");

    companion object {
        fun fromName(n: String?): ColorMode =
            entries.firstOrNull { it.name == n } ?: DEPTH
    }
}

/** 界面语言 */
enum class AppLanguage(val tag: String, val label: String) {
    ZH("zh-CN", "简体中文"),
    EN("en", "English");

    companion object {
        fun fromName(n: String?): AppLanguage =
            entries.firstOrNull { it.name == n } ?: ZH
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.STANDARD,
    val layoutMode: LayoutMode = LayoutMode.VERTICAL,
    val colorMode: ColorMode = ColorMode.DEPTH,
    val language: AppLanguage = AppLanguage.ZH,
    val fieldLabels: FieldLabels = FieldLabels(),
    val reminder: ReminderSettings = ReminderSettings(),
    /** 首次进入时的操作指南是否已经提示过 */
    val guidePromptDone: Boolean = false,
)

data class AppData(
    val customers: List<Customer> = emptyList(),
    val products: List<Product> = emptyList(),
    val visitLists: List<VisitList> = emptyList(),
    val visitItems: List<VisitItem> = emptyList(),
    val visitRecords: List<VisitRecord> = emptyList(),
    val settings: AppSettings = AppSettings()
)

/** 生成短 id */
fun newId(prefix: String): String =
    prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(10)

// ---------- 便捷扩展 ----------

fun AppData.customerById(id: String?): Customer? =
    if (id == null) null else customers.firstOrNull { it.id == id }

fun AppData.productById(id: String?): Product? =
    if (id == null) null else products.firstOrNull { it.id == id }

/** 视觉上可见的孩子（折叠时不展开） */
fun AppData.childrenOf(id: String): List<Customer> =
    customers.filter { it.referrerId == id }.sortedBy { it.createdAt }

/** 全部子孙（含被折叠的），用于统计数量 */
fun AppData.descendantsOf(id: String): List<Customer> {
    val out = mutableListOf<Customer>()
    val stack = ArrayDeque<String>()
    stack.addLast(id)
    val seen = mutableSetOf(id)
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        for (c in customers.filter { it.referrerId == cur }) {
            if (seen.add(c.id)) {
                out += c
                stack.addLast(c.id)
            }
        }
    }
    return out
}

/**
 * 判断 [candidateParentId] 是否可以作为 [customerId] 的介绍人。
 * 规则：不能是自己，也不能是自己的子孙（否则成环）。
 */
fun AppData.canBeReferrer(customerId: String, candidateParentId: String?): Boolean {
    if (candidateParentId == null) return true
    if (candidateParentId == customerId) return false
    return descendantsOf(customerId).none { it.id == candidateParentId }
}

/** 客户所属的根客户 id（沿介绍人一路上溯） */
fun AppData.rootIdOf(customerId: String): String {
    var cur = customerById(customerId) ?: return customerId
    val guard = mutableSetOf<String>()
    while (true) {
        val ref = cur.referrerId ?: return cur.id
        if (!guard.add(cur.id)) return cur.id // 防御性：数据异常时断开
        cur = customerById(ref) ?: return cur.id
    }
}

/** 从根到该客户的名称路径，如 ["客户A", "客户B"] */
fun AppData.pathTo(customerId: String): List<String> {
    val path = mutableListOf<String>()
    var cur = customerById(customerId) ?: return path
    val guard = mutableSetOf<String>()
    while (true) {
        path.add(0, cur.name)
        val ref = cur.referrerId ?: break
        if (!guard.add(cur.id)) break
        cur = customerById(ref) ?: break
    }
    return path
}

// ==================== 拜访清单便捷扩展 ====================

/** 系统自动清单的固定 id 与名称 */
const val BUILTIN_LIST_ID = "vl_builtin_new_weekly"
const val BUILTIN_LIST_NAME = "新客户每周拜访"

fun AppData.visitListById(id: String?): VisitList? =
    if (id == null) null else visitLists.firstOrNull { it.id == id }

fun AppData.builtinList(): VisitList? = visitLists.firstOrNull { it.builtin }

/** 某张清单的条目：未完成在前、已完成沉底；组内按创建时间 */
fun AppData.itemsOf(listId: String): List<VisitItem> =
    visitItems.filter { it.listId == listId }
        .sortedWith(compareBy({ it.done }, { it.createdAt }))

/**
 * 符合「新客户」条件的客户：首次办卡时间落在最近 months 个月内。
 * 没填办卡时间的无法判断，不纳入。
 */
fun AppData.newCustomers(months: Int): List<Customer> {
    val today = java.time.LocalDate.now()
    val from = today.minusMonths(months.toLong())
    return customers.filter { c ->
        val d = com.kemai.app.util.Dates.parse(c.firstCardDate) ?: return@filter false
        !d.isBefore(from) && !d.isAfter(today)
    }.sortedByDescending { it.firstCardDate }
}

/** 自动清单当前应包含的客户（已排除手动移出的） */
fun AppData.autoListCustomers(months: Int, excludedIds: List<String>): List<Customer> =
    newCustomers(months).filter { it.id !in excludedIds }

/** 全部清单的未完成数量，用于顶部入口角标 */
fun AppData.pendingVisitCount(): Int = visitItems.count { !it.done }

/** 某位客户的拜访历史（新到旧） */
fun AppData.visitRecordsOf(customerId: String): List<VisitRecord> =
    visitRecords.filter { it.customerId == customerId }
        .sortedByDescending { it.completedDate }
