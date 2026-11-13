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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TimetableSlot
import com.example.data.model.UiState
import com.example.ui.components.dayLabelShort
import com.example.ui.components.formatDueSummary
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.viewmodel.TaskFilter

@Composable
fun PlannerScreen(
  subjectsState: UiState<List<Subject>>,
  slotsState: UiState<List<TimetableSlot>>,
  tasksState: UiState<List<StudyTask>>,
  selectedFilter: TaskFilter,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onSelectFilter: (TaskFilter) -> Unit,
  onOpenAddSubject: () -> Unit,
  onOpenAddSlot: () -> Unit,
  onOpenAddTask: () -> Unit,
  onOpenSubjectDetail: (String) -> Unit,
  onOpenTaskDetail: (String) -> Unit,
  onToggleTask: (StudyTask) -> Unit,
  onDeleteTask: (String) -> Unit,
  onDeleteSlot: (String) -> Unit,
  onFocusOnTask: (StudyTask) -> Unit,
) {
  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
  val slots = (slotsState as? UiState.Success)?.data.orEmpty()
  val tasks = (tasksState as? UiState.Success)?.data.orEmpty()

  val filteredTasks =
    when (selectedFilter) {
      TaskFilter.ALL -> tasks.filter { !it.completed }
      TaskFilter.ASSIGNMENTS -> tasks.filter { !it.completed && it.type == "ASSIGNMENT" }
      TaskFilter.EXAMS -> tasks.filter { !it.completed && it.type == "EXAM" }
      TaskFilter.COMPLETED -> tasks.filter { it.completed }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Subjects Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Subjects", style = MaterialTheme.typography.headlineMedium)
        FilledTonalButton(
          onClick = onOpenAddSubject,
          modifier = Modifier.heightIn(min = 44.dp).testTag("add_subject_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Subject")
        }
      }
    }

    if (subjects.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Create your courses (e.g., Math, CS-301) to color-code your timetable and exams.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(subjects, key = { it.id }) { subject ->
            with(sharedTransitionScope) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier =
                  Modifier.width(168.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenSubjectDetail(subject.id) }
                    .testTag("subject_card_${subject.id}"),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(subject.colorHex)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = subject.code.ifBlank { "COURSE" },
                      style = MaterialTheme.typography.labelMedium,
                      color = Color(subject.colorHex),
                    )
                  }
                  Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                      Modifier.sharedElement(
                        rememberSharedContentState(key = "subject_${subject.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                      ),
                  )
                  Text(
                    text = subject.instructor.ifBlank { "Tap for details" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Weekly Timetable Overview
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Weekly Timetable", style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(
          onClick = onOpenAddSlot,
          modifier = Modifier.heightIn(min = 44.dp).testTag("planner_add_slot_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Class Slot")
        }
      }
    }

    if (slots.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No weekly classes scheduled yet. Add class slots once you have at least one subject.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(slots, key = { it.id }) { slot ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = Color(slot.colorHex).copy(alpha = 0.16f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                text = dayLabelShort(slot.dayOfWeek.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = Color(slot.colorHex),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(slot.subjectName, style = MaterialTheme.typography.titleSmall)
              Text(
                text =
                  "${formatMinutesOfDay(slot.startMinutes.toInt())} – ${formatMinutesOfDay(slot.endMinutes.toInt())}" +
                    if (slot.room.isNotBlank()) " • ${slot.room}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = { onDeleteSlot(slot.id) },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete class slot")
            }
          }
        }
      }
    }

    // Assignments & Exams Section
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Assignments & Exams", style = MaterialTheme.typography.titleLarge)
          FilledTonalButton(
            onClick = onOpenAddTask,
            modifier = Modifier.heightIn(min = 44.dp).testTag("planner_add_task_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Task")
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(TaskFilter.entries) { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { onSelectFilter(filter) },
              label = { Text(filter.label) },
              modifier = Modifier.testTag("task_filter_${filter.name.lowercase()}"),
            )
          }
        }
      }
    }

    if (filteredTasks.isEmpty()) {
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
              Icons.AutoMirrored.Rounded.Assignment,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "No ${selectedFilter.label.lowercase()} items right now.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredTasks, key = { it.id }) { task ->
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
                .testTag("planner_task_card_${task.id}"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = { onToggleTask(task) }, modifier = Modifier.size(48.dp)) {
                Icon(
                  imageVector =
                    if (task.completed) Icons.Rounded.CheckCircle
                    else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = "Complete ${task.title}",
                  tint = Color(task.colorHex),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Surface(
                    color = Color(task.colorHex).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    Text(
                      text = if (task.type == "EXAM") "EXAM" else "ASSIGNMENT",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(task.colorHex),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                  if (task.reminderLeadMinutes > 0L && !task.completed) {
                    Icon(
                      Icons.Rounded.NotificationsActive,
                      contentDescription = "Reminder set",
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = task.title,
                  style = MaterialTheme.typography.titleMedium,
                  textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${task.subjectName} • ${formatDueSummary(task.dueAtMillis)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (!task.completed) {
                IconButton(onClick = { onFocusOnTask(task) }, modifier = Modifier.size(48.dp)) {
                  Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Focus on ${task.title}",
                    tint = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
              IconButton(onClick = { onDeleteTask(task.id) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${task.title}")
              }
            }
          }
        }
      }
    }
  }
}
