package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object PlannerRoute

@Serializable
data object MapRoute

@Serializable
data object FocusRoute

@Serializable
data object AnalyticsRoute

@Serializable
data object WeeklySummaryRoute

@Serializable
data object SettingsRoute

@Serializable
data class SubjectDetailRoute(val subjectId: String)

@Serializable
data class TaskDetailRoute(val taskId: String)
