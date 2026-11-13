@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ai.configureAppCheck
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.UiState
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.UserPreferences
import com.example.data.repository.StudyRepository
import com.example.notifications.NotificationHelper
import com.example.ui.components.AddSubjectDialog
import com.example.ui.components.AddTaskDialog
import com.example.ui.components.AddTimetableSlotDialog
import com.example.ui.components.LiveVoiceCoachOverlay
import com.example.ui.navigation.AnalyticsRoute
import com.example.ui.navigation.FocusRoute
import com.example.ui.navigation.MapRoute
import com.example.ui.navigation.PlannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SubjectDetailRoute
import com.example.ui.navigation.TaskDetailRoute
import com.example.ui.navigation.TodayRoute
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CampusMapScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubjectDetailScreen
import com.example.ui.screens.TaskDetailScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.StudyPulseTheme
import com.example.ui.viewmodel.FocusTimerViewModel
import com.example.ui.viewmodel.StudyPlannerViewModel
import com.example.ui.viewmodel.VoiceCoachViewModel
import com.example.ui.viewmodel.VoiceSessionStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    NotificationHelper.ensureChannels(this)
    setContent {
      val context = LocalContext.current
      val prefsManager = remember { PreferencesManager(context.applicationContext) }
      val preferences by prefsManager.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())

      StudyPulseTheme(themeMode = preferences.themeMode) {
        StudyPulseRoot(prefsManager = prefsManager)
      }
    }
  }
}

private data class TopLevelDestination(
  val route: Any,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@Composable
fun StudyPulseRoot(prefsManager: PreferencesManager) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    val uid = user.uid
    val displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Scholar"
    val userEmail = user.email ?: ""

    key(uid) {
      MainStudyWorkspace(
        currentUserId = uid,
        displayName = displayName,
        userEmail = userEmail,
        prefsManager = prefsManager,
        onSignOut = {
          signOutUser(credentialManager = credentialManager, scope = scope)
        },
      )
    }
  }
}

