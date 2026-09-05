package com.moneylog.app

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.moneylog.app.common.BudgetPrefs
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.data.MemoRepository
import com.moneylog.app.model.CalendarDay
import com.moneylog.app.model.Expense
import com.moneylog.app.ui.CalendarDayBinder
import com.moneylog.app.ui.ExpenseAdapter
import com.moneylog.app.ui.SwipeToDeleteHelper
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
 * 그날 지출 삭제는 지출 목록 화면과 똑같이 왼쪽 스와이프 + 실행취소 스낵바로 처리한다.
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: FrameLayout
    private lateinit var btnNextMonth: FrameLayout
    private lateinit var btnMemoList: FrameLayout
    private lateinit var calendarGrid: LinearLayout
    private lateinit var tvSelectedDateLabel: TextView
    private lateinit var tvSelectedDayTotal: TextView
    private lateinit var tvSelectedDayEmpty: TextView
    private lateinit var rvSelectedDayExpenses: RecyclerView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvCalendarBudgetStatus: TextView
    private lateinit var btnAddForDay: FrameLayout

    private val dayExpenseAdapter = ExpenseAdapter(emptyList(), onItemClick = { expense -> openEditExpense(expense) })
    private lateinit var gestureDetector: GestureDetector

    /** 스와이프 삭제/실행취소를 화면에서 바로 반영하기 위해 들고 있는, 선택된 날짜의 지출 목록. */
    private var currentDayExpenses: List<Expense> = emptyList()

    /** 새 지출 등록/기존 지출 수정 화면(AddExpenseActivity)을 모두 이 launcher 하나로 처리한다. */
    private val addExpenseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            // 수정 화면에서 "삭제"를 눌러 돌아온 경우: 기존 삭제+실행취소 로직을 그대로 재사용한다.
            val deleteId = data.getLongExtra(AddExpenseActivity.EXTRA_DELETE_ID, -1L)
            if (deleteId != -1L) {
                currentDayExpenses.find { it.id == deleteId }?.let { deleteExpenseWithUndo(it) }
                return@registerForActivityResult
            }

            val editId = data.getLongExtra(AddExpenseActivity.EXTRA_EDIT_ID, -1L)
            val expense = Expense(
                id = if (editId != -1L) editId else System.currentTimeMillis(),
                date = data.getStringExtra(AddExpenseActivity.EXTRA_DATE).orEmpty(),
                category = data.getStringExtra(AddExpenseActivity.EXTRA_CATEGORY).orEmpty(),
                amount = data.getLongExtra(AddExpenseActivity.EXTRA_AMOUNT, 0L),
                memo = data.getStringExtra(AddExpenseActivity.EXTRA_MEMO).orEmpty()
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (editId != -1L) ExpenseRepository.update(expense) else ExpenseRepository.add(expense)
                }
                loadMonth()
            }
        }

    /** 지출 항목을 탭했을 때: 그 내용을 채운 수정 화면을 연다. */
    private fun openEditExpense(expense: Expense) {
        addExpenseLauncher.launch(AddExpenseActivity.newIntentForEdit(this, expense))
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
        tvCalendarBudgetStatus = findViewById(R.id.tvCalendarBudgetStatus)
        btnAddForDay = findViewById(R.id.btnAddForDay)

        rvSelectedDayExpenses.layoutManager = LinearLayoutManager(this)
        rvSelectedDayExpenses.adapter = dayExpenseAdapter

        SwipeToDeleteHelper(
            density = resources.displayMetrics.density,
            canSwipe = { true },
            onSwiped = { position, itemView ->
                dayExpenseAdapter.getItemAt(position)?.let {
                    TouchPopEffect.pop(itemView)
                    deleteExpenseWithUndo(it)
                }
            }
        ).attachTo(rvSelectedDayExpenses)

        btnPrevMonth.setOnClickListener {
            TouchPopEffect.pop(it)
            goToPreviousMonth()
        }
        btnNextMonth.setOnClickListener {
            TouchPopEffect.pop(it)
            goToNextMonth()
        }
        btnAddForDay.setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.bounce(btnAddForDay)
            addExpenseLauncher.launch(AddExpenseActivity.newIntent(this, selectedDate.toString()))
        }
        btnMemoList.setOnClickListener {
            TouchPopEffect.pop(it)
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
            tvMonthTotal.text = getString(R.string.total_amount_format, monthTotal)
            if (monthTotal > 0) FunAnimations.jingle(tvMonthTotal)
            renderBudgetStatus(monthTotal, yearMonthStr)

            renderCalendarGrid()
            loadSelectedDayExpenses()
        }
    }

    /** 지금 보고 있는 달의 예산과 비교해서, 남은 금액 또는 초과한 금액을 보여준다.
     *  예산은 달마다 따로 저장되므로, 달을 넘길 때마다 그 달에 맞는 값을 다시 가져와서 계산한다. */
    private fun renderBudgetStatus(monthTotal: Long, yearMonth: String) {
        val budget = BudgetPrefs.getBudget(this, yearMonth)
        val remaining = budget - monthTotal
        if (remaining >= 0) {
            tvCalendarBudgetStatus.text = getString(R.string.budget_remaining_format, remaining)
            tvCalendarBudgetStatus.setTextColor(ContextCompat.getColor(this, R.color.safe))
        } else {
            tvCalendarBudgetStatus.text = getString(R.string.budget_over_format, -remaining)
            tvCalendarBudgetStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
    }

    private fun renderCalendarGrid() {
        tvMonthYear.text = getString(
            R.string.calendar_month_format,
            currentMonth.year,
            currentMonth.monthValue
        )

        val today = LocalDate.now()
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
                    hasMemo = memoDates.contains(date.toString()),
                    isToday = date == today
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

        // 달을 넘길 때마다 격자가 살짝 페이드로 다시 나타나게 해서 화면이 뚝뚝 바뀌지 않게 한다.
        calendarGrid.alpha = 0f
        calendarGrid.animate().alpha(1f).setDuration(220L).start()
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

        updateSelectedDayTotalLabel()

        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) {
                ExpenseRepository.getByDate(selectedDate.toString())
            }

            currentDayExpenses = expenses
            dayExpenseAdapter.resetAnimation()
            renderDayExpenseList()

            // 선택한 날짜의 상세 영역도 살짝 페이드로 갱신되게 한다.
            val detailGroup = listOf(tvSelectedDateLabel, tvSelectedDayEmpty, rvSelectedDayExpenses)
            detailGroup.forEach { it.alpha = 0f }
            detailGroup.forEach { view ->
                view.animate().alpha(1f).setDuration(200L).start()
            }
        }
    }

    private fun updateSelectedDayTotalLabel() {
        val dayTotal = dailyTotals[selectedDate] ?: 0L
        if (dayTotal > 0) {
            tvSelectedDayTotal.visibility = View.VISIBLE
            tvSelectedDayTotal.text = getString(R.string.amount_won_format, dayTotal)
            FunAnimations.jingle(tvSelectedDayTotal)
        } else {
            tvSelectedDayTotal.visibility = View.GONE
        }
    }

    private fun renderDayExpenseList() {
        dayExpenseAdapter.submitList(currentDayExpenses)
        tvSelectedDayEmpty.visibility = if (currentDayExpenses.isEmpty()) View.VISIBLE else View.GONE
        rvSelectedDayExpenses.visibility = if (currentDayExpenses.isEmpty()) View.GONE else View.VISIBLE
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

    /**
     * 왼쪽으로 스와이프해서 지운 지출을 즉시 화면에서 지우고(서버 삭제는 백그라운드),
     * 실행취소 스낵바를 보여준다. 달력 격자/월 총 지출도 다시 조회 없이 그 자리에서 갱신한다.
     */
    private fun deleteExpenseWithUndo(expense: Expense) {
        currentDayExpenses = currentDayExpenses.filterNot { it.id == expense.id }
        renderDayExpenseList()
        updateSelectedDayTotalLabel()

        dailyTotals = dailyTotals.toMutableMap().apply {
            val updated = (this[selectedDate] ?: 0L) - expense.amount
            if (updated > 0) this[selectedDate] = updated else remove(selectedDate)
        }

        val previousMonthTotalText = tvMonthTotal.text
        val previousBudgetStatusText = tvCalendarBudgetStatus.text
        val previousBudgetStatusColor = tvCalendarBudgetStatus.currentTextColor
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ExpenseRepository.delete(expense) }
            val yearMonthStr = currentYearMonthString()
            val monthTotal = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(yearMonthStr)
            }
            tvMonthTotal.text = getString(R.string.total_amount_format, monthTotal)
            renderBudgetStatus(monthTotal, yearMonthStr)
        }

        Snackbar.make(rvSelectedDayExpenses, getString(R.string.expense_deleted_message), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo_button) {
                tvMonthTotal.text = previousMonthTotalText
                tvCalendarBudgetStatus.text = previousBudgetStatusText
                tvCalendarBudgetStatus.setTextColor(previousBudgetStatusColor)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExpenseRepository.add(expense) }
                    loadMonth()
                }
            }
            .show()
    }
}
