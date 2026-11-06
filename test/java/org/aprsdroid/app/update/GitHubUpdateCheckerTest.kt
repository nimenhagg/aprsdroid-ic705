package org.aprsdroid.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun parsesReleaseTagAndBuildVersionName() {
        assertEquals(AppVersion(1, 9, 3), parseAppVersion("v1.9.3-ic705"))
        assertEquals(AppVersion(1, 9, 4), parseAppVersion("Mod-v1.9.4"))
        assertEquals(
            AppVersion(1, 9, 2),
            parseAppVersion("1.9.2-ic705 (based on APRSdroid v1.7.0)"),
        )
    }

    @Test
    fun parsesCancryTagTimestamps() {
        assertEquals(202611070001L, parseCancryTimestamp("Cancry-202611070001"))
        assertEquals(202611070001L, parseCancryTimestamp("Canary-202611070001"))
        assertNull(parseCancryTimestamp("Mod-v2.8.0"))
        assertNull(parseCancryTimestamp("random-tag"))

        val version = parseAppVersion("Cancry-202611070001")
        assertNotNull(version)
        assertEquals(202611070001L, version?.canaryTimestamp)
    }

    @Test
    fun comparesSemanticAndCanaryVersions() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(AppVersion(1, 9, 2), AppVersion(1, 9, 2))

        // Canary comparisons by timestamp
        assertTrue(AppVersion(0, 0, 0, 202611070002L) > AppVersion(0, 0, 0, 202611070001L))
        assertEquals(AppVersion(0, 0, 0, 202611070001L), AppVersion(0, 0, 0, 202611070001L))
    }

    @Test
    fun formatsSupportedArchitectures() {
        assertEquals("ARM64 (arm64-v8a)", formatDeviceArchitecture(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("ARMv7 (armeabi-v7a)", formatDeviceArchitecture(arrayOf("armeabi-v7a")))
        assertEquals("x86_64 (x86_64)", formatDeviceArchitecture(arrayOf("x86_64")))
        assertEquals("Unknown (riscv64)", formatDeviceArchitecture(arrayOf("riscv64")))
        assertEquals("Unknown (unknown)", formatDeviceArchitecture(emptyArray()))
    }

    @Test
    fun rejectsStringsWithoutSemanticVersion() {
        assertNull(parseAppVersion("unknown"))
    }

    @Test
    fun findsLatestCancryReleaseFromReleasesList() {
        val json = """
            [
                {
                    "tag_name": "Mod-v2.8.0",
                    "name": "Mod-v2.8.0 Stable",
                    "html_url": "https://github.com/nimenhagg/aprsdroid-ic705/releases/tag/Mod-v2.8.0"
                },
                {
                    "tag_name": "Cancry-202611070005",
                    "name": "Cancry-202611070005",
                    "html_url": "https://github.com/nimenhagg/aprsdroid-ic705/releases/tag/Cancry-202611070005"
                },
                {
                    "tag_name": "Cancry-202611062300",
                    "name": "Cancry-202611062300",
                    "html_url": "https://github.com/nimenhagg/aprsdroid-ic705/releases/tag/Cancry-202611062300"
                }
            ]
        """.trimIndent()

        val found = findLatestCancryRelease(json)
        assertNotNull(found)
        assertEquals("Cancry-202611070005", found?.optString("tag_name"))
    }

    @Test
    fun detectsPromptSuppressionMarkersInReleaseNotesAndTitle() {
        // Explicit tags in notes
        assertTrue(isUpdatePromptSuppressed("Release v2.8.0\n[skip-update-prompt]\nBug fixes"))
        assertTrue(isUpdatePromptSuppressed("Test release\n[no-prompt]"))
        assertTrue(isUpdatePromptSuppressed("Minor patch [silent-update]"))
        assertTrue(isUpdatePromptSuppressed("Hotfix [SILENT]"))

        // HTML comment tags in notes
        assertTrue(isUpdatePromptSuppressed("Release notes\n<!-- aprsdroid:skip-prompt -->\nChangelog"))
        assertTrue(isUpdatePromptSuppressed("Release notes <!-- skip-update -->"))

        // Chinese marker tags in notes
        assertTrue(isUpdatePromptSuppressed("修复一些已知问题 [不提示更新]"))
        assertTrue(isUpdatePromptSuppressed("内测版 [静默更新]"))
        assertTrue(isUpdatePromptSuppressed("构建版本 [忽略更新提示]"))

        // Title marker
        assertTrue(isUpdatePromptSuppressed("Regular notes", "Mod-v2.8.0 [skip-update-prompt]"))
        assertTrue(isUpdatePromptSuppressed("Regular notes", "Mod-v2.8.0 [静默更新]"))

        // Normal releases should NOT be suppressed
        org.junit.Assert.assertFalse(isUpdatePromptSuppressed("Normal release notes with great features!"))
        org.junit.Assert.assertFalse(isUpdatePromptSuppressed("", "Mod-v2.8.0"))
        org.junit.Assert.assertFalse(isUpdatePromptSuppressed("Fixed prompt issues for station dialog"))
    }
}
