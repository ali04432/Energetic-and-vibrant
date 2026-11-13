@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TimetableSlot
import com.example.data.preferences.UserPreferences
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.dayLabelShort
import com.example.ui.components.formatDueSummary
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SubjectDetailScreen(
  subject: Subject?,
  slots: List<TimetableSlot>,
  tasks: List<StudyTask>,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onBack: () -> Unit,
  onFocusSubject: (Subject) -> Unit,
  onDeleteSubject: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Subject Details") },
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        if (subject != null) {
          IconButton(
            onClick = {
              onDeleteSubject(subject.id)
              onBack()
            },
            modifier = Modifier.size(48.dp).testTag("delete_subject_detail_button"),
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete subject")
          }
        }
      },
    )

    if (subject == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Subject not found", style = MaterialTheme.typography.titleMedium)
      }
      return
    }

    val subjectSlots = slots.filter { it.subjectId == subject.id }
    val subjectTasks = tasks.filter { it.subjectId == subject.id }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        with(sharedTransitionScope) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(subject.colorHex)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = subject.code.ifBlank { "SUBJECT" },
                  style = MaterialTheme.typography.labelLarge,
                  color = Color(subject.colorHex),
                )
              }
              Text(
                text = subject.name,
                style = MaterialTheme.typography.headlineLarge,
                modifier =
                  Modifier.sharedElement(
                    rememberSharedContentState(key = "subject_${subject.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                  ),
              )
              if (subject.instructor.isNotBlank()) {
                Text(
                  text = "Instructor: ${subject.instructor}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = { onFocusSubject(subject) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Pomodoro for ${subject.name}")
              }
            }
          }
        }
      }

      item {
        Text("Scheduled Classes (${subjectSlots.size})", style = MaterialTheme.typography.titleLarge)
      }
      items(subjectSlots, key = { it.id }) { slot ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "${dayLabelShort(slot.dayOfWeek.toInt())} • ${formatMinutesOfDay(slot.startMinutes.toInt())} – ${formatMinutesOfDay(slot.endMinutes.toInt())}",
              style = MaterialTheme.typography.titleSmall,
            )
            if (slot.room.isNotBlank()) {
              Text(slot.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      item {
        Text("Study Tasks (${subjectTasks.size})", style = MaterialTheme.typography.titleLarge)
      }
      items(subjectTasks, key = { it.id }) { task ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskCategoryBadge(typeRaw = task.type)
            Text(task.title, style = MaterialTheme.typography.titleSmall)
            Text(
              text = formatDueSummary(task.dueAtMillis),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun TaskDetailScreen(
  task: StudyTask?,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onBack: () -> Unit,
  onToggleComplete: (StudyTask) -> Unit,
  onFocusTask: (StudyTask) -> Unit,
  onDeleteTask: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Deadline Details") },
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        if (task != null) {
          IconButton(
            onClick = {
              onDeleteTask(task.id)
              onBack()
            },
            modifier = Modifier.size(48.dp).testTag("delete_task_detail_button"),
          ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete task")
          }
        }
      },
    )

    if (task == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Task not found", style = MaterialTheme.typography.titleMedium)
      }
      return
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        with(sharedTransitionScope) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            modifier =
              Modifier.fillMaxWidth()
                .sharedElement(
                  rememberSharedContentState(key = "task_${task.id}"),
                  animatedVisibilityScope = animatedVisibilityScope,
                ),
          ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskCategoryBadge(typeRaw = task.type)
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = "Priority: ${task.priority}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              Text(task.title, style = MaterialTheme.typography.headlineMedium)
              Text(
                text = "${task.subjectName} • ${formatDueSummary(task.dueAtMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (task.notes.isNotBlank()) {
                Text(task.notes, style = MaterialTheme.typography.bodyLarge)
              }
              Text(
                text =
                  if (task.reminderLeadMinutes > 0L)
                    "Notification alert scheduled ${task.reminderLeadMinutes} min before deadline"
                  else "No deadline reminder scheduled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { onFocusTask(task) },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Focus")
                }
                OutlinedButton(
                  onClick = { onToggleComplete(task) },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                  Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (task.completed) "Reopen" else "Complete")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsScreen(
  userEmail: String,
  preferences: UserPreferences,
  dailyGoalMinutes: Int,
  onBack: () -> Unit,
  onSelectTheme: (ThemeMode) -> Unit,
  onSaveDurations: (focus: Int, shortBreak: Int, longBreak: Int) -> Unit,
  onUpdateDailyGoal: (Int) -> Unit,
  onUpdateReminderLead: (Long) -> Unit,
  onSignOut: () -> Unit,
) {
  var focusSlider by remember(preferences.focusMinutes) { mutableIntStateOf(preferences.focusMinutes) }
  var shortSlider by remember(preferences.shortBreakMinutes) { mutableIntStateOf(preferences.shortBreakMinutes) }
  var longSlider by remember(preferences.longBreakMinutes) { mutableIntStateOf(preferences.longBreakMinutes) }
  var goalSlider by remember(dailyGoalMinutes) { mutableIntStateOf(dailyGoalMinutes) }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Settings & Preferences") },
      navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = preferences.themeMode == mode,
                  onClick = { onSelectTheme(mode) },
                  label = { Text(mode.label) },
                  modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                )
              }
            }
          }
        }
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pomodoro Presets", style = MaterialTheme.typography.titleMedium)
            Text("Focus Sprint: $focusSlider min", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = focusSlider.toFloat(),
              onValueChange = { focusSlider = (it / 5f).roundToInt() * 5 },
              valueRange = 15f..60f,
            )
            Text("Short Break: $shortSlider min", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = shortSlider.toFloat(),
              onValueChange = { shortSlider = it.roundToInt() },
              valueRange = 3f..15f,
            )
            Text("Long Break: $longSlider min", style = MaterialTheme.typography.bodyMedium)
            Slider(
              value = longSlider.toFloat(),
              onValueChange = { longSlider = (it / 5f).roundToInt() * 5 },
              valueRange = 10f..30f,
            )
            Button(
              onClick = { onSaveDurations(focusSlider, shortSlider, longSlider) },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_pomodoro_presets_button"),
            ) {
              Text("Apply Timer Presets")
            }
          }
        }
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Daily Study Target: $goalSlider min", style = MaterialTheme.typography.titleMedium)
            Slider(
              value = goalSlider.toFloat(),
              onValueChange = { goalSlider = (it / 15f).roundToInt() * 15 },
              valueRange = 30f..240f,
            )
            Button(
              onClick = { onUpdateDailyGoal(goalSlider) },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
              Text("Save Daily Goal")
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Default Deadline Reminder", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(0L to "Off", 15L to "15m", 60L to "1h", 360L to "6h", 1440L to "1d").forEach { (m, lbl) ->
                FilterChip(
                  selected = preferences.defaultReminderLeadMinutes == m,
                  onClick = { onUpdateReminderLead(m) },
                  label = { Text(lbl) },
                )
              }
            }
          }
        }
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cloud Sync Account", style = MaterialTheme.typography.titleMedium)
            Text(
              text = userEmail.ifBlank { "Signed in with Google" },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = onSignOut,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sign Out")
            }
          }
        }
      }
    }
  }
}
