package com.moneylog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.moneylog.app.model.Expense

data class DailyTotal(
    val date: String,
    val total: Long
)

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

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
}
