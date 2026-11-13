package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SpotCategory
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

  private suspend fun <T> tracedStep(stepName: String, block: suspend () -> T): T {
    return try {
      block()
    } catch (e: Exception) {
      throw AssertionError("Rule failure at step '$stepName': ${e.message}", e)
    }
  }

  @Test
  fun createAndObserveSubjectSlotTaskAndSession_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)

    tracedStep("ensureUserStats") {
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserStats(uid, "Alice") }
    }

    val subjectId =
      tracedStep("addSubject") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addSubject(uid, "Algorithms", "CS301", "Dr. Rao", 0xFF4338CAL)
        }
      }
    assertTrue(subjectId.isNotBlank())

    val subjects =
      tracedStep("observeSubjects") {
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeSubjects(uid).first { list -> list.any { it.id == subjectId } }
        }
      }
    assertEquals("Algorithms", subjects.first { it.id == subjectId }.name)

    val slotId =
      tracedStep("addTimetableSlot") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addTimetableSlot(uid, subjectId, "Algorithms", 0xFF4338CAL, 1, 540, 600, "Room 204")
        }
      }
    assertTrue(slotId.isNotBlank())

    val taskId =
      tracedStep("addTask") {
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
      }
    val fetchedTask = tracedStep("getTaskById") { withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(uid, taskId) } }
    assertNotNull(fetchedTask)
    assertEquals("Graph Theory Assignment", fetchedTask?.title)

    tracedStep("toggleTaskCompleted") {
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.toggleTaskCompleted(uid, fetchedTask!!) }
    }
    val updatedTask = tracedStep("getTaskById_updated") { withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(uid, taskId) } }
    assertEquals(true, updatedTask?.completed)

    val readingId =
      tracedStep("addReadingTask") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addTask(
            userId = uid,
            subjectId = subjectId,
            subjectName = "Algorithms",
            colorHex = 0xFF4338CAL,
            title = "Chapter 5 Reading",
            notes = "",
            type = TaskType.READING.name,
            priority = TaskPriority.MEDIUM.name,
            dueAtMillis = System.currentTimeMillis() + 86_400_000L,
            reminderLeadMinutes = 60L,
          )
        }
      }
    assertEquals(TaskType.READING.name, tracedStep("getReadingTask") { withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTaskById(uid, readingId) } }?.type)

    val projectId =
      tracedStep("addProjectTask") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addTask(
            userId = uid,
            subjectId = subjectId,
            subjectName = "Algorithms",
            colorHex = 0xFF4338CAL,
            title = "Team Graph Visualizer",
            notes = "",
            type = TaskType.PROJECT.name,
            priority = TaskPriority.HIGH.name,
            dueAtMillis = System.currentTimeMillis() + 172_800_000L,
            reminderLeadMinutes = 60L,
          )
        }
      }
    assertTrue(projectId.isNotBlank())

    val labId =
      tracedStep("addLabTask") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addTask(
            userId = uid,
            subjectId = subjectId,
            subjectName = "Algorithms",
            colorHex = 0xFF4338CAL,
            title = "Sorting Benchmark Lab",
            notes = "",
            type = TaskType.LAB.name,
            priority = TaskPriority.LOW.name,
            dueAtMillis = System.currentTimeMillis() + 259_200_000L,
            reminderLeadMinutes = 30L,
          )
        }
      }
    assertTrue(labId.isNotBlank())

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addTask(
          userId = uid,
          subjectId = subjectId,
          subjectName = "Algorithms",
          colorHex = 0xFF4338CAL,
          title = "Invalid Type Check",
          notes = "",
          type = "INVALID_CATEGORY",
          priority = TaskPriority.MEDIUM.name,
          dueAtMillis = System.currentTimeMillis() + 86_400_000L,
          reminderLeadMinutes = 60L,
        )
      }
      fail("Expected PERMISSION_DENIED for invalid task category")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    val sessionId =
      tracedStep("recordFocusSessionAndUpdateStreak") {
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
      }
    assertTrue(sessionId.isNotBlank())

    val stats =
      tracedStep("observeUserStats") {
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeUserStats(uid).first { it != null && it.currentStreak >= 1L }
        }
      }
    assertEquals(1L, stats?.currentStreak)

    val spotId =
      tracedStep("addStudySpot") {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.addStudySpot(
            userId = uid,
            name = "Engineering Library",
            category = SpotCategory.LIBRARY.name,
            address = "250 College Walk",
            notes = "Quiet desks with outlets",
            subjectId = subjectId,
            latitude = 37.4275,
            longitude = -122.1697,
          )
        }
      }
    assertTrue(spotId.isNotBlank())
    val spots =
      tracedStep("observeStudySpots") {
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeStudySpots(uid).first { list -> list.any { it.id == spotId } }
        }
      }
    assertEquals("Engineering Library", spots.first { it.id == spotId }.name)
    tracedStep("deleteStudySpot") {
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteStudySpot(uid, spotId) }
    }
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
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 8000L
  }
}
