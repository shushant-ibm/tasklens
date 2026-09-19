package dev.shushant.tasklens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.ui.theme.AccentBlue
import dev.shushant.tasklens.ui.theme.DarkBorder
import dev.shushant.tasklens.ui.theme.DarkSurface
import dev.shushant.tasklens.ui.theme.SuccessGreen
import dev.shushant.tasklens.ui.theme.TextPrimary
import dev.shushant.tasklens.ui.theme.TextSecondary
import dev.shushant.tasklens.ui.theme.WarningYellow

@Composable
fun EnvironmentComponent(
    environmentEvents: List<TaskLensEvent>,
    modifier: Modifier = Modifier
) {
    if (environmentEvents.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No environment signals recorded yet.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    val latestNetwork = environmentEvents.lastOrNull { it.type == EventType.NETWORK_CHANGED }
    val latestBattery = environmentEvents.lastOrNull { it.type == EventType.BATTERY_CHANGED }
    val latestPower = environmentEvents.lastOrNull { it.type == EventType.POWER_MODE_CHANGED }
    val latestAppState = environmentEvents.lastOrNull { it.type == EventType.APP_STATE_CHANGED }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "LATEST SYSTEM ENVIRONMENT",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Grid cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnvCard(
                title = "Network",
                value = if (latestNetwork?.attributes?.get("connected") == "true") "Connected" else "Offline",
                subtitle = "Transport: ${latestNetwork?.attributes?.get("transport") ?: "None"}",
                isGood = latestNetwork?.attributes?.get("connected") == "true",
                modifier = Modifier.weight(1f)
            )
            EnvCard(
                title = "Battery",
                value = "${latestBattery?.attributes?.get("level") ?: "?"}%",
                subtitle = if (latestBattery?.attributes?.get("charging") == "true") "⚡ Charging" else "Discharging",
                isGood = latestBattery?.attributes?.get("charging") == "true",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnvCard(
                title = "Power Mode",
                value = if (latestPower?.attributes?.get("power_save") == "true") "Saver Active" else "Standard",
                subtitle = "Optimized: ${latestPower?.attributes?.get("ignoring_optimizations") ?: "Standard"}",
                isGood = latestPower?.attributes?.get("power_save") != "true",
                modifier = Modifier.weight(1f)
            )
            EnvCard(
                title = "App Lifecycle",
                value = latestAppState?.attributes?.get("state") ?: "Active",
                subtitle = "Process State",
                isGood = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "ENVIRONMENT SIGNAL LOG",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        environmentEvents.reversed().forEach { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.type.name,
                    color = AccentBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(170.dp)
                )
                Text(
                    text = event.attributes.entries.joinToString(", ") { "${it.key}=${it.value}" },
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun EnvCard(
    title: String,
    value: String,
    subtitle: String,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = if (isGood) SuccessGreen else WarningYellow,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}
