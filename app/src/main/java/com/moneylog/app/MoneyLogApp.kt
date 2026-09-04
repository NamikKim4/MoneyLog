package com.moneylog.app

import android.app.Application
import com.moneylog.app.common.NotificationHelper
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.data.MemoRepository

class MoneyLogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        ExpenseRepository.init(this)
        MemoRepository.init(this)
    }
}
