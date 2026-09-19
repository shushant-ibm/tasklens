package dev.shushant.tasklens.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.TaskTimeline
import dev.shushant.tasklens.core.TimelineItem
import dev.shushant.tasklens.ui.theme.AccentBlue
import dev.shushant.tasklens.ui.theme.DangerRed
import dev.shushant.tasklens.ui.theme.DarkBorder
import dev.shushant.tasklens.ui.theme.DarkSurface
import dev.shushant.tasklens.ui.theme.SuccessGreen
import dev.shushant.tasklens.ui.theme.TextPrimary
import dev.shushant.tasklens.ui.theme.TextSecondary
import dev.shushant.tasklens.ui.theme.WarningYellow

@Composable
fun TimelineComponent(
    timeline: TaskTimeline,
    modifier: Modifier = Modifier
) {
    if (timeline.items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No timeline events recorded for this task.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    Column(modifier = modifier.padding(16.dp)) {
        timeline.items.forEachIndexed { index, item ->
            TimelineNode(
                item = item,
                isLast = index == timeline.items.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineNode(
    item: TimelineItem,
    isLast: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    val dotColor = when {
        item.isFailure -> DangerRed
        item.isWarning -> WarningYellow
        item.title.contains("Completed") || item.title.contains("Succeeded") -> SuccessGreen
        else -> AccentBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        // Connector column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(54.dp)
                        .background(DarkBorder)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.icon.symbol} ${item.title}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (item.durationMs != null) {
                    Text(
                        text = "+${item.durationMs}ms",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.description,
                color = TextSecondary,
                fontSize = 13.sp
            )

            AnimatedVisibility(visible = expanded && item.attributes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .padding(8.dp)
                ) {
                    item.attributes.forEach { (key, value) ->
                        Row(modifier = Modifier.padding(vertical = 1.dp)) {
                            Text(
                                text = "$key: ",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = value,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
