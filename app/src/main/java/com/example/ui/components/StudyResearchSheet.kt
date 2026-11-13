package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.SearchGroundingUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyResearchSheet(
  state: SearchGroundingUiState,
  initialQuery: String,
  subjectHint: String,
  onSearch: (query: String, subjectContext: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var queryInput by remember(initialQuery) { mutableStateOf(initialQuery) }

  LaunchedEffect(initialQuery) {
    if (initialQuery.isNotBlank() && state.query != initialQuery && !state.isLoading) {
      onSearch(initialQuery, subjectHint)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.background,
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      modifier =
        Modifier.fillMaxSize()
          .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Rounded.TravelExplore,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp),
            )
            Column {
              Text("Google Search Study Research", style = MaterialTheme.typography.titleLarge)
              Text(
                text = "Up-to-date answers & sources powered by Gemini",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp).testTag("close_study_research_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close research panel")
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = queryInput,
            onValueChange = { queryInput = it },
            placeholder = { Text("Search formula, theorem, exam topic...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("research_query_input"),
          )
          Button(
            onClick = { onSearch(queryInput, subjectHint) },
            enabled = queryInput.isNotBlank() && !state.isLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 52.dp).testTag("research_submit_button"),
          ) {
            Text("Search")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val suggestions =
            listOf(
              "Pomodoro active recall study techniques",
              "Key calculus derivative rules cheat sheet",
              "How to structure a college lab report",
            )
          suggestions.forEachIndexed { index, prompt ->
            FilterChip(
              selected = state.query == prompt,
              onClick = {
                queryInput = prompt
                onSearch(prompt, subjectHint)
              },
              label = { Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("research_preset_$index"),
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (state.isLoading) {
            item {
              Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator()
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(
                    text = "Searching Google & synthesizing study notes...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          state.errorMessage?.let { error ->
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = error,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(14.dp),
                )
              }
            }
          }

          if (state.answerText.isNotBlank()) {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("research_answer_card"),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Text(
                    text = "Grounded Summary",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = state.answerText,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  if (state.webQueries.isNotEmpty()) {
                    Text(
                      text = "Queries: ${state.webQueries.joinToString(" • ")}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          if (state.sources.isNotEmpty()) {
            item {
              Text(
                text = "Verified Web Sources (${state.sources.size})",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            items(state.sources, key = { it.uri }) { source ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.uri)))
                    }
                    .testTag("research_source_item"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      Icons.Rounded.Public,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = source.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = source.domain.ifBlank { source.uri },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open source link",
                    tint = MaterialTheme.colorScheme.primary,
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
