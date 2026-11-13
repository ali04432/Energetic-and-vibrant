package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGoalAndStreakTest {
  @Test
  fun streakMilestoneInfo_returnsExpectedTiersAndTargets() {
    val zero = streakMilestoneInfo(0)
    assertEquals("Ready to Ignite", zero.first)
    assertEquals(1, zero.second)

    val oneDay = streakMilestoneInfo(1)
    assertEquals("Starter Spark", oneDay.first)
    assertEquals(3, oneDay.second)
    assertTrue(oneDay.third.contains("2d"))

    val fiveDays = streakMilestoneInfo(5)
    assertEquals("3-Day Flow", fiveDays.first)
    assertEquals(7, fiveDays.second)
    assertTrue(fiveDays.third.contains("2d"))

    val tenDays = streakMilestoneInfo(10)
    assertEquals("Week Warrior", tenDays.first)
    assertEquals(14, tenDays.second)
    assertTrue(tenDays.third.contains("4d"))

    val twentyDays = streakMilestoneInfo(20)
    assertEquals("Fortnight Scholar", twentyDays.first)
    assertEquals(30, twentyDays.second)
    assertTrue(twentyDays.third.contains("10d"))

    val legend = streakMilestoneInfo(30)
    assertEquals("30-Day Legend", legend.first)
    assertEquals(30, legend.second)
  }
}
