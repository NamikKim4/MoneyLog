package com.moneylog.app.common

import android.content.Context

/** 설정 화면의 "예산 초과 알림" 토글 값을 저장해두는 저장소. 기본값은 켜짐(true). */
object NotificationPrefs {

    private const val PREFS_NAME = "moneylog_prefs"
    private const val KEY_ENABLED = "notification_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
