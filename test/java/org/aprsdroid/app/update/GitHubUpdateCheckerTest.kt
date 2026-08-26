package org.aprsdroid.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun parsesReleaseTagAndBuildVersionName() {
        assertEquals(AppVersion(1, 9, 3), parseIc705Version("v1.9.3-ic705"))
        assertEquals(
            AppVersion(1, 9, 2),
            parseIc705Version("1.9.2-ic705 (based on APRSdroid v1.7.0)"),
        )
    }

    @Test
    fun comparesSemanticVersionsNumerically() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(AppVersion(1, 9, 2), AppVersion(1, 9, 2))
    }

    @Test
    fun rejectsStringsWithoutSemanticVersion() {
        assertNull(parseIc705Version("unknown"))
    }
}
