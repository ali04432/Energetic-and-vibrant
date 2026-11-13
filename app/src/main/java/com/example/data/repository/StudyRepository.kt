package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ChatThread
import com.example.data.model.CoachPersona
import com.example.data.model.FocusSession
import com.example.data.model.StudySpot
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TaskType
import com.example.data.model.TimetableSlot
import com.example.data.model.UserStats
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
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

  private fun spotsCol(userId: String) = userDoc(userId).collection("spots")

  private fun chatThreadsCol(userId: String) = userDoc(userId).collection("chatThreads")

  private fun chatMessagesCol(userId: String, threadId: String) =
    chatThreadsCol(userId).document(threadId).collection("messages")

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

  fun observeStudySpots(userId: String): Flow<List<StudySpot>> {
    val col = spotsCol(userId)
    return col
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(StudySpot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.name.lowercase() }
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
    taskType: String = TaskType.ASSIGNMENT.name,
    durationMinutes: Int,
    todayDateKey: String,
    yesterdayDateKey: String,
  ): String {
    val statsRef = userDoc(userId)
    val sessionRef = sessionsCol(userId).document()
    val safeDuration = durationMinutes.coerceIn(1, 240).toLong()
    val safeTaskType = TaskType.fromRaw(taskType).name

    try {
      db.runTransaction { transaction ->
          val statsSnap = transaction.get(statsRef)
          val sessionPayload =
            mapOf(
              "userId" to userId,
              "subjectId" to subjectId,
              "subjectName" to subjectName.trim().take(60),
              "taskId" to taskId,
              "taskType" to safeTaskType,
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

  suspend fun addStudySpot(
    userId: String,
    name: String,
    category: String,
    address: String,
    notes: String,
    subjectId: String,
    latitude: Double,
    longitude: Double,
  ): String {
    val docRef = spotsCol(userId).document()
    val payload =
      mapOf(
        "userId" to userId,
        "name" to name.trim().take(80),
        "category" to category,
        "address" to address.trim().take(200),
        "notes" to notes.trim().take(300),
        "subjectId" to subjectId,
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
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

  suspend fun deleteStudySpot(userId: String, spotId: String) {
    val docRef = spotsCol(userId).document(spotId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  fun observeChatThreads(userId: String): Flow<List<ChatThread>> {
    val col = chatThreadsCol(userId)
    return col
      .orderBy("updatedAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  fun observeChatMessages(userId: String, threadId: String): Flow<List<ChatMessageRecord>> {
    val col = chatMessagesCol(userId, threadId)
    return col
      .orderBy("sequenceIndex", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          ChatMessageRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        logIfActiveUser(error, OperationType.LIST, col.path, userId)
        throw error
      }
  }

  suspend fun createChatThread(
    userId: String,
    title: String,
    persona: CoachPersona,
    subjectId: String = "",
    subjectName: String = "",
    useSearchGrounding: Boolean = false,
  ): String {
    val docRef = chatThreadsCol(userId).document()
    val safeTitle = title.trim().take(100).ifEmpty { "New Study Chat" }
    val payload =
      mapOf(
        "userId" to userId,
        "title" to safeTitle,
        "persona" to persona.name,
        "subjectId" to subjectId.take(128),
        "subjectName" to subjectName.trim().take(60),
        "useSearchGrounding" to useSearchGrounding,
        "lastMessagePreview" to "",
        "messageCount" to 0L,
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

  suspend fun updateChatThreadMeta(
    userId: String,
    threadId: String,
    title: String? = null,
    persona: CoachPersona? = null,
    subjectId: String? = null,
    subjectName: String? = null,
    useSearchGrounding: Boolean? = null,
  ) {
    require(threadId.isNotBlank()) { "Thread ID must not be blank" }
    val docRef = chatThreadsCol(userId).document(threadId)
    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
    if (title != null) updates["title"] = title.trim().take(100).ifEmpty { "Study Chat" }
    if (persona != null) updates["persona"] = persona.name
    if (subjectId != null) updates["subjectId"] = subjectId.take(128)
    if (subjectName != null) updates["subjectName"] = subjectName.trim().take(60)
    if (useSearchGrounding != null) updates["useSearchGrounding"] = useSearchGrounding

    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun appendChatMessage(
    userId: String,
    threadId: String,
    senderRole: String,
    text: String,
    persona: CoachPersona,
    modelUsed: String,
    webSearchUsed: Boolean,
    sourcesJson: String,
    sequenceIndex: Long,
  ): String {
    require(threadId.isNotBlank()) { "Thread ID must not be blank" }
    val trimmedText = text.trim().take(12000)
    require(trimmedText.isNotEmpty()) { "Message text cannot be empty" }
    val msgRef = chatMessagesCol(userId, threadId).document()
    val threadRef = chatThreadsCol(userId).document(threadId)
    val msgPayload =
      mapOf(
        "userId" to userId,
        "threadId" to threadId,
        "senderRole" to senderRole,
        "text" to trimmedText,
        "persona" to persona.name,
        "modelUsed" to modelUsed.trim().take(60),
        "webSearchUsed" to webSearchUsed,
        "sourcesJson" to sourcesJson.take(8000),
        "sequenceIndex" to sequenceIndex.coerceIn(0L, 10000L),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    val threadUpdates =
      mapOf(
        "persona" to persona.name,
        "useSearchGrounding" to webSearchUsed,
        "lastMessagePreview" to trimmedText.replace('\n', ' ').take(300),
        "messageCount" to (sequenceIndex + 1L).coerceIn(1L, 10000L),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      val batch = db.batch()
      batch.set(msgRef, msgPayload)
      batch.update(threadRef, threadUpdates)
      batch.commit().await()
      return msgRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, msgRef.path, auth)
      throw e
    }
  }

  suspend fun deleteChatThread(userId: String, threadId: String) {
    val threadRef = chatThreadsCol(userId).document(threadId)
    val messagesCol = chatMessagesCol(userId, threadId)
    try {
      val msgs = messagesCol.get().await()
      for (chunk in msgs.documents.chunked(400)) {
        val batch = db.batch()
        chunk.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      threadRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, threadRef.path, auth)
      throw e
    }
  }
}
