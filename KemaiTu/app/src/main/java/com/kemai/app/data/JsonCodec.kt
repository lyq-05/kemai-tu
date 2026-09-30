package com.kemai.app.data

import com.kemai.app.model.AppData
import com.kemai.app.model.AppSettings
import com.kemai.app.model.CustomEntry
import com.kemai.app.model.Customer
import com.kemai.app.model.FontScale
import com.kemai.app.model.Product
import com.kemai.app.model.ReminderFrequency
import com.kemai.app.model.ReminderSettings
import com.kemai.app.model.ThemeMode
import com.kemai.app.model.VisitItem
import com.kemai.app.model.VisitList
import com.kemai.app.model.VisitRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * 数据编解码（org.json，Android 平台自带，无第三方依赖）。
 *
 * 容错原则：任何一条记录解析失败都只跳过该条，绝不因为局部脏数据丢掉整份数据。
 */
object JsonCodec {

    const val SCHEMA_VERSION = 1

    // ---------------- 编码 ----------------

    fun encode(data: AppData): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("app", "客脉图")
        root.put("exportedAt", OffsetDateTime.now().toString())

        root.put(
            "settings",
            JSONObject().apply {
                put("theme", data.settings.theme.name)
                put("fontScale", data.settings.fontScale.name)
                put("layoutMode", data.settings.layoutMode.name)
                put("colorMode", data.settings.colorMode.name)
                put("language", data.settings.language.name)
                put("guidePromptDone", data.settings.guidePromptDone)
                val fl = data.settings.fieldLabels
                put(
                    "fieldLabels",
                    JSONObject().apply {
                        put("name", fl.name)
                        put("cardDate", fl.cardDate)
                        put("product", fl.product)
                    }
                )
                val r = data.settings.reminder
                put(
                    "reminder",
                    JSONObject().apply {
                        put("enabled", r.enabled)
                        put("notifyHour", r.notifyHour)
                        put("notifyDayOfWeek", r.notifyDayOfWeek)
                        put("newCustomerMonths", r.newCustomerMonths)
                        put("frequency", r.frequency.name)
                        put("bannerDismissedCycle", r.bannerDismissedCycle ?: JSONObject.NULL)
                    }
                )
            }
        )

