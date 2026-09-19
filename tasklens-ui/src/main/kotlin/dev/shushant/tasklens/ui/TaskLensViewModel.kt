package dev.shushant.tasklens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shushant.tasklens.core.Diagnosis
import dev.shushant.tasklens.core.ExecutionAttempt
import dev.shushant.tasklens.core.ScheduledWork
import dev.shushant.tasklens.core.TaskLensEvent
import dev.shushant.tasklens.storage.TaskQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

sealed interface TaskLensUiState {
    data object Loading : TaskLensUiState

    data class TaskList(
        val tasks: List<ScheduledWork>,
        val searchQuery: String = ""
    ) : TaskLensUiState

    data class TaskDetail(
        val task: ScheduledWork,
        val attempts: List<ExecutionAttempt>,
        val events: List<TaskLensEvent>,
        val diagnoses: List<Diagnosis>
    ) : TaskLensUiState

    data class Error(val message: String) : TaskLensUiState
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel that powers the TaskLens inspector UI.
 *
 * Data is sourced through [TaskLensBridge] providers to keep this module
 * independent of `tasklens-android`.
 */
class TaskLensViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<TaskLensUiState>(TaskLensUiState.Loading)
    val uiState: StateFlow<TaskLensUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    // ------------------------------------------------------------------
    // Load / Reload
    // ------------------------------------------------------------------

    fun reload() {
        viewModelScope.launch {
            _uiState.value = TaskLensUiState.Loading
            runCatching {
                val store = TaskLensBridge.storeProvider?.invoke()
                    ?: error("TaskLens storeProvider not initialised")
                store.tasks(TaskQuery(limit = 200))
            }.onSuccess { tasks ->
                _uiState.value = TaskLensUiState.TaskList(tasks)
            }.onFailure { t ->
                _uiState.value = TaskLensUiState.Error(
                    t.message ?: "Failed to load tasks"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Task selection
    // ------------------------------------------------------------------

    fun selectTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = TaskLensUiState.Loading
            runCatching {
                val store = TaskLensBridge.storeProvider?.invoke()
                    ?: error("TaskLens storeProvider not initialised")
                val task = store.task(taskId) ?: error("Task $taskId not found")
                val attempts = store.attempts(taskId)
                val events = store.events(taskId)
                val diagnoses = store.diagnoses(taskId)
                TaskLensUiState.TaskDetail(
                    task = task,
                    attempts = attempts,
                    events = events,
                    diagnoses = diagnoses
                )
            }.onSuccess { detail ->
                _uiState.value = detail
            }.onFailure { t ->
                _uiState.value = TaskLensUiState.Error(
                    t.message ?: "Failed to load task detail"
                )
            }
        }
    }

    fun backToDashboard() {
        reload()
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile.asStateFlow()

    fun exportTask(taskId: String) {
        viewModelScope.launch {
            runCatching {
                TaskLensBridge.exportProvider?.invoke(taskId)
                    ?: error("TaskLens exportProvider not initialised")
            }.onSuccess { file ->
                _exportedFile.value = file
            }.onFailure { /* silently drop; host can observe exportedFile */ }
        }
    }

    // ------------------------------------------------------------------
    // Clear
    // ------------------------------------------------------------------

    fun clearAll() {
        viewModelScope.launch {
            runCatching {
                TaskLensBridge.clearProvider?.invoke()
            }.onSuccess {
                reload()
            }
        }
    }

    // ------------------------------------------------------------------
    // Search filter (applied to the task list in-memory)
    // ------------------------------------------------------------------

    fun filterTasks(query: String) {
        val current = _uiState.value
        if (current is TaskLensUiState.TaskList) {
            _uiState.value = current.copy(searchQuery = query)
        }
    }
}
