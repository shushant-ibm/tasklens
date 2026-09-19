package dev.shushant.tasklens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.shushant.tasklens.ui.theme.TaskLensUiTheme

/**
 * Entry-point Activity for the TaskLens on-device inspector UI.
 *
 * Launched by [dev.shushant.tasklens.android.TaskLens.show] via explicit class name:
 *   `dev.shushant.tasklens.ui.TaskLensActivity`
 */
class TaskLensActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskLensUiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskLensNavGraph()
                }
            }
        }
    }
}
