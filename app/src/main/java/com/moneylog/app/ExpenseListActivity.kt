package com.moneylog.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.moneylog.app.common.NotificationHelper
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.model.Expense
import com.moneylog.app.ui.ExpenseAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 지출 목록 화면.
 * - 등록된 지출을 최신순으로 보여준다.
 * - 우측 하단 + 버튼으로 지출 등록 화면으로 이동한다.
 * - 상단 "캘린더 보기" 버튼으로 날짜별 지출을 보는 캘린더 화면으로 이동한다.
 * - 이번 달 예산(MONTHLY_BUDGET)을 넘으면 알림을 띄운다.
 */
class ExpenseListActivity : AppCompatActivity() {

    companion object {
        private const val MONTHLY_BUDGET = 300_000L
        private const val SWIPE_MIN_DISTANCE = 100
        private const val SWIPE_MIN_VELOCITY = 100
    }

    private lateinit var tvTotalAmount: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var rvExpenses: RecyclerView
    private lateinit var fabAddExpense: FloatingActionButton
    private lateinit var btnOpenCalendar: TextView
    private lateinit var tvMonthSelector: TextView

    /** 좌우로 스와이프하면 이전/다음 달로 이동한다. */
    private lateinit var monthSwipeDetector: GestureDetector

    /** 지출 목록 화면에서 현재 골라서 보고 있는 달("yyyy-MM"). 기본값은 실제 이번 달. */
    private var selectedYearMonth: String = actualCurrentYearMonth()

    private val adapter = ExpenseAdapter(emptyList()) { expense -> confirmDelete(expense) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과는 무시, 거부해도 앱은 정상 동작 */ }

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
                    refreshList()
                    checkBudget()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_expense_list)

        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        rvExpenses = findViewById(R.id.rvExpenses)
        fabAddExpense = findViewById(R.id.fabAddExpense)
        btnOpenCalendar = findViewById(R.id.btnOpenCalendar)
        tvMonthSelector = findViewById(R.id.tvMonthSelector)

        rvExpenses.layoutManager = LinearLayoutManager(this)
        rvExpenses.adapter = adapter

        fabAddExpense.setOnClickListener {
            addExpenseLauncher.launch(AddExpenseActivity.newIntent(this))
        }

        btnOpenCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        tvMonthSelector.setOnClickListener { showMonthPicker() }

        setupMonthSwipeGesture()

        requestNotificationPermissionIfNeeded()
    }

    /** 화면을 좌우로 스와이프하면 이전/다음 달 지출 내역을 볼 수 있게 한다. */
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

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) { ExpenseRepository.getByMonth(selectedYearMonth) }
            val monthTotal = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(selectedYearMonth)
            }

            adapter.submitList(expenses)
            tvTotalAmount.text = getString(R.string.total_amount_format, monthTotal)
            tvEmptyMessage.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
            tvMonthSelector.text = formatYearMonthLabel(selectedYearMonth)
        }
    }

    private fun checkBudget() {
        lifecycleScope.launch {
            val monthTotal = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(actualCurrentYearMonth())
            }

            if (monthTotal > MONTHLY_BUDGET) {
                NotificationHelper.showBudgetAlert(this@ExpenseListActivity, monthTotal, MONTHLY_BUDGET)
            }
        }
    }

    private fun actualCurrentYearMonth(): String {
        val today = LocalDate.now()
        return "%04d-%02d".format(today.year, today.monthValue)
    }

    /**
     * 지출이 있는 달 + 실제 이번 달 + 현재 선택된 달을 모아 최신순으로 보여주는
     * 커스텀 드롭다운(팝업)을 띄운다. 고르면 그 달 지출만 목록에 남는다.
     */
    private fun showMonthPicker() {
        lifecycleScope.launch {
            val months = withContext(Dispatchers.IO) { ExpenseRepository.distinctMonths() }.toMutableSet()
            months.add(actualCurrentYearMonth())
            months.add(selectedYearMonth)
            val sortedMonths = months.sortedDescending()

            val container = LinearLayout(this@ExpenseListActivity).apply {
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
                ContextCompat.getDrawable(this@ExpenseListActivity, R.drawable.bg_rounded_card)
            )
            popupWindow.elevation = dp(8).toFloat()
            popupWindow.isOutsideTouchable = true

            sortedMonths.forEach { yearMonth ->
                val isSelected = yearMonth == selectedYearMonth
                val row = TextView(this@ExpenseListActivity).apply {
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

    private fun confirmDelete(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExpenseRepository.delete(expense) }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