@Composable
fun MainStudyWorkspace(
  currentUserId: String,
  displayName: String,
  userEmail: String,
  prefsManager: PreferencesManager,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current

  val plannerViewModel: StudyPlannerViewModel =
    viewModel(
      key = "planner_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            StudyPlannerViewModel(
              repository = StudyRepository(db),
              preferencesManager = prefsManager,
              appContext = app.applicationContext,
              currentUserId = currentUserId,
              displayName = displayName,
            )
          }
        },
    )

  val focusViewModel: FocusTimerViewModel =
    viewModel(
      key = "focus_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            FocusTimerViewModel(
              repository = StudyRepository(db),
              appContext = app.applicationContext,
              currentUserId = currentUserId,
              displayName = displayName,
            )
          }
        },
    )

  val voiceViewModel: VoiceCoachViewModel =
    viewModel(
      key = "voice_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            VoiceCoachViewModel(
              repository = StudyRepository(db),
              appContext = app.applicationContext,
              currentUserId = currentUserId,
              displayName = displayName,
            )
          }
        },
    )

  val userStatsState by plannerViewModel.userStatsState.collectAsStateWithLifecycle()
  val subjectsState by plannerViewModel.subjectsState.collectAsStateWithLifecycle()
  val slotsState by plannerViewModel.slotsState.collectAsStateWithLifecycle()
  val tasksState by plannerViewModel.tasksState.collectAsStateWithLifecycle()
  val spotsState by plannerViewModel.spotsState.collectAsStateWithLifecycle()
  val sessionsState by plannerViewModel.sessionsState.collectAsStateWithLifecycle()
  val preferences by plannerViewModel.preferences.collectAsStateWithLifecycle()
  val selectedDay by plannerViewModel.selectedDayOfWeek.collectAsStateWithLifecycle()
  val selectedFilter by plannerViewModel.selectedTaskFilter.collectAsStateWithLifecycle()
  val actionMessage by plannerViewModel.actionMessage.collectAsStateWithLifecycle()
  val timerState by focusViewModel.uiState.collectAsStateWithLifecycle()
  val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(preferences.focusMinutes, preferences.shortBreakMinutes, preferences.longBreakMinutes) {
    focusViewModel.syncDurations(
      preferences.focusMinutes,
      preferences.shortBreakMinutes,
      preferences.longBreakMinutes,
    )
  }

  var hasNotificationPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }
  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(actionMessage) {
    actionMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      plannerViewModel.clearActionMessage()
    }
  }
  LaunchedEffect(timerState.statusMessage) {
    timerState.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      focusViewModel.clearStatusMessage()
    }
  }

  var showAddSubject by remember { mutableStateOf(false) }
  var showAddSlot by remember { mutableStateOf(false) }
  var showAddTask by remember { mutableStateOf(false) }
  var showVoiceCoach by remember { mutableStateOf(false) }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val destinations =
    remember {
      listOf(
        TopLevelDestination(TodayRoute, "Today", Icons.Rounded.Home, Icons.Outlined.Home, "nav_today"),
        TopLevelDestination(PlannerRoute, "Planner", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_planner"),
        TopLevelDestination(MapRoute, "Map", Icons.Rounded.Map, Icons.Outlined.Map, "nav_map"),
        TopLevelDestination(FocusRoute, "Focus", Icons.Rounded.Timer, Icons.Outlined.Timer, "nav_focus"),
        TopLevelDestination(AnalyticsRoute, "Insights", Icons.Rounded.AutoGraph, Icons.Outlined.AutoGraph, "nav_analytics"),
      )
    }

  val showTopLevelNav =
    currentDest == null ||
      currentDest.hasRoute<TodayRoute>() ||
      currentDest.hasRoute<PlannerRoute>() ||
      currentDest.hasRoute<MapRoute>() ||
      currentDest.hasRoute<FocusRoute>() ||
      currentDest.hasRoute<AnalyticsRoute>()

  fun navigateTopLevel(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(subjectsState, slotsState, tasksState, spotsState) {
    voiceViewModel.updateLiveContext(
      subjects = (subjectsState as? UiState.Success)?.data.orEmpty(),
      slots = (slotsState as? UiState.Success)?.data.orEmpty(),
      tasks = (tasksState as? UiState.Success)?.data.orEmpty(),
      spots = (spotsState as? UiState.Success)?.data.orEmpty(),
      onStartFocusTarget = { subjId, subjName, taskId, taskTitle ->
        focusViewModel.linkStudyTarget(subjId, subjName, taskId, taskTitle)
        showVoiceCoach = false
        navigateTopLevel(FocusRoute)
      },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavRail && showTopLevelNav) {
        NavigationRail(
          modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
          destinations.forEach { dest ->
            val selected =
              when (dest.route) {
                is TodayRoute -> currentDest?.hasRoute<TodayRoute>() == true
                is PlannerRoute -> currentDest?.hasRoute<PlannerRoute>() == true
                is MapRoute -> currentDest?.hasRoute<MapRoute>() == true
                is FocusRoute -> currentDest?.hasRoute<FocusRoute>() == true
                is AnalyticsRoute -> currentDest?.hasRoute<AnalyticsRoute>() == true
                else -> false
              }
            NavigationRailItem(
              selected = selected,
              onClick = { navigateTopLevel(dest.route) },
              icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label) },
              label = { Text(dest.label) },
              modifier = Modifier.testTag(dest.testTag),
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
          if (showTopLevelNav) {
            val isLiveActive = voiceState.isSessionActive && voiceState.status != VoiceSessionStatus.IDLE
            FloatingActionButton(
              onClick = { showVoiceCoach = true },
              containerColor =
                if (isLiveActive) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.secondary,
              contentColor =
                if (isLiveActive) MaterialTheme.colorScheme.onTertiary
                else MaterialTheme.colorScheme.onSecondary,
              elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
              modifier = Modifier.testTag("floating_voice_coach_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Open Live Voice Study Coach",
              )
            }
          }
        },
        bottomBar = {
          if (!useNavRail && showTopLevelNav) {
            NavigationBar(windowInsets = WindowInsets.navigationBars) {
              destinations.forEach { dest ->
                val selected =
                  when (dest.route) {
                    is TodayRoute -> currentDest?.hasRoute<TodayRoute>() == true
                    is PlannerRoute -> currentDest?.hasRoute<PlannerRoute>() == true
                    is MapRoute -> currentDest?.hasRoute<MapRoute>() == true
                    is FocusRoute -> currentDest?.hasRoute<FocusRoute>() == true
                    is AnalyticsRoute -> currentDest?.hasRoute<AnalyticsRoute>() == true
                    else -> false
                  }
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateTopLevel(dest.route) },
                  icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label) },
                  label = { Text(dest.label) },
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          SharedTransitionLayout(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
            NavHost(
              navController = navController,
              startDestination = TodayRoute,
            ) {
              composable<TodayRoute> {
                TodayScreen(
                  displayName = displayName,
                  userStatsState = userStatsState,
                  subjectsState = subjectsState,
                  slotsState = slotsState,
                  tasksState = tasksState,
                  sessionsState = sessionsState,
                  selectedDayOfWeek = selectedDay,
                  sharedTransitionScope = this@SharedTransitionLayout,
                  animatedVisibilityScope = this@composable,
                  onSelectDay = plannerViewModel::selectDay,
                  onCycleTheme = plannerViewModel::cycleThemeMode,
                  onOpenSettings = { navController.navigate(SettingsRoute) },
                  onStartQuickFocus = { subjId, subjName, taskId, taskTitle ->
                    focusViewModel.linkStudyTarget(subjId, subjName, taskId, taskTitle)
                    navigateTopLevel(FocusRoute)
                  },
                  onOpenTaskDetail = { taskId -> navController.navigate(TaskDetailRoute(taskId)) },
                  onOpenSubjectDetail = { subjId -> navController.navigate(SubjectDetailRoute(subjId)) },
                  onToggleTask = plannerViewModel::toggleTaskCompleted,
                  onOpenAddTask = { showAddTask = true },
                  onOpenAddSlot = {
                    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
                    if (subjects.isEmpty()) showAddSubject = true else showAddSlot = true
                  },
                  hasNotificationPermission = hasNotificationPermission,
                  onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                  },
                )
              }

              composable<PlannerRoute> {
                PlannerScreen(
                  subjectsState = subjectsState,
                  slotsState = slotsState,
                  tasksState = tasksState,
                  selectedFilter = selectedFilter,
                  sharedTransitionScope = this@SharedTransitionLayout,
                  animatedVisibilityScope = this@composable,
                  onSelectFilter = plannerViewModel::setTaskFilter,
                  onOpenAddSubject = { showAddSubject = true },
                  onOpenAddSlot = {
                    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
                    if (subjects.isEmpty()) showAddSubject = true else showAddSlot = true
                  },
                  onOpenAddTask = { showAddTask = true },
                  onOpenSubjectDetail = { subjId -> navController.navigate(SubjectDetailRoute(subjId)) },
                  onOpenTaskDetail = { taskId -> navController.navigate(TaskDetailRoute(taskId)) },
                  onToggleTask = plannerViewModel::toggleTaskCompleted,
                  onDeleteTask = plannerViewModel::deleteTask,
                  onDeleteSlot = plannerViewModel::deleteTimetableSlot,
                  onFocusOnTask = { task ->
                    focusViewModel.linkStudyTarget(task.subjectId, task.subjectName, task.id, task.title)
                    navigateTopLevel(FocusRoute)
                  },
                )
              }

              composable<MapRoute> {
                CampusMapScreen(
                  themeMode = preferences.themeMode,
                  spotsState = spotsState,
                  subjectsState = subjectsState,
                  slotsState = slotsState,
                  onAddStudySpot = { name, category, address, notes, subjectId, lat, lng ->
                    plannerViewModel.addStudySpot(
                      name = name,
                      category = category,
                      address = address,
                      notes = notes,
                      subjectId = subjectId,
                      latitude = lat,
                      longitude = lng,
                    )
                  },
                  onDeleteStudySpot = plannerViewModel::deleteStudySpot,
                  onFocusHere = { subjId, subjName ->
                    focusViewModel.linkStudyTarget(subjId, subjName)
                    navigateTopLevel(FocusRoute)
                  },
                )
              }

              composable<FocusRoute> {
                FocusScreen(
                  timerState = timerState,
                  subjectsState = subjectsState,
                  onSelectPhase = focusViewModel::selectPhase,
                  onSelectSubject = { id, name -> focusViewModel.linkStudyTarget(id, name) },
                  onToggleStartPause = focusViewModel::toggleStartPause,
                  onReset = focusViewModel::resetTimer,
                  onSkip = focusViewModel::skipPhase,
                  onFinishEarly = focusViewModel::completeEarlyAndLog,
                  onDismissCelebration = focusViewModel::dismissCelebration,
                )
              }

              composable<AnalyticsRoute> {
                AnalyticsScreen(
                  userStatsState = userStatsState,
                  sessionsState = sessionsState,
                  tasksState = tasksState,
                  subjectsState = subjectsState,
                )
              }

              composable<SubjectDetailRoute> { entry ->
                val route = entry.toRoute<SubjectDetailRoute>()
                val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
                val slots = (slotsState as? UiState.Success)?.data.orEmpty()
                val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
                SubjectDetailScreen(
                  subject = subjects.firstOrNull { it.id == route.subjectId },
                  slots = slots,
                  tasks = tasks,
                  sharedTransitionScope = this@SharedTransitionLayout,
                  animatedVisibilityScope = this@composable,
                  onBack = { navController.popBackStack() },
                  onFocusSubject = { subj ->
                    focusViewModel.linkStudyTarget(subj.id, subj.name)
                    navigateTopLevel(FocusRoute)
                  },
                  onDeleteSubject = plannerViewModel::deleteSubject,
                )
              }

              composable<TaskDetailRoute> { entry ->
                val route = entry.toRoute<TaskDetailRoute>()
                val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
                TaskDetailScreen(
                  task = tasks.firstOrNull { it.id == route.taskId },
                  sharedTransitionScope = this@SharedTransitionLayout,
                  animatedVisibilityScope = this@composable,
                  onBack = { navController.popBackStack() },
                  onToggleComplete = plannerViewModel::toggleTaskCompleted,
                  onFocusTask = { task ->
                    focusViewModel.linkStudyTarget(task.subjectId, task.subjectName, task.id, task.title)
                    navigateTopLevel(FocusRoute)
                  },
                  onDeleteTask = plannerViewModel::deleteTask,
                )
              }

              composable<SettingsRoute> {
                val stats = (userStatsState as? UiState.Success)?.data
                SettingsScreen(
                  userEmail = userEmail,
                  preferences = preferences,
                  dailyGoalMinutes = (stats?.dailyGoalMinutes ?: 60L).toInt(),
                  onBack = { navController.popBackStack() },
                  onSelectTheme = plannerViewModel::setThemeMode,
                  onSaveDurations = plannerViewModel::updatePomodoroDurations,
                  onUpdateDailyGoal = plannerViewModel::updateDailyGoal,
                  onUpdateReminderLead = plannerViewModel::updateDefaultReminderLead,
                  onSignOut = onSignOut,
                )
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = showVoiceCoach,
      enter = fadeIn() + slideInVertically { it / 8 },
      exit = fadeOut() + slideOutVertically { it / 8 },
    ) {
      LiveVoiceCoachOverlay(
        state = voiceState,
        onSelectVoice = voiceViewModel::selectVoice,
        onStartVoice = voiceViewModel::startVoiceSession,
        onStopVoice = voiceViewModel::stopVoiceSession,
        onSendText = voiceViewModel::sendTextMessage,
        onDismissError = voiceViewModel::clearError,
        onCloseOverlay = { showVoiceCoach = false },
      )
    }
  }

  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()

  if (showAddSubject) {
    AddSubjectDialog(
      onDismiss = { showAddSubject = false },
      onConfirm = { name, code, instructor, colorHex ->
        plannerViewModel.addSubject(name, code, instructor, colorHex)
        showAddSubject = false
      },
    )
  }

  if (showAddSlot && subjects.isNotEmpty()) {
    AddTimetableSlotDialog(
      subjects = subjects,
      initialDayOfWeek = selectedDay,
      onDismiss = { showAddSlot = false },
      onConfirm = { subj, day, start, end, room ->
        plannerViewModel.addTimetableSlot(subj, day, start, end, room)
        showAddSlot = false
      },
    )
  }

  if (showAddTask) {
    AddTaskDialog(
      subjects = subjects,
      defaultLeadMinutes = preferences.defaultReminderLeadMinutes,
      onDismiss = { showAddTask = false },
      onConfirm = { subj, title, notes, type, priority, dueMillis, leadMinutes ->
        if (leadMinutes > 0L && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        plannerViewModel.addTask(subj, title, notes, type, priority, dueMillis, leadMinutes)
        showAddTask = false
      },
    )
  }
}
