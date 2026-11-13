@file:OptIn(PublicPreviewAPI::class)

package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SpotCategory
import com.example.data.model.StudySpot
import com.example.data.model.StudyTask
import com.example.data.model.Subject
import com.example.data.model.TaskPriority
import com.example.data.model.TaskType
import com.example.data.model.TimetableSlot
import com.example.data.repository.StudyRepository
import com.example.notifications.DeadlineReminderScheduler
import com.example.ui.components.dayLabelShort
import com.example.ui.components.formatDueSummary
import com.example.ui.components.formatMinutesOfDay
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class VoiceSessionStatus(val label: String) {
  IDLE("Ready to Talk"),
  CONNECTING("Connecting to StudyPulse Live..."),
  LISTENING("Listening"),
  SPEAKING("Coach Speaking"),
  THINKING("Thinking..."),
  ERROR("Connection Issue"),
}

enum class TranscriptRole {
  USER,
  COACH,
  TOOL,
}

data class TranscriptEntry(
  val id: String = UUID.randomUUID().toString(),
  val role: TranscriptRole,
  val text: String,
  val isPartial: Boolean = false,
)

data class VoiceCoachUiState(
  val status: VoiceSessionStatus = VoiceSessionStatus.IDLE,
  val isSessionActive: Boolean = false,
  val selectedVoice: String = "KORE",
  val transcript: List<TranscriptEntry> = emptyList(),
  val errorMessage: String? = null,
  val sessionWarning: String? = null,
)

