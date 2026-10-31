package org.aprsdroid.app.update

import org.junit.Assert.assertEquals
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
    fun comparesSemanticVersionsNumerically() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(AppVersion(1, 9, 2), AppVersion(1, 9, 2))
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
