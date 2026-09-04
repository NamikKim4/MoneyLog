package com.moneylog.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 하루 단위 자유 메모(일기 같은 느낌) 한 건.
 * 지출 항목의 메모와는 별개로, 캘린더에서 날짜를 한 번 더 탭하면 쓰거나 볼 수 있다.
 * 날짜(yyyy-MM-dd) 하나당 메모는 하나만 존재하고, 다시 저장하면 덮어쓴다.
 */
@Entity(tableName = "daily_memos")
data class DailyMemo(
    @PrimaryKey val date: String,
    val memo: String
)
