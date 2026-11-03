package net.orbitratech.healthmate.feature.dashboard.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.component.AppIcon
import net.orbitratech.healthmate.core.designsystem.theme.SuccessGreen
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.feature.records.model.HealthMetric

/**
 * Today's value for one metric with an animated progress ring towards the
 * user's daily goal. Tapping the summary calls [onClick]; an optional
 * [footer] (e.g. quick-add buttons) is shown below it.
 */
@Composable
fun MetricProgressCard(
    metric: HealthMetric,
    value: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
) {
    val progress = if (goal <= 0) 0f else value.toFloat() / goal
    val reached = progress >= 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today_${metric.name.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Show ${metric.shortLabel.lowercase()} trend", onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(metric, progress)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    metric.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildAnnotatedString {
                        withStyle(
                            MaterialTheme.typography.headlineSmall.toSpanStyle().copy(color = metric.color),
                        ) { append(Formatters.number(value)) }
                        withStyle(
                            SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        ) { append("  ${metric.unit}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    "Goal ${Formatters.number(goal)} ${metric.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (reached) {
                GoalReachedBadge()
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formatters.number(goal - value), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "to go",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (footer != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) { footer() }
        }
    }
}

/** Status badge (not a button) shown once the daily goal is met. */
@Composable
private fun GoalReachedBadge() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SuccessGreen.copy(alpha = 0.12f))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_check_circle, "Goal reached:", Modifier.size(18.dp), tint = SuccessGreen)
        Spacer(Modifier.width(4.dp))
        Text("Done", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ProgressRing(metric: HealthMetric, progress: Float) {
    // Animate from 0 on first display, then between values on updates.
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) { target = progress.coerceIn(0f, 1f) }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize(),
            color = metric.color,
            strokeWidth = 7.dp,
            trackColor = metric.color.copy(alpha = 0.14f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        AppIcon(metric.icon, null, tint = metric.color, modifier = Modifier.size(26.dp))
    }
}
