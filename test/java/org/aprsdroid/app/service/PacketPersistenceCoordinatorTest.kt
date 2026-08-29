package org.aprsdroid.app.service

import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.Position as AprsPosition
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.PacketPostRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketPersistenceCoordinatorTest {
    @Test
    fun postRoutingMatchesLegacyRules() {
        assertEquals(PostRoute.PARSE, postRoute(StorageDatabase.Companion.Post.TYPE_POST))
        assertEquals(PostRoute.PARSE, postRoute(StorageDatabase.Companion.Post.TYPE_INCMG))
        assertEquals(PostRoute.PARSE, postRoute(StorageDatabase.Companion.Post.TYPE_TX))
        assertEquals(PostRoute.LOG_ONLY, postRoute(StorageDatabase.Companion.Post.TYPE_INFO))
        assertEquals(PostRoute.LOG_ONLY, postRoute(StorageDatabase.Companion.Post.TYPE_ERROR))
    }

    @Test
    fun logOnlyPostPersistsThenLogsThenBroadcasts() {
        val events = mutableListOf<String>()
        val repository = RecordingRepository(events)
        val coordinator = coordinator(
            repository = repository,
            events = events,
            parser = { throw AssertionError("parser must not run") },
        )

        coordinator.addPost(StorageDatabase.Companion.Post.TYPE_INFO, null, "hello")

        assertEquals(
            listOf(
                "store:123:1::hello",
                "log:null:hello",
                "update:1:hello",
            ),
            events,
        )
    }

    @Test
    fun parsedPostPersistsThenAttemptsParseThenBroadcasts() {
        val events = mutableListOf<String>()
        val repository = RecordingRepository(events)
        val failure = IllegalStateException("parse")
        val coordinator = coordinator(
            repository = repository,
            events = events,
            parser = { throw failure },
        )

        coordinator.addPost(StorageDatabase.Companion.Post.TYPE_TX, "Sent", "packet")

        assertEquals(
            listOf(
                "store:123:4:Sent:packet",
                "parseFailure:packet:${failure}",
                "update:4:packet",
            ),
            events,
        )
    }

    private fun coordinator(
        repository: PacketPostRepository,
        events: MutableList<String>,
        parser: (String) -> APRSPacket,
    ) = PacketPersistenceCoordinator(
        repository = repository,
        callSsid = { "N0CALL" },
        onOwnDigipeat = { digi, info -> events += "digipeat:$digi:$info" },
        onMessage = { _, _, _ -> events += "message" },
        onPositionPersisted = { _, _, _, _, _ -> events += "position" },
        onPostUpdated = { type, message -> events += "update:$type:$message" },
        onLogOnly = { status, message -> events += "log:$status:$message" },
        onDebug = { message -> events += "debug:$message" },
        onParseFailure = { message, error -> events += "parseFailure:$message:$error" },
        packetParser = parser,
        clock = { 123L },
    )

    private class RecordingRepository(
        private val events: MutableList<String>,
    ) : PacketPostRepository {
        override fun addPost(ts: Long, postType: Int, status: String, message: String) {
            events += "store:$ts:$postType:$status:$message"
        }

        override fun addPosition(
            ts: Long,
            packet: APRSPacket,
            position: AprsPosition,
            courseAndSpeed: CourseAndSpeedExtension?,
            objectName: String?,
        ) {
            events += "position-store"
        }
    }
}
