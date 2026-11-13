package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ViewPortHandler

@Composable
fun WeeklyFocusBarChart(
  dailyMinutes: List<Pair<String, Int>>,
  modifier: Modifier = Modifier,
) {
  val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
  val secondaryArgb = MaterialTheme.colorScheme.secondary.toArgb()
  val textArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
  val gridArgb = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f).toArgb()

  AndroidView(
    modifier = modifier.fillMaxWidth().height(220.dp),
    factory = { context ->
      BarChart(context).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        description.isEnabled = false
        legend.isEnabled = false
        isDrawGridBackgroundEnabled = false
        isDrawBarShadowEnabled = false
        isScaleEnabled = false
        isPinchZoomEnabled = false
        isFitBarsEnabled = true
        axisRight.isEnabled = false
        extraBottomOffset = 8f

        xAxis.apply {
          position = XAxis.XAxisPosition.BOTTOM
          isDrawGridLinesEnabled = false
          granularity = 1f
          textSize = 12f
        }
        axisLeft.apply {
          axisMinimum = 0f
          isDrawAxisLineEnabled = false
          granularity = 15f
          textSize = 11f
        }
      }
    },
    update = { chart ->
      val labels = dailyMinutes.map { it.first }
      val entries =
        dailyMinutes.mapIndexed { index, pair -> BarEntry(index.toFloat(), pair.second.toFloat()) }
      val barColors =
        dailyMinutes.mapIndexed { index, _ ->
          if (index == dailyMinutes.lastIndex) secondaryArgb else primaryArgb
        }

      val dataSet =
        BarDataSet(entries, "Focus Minutes").apply {
          colors = barColors
          valueTextColor = textArgb
          valueTextSize = 11f
          valueFormatter =
            object : IValueFormatter {
              override fun getFormattedValue(
                value: Float,
                entry: Entry<*>,
                dataSetIndex: Int,
                viewPortHandler: ViewPortHandler,
              ): String = if (value <= 0f) "" else "${value.toInt()}m"

              override fun getStackedFormattedValue(
                value: Float,
                stackIndex: Int,
                entry: Entry<*>,
                dataSetIndex: Int,
                viewPortHandler: ViewPortHandler,
              ): String = if (value <= 0f) "" else "${value.toInt()}m"
            }
        }

      chart.xAxis.apply {
        valueFormatter = IndexAxisValueFormatter(labels)
        labelCount = labels.size
        textColor = textArgb
      }
      chart.axisLeft.apply {
        textColor = textArgb
        gridColor = gridArgb
      }
      chart.data =
        BarData(dataSet).apply {
          barWidth = 0.52f
        }
      chart.animateY(650)
      chart.invalidate()
    },
  )
}

@Composable
fun SubjectBreakdownPieChart(
  subjectMinutes: List<Triple<String, Int, Long>>,
  modifier: Modifier = Modifier,
) {
  val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
  val textArgb = MaterialTheme.colorScheme.onSurface.toArgb()
  val subTextArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

  AndroidView(
    modifier = modifier.fillMaxWidth().height(230.dp),
    factory = { context ->
      PieChart(context).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        description.isEnabled = false
        isDrawHoleEnabled = true
        holeRadius = 58f
        transparentCircleRadius = 64f
        isDrawEntryLabelsEnabled = false
        legend.apply {
          isEnabled = true
          isWordWrapEnabled = true
          textSize = 12f
        }
        centerTextSize = 14f
      }
    },
    update = { chart ->
      val totalMins = subjectMinutes.sumOf { it.second }
      val entries = subjectMinutes.map { PieEntry(it.second.toFloat(), it.first) }
      val colors = subjectMinutes.map { it.third.toInt() }

      val dataSet =
        PieDataSet(entries, "").apply {
          this.colors = colors
          sliceSpace = 3f
          valueTextColor = AndroidColor.WHITE
          valueTextSize = 11f
          valueFormatter =
            object : IValueFormatter {
              override fun getFormattedValue(
                value: Float,
                entry: Entry<*>,
                dataSetIndex: Int,
                viewPortHandler: ViewPortHandler,
              ): String = "${value.toInt()}m"

              override fun getStackedFormattedValue(
                value: Float,
                stackIndex: Int,
                entry: Entry<*>,
                dataSetIndex: Int,
                viewPortHandler: ViewPortHandler,
              ): String = "${value.toInt()}m"
            }
        }

      chart.holeColor = surfaceArgb
      chart.centerTextColor = textArgb
      chart.legend.textColor = subTextArgb
      val hrs = totalMins / 60
      val rem = totalMins % 60
      chart.centerText = if (hrs > 0) "${hrs}h ${rem}m\nFocused" else "${rem}m\nFocused"
      chart.data = PieData(dataSet)
      chart.animateY(650)
      chart.invalidate()
    },
  )
}
