package com.moneylog.app.common

import android.content.Context

/**
 * 달(yyyy-MM)마다 완전히 독립적으로 예산을 저장해두는 저장소 (서버/로그인 없이 SharedPreferences만 사용).
 * 어떤 달의 예산을 바꿔도 다른 달에는 전혀 영향을 주지 않는다 — 아직 한 번도 손대지 않은 달은
 * 항상 기본 예산(30만원, 또는 이 기능이 생기기 전에 쓰던 값)만 보여준다.
 */
object BudgetPrefs {

    private const val PREFS_NAME = "moneylog_prefs"
    private const val KEY_BUDGET_PREFIX = "monthly_budget_"

    /** 예전 버전에서는 달 구분 없이 이 키 하나에만 예산을 저장했다. 새로 값을 설정하지 않은 달을 위한
     *  기본값으로만 한 번 읽어줄 뿐, 이후 다른 달을 수정해도 이 값 자체는 절대 바뀌지 않는다. */
    private const val KEY_LEGACY_BUDGET = "monthly_budget"

    const val DEFAULT_BUDGET = 300_000L

    /** [yearMonth]에 따로 정해둔 예산이 있으면 그 값을, 없으면 기본 예산을 돌려준다. 다른 달의 값과는 무관하다. */
    fun getBudget(context: Context, yearMonth: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val perMonth = prefs.getLong(KEY_BUDGET_PREFIX + yearMonth, -1L)
        if (perMonth > 0) return perMonth

        val legacy = prefs.getLong(KEY_LEGACY_BUDGET, -1L)
        return if (legacy > 0) legacy else DEFAULT_BUDGET
    }

    /** [yearMonth]의 예산만 저장한다. 다른 달의 예산은 전혀 건드리지 않는다. */
    fun setBudget(context: Context, yearMonth: String, amount: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BUDGET_PREFIX + yearMonth, amount)
            .apply()
    }
}
