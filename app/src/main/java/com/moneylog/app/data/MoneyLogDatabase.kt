package com.moneylog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneylog.app.model.DailyMemo
import com.moneylog.app.model.Expense

@Database(entities = [Expense::class, DailyMemo::class], version = 2, exportSchema = false)
abstract class MoneyLogDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun dailyMemoDao(): DailyMemoDao

    companion object {
        @Volatile
        private var INSTANCE: MoneyLogDatabase? = null

        /** 1 -> 2: 일별 메모 기능 추가(daily_memos 테이블 신규 생성). 기존 지출 데이터는 그대로 유지된다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_memos` (`date` TEXT NOT NULL, `memo` TEXT NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        fun getInstance(context: Context): MoneyLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneyLogDatabase::class.java,
                    "moneylog.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
