package com.moneylog.app.model

import java.time.LocalDate

/**
 * 캘린더 그리드 한 칸을 나타낸다.
 * date가 null이면 월의 시작/끝을 맞추기 위한 빈 칸이다.
 * totalAmount는 그 날짜에 등록된 지출 합계(원)다.
 * hasMemo는 그 날짜에 저장된 일별 메모가 있는지 여부다.
 */
data class CalendarDay(
    val date: LocalDate?,
    val totalAmount: Long = 0L,
    val isSelected: Boolean = false,
    val hasMemo: Boolean = false
) {
    val hasExpense: Boolean get() = totalAmount > 0
}
