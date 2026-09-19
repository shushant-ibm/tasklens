package dev.shushant.tasklens.ui

import dev.shushant.tasklens.storage.TaskLensStore
import java.io.File

/**
 * Singleton bridge that connects [dev.shushant.tasklens.android.TaskLens] (in the host app's
 * dependency graph) to this UI module without introducing a circular dependency.
 *
 * TaskLens.install() populates these providers immediately after initialisation.
 * All three must be non-null before [TaskLensActivity] is launched.
 */
object TaskLensBridge {
    /** Returns the active [TaskLensStore] instance. */
    var storeProvider: (() -> TaskLensStore)? = null

    /**
     * Exports the trace for the given task ID and returns the resulting [File].
     * Executed from a coroutine inside the ViewModel.
     */
    var exportProvider: (suspend (taskId: String) -> File)? = null

    /** Clears all stored TaskLens data. */
    var clearProvider: (() -> Unit)? = null

    /** Returns `true` when all providers are wired and the SDK is ready. */
    val isReady: Boolean
        get() = storeProvider != null && exportProvider != null && clearProvider != null
}
