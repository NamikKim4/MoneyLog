package com.moneylog.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import java.time.LocalDate

/**
 * ⚙️ 설정 화면.
 * - 예산 수정, 예산 초과 알림 on/off, 데이터 백업(CSV 내보내기), 데이터 초기화, 앱 버전 정보를 모아둔다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var rowEditBudget: LinearLayout
    private lateinit var rowPiggyBank: LinearLayout
    private lateinit var rowNotification: LinearLayout
    private lateinit var swNotification: Switch
    private lateinit var tvNotificationStatus: TextView
    private lateinit var rowBackup: LinearLayout
    private lateinit var rowReset: LinearLayout
    private lateinit var tvAppVersion: TextView

    /**
     * 앱 안의 "알림 받기" 토글을 켰다고 해서 실제로 알림이 뜨는 건 아니다 — 안드로이드 13+에서는
     * 시스템 알림 권한(POST_NOTIFICATIONS)을 따로 허용해줘야 한다. 이 런처는 그 권한을 요청하고,
     * 결과(허용/거부)에 따라 스위치와 상태 문구를 다시 맞춘다.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            NotificationPrefs.setEnabled(this, granted)
            refreshNotificationStatus()
            if (!granted) {
                Snackbar.make(
                    findViewById(R.id.settingsRoot),
                    getString(R.string.settings_notification_permission_denied_message),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.settings_notification_open_settings_action) {
                    openAppNotificationSettings()
                }.show()
            }
        }

    /** 스위치를 프로그램적으로 켜고 끌 때 리스너가 다시 불려서 꼬이지 않도록, 리스너를 따로 이름 붙여둔다. */
    private val notificationCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked && !hasSystemNotificationPermission()) {
            // 앱 토글은 켰지만 휴대폰 알림 권한이 없으면, 켜지는 걸 그대로 저장하지 않고
            // 먼저 권한부터 확보한다(안드로이드 13+는 권한 요청, 그 이전 버전은 시스템 설정으로 안내).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings()
            }
            refreshNotificationStatus()
        } else {
            NotificationPrefs.setEnabled(this, isChecked)
            refreshNotificationStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rowEditBudget = findViewById(R.id.rowEditBudget)
        rowPiggyBank = findViewById(R.id.rowPiggyBank)
        rowNotification = findViewById(R.id.rowNotification)
        swNotification = findViewById(R.id.swNotification)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        rowBackup = findViewById(R.id.rowBackup)
        rowReset = findViewById(R.id.rowReset)
        tvAppVersion = findViewById(R.id.tvAppVersion)

        tvAppVersion.text = getString(R.string.version_text, BuildConfig.VERSION_NAME)

        rowEditBudget.setOnClickListener { showBudgetEditDialog() }
        rowPiggyBank.setOnClickListener { startActivity(Intent(this, PiggyBankActivity::class.java)) }

        refreshNotificationStatus()
        swNotification.setOnCheckedChangeListener(notificationCheckedChangeListener)
        rowNotification.setOnClickListener {
            // 이미 켜놓았는데 알림 권한만 막혀 있는 상태라면, 스위치를 다시 눌러봤자 소용없으니
            // 바로 시스템 설정으로 보내준다. 그 외엔 평소처럼 토글한다.
            if (NotificationPrefs.isEnabled(this) && !hasSystemNotificationPermission()) {
                openAppNotificationSettings()
            } else {
                swNotification.toggle()
            }
        }

        rowBackup.setOnClickListener { exportBackup() }
        rowReset.setOnClickListener { confirmResetData() }
    }

    override fun onResume() {
        super.onResume()
        // 시스템 알림 설정 화면에 다녀왔을 수도 있으니, 화면에 다시 보일 때마다 최신 상태로 갱신한다.
        if (::tvNotificationStatus.isInitialized) {
            refreshNotificationStatus()
        }
    }

    private fun hasSystemNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    /** 안드로이드 8+에서는 앱별 알림 설정 화면으로, 그 이전 버전에서는 앱 정보 화면으로 보낸다. */
    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    /**
     * 스위치 상태와 상태 문구를 한 번에 맞춘다.
     * - 앱 토글도 켜져 있고 시스템 알림 권한도 있으면: 켜짐
     * - 앱 토글은 켜놨는데 시스템 알림 권한이 없으면: 실제로는 알림이 안 가니, 경고 문구를 보여준다
     * - 앱 토글이 꺼져 있으면: 꺼짐
     */
    private fun refreshNotificationStatus() {
        val appEnabled = NotificationPrefs.isEnabled(this)
        val systemGranted = hasSystemNotificationPermission()
        val effectivelyOn = appEnabled && systemGranted

        // 프로그램적으로 값을 바꾸는 동안엔 리스너를 잠깐 떼어내서, 리스너가 또 불려 값이 꼬이는 걸 막는다.
        swNotification.setOnCheckedChangeListener(null)
        swNotification.isChecked = effectivelyOn
        swNotification.setOnCheckedChangeListener(notificationCheckedChangeListener)

        val blockedByPermission = appEnabled && !systemGranted
        tvNotificationStatus.text = getString(
            when {
                effectivelyOn -> R.string.settings_notification_on_desc
                blockedByPermission -> R.string.settings_notification_permission_off_desc
                else -> R.string.settings_notification_off_desc
            }
        )
        tvNotificationStatus.setTextColor(
            ContextCompat.getColor(this, if (blockedByPermission) R.color.danger else R.color.text_sub)
        )
    }

    private fun showBudgetEditDialog() {
        val currentYearMonth = LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            val current = BudgetPrefs.getBudget(this@SettingsActivity, currentYearMonth).toString()
            setText(current)
            setSelection(current.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.budget_edit_title)
            .setView(input)
            .setPositiveButton(R.string.budget_save_button) { _, _ ->
                val amount = input.text.toString().toLongOrNull()
                if (amount != null && amount > 0) {
                    BudgetPrefs.setBudget(this, currentYearMonth, amount)
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
                // "text/csv" 같은 "text/*" 타입은 카카오톡 등 일부 앱이 "텍스트 공유"로 오해해서
                // 실제 파일(EXTRA_STREAM)이 아니라 텍스트(EXTRA_TEXT)를 찾다가 아무것도 못 찾고
                // 파일을 빼먹는 문제가 있었다. 범용 바이너리 타입으로 바꿔서 "파일 첨부"로 확실히 인식하게 한다.
                type = "application/octet-stream"
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
                // 엑셀이 UTF-8로 열도록 맨 앞에 BOM을 붙인다. 이게 없으면 한글(카테고리/메모)이 깨져 보일 수 있다.
                writer.write("\uFEFF")
                writer.write("date,category,amount,memo")
                writer.newLine()
                expenses.forEach { expense ->
                    val safeMemo = expense.memo.replace("\"", "\"\"")
                    // 날짜를 그냥 "2026-09-04"처럼 쓰면, 엑셀이 이걸 멋대로 날짜 값으로 해석해서
                    // 지역 설정에 따라 형식이 바뀌거나 심하면 월/일이 뒤바뀌어 보이는 문제가 있었다.
                    // ="2026-09-04" 형태로 감싸면 엑셀이 이 칸을 그대로(있는 그대로) 글자로만 표시한다.
                    writer.write("=\"${expense.date}\",\"${expense.category}\",${expense.amount},\"$safeMemo\"")
                    writer.newLine()
                }
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            // IOException(파일 쓰기 실패)뿐 아니라, FileProvider 경로 설정이 어긋났을 때 나는
            // IllegalArgumentException까지 여기서 잡아서, 앱이 죽는 대신 에러 메시지만 보여준다.
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
