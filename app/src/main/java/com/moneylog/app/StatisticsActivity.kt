package com.moneylog.app

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.moneylog.app.common.CategoryStyle
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.CategoryTotal
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.ui.CategoryDonutChartView
import com.moneylog.app.ui.MonthlyBarChartView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs

/**
 * 지출 통계 화면.
 * - 선택한 달을 기준으로 최근 6개월 지출 합계를 귀여운 막대그래프로 보여준다.
 * - 선택한 달의 카테고리별 비중을 도넛 그래프 + 목록으로 보여준다.
 * - 화면 상단의 ‹ › 버튼뿐 아니라, 화면을 좌우로 스와이프해도 기준 달을 옮길 수 있다
 *   (지출 목록/캘린더와 동일한 조작 방식).
 * - 막대그래프의 막대나 카테고리 목록 한 줄을 탭하면, 그 달(또는 그 달의 그 카테고리)만
 *   걸러서 보여주는 지출 목록 화면으로 바로 넘어간다.
 */
class StatisticsActivity : AppCompatActivity() {

    companion object {
        private const val SWIPE_MIN_DISTANCE = 100
        private const val SWIPE_MIN_VELOCITY = 100
    }

    private lateinit var tvStatsMonth: TextView
    private lateinit var btnPrevMonth: FrameLayout
    private lateinit var btnNextMonth: FrameLayout
    private lateinit var trendChart: MonthlyBarChartView
    private lateinit var donutChart: CategoryDonutChartView
    private lateinit var categoryLegendContainer: LinearLayout
    private lateinit var tvStatsEmpty: TextView

    private lateinit var monthSwipeDetector: GestureDetector

    private var selectedMonth: YearMonth = YearMonth.now()

    /** 막대그래프에 지금 그려져 있는 6개 달(순서대로) - 막대를 탭했을 때 어느 달인지 찾는 데 쓴다. */
    private var trendMonths: List<YearMonth> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        tvStatsMonth = findViewById(R.id.tvStatsMonth)
        btnPrevMonth = findViewById(R.id.btnStatsPrevMonth)
        btnNextMonth = findViewById(R.id.btnStatsNextMonth)
        trendChart = findViewById(R.id.trendChart)
        donutChart = findViewById(R.id.donutChart)
        categoryLegendContainer = findViewById(R.id.categoryLegendContainer)
        tvStatsEmpty = findViewById(R.id.tvStatsEmpty)

        btnPrevMonth.setOnClickListener {
            TouchPopEffect.pop(it)
            goToMonth(-1)
        }
        btnNextMonth.setOnClickListener {
            TouchPopEffect.pop(it)
            goToMonth(1)
        }

        // 막대그래프의 막대 하나를 탭하면 그 달의 지출 목록으로 바로 이동한다.
        trendChart.onBarClick = { index ->
            trendMonths.getOrNull(index)?.let { month -> openExpenseList(month) }
        }

        setupMonthSwipeGesture()
        loadStatistics()
    }

    /** delta만큼 기준 달을 이동한다 (예: -1은 이전 달, 1은 다음 달). */
    private fun goToMonth(delta: Long) {
        selectedMonth = selectedMonth.plusMonths(delta)
        loadStatistics()
    }

    /** 화면을 좌우로 스와이프하면 이전/다음 달 통계를 볼 수 있게 한다. */
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
                    if (diffX < 0) goToMonth(1) else goToMonth(-1)
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

    private fun loadStatistics() {
        tvStatsMonth.text = getString(R.string.month_full_format, selectedMonth.year, selectedMonth.monthValue)

        lifecycleScope.launch {
            // 선택한 달을 마지막으로 하는 최근 6개월 합계를 순서대로 모은다.
            val months = (5 downTo 0).map { selectedMonth.minusMonths(it.toLong()) }
            trendMonths = months
            val trendBars = months.map { month ->
                val yearMonthStr = "%04d-%02d".format(month.year, month.monthValue)
                val total = withContext(Dispatchers.IO) { ExpenseRepository.totalAmountForMonth(yearMonthStr) }
                MonthlyBarChartView.MonthBar(
                    label = getString(R.string.month_header_format, month.monthValue),
                    amount = total,
                    isCurrent = month == selectedMonth
                )
            }
            trendChart.setData(trendBars)

            // 선택한 달의 카테고리별 지출 비중.
            val categoryTotals = withContext(Dispatchers.IO) {
                ExpenseRepository.getCategoryTotals(yearMonthKey(selectedMonth))
            }
            val monthTotal = categoryTotals.sumOf { it.total }

            renderDonutAndLegend(categoryTotals, monthTotal)
        }
    }

    /** 도넛 그래프 + 그 아래 카테고리별 상세 목록을 함께 그린다. 목록 한 줄을 탭하면 그 카테고리로 필터링된 지출 목록이 열린다. */
    private fun renderDonutAndLegend(categoryTotals: List<CategoryTotal>, monthTotal: Long) {
        categoryLegendContainer.removeAllViews()

        if (categoryTotals.isEmpty() || monthTotal <= 0) {
            donutChart.setData(emptyList(), 0L)
            tvStatsEmpty.visibility = View.VISIBLE
            return
        }
        tvStatsEmpty.visibility = View.GONE

        val segments = categoryTotals.map {
            CategoryDonutChartView.Segment(it.total, CategoryStyle.backgroundColorRes(it.category))
        }
        donutChart.setData(segments, monthTotal)

        categoryTotals.forEachIndexed { index, stat ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(this@StatisticsActivity, R.drawable.bg_list_item_ripple)
                setPadding(dp(6), dp(8) + (if (index > 0) dp(4) else 0), dp(6), dp(8))
                setOnClickListener {
                    TouchPopEffect.pop(it)
                    openExpenseList(selectedMonth, stat.category)
                }
            }

            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        ContextCompat.getColor(this@StatisticsActivity, CategoryStyle.backgroundColorRes(stat.category))
                    )
                }
            }

            val tvName = TextView(this).apply {
                text = stat.category
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_main))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val percent = ((stat.total * 100) / monthTotal).toInt().coerceIn(0, 100)
            val tvAmount = TextView(this).apply {
                text = getString(R.string.category_stat_amount_format, stat.total, percent)
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_sub))
                textSize = 12f
            }

            row.addView(dot)
            row.addView(tvName)
            row.addView(tvAmount)
            categoryLegendContainer.addView(row)
        }
    }

    /** 지출 목록 화면을 그 달(선택적으로 카테고리까지) 필터링된 상태로 열고, 통계 화면은 닫는다. */
    private fun openExpenseList(month: YearMonth, category: String? = null) {
        startActivity(ExpenseListActivity.newIntent(this, yearMonthKey(month), category))
        finish()
    }

    private fun yearMonthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
