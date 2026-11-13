package com.example.ui.screens

import com.example.data.model.FocusSession
import com.example.data.model.StudyTask
import com.example.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklySummaryAggregationTest {

  private fun dateKeyDaysAgo(daysAgo: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
  }

  @Test
  fun computeWeeklyCategorySummary_aggregatesHoursAcrossCategoriesAndPriorWeek() {
    val tasks =
      listOf(
        StudyTask(id = "task_legacy_lab", title = "Chem Lab", type = TaskType.LAB.name),
      )

    val sessions =
      listOf(
        // Current 7 days (days ago: 0..6)
        FocusSession(id = "s1", taskType = TaskType.EXAM.name, durationMinutes = 90L, dateKey = dateKeyDaysAgo(0)),
        FocusSession(id = "s2", taskType = TaskType.EXAM.name, durationMinutes = 30L, dateKey = dateKeyDaysAgo(2)),
        FocusSession(id = "s3", taskType = TaskType.READING.name, durationMinutes = 45L, dateKey = dateKeyDaysAgo(1)),
        // Legacy session without taskType field should resolve from taskId
        FocusSession(id = "s4", taskId = "task_legacy_lab", taskType = "", durationMinutes = 60L, dateKey = dateKeyDaysAgo(3)),
        // Prior 7 days (days ago: 7..13)
        FocusSession(id = "s5", taskType = TaskType.EXAM.name, durationMinutes = 60L, dateKey = dateKeyDaysAgo(7)),
        FocusSession(id = "s6", taskType = TaskType.PROJECT.name, durationMinutes = 90L, dateKey = dateKeyDaysAgo(10)),
        // Older than 14 days should be ignored
        FocusSession(id = "s7", taskType = TaskType.ASSIGNMENT.name, durationMinutes = 120L, dateKey = dateKeyDaysAgo(15)),
      )

    val summary = computeWeeklyCategorySummary(sessions, tasks)

    assertEquals(225, summary.currentTotalMinutes)
    assertEquals(3.75f, summary.currentTotalHours, 0.001f)
    assertEquals(150, summary.priorTotalMinutes)
    assertEquals(1.25f, summary.deltaHours, 0.001f)
    assertEquals(50, summary.deltaPercent)
    assertEquals(TaskType.EXAM, summary.topCategory?.taskType)

    val examStat = summary.categoryStats.first { it.taskType == TaskType.EXAM }
    assertEquals(120, examStat.currentMinutes)
    assertEquals(2.0f, examStat.currentHours, 0.001f)
    assertEquals(60, examStat.priorMinutes)
    assertEquals(2, examStat.sessionCount)

    val labStat = summary.categoryStats.first { it.taskType == TaskType.LAB }
    assertEquals(60, labStat.currentMinutes)
    assertEquals(1.0f, labStat.currentHours, 0.001f)

    val (examCurDaily, examPriorDaily) = summary.dailyHoursFor(TaskType.EXAM)
    assertEquals(7, examCurDaily.size)
    assertEquals(7, examPriorDaily.size)
    assertEquals(1.5f, examCurDaily.last().second, 0.001f)
    assertEquals(1.0f, examPriorDaily.last(), 0.001f)
  }
}
