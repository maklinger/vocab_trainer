package com.marcklinger.vocabnotifier

import android.app.Application
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AlarmScheduler.schedule(this)

        // TEMPORARY TEST — remove before final use
        val testRequest = OneTimeWorkRequestBuilder<NotifyWorker>().build()
        WorkManager.getInstance(this).enqueue(testRequest)
    }
}