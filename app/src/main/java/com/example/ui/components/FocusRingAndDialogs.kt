package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Subject
import com.example.data.model.TaskPriority
import com.example.data.model.TaskType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SubjectPaletteHex
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun formatMinutesOfDay(minutes: Int): String {
  val clamped = minutes.coerceIn(0, 1439)
  val h24 = clamped / 60
  val m = clamped % 60
  val amPm = if (h24 < 12) "AM" else "PM"
  val h12 = when {
    h24 == 0 -> 12
    h24 > 12 -> h24 - 12
    else -> h24
  }
  return String.format(Locale.US, "%d:%02d %s", h12, m, amPm)
}

fun dayLabelShort(dayOfWeek: Int): String =
  when (dayOfWeek) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    7 -> "Sun"
    else -> "Mon"
  }

fun formatDueSummary(dueAtMillis: Long): String {
  val now = System.currentTimeMillis()
  val diff = dueAtMillis - now
  val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(dueAtMillis))
  return when {
    diff < 0L -> "Overdue • $dateStr"
    diff < 3_600_000L -> "In ${(diff / 60_000L).coerceAtLeast(1)}m • $dateStr"
    diff < 86_400_000L -> "In ${diff / 3_600_000L}h • $dateStr"
    else -> "In ${diff / 86_400_000L}d • $dateStr"
  }
}

