package com.moneylog.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.util.Locale

/**
 * 지출 한 건을 입력하는 화면.
 * 날짜는 직접 타이핑하지 않고, 필드를 탭하면 캘린더 팝업(DatePickerDialog)에서 골라 채운다.
 * 카테고리는 프리셋 목록을 드롭다운에서 고르거나, 목록에 없는 이름을 직접 타이핑해서 등록할 수 있다.
 * 금액은 시스템 키보드 대신 머니로그 전용 키패드(00/0/빠른 금액 추가 포함)로 입력한다.
 * 저장 버튼을 누르면 결과를 Intent에 담아 이전 화면(ExpenseListActivity)으로 돌려준다.
 */
class AddExpenseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "EXTRA_DATE"
        const val EXTRA_CATEGORY = "EXTRA_CATEGORY"
        const val EXTRA_AMOUNT = "EXTRA_AMOUNT"
        const val EXTRA_MEMO = "EXTRA_MEMO"
        const val EXTRA_PREFILL_DATE = "EXTRA_PREFILL_DATE"

        private const val MAX_AMOUNT = 999_999_999_999L

        fun newIntent(context: Context, prefillDate: String? = null): Intent {
            return Intent(context, AddExpenseActivity::class.java).apply {
                if (prefillDate != null) {
                    putExtra(EXTRA_PREFILL_DATE, prefillDate)
                }
            }
        }
    }

    private lateinit var etDate: EditText
    private lateinit var etCategory: AutoCompleteTextView
    private lateinit var etAmount: EditText
    private lateinit var etMemo: EditText
    private lateinit var btnSave: Button

    private lateinit var keypadContainer: LinearLayout

    private var rawAmount: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_expense)

        etDate = findViewById(R.id.etDate)
        etCategory = findViewById(R.id.etCategory)
        etAmount = findViewById(R.id.etAmount)
        etMemo = findViewById(R.id.etMemo)
        btnSave = findViewById(R.id.btnSave)
        keypadContainer = findViewById(R.id.keypadContainer)

        etDate.setText(intent.getStringExtra(EXTRA_PREFILL_DATE) ?: LocalDate.now().toString())

        // 키보드로 직접 입력하지 못하게 막고, 탭하면 캘린더 팝업이 뜨도록 한다.
        etDate.isFocusable = false
        etDate.isFocusableInTouchMode = false
        etDate.setOnClickListener {
            hideKeypad()
            showDatePicker()
        }

        setupCategoryDropdown()
        setupAmountKeypad()

        etMemo.setOnClickListener { hideKeypad() }
        etMemo.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hideKeypad() }

        btnSave.setOnClickListener { onSaveClicked() }
    }

    /** 카테고리 드롭다운 + 직접 입력 설정. */
    private fun setupCategoryDropdown() {
        val categories = resources.getStringArray(R.array.category_presets)
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_category, R.id.tvCategoryItem, categories)
        etCategory.setAdapter(adapter)
        etCategory.threshold = 0

        etCategory.setOnClickListener {
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

    /** 머니로그 전용 금액 키패드 설정 (시스템 키보드 대신 사용). */
    private fun setupAmountKeypad() {
        // 시스템 키보드가 뜨지 않도록 inputType을 TYPE_NULL로 막고, 커스텀 키패드로만 입력받는다.
        etAmount.inputType = InputType.TYPE_NULL
        etAmount.isFocusable = true
        etAmount.isFocusableInTouchMode = true
        etAmount.isCursorVisible = false
        etAmount.isLongClickable = false
        updateAmountDisplay()

        etAmount.setOnClickListener { showKeypad() }
        etAmount.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showKeypad() }

        val digitButtons = mapOf(
            R.id.btnKey1 to 1L, R.id.btnKey2 to 2L, R.id.btnKey3 to 3L,
            R.id.btnKey4 to 4L, R.id.btnKey5 to 5L, R.id.btnKey6 to 6L,
            R.id.btnKey7 to 7L, R.id.btnKey8 to 8L, R.id.btnKey9 to 9L,
            R.id.btnKey0 to 0L
        )

        digitButtons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener { appendDigit(digit) }
        }

        findViewById<Button>(R.id.btnKey00).setOnClickListener { appendDouble() }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { backspaceAmount() }
        findViewById<Button>(R.id.btnClearAmount).setOnClickListener { clearAmount() }

        findViewById<Button>(R.id.btnQuick1000).setOnClickListener { addQuickAmount(1_000L) }
        findViewById<Button>(R.id.btnQuick10000).setOnClickListener { addQuickAmount(10_000L) }
        findViewById<Button>(R.id.btnQuick100000).setOnClickListener { addQuickAmount(100_000L) }

        findViewById<Button>(R.id.btnDoneAmount).setOnClickListener { hideKeypad() }
    }

    private fun appendDigit(digit: Long) {
        val next = rawAmount * 10 + digit
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    private fun appendDouble() {
        if (rawAmount == 0L) return
        val next = rawAmount * 100
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    private fun backspaceAmount() {
        rawAmount /= 10
        updateAmountDisplay()
    }

    private fun clearAmount() {
        rawAmount = 0L
        updateAmountDisplay()
    }

    private fun addQuickAmount(amount: Long) {
        val next = rawAmount + amount
        if (next <= MAX_AMOUNT) {
            rawAmount = next
            updateAmountDisplay()
        }
    }

    private fun updateAmountDisplay() {
        val formatted = String.format(Locale.KOREA, "%,d", rawAmount)
        etAmount.setText(formatted)
        etAmount.setSelection(formatted.length)
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
        }

        setResult(RESULT_OK, result)
        finish()
    }
}
