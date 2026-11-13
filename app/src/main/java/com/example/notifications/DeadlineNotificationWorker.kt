package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import java.util.concurrent.TimeUnit

object NotificationHelper {
  const val CHANNEL_DEADLINES = "studypulse_deadlines"
  const val CHANNEL_TIMER = "studypulse_timer"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val deadlineChannel =
        NotificationChannel(
            CHANNEL_DEADLINES,
            "Assignment & Exam Deadlines",
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply { description = "Reminders before assignment and exam deadlines" }
      val timerChannel =
        NotificationChannel(
            CHANNEL_TIMER,
            "Focus Timer Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply { description = "Pomodoro focus and break completion alerts" }
      manager.createNotificationChannel(deadlineChannel)
      manager.createNotificationChannel(timerChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun showNotification(
    context: Context,
    channelId: String,
    notificationId: Int,
    title: String,
    body: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context)
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }
}

class DeadlineNotificationWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
    val title = inputData.getString(KEY_TITLE) ?: "Upcoming Study Deadline"
    val subject = inputData.getString(KEY_SUBJECT) ?: "StudyPulse"
    val typeLabel = inputData.getString(KEY_TYPE_LABEL) ?: "Task"
    val leadLabel = inputData.getString(KEY_LEAD_LABEL) ?: "soon"

    NotificationHelper.showNotification(
      context = applicationContext,
      channelId = NotificationHelper.CHANNEL_DEADLINES,
      notificationId = taskId.hashCode(),
      title = "$typeLabel Due $leadLabel: $title",
      body = "$subject • Tap to review or start a Pomodoro focus session.",
    )
    return Result.success()
  }

  companion object {
    const val KEY_TASK_ID = "task_id"
    const val KEY_TITLE = "title"
    const val KEY_SUBJECT = "subject"
    const val KEY_TYPE_LABEL = "type_label"
    const val KEY_LEAD_LABEL = "lead_label"
  }
}

object DeadlineReminderScheduler {
  private fun workName(taskId: String) = "deadline_reminder_$taskId"

  fun scheduleTaskReminder(
    context: Context,
    taskId: String,
    title: String,
    subjectName: String,
    typeLabel: String,
    dueAtMillis: Long,
    leadMinutes: Long,
  ) {
    if (leadMinutes <= 0L) {
      cancelTaskReminder(context, taskId)
      return
    }
    val triggerAtMillis = dueAtMillis - TimeUnit.MINUTES.toMillis(leadMinutes)
    val delayMillis = triggerAtMillis - System.currentTimeMillis()
    if (delayMillis <= 0L) {
      // If due soon and still in the future, schedule a short 5s heads-up only if dueAtMillis > now
      if (dueAtMillis <= System.currentTimeMillis()) {
        cancelTaskReminder(context, taskId)
        return
      }
    }
    val effectiveDelayMs = maxOf(delayMillis, 5_000L)
    val leadLabel =
      when {
        leadMinutes >= 1440L -> "in ${leadMinutes / 1440L}d"
        leadMinutes >= 60L -> "in ${leadMinutes / 60L}h"
        else -> "in ${leadMinutes}m"
      }
    val data =
      Data.Builder()
        .putString(DeadlineNotificationWorker.KEY_TASK_ID, taskId)
        .putString(DeadlineNotificationWorker.KEY_TITLE, title)
        .putString(DeadlineNotificationWorker.KEY_SUBJECT, subjectName.ifBlank { "StudyPulse" })
        .putString(DeadlineNotificationWorker.KEY_TYPE_LABEL, typeLabel)
        .putString(DeadlineNotificationWorker.KEY_LEAD_LABEL, leadLabel)
        .build()

    val request =
      OneTimeWorkRequestBuilder<DeadlineNotificationWorker>()
        .setInitialDelay(effectiveDelayMs, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .build()

    WorkManager.getInstance(context)
      .enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, request)
  }

  fun cancelTaskReminder(context: Context, taskId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
  }
}
