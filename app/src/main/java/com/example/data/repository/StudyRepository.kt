package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FocusSession
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TimetableSlot
import com.example.data.model.UserStats
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudyRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun userDoc(userId: String) = db.collection("users").document(userId)

  private fun subjectsCol(userId: String) = userDoc(userId).collection("subjects")

  private fun slotsCol(userId: String) = userDoc(userId).collection("slots")

  private fun tasksCol(userId: String) = userDoc(userId).collection("tasks")

  private fun sessionsCol(userId: String) = userDoc(userId).collection("sessions")

  private fun logIfActiveUser(error: Throwable, op: OperationType, path: String, userId: String) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && (auth.currentUser == null || auth.currentUser?.uid == userId)) {
      handleFirestoreError(exceptionToLog, op, path, auth)
    }
  }

  fun observeUserStats(userId: String): Flow<UserStats?> {
    val docRef = userDoc(userId)
    return docRef
      .snapshots()
      .map { snapshot ->
        if (!snapshot.exists()) null
        else snapshot.toObject(UserStats::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.GET, docRef.path, userId)
        throw error
      }
  }

  fun observeSubjects(userId: String): Flow<List<Subject>> {
    val col = subjectsCol(userId)
    return col
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.name.lowercase() }
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  fun observeTimetableSlots(userId: String): Flow<List<TimetableSlot>> {
    val col = slotsCol(userId)
    return col
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(TimetableSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(compareBy<TimetableSlot> { it.dayOfWeek }.thenBy { it.startMinutes })
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  fun observeTasks(userId: String): Flow<List<StudyTask>> {
    val col = tasksCol(userId)
    return col
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(compareBy<StudyTask> { it.completed }.thenBy { it.dueAtMillis })
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  fun observeFocusSessions(userId: String): Flow<List<FocusSession>> {
    val col = sessionsCol(userId)
    return col
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(FocusSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.completedAt?.seconds ?: 0L }
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  suspend fun ensureUserStats(userId: String, displayName: String) {
    val docRef = userDoc(userId)
    try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        val safeName = displayName.trim().take(80).ifEmpty { "Student" }
        val payload =
          mapOf(
            "userId" to userId,
            "displayName" to safeName,
            "currentStreak" to 0L,
            "longestStreak" to 0L,
            "lastFocusDate" to "",
            "dailyGoalMinutes" to 60L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }

  suspend fun updateDailyGoal(userId: String, dailyGoalMinutes: Int) {
    val docRef = userDoc(userId)
    val clamped = dailyGoalMinutes.coerceIn(10, 720).toLong()
    try {
      docRef
        .update(
          mapOf(
            "dailyGoalMinutes" to clamped,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun addSubject(
    userId: String,
    name: String,
    code: String,
    instructor: String,
    colorHex: Long,
  ): String {
    val docRef = subjectsCol(userId).document()
    val payload =
      mapOf(
        "userId" to userId,
        "name" to name.trim().take(60),
        "code" to code.trim().take(20),
        "instructor" to instructor.trim().take(60),
        "colorHex" to colorHex,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun updateSubject(userId: String, subject: Subject) {
    require(subject.id.isNotBlank()) { "Subject ID must not be blank" }
    requireNotNull(subject.createdAt) { "Subject createdAt must not be null for update" }
    val docRef = subjectsCol(userId).document(subject.id)
    try {
      docRef.set(subject.copy(userId = userId, updatedAt = null)).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun deleteSubject(userId: String, subjectId: String) {
    val docRef = subjectsCol(userId).document(subjectId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  suspend fun addTimetableSlot(
    userId: String,
    subjectId: String,
    subjectName: String,
    colorHex: Long,
    dayOfWeek: Int,
    startMinutes: Int,
    endMinutes: Int,
    room: String,
  ): String {
    val docRef = slotsCol(userId).document()
    val payload =
      mapOf(
        "userId" to userId,
        "subjectId" to subjectId,
        "subjectName" to subjectName.trim().take(60),
        "colorHex" to colorHex,
        "dayOfWeek" to dayOfWeek.coerceIn(1, 7).toLong(),
        "startMinutes" to startMinutes.coerceIn(0, 1439).toLong(),
        "endMinutes" to endMinutes.coerceIn(startMinutes + 1, 1440).toLong(),
        "room" to room.trim().take(40),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun deleteTimetableSlot(userId: String, slotId: String) {
    val docRef = slotsCol(userId).document(slotId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  suspend fun addTask(
    userId: String,
    subjectId: String,
    subjectName: String,
    colorHex: Long,
    title: String,
    notes: String,
    type: String,
    priority: String,
    dueAtMillis: Long,
    reminderLeadMinutes: Long,
  ): String {
    val docRef = tasksCol(userId).document()
    val payload =
      mapOf(
        "userId" to userId,
        "subjectId" to subjectId,
        "subjectName" to subjectName.trim().take(60),
        "colorHex" to colorHex,
        "title" to title.trim().take(100),
        "notes" to notes.trim().take(500),
        "type" to type,
        "priority" to priority,
        "dueAtMillis" to dueAtMillis,
        "reminderLeadMinutes" to reminderLeadMinutes.coerceIn(0L, 10080L),
        "completed" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun updateTask(userId: String, task: StudyTask) {
    require(task.id.isNotBlank()) { "Task ID must not be blank" }
    requireNotNull(task.createdAt) { "Task createdAt must not be null for update" }
    val docRef = tasksCol(userId).document(task.id)
    try {
      docRef.set(task.copy(userId = userId, updatedAt = null)).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun toggleTaskCompleted(userId: String, task: StudyTask) {
    require(task.id.isNotBlank()) { "Task ID must not be blank" }
    val docRef = tasksCol(userId).document(task.id)
    try {
      docRef
        .update(
          mapOf(
            "completed" to !task.completed,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun deleteTask(userId: String, taskId: String) {
    val docRef = tasksCol(userId).document(taskId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  suspend fun getTaskById(userId: String, taskId: String): StudyTask? {
    val docRef = tasksCol(userId).document(taskId)
    return try {
      docRef
        .get()
        .await()
        .toObject(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  suspend fun recordFocusSessionAndUpdateStreak(
    userId: String,
    displayName: String,
    subjectId: String,
    subjectName: String,
    taskId: String,
    durationMinutes: Int,
    todayDateKey: String,
    yesterdayDateKey: String,
  ): String {
    val statsRef = userDoc(userId)
    val sessionRef = sessionsCol(userId).document()
    val safeDuration = durationMinutes.coerceIn(1, 240).toLong()

    try {
      db.runTransaction { transaction ->
          val statsSnap = transaction.get(statsRef)
          val sessionPayload =
            mapOf(
              "userId" to userId,
              "subjectId" to subjectId,
              "subjectName" to subjectName.trim().take(60),
              "taskId" to taskId,
              "durationMinutes" to safeDuration,
              "dateKey" to todayDateKey,
              "completedAt" to FieldValue.serverTimestamp(),
            )
          transaction.set(sessionRef, sessionPayload)

          if (!statsSnap.exists()) {
            transaction.set(
              statsRef,
              mapOf(
                "userId" to userId,
                "displayName" to displayName.trim().take(80).ifEmpty { "Student" },
                "currentStreak" to 1L,
                "longestStreak" to 1L,
                "lastFocusDate" to todayDateKey,
                "dailyGoalMinutes" to 60L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          } else {
            val prevStreak = statsSnap.getLong("currentStreak") ?: 0L
            val prevLongest = statsSnap.getLong("longestStreak") ?: 0L
            val lastDate = statsSnap.getString("lastFocusDate") ?: ""
            val newStreak =
              when (lastDate) {
                todayDateKey -> if (prevStreak == 0L) 1L else prevStreak
                yesterdayDateKey -> prevStreak + 1L
                else -> 1L
              }
            val newLongest = maxOf(prevLongest, newStreak)
            transaction.update(
              statsRef,
              mapOf(
                "currentStreak" to newStreak,
                "longestStreak" to newLongest,
                "lastFocusDate" to todayDateKey,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
        .await()
      return sessionRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, sessionRef.path, auth)
      throw e
    }
  }
}
