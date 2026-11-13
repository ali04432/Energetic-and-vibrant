package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.TranscriptEntry
import com.example.ui.viewmodel.TranscriptRole
import com.example.ui.viewmodel.VoiceCoachUiState
import com.example.ui.viewmodel.VoiceSessionStatus
import kotlin.math.cos
import kotlin.math.sin

private val VOICE_OPTIONS = listOf("KORE", "AOEDE", "CHARON", "FENRIR", "PUCK")
private val QUICK_PROMPTS =
  listOf(
    "Brief me on today",
    "Quiz me on my next exam",
    "Add an assignment",
    "Where should I study?",
  )

@Composable
fun LiveVoiceCoachOverlay(
  state: VoiceCoachUiState,
  onSelectVoice: (String) -> Unit,
  onStartVoice: (String?) -> Unit,
  onStopVoice: () -> Unit,
  onSendText: (String) -> Unit,
  onDismissError: () -> Unit,
  onCloseOverlay: () -> Unit,
) {
  BackHandler { onCloseOverlay() }

  val context = LocalContext.current
  var hasAudioPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var pendingAutoPrompt by remember { mutableStateOf<String?>(null) }
  var typedMessage by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val micLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasAudioPermission = granted
      if (granted) {
        onStartVoice(pendingAutoPrompt)
        pendingAutoPrompt = null
      }
    }

  LaunchedEffect(state.transcript.size) {
    if (state.transcript.isNotEmpty()) {
      listState.animateScrollToItem(state.transcript.lastIndex)
    }
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("live_voice_overlay"),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("StudyPulse Live Coach", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "gemini-3.8-live • Real-Time Audio",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = onCloseOverlay,
          modifier = Modifier.size(48.dp).testTag("close_voice_overlay_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Minimize voice coach")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Voice Selection Pills (when idle)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VOICE_OPTIONS) { voice ->
          FilterChip(
            selected = state.selectedVoice == voice,
            onClick = { if (!state.isSessionActive) onSelectVoice(voice) },
            enabled = !state.isSessionActive,
            label = { Text(voice.lowercase().replaceFirstChar { it.uppercase() }) },
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Animated Voice Orb
      AnimatedVoiceOrb(
        status = state.status,
        modifier = Modifier.size(190.dp),
      )

      Surface(
        color =
          when (state.status) {
            VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer
            VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
          },
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = state.status.label,
          style = MaterialTheme.typography.labelLarge,
          color =
            when (state.status) {
              VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.onSecondaryContainer
              VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
              else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
      }

      state.errorMessage?.let { err ->
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismissError, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Rounded.Close, contentDescription = "Dismiss error")
            }
          }
        }
      }

      state.sessionWarning?.let { warn ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = warn,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Live Scrolling Transcript
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        if (state.transcript.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(34.dp),
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Tap the mic below to talk with your AI Study Coach or pick a quick prompt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        } else {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(state.transcript, key = { it.id }) { entry ->
              TranscriptBubble(entry)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Quick Prompts Strip
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(QUICK_PROMPTS) { prompt ->
          SuggestionChip(
            onClick = {
              if (state.isSessionActive) {
                onSendText(prompt)
              } else if (hasAudioPermission) {
                onStartVoice(prompt)
              } else {
                pendingAutoPrompt = prompt
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            label = { Text(prompt) },
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Text Follow-up + Primary Mic Call Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = typedMessage,
          onValueChange = { typedMessage = it },
          placeholder = { Text("Ask or respond by text...") },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          trailingIcon = {
            IconButton(
              onClick = {
                val msg = typedMessage.trim()
                if (msg.isNotEmpty()) {
                  if (!state.isSessionActive && !hasAudioPermission) {
                    pendingAutoPrompt = msg
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  } else {
                    onSendText(msg)
                  }
                  typedMessage = ""
                }
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send text message")
            }
          },
          modifier = Modifier.weight(1f).testTag("voice_text_input"),
        )

        Button(
          onClick = {
            if (state.isSessionActive) {
              onStopVoice()
            } else if (hasAudioPermission) {
              onStartVoice(null)
            } else {
              micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (state.isSessionActive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            ),
          modifier = Modifier.size(58.dp).testTag("voice_call_toggle_button"),
        ) {
          when (state.status) {
            VoiceSessionStatus.CONNECTING ->
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
            else ->
              Icon(
                imageVector = if (state.isSessionActive) Icons.Rounded.CallEnd else Icons.Rounded.Mic,
                contentDescription = if (state.isSessionActive) "End voice session" else "Start voice session",
              )
          }
        }
      }
    }
  }
}

@Composable
private fun AnimatedVoiceOrb(
  status: VoiceSessionStatus,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
  val pulse by
    infiniteTransition.animateFloat(
      initialValue = 0.86f,
      targetValue = 1.12f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis =
                when (status) {
                  VoiceSessionStatus.SPEAKING -> 620
                  VoiceSessionStatus.THINKING -> 820
                  VoiceSessionStatus.LISTENING -> 1250
                  else -> 2100
                },
              easing = FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_pulse",
    )

  val phase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(animation = tween(5500)),
      label = "orb_phase",
    )

  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val tertiary = MaterialTheme.colorScheme.tertiary

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = size.minDimension * 0.30f

      val ringAlpha =
        when (status) {
          VoiceSessionStatus.SPEAKING -> 0.36f
          VoiceSessionStatus.LISTENING -> 0.28f
          VoiceSessionStatus.CONNECTING, VoiceSessionStatus.THINKING -> 0.22f
          else -> 0.12f
        }

      // Outer harmonic ring
      drawCircle(
        color = secondary.copy(alpha = ringAlpha),
        radius = baseRadius * (1.34f * pulse),
        center = center,
        style = Stroke(width = 3.dp.toPx()),
      )

      // Mid orbiting highlight halo
      val rad = Math.toRadians(phase.toDouble())
      val orbitCenter =
        Offset(
          center.x + (10.dp.toPx() * cos(rad)).toFloat(),
          center.y + (10.dp.toPx() * sin(rad)).toFloat(),
        )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                when (status) {
                  VoiceSessionStatus.SPEAKING -> secondary.copy(alpha = 0.55f)
                  VoiceSessionStatus.THINKING -> tertiary.copy(alpha = 0.45f)
                  else -> primary.copy(alpha = 0.45f)
                },
                Color.Transparent,
              ),
            center = orbitCenter,
            radius = baseRadius * 1.65f,
          ),
        radius = baseRadius * 1.55f,
        center = orbitCenter,
      )

      // Core orb gradient sphere
      drawCircle(
        brush =
          Brush.linearGradient(
            colors =
              when (status) {
                VoiceSessionStatus.SPEAKING -> listOf(secondary, primary)
                VoiceSessionStatus.THINKING -> listOf(tertiary, primary)
                VoiceSessionStatus.ERROR -> listOf(Color(0xFFF43F5E), primary)
                else -> listOf(primary, secondary)
              }
          ),
        radius = baseRadius * (if (status == VoiceSessionStatus.IDLE) 0.96f else pulse * 0.95f),
        center = center,
      )
    }

    Icon(
      imageVector =
        when (status) {
          VoiceSessionStatus.SPEAKING -> Icons.Rounded.GraphicEq
          VoiceSessionStatus.THINKING -> Icons.Rounded.AutoAwesome
          else -> Icons.Rounded.Mic
        },
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(34.dp),
    )
  }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
  when (entry.role) {
    TranscriptRole.TOOL -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = entry.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          )
        }
      }
    }
    TranscriptRole.USER -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
          modifier = Modifier.widthIn(max = 280.dp),
        ) {
          Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          )
        }
      }
    }
    TranscriptRole.COACH -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
          modifier = Modifier.widthIn(max = 296.dp),
        ) {
          Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
              text = "StudyPulse Coach",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = entry.text,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }
  }
}
