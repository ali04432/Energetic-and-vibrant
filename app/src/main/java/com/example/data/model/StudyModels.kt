package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskType(val label: String) {
  ASSIGNMENT("Assignment"),
  EXAM("Exam"),
}

enum class TaskPriority(val label: String) {
  LOW("Low"),
  MEDIUM("Medium"),
  HIGH("High"),
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserStats(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val currentStreak: Long = 0L,
  val longestStreak: Long = 0L,
  val lastFocusDate: String = "",
  val dailyGoalMinutes: Long = 60L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Subject(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val code: String = "",
  val instructor: String = "",
  val colorHex: Long = 0xFF4338CAL,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TimetableSlot(
  @DocumentId val id: String = "",
  val userId: String = "",
  val subjectId: String = "",
  val subjectName: String = "",
  val colorHex: Long = 0xFF4338CAL,
  val dayOfWeek: Long = 1L,
  val startMinutes: Long = 540L,
  val endMinutes: Long = 600L,
  val room: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StudyTask(
  @DocumentId val id: String = "",
  val userId: String = "",
  val subjectId: String = "",
  val subjectName: String = "",
  val colorHex: Long = 0xFF4338CAL,
  val title: String = "",
  val notes: String = "",
  val type: String = TaskType.ASSIGNMENT.name,
  val priority: String = TaskPriority.MEDIUM.name,
  val dueAtMillis: Long = 0L,
  val reminderLeadMinutes: Long = 60L,
  val completed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FocusSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val subjectId: String = "",
  val subjectName: String = "",
  val taskId: String = "",
  val durationMinutes: Long = 25L,
  val dateKey: String = "",
  @ServerTimestamp val completedAt: Timestamp? = null,
)