@Composable
fun AnimatedFocusProgressRing(
  progress: Float,
  formattedTime: String,
  phaseLabel: String,
  isRunning: Boolean,
  modifier: Modifier = Modifier,
) {
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
      label = "focus_ring_progress",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.25f,
      targetValue = 0.75f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_alpha",
    )

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val glowColor = if (isRunning) secondaryColor.copy(alpha = pulseAlpha) else primaryColor.copy(alpha = 0.2f)

  Box(
    modifier = modifier.size(264.dp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
      val strokeWidth = 18.dp.toPx()
      val diameter = size.minDimension
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arcSize = Size(diameter, diameter)

      // Outer ambient halo when running
      drawCircle(
        color = glowColor,
        radius = diameter / 2f + 10.dp.toPx(),
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = 4.dp.toPx()),
      )

      // Background track
      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )

      // Active gradient progress sweep
      val sweep = 360f * animatedProgress
      if (sweep > 0.5f) {
        drawArc(
          brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
          startAngle = -90f,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Glowing indicator dot at leading edge
        val angleRad = (-90.0 + sweep) * PI / 180.0
        val radius = diameter / 2f
        val cx = size.width / 2f + (radius * cos(angleRad)).toFloat()
        val cy = size.height / 2f + (radius * sin(angleRad)).toFloat()
        drawCircle(
          color = Color.White,
          radius = 6.dp.toPx(),
          center = Offset(cx, cy),
        )
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Surface(
        color =
          if (isRunning) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = phaseLabel,
          style = MaterialTheme.typography.labelMedium,
          color =
            if (isRunning) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = formattedTime,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 46.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("focus_timer_digits"),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "${(animatedProgress * 100).toInt()}% complete",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSubjectDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, code: String, instructor: String, colorHex: Long) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("") }
  var instructor by remember { mutableStateOf("") }
  var selectedColor by remember { mutableLongStateOf(SubjectPaletteHex.first()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Subject", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Subject Name *") },
          placeholder = { Text("e.g., Data Structures") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("subject_name_input"),
        )
        OutlinedTextField(
          value = code,
          onValueChange = { code = it },
          label = { Text("Course Code") },
          placeholder = { Text("e.g., CS-204") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("subject_code_input"),
        )
        OutlinedTextField(
          value = instructor,
          onValueChange = { instructor = it },
          label = { Text("Instructor") },
          placeholder = { Text("e.g., Prof. Sarah Khan") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("subject_instructor_input"),
        )
        Text("Tag Color", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          SubjectPaletteHex.forEach { hex ->
            val selected = hex == selectedColor
            Box(
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(Color(hex))
                  .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                  )
                  .clickable { selectedColor = hex },
              contentAlignment = Alignment.Center,
            ) {
              if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected color", tint = Color.White, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, code, instructor, selectedColor) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("save_subject_button"),
      ) {
        Text("Save Subject")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTimetableSlotDialog(
  subjects: List<Subject>,
  initialDayOfWeek: Int,
  onDismiss: () -> Unit,
  onConfirm: (subject: Subject, dayOfWeek: Int, startMinutes: Int, endMinutes: Int, room: String) -> Unit,
) {
  var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
  var dayOfWeek by remember { mutableIntStateOf(initialDayOfWeek) }
  var startHour by remember { mutableIntStateOf(9) }
  var startMinute by remember { mutableIntStateOf(0) }
  var durationMinutes by remember { mutableIntStateOf(60) }
  var room by remember { mutableStateOf("") }

  val startTotal = startHour * 60 + startMinute
  val endTotal = (startTotal + durationMinutes).coerceAtMost(1440)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Timetable Class", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Subject", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          subjects.forEach { subj ->
            FilterChip(
              selected = selectedSubject?.id == subj.id,
              onClick = { selectedSubject = subj },
              label = { Text(subj.name) },
            )
          }
        }

        Text("Day of Week", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..7).forEach { day ->
            FilterChip(
              selected = dayOfWeek == day,
              onClick = { dayOfWeek = day },
              label = { Text(dayLabelShort(day)) },
            )
          }
        }

        Text("Start Time: ${formatMinutesOfDay(startTotal)}", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = false, onClick = { startHour = (startHour - 1).coerceAtLeast(6) }, label = { Text("-1 hr") })
          FilterChip(selected = false, onClick = { startHour = (startHour + 1).coerceAtMost(22) }, label = { Text("+1 hr") })
          FilterChip(
            selected = startMinute == 30,
            onClick = { startMinute = if (startMinute == 0) 30 else 0 },
            label = { Text(if (startMinute == 0) ":00" else ":30") },
          )
        }

        Text("Duration: $durationMinutes min (ends ${formatMinutesOfDay(endTotal)})", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(45, 60, 90, 120).forEach { dur ->
            FilterChip(
              selected = durationMinutes == dur,
              onClick = { durationMinutes = dur },
              label = { Text("${dur}m") },
            )
          }
        }

        OutlinedTextField(
          value = room,
          onValueChange = { room = it },
          label = { Text("Room / Link") },
          placeholder = { Text("e.g., Lab 3B or Zoom") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("slot_room_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          selectedSubject?.let { onConfirm(it, dayOfWeek, startTotal, endTotal, room) }
        },
        enabled = selectedSubject != null && endTotal > startTotal,
        modifier = Modifier.testTag("save_slot_button"),
      ) {
        Text("Add Class")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
  subjects: List<Subject>,
  defaultLeadMinutes: Long,
  onDismiss: () -> Unit,
  onConfirm: (
    subject: Subject?,
    title: String,
    notes: String,
    type: String,
    priority: String,
    dueAtMillis: Long,
    reminderLeadMinutes: Long,
  ) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
  var selectedType by remember { mutableStateOf(TaskType.ASSIGNMENT) }
  var selectedPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }
  var daysAhead by remember { mutableIntStateOf(1) }
  var dueHour by remember { mutableIntStateOf(18) }
  var reminderLeadMinutes by remember { mutableLongStateOf(defaultLeadMinutes) }

  val computedDueMillis =
    remember(daysAhead, dueHour) {
      Calendar.getInstance()
        .apply {
          add(Calendar.DAY_OF_YEAR, daysAhead)
          set(Calendar.HOUR_OF_DAY, dueHour)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }
        .timeInMillis
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Study Task", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TaskType.entries.forEach { t ->
            val categoryColor = t.adaptiveCategoryColor()
            val isSelected = selectedType == t
            FilterChip(
              selected = isSelected,
              onClick = { selectedType = t },
              leadingIcon = {
                Icon(
                  imageVector = t.categoryIcon(),
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = categoryColor.copy(alpha = 0.16f),
                  selectedLabelColor = categoryColor,
                  selectedLeadingIconColor = categoryColor,
                ),
              label = { Text(t.label) },
              modifier = Modifier.testTag("task_type_chip_${t.name.lowercase()}"),
            )
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title *") },
          placeholder = { Text("e.g., Midterm Calculus Review") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
        )

        if (subjects.isNotEmpty()) {
          Text("Subject", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { subj ->
              FilterChip(
                selected = selectedSubject?.id == subj.id,
                onClick = { selectedSubject = subj },
                label = { Text(subj.name) },
              )
            }
          }
        }

        Text("Priority", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TaskPriority.entries.forEach { p ->
            FilterChip(
              selected = selectedPriority == p,
              onClick = { selectedPriority = p },
              label = { Text(p.label) },
            )
          }
        }

        Text("Due Date: ${formatDueSummary(computedDueMillis)}", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(0 to "Today", 1 to "Tomorrow", 3 to "+3d", 7 to "+1w", 14 to "+2w").forEach { (d, lbl) ->
            FilterChip(selected = daysAhead == d, onClick = { daysAhead = d }, label = { Text(lbl) })
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(9 to "9 AM", 14 to "2 PM", 18 to "6 PM", 23 to "11 PM").forEach { (hr, lbl) ->
            FilterChip(selected = dueHour == hr, onClick = { dueHour = hr }, label = { Text(lbl) })
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Notify Before Deadline", style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(0L to "Off", 15L to "15m", 60L to "1h", 360L to "6h", 1440L to "1d").forEach { (mins, lbl) ->
            FilterChip(
              selected = reminderLeadMinutes == mins,
              onClick = { reminderLeadMinutes = mins },
              label = { Text(lbl) },
            )
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (optional)") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth().testTag("task_notes_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            selectedSubject,
            title,
            notes,
            selectedType.name,
            selectedPriority.name,
            computedDueMillis,
            reminderLeadMinutes,
          )
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.testTag("save_task_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
