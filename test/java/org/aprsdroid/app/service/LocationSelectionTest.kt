package org.aprsdroid.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSelectionTest {
    @Test
    fun newestCandidateIsSelected() {
        val candidates = listOf(
            Candidate("gps", 100L),
            Candidate("network", 300L),
            Candidate("passive", 200L),
        )

        assertEquals(
            "network",
            newestByTimestamp(candidates) { it.timestamp }?.name,
        )
    }

    @Test
    fun equalTimestampsKeepFirstCandidate() {
        val candidates = listOf(
            Candidate("gps", 300L),
            Candidate("network", 300L),
        )

        assertEquals(
            "gps",
            newestByTimestamp(candidates) { it.timestamp }?.name,
        )
    }

    @Test
    fun emptyCandidatesReturnNull() {
        assertNull(
            newestByTimestamp(emptyList<Candidate>()) { it.timestamp },
        )
    }

    private data class Candidate(
        val name: String,
        val timestamp: Long,
    )
}
