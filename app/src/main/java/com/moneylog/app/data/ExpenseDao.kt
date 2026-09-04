package com.moneylog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneylog.app.model.Expense

data class DailyTotal(
    val date: String,
    val total: Long
)

/** 카테고리별 지출 합계 (카테고리 순위를 보여줄 때 쓴다). */
data class CategoryTotal(
    val category: String,
    val total: Long
)

/** (카테고리, 금액) 조합이 얼마나 자주 등록됐는지 (지출 등록 화면의 "자주 쓰는 항목" 추천에 쓴다). */
data class CategoryAmountCount(
    val category: String,
    val amount: Long,
    val cnt: Int
)

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense)

    /** 지출 목록/캘린더에서 항목을 탭해 상세 내용을 고쳤을 때, id가 같은 기존 행을 덮어쓴다. */
    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    /** 설정 화면의 "데이터 초기화"에서 쓴다 — 지출 내역을 전부 지운다. */
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<Expense>

    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC")
    suspend fun getByDate(date: String): List<Expense>

    /** 날짜별 지출 합계 (달력에 날짜마다 쓴 금액을 표시하기 위함) */
    @Query("SELECT date as date, SUM(amount) as total FROM expenses GROUP BY date")
    suspend fun getDailyTotals(): List<DailyTotal>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses")
    suspend fun getTotalAmount(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date LIKE :yearMonthPrefix || '%'")
    suspend fun getTotalAmountForMonth(yearMonthPrefix: String): Long

    /** 특정 달("yyyy-MM")에 속한 지출만 최신순으로 가져온다. */
    @Query("SELECT * FROM expenses WHERE date LIKE :yearMonthPrefix || '%' ORDER BY date DESC, id DESC")
    suspend fun getByMonth(yearMonthPrefix: String): List<Expense>

    /** 지출 내역이 있는 달("yyyy-MM") 목록을 최신순으로 가져온다. */
    @Query("SELECT DISTINCT substr(date, 1, 7) FROM expenses ORDER BY substr(date, 1, 7) DESC")
    suspend fun getDistinctMonths(): List<String>

    /** 특정 달의 카테고리별 지출 합계를 많이 쓴 순으로 가져온다 (카테고리 순위용). */
    @Query(
        "SELECT category as category, SUM(amount) as total FROM expenses " +
            "WHERE date LIKE :yearMonthPrefix || '%' GROUP BY category ORDER BY total DESC"
    )
    suspend fun getCategoryTotalsForMonth(yearMonthPrefix: String): List<CategoryTotal>

    /** 가장 자주 등록했던 (카테고리, 금액) 조합을 빈도 순으로 가져온다 (빠른 등록 추천용). */
    @Query(
        "SELECT category as category, amount as amount, COUNT(*) as cnt FROM expenses " +
            "GROUP BY category, amount ORDER BY cnt DESC, MAX(date) DESC LIMIT :limit"
    )
    suspend fun getFrequentEntries(limit: Int): List<CategoryAmountCount>
}
