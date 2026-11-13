@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusSession
import com.example.data.model.StudyTask
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.ui.components.CategoryHoursBarChart
import com.example.ui.components.CategoryHoursBarItem
import com.example.ui.components.WeeklyTrendLineChart
import com.example.ui.components.adaptiveCategoryColor
import com.example.ui.components.categoryIcon
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class CategoryWeeklyStat(
  val taskType: TaskType,
  val currentMinutes: Int,
  val priorMinutes: Int,
  val sessionCount: Int,
  val shareFraction: Float,
) {
  val currentHours: Float
    get() = currentMinutes / 60f

  val priorHours: Float
    get() = priorMinutes / 60f

  val deltaHours: Float
    get() = (currentMinutes - priorMinutes) / 60f
}

data class WeeklyCategorySummaryData(
  val currentTotalMinutes: Int,
  val priorTotalMinutes: Int,
  val currentDayKeys: List<Pair<String, String>>,
  val priorDayKeys: List<String>,
  val categoryStats: List<CategoryWeeklyStat>,
  val currentMinutesByDayAndCategory: Map<String, Map<TaskType, Int>>,
  val priorMinutesByDayAndCategory: Map<String, Map<TaskType, Int>>,
) {
  val currentTotalHours: Float
    get() = currentTotalMinutes / 60f

  val priorTotalHours: Float
    get() = priorTotalMinutes / 60f

  val deltaHours: Float
    get() = (currentTotalMinutes - priorTotalMinutes) / 60f

  val deltaPercent: Int?
    get() =
      if (priorTotalMinutes <= 0) null
      else (((currentTotalMinutes - priorTotalMinutes).toFloat() / priorTotalMinutes.toFloat()) * 100f).roundToInt()

  val dailyAverageHours: Float
    get() = currentTotalHours / 7f

  val topCategory: CategoryWeeklyStat?
    get() = categoryStats.filter { it.currentMinutes > 0 }.maxByOrNull { it.currentMinutes }

  fun dailyHoursFor(category: TaskType?): Pair<List<Pair<String, Float>>, List<Float>> {
    val currentSeries =
      currentDayKeys.map { (dateKey, dayLabel) ->
        val dayMap = currentMinutesByDayAndCategory[dateKey].orEmpty()
        val mins = if (category == null) dayMap.values.sum() else (dayMap[category] ?: 0)
        dayLabel to (mins / 60f)
      }
    val priorSeries =
      priorDayKeys.map { dateKey ->
        val dayMap = priorMinutesByDayAndCategory[dateKey].orEmpty()
        val mins = if (category == null) dayMap.values.sum() else (dayMap[category] ?: 0)
        mins / 60f
      }
    return currentSeries to priorSeries
  }
}

fun resolveSessionCategory(
  session: FocusSession,
  taskTypesById: Map<String, String>,
): TaskType {
  if (session.taskType.isNotBlank()) {
    return TaskType.fromRaw(session.taskType)
  }
  val linkedType = taskTypesById[session.taskId]
  if (!linkedType.isNullOrBlank()) {
    return TaskType.fromRaw(linkedType)
  }
  return TaskType.ASSIGNMENT
}

