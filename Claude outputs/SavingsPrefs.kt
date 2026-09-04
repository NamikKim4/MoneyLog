package com.moneylog.app.common

import android.content.Context

/**
 * 🐷 돼지 저금통에 모인 절약 금액을 기기에 저장해두는 저장소 (BudgetPrefs와 같은 패턴).
 *
 * - totalSaved: 지금까지 저금통에 쌓인 총 금액. 한 달이 지날 때마다(PiggyBankManager가 판단)
 *   "그 달 예산 - 그 달 지출"이 양수면 그 차액만큼 여기 더해진다.
 * - lastProcessedMonth: 마지막으로 정산을 마친 달("yyyy-MM"). 앱을 켤 때마다 이 값과 이번 달을
 *   비교해서, 그 사이에 지나간 달이 있으면 그만큼 저금통에 새로 적립한다.
 * - lastCreditedAmount: 방금 막 저금통에 새로 적립된 금액. 돼지 저금통 화면에 들어갔을 때
 *   "얼마가 새로 쌓였는지" 한 번 축하 메시지로 보여주고 나면 0으로 비운다(consume 패턴).
 * - goal: 저금통 채우기 목표 금액. 화면에 진행률(%)을 보여주는 데 쓴다.
 */
object SavingsPrefs {

    private const val PREFS_NAME = "moneylog_prefs"
    private const val KEY_TOTAL_SAVED = "piggy_total_saved"
    private const val KEY_LAST_PROCESSED_MONTH = "piggy_last_processed_month"
    private const val KEY_LAST_CREDITED = "piggy_last_credited"
    private const val KEY_GOAL = "piggy_goal"

    const val DEFAULT_GOAL = 1_000_000L

    fun getTotalSaved(context: Context): Long {
        return prefs(context).getLong(KEY_TOTAL_SAVED, 0L)
    }

    /** amount가 0보다 클 때만 저금통에 더하고, "방금 새로 쌓인 금액"으로도 함께 기록해둔다. */
    fun addSaved(context: Context, amount: Long) {
        if (amount <= 0) return
        val current = getTotalSaved(context)
        val lastCredited = prefs(context).getLong(KEY_LAST_CREDITED, 0L)
        prefs(context).edit()
            .putLong(KEY_TOTAL_SAVED, current + amount)
            .putLong(KEY_LAST_CREDITED, lastCredited + amount)
            .apply()
    }

    /** 방금 새로 쌓인 금액을 한 번 읽고 나면 0으로 비운다 — 축하 메시지를 딱 한 번만 보여주기 위함. */
    fun consumeLastCredited(context: Context): Long {
        val amount = prefs(context).getLong(KEY_LAST_CREDITED, 0L)
        if (amount > 0) {
            prefs(context).edit().putLong(KEY_LAST_CREDITED, 0L).apply()
        }
        return amount
    }

    fun getGoal(context: Context): Long {
        val saved = prefs(context).getLong(KEY_GOAL, DEFAULT_GOAL)
        return if (saved > 0) saved else DEFAULT_GOAL
    }

    fun setGoal(context: Context, amount: Long) {
        prefs(context).edit().putLong(KEY_GOAL, amount).apply()
    }

    fun getLastProcessedMonth(context: Context): String? {
        return prefs(context).getString(KEY_LAST_PROCESSED_MONTH, null)
    }

    fun setLastProcessedMonth(context: Context, yearMonth: String) {
        prefs(context).edit().putString(KEY_LAST_PROCESSED_MONTH, yearMonth).apply()
    }

    /** 설정 화면의 "데이터 초기화"에서, 저금통도 함께 처음 상태로 되돌릴 때 쓴다. */
    fun reset(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOTAL_SAVED)
            .remove(KEY_LAST_PROCESSED_MONTH)
            .remove(KEY_LAST_CREDITED)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
