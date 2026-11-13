package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FocusSession
import com.example.data.model.StudySpot
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TimetableSlot
import com.example.data.model.UiState
import com.example.data.model.UserStats
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.UserPreferences
import com.example.data.repository.StudyRepository
import com.example.notifications.DeadlineReminderScheduler
import com.example.ui.theme.ThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter(val label: String) {
  ALL("All"),
  ASSIGNMENTS("Assignments"),
  EXAMS("Exams"),
  COMPLETED("Done"),
}

class StudyPlannerViewModel(
  private val repository: StudyRepository,
  private val preferencesManager: PreferencesManager,
  private val appContext: Context,
  val currentUserId: String,
  val displayName: String,
) : ViewModel() {

  init {
    viewModelScope.launch {
      try {
        repository.ensureUserStats(currentUserId, displayName)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize user stats", e)
      }
    }
  }

  private fun <T> Flow<T>.toUiStateFlow(initial: UiState<T> = UiState.Loading): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Firestore stream error", root)
        emit(UiState.Error(root.localizedMessage ?: "Unable to sync study data"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = initial,
      )

  val userStatsState: StateFlow<UiState<UserStats?>> =
    repository.observeUserStats(currentUserId).toUiStateFlow()

  val subjectsState: StateFlow<UiState<List<Subject>>> =
    repository.observeSubjects(currentUserId).toUiStateFlow()

  val slotsState: StateFlow<UiState<List<TimetableSlot>>> =
    repository.observeTimetableSlots(currentUserId).toUiStateFlow()

  val tasksState: StateFlow<UiState<List<StudyTask>>> =
    repository.observeTasks(currentUserId).toUiStateFlow()

  val sessionsState: StateFlow<UiState<List<FocusSession>>> =
    repository.observeFocusSessions(currentUserId).toUiStateFlow()

  val spotsState: StateFlow<UiState<List<StudySpot>>> =
    repository.observeStudySpots(currentUserId).toUiStateFlow()

  val preferences: StateFlow<UserPreferences> =
    preferencesManager.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
      initialValue = UserPreferences(),
    )

  private val _selectedDayOfWeek = MutableStateFlow(todayIsoDayOfWeek())
  val selectedDayOfWeek: StateFlow<Int> = _selectedDayOfWeek.asStateFlow()

  private val _selectedTaskFilter = MutableStateFlow(TaskFilter.ALL)
  val selectedTaskFilter: StateFlow<TaskFilter> = _selectedTaskFilter.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  fun selectDay(day: Int) {
    _selectedDayOfWeek.value = day.coerceIn(1, 7)
  }

  fun setTaskFilter(filter: TaskFilter) {
    _selectedTaskFilter.value = filter
  }

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesManager.setThemeMode(mode) }
  }

  fun cycleThemeMode() {
    val next =
      when (preferences.value.themeMode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
      }
    setThemeMode(next)
  }

  fun updatePomodoroDurations(focus: Int, shortBreak: Int, longBreak: Int) {
    viewModelScope.launch {
      preferencesManager.setPomodoroDurations(focus, shortBreak, longBreak)
      _actionMessage.value = "Focus preset saved ($focus/$shortBreak/$longBreak min)"
    }
  }

  fun updateDefaultReminderLead(minutes: Long) {
    viewModelScope.launch {
      preferencesManager.setDefaultReminderLeadMinutes(minutes)
      _actionMessage.value = "Default reminder lead updated"
    }
  }

  fun updateDailyGoal(minutes: Int) {
    viewModelScope.launch {
      try {
        repository.updateDailyGoal(currentUserId, minutes)
        _actionMessage.value = "Daily goal updated to $minutes min"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to update daily goal"
      }
    }
  }

  fun addSubject(name: String, code: String, instructor: String, colorHex: Long) {
    if (name.isBlank()) {
      _actionMessage.value = "Subject name is required"
      return
    }
    viewModelScope.launch {
      try {
        repository.addSubject(currentUserId, name, code, instructor, colorHex)
        _actionMessage.value = "Added subject \"$name\""
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to add subject"
      }
    }
  }

  fun deleteSubject(subjectId: String) {
    viewModelScope.launch {
      try {
        repository.deleteSubject(currentUserId, subjectId)
        _actionMessage.value = "Subject removed"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to delete subject"
      }
    }
  }

  fun addTimetableSlot(
    subject: Subject,
    dayOfWeek: Int,
    startMinutes: Int,
    endMinutes: Int,
    room: String,
  ) {
    if (endMinutes <= startMinutes) {
      _actionMessage.value = "End time must be after start time"
      return
    }
    viewModelScope.launch {
      try {
        repository.addTimetableSlot(
          userId = currentUserId,
          subjectId = subject.id,
          subjectName = subject.name,
          colorHex = subject.colorHex,
          dayOfWeek = dayOfWeek,
          startMinutes = startMinutes,
          endMinutes = endMinutes,
          room = room,
        )
        _actionMessage.value = "Class slot added to timetable"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to add class slot"
      }
    }
  }

  fun deleteTimetableSlot(slotId: String) {
    viewModelScope.launch {
      try {
        repository.deleteTimetableSlot(currentUserId, slotId)
        _actionMessage.value = "Slot removed"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to remove slot"
      }
    }
  }

  fun addTask(
    subject: Subject?,
    title: String,
    notes: String,
    type: String,
    priority: String,
    dueAtMillis: Long,
    reminderLeadMinutes: Long,
  ) {
    if (title.isBlank()) {
      _actionMessage.value = "Title is required"
      return
    }
    viewModelScope.launch {
      try {
        val subjId = subject?.id ?: ""
        val subjName = subject?.name ?: "General Study"
        val colorHex = subject?.colorHex ?: 0xFF4338CAL
        val taskId =
          repository.addTask(
            userId = currentUserId,
            subjectId = subjId,
            subjectName = subjName,
            colorHex = colorHex,
            title = title,
            notes = notes,
            type = type,
            priority = priority,
            dueAtMillis = dueAtMillis,
            reminderLeadMinutes = reminderLeadMinutes,
          )
        DeadlineReminderScheduler.scheduleTaskReminder(
          context = appContext,
          taskId = taskId,
          title = title.trim(),
          subjectName = subjName,
          typeLabel = if (type == "EXAM") "Exam" else "Assignment",
          dueAtMillis = dueAtMillis,
          leadMinutes = reminderLeadMinutes,
        )
        _actionMessage.value =
          if (reminderLeadMinutes > 0L) "Saved with deadline reminder scheduled"
          else "Saved to planner"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to save task"
      }
    }
  }

  fun toggleTaskCompleted(task: StudyTask) {
    viewModelScope.launch {
      try {
        repository.toggleTaskCompleted(currentUserId, task)
        if (!task.completed) {
          DeadlineReminderScheduler.cancelTaskReminder(appContext, task.id)
        } else {
          DeadlineReminderScheduler.scheduleTaskReminder(
            context = appContext,
            taskId = task.id,
            title = task.title,
            subjectName = task.subjectName,
            typeLabel = if (task.type == "EXAM") "Exam" else "Assignment",
            dueAtMillis = task.dueAtMillis,
            leadMinutes = task.reminderLeadMinutes,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to update task"
      }
    }
  }

  fun updateTask(task: StudyTask) {
    viewModelScope.launch {
      try {
        repository.updateTask(currentUserId, task)
        if (task.completed) {
          DeadlineReminderScheduler.cancelTaskReminder(appContext, task.id)
        } else {
          DeadlineReminderScheduler.scheduleTaskReminder(
            context = appContext,
            taskId = task.id,
            title = task.title,
            subjectName = task.subjectName,
            typeLabel = if (task.type == "EXAM") "Exam" else "Assignment",
            dueAtMillis = task.dueAtMillis,
            leadMinutes = task.reminderLeadMinutes,
          )
        }
        _actionMessage.value = "Task updated"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to update task"
      }
    }
  }

  fun deleteTask(taskId: String) {
    viewModelScope.launch {
      try {
        repository.deleteTask(currentUserId, taskId)
        DeadlineReminderScheduler.cancelTaskReminder(appContext, taskId)
        _actionMessage.value = "Removed from planner"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to delete task"
      }
    }
  }

  fun addStudySpot(
    name: String,
    category: String,
    address: String,
    notes: String,
    subjectId: String,
    latitude: Double,
    longitude: Double,
  ) {
    if (name.isBlank()) {
      _actionMessage.value = "Location name is required"
      return
    }
    viewModelScope.launch {
      try {
        repository.addStudySpot(
          userId = currentUserId,
          name = name,
          category = category,
          address = address,
          notes = notes,
          subjectId = subjectId,
          latitude = latitude,
          longitude = longitude,
        )
        _actionMessage.value = "Pinned \"$name\" to Campus Map"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to pin study spot"
      }
    }
  }

  fun deleteStudySpot(spotId: String) {
    viewModelScope.launch {
      try {
        repository.deleteStudySpot(currentUserId, spotId)
        _actionMessage.value = "Removed study spot pin"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionMessage.value = e.localizedMessage ?: "Failed to remove study spot"
      }
    }
  }

  companion object {
    private const val TAG = "StudyPlannerVM"
    private const val STOP_TIMEOUT_MS = 5000L

    fun todayIsoDayOfWeek(): Int {
      val cal = Calendar.getInstance()
      return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
      }
    }
  }
}
