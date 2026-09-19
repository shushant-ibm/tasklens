# TaskLens Android SDK — consumer ProGuard rules
# These rules are applied to any app that depends on tasklens-android.

# Keep the public TaskLens API surface
-keep public class dev.shushant.tasklens.android.TaskLens { *; }
-keep public class dev.shushant.tasklens.android.TaskLensConfig { *; }
-keep public class dev.shushant.tasklens.android.TaskLensConfig$Builder { *; }
-keep public class dev.shushant.tasklens.android.RetentionPolicy { *; }
-keep public class dev.shushant.tasklens.android.TaskLensRedactor { *; }
-keep public class dev.shushant.tasklens.android.DefaultTaskLensRedactor { *; }
-keep public class dev.shushant.tasklens.android.NoOpTaskLensLogger { *; }
-keep public class dev.shushant.tasklens.android.TaskLensLogger { *; }

# Keep core model classes so serialized JSON round-trips remain intact
-keep @kotlinx.serialization.Serializable class dev.shushant.tasklens.core.** { *; }
-keep @kotlinx.serialization.Serializable class dev.shushant.tasklens.android.** { *; }

# Keep kotlinx-serialization generated serializers
-keep class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Coroutines internals required at runtime
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
