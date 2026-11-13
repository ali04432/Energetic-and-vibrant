package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.example.data.model.FocusSession
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.data.model.UserStats
import com.example.ui.components.SubjectBreakdownPieChart
import com.example.ui.components.WeeklyFocusBarChart
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SubjectPaletteHex
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(
  userStatsState: UiState<UserStats?>,
  sessionsState: UiState<List<FocusSession>>,
  tasksState: UiState<List<StudyTask>>,
  subjectsState: UiState<List<Subject>>,
  onOpenWeeklySummary: () -> Unit,
) {
  val stats = (userStatsState as? UiState.Success)?.data
  val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
  val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()

  val last7Days =
    remember(sessions) {
      val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val labelFmt = SimpleDateFormat("EEE", Locale.US)
      val cal = Calendar.getInstance()
      cal.add(Calendar.DAY_OF_YEAR, -6)
      (0..6).map { offset ->
        val key = keyFmt.format(cal.time)
        val label = labelFmt.format(cal.time)
        val mins = sessions.filter { it.dateKey == key }.sumOf { it.durationMinutes.toInt() }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        Triple(key, label, mins)
      }
    }

  val weeklyBarData = remember(last7Days) { last7Days.map { it.second to it.third } }
  val weeklyTotalMinutes = remember(last7Days) { last7Days.sumOf { it.third } }
  val completedTasksCount = remember(tasks) { tasks.count { it.completed } }

  val subjectPieData =
    remember(sessions, subjects) {
      val subjectColorMap = subjects.associate { it.id to it.colorHex }
      sessions
        .groupBy { it.subjectName.ifBlank { "General Study" } }
        .entries
        .mapIndexed { idx, entry ->
          val total = entry.value.sumOf { it.durationMinutes.toInt() }
          val subjId = entry.value.firstOrNull()?.subjectId ?: ""
          val colorHex =
            subjectColorMap[subjId] ?: SubjectPaletteHex[idx % SubjectPaletteHex.size]
          Triple(entry.key, total, colorHex)
        }
        .filter { it.second > 0 }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text("Weekly Insights", style = MaterialTheme.typography.headlineLarge)
    }

    // Streak Hero Card
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
              Brush.linearGradient(
                listOf(
                  MaterialTheme.colorScheme.primary,
                  MaterialTheme.colorScheme.tertiary,
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
            Column {
              Text(
                text = "Study Streak",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
              )
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "${stats?.currentStreak ?: 0}",
                  fontFamily = JetBrainsMonoFontFamily,
                  style = MaterialTheme.typography.displayMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "days",
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White.copy(alpha = 0.9f),
                  modifier = Modifier.padding(bottom = 6.dp),
                )
              }
            }
            Surface(
              color = Color.White.copy(alpha = 0.2f),
              shape = RoundedCornerShape(16.dp),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = Color.White)
                Text(
                  text = "Best: ${stats?.longestStreak ?: 0}d",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                )
              }
            }
          }

          // 7-Day Activity Dots
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            last7Days.forEach { (_, label, mins) ->
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier =
                    Modifier.size(28.dp)
                      .clip(CircleShape)
                      .background(
                        if (mins > 0) Color.White else Color.White.copy(alpha = 0.22f)
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (mins > 0) {
                    Icon(
                      Icons.Rounded.CheckCircle,
                      contentDescription = "$label studied",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = label.take(2),
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
            }
          }
        }
      }
    }

    // Summary Metrics Row
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StatMetricCard(
          icon = Icons.Rounded.Timer,
          title = "7-Day Focus",
          value = "${weeklyTotalMinutes}m",
          modifier = Modifier.weight(1f),
        )
        StatMetricCard(
          icon = Icons.Rounded.CheckCircle,
          title = "Tasks Done",
          value = "$completedTasksCount / ${tasks.size}",
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Weekly Category Summary CTA Card
    item {
      val weeklyCategorySummary =
        remember(sessions, tasks) { computeWeeklyCategorySummary(sessions, tasks) }
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
              )
              Column {
                Text(
                  text = "Weekly Category Summary",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val topLabel =
                  weeklyCategorySummary.topCategory?.let {
                    String.format(Locale.US, " • Top: %s (%.1fh)", it.taskType.label, it.currentHours)
                  } ?: ""
                Text(
                  text =
                    String.format(
                      Locale.US,
                      "%.1fh across Exam, Assignment, Reading, Project & Lab%s",
                      weeklyCategorySummary.currentTotalHours,
                      topLabel,
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
              }
            }
          }
          Button(
            onClick = onOpenWeeklySummary,
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("insights_open_weekly_summary_button"),
          ) {
            Text("View Category Hours & Trends")
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    // MPAndroidChart Weekly Focus Bar Chart
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Weekly Focus Minutes", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Daily Pomodoro minutes across the last 7 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(14.dp))
          if (weeklyTotalMinutes == 0) {
            EmptyChartState(
              message = "Complete your first Pomodoro sprint to illuminate your 7-day study chart.",
            )
          } else {
            WeeklyFocusBarChart(dailyMinutes = weeklyBarData)
          }
        }
      }
    }

    // MPAndroidChart Subject Breakdown Pie Chart
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Subject Focus Share", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Time distribution across your courses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(14.dp))
          if (subjectPieData.isEmpty()) {
            EmptyChartState(
              message = "Link a subject in Focus Studio to see your course-by-course breakdown.",
            )
          } else {
            SubjectBreakdownPieChart(subjectMinutes = subjectPieData)
          }
        }
      }
    }
  }
}

@Composable
private fun StatMetricCard(
  icon: ImageVector,
  title: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
      Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun EmptyChartState(message: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Rounded.AutoGraph,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
