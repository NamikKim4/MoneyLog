package com.moneylog.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.moneylog.app.common.BudgetPrefs
import com.moneylog.app.common.CategoryStyle
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.NotificationHelper
import com.moneylog.app.common.NotificationPrefs
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.CategoryTotal
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.model.Expense
import com.moneylog.app.ui.ExpenseListItem
import com.moneylog.app.ui.GroupedExpenseAdapter
import com.moneylog.app.ui.SwipeToDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 지출 목록 화면.
 * - 이번 달 지출을 큰 숫자 하나(히어로 넘버)로 보여준다.
 * - 예산 잔여액/지난달 대비 증감/카테고리 TOP3를 차분한 카드 하나에 모아 보여준다.
 * - 지출은 날짜별로 묶어서 보여주고(항목마다 날짜를 반복하지 않는다), 카테고리는 원형 아이콘으로 표시한다.
 * - 삭제는 ✕ 버튼 대신 왼쪽 스와이프 제스처로 하고, 실행취소 스낵바를 보여준다.
 * - 우측 하단 + 버튼으로 지출 등록 화면으로, 상단 캘린더 아이콘으로 캘린더 화면으로 이동한다.
 * - 예산 영역만 탭할 수 있게 분리해서, 그냥 보여주기만 하는 카테고리 순위와 헷갈리지 않게 한다.
 */
class ExpenseListActivity : AppCompatActivity() {

    companion object {
        private const val SWIPE_MIN_DISTANCE = 100
        private const val SWIPE_MIN_VELOCITY = 100

        /** 통계 화면 등 다른 화면에서 "이 달, 이 카테고리만" 보여주며 열 때 쓰는 extra들. */
        const val EXTRA_YEAR_MONTH = "EXTRA_YEAR_MONTH"
        const val EXTRA_CATEGORY_FILTER = "EXTRA_CATEGORY_FILTER"

        fun newIntent(context: Context, yearMonth: String? = null, categoryFilter: String? = null): Intent {
            return Intent(context, ExpenseListActivity::class.java).apply {
                if (yearMonth != null) putExtra(EXTRA_YEAR_MONTH, yearMonth)
                if (categoryFilter != null) putExtra(EXTRA_CATEGORY_FILTER, categoryFilter)
            }
        }
    }

    private lateinit var tvHeroLabel: TextView
    private lateinit var tvHeroAmount: TextView
    private lateinit var tvMonthCompare: TextView
    private lateinit var budgetSection: LinearLayout
    private lateinit var tvBudgetValue: TextView
    private lateinit var pbBudget: ProgressBar
    private lateinit var tvBudgetRemaining: TextView
    private lateinit var categoryStatsContainer: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var tvEmptyMessage: TextView
    private lateinit var rvExpenses: RecyclerView
    private lateinit var fabAddExpense: FloatingActionButton
    private lateinit var btnOpenCalendar: FrameLayout
    private lateinit var btnOpenStatistics: FrameLayout
    private lateinit var btnOpenPiggyBank: FrameLayout
    private lateinit var btnOpenSettings: FrameLayout
    private lateinit var monthSelectorContainer: LinearLayout
    private lateinit var tvMonthSelector: TextView
    private lateinit var scrollRoot: NestedScrollView
    private lateinit var searchContainer: LinearLayout

    /** 좌우로 스와이프하면 이전/다음 달로 이동한다. */
    private lateinit var monthSwipeDetector: GestureDetector

    /** 지출 목록 화면에서 현재 골라서 보고 있는 달("yyyy-MM"). 기본값은 실제 이번 달. */
    private var selectedYearMonth: String = actualCurrentYearMonth()

    /** 서버 쿼리를 다시 하지 않고 검색/삭제를 바로 반영하기 위해 선택된 달의 전체 목록을 들고 있는다. */
    private var currentMonthExpenses: List<Expense> = emptyList()
    private var searchQuery: String = ""

    /** 카테고리 TOP3 카드에서 탭해 고른 카테고리(다시 탭하거나 달을 바꾸면 해제된다). */
    private var categoryFilter: String? = null

    /** 지난달 합계는 이번 달 안에서 지우거나 되돌려도 바뀌지 않으므로 새로 불러올 때만 갱신한다. */
    private var lastMonthTotalCache: Long = 0L

    /** 히어로 숫자가 매번 뚝 바뀌지 않고 스르륵 세어 올라가도록 이전 값을 기억해둔다. */
    private var displayedHeroTotal: Long = 0L
    private var heroAnimator: ValueAnimator? = null

