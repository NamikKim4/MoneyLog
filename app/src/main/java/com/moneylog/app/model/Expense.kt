package com.moneylog.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 지출 한 건을 나타내는 데이터 클래스이자 Room 테이블 엔티티.
 * date는 "yyyy-MM-dd" 형식(ISO)으로 저장해서 정렬/검색이 쉽게 해뒀다.
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val date: String,
    val category: String,
    val amount: Long,
    val memo: String = ""
)
