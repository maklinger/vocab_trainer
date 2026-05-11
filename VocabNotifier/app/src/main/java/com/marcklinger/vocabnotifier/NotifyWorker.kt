package com.marcklinger.vocabnotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.URL
import android.util.Log

const val CSV_URL      = "https://raw.githubusercontent.com/maklinger/vocab_trainer/refs/heads/main/words.txt"
const val VERSION_URL  = "https://raw.githubusercontent.com/maklinger/vocab_trainer/refs/heads/main/version.txt"
const val CSV_FILE     = "words.csv"
const val STATS_FILE   = "stats.json"
const val VERSION_FILE = "version.txt"

val LANG_FLAGS = mapOf(
    "german"  to "🇩🇪",
    "english" to "🇬🇧",
    "dutch"   to "🇳🇱",
    "spanish" to "🇪🇸"
)

class NotifyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        Log.d("VocabNotifier", "Worker started")
        val filesDir = applicationContext.filesDir
        val csvFile = File(filesDir, CSV_FILE)

        // Step 1: Check version on GitHub every time (tiny request, ~50 bytes)
        try {
            val remoteVersion = URL(VERSION_URL).readText().trim()
            val localVersionFile = File(filesDir, VERSION_FILE)
            val localVersion = if (localVersionFile.exists()) localVersionFile.readText().trim() else ""

            if (remoteVersion != localVersion || !csvFile.exists()) {
                csvFile.writeText(URL(CSV_URL).readText())
                localVersionFile.writeText(remoteVersion)
            }
        } catch (e: Exception) {
            Log.e("VocabNotifier", "Network error: ${e.message}")
            if (!csvFile.exists()) return Result.retry()
        }

        // Step 2: Parse CSV
        // Format: word_id,german,english,dutch,spanish
        val words = mutableListOf<Map<String, String>>()
        csvFile.readLines().drop(1).forEach { line ->
            val parts = line.split(",")
            if (parts.size >= 5) {
                words.add(mapOf(
                    "id"      to parts[0].trim(),
                    "german"  to parts[1].trim(),
                    "english" to parts[2].trim(),
                    "dutch"   to parts[3].trim(),
                    "spanish" to parts[4].trim()
                ))
            }
        }
        if (words.isEmpty()) return Result.failure()

        // Step 3: Load stats
        val statsFile = File(filesDir, STATS_FILE)
        val stats = if (statsFile.exists()) JSONObject(statsFile.readText()) else JSONObject()

        // Step 4: Weighted selection — unseen words prioritised
        val weights = words.map { 1.0 / (stats.optInt(it["id"]!!, 0) + 1) }
        val totalWeight = weights.sum()
        var random = Math.random() * totalWeight
        var selected = words.last()
        for ((index, word) in words.withIndex()) {
            random -= weights[index]
            if (random <= 0) { selected = word; break }
        }

        // Step 5: Update stats
        val id = selected["id"]!!
        stats.put(id, stats.optInt(id, 0) + 1)
        statsFile.writeText(stats.toString())

        // Step 6: Randomise language order and notify
        val languages = listOf("german", "english", "dutch", "spanish").shuffled()
        val titleText = "${LANG_FLAGS[languages[0]]} ${selected[languages[0]]}"
        val bodyText  = languages.drop(1).joinToString("\n") { "${LANG_FLAGS[it]} ${selected[it]}" }

        showNotification(titleText, bodyText)
        return Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "vocab_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(channelId, "Daily Vocabulary", NotificationManager.IMPORTANCE_DEFAULT)
        )

        // PendingIntent that enqueues a new worker when the button is tapped
        val nextIntent = Intent(applicationContext, NotificationActionReceiver::class.java)
        val nextPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_next, "Next word", nextPendingIntent)
            .build()

        manager.notify(1001, notification)
    }
}