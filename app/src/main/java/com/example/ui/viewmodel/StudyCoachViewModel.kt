@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ChatThread
import com.example.data.model.ChatWebSource
import com.example.data.model.CoachPersona
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.data.repository.StudyRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class StudyCoachUiState(
  val activeThreadId: String? = null,
  val selectedPersona: CoachPersona = CoachPersona.STUDY_TUTOR,
  val isWebSearchEnabled: Boolean = false,
  val selectedSubjectId: String = "",
  val selectedSubjectName: String = "",
  val isSending: Boolean = false,
  val streamingText: String = "",
  val suggestedFollowUps: List<String> = emptyList(),
  val errorMessage: String? = null,
)

class StudyCoachViewModel(
  private val repository: StudyRepository,
  private val currentUserId: String,
  private val displayName: String,
) : ViewModel() {

  private val fastHelperModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = FAST_HELPER_MODEL,
      systemInstruction =
        content {
          text(
            "You are a concise academic UI assistant. Always follow exact formatting instructions with zero filler."
          )
        },
    )
  }

  private val _uiState = MutableStateFlow(StudyCoachUiState())
  val uiState: StateFlow<StudyCoachUiState> = _uiState.asStateFlow()

  private val activeThreadIdFlow = MutableStateFlow<String?>(null)
  private var userExplicitlyStartedBlank = false
  private var academicContextSummary: String = ""

  val threadsState: StateFlow<UiState<List<ChatThread>>> =
    repository
      .observeChatThreads(currentUserId)
      .map<List<ChatThread>, UiState<List<ChatThread>>> { threads ->
        val currentId = activeThreadIdFlow.value
        if (currentId == null && !userExplicitlyStartedBlank && threads.isNotEmpty()) {
          val latest = threads.first()
          activeThreadIdFlow.value = latest.id
          _uiState.update {
            it.copy(
              activeThreadId = latest.id,
              selectedPersona = CoachPersona.fromRaw(latest.persona),
              isWebSearchEnabled = latest.useSearchGrounding,
              selectedSubjectId = latest.subjectId,
              selectedSubjectName = latest.subjectName,
            )
          }
        }
        UiState.Success(threads)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        emit(UiState.Error((firestoreError ?: error).message ?: "Unable to load study chats"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), UiState.Loading)

  val messagesState: StateFlow<List<ChatMessageRecord>> =
    activeThreadIdFlow
      .flatMapLatest { threadId ->
        if (threadId.isNullOrBlank()) flowOf(emptyList())
        else repository.observeChatMessages(currentUserId, threadId)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.e(TAG, "Failed to observe chat messages", error)
        _uiState.update {
          it.copy(errorMessage = (firestoreError ?: error).message ?: "Failed to load messages")
        }
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  fun updateAcademicContext(subjects: List<Subject>, tasks: List<StudyTask>) {
    val subjectNames = subjects.take(8).joinToString(", ") { "${it.name}${if (it.code.isNotBlank()) " (${it.code})" else ""}" }
    val pendingTasks =
      tasks
        .filter { !it.completed }
        .take(5)
        .joinToString("; ") { "${it.title} [${it.type}] (${it.subjectName.ifBlank { "General" }})" }
    academicContextSummary =
      buildString {
        append("Student: $displayName.")
        if (subjectNames.isNotBlank()) append(" Courses: $subjectNames.")
        if (pendingTasks.isNotBlank()) append(" Upcoming tasks: $pendingTasks.")
      }
  }

  fun selectPersona(persona: CoachPersona) {
    _uiState.update { it.copy(selectedPersona = persona) }
    val threadId = activeThreadIdFlow.value ?: return
    viewModelScope.launch {
      try {
        repository.updateChatThreadMeta(
          userId = currentUserId,
          threadId = threadId,
          persona = persona,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist thread persona", e)
      }
    }
  }

  fun toggleWebSearch() {
    val nextValue = !_uiState.value.isWebSearchEnabled
    _uiState.update { it.copy(isWebSearchEnabled = nextValue) }
    val threadId = activeThreadIdFlow.value ?: return
    viewModelScope.launch {
      try {
        repository.updateChatThreadMeta(
          userId = currentUserId,
          threadId = threadId,
          useSearchGrounding = nextValue,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist web search toggle", e)
      }
    }
  }

  fun setSubjectContext(subjectId: String, subjectName: String) {
    _uiState.update {
      it.copy(selectedSubjectId = subjectId, selectedSubjectName = subjectName)
    }
    val threadId = activeThreadIdFlow.value ?: return
    viewModelScope.launch {
      try {
        repository.updateChatThreadMeta(
          userId = currentUserId,
          threadId = threadId,
          subjectId = subjectId,
          subjectName = subjectName,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist subject context", e)
      }
    }
  }

  fun selectThread(thread: ChatThread) {
    userExplicitlyStartedBlank = false
    activeThreadIdFlow.value = thread.id
    _uiState.update {
      it.copy(
        activeThreadId = thread.id,
        selectedPersona = CoachPersona.fromRaw(thread.persona),
        isWebSearchEnabled = thread.useSearchGrounding,
        selectedSubjectId = thread.subjectId,
        selectedSubjectName = thread.subjectName,
        suggestedFollowUps = emptyList(),
        errorMessage = null,
      )
    }
  }

  fun startNewThread() {
    userExplicitlyStartedBlank = true
    activeThreadIdFlow.value = null
    _uiState.update {
      it.copy(
        activeThreadId = null,
        streamingText = "",
        suggestedFollowUps = emptyList(),
        errorMessage = null,
      )
    }
  }

  fun deleteThread(threadId: String) {
    viewModelScope.launch {
      try {
        if (activeThreadIdFlow.value == threadId) {
          startNewThread()
        }
        repository.deleteChatThread(currentUserId, threadId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete chat thread", e)
        _uiState.update {
          it.copy(errorMessage = e.localizedMessage ?: "Unable to delete conversation thread")
        }
      }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun sendMessage(rawPrompt: String) {
    val prompt = rawPrompt.trim()
    if (prompt.isEmpty() || _uiState.value.isSending) return

    val snapshotState = _uiState.value
    val persona = snapshotState.selectedPersona
    val webSearch = snapshotState.isWebSearchEnabled
    val effectiveModelName = if (webSearch) SEARCH_MODEL else persona.modelName
    val priorMessages = messagesState.value

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isSending = true,
          streamingText = "",
          suggestedFollowUps = emptyList(),
          errorMessage = null,
        )
      }
      try {
        val isBrandNewThread = activeThreadIdFlow.value.isNullOrBlank()
        val threadId =
          activeThreadIdFlow.value
            ?: repository
              .createChatThread(
                userId = currentUserId,
                title = prompt.take(48),
                persona = persona,
                subjectId = snapshotState.selectedSubjectId,
                subjectName = snapshotState.selectedSubjectName,
                useSearchGrounding = webSearch,
              )
              .also { createdId ->
                userExplicitlyStartedBlank = false
                activeThreadIdFlow.value = createdId
                _uiState.update { it.copy(activeThreadId = createdId) }
              }

        val userSeqIndex = priorMessages.size.toLong()
        repository.appendChatMessage(
          userId = currentUserId,
          threadId = threadId,
          senderRole = "user",
          text = prompt,
          persona = persona,
          modelUsed = effectiveModelName,
          webSearchUsed = webSearch,
          sourcesJson = "[]",
          sequenceIndex = userSeqIndex,
        )

        if (isBrandNewThread) {
          launch { updateSmartThreadTitle(threadId, prompt) }
        }

        val systemText =
          buildString {
            append(persona.systemPrompt)
            if (snapshotState.selectedSubjectName.isNotBlank()) {
              append(" Focus specifically on the student's course: ${snapshotState.selectedSubjectName}.")
            }
            if (academicContextSummary.isNotBlank()) {
              append(" Background: $academicContextSummary")
            }
            if (webSearch) {
              append(" Ground factual statements with current web search results whenever helpful.")
            }
          }

        val activeModel =
          if (webSearch) {
            Firebase.ai.generativeModel(
              modelName = SEARCH_MODEL,
              tools = listOf(Tool.googleSearch()),
              systemInstruction = content { text(systemText) },
            )
          } else {
            Firebase.ai.generativeModel(
              modelName = effectiveModelName,
              systemInstruction = content { text(systemText) },
            )
          }

        val historyContent: List<Content> =
          priorMessages.takeLast(MAX_HISTORY_TURNS).map { msg ->
            val role = if (msg.senderRole == "model") "model" else "user"
            content(role) { text(msg.text) }
          }

        val chat = activeModel.startChat(history = historyContent)
        val buffer = StringBuilder()
        val collectedSources = LinkedHashMap<String, ChatWebSource>()

        withTimeout(AI_TIMEOUT_MS) {
          chat.sendMessageStream(prompt).collect { chunk ->
            val delta = chunk.text.orEmpty()
            if (delta.isNotEmpty()) {
              buffer.append(delta)
              _uiState.update { it.copy(streamingText = buffer.toString()) }
            }
            val metadata = chunk.candidates.firstOrNull()?.groundingMetadata
            metadata?.groundingChunks.orEmpty().forEach { gChunk ->
              val web = gChunk.web ?: return@forEach
              val uri = web.uri.orEmpty()
              if (uri.isNotBlank() && !collectedSources.containsKey(uri)) {
                collectedSources[uri] =
                  ChatWebSource(
                    title = web.title?.ifBlank { web.domain ?: "Web Source" } ?: "Web Source",
                    domain = web.domain.orEmpty(),
                    uri = uri,
                  )
              }
            }
          }
        }

        val finalReply = buffer.toString().trim()
        if (finalReply.isEmpty()) {
          _uiState.update {
            it.copy(
              isSending = false,
              streamingText = "",
              errorMessage = "Gemini returned an empty reply. Please try again.",
            )
          }
          return@launch
        }

        val sourcesList = collectedSources.values.toList()
        repository.appendChatMessage(
          userId = currentUserId,
          threadId = threadId,
          senderRole = "model",
          text = finalReply,
          persona = persona,
          modelUsed = effectiveModelName,
          webSearchUsed = webSearch || sourcesList.isNotEmpty(),
          sourcesJson = encodeSourcesJson(sourcesList),
          sequenceIndex = userSeqIndex + 1L,
        )

        _uiState.update { it.copy(isSending = false, streamingText = "") }
        launch { generateSuggestedFollowUps(prompt, finalReply) }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Study coach response timed out", e)
        _uiState.update {
          it.copy(
            isSending = false,
            streamingText = "",
            errorMessage = "Response timed out after 60 seconds. Please retry.",
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Study coach generation failed", e)
        _uiState.update {
          it.copy(
            isSending = false,
            streamingText = "",
            errorMessage = e.localizedMessage ?: "Unable to get response from Study Coach.",
          )
        }
      }
    }
  }

  private suspend fun updateSmartThreadTitle(threadId: String, firstPrompt: String) {
    try {
      val response =
        withTimeout(FAST_HELPER_TIMEOUT_MS) {
          fastHelperModel.generateContent(
            "Create a 3 to 5 word concise study chat title without quotes or punctuation for: $firstPrompt"
          )
        }
      val conciseTitle = response.text?.trim()?.replace("\"", "")?.take(60).orEmpty()
      if (conciseTitle.isNotBlank()) {
        repository.updateChatThreadMeta(
          userId = currentUserId,
          threadId = threadId,
          title = conciseTitle,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Fast title summary skipped: ${e.message}")
    }
  }

  private suspend fun generateSuggestedFollowUps(prompt: String, reply: String) {
    try {
      val response =
        withTimeout(FAST_HELPER_TIMEOUT_MS) {
          fastHelperModel.generateContent(
            "Suggest exactly 3 short follow-up study questions (max 7 words each, one per line, no bullets or numbers) after this exchange.\nUser: ${prompt.take(250)}\nTutor: ${reply.take(450)}"
          )
        }
      val lines =
        response.text
          ?.lines()
          .orEmpty()
          .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '.', ' ') }
          .filter { it.length in 6..64 }
          .take(3)
      if (lines.isNotEmpty()) {
        _uiState.update { it.copy(suggestedFollowUps = lines) }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Fast follow-up suggestion skipped: ${e.message}")
    }
  }

  companion object {
    private const val TAG = "StudyCoachVM"
    private const val SEARCH_MODEL = "gemini-4-hydrogen"
    private const val FAST_HELPER_MODEL = "gemini-3.1-flash-lite"
    private const val AI_TIMEOUT_MS = 60_000L
    private const val FAST_HELPER_TIMEOUT_MS = 15_000L
    private const val MAX_HISTORY_TURNS = 24

    private val jsonCodec = Json { ignoreUnknownKeys = true }
    private val sourcesSerializer = ListSerializer(ChatWebSource.serializer())

    fun encodeSourcesJson(sources: List<ChatWebSource>): String {
      if (sources.isEmpty()) return "[]"
      val normalized =
        sources.take(8).map { src ->
          ChatWebSource(
            title = src.title.take(160),
            domain = src.domain.take(100),
            uri = src.uri.take(600),
          )
        }
      return jsonCodec.encodeToString(sourcesSerializer, normalized).take(8000)
    }

    fun decodeSourcesJson(raw: String): List<ChatWebSource> {
      if (raw.isBlank() || raw == "[]") return emptyList()
      return try {
        jsonCodec.decodeFromString(sourcesSerializer, raw).filter { it.uri.isNotBlank() }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}
