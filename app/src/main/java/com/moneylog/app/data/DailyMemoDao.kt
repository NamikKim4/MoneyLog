package com.moneylog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneylog.app.model.DailyMemo

@Dao
interface DailyMemoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memo: DailyMemo)

    @Query("DELETE FROM daily_memos WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM daily_memos WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyMemo?

    /** yearMonthPrefix는 "yyyy-MM" 형식. 그 달에 속한 메모만 날짜순으로 가져온다. */
    @Query("SELECT * FROM daily_memos WHERE date LIKE :yearMonthPrefix || '%' ORDER BY date ASC")
    suspend fun getByMonth(yearMonthPrefix: String): List<DailyMemo>

    @Query("SELECT date FROM daily_memos")
    suspend fun getAllDates(): List<String>

    /** 메모가 있는 달("yyyy-MM") 목록을 최신순으로 가져온다. */
    @Query("SELECT DISTINCT substr(date, 1, 7) FROM daily_memos ORDER BY substr(date, 1, 7) DESC")
    suspend fun getDistinctMonths(): List<String>
}
