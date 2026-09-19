package dev.shushant.tasklens.sample

import android.app.Application
import dev.shushant.tasklens.android.TaskLens

/**
 * Minimal Host Application Setup (following Kourier's pattern).
 *
 * The host application only adds `tasklens-android` (or `tasklens-noop` in release)
 * and calls `TaskLens.install(this)`. WorkManager observation, environment monitors,
 * persistence, and UI are completely encapsulated.
 */
class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1-line idiomatic initialization with builder DSL:
        TaskLens.install(this) {
            retention(days = 7, maxTasks = 500)
            captureEnvironment = true
            autoObserveWorkManager = true
        }
    }
}
