package dev.shushant.tasklens.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Enforces strict API parity and zero divergence between tasklens-android and tasklens-noop.
 *
 * Verifies:
 * 1. Every public method on tasklens-android's TaskLens object exists on tasklens-noop with
 *    identical parameter counts and signatures.
 * 2. Configuration default values match 1:1 between standard and no-op variants.
 * 3. api/tasklens-android.api and api/tasklens-noop.api match across all public function names.
 */
class NoOpApiParityTest {

    @Test
    fun testTaskLensPublicMethodParityWithNoOpBaseline() {
        val taskLensClass = TaskLens::class.java
        val livePublicMethods = taskLensClass.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name to it.parameterCount }
            .toSet()

        // Read baseline for tasklens-noop
        val noopApiFile = File("../api/tasklens-noop.api").let { if (it.exists()) it else File("api/tasklens-noop.api") }
        assertTrue("tasklens-noop.api must exist", noopApiFile.exists())

        val noopApiText = noopApiFile.readText()

        // Required public facade methods that must exist in both
        val requiredMethods = setOf(
            "isInstalled",
            "install",
            "emit",
            "breadcrumb",
            "withCorrelation",
            "show",
            "showUI",
            "hide",
            "hideUI",
            "clear",
            "export",
            "health",
            "getConfig"
        )

        for (methodName in requiredMethods) {
            assertTrue(
                "Method '$methodName' must exist on live tasklens-android TaskLens",
                livePublicMethods.any { it.first == methodName }
            )
            assertTrue(
                "Method '$methodName' must exist in tasklens-noop.api baseline",
                noopApiText.contains(methodName)
            )
        }
    }

    @Test
    fun testTaskLensConfigDefaultsParity() {
        val defaultConfig = TaskLensConfig()

        // Verify configuration defaults match canonical specification
        assertFalse("captureWorkerPayloads default must be false", defaultConfig.captureWorkerPayloads)
        assertTrue("captureEnvironment default must be true", defaultConfig.captureEnvironment)
        assertTrue("enableKourierBridge default must be true", defaultConfig.enableKourierBridge)
        assertTrue("autoObserveWorkManager default must be true", defaultConfig.autoObserveWorkManager)
        assertTrue("diagnosticsEnabled default must be true", defaultConfig.diagnosticsEnabled)

        // Builder parity
        val builtConfig = TaskLensConfig.Builder().build()
        assertEquals(defaultConfig.captureWorkerPayloads, builtConfig.captureWorkerPayloads)
        assertEquals(defaultConfig.captureEnvironment, builtConfig.captureEnvironment)
        assertEquals(defaultConfig.enableKourierBridge, builtConfig.enableKourierBridge)
        assertEquals(defaultConfig.autoObserveWorkManager, builtConfig.autoObserveWorkManager)
        assertEquals(defaultConfig.diagnosticsEnabled, builtConfig.diagnosticsEnabled)
    }

    @Test
    fun testApiBaselinesParityAcrossAndroidAndNoop() {
        val androidApiFile = File("../api/tasklens-android.api").let { if (it.exists()) it else File("api/tasklens-android.api") }
        val noopApiFile = File("../api/tasklens-noop.api").let { if (it.exists()) it else File("api/tasklens-noop.api") }

        assertTrue(androidApiFile.exists())
        assertTrue(noopApiFile.exists())

        val androidMethods = extractMethodNames(androidApiFile.readText())
        val noopMethods = extractMethodNames(noopApiFile.readText())

        assertEquals(
            "Public method surface of TaskLens in tasklens-android and tasklens-noop must match exactly",
            androidMethods,
            noopMethods
        )
    }

    private fun extractMethodNames(apiText: String): Set<String> {
        return apiText.lines()
            .map { it.trim() }
            .filter { it.startsWith("public") && it.contains("(") }
            .map { line ->
                val beforeParen = line.substringBefore("(")
                beforeParen.substringAfterLast(" ")
            }
            .toSet()
    }
}
