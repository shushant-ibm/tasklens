package dev.shushant.tasklens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.DiagnosisClassification
import dev.shushant.tasklens.core.DiagnosisConfidence

/**
 * Card that renders a single [Diagnosis] entry.
 * Includes a colour-coded confidence dot, classification badge, summary, and rule reference.
 */
@Composable
fun DiagnosisCard(
    diagnosis: Diagnosis,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Confidence indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = diagnosis.confidence.color(),
                        shape = CircleShape
                    )
                    .padding(top = 4.dp)
            )
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                // Title + confidence label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = diagnosis.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    ConfidenceBadge(diagnosis.confidence)
                }
                Spacer(Modifier.height(4.dp))
                // Classification tag
                ClassificationBadge(diagnosis.classification)
                Spacer(Modifier.height(6.dp))
                // Summary
                Text(
                    text = diagnosis.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                // Rule ID reference
                if (diagnosis.ruleId.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Rule: ${diagnosis.ruleId} v${diagnosis.ruleVersion}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
                // Possible factors
                if (diagnosis.possibleFactors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Factors:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    diagnosis.possibleFactors.forEach { factor ->
                        Text(
                            text = "• ${factor.title}: ${factor.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Badge helpers
// ---------------------------------------------------------------------------

@Composable
private fun ConfidenceBadge(confidence: DiagnosisConfidence) {
    val (label, bg) = when (confidence) {
        DiagnosisConfidence.CONFIRMED -> "Confirmed" to Color(0xFF2ECC71)
        DiagnosisConfidence.LIKELY -> "Likely" to Color(0xFFF39C12)
        DiagnosisConfidence.POSSIBLE -> "Possible" to Color(0xFF3498DB)
        DiagnosisConfidence.UNKNOWN -> "Unknown" to Color(0xFF95A5A6)
    }
    Badge(label = label, background = bg)
}

@Composable
private fun ClassificationBadge(classification: DiagnosisClassification) {
    val label = classification.name
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
    Badge(
        label = label,
        background = MaterialTheme.colorScheme.secondaryContainer,
        textColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun Badge(
    label: String,
    background: Color,
    textColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .background(
                color = background.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        )
    }
}

// ---------------------------------------------------------------------------
// Extension helpers
// ---------------------------------------------------------------------------

private fun DiagnosisConfidence.color(): Color = when (this) {
    DiagnosisConfidence.CONFIRMED -> Color(0xFF2ECC71)
    DiagnosisConfidence.LIKELY -> Color(0xFFF39C12)
    DiagnosisConfidence.POSSIBLE -> Color(0xFF3498DB)
    DiagnosisConfidence.UNKNOWN -> Color(0xFF95A5A6)
}
