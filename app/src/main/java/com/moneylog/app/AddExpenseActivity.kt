package com.moneylog.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/**
 * 지출 한 건을 입력(또는 수정)하는 화면.
 * 날짜는 직접 타이핑하지 않고, 필드를 탭하면 캘린더 팝업(DatePickerDialog)에서 골라 채운다.
 * 카테고리는 프리셋 목록을 드롭다운에서 고르거나, 목록에 없는 이름을 직접 타이핑해서 등록할 수 있다.
 * 금액은 시스템 키보드 대신 머니로그 전용 키패드(00/0/빠른 금액 추가 포함)로 입력한다.
 * 자주 등록했던 (카테고리, 금액) 조합은 화면 위쪽에 칩으로 떠서 탭 한 번으로 바로 채워 넣을 수 있다.
 * newIntentForEdit로 열렸을 때는 기존 지출 내용을 미리 채워주고, 우측 상단에 삭제 버튼도 보여준다.
 * 저장(또는 수정 완료) 버튼을 누르면 결과를 Intent에 담아 이전 화면으로 돌려준다.
 */
class AddExpenseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "EXTRA_DATE"
        const val EXTRA_CATEGORY = "EXTRA_CATEGORY"
        const val EXTRA_AMOUNT = "EXTRA_AMOUNT"
        const val EXTRA_MEMO = "EXTRA_MEMO"
        const val EXTRA_PREFILL_DATE = "EXTRA_PREFILL_DATE"

        /** 결과 Intent에 이 값이 들어있으면 "새로 추가"가 아니라 "이 id의 기존 지출을 수정"이라는 뜻. */
        const val EXTRA_EDIT_ID = "EXTRA_EDIT_ID"

        /** 결과 Intent에 이 값이 들어있으면, 다른 필드는 무시하고 이 id의 지출을 삭제하라는 뜻. */
        const val EXTRA_DELETE_ID = "EXTRA_DELETE_ID"

        private const val NO_ID = -1L
        private const val MAX_AMOUNT = 999_999_999_999L
        private const val QUICK_ENTRY_LIMIT = 6

        fun newIntent(context: Context, prefillDate: String? = null): Intent {
            return Intent(context, AddExpenseActivity::class.java).apply {
                if (prefillDate != null) {
                    putExtra(EXTRA_PREFILL_DATE, prefillDate)
                }
            }
        }

        /** 지출 목록/캘린더에서 항목을 탭했을 때, 그 내용을 미리 채운 "수정 모드"로 연다. */
        fun newIntentForEdit(context: Context, expense: Expense): Intent {
            return Intent(context, AddExpenseActivity::class.java).apply {
                putExtra(EXTRA_EDIT_ID, expense.id)
                putExtra(EXTRA_DATE, expense.date)
                putExtra(EXTRA_CATEGORY, expense.category)
                putExtra(EXTRA_AMOUNT, expense.amount)
                putExtra(EXTRA_MEMO, expense.memo)
            }
        }
    }

    private lateinit var etDate: EditText
    private lateinit var etCategory: AutoCompleteTextView
    private lateinit var etAmount: EditText
    private lateinit var etMemo: EditText
    private lateinit var btnSave: Button

    private lateinit var keypadContainer: LinearLayout
    private lateinit var tvQuickEntryLabel: TextView
    private lateinit var quickEntryScroll: HorizontalScrollView
    private lateinit var quickEntryContainer: LinearLayout

    private lateinit var tvTitle: TextView
    private lateinit var tvDeleteExpense: TextView

    /** null이면 새 지출 등록, 값이 있으면 그 id의 기존 지출을 수정하는 중이라는 뜻. */
    private var editingId: Long? = null

    private var rawAmount: Long = 0L

    /**
     * 수정 모드로 들어오면 기존 금액이 미리 채워져 있는데, 그 상태에서 바로 숫자를 누르면
     * 기존 금액 뒤에 그대로 덧붙어서 이상한 값이 되기 쉽다. 그래서 수정 화면을 열면 이 값을
     * true로 켜두고, 사용자가 숫자패드를 처음 조작하는 순간 기존 금액을 0으로 비운 뒤 새로
     * 입력을 시작하게 한다 — 새 지출 등록 때 "0"이 안 보이고 바로 입력하기 편한 것과 같은 느낌.
     */
    private var clearAmountOnNextInput = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_expense)

        tvTitle = findViewById(R.id.tvAddExpenseTitle)
        tvDeleteExpense = findViewById(R.id.tvDeleteExpense)
        etDate = findViewById(R.id.etDate)
        etCategory = findViewById(R.id.etCategory)
        etAmount = findViewById(R.id.etAmount)
        etMemo = findViewById(R.id.etMemo)
        btnSave = findViewById(R.id.btnSave)
        keypadContainer = findViewById(R.id.keypadContainer)
        tvQuickEntryLabel = findViewById(R.id.tvQuickEntryLabel)
        quickEntryScroll = findViewById(R.id.quickEntryScroll)
        quickEntryContainer = findViewById(R.id.quickEntryContainer)

        editingId = intent.getLongExtra(EXTRA_EDIT_ID, NO_ID).takeIf { it != NO_ID }

        if (editingId != null) {
            // 수정 모드: 탭해서 들어온 그 지출의 내용을 그대로 채워준다.
            tvTitle.setText(R.string.edit_expense_title)
            btnSave.setText(R.string.update_button)
            etDate.setText(intent.getStringExtra(EXTRA_DATE) ?: LocalDate.now().toString())
            rawAmount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
            clearAmountOnNextInput = true
            etMemo.setText(intent.getStringExtra(EXTRA_MEMO).orEmpty())

            tvDeleteExpense.visibility = View.VISIBLE
            tvDeleteExpense.setOnClickListener {
                TouchPopEffect.pop(it)
                confirmDeleteExpense()
            }
        } else {
            etDate.setText(intent.getStringExtra(EXTRA_PREFILL_DATE) ?: LocalDate.now().toString())
        }

        // 키보드로 직접 입력하지 못하게 막고, 탭하면 캘린더 팝업이 뜨도록 한다.
        etDate.isFocusable = false
        etDate.isFocusableInTouchMode = false
        etDate.setOnClickListener {
            TouchPopEffect.pop(it)
            hideKeypad()
            showDatePicker()
        }

        setupCategoryDropdown()
        setupAmountKeypad()
        loadQuickEntries()

        // 카테고리는 AutoCompleteTextView라 어댑터가 붙은 뒤에 채워야 드롭다운 필터링이
        // 엉뚱하게 발동하지 않는다.
        editingId?.let {
            etCategory.setText(intent.getStringExtra(EXTRA_CATEGORY).orEmpty(), false)
        }

        etMemo.setOnClickListener { hideKeypad() }
        etMemo.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hideKeypad() }

        btnSave.setOnClickListener { onSaveClicked() }

        // 화면에 들어오면 돼지가 한 번 통통 튀며 반겨주는 느낌을 준다.
        tvTitle.postDelayed({ FunAnimations.bounce(tvTitle) }, 150L)
    }

    /** 수정 화면에서 삭제를 누르면 한 번 더 확인하고, 확인하면 결과를 delete로 표시해 돌려준다. */
    private fun confirmDeleteExpense() {
        val id = editingId ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                val result = Intent().apply { putExtra(EXTRA_DELETE_ID, id) }
                setResult(RESULT_OK, result)
                finish()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    /** 카테고리 드롭다운 + 직접 입력 설정. */
    private fun setupCategoryDropdown() {
        val categories = resources.getStringArray(R.array.category_presets)
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_category, R.id.tvCategoryItem, categories)
        etCategory.setAdapter(adapter)
        etCategory.threshold = 0

        etCategory.setOnClickListener {
            TouchPopEffect.pop(it)
            hideKeypad()
            etCategory.showDropDown()
        }
        etCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                hideKeypad()
                etCategory.post { etCategory.showDropDown() }
            }
        }
    }

    /**
     * 예전에 자주 등록했던 (카테고리, 금액) 조합을 칩으로 보여준다.
     * 기록이 없으면(첫 사용 등) 아무것도 표시하지 않는다.
     */
    private fun loadQuickEntries() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { ExpenseRepository.getFrequentEntries(QUICK_ENTRY_LIMIT) }
            if (entries.isEmpty()) return@launch

            tvQuickEntryLabel.visibility = View.VISIBLE
            quickEntryScroll.visibility = View.VISIBLE
            quickEntryContainer.removeAllViews()

            entries.forEach { entry ->
                val chip = TextView(this@AddExpenseActivity).apply {
                    text = getString(R.string.quick_entry_chip_format, entry.category, entry.amount)
                    setBackgroundResource(R.drawable.bg_chip_category)
                    setTextColor(ContextCompat.getColor(this@AddExpenseActivity, R.color.primary_dark))
                    textSize = 13f
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(8) }
                    setOnClickListener {
                        TouchPopEffect.pop(it)
                        FunAnimations.jingle(it)
                        hideKeypad()
                        etCategory.setText(entry.category)
                        clearAmountOnNextInput = false
                        rawAmount = entry.amount
                        updateAmountDisplay()
                    }
                }
                quickEntryContainer.addView(chip)
            }
        }
    }

    /** 머니로그 전용 금액 키패드 설정 (시스템 키보드 대신 사용). */
    private fun setupAmountKeypad() {
        // 시스템 키보드가 뜨지 않도록 inputType을 TYPE_NULL로 막고, 커스텀 키패드로만 입력받는다.
        // inputType만으로는 갤럭시 자체 키보드가 (특히 메모 칸 등 다른 입력창에서 넘어올 때)
        // 완전히 내려가지 않는 경우가 있어서, showSoftInputOnFocus로 아예 요청 자체를 막고
        // 혹시 이미 떠 있는 시스템 키보드는 탭할 때 직접 내려준다.
        etAmount.inputType = InputType.TYPE_NULL
        etAmount.showSoftInputOnFocus = false
        etAmount.isFocusable = true
        etAmount.isFocusableInTouchMode = true
        etAmount.isCursorVisible = false
        etAmount.isLongClickable = false
        updateAmountDisplay()

        etAmount.setOnClickListener { activateAmountField() }
        etAmount.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activateAmountField() }

        val digitButtons = mapOf(
            R.id.btnKey1 to 1L, R.id.btnKey2 to 2L, R.id.btnKey3 to 3L,
            R.id.btnKey4 to 4L, R.id.btnKey5 to 5L, R.id.btnKey6 to 6L,
            R.id.btnKey7 to 7L, R.id.btnKey8 to 8L, R.id.btnKey9 to 9L,
            R.id.btnKey0 to 0L
        )

        digitButtons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                TouchPopEffect.pop(it)
                appendDigit(digit)
            }
        }

        findViewById<Button>(R.id.btnKey00).setOnClickListener {
            TouchPopEffect.pop(it)
            appendDouble()
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            TouchPopEffect.pop(it)
            backspaceAmount()
        }
        findViewById<Button>(R.id.btnClearAmount).setOnClickListener {
            TouchPopEffect.pop(it)
            clearAmount()
        }

        findViewById<Button>(R.id.btnQuick1000).setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.jingle(it)
            addQuickAmount(1_000L)
        }
        findViewById<Button>(R.id.btnQuick10000).setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.jingle(it)
            addQuickAmount(10_000L)
        }
        findViewById<Button>(R.id.btnQuick100000).setOnClickListener {
            TouchPopEffect.pop(it)
            FunAnimations.jingle(it)
            addQuickAmount(100_000L)
        }

        findViewById<Button>(R.id.btnDoneAmount).setOnClickListener {
            TouchPopEffect.pop(it)
            hideKeypad()
        }
    }

    /** 수정 모드에서 숫자패드를 처음 조작하는 순간, 미리 채워둔 기존 금액을 비워 새로 입력할 수 있게 한다. */
    private fun consumeClearOnFirstInput() {
        if (clearAmountOnNextInput) {
            rawAmount = 0L
            clearAmountOnNextInput = false
        }
    }

    private fun appendDigit(digit: Long) {
        consumeClearOnFirstInput()
        val next = rawAmount * 10 + digit
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    private fun appendDouble() {
        consumeClearOnFirstInput()
        if (rawAmount == 0L) return
        val next = rawAmount * 100
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    private fun backspaceAmount() {
        consumeClearOnFirstInput()
        rawAmount /= 10
        updateAmountDisplay()
    }

    private fun clearAmount() {
        clearAmountOnNextInput = false
        rawAmount = 0L
        updateAmountDisplay()
    }

    private fun addQuickAmount(amount: Long) {
        consumeClearOnFirstInput()
        val next = rawAmount + amount
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    /**
     * 금액이 0이면(아직 아무것도 안 눌렀거나, 지우기로 다 지운 경우) 굳이 "0"을 보여주지 않고
     * 비워둬서 힌트("금액 (원)")가 보이게 한다 — 처음부터 "0"이 떡하니 박혀 있으면
     * 지워야 할 것처럼 보여서 입력을 시작하기 불편하기 때문.
     */
    private fun updateAmountDisplay() {
        if (rawAmount <= 0L) {
            etAmount.setText("")
            return
        }
        val formatted = String.format(Locale.KOREA, "%,d", rawAmount)
        etAmount.setText(formatted)
        etAmount.setSelection(formatted.length)
    }

    /**
     * 금액 칸을 탭하거나 포커스가 들어오면: 혹시 다른 칸(메모 등)에서 넘어와 아직 떠 있는
     * 시스템 키보드가 있으면 내려주고, 앱 전용 키패드를 띄운다. 수정 모드로 들어와서 기존
     * 금액이 미리 채워져 있는 상태라면, 굳이 지우개 버튼을 눌러 지울 필요 없이 여기서 바로
     * 0으로 비워서 새 금액을 바로 입력할 수 있게 한다.
     */
    private fun activateAmountField() {
        hideSystemKeyboard()
        showKeypad()
        if (clearAmountOnNextInput) {
            consumeClearOnFirstInput()
            updateAmountDisplay()
        }
    }

    private fun hideSystemKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etAmount.windowToken, 0)
    }

    private fun showKeypad() {
        keypadContainer.visibility = View.VISIBLE
    }

    private fun hideKeypad() {
        keypadContainer.visibility = View.GONE
    }

    private fun showDatePicker() {
        val current = runCatching { LocalDate.parse(etDate.text.toString().trim()) }
            .getOrDefault(LocalDate.now())

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // DatePickerDialog의 month는 0부터 시작(1월=0)하므로 +1 보정
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                etDate.setText(picked.toString())
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    private fun onSaveClicked() {
        val date = etDate.text.toString().trim()
        val category = etCategory.text.toString().trim()
        val memo = etMemo.text.toString().trim()

        if (date.isEmpty() || runCatching { LocalDate.parse(date) }.isFailure) {
            Toast.makeText(this, getString(R.string.input_date_message), Toast.LENGTH_SHORT).show()
            return
        }

        if (category.isEmpty()) {
            Toast.makeText(this, getString(R.string.input_category_message), Toast.LENGTH_SHORT).show()
            return
        }

        if (rawAmount <= 0) {
            Toast.makeText(this, getString(R.string.input_amount_message), Toast.LENGTH_SHORT).show()
            return
        }

        val result = Intent().apply {
            putExtra(EXTRA_DATE, date)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_AMOUNT, rawAmount)
            putExtra(EXTRA_MEMO, memo)
            editingId?.let { putExtra(EXTRA_EDIT_ID, it) }
        }

        // 저금통에 동전이 들어가는 느낌으로 버튼이 한 번 튀어 오른 다음 화면을 닫는다.
        btnSave.isEnabled = false
        TouchPopEffect.pop(btnSave)
        FunAnimations.bounce(btnSave)
        btnSave.postDelayed({
            setResult(RESULT_OK, result)
            finish()
        }, 260L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
