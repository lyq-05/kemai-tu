package com.kemai.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Dates {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun todayIso(): String = LocalDate.now().format(ISO)

    fun parse(iso: String?): LocalDate? =
        if (iso.isNullOrBlank()) null
        else runCatching { LocalDate.parse(iso, ISO) }.getOrNull()

    fun isValid(iso: String?): Boolean = parse(iso) != null

    /** yyyy-MM-dd → 2025年3月12日 */
    fun humanize(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }

    /** yyyy-MM-dd → 3月12日 */
    fun shortHuman(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 距今多少天（负数表示过去） */
    fun daysFromToday(iso: String?): Long? {
        val d = parse(iso) ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
    }

    /** 本周一的日期（ISO）。拜访周期以周一为界 */
    fun currentWeekStartIso(): String {
        val today = LocalDate.now()
        // DayOfWeek: MONDAY=1 … SUNDAY=7
        val back = today.dayOfWeek.value - 1L
        return today.minusDays(back).format(ISO)
    }

    /** 把一个日期往后推 n 天 */
    fun plusDays(iso: String, days: Long): String? =
        parse(iso)?.plusDays(days)?.format(ISO)

    /** 展示用：8月11日 */
    fun shortOf(iso: String?): String = shortHuman(iso)

    /** 相对今天的口语化描述：今天 / 明天 / 3 天前 / 8月11日 */
    fun relative(iso: String?): String {
        val d = parse(iso) ?: return ""
        val diff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
        return when {
            diff == 0L -> "今天"
            diff == 1L -> "明天"
            diff == -1L -> "昨天"
            diff in 2..6 -> "$diff 天后"
            diff in -6..-2 -> "${-diff} 天前"
            d.year == LocalDate.now().year -> shortHuman(iso)
            else -> "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
        }
    }

    /** 星期几的中文，1=周一 … 7=周日 */
    fun weekdayLabel(dow: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse((dow - 1).coerceIn(0, 6)) { "周一" }

    /** Material3 的 DatePicker 用的是 UTC 毫秒，这里做双向转换 */
    fun isoToUtcMillis(iso: String?): Long? {
        val d = parse(iso) ?: return null
        return d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun utcMillisToIso(millis: Long?): String? {
        if (millis == null) return null
        return java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
    }
}
