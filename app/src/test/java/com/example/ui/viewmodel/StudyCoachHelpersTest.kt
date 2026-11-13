package com.example.ui.viewmodel

import com.example.data.model.ChatWebSource
import com.example.data.model.CoachPersona
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyCoachHelpersTest {
  @Test
  fun coachPersona_mapsExpectedGeminiModelTiers() {
    assertEquals("gemini-4-hydrogen", CoachPersona.STUDY_TUTOR.modelName)
    assertEquals("gemini-4-argon", CoachPersona.EXAM_COACH.modelName)
    assertEquals("gemini-4-carbon", CoachPersona.DEEP_SOLVER.modelName)
    assertEquals(CoachPersona.STUDY_TUTOR, CoachPersona.fromRaw("unknown"))
  }

  @Test
  fun sourcesJson_roundTripsCleanly() {
    val sources =
      listOf(
        ChatWebSource("MIT OpenCourseWare", "ocw.mit.edu", "https://ocw.mit.edu/courses"),
        ChatWebSource("Wikipedia", "en.wikipedia.org", "https://en.wikipedia.org/wiki/Calculus"),
      )
    val encoded = StudyCoachViewModel.encodeSourcesJson(sources)
    val decoded = StudyCoachViewModel.decodeSourcesJson(encoded)
    assertEquals(sources, decoded)
  }
}
