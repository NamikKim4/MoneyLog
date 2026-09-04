package com.moneylog.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.MemoRepository
import com.moneylog.app.model.DailyMemo
import com.moneylog.app.ui.MemoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 월별로 적어둔 일별 메모를 모아 보는 화면. 캘린더 화면의 "📝 메모 목록" 버튼으로 들어온다.
 * 항목을 탭하면 그 날짜 메모를 다시 열어 수정할 수 있고, ✕를 누르면 삭제한다.
 */
class MemoListActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_YEAR_MONTH = "EXTRA_YEAR_MONTH"
        private const val SWIPE_MIN_DISTANCE = 100
        private const val SWIPE_MIN_VELOCITY = 100

        fun newIntent(context: Context, yearMonth: String): Intent {
            return Intent(context, MemoListActivity::class.java).apply {
                putExtra(EXTRA_YEAR_MONTH, yearMonth)
            }
        }
    }

    private lateinit var tvMonthSelector: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var rvMemoList: RecyclerView
    private lateinit var fabAddMemo: FloatingActionButton
    private lateinit var monthSwipeDetector: GestureDetector

    private val adapter = MemoAdapter(
        emptyList(),
        onItemClick = { memo -> showMemoDialog(memo.date) },
        onDeleteClick = { memo -> confirmDelete(memo) }
    )

    private var selectedYearMonth: String = actualCurrentYearMonth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memo_list)

        intent.getStringExtra(EXTRA_YEAR_MONTH)?.let { selectedYearMonth = it }

        tvMonthSelector = findViewById(R.id.tvMemoMonthSelector)
        tvEmptyMessage = findViewById(R.id.tvMemoListEmpty)
        rvMemoList = findViewById(R.id.rvMemoList)
        fabAddMemo = findViewById(R.id.fabAddMemo)

        rvMemoList.layoutManager = LinearLayoutManager(this)
        rvMemoList.adapter = adapter

        tvMonthSelector.setOnClickListener {
            TouchPopEffect.pop(it)
            showMonthPicker()
        }
        fabAddMemo.setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.bounce(fabAddMemo)
            showDatePickerThenMemo()
        }

        setupMonthSwipeGesture()
    }

    /** 화면을 좌우로 스와이프하면 이전/다음 달 메모 목록을 볼 수 있게 한다. */
    private fun setupMonthSwipeGesture() {
        monthSwipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_MIN_DISTANCE &&
                    abs(velocityX) > SWIPE_MIN_VELOCITY
                ) {
                    if (diffX < 0) {
                        goToMonth(1) // 왼쪽으로 스와이프 -> 다음 달
                    } else {
                        goToMonth(-1) // 오른쪽으로 스와이프 -> 이전 달
                    }
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        monthSwipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** delta만큼 달을 이동한다 (예: -1은 이전 달, 1은 다음 달). */
    private fun goToMonth(delta: Long) {
        val current = runCatching { YearMonth.parse(selectedYearMonth) }.getOrDefault(YearMonth.now())
        selectedYearMonth = current.plusMonths(delta).toString()
        refreshList()
    }

    /** + 버튼: 날짜를 먼저 고르고, 그 날짜의 메모 작성 창을 연다. */
    private fun showDatePickerThenMemo() {
        val parts = selectedYearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: LocalDate.now().year
        val month = parts.getOrNull(1)?.toIntOrNull() ?: LocalDate.now().monthValue
        val today = LocalDate.now()
        val initial = if (today.year == year && today.monthValue == month) {
            today
        } else {
            LocalDate.of(year, month, 1)
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                selectedYearMonth = "%04d-%02d".format(picked.year, picked.monthValue)
                showMemoDialog(picked.toString())
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun actualCurrentYearMonth(): String {
        val today = LocalDate.now()
        return "%04d-%02d".format(today.year, today.monthValue)
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val memos = withContext(Dispatchers.IO) { MemoRepository.getByMonth(selectedYearMonth) }
            adapter.resetAnimation()
            adapter.submitList(memos)
            tvEmptyMessage.visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE
            rvMemoList.visibility = if (memos.isEmpty()) View.GONE else View.VISIBLE
            tvMonthSelector.text = formatYearMonthLabel(selectedYearMonth)
        }
    }

    private fun showMonthPicker() {
        lifecycleScope.launch {
            val months = withContext(Dispatchers.IO) { MemoRepository.distinctMonths() }.toMutableSet()
            months.add(actualCurrentYearMonth())
            months.add(selectedYearMonth)
            val sortedMonths = months.sortedDescending()

            val container = LinearLayout(this@MemoListActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }

            val popupWindow = PopupWindow(
                container,
                dp(170),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.setBackgroundDrawable(
                ContextCompat.getDrawable(this@MemoListActivity, R.drawable.bg_rounded_card)
            )
            popupWindow.elevation = dp(8).toFloat()
            popupWindow.isOutsideTouchable = true

            sortedMonths.forEach { yearMonth ->
                val isSelected = yearMonth == selectedYearMonth
                val row = TextView(this@MemoListActivity).apply {
                    text = formatYearMonthLabel(yearMonth)
                    textSize = 15f
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    if (isSelected) {
                        setBackgroundResource(R.drawable.bg_rounded_day_selected)
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        setTypeface(typeface, Typeface.BOLD)
                    } else {
                        setTextColor(ContextCompat.getColor(context, R.color.text_main))
                    }
                    setOnClickListener {
                        selectedYearMonth = yearMonth
                        refreshList()
                        popupWindow.dismiss()
                    }
                }
                container.addView(row)
            }

            popupWindow.showAsDropDown(tvMonthSelector, 0, dp(6))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatYearMonthLabel(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null) return yearMonth
        return getString(R.string.month_selector_format, year, month)
    }

    private fun showMemoDialog(date: String) {
        val localDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_daily_memo, null)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvMemoDialogDate)
        val etMemo = dialogView.findViewById<EditText>(R.id.etMemoContent)

        tvDate.text = getString(R.string.memo_dialog_title_format, localDate.monthValue, localDate.dayOfMonth)

        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { MemoRepository.getByDate(date) }
            etMemo.setText(existing?.memo.orEmpty())
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val text = etMemo.text.toString()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { MemoRepository.save(date, text) }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun confirmDelete(memo: DailyMemo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.memo_delete_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { MemoRepository.delete(memo.date) }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }
}
