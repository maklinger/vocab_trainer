package com.marcklinger.vocabnotifier

import android.app.Activity
import android.os.Bundle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = OneTimeWorkRequestBuilder<NotifyWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
        finish()
    }
}