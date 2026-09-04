package com.moneylog.app.common

/**
 * 좁은 공간(달력 칸 등)에 지출 금액을 짧게 표시하기 위한 포맷터.
 * 예: 8000 -> "8,000", 15000 -> "1.5만", 30000 -> "3만"
 */
object AmountFormat {

    fun compact(amount: Long): String {
        if (amount <= 0) return ""

        return when {
            amount >= 100_000_000L -> formatUnit(amount, 100_000_000.0, "억")
            amount >= 10_000L -> formatUnit(amount, 10_000.0, "만")
            else -> "%,d".format(amount)
        }
    }

    private fun formatUnit(amount: Long, unit: Double, suffix: String): String {
        val value = amount / unit
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == Math.floor(rounded)) {
            "${rounded.toLong()}$suffix"
        } else {
            "%.1f%s".format(rounded, suffix)
        }
    }
}
