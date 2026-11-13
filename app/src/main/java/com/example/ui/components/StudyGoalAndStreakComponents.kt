package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberTertiaryLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSecondaryLight
import java.util.Locale
import kotlin.math.roundToInt

fun streakMilestoneInfo(streakDays: Int): Triple<String, Int, String> =
  when {
    streakDays >= 30 -> Triple("30-Day Legend", 30, "Master of daily consistency!")
    streakDays >= 14 -> Triple("Fortnight Scholar", 30, "${30 - streakDays}d to 30-Day Legend")
    streakDays >= 7 -> Triple("Week Warrior", 14, "${14 - streakDays}d to Fortnight Scholar")
    streakDays >= 3 -> Triple("3-Day Flow", 7, "${7 - streakDays}d to Week Warrior")
    streakDays >= 1 -> Triple("Starter Spark", 3, "${3 - streakDays}d to 3-Day Flow")
    else -> Triple("Ready to Ignite", 1, "Complete 1 focus sprint today to start a streak")
  }

@Composable
fun DashboardDailyGoalRing(
  studiedHours: Float,
  goalHours: Float,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val rawProgress = if (goalHours <= 0f) 0f else (studiedHours / goalHours).coerceIn(0f, 1f)
  val animatedProgress by
    animateFloatAsState(
      targetValue = rawProgress,
      animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
      label = "dashboard_goal_ring",
    )
  val pct = (rawProgress * 100f).roundToInt()

  Box(
    modifier =
      modifier
        .size(108.dp)
        .clip(CircleShape)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics {
          contentDescription =
            String.format(
              Locale.US,
              "Daily study goal progress ring: %.1f of %.1f hours completed, %d percent. Tap to edit target hours.",
              studiedHours,
              goalHours,
              pct,
            )
        }
        .testTag("dashboard_daily_goal_ring"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      val stroke = 10.dp.toPx()
      val diameter = size.minDimension
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arcSize = Size(diameter, diameter)

      drawArc(
        color = Color.White.copy(alpha = 0.24f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )

      val sweep = 360f * animatedProgress
      if (sweep > 1f) {
        drawArc(
          brush = Brush.sweepGradient(listOf(Color.White, MintSecondaryLight, Color.White)),
          startAngle = -90f,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$pct%",
        fontFamily = JetBrainsMonoFontFamily,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Text(
        text = String.format(Locale.US, "%.1fh", goalHours),
        fontFamily = JetBrainsMonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyGoalInputDialog(
  currentGoalMinutes: Int,
  onDismiss: () -> Unit,
  onConfirmHours: (Double) -> Unit,
) {
  val initialHours = (currentGoalMinutes / 60.0).coerceIn(0.25, 12.0)
  var hoursText by
    remember(currentGoalMinutes) {
      mutableStateOf(
        if (initialHours % 1.0 == 0.0) initialHours.toInt().toString()
        else String.format(Locale.US, "%.1f", initialHours)
      )
    }

  val parsedHours = hoursText.trim().toDoubleOrNull()
  val isValid = parsedHours != null && parsedHours in 0.25..12.0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Daily Study Hour Goal", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Set how many hours you aim to focus each day. Your dashboard progress ring updates automatically.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = hoursText,
          onValueChange = { raw ->
            if (raw.length <= 5 && raw.all { it.isDigit() || it == '.' }) {
              hoursText = raw
            }
          },
          label = { Text("Target Study Hours (0.25 – 12.0)") },
          suffix = { Text("hrs/day") },
          singleLine = true,
          isError = hoursText.isNotBlank() && !isValid,
          supportingText = {
            if (hoursText.isNotBlank() && !isValid) {
              Text("Enter a target between 0.25 and 12.0 hours")
            } else if (parsedHours != null) {
              Text("Equals ${(parsedHours * 60).roundToInt()} focused minutes per day")
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("daily_goal_hours_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val next = ((parsedHours ?: 1.0) - 0.5).coerceAtLeast(0.5)
              hoursText = String.format(Locale.US, "%.1f", next)
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("goal_minus_half_button"),
          ) {
            Text("-0.5h")
          }
          OutlinedButton(
            onClick = {
              val next = ((parsedHours ?: 1.0) + 0.5).coerceAtMost(12.0)
              hoursText = String.format(Locale.US, "%.1f", next)
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("goal_plus_half_button"),
          ) {
            Text("+0.5h")
          }
        }

        Text("Quick Presets", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(1.0, 2.0, 3.0, 4.0, 6.0).forEach { preset ->
            val label = "${preset.toInt()}h"
            val selected = parsedHours != null && kotlin.math.abs(parsedHours - preset) < 0.05
            FilterChip(
              selected = selected,
              onClick = { hoursText = String.format(Locale.US, "%.1f", preset) },
              label = { Text(label) },
              modifier = Modifier.testTag("goal_preset_${preset.toInt()}h"),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          parsedHours?.let { onConfirmHours(it) }
        },
        enabled = isValid,
        modifier = Modifier.heightIn(min = 48.dp).testTag("save_daily_goal_hours_button"),
      ) {
        Text("Save Goal")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun StreakGamificationDialog(
  currentStreak: Int,
  longestStreak: Int,
  todayMinutes: Int,
  last7Days: List<Triple<String, String, Int>>,
  onDismiss: () -> Unit,
  onStartFocus: () -> Unit,
) {
  val (tierName, nextTarget, nextTip) = streakMilestoneInfo(currentStreak)
  val tierProgress = (currentStreak.toFloat() / nextTarget.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
  val flameTint = if (currentStreak > 0) AmberTertiaryLight else MaterialTheme.colorScheme.onSurfaceVariant

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(40.dp)
              .clip(CircleShape)
              .background(flameTint.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = "Study streak flame",
            tint = flameTint,
            modifier = Modifier.size(24.dp),
          )
        }
        Column {
          Text("Study Streak", style = MaterialTheme.typography.titleLarge)
          Text(
            text = tierName,
            style = MaterialTheme.typography.labelMedium,
            color = flameTint,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Current Streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (currentStreak == 1) "1 Day" else "$currentStreak Days",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = flameTint,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Rounded.EmojiEvents,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Best: ${longestStreak}d",
                  fontFamily = JetBrainsMonoFontFamily,
                  style = MaterialTheme.typography.labelLarge,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (todayMinutes > 0) "Today's flame lit (+${todayMinutes}m)" else "No sprints yet today",
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (todayMinutes > 0) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = nextTip,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LinearProgressIndicator(
            progress = { tierProgress },
            color = flameTint,
            trackColor = flameTint.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("7-Day Consistency Check-In", style = MaterialTheme.typography.labelLarge)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            last7Days.forEach { (_, dayLabel, mins) ->
              val active = mins > 0
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier =
                    Modifier.size(30.dp)
                      .clip(CircleShape)
                      .background(
                        if (active) flameTint.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (active) {
                    Icon(
                      imageVector = Icons.Rounded.LocalFireDepartment,
                      contentDescription = "$dayLabel studied",
                      tint = flameTint,
                      modifier = Modifier.size(18.dp),
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = "$dayLabel idle",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = dayLabel.take(2),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onDismiss()
          onStartFocus()
        },
        modifier = Modifier.heightIn(min = 48.dp).testTag("streak_dialog_focus_button"),
      ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (todayMinutes > 0) "Focus Again" else "Ignite Streak")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}