class VoiceCoachViewModel(
  private val repository: StudyRepository,
  private val appContext: Context,
  private val currentUserId: String,
  private val displayName: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(VoiceCoachUiState())
  val uiState: StateFlow<VoiceCoachUiState> = _uiState.asStateFlow()

  private var latestSubjects: List<Subject> = emptyList()
  private var latestSlots: List<TimetableSlot> = emptyList()
  private var latestTasks: List<StudyTask> = emptyList()
  private var latestSpots: List<StudySpot> = emptyList()
  private var onNavigateFocusCallback: ((subjectId: String, subjectName: String, taskId: String, taskTitle: String, taskType: TaskType?) -> Unit)? = null

  private var liveSession: LiveSession? = null
  private var connectJob: Job? = null

  private val studyCoachTools: List<Tool> by lazy {
    listOf(
      Tool.functionDeclarations(
        listOf(
          FunctionDeclaration(
            name = "get_study_overview",
            description = "Returns the student's current subjects, weekly timetable classes, upcoming assignments and exams, and pinned campus study spots.",
            parameters = emptyMap(),
          ),
          FunctionDeclaration(
            name = "create_study_task",
            description = "Creates a new categorized study task (Exam, Assignment, Reading, Project, or Lab) in StudyPulse with a pre-deadline notification.",
            parameters = mapOf(
              "title" to Schema.string("Short title of the study task"),
              "subjectName" to Schema.string("Matching subject name or General Study"),
              "type" to Schema.enumeration(TaskType.entries.map { it.name }, "Task category"),
              "priority" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH"), "Priority level"),
              "hoursFromNow" to Schema.integer("Hours until the deadline from now, e.g. 24 or 48"),
            ),
          ),
          FunctionDeclaration(
            name = "start_focus_session_target",
            description = "Sets the Pomodoro Focus Studio target to a subject or study task so the student can start focusing.",
            parameters = mapOf(
              "subjectName" to Schema.string("Subject name to focus on"),
              "taskTitle" to Schema.string("Optional task title to link"),
            ),
            optionalParameters = listOf("taskTitle"),
          ),
        )
      )
    )
  }

  fun updateLiveContext(
    subjects: List<Subject>,
    slots: List<TimetableSlot>,
    tasks: List<StudyTask>,
    spots: List<StudySpot>,
    onStartFocusTarget: (subjectId: String, subjectName: String, taskId: String, taskTitle: String, taskType: TaskType?) -> Unit,
  ) {
    latestSubjects = subjects
    latestSlots = slots
    latestTasks = tasks
    latestSpots = spots
    onNavigateFocusCallback = onStartFocusTarget
  }

  fun selectVoice(voiceName: String) {
    _uiState.update { it.copy(selectedVoice = voiceName) }
  }

  fun clearError() {
    _uiState.update {
      it.copy(
        errorMessage = null,
        status = if (it.isSessionActive) VoiceSessionStatus.LISTENING else VoiceSessionStatus.IDLE,
      )
    }
  }

  private fun buildSystemPrompt(): String {
    val firstName = displayName.substringBefore(" ").ifBlank { "Scholar" }
    val pendingTasks = latestTasks.filter { !it.completed }.take(6)
    val taskSummary =
      if (pendingTasks.isEmpty()) "No pending assignments or exams."
      else pendingTasks.joinToString("; ") { "${it.title} (${it.type}, ${it.subjectName}, ${formatDueSummary(it.dueAtMillis)})" }
    val classSummary =
      if (latestSlots.isEmpty()) "No timetable classes added yet."
      else latestSlots.take(8).joinToString("; ") { "${dayLabelShort(it.dayOfWeek.toInt())} ${formatMinutesOfDay(it.startMinutes.toInt())} ${it.subjectName} (${it.room.ifBlank { "Campus" }})" }
    val spotSummary =
      if (latestSpots.isEmpty()) "No saved map spots yet."
      else latestSpots.take(6).joinToString("; ") { "${it.name} [${it.category}]" }

    return """
      You are StudyPulse Voice Coach — an energetic, concise, encouraging real-time study coach, oral quizzer, and timetable helper for $firstName.
      Keep spoken responses natural, structured, and concise (2-4 sentences unless quizzing or explaining a concept step by step).
      When quizzing orally, ask one focused question at a time and wait for the student's answer before giving warm feedback.
      You can call get_study_overview, create_study_task, and start_focus_session_target when helpful.
      Current context:
      - Subjects: ${latestSubjects.joinToString(", ") { it.name }.ifBlank { "None yet" }}
      - Schedule: $classSummary
      - Upcoming deadlines: $taskSummary
      - Pinned study spots: $spotSummary
    """.trimIndent()
  }

  @SuppressLint("MissingPermission")
  fun startVoiceSession(initialPrompt: String? = null) {
    val hasMic =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) {
      _uiState.update {
        it.copy(
          status = VoiceSessionStatus.ERROR,
          errorMessage = "Microphone permission is required for live voice coaching.",
        )
      }
      return
    }

    if (_uiState.value.isSessionActive && liveSession != null) {
      if (!initialPrompt.isNullOrBlank()) {
        sendTextMessage(initialPrompt)
      }
      return
    }

    connectJob?.cancel()
    connectJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(
            status = VoiceSessionStatus.CONNECTING,
            isSessionActive = true,
            errorMessage = null,
            sessionWarning = null,
          )
        }

        try {
          val selectedVoice = _uiState.value.selectedVoice
          val liveModel =
            Firebase.ai.liveModel(
              modelName = LIVE_MODEL_NAME,
              generationConfig =
                liveGenerationConfig {
                  responseModality = ResponseModality.AUDIO
                  speechConfig = SpeechConfig(voice = Voice(selectedVoice))
                  inputAudioTranscription = AudioTranscriptionConfig()
                  outputAudioTranscription = AudioTranscriptionConfig()
                },
              tools = studyCoachTools,
              systemInstruction = content { text(buildSystemPrompt()) },
            )

          val session = withTimeout(CONNECT_TIMEOUT_MS) { liveModel.connect() }
          liveSession = session

          val config =
            liveAudioConversationConfig {
              enableInterruptions = true
              functionCallHandler = ::handleToolCall
              transcriptHandler = { inputTranscript, outputTranscript ->
                val userText = inputTranscript?.text?.trim().orEmpty()
                val coachText = outputTranscript?.text?.trim().orEmpty()
                if (userText.isNotEmpty()) {
                  appendTranscript(TranscriptRole.USER, userText)
                  _uiState.update { state ->
                    if (state.isSessionActive) state.copy(status = VoiceSessionStatus.THINKING) else state
                  }
                }
                if (coachText.isNotEmpty()) {
                  appendTranscript(TranscriptRole.COACH, coachText)
                  _uiState.update { state ->
                    if (state.isSessionActive) state.copy(status = VoiceSessionStatus.SPEAKING) else state
                  }
                }
              }
              goAwayHandler = { _ ->
                _uiState.update {
                  it.copy(sessionWarning = "Live session nearing limit — reconnect soon to continue.")
                }
              }
            }

          withContext(Dispatchers.IO) {
            session.startAudioConversation(config)
          }

          _uiState.update {
            it.copy(
              status = VoiceSessionStatus.LISTENING,
              isSessionActive = true,
              errorMessage = null,
            )
          }

          if (!initialPrompt.isNullOrBlank()) {
            appendTranscript(TranscriptRole.USER, initialPrompt)
            session.send(initialPrompt)
          }
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Timed out connecting to gemini-3.8-live", e)
          cleanupSessionOnly()
          _uiState.update {
            it.copy(
              status = VoiceSessionStatus.ERROR,
              isSessionActive = false,
              errorMessage = "Timed out connecting to Gemini Live. Please check your connection and retry.",
            )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Failed to start gemini-3.8-live session", e)
          cleanupSessionOnly()
          _uiState.update {
            it.copy(
              status = VoiceSessionStatus.ERROR,
              isSessionActive = false,
              errorMessage = e.localizedMessage ?: "Unable to connect to Gemini Live voice service.",
            )
          }
        }
      }
  }

  fun sendTextMessage(message: String) {
    val trimmed = message.trim()
    if (trimmed.isEmpty()) return

    val session = liveSession
    if (session == null || session.isClosed()) {
      startVoiceSession(initialPrompt = trimmed)
      return
    }

    viewModelScope.launch {
      try {
        appendTranscript(TranscriptRole.USER, trimmed)
        _uiState.update { it.copy(status = VoiceSessionStatus.THINKING) }
        withTimeout(SEND_TIMEOUT_MS) {
          session.send(trimmed)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed sending prompt to live session", e)
        _uiState.update {
          it.copy(
            status = VoiceSessionStatus.ERROR,
            errorMessage = e.localizedMessage ?: "Failed to send message.",
          )
        }
      }
    }
  }

  fun stopVoiceSession() {
    connectJob?.cancel()
    viewModelScope.launch {
      cleanupSessionOnly()
      _uiState.update {
        it.copy(
          status = VoiceSessionStatus.IDLE,
          isSessionActive = false,
        )
      }
    }
  }

  private suspend fun cleanupSessionOnly() {
    val session = liveSession
    liveSession = null
    if (session != null) {
      try {
        session.stopAudioConversation()
      } catch (e: Exception) {
        Log.w(TAG, "Error stopping audio conversation", e)
      }
      try {
        session.close()
      } catch (e: Exception) {
        Log.w(TAG, "Error closing LiveSession", e)
      }
    }
  }

  private fun appendTranscript(role: TranscriptRole, chunk: String) {
    _uiState.update { state ->
      val current = state.transcript.toMutableList()
      val last = current.lastOrNull()
      if (last != null && last.role == role && role != TranscriptRole.TOOL) {
        val separator =
          if (last.text.endsWith(" ") || chunk.startsWith(" ") || chunk.firstOrNull() in listOf('.', ',', '!', '?', '\'', ':', ';')) ""
          else " "
        current[current.lastIndex] = last.copy(text = (last.text + separator + chunk).trim())
      } else {
        current.add(TranscriptEntry(role = role, text = chunk))
      }
      state.copy(transcript = current)
    }
  }

  private fun handleToolCall(call: FunctionCallPart): FunctionResponsePart {
    return when (call.name) {
      "get_study_overview" -> {
        appendTranscript(TranscriptRole.TOOL, "Checked timetable, deadlines & map spots")
        val tasksArr =
          JsonArray(
            latestTasks.filter { !it.completed }.take(8).map { task ->
              JsonObject(
                mapOf(
                  "title" to JsonPrimitive(task.title),
                  "subject" to JsonPrimitive(task.subjectName),
                  "type" to JsonPrimitive(task.type),
                  "priority" to JsonPrimitive(task.priority),
                  "dueSummary" to JsonPrimitive(formatDueSummary(task.dueAtMillis)),
                )
              )
            }
          )
        val slotsArr =
          JsonArray(
            latestSlots.take(10).map { slot ->
              JsonObject(
                mapOf(
                  "subject" to JsonPrimitive(slot.subjectName),
                  "day" to JsonPrimitive(dayLabelShort(slot.dayOfWeek.toInt())),
                  "time" to JsonPrimitive("${formatMinutesOfDay(slot.startMinutes.toInt())} - ${formatMinutesOfDay(slot.endMinutes.toInt())}"),
                  "room" to JsonPrimitive(slot.room),
                )
              )
            }
          )
        val spotsArr =
          JsonArray(
            latestSpots.take(8).map { spot ->
              JsonObject(
                mapOf(
                  "name" to JsonPrimitive(spot.name),
                  "category" to JsonPrimitive(spot.category),
                  "address" to JsonPrimitive(spot.address),
                )
              )
            }
          )
        FunctionResponsePart(
          name = call.name,
          response =
            JsonObject(
              mapOf(
                "pendingTasks" to tasksArr,
                "timetableSlots" to slotsArr,
                "campusStudySpots" to spotsArr,
              )
            ),
        )
      }

      "create_study_task" -> {
        val title = call.args["title"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Study Task" }
        val subjQuery = call.args["subjectName"]?.jsonPrimitive?.content?.trim().orEmpty()
        val typeRaw = call.args["type"]?.jsonPrimitive?.content?.uppercase().orEmpty()
        val priorityRaw = call.args["priority"]?.jsonPrimitive?.content?.uppercase().orEmpty()
        val hours = (call.args["hoursFromNow"]?.jsonPrimitive?.intOrNull ?: 24).coerceIn(1, 720)

        val matchedSubj =
          latestSubjects.firstOrNull { it.name.equals(subjQuery, ignoreCase = true) }
            ?: latestSubjects.firstOrNull { it.name.contains(subjQuery, ignoreCase = true) }
        val resolvedType = TaskType.fromRaw(typeRaw)
        val safeType = resolvedType.name
        val safePriority =
          when (priorityRaw) {
            TaskPriority.HIGH.name -> TaskPriority.HIGH.name
            TaskPriority.LOW.name -> TaskPriority.LOW.name
            else -> TaskPriority.MEDIUM.name
          }
        val dueAtMillis = System.currentTimeMillis() + hours * 3_600_000L

        viewModelScope.launch {
          try {
            val taskId =
              repository.addTask(
                userId = currentUserId,
                subjectId = matchedSubj?.id ?: "",
                subjectName = matchedSubj?.name ?: subjQuery.ifBlank { "General Study" },
                colorHex = matchedSubj?.colorHex ?: 0xFF4338CAL,
                title = title,
                notes = "Added via Voice Coach",
                type = safeType,
                priority = safePriority,
                dueAtMillis = dueAtMillis,
                reminderLeadMinutes = 60L,
              )
            DeadlineReminderScheduler.scheduleTaskReminder(
              context = appContext,
              taskId = taskId,
              title = title,
              subjectName = matchedSubj?.name ?: "General Study",
              typeLabel = resolvedType.label,
              dueAtMillis = dueAtMillis,
              leadMinutes = 60L,
            )
          } catch (e: Exception) {
            Log.e(TAG, "Voice tool failed adding task", e)
          }
        }
        appendTranscript(TranscriptRole.TOOL, "Added $safeType: $title")
        FunctionResponsePart(
          name = call.name,
          response =
            JsonObject(
              mapOf(
                "success" to JsonPrimitive(true),
                "message" to JsonPrimitive("Added '$title' due in ${hours}h with a 1h reminder."),
              )
            ),
        )
      }

      "start_focus_session_target" -> {
        val subjQuery = call.args["subjectName"]?.jsonPrimitive?.content?.trim().orEmpty()
        val taskQuery = call.args["taskTitle"]?.jsonPrimitive?.content?.trim().orEmpty()
        val matchedSubj =
          latestSubjects.firstOrNull { it.name.contains(subjQuery, ignoreCase = true) }
        val matchedTask =
          if (taskQuery.isNotBlank()) latestTasks.firstOrNull { it.title.contains(taskQuery, ignoreCase = true) }
          else null

        val subjId = matchedSubj?.id ?: ""
        val subjName = matchedSubj?.name ?: subjQuery.ifBlank { "General Study" }
        val taskId = matchedTask?.id ?: ""
        val taskTitle = matchedTask?.title ?: taskQuery
        val taskType = matchedTask?.type?.let { TaskType.fromRaw(it) }

        viewModelScope.launch(Dispatchers.Main) {
          onNavigateFocusCallback?.invoke(subjId, subjName, taskId, taskTitle, taskType)
        }
        appendTranscript(TranscriptRole.TOOL, "Linked Focus Studio to $subjName")
        FunctionResponsePart(
          name = call.name,
          response =
            JsonObject(
              mapOf(
                "success" to JsonPrimitive(true),
                "subject" to JsonPrimitive(subjName),
              )
            ),
        )
      }

      else ->
        FunctionResponsePart(
          name = call.name,
          response = JsonObject(mapOf("error" to JsonPrimitive("Unknown tool: ${call.name}"))),
        )
    }
  }

  override fun onCleared() {
    connectJob?.cancel()
    val session = liveSession
    liveSession = null
    if (session != null) {
      try {
        session.stopAudioConversation()
      } catch (_: Exception) {}
    }
    super.onCleared()
  }

  private companion object {
    const val TAG = "VoiceCoachVM"
    const val LIVE_MODEL_NAME = "gemini-3.8-live"
    const val CONNECT_TIMEOUT_MS = 30_000L
    const val SEND_TIMEOUT_MS = 20_000L
  }
}
