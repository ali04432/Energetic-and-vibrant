@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusSession
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TaskType
import com.example.data.model.TimetableSlot
import com.example.data.model.UiState
import com.example.data.model.UserStats
import com.example.ui.components.DailyGoalInputDialog
import com.example.ui.components.DashboardDailyGoalRing
import com.example.ui.components.StreakGamificationDialog
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.adaptiveCategoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.components.dayLabelShort
import com.example.ui.components.formatDueSummary
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.theme.AmberTertiaryLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
  displayName: String,
  userStatsState: UiState<UserStats?>,
  subjectsState: UiState<List<Subject>>,
  slotsState: UiState<List<TimetableSlot>>,
  tasksState: UiState<List<StudyTask>>,
  sessionsState: UiState<List<FocusSession>>,
  selectedDayOfWeek: Int,
  selectedCategory: TaskType?,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onSelectDay: (Int) -> Unit,
  onSelectCategory: (TaskType?) -> Unit,
  onCycleTheme: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenWeeklySummary: () -> Unit,
  onOpenResearch: () -> Unit,
  onUpdateDailyGoalHours: (Double) -> Unit,
  onStartQuickFocus: (subjectId: String, subjectName: String, taskId: String, taskTitle: String) -> Unit,
  onOpenTaskDetail: (String) -> Unit,
  onOpenSubjectDetail: (String) -> Unit,
  onToggleTask: (StudyTask) -> Unit,
  onOpenAddTask: () -> Unit,
  onOpenAddSlot: () -> Unit,
  hasNotificationPermission: Boolean,
  onRequestNotificationPermission: () -> Unit,
) {
  val stats = (userStatsState as? UiState.Success)?.data
  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
  val slots = (slotsState as? UiState.Success)?.data.orEmpty()
  val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
  val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()

  var showGoalDialog by remember { mutableStateOf(false) }
  var showStreakDialog by remember { mutableStateOf(false) }

  val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  val todayFocusMinutes = sessions.filter { it.dateKey == todayKey }.sumOf { it.durationMinutes.toInt() }
  val dailyGoalMinutes = (stats?.dailyGoalMinutes ?: 60L).toInt().coerceAtLeast(15)
  val studiedHours = todayFocusMinutes / 60f
  val goalHours = dailyGoalMinutes / 60f
  val currentStreak = (stats?.currentStreak ?: 0L).toInt()
  val longestStreak = (stats?.longestStreak ?: 0L).toInt()

  val last7Days =
    remember(sessions) {
      val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val labelFmt = SimpleDateFormat("EEE", Locale.US)
      val cal = Calendar.getInstance()
      cal.add(Calendar.DAY_OF_YEAR, -6)
      (0..6).map {
        val key = keyFmt.format(cal.time)
        val label = labelFmt.format(cal.time)
        val mins = sessions.filter { it.dateKey == key }.sumOf { it.durationMinutes.toInt() }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        Triple(key, label, mins)
      }
    }

  val daySlots = slots.filter { it.dayOfWeek.toInt() == selectedDayOfWeek }
  val pendingTasks = tasks.filter { !it.completed }
  val categoryCounts = pendingTasks.groupingBy { TaskType.fromRaw(it.type) }.eachCount()
  val upcomingTasks =
    if (selectedCategory == null) pendingTasks.take(6)
    else pendingTasks.filter { TaskType.fromRaw(it.type) == selectedCategory }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Welcome back,",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = displayName.substringBefore(" ").ifBlank { "Scholar" },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          Surface(
            color =
              if (currentStreak > 0) MaterialTheme.colorScheme.tertiaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            modifier =
              Modifier.heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .clickable { showStreakDialog = true }
                .testTag("dashboard_streak_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = "Study streak: $currentStreak days. Tap for milestones.",
                tint =
                  if (currentStreak > 0) AmberTertiaryLight
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${currentStreak}d",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (currentStreak > 0) MaterialTheme.colorScheme.onTertiaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          IconButton(
            onClick = onOpenResearch,
            modifier = Modifier.size(48.dp).testTag("open_study_research_button"),
          ) {
            Icon(Icons.Rounded.TravelExplore, contentDescription = "Study web research")
          }

          IconButton(
            onClick = onCycleTheme,
            modifier = Modifier.size(48.dp).testTag("quick_theme_toggle"),
          ) {
            Icon(Icons.Rounded.DarkMode, contentDescription = "Switch theme")
          }

          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp).testTag("open_settings_button"),
          ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Open settings")
          }
        }
      }
    }

    // Notification Permission Banner if not granted
    if (!hasNotificationPermission) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          shape = RoundedCornerShape(18.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Enable notifications for assignment & exam deadline alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
              onClick = onRequestNotificationPermission,
              modifier = Modifier.testTag("enable_notifications_button"),
            ) {
              Text("Allow")
            }
          }
        }
      }
    }

    // Hero Daily Study Hour Goal Card with Progress Ring
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
              Brush.linearGradient(
                listOf(
                  MaterialTheme.colorScheme.primary,
                  MaterialTheme.colorScheme.secondary,
                )
              )
            )
            .padding(20.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Today's Study Goal",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
              )
              Text(
                text = String.format(Locale.US, "%.1f / %.1f hrs", studiedHours, goalHours),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              val remainingMinutes = (dailyGoalMinutes - todayFocusMinutes).coerceAtLeast(0)
              Text(
                text =
                  if (todayFocusMinutes >= dailyGoalMinutes)
                    "Daily goal reached! Your ${currentStreak}d streak is safe."
                  else String.format(
                    Locale.US,
                    "%.1fh (%dm) left to hit today's target",
                    remainingMinutes / 60f,
                    remainingMinutes,
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            DashboardDailyGoalRing(
              studiedHours = studiedHours,
              goalHours = goalHours,
              onClick = { showGoalDialog = true },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { showGoalDialog = true },
              colors =
                ButtonDefaults.outlinedButtonColors(
                  contentColor = Color.White,
                  containerColor = Color.White.copy(alpha = 0.14f),
                ),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today_edit_goal_button"),
            ) {
              Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Set Goal")
            }

            Button(
              onClick = { onStartQuickFocus("", "General Study", "", "") },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color.White,
                  contentColor = MaterialTheme.colorScheme.primary,
                ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("hero_start_focus_button"),
            ) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Focus Now")
            }
          }
        }
      }
    }

    // Quick Entry to Weekly Category Summary
    item {
      val weeklyCategorySummary =
        androidx.compose.runtime.remember(sessions, tasks) {
          computeWeeklyCategorySummary(sessions, tasks)
        }
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenWeeklySummary)
            .testTag("today_open_weekly_summary_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Weekly Category Summary", style = MaterialTheme.typography.titleSmall)
              val summarySub =
                weeklyCategorySummary.topCategory?.let {
                  String.format(
                    Locale.US,
                    "%.1fh total (7d) • Top: %s",
                    weeklyCategorySummary.currentTotalHours,
                    it.taskType.label,
                  )
                } ?: String.format(
                  Locale.US,
                  "%.1fh total (7d) • Tap to view category trends",
                  weeklyCategorySummary.currentTotalHours,
                )
              Text(
                text = summarySub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          FilledTonalButton(
            onClick = onOpenWeeklySummary,
            modifier = Modifier.heightIn(min = 40.dp).testTag("today_weekly_summary_button"),
          ) {
            Text("Summary")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // Weekday Timetable Strip
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Class Timetable", style = MaterialTheme.typography.titleLarge)
          FilledTonalButton(
            onClick = onOpenAddSlot,
            modifier = Modifier.heightIn(min = 40.dp).testTag("today_add_slot_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Class")
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items((1..7).toList()) { day ->
            val selected = day == selectedDayOfWeek
            Surface(
              color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onSelectDay(day) }
                  .testTag("day_chip_$day"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = dayLabelShort(day),
                  style = MaterialTheme.typography.labelLarge,
                  color =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    if (slotsState is UiState.Loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    } else if (daySlots.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "No classes on ${dayLabelShort(selectedDayOfWeek)}",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  if (subjects.isEmpty()) "Add your subjects in Planner, then schedule your classes."
                  else "Tap \"Add Class\" to schedule a study block or lecture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    } else {
      items(daySlots, key = { it.id }) { slot ->
        with(sharedTransitionScope) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                  if (slot.subjectId.isNotBlank()) onOpenSubjectDetail(slot.subjectId)
                }
                .testTag("slot_card_${slot.id}"),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(width = 6.dp, height = 46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(slot.colorHex)),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = slot.subjectName,
                  style = MaterialTheme.typography.titleMedium,
                  modifier =
                    if (slot.subjectId.isNotBlank()) {
                      Modifier.sharedElement(
                        rememberSharedContentState(key = "subject_${slot.subjectId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                      )
                    } else Modifier,
                )
                Text(
                  text =
                    "${formatMinutesOfDay(slot.startMinutes.toInt())} – ${formatMinutesOfDay(slot.endMinutes.toInt())}" +
                      if (slot.room.isNotBlank()) " • ${slot.room}" else "",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { onStartQuickFocus(slot.subjectId, slot.subjectName, "", "") },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  Icons.Rounded.PlayArrow,
                  contentDescription = "Focus on ${slot.subjectName}",
                  tint = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }
        }
      }
    }

    // Upcoming Deadlines & Category Filter Strip
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Upcoming Deadlines", style = MaterialTheme.typography.titleLarge)
          FilledTonalButton(
            onClick = onOpenAddTask,
            modifier = Modifier.heightIn(min = 40.dp).testTag("today_add_task_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Task")
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = selectedCategory == null,
              onClick = { onSelectCategory(null) },
              label = { Text("All (${pendingTasks.size})") },
              modifier = Modifier.testTag("dashboard_filter_all"),
            )
          }
          items(TaskType.entries) { category ->
            val count = categoryCounts[category] ?: 0
            val tint = category.adaptiveCategoryColor()
            val isSelected = selectedCategory == category
            FilterChip(
              selected = isSelected,
              onClick = { onSelectCategory(if (isSelected) null else category) },
              leadingIcon = {
                Icon(
                  imageVector = category.categoryIcon(),
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = tint.copy(alpha = 0.16f),
                  selectedLabelColor = tint,
                  selectedLeadingIconColor = tint,
                ),
              label = { Text("${category.label} ($count)") },
              modifier = Modifier.testTag("dashboard_filter_${category.name.lowercase()}"),
            )
          }
        }
      }
    }

    if (pendingTasks.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.School,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("You're all caught up!", style = MaterialTheme.typography.titleMedium)
              Text(
                "Add exams, assignments, readings, projects, or labs to get timely reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    } else if (upcomingTasks.isEmpty() && selectedCategory != null) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = selectedCategory.categoryIcon(),
                contentDescription = null,
                tint = selectedCategory.adaptiveCategoryColor(),
                modifier = Modifier.size(26.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "No ${selectedCategory.label.lowercase()} tasks pending",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Switch back to All to view your other active deadlines.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
              onClick = { onSelectCategory(null) },
              modifier = Modifier.testTag("reset_dashboard_category_filter"),
            ) {
              Text("Show All")
            }
          }
        }
      }
    } else {
      items(upcomingTasks, key = { it.id }) { task ->
        val taskCategory = TaskType.fromRaw(task.type)
        val categoryColor = taskCategory.adaptiveCategoryColor()
        with(sharedTransitionScope) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier =
              Modifier.fillMaxWidth()
                .sharedElement(
                  rememberSharedContentState(key = "task_${task.id}"),
                  animatedVisibilityScope = animatedVisibilityScope,
                )
                .clip(RoundedCornerShape(18.dp))
                .clickable { onOpenTaskDetail(task.id) }
                .testTag("today_task_card_${task.id}"),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(width = 4.dp, height = 46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(categoryColor),
              )
              IconButton(
                onClick = { onToggleTask(task) },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  imageVector =
                    if (task.completed) Icons.Rounded.CheckCircle
                    else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = "Toggle ${task.title}",
                  tint = categoryColor,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  TaskCategoryBadge(typeRaw = task.type)
                  Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(task.colorHex)))
                  Text(
                    text = task.subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = task.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = formatDueSummary(task.dueAtMillis),
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (task.dueAtMillis < System.currentTimeMillis()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Box(
                modifier =
                  Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(
                      when (task.priority) {
                        "HIGH" -> MaterialTheme.colorScheme.error
                        "MEDIUM" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                      }
                    ),
              )
            }
          }
        }
      }
    }
  }

  if (showGoalDialog) {
    DailyGoalInputDialog(
      currentGoalMinutes = dailyGoalMinutes,
      onDismiss = { showGoalDialog = false },
      onConfirmHours = { hours ->
        onUpdateDailyGoalHours(hours)
        showGoalDialog = false
      },
    )
  }

  if (showStreakDialog) {
    StreakGamificationDialog(
      currentStreak = currentStreak,
      longestStreak = longestStreak,
      todayMinutes = todayFocusMinutes,
      last7Days = last7Days,
      onDismiss = { showStreakDialog = false },
      onStartFocus = { onStartQuickFocus("", "General Study", "", "") },
    )
  }
}
