package dev.shushant.tasklens.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shushant.tasklens.ui.screens.TaskDetailScreen
import dev.shushant.tasklens.ui.screens.TaskLensDashboard

/**
 * Root navigation composable for the TaskLens inspector.
 *
 * Handles the two-screen stack (Dashboard → Detail) without requiring a navigation library
 * dependency in this module.
 */
@Composable
fun TaskLensNavGraph(
    vm: TaskLensViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsState()

    when (val state = uiState) {
        is TaskLensUiState.Loading -> {
            // Dashboard will handle loading shimmer itself
            TaskLensDashboard(
                state = state,
                onTaskClick = { /* no-op while loading */ },
                onClearAll = vm::clearAll,
                onRefresh = vm::reload
            )
        }

        is TaskLensUiState.TaskList -> {
            TaskLensDashboard(
                state = state,
                onTaskClick = { taskId -> vm.selectTask(taskId) },
                onClearAll = vm::clearAll,
                onRefresh = vm::reload
            )
        }

        is TaskLensUiState.TaskDetail -> {
            TaskDetailScreen(
                state = state,
                onBack = vm::backToDashboard,
                onExport = { vm.exportTask(state.task.id) }
            )
        }

        is TaskLensUiState.Error -> {
            TaskLensDashboard(
                state = state,
                onTaskClick = { /* no-op */ },
                onClearAll = vm::clearAll,
                onRefresh = vm::reload
            )
        }
    }
}
