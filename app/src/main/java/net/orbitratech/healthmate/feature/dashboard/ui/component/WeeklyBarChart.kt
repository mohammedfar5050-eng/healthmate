package net.orbitratech.healthmate.feature.dashboard.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.feature.records.model.DailySummary
import net.orbitratech.healthmate.feature.records.model.HealthMetric
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Seven-day bar chart drawn on a Compose [Canvas] (no chart library needed).
 * A dashed line marks the daily goal, today's bar is highlighted, and tapping
 * a bar shows its exact value.
 */
@Composable
fun WeeklyBarChart(
    days: List<DailySummary>,
    metric: HealthMetric,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    var selected by remember(metric, days) { mutableStateOf<Int?>(null) }

    // Grow bars from zero whenever the data or metric changes.
    val growth = remember { Animatable(0f) }
    LaunchedEffect(metric, days) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    val maxY = niceMax(max(days.maxOf { it.valueOf(metric) }, goal))
    val description = days.joinToString { "${DateUtils.formatWeekday(it.date)} ${Formatters.number(it.valueOf(metric))}" }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .semantics {
                contentDescription = "${metric.shortLabel} for the last 7 days, goal ${Formatters.number(goal)}: $description"
            }
            .pointerInput(days, metric) {
                detectTapGestures { offset ->
                    val left = 44.dp.toPx()
                    val slot = (size.width - left) / days.size
                    val index = ((offset.x - left) / slot).toInt()
                    selected = if (index in days.indices && index != selected) index else null
                }
            },
    ) {
        val leftAxis = 44.dp.toPx()
        val bottomAxis = 24.dp.toPx()
        val top = 8.dp.toPx()
        val chartHeight = size.height - bottomAxis - top
        val slot = (size.width - leftAxis) / days.size
        val barWidth = minOf(slot * 0.5f, 22.dp.toPx())
        fun yOf(value: Float) = top + chartHeight * (1f - value / maxY)

        // Grid lines and Y-axis labels (4 intervals).
        for (i in 0..4) {
            val value = maxY * i / 4
            val y = yOf(value)
            drawLine(
                color = colors.outlineVariant,
                start = Offset(leftAxis, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            drawLabel(
                textMeasurer, Formatters.compact(value.toInt()),
                Offset(leftAxis - 6.dp.toPx(), y), labelStyle.copy(color = colors.onSurfaceVariant),
                alignRight = true,
            )
        }

        // Bars.
        days.forEachIndexed { index, day ->
            val centerX = leftAxis + slot * index + slot / 2
            val isToday = index == days.lastIndex
            // Track behind the bar.
            drawRoundRect(
                color = metric.color.copy(alpha = 0.07f),
                topLeft = Offset(centerX - barWidth / 2, top),
                size = Size(barWidth, chartHeight),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            val value = day.valueOf(metric) * growth.value
            if (value > 0) {
                val barTop = yOf(value)
                drawTopRoundedBar(
                    left = centerX - barWidth / 2,
                    top = barTop,
                    width = barWidth,
                    bottom = top + chartHeight,
                    radius = 6.dp.toPx(),
                    color = if (isToday || selected == index) metric.color else metric.color.copy(alpha = 0.55f),
                )
            }
            drawLabel(
                textMeasurer,
                if (isToday) "Today" else DateUtils.formatWeekday(day.date),
                Offset(centerX, size.height - bottomAxis / 2),
                labelStyle.copy(
                    color = if (isToday) metric.color else colors.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                center = true,
            )
        }

        // Goal line. It is explained by the legend above the chart, so no label is
        // drawn here where it would collide with today's bar.
        if (goal > 0) {
            val y = yOf(goal.toFloat())
            drawLine(
                color = metric.color,
                start = Offset(leftAxis, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
            )
        }

        // Tooltip for the tapped bar.
        selected?.let { index ->
            val day = days[index]
            val centerX = leftAxis + slot * index + slot / 2
            val text = "${Formatters.number(day.valueOf(metric))} ${metric.unit}\n${DateUtils.formatShort(day.date)}"
            val layout = textMeasurer.measure(
                text,
                TextStyle(color = colors.inverseOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            val pad = 8.dp.toPx()
            val boxWidth = layout.size.width + pad * 2
            val boxHeight = layout.size.height + pad * 2
            val x = (centerX - boxWidth / 2).coerceIn(leftAxis, size.width - boxWidth)
            val y = (yOf(day.valueOf(metric).toFloat()) - boxHeight - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(colors.inverseSurface, Offset(x, y), Size(boxWidth, boxHeight), CornerRadius(8.dp.toPx()))
            drawText(layout, topLeft = Offset(x + pad, y + pad))
        }
    }
}

private fun DrawScope.drawTopRoundedBar(
    left: Float, top: Float, width: Float, bottom: Float, radius: Float, color: androidx.compose.ui.graphics.Color,
) {
    val r = minOf(radius, (bottom - top) / 2, width / 2)
    val path = Path().apply {
        moveTo(left, bottom)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticTo(left + width, top, left + width, top + r)
        lineTo(left + width, bottom)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    anchor: Offset,
    style: TextStyle,
    alignRight: Boolean = false,
    center: Boolean = false,
) {
    val layout = measurer.measure(text, style)
    val x = when {
        alignRight -> anchor.x - layout.size.width
        center -> anchor.x - layout.size.width / 2f
        else -> anchor.x
    }
    drawText(layout, topLeft = Offset(x, anchor.y - layout.size.height / 2f))
}

/**
 * Rounds the axis maximum up so each of the 4 grid intervals is a "nice"
 * number (e.g. 2,500 or 4,000), keeping axis labels tidy.
 */
internal fun niceMax(value: Int): Float {
    if (value <= 0) return 4f
    val rawInterval = value * 1.1 / 4
    val magnitude = 10.0.pow(floor(log10(rawInterval)))
    val steps = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
    val nice = steps.first { it >= rawInterval / magnitude - 1e-9 }
    return (ceil(nice * magnitude) * 4).toFloat()
}
