package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.data.model.Subject
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.ui.components.AnimatedFocusProgressRing
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.adaptiveCategoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.viewmodel.FocusPhase
import com.example.ui.viewmodel.FocusTimerUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
  timerState: FocusTimerUiState,
  subjectsState: UiState<List<Subject>>,
  onSelectPhase: (FocusPhase) -> Unit,
  onSelectSubject: (subjectId: String, subjectName: String) -> Unit,
  onSelectTaskType: (TaskType) -> Unit,
  onToggleStartPause: () -> Unit,
  onReset: () -> Unit,
  onSkip: () -> Unit,
  onFinishEarly: () -> Unit,
  onDismissCelebration: () -> Unit,
) {
  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
  val celebrationComposition by
    rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_celebration))
  val pulseComposition by
    rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_study_pulse))

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("Focus Studio", style = MaterialTheme.typography.headlineLarge)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = timerState.phase.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Phase Switcher
      item {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          FocusPhase.entries.forEachIndexed { index, phase ->
            val label =
              when (phase) {
                FocusPhase.FOCUS -> "Focus ${timerState.focusMinutes}m"
                FocusPhase.SHORT_BREAK -> "Break ${timerState.shortBreakMinutes}m"
                FocusPhase.LONG_BREAK -> "Long ${timerState.longBreakMinutes}m"
              }
            SegmentedButton(
              selected = timerState.phase == phase,
              onClick = { onSelectPhase(phase) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = FocusPhase.entries.size),
              modifier = Modifier.testTag("phase_tab_${phase.name.lowercase()}"),
            ) {
              Text(label, maxLines = 1)
            }
          }
        }
      }

      // Animated Circular Progress Ring + subtle ambient Lottie pulse
      item {
        Box(contentAlignment = Alignment.Center) {
          if (timerState.isRunning) {
            LottieAnimation(
              composition = pulseComposition,
              iterations = LottieConstants.IterateForever,
              modifier = Modifier.size(292.dp),
            )
          }
          AnimatedFocusProgressRing(
            progress = timerState.progress,
            formattedTime = timerState.formattedTime,
            phaseLabel = timerState.phase.title,
            isRunning = timerState.isRunning,
          )
        }
      }

      // Active Context Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Focusing On", style = MaterialTheme.typography.labelLarge)
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
              ) {
                Text(
                  text = "Cycle ${timerState.completedPomodorosInCycle % 4}/4",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }

            if (timerState.linkedTaskTitle.isNotBlank()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TaskCategoryBadge(typeRaw = timerState.selectedTaskType.name)
                Text(
                  text = "Task: ${timerState.linkedTaskTitle}",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = timerState.linkedSubjectId.isBlank(),
                onClick = { onSelectSubject("", "General Study") },
                label = { Text("General Study") },
              )
              subjects.forEach { subject ->
                FilterChip(
                  selected = timerState.linkedSubjectId == subject.id,
                  onClick = { onSelectSubject(subject.id, subject.name) },
                  label = { Text(subject.name) },
                )
              }
            }

            Text(
              text = "Study Category",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TaskType.entries.forEach { type ->
                val tint = type.adaptiveCategoryColor()
                val isSelected = timerState.selectedTaskType == type
                FilterChip(
                  selected = isSelected,
                  onClick = { onSelectTaskType(type) },
                  leadingIcon = {
                    Icon(
                      imageVector = type.categoryIcon(),
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
                  label = { Text(type.label) },
                  modifier = Modifier.testTag("focus_category_${type.name.lowercase()}"),
                )
              }
            }
          }
        }
      }

      // Primary Controls
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalIconButton(
            onClick = onReset,
            modifier = Modifier.size(56.dp).testTag("focus_reset_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reset timer")
          }
          Spacer(modifier = Modifier.width(18.dp))
          Button(
            onClick = onToggleStartPause,
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  if (timerState.isRunning) MaterialTheme.colorScheme.tertiary
                  else MaterialTheme.colorScheme.primary,
              ),
            modifier = Modifier.size(76.dp).testTag("focus_play_pause_button"),
          ) {
            Icon(
              imageVector =
                if (timerState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (timerState.isRunning) "Pause timer" else "Start timer",
              modifier = Modifier.size(34.dp),
            )
          }
          Spacer(modifier = Modifier.width(18.dp))
          FilledTonalIconButton(
            onClick = onSkip,
            modifier = Modifier.size(56.dp).testTag("focus_skip_button"),
          ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Skip phase")
          }
        }
      }

      if (timerState.phase == FocusPhase.FOCUS && timerState.remainingSeconds < timerState.totalSeconds) {
        item {
          OutlinedButton(
            onClick = onFinishEarly,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("focus_finish_early_button"),
          ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Finish & Log Session Now")
          }
        }
      }
    }

    // Lottie Celebration Overlay
    AnimatedVisibility(
      visible = timerState.showCelebration,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
      modifier = Modifier.align(Alignment.Center),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          LottieAnimation(
            composition = celebrationComposition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(160.dp),
          )
          Text(
            text = "Focus Sprint Complete!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "+${timerState.lastLoggedMinutes} min logged for ${timerState.linkedSubjectName} (${timerState.selectedTaskType.label}). Your daily streak is updated!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(18.dp))
          Button(
            onClick = onDismissCelebration,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dismiss_celebration_button"),
          ) {
            Text("Continue")
          }
        }
      }
    }
  }
}
