package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.StudyRepository
import com.example.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase(val title: String, val subtitle: String) {
  FOCUS("Focus Sprint", "Deep work — stay locked in"),
  SHORT_BREAK("Short Break", "Stretch, hydrate, rest your eyes"),
  LONG_BREAK("Long Break", "Recharge for the next cycle"),
}

data class FocusTimerUiState(
  val phase: FocusPhase = FocusPhase.FOCUS,
  val focusMinutes: Int = 25,
  val shortBreakMinutes: Int = 5,
  val longBreakMinutes: Int = 15,
  val totalSeconds: Int = 25 * 60,
  val remainingSeconds: Int = 25 * 60,
  val isRunning: Boolean = false,
  val completedPomodorosInCycle: Int = 0,
  val linkedSubjectId: String = "",
  val linkedSubjectName: String = "General Study",
  val linkedTaskId: String = "",
  val linkedTaskTitle: String = "",
  val showCelebration: Boolean = false,
  val lastLoggedMinutes: Int = 0,
  val statusMessage: String? = null,
) {
  val progress: Float
    get() =
      if (totalSeconds <= 0) 0f
      else ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

  val formattedTime: String
    get() {
      val mins = remainingSeconds / 60
      val secs = remainingSeconds % 60
      return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

class FocusTimerViewModel(
  private val repository: StudyRepository,
  private val appContext: Context,
  private val currentUserId: String,
  private val displayName: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FocusTimerUiState())
  val uiState: StateFlow<FocusTimerUiState> = _uiState.asStateFlow()

  private var timerJob: Job? = null

  fun syncDurations(focusMinutes: Int, shortBreakMinutes: Int, longBreakMinutes: Int) {
    _uiState.update { current ->
      if (current.isRunning) {
        current.copy(
          focusMinutes = focusMinutes,
          shortBreakMinutes = shortBreakMinutes,
          longBreakMinutes = longBreakMinutes,
        )
      } else {
        val newDuration =
          when (current.phase) {
            FocusPhase.FOCUS -> focusMinutes * 60
            FocusPhase.SHORT_BREAK -> shortBreakMinutes * 60
            FocusPhase.LONG_BREAK -> longBreakMinutes * 60
          }
        current.copy(
          focusMinutes = focusMinutes,
          shortBreakMinutes = shortBreakMinutes,
          longBreakMinutes = longBreakMinutes,
          totalSeconds = newDuration,
          remainingSeconds = newDuration,
        )
      }
    }
  }

  fun selectPhase(phase: FocusPhase) {
    timerJob?.cancel()
    _uiState.update { current ->
      val secs = durationSecondsFor(phase, current)
      current.copy(
        phase = phase,
        totalSeconds = secs,
        remainingSeconds = secs,
        isRunning = false,
      )
    }
  }

  fun linkStudyTarget(
    subjectId: String,
    subjectName: String,
    taskId: String = "",
    taskTitle: String = "",
  ) {
    _uiState.update {
      it.copy(
        linkedSubjectId = subjectId,
        linkedSubjectName = subjectName.ifBlank { "General Study" },
        linkedTaskId = taskId,
        linkedTaskTitle = taskTitle,
      )
    }
  }

  fun toggleStartPause() {
    if (_uiState.value.isRunning) {
      pauseTimer()
    } else {
      startTimer()
    }
  }

  private fun startTimer() {
    timerJob?.cancel()
    _uiState.update { it.copy(isRunning = true, showCelebration = false) }
    timerJob =
      viewModelScope.launch {
        while (_uiState.value.isRunning && _uiState.value.remainingSeconds > 0) {
          delay(1000L)
          _uiState.update { current ->
            if (!current.isRunning) current
            else current.copy(remainingSeconds = (current.remainingSeconds - 1).coerceAtLeast(0))
          }
        }
        if (_uiState.value.isRunning && _uiState.value.remainingSeconds == 0) {
          onPhaseCompleted()
        }
      }
  }

  private fun pauseTimer() {
    timerJob?.cancel()
    _uiState.update { it.copy(isRunning = false) }
  }

  fun resetTimer() {
    timerJob?.cancel()
    _uiState.update { current ->
      val secs = durationSecondsFor(current.phase, current)
      current.copy(isRunning = false, totalSeconds = secs, remainingSeconds = secs)
    }
  }

  fun skipPhase() {
    timerJob?.cancel()
    _uiState.update { current ->
      val nextPhase =
        when (current.phase) {
          FocusPhase.FOCUS ->
            if ((current.completedPomodorosInCycle + 1) % 4 == 0) FocusPhase.LONG_BREAK
            else FocusPhase.SHORT_BREAK
          FocusPhase.SHORT_BREAK,
          FocusPhase.LONG_BREAK -> FocusPhase.FOCUS
        }
      val nextSecs = durationSecondsFor(nextPhase, current)
      current.copy(
        phase = nextPhase,
        totalSeconds = nextSecs,
        remainingSeconds = nextSecs,
        isRunning = false,
      )
    }
  }

  fun completeEarlyAndLog() {
    val current = _uiState.value
    if (current.phase != FocusPhase.FOCUS) return
    val elapsedMinutes = ((current.totalSeconds - current.remainingSeconds) / 60).coerceAtLeast(1)
    timerJob?.cancel()
    logFocusCompletion(elapsedMinutes, current)
  }

  fun dismissCelebration() {
    _uiState.update { it.copy(showCelebration = false) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  private fun onPhaseCompleted() {
    val current = _uiState.value
    if (current.phase == FocusPhase.FOCUS) {
      val minutes = current.focusMinutes
      logFocusCompletion(minutes, current)
    } else {
      NotificationHelper.showNotification(
        context = appContext,
        channelId = NotificationHelper.CHANNEL_TIMER,
        notificationId = TIMER_NOTIF_ID,
        title = "Break complete!",
        body = "Ready for your next Focus Sprint on StudyPulse.",
      )
      _uiState.update { state ->
        val nextSecs = durationSecondsFor(FocusPhase.FOCUS, state)
        state.copy(
          phase = FocusPhase.FOCUS,
          totalSeconds = nextSecs,
          remainingSeconds = nextSecs,
          isRunning = false,
          statusMessage = "Break finished! Ready to focus.",
        )
      }
    }
  }

  private fun logFocusCompletion(minutes: Int, snapshot: FocusTimerUiState) {
    val nextCount = snapshot.completedPomodorosInCycle + 1
    val nextPhase =
      if (nextCount % 4 == 0) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
    val nextSecs = durationSecondsFor(nextPhase, snapshot)

    _uiState.update {
      it.copy(
        phase = nextPhase,
        totalSeconds = nextSecs,
        remainingSeconds = nextSecs,
        isRunning = false,
        completedPomodorosInCycle = nextCount,
        showCelebration = true,
        lastLoggedMinutes = minutes,
      )
    }

    NotificationHelper.showNotification(
      context = appContext,
      channelId = NotificationHelper.CHANNEL_TIMER,
      notificationId = TIMER_NOTIF_ID,
      title = "Focus Sprint Complete (+${minutes}m)!",
      body = "${snapshot.linkedSubjectName} logged. Time for a ${nextPhase.title.lowercase()}.",
    )

    viewModelScope.launch {
      try {
        val (todayKey, yesterdayKey) = todayAndYesterdayKeys()
        repository.recordFocusSessionAndUpdateStreak(
          userId = currentUserId,
          displayName = displayName,
          subjectId = snapshot.linkedSubjectId,
          subjectName = snapshot.linkedSubjectName,
          taskId = snapshot.linkedTaskId,
          durationMinutes = minutes,
          todayDateKey = todayKey,
          yesterdayDateKey = yesterdayKey,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Failed to log focus session", e)
        _uiState.update {
          it.copy(statusMessage = e.localizedMessage ?: "Unable to sync focus session")
        }
      }
    }
  }

  private fun durationSecondsFor(phase: FocusPhase, state: FocusTimerUiState): Int =
    when (phase) {
      FocusPhase.FOCUS -> state.focusMinutes * 60
      FocusPhase.SHORT_BREAK -> state.shortBreakMinutes * 60
      FocusPhase.LONG_BREAK -> state.longBreakMinutes * 60
    }

  private fun todayAndYesterdayKeys(): Pair<String, String> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    val today = fmt.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = fmt.format(cal.time)
    return today to yesterday
  }

  override fun onCleared() {
    super.onCleared()
    timerJob?.cancel()
  }

  companion object {
    private const val TAG = "FocusTimerVM"
    private const val TIMER_NOTIF_ID = 7001
  }
}
