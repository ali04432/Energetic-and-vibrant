package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TaskPriority
import com.example.data.model.TaskType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndObserveSubjectSlotTaskAndSession_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserStats(uid, "Alice") }

    val subjectId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addSubject(uid, "Algorithms", "CS301", "Dr. Rao", 0xFF4338CAL)
      }
    assertTrue(subjectId.isNotBlank())

    val subjects =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeSubjects(uid).first { list -> list.any { it.id == subjectId } }
      }
    assertEquals("Algorithms", subjects.first { it.id == subjectId }.name)

    val slotId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addTimetableSlot(uid, subjectId, "Algorithms", 0xFF4338CAL, 1, 540, 600, "Room 204")
      }
    assertTrue(slotId.isNotBlank())

    val taskId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addTask(
          userId = uid,
          subjectId = subjectId,
          subjectName = "Algorithms",
          colorHex = 0xFF4338CAL,
          title = "Graph Theory Assignment",
          notes = "Complete BFS and DFS proofs",
          type = TaskType.ASSIGNMENT.name,
          priority = TaskPriority.HIGH.name,
          dueAtMillis = System.currentTimeMillis() + 86_400_000L,
          reminderLeadMinutes = 60L,
        )
      }
    val fetchedTask = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(uid, taskId) }
    assertNotNull(fetchedTask)
    assertEquals("Graph Theory Assignment", fetchedTask?.title)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.toggleTaskCompleted(uid, fetchedTask!!) }
    val updatedTask = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(uid, taskId) }
    assertEquals(true, updatedTask?.completed)

    val sessionId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.recordFocusSessionAndUpdateStreak(
          userId = uid,
          displayName = "Alice",
          subjectId = subjectId,
          subjectName = "Algorithms",
          taskId = taskId,
          durationMinutes = 25,
          todayDateKey = "2026-11-12",
          yesterdayDateKey = "2026-11-11",
        )
      }
    assertTrue(sessionId.isNotBlank())

    val stats =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserStats(uid).first { it != null && it.currentStreak >= 1L }
      }
    assertEquals(1L, stats?.currentStreak)
  }

  @Test
  fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)
    val taskId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addTask(
          userId = aliceUid,
          subjectId = "sub_1",
          subjectName = "Physics",
          colorHex = 0xFF10B981L,
          title = "Lab Report",
          notes = "",
          type = TaskType.ASSIGNMENT.name,
          priority = TaskPriority.MEDIUM.name,
          dueAtMillis = System.currentTimeMillis() + 3_600_000L,
          reminderLeadMinutes = 30L,
        )
      }

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(aliceUid, taskId) }
      fail("Expected PERMISSION_DENIED for cross-user read")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = StudyRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeTasks("alice_unauth_target").first() }
      fail("Expected PERMISSION_DENIED for unauthenticated list")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@studypulse.test"
    const val BOB_EMAIL = "bob@studypulse.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
