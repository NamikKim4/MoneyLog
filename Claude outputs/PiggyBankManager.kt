package com.moneylog.app.common

import android.content.Context
import com.moneylog.app.data.ExpenseRepository
import java.time.YearMonth

/**
 * 🐷 돼지 저금통 적립 로직.
 *
 * 앱을 열 때마다(스플래시 화면) 한 번씩 불러서, "이미 다 지나가버린 달" 중에
 * 아직 저금통에 반영하지 않은 달이 있으면 그 달의 (예산 - 지출)을 계산해
 * 양수인 만큼 저금통에 쌓아준다. 이번 달처럼 "아직 진행 중인 달"은 최종 지출액이
 * 확정되지 않았으니 여기서는 건드리지 않는다 (진행 중인 예상 절약액은 화면에서 별도로
 * 실시간 계산해서 보여준다).
 *
 * 처음 쓰는 사람이거나 이 기능이 막 추가된 시점에는 과거 달을 소급 적용하지 않고,
 * "이번 달부터 지켜보기 시작"한 것으로 기준을 잡는다 — 과거 데이터로 갑자기 큰 금액이
 * 훅 쌓이면 오히려 어리둥절할 수 있기 때문.
 */
object PiggyBankManager {

    suspend fun processMonthRollover(context: Context) {
        val current = YearMonth.now()
        val lastProcessedStr = SavingsPrefs.getLastProcessedMonth(context)

        if (lastProcessedStr == null) {
            // 처음 실행: 지금 이 순간부터를 기준으로 삼는다.
            SavingsPrefs.setLastProcessedMonth(context, current.toString())
            return
        }

        val lastProcessed = runCatching { YearMonth.parse(lastProcessedStr) }.getOrNull() ?: current
        if (!lastProcessed.isBefore(current)) return

        val budget = BudgetPrefs.getBudget(context)
        var newlySaved = 0L
        var cursor = lastProcessed.plusMonths(1)
        while (cursor.isBefore(current)) {
            val key = "%04d-%02d".format(cursor.year, cursor.monthValue)
            val spent = ExpenseRepository.totalAmountForMonth(key)
            val diff = budget - spent
            if (diff > 0) newlySaved += diff
            cursor = cursor.plusMonths(1)
        }

        if (newlySaved > 0) {
            SavingsPrefs.addSaved(context, newlySaved)
        }
        SavingsPrefs.setLastProcessedMonth(context, current.toString())
    }
}
