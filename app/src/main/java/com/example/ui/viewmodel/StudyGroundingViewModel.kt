package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class SearchGroundedSource(
  val title: String,
  val domain: String,
  val uri: String,
)

data class SearchGroundingUiState(
  val query: String = "",
  val isLoading: Boolean = false,
  val answerText: String = "",
  val webQueries: List<String> = emptyList(),
  val sources: List<SearchGroundedSource> = emptyList(),
  val errorMessage: String? = null,
)

data class MapsGroundedPlace(
  val title: String,
  val uri: String,
  val placeId: String,
)

data class MapsGroundingUiState(
  val query: String = "",
  val isLoading: Boolean = false,
  val summaryText: String = "",
  val places: List<MapsGroundedPlace> = emptyList(),
  val errorMessage: String? = null,
)

class StudyGroundingViewModel : ViewModel() {

  private val searchGroundedModel by lazy {
    Firebase.ai.generativeModel(
      modelName = GROUNDING_MODEL_NAME,
      tools = listOf(Tool.googleSearch()),
      systemInstruction =
        content {
          text(
            "You are StudyPulse Research Assistant. Provide accurate, up-to-date, student-friendly explanations, key formulas, or study outlines grounded in Google Search results. Keep responses structured and concise."
          )
        },
    )
  }

  private val _searchState = MutableStateFlow(SearchGroundingUiState())
  val searchState: StateFlow<SearchGroundingUiState> = _searchState.asStateFlow()

  private val _mapsState = MutableStateFlow(MapsGroundingUiState())
  val mapsState: StateFlow<MapsGroundingUiState> = _mapsState.asStateFlow()

  fun searchStudyTopic(query: String, subjectContext: String = "") {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return

    val fullPrompt =
      if (subjectContext.isNotBlank()) {
        "Course context: $subjectContext. Research and explain for a student: $trimmed"
      } else {
        trimmed
      }

    viewModelScope.launch {
      _searchState.value =
        SearchGroundingUiState(
          query = trimmed,
          isLoading = true,
        )
      try {
        val response =
          withTimeout(AI_TIMEOUT_MS) {
            searchGroundedModel.generateContent(fullPrompt)
          }
        val text = response.text?.trim().orEmpty()
        val metadata = response.candidates.firstOrNull()?.groundingMetadata
        val webQueries = metadata?.webSearchQueries.orEmpty().distinct()
        val sources =
          metadata
            ?.groundingChunks
            .orEmpty()
            .mapNotNull { chunk ->
              val web = chunk.web ?: return@mapNotNull null
              val uri = web.uri.orEmpty()
              if (uri.isBlank()) return@mapNotNull null
              SearchGroundedSource(
                title = web.title?.ifBlank { web.domain ?: "Web Source" } ?: "Web Source",
                domain = web.domain.orEmpty(),
                uri = uri,
              )
            }
            .distinctBy { it.uri }

        if (text.isBlank()) {
          _searchState.update {
            it.copy(
              isLoading = false,
              errorMessage = "No grounded response returned. Try refining your study query.",
            )
          }
        } else {
          _searchState.update {
            it.copy(
              isLoading = false,
              answerText = text,
              webQueries = webQueries,
              sources = sources,
              errorMessage = null,
            )
          }
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Google Search grounding timed out", e)
        _searchState.update {
          it.copy(
            isLoading = false,
            errorMessage = "Search request timed out after 60s. Please retry.",
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Google Search grounding failed", e)
        _searchState.update {
          it.copy(
            isLoading = false,
            errorMessage = e.localizedMessage ?: "Unable to fetch grounded search results.",
          )
        }
      }
    }
  }

  fun findCampusStudyPlaces(query: String, latitude: Double, longitude: Double) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return

    viewModelScope.launch {
      _mapsState.value =
        MapsGroundingUiState(
          query = trimmed,
          isLoading = true,
        )
      try {
        val mapsModel =
          Firebase.ai.generativeModel(
            modelName = GROUNDING_MODEL_NAME,
            tools = listOf(Tool.googleMaps()),
            toolConfig =
              ToolConfig(
                retrievalConfig =
                  RetrievalConfig.builder()
                    .setLatLng(LatLng(latitude, longitude))
                    .setLanguageCode("en")
                    .build()
              ),
            systemInstruction =
              content {
                text(
                  "You are StudyPulse Campus Navigator. Recommend real, currently operating study spots, libraries, or quiet cafes near the provided coordinates with brief practical notes for students."
                )
              },
          )

        val response =
          withTimeout(AI_TIMEOUT_MS) {
            mapsModel.generateContent(
              "Find recommended places near coordinates ($latitude, $longitude) for: $trimmed"
            )
          }

        val text = response.text?.trim().orEmpty()
        val metadata = response.candidates.firstOrNull()?.groundingMetadata
        val places =
          metadata
            ?.groundingChunks
            .orEmpty()
            .mapNotNull { chunk ->
              val maps = chunk.maps ?: return@mapNotNull null
              val title = maps.title.orEmpty()
              val uri = maps.uri.orEmpty()
              if (title.isBlank() && uri.isBlank()) return@mapNotNull null
              MapsGroundedPlace(
                title = title.ifBlank { "Google Maps Place" },
                uri = uri,
                placeId = maps.placeId.orEmpty(),
              )
            }
            .distinctBy { it.placeId.ifBlank { it.title } }

        if (text.isBlank() && places.isEmpty()) {
          _mapsState.update {
            it.copy(
              isLoading = false,
              errorMessage = "No Maps-grounded study spots found nearby.",
            )
          }
        } else {
          _mapsState.update {
            it.copy(
              isLoading = false,
              summaryText = text,
              places = places,
              errorMessage = null,
            )
          }
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Google Maps grounding timed out", e)
        _mapsState.update {
          it.copy(
            isLoading = false,
            errorMessage = "Maps discovery timed out after 60s. Please retry.",
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Google Maps grounding failed", e)
        _mapsState.update {
          it.copy(
            isLoading = false,
            errorMessage = e.localizedMessage ?: "Unable to fetch Google Maps recommendations.",
          )
        }
      }
    }
  }

  fun clearMapsResults() {
    _mapsState.value = MapsGroundingUiState()
  }

  fun clearSearchResults() {
    _searchState.value = SearchGroundingUiState()
  }

  private companion object {
    const val TAG = "StudyGroundingVM"
    const val GROUNDING_MODEL_NAME = "gemini-4-hydrogen"
    const val AI_TIMEOUT_MS = 60_000L
  }
}
