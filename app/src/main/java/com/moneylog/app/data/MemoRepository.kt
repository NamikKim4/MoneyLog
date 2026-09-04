package com.moneylog.app.data

import android.content.Context
import com.moneylog.app.model.DailyMemo

/**
 * 일별 메모 데이터 접근을 한 곳에 모아둔 저장소 (ExpenseRepository와 같은 패턴).
 */
object MemoRepository {

    private lateinit var dao: DailyMemoDao

    fun init(context: Context) {
        if (!::dao.isInitialized) {
            dao = MoneyLogDatabase.getInstance(context).dailyMemoDao()
        }
    }

    suspend fun getByDate(date: String): DailyMemo? {
        return dao.getByDate(date)
    }

    /** yearMonth는 "yyyy-MM" 형식. */
    suspend fun getByMonth(yearMonth: String): List<DailyMemo> {
        return dao.getByMonth(yearMonth)
    }

    suspend fun getAllDates(): Set<String> {
        return dao.getAllDates().toSet()
    }

    suspend fun distinctMonths(): List<String> {
        return dao.getDistinctMonths()
    }

    /** 내용이 비어있으면 그 날짜의 메모를 삭제하고, 아니면 저장(덮어쓰기)한다. */
    suspend fun save(date: String, memo: String) {
        val trimmed = memo.trim()
        if (trimmed.isEmpty()) {
            dao.deleteByDate(date)
        } else {
            dao.upsert(DailyMemo(date = date, memo = trimmed))
        }
    }

    suspend fun delete(date: String) {
        dao.deleteByDate(date)
    }

    /** 설정 화면의 "데이터 초기화"에서 쓴다 — 메모를 전부 지운다. */
    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
