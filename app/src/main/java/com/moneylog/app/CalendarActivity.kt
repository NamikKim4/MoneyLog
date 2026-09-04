package com.moneylog.app

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.data.MemoRepository
import com.moneylog.app.model.CalendarDay
import com.moneylog.app.model.Expense
import com.moneylog.app.ui.CalendarDayBinder
import com.moneylog.app.ui.ExpenseAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 월간 달력에서 날짜마다 그날 쓴 금액을 바로 보여주고,
 * 날짜를 탭하면 그 날의 지출 내역(+ 합계)을 화면 하단에 보여준다.
 * 이미 선택된 날짜를 한 번 더 탭하면 그날의 메모를 쓰거나 볼 수 있는 창이 뜬다.
 * 메모가 있는 날짜는 캘린더 칸에 📝 표시가 붙는다.
 * 화면 아무 곳이나 좌우로 스와이프해도 이전/다음 달로 넘어간다.
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: TextView
    private lateinit var btnNextMonth: TextView
    private lateinit var btnMemoList: TextView
    private lateinit var calendarGrid: LinearLayout
    private lateinit var tvSelectedDateLabel: TextView
    private lateinit var tvSelectedDayTotal: TextView
    private lateinit var tvSelectedDayEmpty: TextView
    private lateinit var rvSelectedDayExpenses: RecyclerView
    private lateinit var tvMonthTotal: TextView
    private lateinit var btnAddForDay: TextView

    private val dayExpenseAdapter = ExpenseAdapter(emptyList()) { expense -> confirmDeleteExpense(expense) }
    private lateinit var gestureDetector: GestureDetector

    private val addExpenseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult

                val expense = Expense(
                    date = data.getStringExtra(AddExpenseActivity.EXTRA_DATE).orEmpty(),
                    category = data.getStringExtra(AddExpenseActivity.EXTRA_CATEGORY).orEmpty(),
                    amount = data.getLongExtra(AddExpenseActivity.EXTRA_AMOUNT, 0L),
                    memo = data.getStringExtra(AddExpenseActivity.EXTRA_MEMO).orEmpty()
                )

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExpenseRepository.add(expense) }
                    loadMonth()
                }
            }
        }

    private var currentMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()

    /** 날짜 -> 그날 지출 합계 */
    private var dailyTotals: Map<LocalDate, Long> = emptyMap()

    /** 이번 달에 메모가 있는 날짜("yyyy-MM-dd") 목록 */
    private var memoDates: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_calendar)

        tvMonthYear = findViewById(R.id.tvMonthYear)
        btnPrevMonth = findViewById(R.id.btnPrevMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        btnMemoList = findViewById(R.id.btnMemoList)
        calendarGrid = findViewById(R.id.calendarGrid)
        tvSelectedDateLabel = findViewById(R.id.tvSelectedDateLabel)
        tvSelectedDayTotal = findViewById(R.id.tvSelectedDayTotal)
        tvSelectedDayEmpty = findViewById(R.id.tvSelectedDayEmpty)
        rvSelectedDayExpenses = findViewById(R.id.rvSelectedDayExpenses)
        tvMonthTotal = findViewById(R.id.tvMonthTotal)
        btnAddForDay = findViewById(R.id.btnAddForDay)

        rvSelectedDayExpenses.layoutManager = LinearLayoutManager(this)
        rvSelectedDayExpenses.adapter = dayExpenseAdapter

        btnPrevMonth.setOnClickListener { goToPreviousMonth() }
        btnNextMonth.setOnClickListener { goToNextMonth() }
        btnAddForDay.setOnClickListener {
            addExpenseLauncher.launch(AddExpenseActivity.newIntent(this, selectedDate.toString()))
        }
        btnMemoList.setOnClickListener {
            startActivity(MemoListActivity.newIntent(this, currentYearMonthString()))
        }

        setupSwipeGesture()
    }

    override fun onResume() {
        super.onResume()
        loadMonth()
    }

    /**
     * 화면 전체 터치 흐름을 가로채지 않고 "곁눈질"만 해서 좌우 스와이프를 감지한다.
     * super.dispatchTouchEvent를 항상 호출하기 때문에 날짜 칸 클릭 등 기존 동작은 그대로 유지된다.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 80 && abs(velocityX) > 200) {
                    if (deltaX > 0) goToPreviousMonth() else goToNextMonth()
                    return true
                }

                return false
            }
        })
    }

    private fun goToPreviousMonth() {
        currentMonth = currentMonth.minusMonths(1)
        selectDefaultDayForCurrentMonth()
        loadMonth()
    }

    private fun goToNextMonth() {
        currentMonth = currentMonth.plusMonths(1)
        selectDefaultDayForCurrentMonth()
        loadMonth()
    }

    /**
     * 달을 넘기면 하단 상세 목록도 그 달에 맞는 날짜를 보여줘야 하므로,
     * 오늘이 보이는 달에 속해 있으면 오늘을, 아니면 1일을 기본 선택으로 맞춘다.
     */
    private fun selectDefaultDayForCurrentMonth() {
        val today = LocalDate.now()
        selectedDate = if (YearMonth.from(today) == currentMonth) {
            today
        } else {
            currentMonth.atDay(1)
        }
    }

    private fun currentYearMonthString(): String {
        return "%04d-%02d".format(currentMonth.year, currentMonth.monthValue)
    }

    private fun loadMonth() {
        lifecycleScope.launch {
            val rawTotals = withContext(Dispatchers.IO) { ExpenseRepository.getDailyTotals() }
            dailyTotals = rawTotals.mapNotNull { (dateString, total) ->
                runCatching { LocalDate.parse(dateString) }.getOrNull()?.let { it to total }
            }.toMap()

            val yearMonthStr = currentYearMonthString()

            val monthMemos = withContext(Dispatchers.IO) { MemoRepository.getByMonth(yearMonthStr) }
            memoDates = monthMemos.map { it.date }.toSet()

            val monthTotal = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(yearMonthStr)
            }
            tvMonthTotal.text = getString(R.string.month_total_format, monthTotal)

            renderCalendarGrid()
            loadSelectedDayExpenses()
        }
    }

    private fun renderCalendarGrid() {
        tvMonthYear.text = getString(
            R.string.calendar_month_format,
            currentMonth.year,
            currentMonth.monthValue
        )

        val firstDayOfMonth = currentMonth.atDay(1)
        // 일요일=0, 월요일=1 ... 토요일=6 이 되도록 보정
        val leadingBlanks = firstDayOfMonth.dayOfWeek.value % 7

        val cells = mutableListOf<CalendarDay>()
        repeat(leadingBlanks) { cells.add(CalendarDay(date = null)) }

        for (day in 1..currentMonth.lengthOfMonth()) {
            val date = currentMonth.atDay(day)
            cells.add(
                CalendarDay(
                    date = date,
                    totalAmount = dailyTotals[date] ?: 0L,
                    isSelected = date == selectedDate,
                    hasMemo = memoDates.contains(date.toString())
                )
            )
        }

        // 마지막 주도 7칸을 맞춰서 열이 어긋나지 않도록 빈 칸을 채운다.
        while (cells.size % 7 != 0) {
            cells.add(CalendarDay(date = null))
        }

        calendarGrid.removeAllViews()
        val inflater = LayoutInflater.from(this)

        var rowLayout: LinearLayout? = null
        cells.forEachIndexed { index, day ->
            if (index % 7 == 0) {
                rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                calendarGrid.addView(rowLayout)
            }

            val cellView = inflater.inflate(R.layout.item_calendar_day, rowLayout, false)
            CalendarDayBinder.bind(cellView, day) { clickedDay -> onDaySelected(clickedDay) }
            rowLayout?.addView(cellView)
        }
    }

    /** 다른 날짜를 탭하면 그 날짜를 선택하고, 이미 선택되어 있던 날짜를 한 번 더 탭하면 메모 창을 연다. */
    private fun onDaySelected(day: CalendarDay) {
        val date = day.date ?: return

        if (date == selectedDate) {
            showMemoDialog(date)
            return
        }

        selectedDate = date
        renderCalendarGrid()
        loadSelectedDayExpenses()
    }

    private fun loadSelectedDayExpenses() {
        tvSelectedDateLabel.text = getString(
            R.string.selected_day_format,
            selectedDate.monthValue,
            selectedDate.dayOfMonth
        )

        val dayTotal = dailyTotals[selectedDate] ?: 0L
        if (dayTotal > 0) {
            tvSelectedDayTotal.visibility = View.VISIBLE
            tvSelectedDayTotal.text = getString(R.string.selected_day_total_format, dayTotal)
        } else {
            tvSelectedDayTotal.visibility = View.GONE
        }

        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) {
                ExpenseRepository.getByDate(selectedDate.toString())
            }

            dayExpenseAdapter.submitList(expenses)
            tvSelectedDayEmpty.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
            rvSelectedDayExpenses.visibility = if (expenses.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** 그 날짜의 메모를 쓰거나 수정하는 다이얼로그. 저장하면 캘린더의 📝 표시도 즉시 갱신된다. */
    private fun showMemoDialog(date: LocalDate) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_daily_memo, null)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvMemoDialogDate)
        val etMemo = dialogView.findViewById<EditText>(R.id.etMemoContent)

        tvDate.text = getString(R.string.memo_dialog_title_format, date.monthValue, date.dayOfMonth)

        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { MemoRepository.getByDate(date.toString()) }
            etMemo.setText(existing?.memo.orEmpty())
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val text = etMemo.text.toString()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { MemoRepository.save(date.toString(), text) }
                    loadMonth()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun confirmDeleteExpense(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExpenseRepository.delete(expense) }
                    loadMonth()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }
}
