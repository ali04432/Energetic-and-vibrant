package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

enum class TaskType(
  val label: String,
  val badge: String,
  val accentColorHex: Long,
) {
  EXAM("Exam", "EXAM", 0xFFEF4444L),
  ASSIGNMENT("Assignment", "ASSIGNMENT", 0xFF4338CAL),
  READING("Reading", "READING", 0xFF10B981L),
  PROJECT("Project", "PROJECT", 0xFF8B5CF6L),
  LAB("Lab", "LAB", 0xFFF59E0BL);

  companion object {
    fun fromRaw(raw: String?): TaskType =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ASSIGNMENT
  }
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
  val taskType: String = "",
  val durationMinutes: Long = 25L,
  val dateKey: String = "",
  @ServerTimestamp val completedAt: Timestamp? = null,
)

enum class SpotCategory(val label: String) {
  CLASSROOM("Classrooms"),
  LIBRARY("Libraries"),
  CAFE("Quiet Cafes"),
  EXAM_HALL("Exam Halls"),
}

@IgnoreExtraProperties
data class StudySpot(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val category: String = SpotCategory.LIBRARY.name,
  val address: String = "",
  val notes: String = "",
  val subjectId: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class CoachPersona(
  val label: String,
  val shortTag: String,
  val modelName: String,
  val tagline: String,
  val systemPrompt: String,
) {
  STUDY_TUTOR(
    label = "Study Tutor",
    shortTag = "Tutor • 4-Hydrogen",
    modelName = "gemini-4-hydrogen",
    tagline = "Clear concept explanations, reading summaries & everyday study Q&A",
    systemPrompt =
      "You are StudyPulse Study Tutor, an encouraging, crystal-clear academic tutor. Break down concepts with intuitive analogies, concise summaries, and actionable examples tailored to the student's courses and upcoming deadlines.",
  ),
  EXAM_COACH(
    label = "Exam Coach",
    shortTag = "Coach • 4-Argon",
    modelName = "gemini-4-argon",
    tagline = "Complex interactive practice quizzes, Socratic drills & rubric feedback",
    systemPrompt =
      "You are StudyPulse Exam Coach, an adaptive, interactive test-prep specialist. Run structured multi-turn practice drills, quiz the student one question or checkpoint at a time, wait for their answer, evaluate reasoning rigorously against exam rubrics, and adapt difficulty dynamically.",
  ),
  DEEP_SOLVER(
    label = "Deep Solver",
    shortTag = "Solver • 4-Carbon",
    modelName = "gemini-4-carbon",
    tagline = "Deep reasoning for rigorous proofs, STEM derivations & algorithm analysis",
    systemPrompt =
      "You are StudyPulse Deep Solver, a rigorous analytical reasoning partner for advanced mathematics, science, proofs, and algorithmic problem solving. Verify every assumption explicitly, walk through step-by-step derivations carefully, and flag subtle edge cases.",
  );

  companion object {
    fun fromRaw(raw: String?): CoachPersona =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STUDY_TUTOR
  }
}

@IgnoreExtraProperties
data class ChatThread(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "New Study Chat",
  val persona: String = CoachPersona.STUDY_TUTOR.name,
  val subjectId: String = "",
  val subjectName: String = "",
  val useSearchGrounding: Boolean = false,
  val lastMessagePreview: String = "",
  val messageCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessageRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val threadId: String = "",
  val senderRole: String = "user",
  val text: String = "",
  val persona: String = CoachPersona.STUDY_TUTOR.name,
  val modelUsed: String = CoachPersona.STUDY_TUTOR.modelName,
  val webSearchUsed: Boolean = false,
  val sourcesJson: String = "[]",
  val sequenceIndex: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Serializable
data class ChatWebSource(
  val title: String,
  val domain: String,
  val uri: String,
)