    private val groupedAdapter = GroupedExpenseAdapter(onItemClick = { expense -> openEditExpense(expense) })

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과는 무시, 거부해도 앱은 정상 동작 */ }

    /** 새 지출 등록/기존 지출 수정 화면(AddExpenseActivity)을 모두 이 launcher 하나로 처리한다. */
    private val addExpenseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            // 수정 화면에서 "삭제"를 눌러 돌아온 경우: 기존 삭제+실행취소 로직을 그대로 재사용한다.
            val deleteId = data.getLongExtra(AddExpenseActivity.EXTRA_DELETE_ID, -1L)
            if (deleteId != -1L) {
                currentMonthExpenses.find { it.id == deleteId }?.let { deleteWithUndo(it) }
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
                refreshList()
                checkBudget()
            }
        }

    /** 지출 항목을 탭했을 때: 그 내용을 채운 수정 화면을 연다. */
    private fun openEditExpense(expense: Expense) {
        addExpenseLauncher.launch(AddExpenseActivity.newIntentForEdit(this, expense))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_expense_list)

        // 통계 화면 등에서 "이 달, 이 카테고리만" 보라며 넘어온 경우, 그 상태로 시작한다.
        intent.getStringExtra(EXTRA_YEAR_MONTH)?.let { selectedYearMonth = it }
        categoryFilter = intent.getStringExtra(EXTRA_CATEGORY_FILTER)

        tvHeroLabel = findViewById(R.id.tvHeroLabel)
        tvHeroAmount = findViewById(R.id.tvHeroAmount)
        tvMonthCompare = findViewById(R.id.tvMonthCompare)
        budgetSection = findViewById(R.id.budgetSection)
        tvBudgetValue = findViewById(R.id.tvBudgetValue)
        pbBudget = findViewById(R.id.pbBudget)
        tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining)
        categoryStatsContainer = findViewById(R.id.categoryStatsContainer)
        etSearch = findViewById(R.id.etSearch)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
        rvExpenses = findViewById(R.id.rvExpenses)
        fabAddExpense = findViewById(R.id.fabAddExpense)
        btnOpenCalendar = findViewById(R.id.btnOpenCalendar)
        btnOpenStatistics = findViewById(R.id.btnOpenStatistics)
        btnOpenPiggyBank = findViewById(R.id.btnOpenPiggyBank)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        monthSelectorContainer = findViewById(R.id.monthSelectorContainer)
        tvMonthSelector = findViewById(R.id.tvMonthSelector)
        scrollRoot = findViewById(R.id.scrollRoot)
        searchContainer = findViewById(R.id.searchContainer)

        rvExpenses.layoutManager = LinearLayoutManager(this)
        rvExpenses.adapter = groupedAdapter

        SwipeToDeleteHelper(
            density = resources.displayMetrics.density,
            canSwipe = { position -> groupedAdapter.isSwipeable(position) },
            onSwiped = { position, itemView ->
                groupedAdapter.expenseAt(position)?.let {
                    TouchPopEffect.pop(itemView)
                    deleteWithUndo(it)
                }
            }
        ).attachTo(rvExpenses)

        fabAddExpense.setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.bounce(fabAddExpense)
            addExpenseLauncher.launch(AddExpenseActivity.newIntent(this))
        }

        btnOpenCalendar.setOnClickListener {
            TouchPopEffect.pop(it)
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        btnOpenStatistics.setOnClickListener {
            TouchPopEffect.pop(it)
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        btnOpenPiggyBank.setOnClickListener {
            TouchPopEffect.pop(it)
            startActivity(Intent(this, PiggyBankActivity::class.java))
        }

        btnOpenSettings.setOnClickListener {
            TouchPopEffect.pop(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        monthSelectorContainer.setOnClickListener {
            TouchPopEffect.pop(it)
            showMonthPicker()
        }

        // 예산 영역만 탭할 수 있게 해서, 바로 아래 카테고리 순위(정보성 텍스트)와는
        // 분명히 다른 "편집 가능한 항목"으로 보이게 한다.
        budgetSection.setOnClickListener {
            TouchPopEffect.pop(it)
            showBudgetEditDialog()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) TouchPopEffect.pop(etSearch)
                searchQuery = s?.toString().orEmpty()
                applyFilterAndRender()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 키보드가 올라와서 화면이 눌려도 검색창과 그 아래 결과가 가려지지 않도록,
        // 검색창에 포커스가 가면 그 위치까지 확실히 끌어올려 보여준다.
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollRoot.post { scrollRoot.smoothScrollTo(0, searchContainer.top) }
            }
        }

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
        // 달이 바뀌면 카테고리 구성도 달라지므로, 이전에 걸어둔 카테고리 필터는 해제한다.
        categoryFilter = null
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    /** 검색/삭제/화면 복귀 등으로 목록만 다시 그릴 때 호출한다. 카테고리 필터는 그대로 유지된다. */
    private fun refreshList() {
        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) { ExpenseRepository.getByMonth(selectedYearMonth) }
            lastMonthTotalCache = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(previousYearMonth(selectedYearMonth))
            }

            currentMonthExpenses = expenses
            groupedAdapter.resetAnimation()
            applyFilterAndRender()
            recomputeAndRenderFromCurrentList()

            tvMonthSelector.text = formatMonthHeaderLabel(selectedYearMonth)
        }
    }

    /** 검색창 내용 + 카테고리 필터로 이미 불러온 이번 달 목록만 걸러서 날짜별로 묶어 다시 보여준다 (새 쿼리 없음). */
    private fun applyFilterAndRender() {
        val query = searchQuery.trim()
        var filtered = if (query.isEmpty()) {
            currentMonthExpenses
        } else {
            currentMonthExpenses.filter {
                it.category.contains(query, ignoreCase = true) || it.memo.contains(query, ignoreCase = true)
            }
        }
        categoryFilter?.let { category ->
            filtered = filtered.filter { it.category == category }
        }

        groupedAdapter.submitList(buildGroupedItems(filtered))

        val isFiltering = query.isNotEmpty() || categoryFilter != null
        tvEmptyMessage.text = if (isFiltering && filtered.isEmpty() && currentMonthExpenses.isNotEmpty()) {
            getString(R.string.search_empty_message)
        } else {
            getString(R.string.expense_list_empty_message)
        }
        tvEmptyMessage.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 날짜순(최신순)으로 이미 정렬된 목록을 날짜 헤더 + 그날 지출들로 묶는다. */
    private fun buildGroupedItems(expenses: List<Expense>): List<ExpenseListItem> {
        val items = mutableListOf<ExpenseListItem>()
        var lastDate: String? = null

        expenses.forEach { expense ->
            if (expense.date != lastDate) {
                val dayTotal = expenses.filter { it.date == expense.date }.sumOf { it.amount }
                items.add(ExpenseListItem.Header(formatDateHeader(expense.date), dayTotal))
                lastDate = expense.date
            }
            items.add(ExpenseListItem.Row(expense))
        }

        return items
    }

    /** 총 지출/지난달 비교/예산/카테고리 순위를 화면에 있는 목록으로부터 다시 계산해서 그린다 (DB 재조회 없음). */
    private fun recomputeAndRenderFromCurrentList() {
        val monthTotal = currentMonthExpenses.sumOf { it.amount }
        val categoryTotals = currentMonthExpenses
            .groupBy { it.category }
            .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amount }) }
            .sortedByDescending { it.total }

        animateHeroTotal(monthTotal)
        renderMonthCompare(monthTotal, lastMonthTotalCache)
        renderBudgetStatus(monthTotal)
        renderCategoryStats(categoryTotals, monthTotal)
    }

    /** 히어로 숫자를 이전 값에서 새 값까지 짧게 세어 올라가듯 보여준다. */
    private fun animateHeroTotal(target: Long) {
        heroAnimator?.cancel()
        val start = displayedHeroTotal
        if (start == target) {
            tvHeroAmount.text = getString(R.string.total_amount_format, target)
            return
        }
        // 금액이 실제로 바뀔 때만 옆의 돼지 라벨이 통통 튀게 한다.
        FunAnimations.bounce(tvHeroLabel)
        heroAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val current = start + ((target - start) * fraction).toLong()
                tvHeroAmount.text = getString(R.string.total_amount_format, current)
            }
            start()
        }
        displayedHeroTotal = target
    }

    /** 왼쪽으로 스와이프해서 지운 지출을 즉시 화면/서버에서 지우고, 실행취소 스낵바를 보여준다. */
    private fun deleteWithUndo(expense: Expense) {
        currentMonthExpenses = currentMonthExpenses.filterNot { it.id == expense.id }
        applyFilterAndRender()
        recomputeAndRenderFromCurrentList()

        lifecycleScope.launch { withContext(Dispatchers.IO) { ExpenseRepository.delete(expense) } }

        Snackbar.make(rvExpenses, getString(R.string.expense_deleted_message), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo_button) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ExpenseRepository.add(expense) }
                    refreshList()
                }
            }
            .show()
    }

    /** 이번 달 지출을 지난달과 비교해서 한 줄 문구로 보여준다. */
    private fun renderMonthCompare(monthTotal: Long, lastMonthTotal: Long) {
        val diff = monthTotal - lastMonthTotal
        tvMonthCompare.text = when {
            diff > 0 -> getString(R.string.month_compare_more_format, diff)
            diff < 0 -> getString(R.string.month_compare_less_format, -diff)
            else -> getString(R.string.month_compare_same)
        }
        tvMonthCompare.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    diff > 0 -> R.color.danger
                    diff < 0 -> R.color.safe
                    else -> R.color.text_sub
                }
            )
        )
    }

    /** 예산 대비 사용량을 진행바 + 텍스트로 보여준다. 예산 영역을 탭하면 예산을 바꿀 수 있다. */
    private fun renderBudgetStatus(monthTotal: Long) {
        val budget = BudgetPrefs.getBudget(this)
        tvBudgetValue.text = getString(R.string.budget_progress_format, monthTotal, budget)

        val percent = if (budget > 0) ((monthTotal * 100) / budget).toInt() else 0
        val targetProgress = percent.coerceIn(0, 100)
        ObjectAnimator.ofInt(pbBudget, "progress", pbBudget.progress, targetProgress).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            start()
        }

        val remaining = budget - monthTotal
        if (remaining >= 0) {
            tvBudgetRemaining.text = getString(R.string.budget_remaining_format, remaining)
            tvBudgetRemaining.setTextColor(ContextCompat.getColor(this, R.color.safe))
            pbBudget.progressTintList = ContextCompat.getColorStateList(this, R.color.primary)
        } else {
            tvBudgetRemaining.text = getString(R.string.budget_over_format, -remaining)
            tvBudgetRemaining.setTextColor(ContextCompat.getColor(this, R.color.danger))
            pbBudget.progressTintList = ContextCompat.getColorStateList(this, R.color.danger)
        }
    }

    /** 이번 달 예산 금액을 직접 입력해서 바꾸는 간단한 다이얼로그. */
    private fun showBudgetEditDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            val current = BudgetPrefs.getBudget(this@ExpenseListActivity).toString()
            setText(current)
            setSelection(current.length)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.budget_edit_title)
            .setView(input)
            .setPositiveButton(R.string.budget_save_button) { _, _ ->
                val amount = input.text.toString().toLongOrNull()
                if (amount != null && amount > 0) {
                    BudgetPrefs.setBudget(this, amount)
                    recomputeAndRenderFromCurrentList()
                    FunAnimations.jingle(tvBudgetValue)
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    /** 카테고리별 지출 중 가장 많이 쓴 3개를 막대바와 함께 보여준다. */
    private fun renderCategoryStats(categoryTotals: List<CategoryTotal>, monthTotal: Long) {
        categoryStatsContainer.removeAllViews()

        val top3 = categoryTotals.take(3)
        if (top3.isEmpty() || monthTotal <= 0) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.category_stats_empty)
                setTextColor(ContextCompat.getColor(this@ExpenseListActivity, R.color.text_sub))
                textSize = 12f
            }
            categoryStatsContainer.addView(emptyView)
            return
        }

        top3.forEachIndexed { index, stat ->
            // 카테고리 한 줄을 탭하면 그 카테고리만 아래 목록에서 보이도록 필터링한다(다시 탭하면 해제).
            val isSelected = categoryFilter == stat.category
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(
                    this@ExpenseListActivity,
                    if (isSelected) R.drawable.bg_chip_category else R.drawable.bg_list_item_ripple
                )
                setPadding(dp(8), dp(8) + (if (index > 0) dp(9) else 0), dp(8), dp(8))
                setOnClickListener {
                    TouchPopEffect.pop(it)
                    categoryFilter = if (isSelected) null else stat.category
                    applyFilterAndRender()
                    renderCategoryStats(categoryTotals, monthTotal)
                    if (categoryFilter != null) {
                        Snackbar.make(rvExpenses, getString(R.string.category_filter_active_message, stat.category), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            // 아래 지출 목록의 카테고리 원형 아이콘과 같은 색 점을 찍어서,
            // 카드가 그냥 텍스트 나열이 아니라 목록과 이어지는 느낌을 준다.
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        ContextCompat.getColor(
                            this@ExpenseListActivity,
                            CategoryStyle.backgroundColorRes(stat.category)
                        )
                    )
                }
            }
            labelRow.addView(dot)
            val tvName = TextView(this).apply {
                text = stat.category
                setTextColor(ContextCompat.getColor(this@ExpenseListActivity, R.color.text_main))
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val percent = ((stat.total * 100) / monthTotal).toInt().coerceIn(0, 100)
            val tvAmount = TextView(this).apply {
                text = getString(R.string.category_stat_amount_format, stat.total, percent)
                setTextColor(ContextCompat.getColor(this@ExpenseListActivity, R.color.text_sub))
                textSize = 11.5f
            }
            labelRow.addView(tvName)
            labelRow.addView(tvAmount)

            // 막대는 트랙 위에 채움 뷰를 겹쳐놓고 왼쪽을 축으로 scaleX를 0에서 목표 비율까지
            // 늘려서, 카드가 그려질 때마다 살짝 채워지는 느낌을 준다.
            val barTrack = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(4)
                ).apply { topMargin = dp(4) }
                setBackgroundColor(ContextCompat.getColor(this@ExpenseListActivity, R.color.divider))
            }
            val barFill = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(this@ExpenseListActivity, R.color.primary_dark))
                pivotX = 0f
                scaleX = 0f
            }
            barFill.animate()
                .scaleX(percent.coerceAtLeast(2) / 100f)
                .setStartDelay(index * 90L)
                .setDuration(420L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            barTrack.addView(barFill)

            row.addView(labelRow)
            row.addView(barTrack)
            categoryStatsContainer.addView(row)
        }
    }

    private fun checkBudget() {
        lifecycleScope.launch {
            val monthTotal = withContext(Dispatchers.IO) {
                ExpenseRepository.totalAmountForMonth(actualCurrentYearMonth())
            }
            val budget = BudgetPrefs.getBudget(this@ExpenseListActivity)

            if (monthTotal > budget && NotificationPrefs.isEnabled(this@ExpenseListActivity)) {
                NotificationHelper.showBudgetAlert(this@ExpenseListActivity, monthTotal, budget)
            }
        }
    }

    private fun actualCurrentYearMonth(): String {
        val today = LocalDate.now()
        return "%04d-%02d".format(today.year, today.monthValue)
    }

    private fun previousYearMonth(yearMonth: String): String {
        val current = runCatching { YearMonth.parse(yearMonth) }.getOrDefault(YearMonth.now())
        return current.minusMonths(1).toString()
    }

    /** 오늘이면 "오늘", 아니면 요일 한 글자를 붙여 날짜 헤더 문구를 만든다. */
    private fun formatDateHeader(dateString: String): String {
        val date = runCatching { LocalDate.parse(dateString) }.getOrNull() ?: return dateString
        val dayLabel = if (date == LocalDate.now()) getString(R.string.today_label) else weekdayLabel(date.dayOfWeek)
        return getString(R.string.date_header_format, date.monthValue, date.dayOfMonth, dayLabel)
    }

    private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> getString(R.string.weekday_mon)
        DayOfWeek.TUESDAY -> getString(R.string.weekday_tue)
        DayOfWeek.WEDNESDAY -> getString(R.string.weekday_wed)
        DayOfWeek.THURSDAY -> getString(R.string.weekday_thu)
        DayOfWeek.FRIDAY -> getString(R.string.weekday_fri)
        DayOfWeek.SATURDAY -> getString(R.string.weekday_sat)
        DayOfWeek.SUNDAY -> getString(R.string.weekday_sun)
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
                    text = formatMonthOptionLabel(yearMonth)
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
                        categoryFilter = null
                        refreshList()
                        popupWindow.dismiss()
                    }
                }
                container.addView(row)
            }

            popupWindow.showAsDropDown(monthSelectorContainer, 0, dp(6))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 상단에 조용히 보여줄 짧은 라벨: 올해면 "9월", 아니면 "2025년 9월". */
    private fun formatMonthHeaderLabel(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null) return yearMonth
        return if (year == LocalDate.now().year) {
            getString(R.string.month_header_format, month)
        } else {
            getString(R.string.month_full_format, year, month)
        }
    }

    /** 월 선택 팝업 목록에 쓰는, 연도가 항상 붙는 라벨. */
    private fun formatMonthOptionLabel(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null) return yearMonth
        return getString(R.string.month_full_format, year, month)
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
