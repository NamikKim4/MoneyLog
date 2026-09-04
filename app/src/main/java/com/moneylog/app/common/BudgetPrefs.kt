package com.moneylog.app.common

import android.content.Context

/**
 * 이번 달 예산 금액을 기기에 저장해두는 아주 단순한 저장소 (서버/로그인 없이 SharedPreferences만 사용).
 * 값을 저장한 적이 없으면 기본 예산(30만원)을 쓴다.
 */
object BudgetPrefs {

    private const val PREFS_NAME = "moneylog_prefs"
    private const val KEY_MONTHLY_BUDGET = "monthly_budget"
    const val DEFAULT_BUDGET = 300_000L

    fun getBudget(context: Context): Long {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_MONTHLY_BUDGET, DEFAULT_BUDGET)
        return if (saved > 0) saved else DEFAULT_BUDGET
    }

    fun setBudget(context: Context, amount: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_MONTHLY_BUDGET, amount)
            .apply()
    }
}
