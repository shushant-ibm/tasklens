package dev.shushant.tasklens.export

import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskTimeline

object HtmlReportGenerator {

    fun generateHtml(
        task: ScheduledWork,
        timeline: TaskTimeline,
        diagnoses: List<Diagnosis>,
        deviceInfo: Map<String, String>,
        manifest: ExportManifest
    ): String {
        val primaryDiagnosis = diagnoses.firstOrNull()

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TaskLens Trace: ${task.name ?: task.id}</title>
    <style>
        :root {
            --bg: #0d1117;
            --surface: #161b22;
            --border: #30363d;
            --text-primary: #f0f6fc;
            --text-secondary: #8b949e;
            --accent: #58a6ff;
            --success: #3fb950;
            --danger: #f85149;
            --warning: #d29922;
            --tag-bg: #21262d;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text-primary);
            margin: 0;
            padding: 24px;
            line-height: 1.5;
        }
        .container {
            max-width: 960px;
            margin: 0 auto;
        }
        .header {
            border-bottom: 1px solid var(--border);
            padding-bottom: 16px;
            margin-bottom: 24px;
        }
        .header h1 {
            margin: 0 0 8px 0;
            font-size: 26px;
        }
        .header .subtitle {
            color: var(--text-secondary);
            font-size: 14px;
        }
        .badge {
            display: inline-block;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
            margin-right: 6px;
        }
        .badge-confirmed { background: rgba(63, 185, 80, 0.2); color: var(--success); border: 1px solid var(--success); }
        .badge-danger { background: rgba(248, 81, 73, 0.2); color: var(--danger); border: 1px solid var(--danger); }
        .badge-warning { background: rgba(210, 153, 34, 0.2); color: var(--warning); border: 1px solid var(--warning); }
        .badge-info { background: rgba(88, 166, 255, 0.2); color: var(--accent); border: 1px solid var(--accent); }
        
        .card {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 24px;
        }
        .card h2 {
            margin-top: 0;
            font-size: 18px;
            border-bottom: 1px solid var(--border);
            padding-bottom: 10px;
        }
        .timeline-item {
            display: flex;
            align-items: flex-start;
            padding: 12px 0;
            border-left: 2px solid var(--border);
            margin-left: 12px;
            padding-left: 18px;
            position: relative;
        }
        .timeline-item::before {
            content: '';
            position: absolute;
            left: -6px;
            top: 16px;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--accent);
        }
        .timeline-item.failure::before { background: var(--danger); }
        .timeline-item.warning::before { background: var(--warning); }
        .timeline-time {
            color: var(--text-secondary);
            font-size: 13px;
            min-width: 120px;
        }
        .timeline-content {
            flex: 1;
        }
        .timeline-title {
            font-weight: 600;
            margin-bottom: 4px;
        }
        .timeline-desc {
            color: var(--text-secondary);
            font-size: 13px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            margin-top: 10px;
        }
        th, td {
            text-align: left;
            padding: 8px 12px;
            border-bottom: 1px solid var(--border);
        }
        th {
            color: var(--text-secondary);
            background: var(--tag-bg);
        }
        .footer {
            text-align: center;
            color: var(--text-secondary);
            font-size: 12px;
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid var(--border);
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔍 TaskLens Execution Report</h1>
            <div class="subtitle">
                <strong>Task:</strong> ${task.name ?: task.id} &bull; 
                <strong>Scheduler:</strong> ${task.scheduler.name} &bull; 
                <strong>Platform:</strong> ${manifest.platform} &bull; 
                <strong>Exported:</strong> ${manifest.createdAt}
            </div>
        </div>

        ${primaryDiagnosis?.let { diag ->
            """
            <div class="card">
                <h2>⚡ What Happened? (Diagnosis)</h2>
                <p><strong>${diag.title}</strong></p>
                <p>${diag.summary}</p>
                <div>
                    <span class="badge badge-confirmed">Confidence: ${diag.confidence.name}</span>
                    <span class="badge badge-info">Type: ${diag.classification.name}</span>
                </div>

                ${if (diag.evidence.isNotEmpty()) {
                    """
                    <h3 style="font-size: 14px; margin-top: 20px;">Corroborating Evidence</h3>
                    <table>
                        <thead>
                            <tr>
                                <th>Type</th>
                                <th>Source</th>
                                <th>Finding</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${diag.evidence.joinToString("") { ev ->
                                """
                                <tr>
                                    <td>${ev.type.name}</td>
                                    <td>${ev.source.name}</td>
                                    <td><strong>${ev.title}</strong></td>
                                    <td>${ev.description}</td>
                                </tr>
                                """
                            }}
                        </tbody>
                    </table>
                    """
                } else ""}

                ${if (diag.limitations.isNotEmpty()) {
                    """
                    <div style="margin-top: 16px; padding: 12px; background: rgba(210, 153, 34, 0.1); border-radius: 6px; border-left: 3px solid var(--warning);">
                        <strong>Platform Limitation:</strong>
                        ${diag.limitations.joinToString("<br>") { it.message }}
                    </div>
                    """
                } else ""}
            </div>
            """
        } ?: ""}

        <div class="card">
            <h2>⏱️ Execution Timeline</h2>
            ${timeline.items.joinToString("") { item ->
                val cls = when {
                    item.isFailure -> "failure"
                    item.isWarning -> "warning"
                    else -> ""
                }
                """
                <div class="timeline-item $cls">
                    <div class="timeline-time">${item.timestamp}</div>
                    <div class="timeline-content">
                        <div class="timeline-title">${item.icon.symbol} ${item.title} ${item.durationMs?.let { "<span style='color:var(--text-secondary); font-size:12px;'>(${it}ms)</span>" } ?: ""}</div>
                        <div class="timeline-desc">${item.description}</div>
                    </div>
                </div>
                """
            }}
        </div>

        <div class="card">
            <h2>📱 Device & Environment Metadata</h2>
            <table>
                <tbody>
                    ${deviceInfo.entries.joinToString("") { (k, v) ->
                        """
                        <tr>
                            <td style="width: 200px; color: var(--text-secondary);">$k</td>
                            <td>$v</td>
                        </tr>
                        """
                    }}
                </tbody>
            </table>
        </div>

        <div class="footer">
            Generated by TaskLens SDK &bull; "Your background task didn't run. TaskLens tells you why."
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
