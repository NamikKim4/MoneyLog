package com.moneylog.app.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moneylog.app.R

/**
 * 예산 초과 알림을 만들고 보여주는 역할을 모아둔 헬퍼.
 */
object NotificationHelper {

    const val CHANNEL_ID = "budget_alert_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showBudgetAlert(context: Context, totalAmount: Long, budget: Long) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_budget_title))
            .setContentText(context.getString(R.string.notification_budget_message, totalAmount, budget))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // 알림 권한이 없는 경우 (Android 13+에서 거부됨) 조용히 무시한다.
            e.printStackTrace()
        }
    }
}
