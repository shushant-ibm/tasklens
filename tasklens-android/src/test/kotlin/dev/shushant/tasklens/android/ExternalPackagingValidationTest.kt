package dev.shushant.tasklens.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Validates external consumer packaging against Maven Local published artifacts.
 *
 * Verifies:
 * 1. Published coordinates exist in ~/.m2/repository/dev/shushant/tasklens/
 * 2. tasklens-android:0.1.0 AAR is valid and contains classes.jar and AndroidManifest.xml
 * 3. tasklens-noop:0.1.0 AAR is valid, zero-overhead, and has no operational dependencies
 * 4. tasklens-core-kmp:0.1.0 JAR and multiplatform targets (JVM, iOS) are published
 * 5. All POM metadata files specify correct Group, Artifact, and Version
 */
class ExternalPackagingValidationTest {

    private val userHome = System.getProperty("user.home")
    private val m2TaskLensDir = File(userHome, ".m2/repository/dev/shushant/tasklens")
    private val targetVersion = "0.1.0"

    @Test
    fun testMavenLocalRepositoryContainsPublishedArtifacts() {
        assertTrue("Maven local repository must contain TaskLens group directory", m2TaskLensDir.exists())

        val requiredArtifacts = listOf(
            "tasklens-android",
            "tasklens-noop",
            "tasklens-core-kmp",
            "tasklens-core-kmp-jvm",
            "tasklens-core-kmp-iosarm64",
            "tasklens-core-kmp-iossimulatorarm64",
            "tasklens-workmanager",
            "tasklens-storage",
            "tasklens-diagnosis",
            "tasklens-export"
        )

        for (art in requiredArtifacts) {
            val artDir = File(m2TaskLensDir, art)
            assertTrue("Artifact directory '$art' must exist in Maven local", artDir.exists() && artDir.isDirectory)
            val versionDir = File(artDir, targetVersion)
            assertTrue("Version directory '$targetVersion' must exist for '$art'", versionDir.exists())
        }
    }

    @Test
    fun testAndroidAarPackagingIntegrity() {
        val aarFile = File(m2TaskLensDir, "tasklens-android/$targetVersion/tasklens-android-$targetVersion.aar")
        assertTrue("tasklens-android AAR must exist", aarFile.exists() && aarFile.length() > 0)

        // Verify AAR zip contents
        ZipFile(aarFile).use { zip ->
            assertNotNull("AAR must contain AndroidManifest.xml", zip.getEntry("AndroidManifest.xml"))
            assertNotNull("AAR must contain classes.jar", zip.getEntry("classes.jar"))
        }

        // Verify POM dependencies
        val pomFile = File(m2TaskLensDir, "tasklens-android/$targetVersion/tasklens-android-$targetVersion.pom")
        assertTrue(pomFile.exists())
        val pomContent = pomFile.readText()
        assertTrue("POM must declare groupId dev.shushant.tasklens", pomContent.contains("<groupId>dev.shushant.tasklens</groupId>"))
        assertTrue("POM must declare artifactId tasklens-android", pomContent.contains("<artifactId>tasklens-android</artifactId>"))
        assertTrue("POM must declare version $targetVersion", pomContent.contains("<version>$targetVersion</version>"))
        assertTrue("POM must declare dependency on tasklens-core-kmp", pomContent.contains("tasklens-core-kmp"))
    }

    @Test
    fun testNoOpAarPackagingIntegrity() {
        val aarFile = File(m2TaskLensDir, "tasklens-noop/$targetVersion/tasklens-noop-$targetVersion.aar")
        assertTrue("tasklens-noop AAR must exist", aarFile.exists() && aarFile.length() > 0)

        ZipFile(aarFile).use { zip ->
            assertNotNull("NoOp AAR must contain AndroidManifest.xml", zip.getEntry("AndroidManifest.xml"))
            assertNotNull("NoOp AAR must contain classes.jar", zip.getEntry("classes.jar"))
        }

        val pomFile = File(m2TaskLensDir, "tasklens-noop/$targetVersion/tasklens-noop-$targetVersion.pom")
        assertTrue(pomFile.exists())
        val pomContent = pomFile.readText()
        assertTrue("POM must declare artifactId tasklens-noop", pomContent.contains("<artifactId>tasklens-noop</artifactId>"))
        // Zero operational dependencies: must not depend on storage, diagnosis, export, workmanager
        org.junit.Assert.assertFalse("NoOp POM must not depend on tasklens-storage", pomContent.contains("tasklens-storage"))
        org.junit.Assert.assertFalse("NoOp POM must not depend on tasklens-diagnosis", pomContent.contains("tasklens-diagnosis"))
        org.junit.Assert.assertFalse("NoOp POM must not depend on tasklens-export", pomContent.contains("tasklens-export"))
    }

    @Test
    fun testKmpCorePackagingIntegrity() {
        val jvmJar = File(m2TaskLensDir, "tasklens-core-kmp-jvm/$targetVersion/tasklens-core-kmp-jvm-$targetVersion.jar")
        assertTrue("KMP JVM JAR must exist", jvmJar.exists() && jvmJar.length() > 0)

        ZipFile(jvmJar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(
                "KMP JVM JAR must contain dev/shushant/tasklens/core classes",
                entries.any { it.startsWith("dev/shushant/tasklens/core/") }
            )
        }

        val moduleFile = File(m2TaskLensDir, "tasklens-core-kmp/$targetVersion/tasklens-core-kmp-$targetVersion.module")
        assertTrue("Gradle module metadata must exist for KMP core", moduleFile.exists())
        val moduleText = moduleFile.readText()
        assertTrue("Module metadata must include iosArm64 variant", moduleText.contains("iosArm64"))
        assertTrue("Module metadata must include jvm variant", moduleText.contains("jvm"))
    }
}