        val products = JSONArray()
        for (p in data.products) {
            products.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("order", p.order)
                    put("archived", p.archived)
                }
            )
        }
        root.put("products", products)

        val customers = JSONArray()
        for (c in data.customers) {
            customers.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("firstCardDate", c.firstCardDate ?: JSONObject.NULL)
                    put("productIds", JSONArray(c.productIds))
                    put("note", c.note)
                    val entries = JSONArray()
                    for (e in c.customEntries) {
                        entries.put(
                            JSONObject().apply {
                                put("id", e.id)
                                put("label", e.label)
                                put("value", e.value)
                            }
                        )
                    }
                    put("customEntries", entries)
                    put("referrerId", c.referrerId ?: JSONObject.NULL)
                    put("collapsed", c.collapsed)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                }
            )
        }
        root.put("customers", customers)

        // ---------- 拜访清单 ----------
        val lists = JSONArray()
        for (l in data.visitLists) {
            lists.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("builtin", l.builtin)
                    put("order", l.order)
                    put("excludedIds", JSONArray(l.excludedIds))
                    put("manualIds", JSONArray(l.manualIds))
                    put("cycleStart", l.cycleStart ?: JSONObject.NULL)
                    put("createdAt", l.createdAt)
                }
            )
        }
        root.put("visitLists", lists)

        val items = JSONArray()
        for (i in data.visitItems) {
            items.put(
                JSONObject().apply {
                    put("id", i.id)
                    put("listId", i.listId)
                    put("customerId", i.customerId ?: JSONObject.NULL)
                    put("customerName", i.customerName)
                    put("plannedDate", i.plannedDate ?: JSONObject.NULL)
                    put("content", i.content)
                    put("done", i.done)
                    put("completedDate", i.completedDate ?: JSONObject.NULL)
                    put("order", i.order)
                    put("createdAt", i.createdAt)
                }
            )
        }
        root.put("visitItems", items)

        val records = JSONArray()
        for (r in data.visitRecords) {
            records.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("customerId", r.customerId ?: JSONObject.NULL)
                    put("customerName", r.customerName)
                    put("itemId", r.itemId ?: JSONObject.NULL)
                    put("plannedDate", r.plannedDate ?: JSONObject.NULL)
                    put("completedDate", r.completedDate)
                    put("content", r.content)
                    put("createdAt", r.createdAt)
                }
            )
        }
        root.put("visitRecords", records)

        return root.toString(2)
    }

    // ---------------- 解码 ----------------

    /** @throws IllegalArgumentException 当内容根本不是客脉图的备份文件时 */
    fun decode(text: String): AppData {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("文件内容不是有效的 JSON")
        }
        if (!root.has("customers") && !root.has("products")) {
            throw IllegalArgumentException("这不是「客脉图」的备份文件")
        }

        val settingsObj = root.optJSONObject("settings")
        val reminderObj = settingsObj?.optJSONObject("reminder")
        val labelsObj = settingsObj?.optJSONObject("fieldLabels")
        val defaultLabels = com.kemai.app.model.FieldLabels()
        val settings = AppSettings(
            theme = runCatching { ThemeMode.valueOf(settingsObj?.optString("theme") ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            fontScale = FontScale.fromName(settingsObj?.optString("fontScale")),
            layoutMode = com.kemai.app.model.LayoutMode.fromName(settingsObj?.optString("layoutMode")),
            colorMode = com.kemai.app.model.ColorMode.fromName(settingsObj?.optString("colorMode")),
            language = com.kemai.app.model.AppLanguage.fromName(settingsObj?.optString("language")),
            fieldLabels = com.kemai.app.model.FieldLabels(
                name = labelsObj?.optString("name")?.takeIf { it.isNotBlank() } ?: defaultLabels.name,
                cardDate = labelsObj?.optString("cardDate")?.takeIf { it.isNotBlank() } ?: defaultLabels.cardDate,
                product = labelsObj?.optString("product")?.takeIf { it.isNotBlank() } ?: defaultLabels.product,
            ),
            guidePromptDone = settingsObj?.optBoolean("guidePromptDone", false) ?: false,
            reminder = ReminderSettings(
                enabled = reminderObj?.optBoolean("enabled", true) ?: true,
                notifyHour = (reminderObj?.optInt("notifyHour", 9) ?: 9).coerceIn(0, 23),
                notifyDayOfWeek = (reminderObj?.optInt("notifyDayOfWeek", 1) ?: 1).coerceIn(1, 7),
                newCustomerMonths = (reminderObj?.optInt("newCustomerMonths", 6) ?: 6)
                    .let { if (it in listOf(3, 6, 12)) it else 6 },
                frequency = ReminderFrequency.fromName(reminderObj?.optString("frequency")),
                bannerDismissedCycle = reminderObj?.optString("bannerDismissedCycle")
                    ?.takeIf { it.isNotBlank() && it != "null" },
            ),
        )

        val products = ArrayList<Product>()
        val productsArr = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until productsArr.length()) {
            val o = productsArr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { continue }
            products += Product(
                id = id,
                name = o.optString("name"),
                order = o.optInt("order", i),
                archived = o.optBoolean("archived", false),
            )
        }

        val customers = ArrayList<Customer>()
        val customersArr = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until customersArr.length()) {
            val o = customersArr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { continue }

            val productIds = ArrayList<String>()
            o.optJSONArray("productIds")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val v = arr.optString(j)
                    if (v.isNotBlank()) productIds += v
                }
            }

            val entries = ArrayList<CustomEntry>()
            o.optJSONArray("customEntries")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val e = arr.optJSONObject(j) ?: continue
                    entries += CustomEntry(
                        id = e.optString("id").ifBlank { "e_$i$j" },
                        label = e.optString("label"),
                        value = e.optString("value"),
                    )
                }
            }

            customers += Customer(
                id = id,
                name = o.optString("name"),
                firstCardDate = o.optString("firstCardDate").takeIf { it.isNotBlank() && it != "null" },
                productIds = productIds,
                note = o.optString("note"),
                customEntries = entries,
                referrerId = o.optString("referrerId").takeIf { it.isNotBlank() && it != "null" },
                collapsed = o.optBoolean("collapsed", false),
                createdAt = o.optLong("createdAt", i.toLong()),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }

        // 清理悬空引用，避免导入外部文件后出现看不见的节点
        val idSet = customers.map { it.id }.toHashSet()
        val cleaned = customers.map { c ->
            if (c.referrerId != null && c.referrerId !in idSet) c.copy(referrerId = null) else c
        }
        val productIdSet = products.map { it.id }.toHashSet()
        val cleaned2 = cleaned.map { c ->
            val kept = c.productIds.filter { it in productIdSet }
            if (kept.size != c.productIds.size) c.copy(productIds = kept) else c
        }

        // ---------- 拜访清单 ----------
        val visitLists = ArrayList<VisitList>()
        root.optJSONArray("visitLists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { continue }
                val excluded = ArrayList<String>()
                o.optJSONArray("excludedIds")?.let { ex ->
                    for (j in 0 until ex.length()) ex.optString(j).takeIf { it.isNotBlank() }?.let { excluded += it }
                }
                val manual = ArrayList<String>()
                o.optJSONArray("manualIds")?.let { mn ->
                    for (j in 0 until mn.length()) mn.optString(j).takeIf { it.isNotBlank() }?.let { manual += it }
                }
                visitLists += VisitList(
                    id = id,
                    name = o.optString("name"),
                    builtin = o.optBoolean("builtin", false),
                    order = o.optInt("order", i),
                    excludedIds = excluded,
                    manualIds = manual,
                    cycleStart = o.optString("cycleStart").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = o.optLong("createdAt", i.toLong()),
                )
            }
        }

        val visitItems = ArrayList<VisitItem>()
        root.optJSONArray("visitItems")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { continue }
                val listId = o.optString("listId").ifBlank { continue }
                visitItems += VisitItem(
                    id = id,
                    listId = listId,
                    customerId = o.optString("customerId").takeIf { it.isNotBlank() && it != "null" },
                    customerName = o.optString("customerName"),
                    plannedDate = o.optString("plannedDate").takeIf { it.isNotBlank() && it != "null" },
                    content = o.optString("content"),
                    done = o.optBoolean("done", false),
                    completedDate = o.optString("completedDate").takeIf { it.isNotBlank() && it != "null" },
                    order = o.optInt("order", i),
                    createdAt = o.optLong("createdAt", i.toLong()),
                )
            }
        }

        val visitRecords = ArrayList<VisitRecord>()
        root.optJSONArray("visitRecords")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { continue }
                visitRecords += VisitRecord(
                    id = id,
                    customerId = o.optString("customerId").takeIf { it.isNotBlank() && it != "null" },
                    customerName = o.optString("customerName"),
                    itemId = o.optString("itemId").takeIf { it.isNotBlank() && it != "null" },
                    plannedDate = o.optString("plannedDate").takeIf { it.isNotBlank() && it != "null" },
                    completedDate = o.optString("completedDate"),
                    content = o.optString("content"),
                    createdAt = o.optLong("createdAt", i.toLong()),
                )
            }
        }

        // 丢掉指向不存在清单的孤儿条目，避免导入外部文件后出现看不见的数据
        val listIdSet = visitLists.map { it.id }.toHashSet()
        val cleanItems = visitItems.filter { it.listId in listIdSet }

        return AppData(
            customers = cleaned2,
            products = products.sortedBy { it.order },
            visitLists = visitLists.sortedBy { it.order },
            visitItems = cleanItems,
            visitRecords = visitRecords,
            settings = settings,
        )
    }
}
