package com.moneylog.app.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.moneylog.app.R
import com.moneylog.app.common.AmountFormat
import com.moneylog.app.model.CalendarDay

/**
 * item_calendar_day 레이아웃 한 칸에 CalendarDay 데이터를 채워 넣는다.
 * 캘린더 칸 개수가 최대 42개로 적고 재사용(recycle)이 필요 없는 화면이라,
 * RecyclerView 없이 직접 그려 넣는 방식이 더 단순하고 확실하다.
 */
object CalendarDayBinder {

    fun bind(view: View, day: CalendarDay, onClick: (CalendarDay) -> Unit) {
        val cell = view.findViewById<LinearLayout>(R.id.dayCell)
        val tvDayNumber = view.findViewById<TextView>(R.id.tvDayNumber)
        val tvDayAmount = view.findViewById<TextView>(R.id.tvDayAmount)
        val tvMemoDot = view.findViewById<TextView>(R.id.tvMemoDot)
        val context = view.context

        if (day.date == null) {
            tvDayNumber.text = ""
            tvDayAmount.text = ""
            tvMemoDot.visibility = View.GONE
            cell.background = null
            view.isClickable = false
            view.setOnClickListener(null)
            return
        }

        tvDayNumber.text = day.date.dayOfMonth.toString()
        tvDayAmount.text = AmountFormat.compact(day.totalAmount)
        tvMemoDot.visibility = if (day.hasMemo) View.VISIBLE else View.GONE

        if (day.isSelected) {
            cell.setBackgroundResource(R.drawable.bg_rounded_day_selected)
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.white))
            tvDayAmount.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            cell.background = null
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_main))
            tvDayAmount.setTextColor(ContextCompat.getColor(context, R.color.danger))
        }

        view.isClickable = true
        view.setOnClickListener { onClick(day) }
    }
}
