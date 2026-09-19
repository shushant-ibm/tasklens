pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tasklens"

include(":tasklens-core")
include(":tasklens-storage")
include(":tasklens-diagnosis")
include(":tasklens-export")
include(":tasklens-android")
include(":tasklens-workmanager")
include(":tasklens-jobscheduler")
include(":tasklens-foreground")
include(":tasklens-alarm")
include(":tasklens-ui")
include(":tasklens-kourier")
include(":tasklens-noop")
include(":tasklens-core-kmp")

val skipSamples = providers.gradleProperty("skipSamples").isPresent ||
    extra.properties.containsKey("skipSamples")
if (!skipSamples) {
    include(":sample-android")
}
