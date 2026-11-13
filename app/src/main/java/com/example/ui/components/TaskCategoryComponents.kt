package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskType

fun TaskType.categoryIcon(): ImageVector =
  when (this) {
    TaskType.EXAM -> Icons.Rounded.Quiz
    TaskType.ASSIGNMENT -> Icons.AutoMirrored.Rounded.Assignment
    TaskType.READING -> Icons.AutoMirrored.Rounded.MenuBook
    TaskType.PROJECT -> Icons.Rounded.FolderSpecial
    TaskType.LAB -> Icons.Rounded.Science
  }

@Composable
fun TaskType.adaptiveCategoryColor(): Color {
  val base = Color(accentColorHex)
  val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  return if (isDarkSurface) lerp(base, Color.White, 0.28f) else base
}

@Composable
fun TaskCategoryBadge(
  typeRaw: String,
  modifier: Modifier = Modifier,
) {
  val taskType = TaskType.fromRaw(typeRaw)
  val tint = taskType.adaptiveCategoryColor()
  Surface(
    color = tint.copy(alpha = 0.14f),
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(0.75.dp, tint.copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = taskType.categoryIcon(),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = taskType.badge,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
      )
    }
  }
}
