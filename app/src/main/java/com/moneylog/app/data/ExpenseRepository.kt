package com.moneylog.app.data

import android.content.Context
import com.moneylog.app.model.Expense

/**
 * 지출 데이터 접근을 한 곳에 모아둔 저장소.
 * 내부적으로 Room(SQLite)을 써서 앱을 껐다 켜도 데이터가 유지된다.
 * init(context)는 앱 시작 시(MoneyLogApp) 한 번만 호출하면 된다.
 */
object ExpenseRepository {

    private lateinit var dao: ExpenseDao

    fun init(context: Context) {
        if (!::dao.isInitialized) {
            dao = MoneyLogDatabase.getInstance(context).expenseDao()
        }
    }

    suspend fun getAll(): List<Expense> {
        return dao.getAll()
    }

    suspend fun getByDate(date: String): List<Expense> {
        return dao.getByDate(date)
    }

    /** 날짜("yyyy-MM-dd") -> 그날 지출 합계 맵. 달력에 날짜별 총액을 표시할 때 쓴다. */
    suspend fun getDailyTotals(): Map<String, Long> {
        return dao.getDailyTotals().associate { it.date to it.total }
    }

    suspend fun add(expense: Expense) {
        dao.insert(expense)
    }

    suspend fun delete(expense: Expense) {
        dao.delete(expense)
    }

    suspend fun totalAmount(): Long {
        return dao.getTotalAmount()
    }

    /** yearMonth는 "yyyy-MM" 형식 (예: "2026-09") */
    suspend fun totalAmountForMonth(yearMonth: String): Long {
        return dao.getTotalAmountForMonth(yearMonth)
    }

    /** yearMonth는 "yyyy-MM" 형식. 그 달에 속한 지출만 최신순으로 가져온다. */
    suspend fun getByMonth(yearMonth: String): List<Expense> {
        return dao.getByMonth(yearMonth)
    }

    /** 지출 내역이 있는 "yyyy-MM" 목록(최신순). 지출 목록 화면의 월 선택 드롭다운에 쓴다. */
    suspend fun distinctMonths(): List<String> {
        return dao.getDistinctMonths()
    }
}
