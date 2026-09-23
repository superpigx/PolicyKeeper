package com.baodan.keeper.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 日期工具：全部基于 Calendar，兼容 minSdk 24，无需 desugaring */
object Dates {

    fun today(): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** 解析 yyyy-MM-dd，失败返回 null */
    fun parse(text: String?): Calendar? {
        if (text.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            val d: Date = sdf.parse(text.trim()) ?: return null
            Calendar.getInstance().apply {
                time = d
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun format(c: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)

    fun format(d: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    fun isValid(text: String?): Boolean = parse(text) != null

    /** 距离今天的天数：正数=未来，0=今天，负数=已过去 */
    fun daysFromToday(dateText: String?): Long? {
        val target = parse(dateText) ?: return null
        val diff = target.timeInMillis - today().timeInMillis
        return Math.round(diff.toDouble() / 86_400_000.0)
    }

    fun yearOf(dateText: String?): Int? {
        val c = parse(dateText) ?: return null
        return c.get(Calendar.YEAR)
    }

    fun todayText(): String = format(today())

}

/** 金额格式化 */
object Money {

    /** 12345.5 -> "12,345.50"；整数则不带小数 */
    fun format(value: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale.CHINA)
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = if (Math.abs(value % 1.0) < 0.005) 0 else 2
        return nf.format(value)
    }

    /** 图表轴用短格式：12345 -> "1.2万" */
    fun short(value: Double): String {
        return when {
            Math.abs(value) >= 100_000_000 -> trim(value / 100_000_000.0) + "亿"
            Math.abs(value) >= 10_000 -> trim(value / 10_000.0) + "万"
            else -> format(value)
        }
    }

    private fun trim(v: Double): String {
        val s = String.format(Locale.US, "%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.trim().replace(",", "").replace("¥", "").replace("元", "")
        if (cleaned.isEmpty()) return null
        return try {
            cleaned.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