fun computeWeeklyCategorySummary(
  sessions: List<FocusSession>,
  tasks: List<StudyTask>,
): WeeklyCategorySummaryData {
  val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  val dayFmt = SimpleDateFormat("EEE", Locale.US)

  val cal = Calendar.getInstance()
  cal.add(Calendar.DAY_OF_YEAR, -13)
  val priorDayKeys =
    (0..6).map {
      val key = keyFmt.format(cal.time)
      cal.add(Calendar.DAY_OF_YEAR, 1)
      key
    }
  val currentDayKeys =
    (0..6).map {
      val key = keyFmt.format(cal.time)
      val label = dayFmt.format(cal.time)
      cal.add(Calendar.DAY_OF_YEAR, 1)
      key to label
    }

  val currentKeySet = currentDayKeys.map { it.first }.toSet()
  val priorKeySet = priorDayKeys.toSet()
  val taskTypesById = tasks.associate { it.id to it.type }

  val currentMinutesByCat = mutableMapOf<TaskType, Int>()
  val priorMinutesByCat = mutableMapOf<TaskType, Int>()
  val sessionCountByCat = mutableMapOf<TaskType, Int>()
  val currentDayCatMinutes = mutableMapOf<String, MutableMap<TaskType, Int>>()
  val priorDayCatMinutes = mutableMapOf<String, MutableMap<TaskType, Int>>()

  sessions.forEach { session ->
    val mins = session.durationMinutes.toInt().coerceAtLeast(0)
    if (mins <= 0) return@forEach
    val category = resolveSessionCategory(session, taskTypesById)
    val dateKey = session.dateKey

    if (dateKey in currentKeySet) {
      currentMinutesByCat[category] = (currentMinutesByCat[category] ?: 0) + mins
      sessionCountByCat[category] = (sessionCountByCat[category] ?: 0) + 1
      val dayMap = currentDayCatMinutes.getOrPut(dateKey) { mutableMapOf() }
      dayMap[category] = (dayMap[category] ?: 0) + mins
    } else if (dateKey in priorKeySet) {
      priorMinutesByCat[category] = (priorMinutesByCat[category] ?: 0) + mins
      val dayMap = priorDayCatMinutes.getOrPut(dateKey) { mutableMapOf() }
      dayMap[category] = (dayMap[category] ?: 0) + mins
    }
  }

  val currentTotalMinutes = currentMinutesByCat.values.sum()
  val priorTotalMinutes = priorMinutesByCat.values.sum()

  val categoryStats =
    TaskType.entries.map { type ->
      val curMins = currentMinutesByCat[type] ?: 0
      val prevMins = priorMinutesByCat[type] ?: 0
      val share =
        if (currentTotalMinutes <= 0) 0f
        else (curMins.toFloat() / currentTotalMinutes.toFloat()).coerceIn(0f, 1f)
      CategoryWeeklyStat(
        taskType = type,
        currentMinutes = curMins,
        priorMinutes = prevMins,
        sessionCount = sessionCountByCat[type] ?: 0,
        shareFraction = share,
      )
    }

  return WeeklyCategorySummaryData(
    currentTotalMinutes = currentTotalMinutes,
    priorTotalMinutes = priorTotalMinutes,
    currentDayKeys = currentDayKeys,
    priorDayKeys = priorDayKeys,
    categoryStats = categoryStats,
    currentMinutesByDayAndCategory = currentDayCatMinutes,
    priorMinutesByDayAndCategory = priorDayCatMinutes,
  )
}

