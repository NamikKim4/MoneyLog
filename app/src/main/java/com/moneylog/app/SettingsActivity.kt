package com.moneylog.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.moneylog.app.common.BudgetPrefs
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.NotificationPrefs
import com.moneylog.app.common.SavingsPrefs
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.data.MemoRepository
import com.moneylog.app.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * ⚙️ 설정 화면.
 * - 예산 수정, 예산 초과 알림 on/off, 데이터 백업(CSV 내보내기), 데이터 초기화, 앱 버전 정보를 모아둔다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var rowEditBudget: LinearLayout
    private lateinit var rowNotification: LinearLayout
    private lateinit var swNotification: Switch
    private lateinit var rowBackup: LinearLayout
    private lateinit var rowReset: LinearLayout
    private lateinit var tvAppVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rowEditBudget = findViewById(R.id.rowEditBudget)
        rowNotification = findViewById(R.id.rowNotification)
        swNotification = findViewById(R.id.swNotification)
        rowBackup = findViewById(R.id.rowBackup)
        rowReset = findViewById(R.id.rowReset)
        tvAppVersion = findViewById(R.id.tvAppVersion)

        tvAppVersion.text = getString(R.string.version_text, BuildConfig.VERSION_NAME)

        rowEditBudget.setOnClickListener { showBudgetEditDialog() }

        swNotification.isChecked = NotificationPrefs.isEnabled(this)
        rowNotification.setOnClickListener { swNotification.toggle() }
        swNotification.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setEnabled(this, isChecked)
        }

        rowBackup.setOnClickListener { exportBackup() }
        rowReset.setOnClickListener { confirmResetData() }
    }

    private fun showBudgetEditDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            val current = BudgetPrefs.getBudget(this@SettingsActivity).toString()
            setText(current)
            setSelection(current.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.budget_edit_title)
            .setView(input)
            .setPositiveButton(R.string.budget_save_button) { _, _ ->
                val amount = input.text.toString().toLongOrNull()
                if (amount != null && amount > 0) {
                    BudgetPrefs.setBudget(this, amount)
                    FunAnimations.jingle(rowEditBudget)
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    /** 지출 내역을 CSV 파일로 만들어서, 다른 앱(메일/드라이브 등)으로 공유할 수 있게 한다. */
    private fun exportBackup() {
        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) { ExpenseRepository.getAll() }
            if (expenses.isEmpty()) {
                showSnackbar(R.string.settings_backup_no_data_message)
                return@launch
            }

            val uri = withContext(Dispatchers.IO) { writeBackupCsv(expenses) }
            if (uri == null) {
                showSnackbar(R.string.settings_backup_error_message)
                return@launch
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_backup_row)))
        }
    }

    private fun writeBackupCsv(expenses: List<Expense>): Uri? {
        return try {
            val dir = File(cacheDir, "backups").apply { mkdirs() }
            val file = File(dir, "moneylog_backup_${LocalDate.now()}.csv")
            file.bufferedWriter().use { writer ->
                writer.write("date,category,amount,memo")
                writer.newLine()
                expenses.forEach { expense ->
                    val safeMemo = expense.memo.replace("\"", "\"\"")
                    writer.write("${expense.date},\"${expense.category}\",${expense.amount},\"$safeMemo\"")
                    writer.newLine()
                }
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /** 등록된 지출/메모를 전부 지운다. 되돌릴 수 없는 작업이라 한 번 더 확인한다. */
    private fun confirmResetData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset_confirm_title)
            .setMessage(R.string.settings_reset_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ExpenseRepository.deleteAll()
                        MemoRepository.deleteAll()
                    }
                    showSnackbar(R.string.settings_reset_done_message)
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun showSnackbar(messageRes: Int) {
        Snackbar.make(findViewById(R.id.settingsRoot), getString(messageRes), Snackbar.LENGTH_SHORT).show()
    }
}
