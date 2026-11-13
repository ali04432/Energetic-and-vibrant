@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ChatThread
import com.example.data.model.ChatWebSource
import com.example.data.model.CoachPersona
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.StudyCoachUiState
import com.example.ui.viewmodel.StudyCoachViewModel

@Composable
fun StudyCoachChatScreen(
  uiState: StudyCoachUiState,
  threadsState: UiState<List<ChatThread>>,
  messages: List<ChatMessageRecord>,
  subjects: List<Subject>,
  onBack: () -> Unit,
  onSelectPersona: (CoachPersona) -> Unit,
  onToggleWebSearch: () -> Unit,
  onSelectSubjectContext: (subjectId: String, subjectName: String) -> Unit,
  onSelectThread: (ChatThread) -> Unit,
  onStartNewThread: () -> Unit,
  onDeleteThread: (String) -> Unit,
  onSendMessage: (String) -> Unit,
  onClearError: () -> Unit,
) {
  val context = LocalContext.current
  val threads = (threadsState as? UiState.Success)?.data.orEmpty()
  val activeThread = threads.firstOrNull { it.id == uiState.activeThreadId }
  val listState = rememberLazyListState()

  var messageInput by remember { mutableStateOf("") }
  var showHistorySheet by remember { mutableStateOf(false) }

  LaunchedEffect(messages.size, uiState.streamingText, uiState.isSending) {
    val totalCount = messages.size + if (uiState.isSending) 1 else 0
    if (totalCount > 0) {
      listState.animateScrollToItem((totalCount - 1).coerceAtLeast(0))
    }
  }

  Column(modifier = Modifier.fillMaxSize().imePadding()) {
    TopAppBar(
      title = {
        Column {
          Text(
            text = "Study Coach",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = activeThread?.title ?: "New Conversation • ${uiState.selectedPersona.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
      navigationIcon = {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("coach_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        IconButton(
          onClick = { showHistorySheet = true },
          modifier = Modifier.size(48.dp).testTag("coach_history_button"),
        ) {
          Icon(Icons.Rounded.History, contentDescription = "Saved study chat history")
        }
        IconButton(
          onClick = onStartNewThread,
          modifier = Modifier.size(48.dp).testTag("coach_new_chat_button"),
        ) {
          Icon(Icons.Rounded.AddComment, contentDescription = "Start new chat")
        }
      },
    )

    // Persona Selector Row (Study Tutor / Exam Coach / Deep Solver)
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(CoachPersona.entries) { persona ->
        val selected = uiState.selectedPersona == persona
        FilterChip(
          selected = selected,
          onClick = { onSelectPersona(persona) },
          leadingIcon = {
            Icon(
              imageVector = personaIcon(persona),
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          label = { Text(persona.label) },
          modifier = Modifier.testTag("coach_persona_${persona.name.lowercase()}"),
        )
      }
    }

    // Subject Scope Row (Optional)
    if (subjects.isNotEmpty()) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        item {
          FilterChip(
            selected = uiState.selectedSubjectId.isBlank(),
            onClick = { onSelectSubjectContext("", "") },
            label = { Text("All Courses") },
          )
        }
        items(subjects, key = { it.id }) { subj ->
          FilterChip(
            selected = uiState.selectedSubjectId == subj.id,
            onClick = { onSelectSubjectContext(subj.id, subj.name) },
            label = { Text(subj.name) },
          )
        }
      }
    }

    // Error Banner
    uiState.errorMessage?.let { err ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onClearError, modifier = Modifier.size(48.dp)) {
            Icon(
              Icons.Rounded.Close,
              contentDescription = "Dismiss error",
              tint = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }
    }

    // Conversation Thread List
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("coach_message_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (messages.isEmpty() && !uiState.isSending) {
        item {
          CoachEmptyState(
            persona = uiState.selectedPersona,
            subjectName = uiState.selectedSubjectName,
            onPickStarter = { starter ->
              onSendMessage(starter)
            },
          )
        }
      } else {
        itemsIndexed(
          items = messages,
          key = { idx, msg -> msg.id.ifBlank { "${msg.sequenceIndex}_$idx" } },
        ) { index, msg ->
          ChatBubbleItem(
            message = msg,
            messageIndex = index,
            onOpenSource = { source ->
              runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.uri)))
              }
            },
          )
        }
      }

      if (uiState.isSending) {
        item {
          StreamingAssistantCard(
            persona = uiState.selectedPersona,
            isWebSearch = uiState.isWebSearchEnabled,
            streamingText = uiState.streamingText,
          )
        }
      }
    }

    // Fast Suggested Follow-ups (powered by gemini-3.1-flash-lite)
    AnimatedVisibility(visible = uiState.suggestedFollowUps.isNotEmpty() && !uiState.isSending) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(uiState.suggestedFollowUps) { suggestion ->
          AssistChip(
            onClick = { onSendMessage(suggestion) },
            leadingIcon = {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.heightIn(min = 48.dp),
          )
        }
      }
    }

    // Bottom Composer Bar
    Surface(
      tonalElevation = 4.dp,
      shadowElevation = 6.dp,
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = uiState.isWebSearchEnabled,
            onClick = onToggleWebSearch,
            leadingIcon = {
              Icon(
                Icons.Rounded.TravelExplore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text("Web Search") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            modifier = Modifier.testTag("coach_web_search_toggle"),
          )

          Text(
            text =
              if (uiState.isWebSearchEnabled) "gemini-4-hydrogen + Google Search"
              else uiState.selectedPersona.shortTag,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = messageInput,
            onValueChange = { messageInput = it },
            placeholder = { Text("Ask ${uiState.selectedPersona.label} anything...") },
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).testTag("coach_message_input"),
          )
          FilledIconButton(
            onClick = {
              val outgoing = messageInput.trim()
              if (outgoing.isNotEmpty()) {
                messageInput = ""
                onSendMessage(outgoing)
              }
            },
            enabled = !uiState.isSending && messageInput.isNotBlank(),
            modifier = Modifier.size(52.dp).testTag("coach_send_button"),
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.Send,
              contentDescription = "Send message",
            )
          }
        }
      }
    }
  }

  if (showHistorySheet) {
    ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Saved Study Conversations", style = MaterialTheme.typography.titleLarge)
          OutlinedButton(
            onClick = {
              onStartNewThread()
              showHistorySheet = false
            },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("New Thread")
          }
        }

        if (threads.isEmpty()) {
          Text(
            text = "Your study conversations with Gemini are saved automatically per topic.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
          ) {
            items(threads, key = { it.id }) { thread ->
              val isCurrent = thread.id == uiState.activeThreadId
              val persona = CoachPersona.fromRaw(thread.persona)
              Card(
                colors =
                  CardDefaults.cardColors(
                    containerColor =
                      if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  ),
                shape = RoundedCornerShape(16.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                      onSelectThread(thread)
                      showHistorySheet = false
                    }
                    .testTag("coach_thread_item_${thread.id}"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                      text = thread.title,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${persona.label} • ${thread.messageCount} msgs",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    if (thread.lastMessagePreview.isNotBlank()) {
                      Text(
                        text = thread.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  IconButton(
                    onClick = { onDeleteThread(thread.id) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete conversation")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CoachEmptyState(
  persona: CoachPersona,
  subjectName: String,
  onPickStarter: (String) -> Unit,
) {
  val starters =
    when (persona) {
      CoachPersona.STUDY_TUTOR ->
        listOf(
          "Explain the core intuition behind ${subjectName.ifBlank { "today's topic" }} in plain terms",
          "Summarize key formulas and common pitfalls to remember",
          "Create a 20-minute structured study outline for tonight",
        )
      CoachPersona.EXAM_COACH ->
        listOf(
          "Quiz me with 3 progressive practice questions on ${subjectName.ifBlank { "my upcoming exam" }}",
          "Give me a realistic exam scenario and grade my reasoning",
          "Help me identify weak spots with a rapid Socratic drill",
        )
      CoachPersona.DEEP_SOLVER ->
        listOf(
          "Walk me through a rigorous proof or step-by-step derivation",
          "Analyze time & space trade-offs for an algorithm problem",
          "Check every assumption in a tricky STEM derivation step by step",
        )
    }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = personaIcon(persona),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
          Column {
            Text(persona.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
              text = persona.modelName,
              fontFamily = JetBrainsMonoFontFamily,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          text = persona.tagline,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text("Try asking:", style = MaterialTheme.typography.labelLarge)
    starters.forEachIndexed { idx, promptText ->
      OutlinedButton(
        onClick = { onPickStarter(promptText) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("coach_starter_prompt_$idx"),
      ) {
        Text(
          text = promptText,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun ChatBubbleItem(
  message: ChatMessageRecord,
  messageIndex: Int,
  onOpenSource: (ChatWebSource) -> Unit,
) {
  val isUser = message.senderRole == "user"
  val sources =
    remember(message.sourcesJson) { StudyCoachViewModel.decodeSourcesJson(message.sourcesJson) }
  val persona = CoachPersona.fromRaw(message.persona)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      color =
        if (isUser) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
      tonalElevation = if (isUser) 0.dp else 2.dp,
      shadowElevation = if (isUser) 2.dp else 1.dp,
      shape =
        if (isUser) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
        else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
      modifier = Modifier.widthIn(max = 330.dp),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (!isUser) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = personaIcon(persona),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = "${persona.label} • ${message.modelUsed}",
              fontFamily = JetBrainsMonoFontFamily,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            if (message.webSearchUsed) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = "Search",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }

        SelectionContainer {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (isUser) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
          )
        }

        if (sources.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            sources.forEachIndexed { sIdx, src ->
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                modifier =
                  Modifier.heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSource(src) }
                    .testTag("coach_source_${messageIndex}_$sIdx"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = src.title.take(28),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open ${src.title}",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StreamingAssistantCard(
  persona: CoachPersona,
  isWebSearch: Boolean,
  streamingText: String,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
      modifier = Modifier.widthIn(max = 330.dp),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
          Text(
            text =
              if (isWebSearch) "Searching Google & drafting..."
              else "${persona.label} (${persona.modelName})...",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (streamingText.isNotBlank()) {
          Text(
            text = streamingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

private fun personaIcon(persona: CoachPersona): ImageVector =
  when (persona) {
    CoachPersona.STUDY_TUTOR -> Icons.Rounded.School
    CoachPersona.EXAM_COACH -> Icons.Rounded.Quiz
    CoachPersona.DEEP_SOLVER -> Icons.Rounded.Psychology
  }
