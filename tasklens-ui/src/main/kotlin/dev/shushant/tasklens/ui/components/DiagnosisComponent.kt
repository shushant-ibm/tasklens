package dev.shushant.tasklens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.DiagnosisConfidence
import dev.shushant.tasklens.ui.theme.AccentBlue
import dev.shushant.tasklens.ui.theme.DangerRed
import dev.shushant.tasklens.ui.theme.DarkBorder
import dev.shushant.tasklens.ui.theme.DarkSurface
import dev.shushant.tasklens.ui.theme.SuccessGreen
import dev.shushant.tasklens.ui.theme.TextPrimary
import dev.shushant.tasklens.ui.theme.TextSecondary
import dev.shushant.tasklens.ui.theme.WarningYellow

@Composable
fun DiagnosisComponent(
    diagnoses: List<Diagnosis>,
    modifier: Modifier = Modifier
) {
    if (diagnoses.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No diagnostic findings recorded.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    Column(modifier = modifier.padding(16.dp)) {
        diagnoses.forEach { diagnosis ->
            DiagnosisCard(diagnosis = diagnosis)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosisCard(
    diagnosis: Diagnosis
) {
    val confidenceColor = when (diagnosis.confidence) {
        DiagnosisConfidence.CONFIRMED -> SuccessGreen
        DiagnosisConfidence.LIKELY -> AccentBlue
        DiagnosisConfidence.POSSIBLE -> WarningYellow
        DiagnosisConfidence.UNKNOWN -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ What Happened?",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Confidence Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(confidenceColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = diagnosis.confidence.name,
                    color = confidenceColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = diagnosis.title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = diagnosis.summary,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        // Evidence section
        if (diagnosis.evidence.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CORROBORATING EVIDENCE",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            diagnosis.evidence.forEach { evidence ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Column {
                        Text(
                            text = evidence.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = evidence.description,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Possible Contributing Factors
        if (diagnosis.possibleFactors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "POSSIBLE CONTRIBUTING FACTORS",
                color = WarningYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            diagnosis.possibleFactors.forEach { factor ->
                Text(
                    text = "• ${factor.title}: ${factor.description}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Platform Limitations callout
        if (diagnosis.limitations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(WarningYellow.copy(alpha = 0.1f))
                    .border(1.dp, WarningYellow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "⚠️ Platform Limitation Note",
                        color = WarningYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    diagnosis.limitations.forEach { limitation ->
                        Text(
                            text = limitation.message,
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
