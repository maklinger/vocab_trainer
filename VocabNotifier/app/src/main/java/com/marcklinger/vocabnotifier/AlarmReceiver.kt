package com.marcklinger.vocabnotifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.schedule(context)
            return
        }
        val request = OneTimeWorkRequestBuilder<NotifyWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}