@Composable
fun WeeklySummaryScreen(
  sessionsState: UiState<List<FocusSession>>,
  tasksState: UiState<List<StudyTask>>,
  onBack: () -> Unit,
  onStartFocus: () -> Unit,
) {
  val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
  val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
  val summary = remember(sessions, tasks) { computeWeeklyCategorySummary(sessions, tasks) }

  var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedCategory = selectedCategoryName?.let { raw -> TaskType.entries.firstOrNull { it.name == raw } }

  val categoryBarColors =
    TaskType.entries.associateWith { it.adaptiveCategoryColor().toArgb() }
  val barItems =
    remember(summary, categoryBarColors) {
      summary.categoryStats.map { stat ->
        CategoryHoursBarItem(
          taskType = stat.taskType,
          hours = stat.currentHours,
          colorArgb = categoryBarColors[stat.taskType] ?: Color(stat.taskType.accentColorHex).toArgb(),
        )
      }
    }

  val (trendCurrentHours, trendPriorHours) =
    remember(summary, selectedCategory) { summary.dailyHoursFor(selectedCategory) }
  val trendAccentColor =
    selectedCategory?.adaptiveCategoryColor() ?: MaterialTheme.colorScheme.primary

  val rankedCategories =
    remember(summary) { summary.categoryStats.sortedByDescending { it.currentMinutes } }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Weekly Summary") },
      navigationIcon = {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("weekly_summary_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Rolling 7-Day Card with Prior-Week Delta
      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(24.dp))
              .background(
                Brush.linearGradient(
                  listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                  )
                )
              )
              .padding(20.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Rolling 7-Day Study Total",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White.copy(alpha = 0.88f),
                )
                Text(
                  text = String.format(Locale.US, "%.1fh", summary.currentTotalHours),
                  fontFamily = JetBrainsMonoFontFamily,
                  style = MaterialTheme.typography.displayMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
              }

              val delta = summary.deltaHours
              val pct = summary.deltaPercent
              val isUp = delta >= 0f
              Surface(
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector =
                      if (isUp) Icons.AutoMirrored.Rounded.TrendingUp
                      else Icons.AutoMirrored.Rounded.TrendingDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                  val deltaText =
                    when {
                      summary.priorTotalMinutes == 0 && summary.currentTotalMinutes == 0 -> "No prior 7d"
                      summary.priorTotalMinutes == 0 -> "New baseline"
                      else -> {
                        val sign = if (delta >= 0f) "+" else ""
                        val pctPart = pct?.let { " ($sign$it%)" } ?: ""
                        String.format(Locale.US, "%s%.1fh%s", sign, delta, pctPart)
                      }
                    }
                  Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                  )
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(
                    text = "Daily Average",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                  )
                  Text(
                    text = String.format(Locale.US, "%.1fh / day", summary.dailyAverageHours),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                  )
                }
              }
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(
                    text = "Top Category",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                  )
                  Text(
                    text =
                      summary.topCategory?.let {
                        String.format(Locale.US, "%s (%.1fh)", it.taskType.label, it.currentHours)
                      } ?: "No sessions yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }
      }

      if (summary.currentTotalMinutes == 0 && summary.priorTotalMinutes == 0) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Rounded.AutoGraph,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "No study sprints logged in the last 14 days",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Text(
                text = "Pick a category in Focus Studio or launch a sprint from any Exam, Assignment, Reading, Project, or Lab task to populate your weekly category charts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = onStartFocus,
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("weekly_summary_empty_focus_button"),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Focus Sprint")
              }
            }
          }
        }
      }

      // Filter Chips syncing Category BarChart & Trend LineChart
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Focus Filter",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategoryName = null },
                label = { Text("All Categories") },
                modifier = Modifier.testTag("weekly_summary_filter_all"),
              )
            }
            items(TaskType.entries) { type ->
              val tint = type.adaptiveCategoryColor()
              val isSelected = selectedCategory == type
              FilterChip(
                selected = isSelected,
                onClick = {
                  selectedCategoryName = if (isSelected) null else type.name
                },
                leadingIcon = {
                  Icon(
                    imageVector = type.categoryIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tint.copy(alpha = 0.16f),
                    selectedLabelColor = tint,
                    selectedLeadingIconColor = tint,
                  ),
                label = { Text(type.label) },
                modifier = Modifier.testTag("weekly_summary_filter_${type.name.lowercase()}"),
              )
            }
          }
        }
      }

      // MPAndroidChart #1: Total Study Hours per Category BarChart
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth().testTag("weekly_category_bar_chart_card"),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text("Total Hours by Category", style = MaterialTheme.typography.titleLarge)
            Text(
              text =
                if (selectedCategory == null) "Rolling 7-day study hours across all 5 categories"
                else "Highlighting ${selectedCategory.label} against other categories",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            CategoryHoursBarChart(
              categoryItems = barItems,
              selectedCategory = selectedCategory,
            )
          }
        }
      }

      // MPAndroidChart #2: Rolling 7-Day vs Prior Week LineChart
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth().testTag("weekly_trend_line_chart_card"),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text("7-Day Productivity Trend", style = MaterialTheme.typography.titleLarge)
            Text(
              text =
                if (selectedCategory == null) "Daily study hours vs prior 7-day period (all categories)"
                else "${selectedCategory.label} daily hours vs prior 7-day period",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            WeeklyTrendLineChart(
              currentWeekDailyHours = trendCurrentHours,
              priorWeekDailyHours = trendPriorHours,
              accentColor = trendAccentColor,
            )
          }
        }
      }

      // Category-by-Category Breakdown Cards
      item {
        Text("Category Breakdown", style = MaterialTheme.typography.titleLarge)
      }

      items(rankedCategories, key = { it.taskType.name }) { stat ->
        val categoryColor = stat.taskType.adaptiveCategoryColor()
        val isSelected = selectedCategory == stat.taskType
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border =
            if (isSelected) BorderStroke(1.5.dp, categoryColor)
            else null,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .clickable {
                selectedCategoryName = if (isSelected) null else stat.taskType.name
              }
              .testTag("weekly_category_row_${stat.taskType.name.lowercase()}"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(38.dp)
                      .clip(CircleShape)
                      .background(categoryColor.copy(alpha = 0.14f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = stat.taskType.categoryIcon(),
                    contentDescription = stat.taskType.label,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp),
                  )
                }
                Column {
                  Text(stat.taskType.label, style = MaterialTheme.typography.titleMedium)
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      Icons.Rounded.Timer,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text =
                        if (stat.sessionCount == 1) "1 sprint"
                        else "${stat.sessionCount} sprints",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = String.format(Locale.US, "%.1fh", stat.currentHours),
                  fontFamily = JetBrainsMonoFontFamily,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = categoryColor,
                )
                val diff = stat.deltaHours
                val diffLabel =
                  when {
                    stat.priorMinutes == 0 && stat.currentMinutes == 0 -> "0.0h vs last wk"
                    stat.priorMinutes == 0 -> "New this wk"
                    else -> {
                      val sign = if (diff >= 0f) "+" else ""
                      String.format(Locale.US, "%s%.1fh vs last wk", sign, diff)
                    }
                  }
                Text(
                  text = diffLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (diff > 0.05f) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              LinearProgressIndicator(
                progress = { stat.shareFraction },
                color = categoryColor,
                trackColor = categoryColor.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(50)),
              )
              Text(
                text = "${(stat.shareFraction * 100f).roundToInt()}%",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
