package org.aprsdroid.app.service

import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.APRSTypes
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.InformationField
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.ObjectPacket
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.Position as AprsPosition
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.StorageDatabase
import org.aprsdroid.app.data.repository.PacketPostRepository

internal enum class PostRoute {
    PARSE,
    LOG_ONLY,
}

internal fun postRoute(postType: Int): PostRoute = when (postType) {
    StorageDatabase.Companion.Post.TYPE_POST,
    StorageDatabase.Companion.Post.TYPE_INCMG,
    StorageDatabase.Companion.Post.TYPE_TX -> PostRoute.PARSE

    else -> PostRoute.LOG_ONLY
}

/**
 * Owns APRS post persistence and packet parsing/routing previously performed by AprsService.
 *
 * Android broadcasts and notifications stay outside this class and are exposed as callbacks,
 * keeping the Service as the platform boundary while removing database/parser orchestration.
 */
internal class PacketPersistenceCoordinator(
    private val repository: PacketPostRepository,
    private val callSsid: () -> String,
    private val onOwnDigipeat: (lastDigi: String, information: String) -> Unit,
    private val onMessage: (ts: Long, packet: APRSPacket, message: MessagePacket) -> Unit,
    private val onPositionPersisted: (
        ts: Long,
        packet: APRSPacket,
        position: AprsPosition,
        courseAndSpeed: CourseAndSpeedExtension?,
        objectName: String?,
    ) -> Unit,
    private val onPostUpdated: (postType: Int, message: String) -> Unit,
    private val onLogOnly: (status: String?, message: String) -> Unit,
    private val onDebug: (message: String) -> Unit,
    private val onParseFailure: (message: String, error: Exception) -> Unit,
    private val packetParser: (String) -> APRSPacket = { Parser.parse(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun addPost(postType: Int, status: String?, message: String) {
        val ts = clock()
        repository.addPost(ts, postType, status ?: "", message)

        when (postRoute(postType)) {
            PostRoute.PARSE -> parsePacket(ts, message, postType)
            PostRoute.LOG_ONLY -> onLogOnly(status, message)
        }

        onPostUpdated(postType, message)
    }

    fun parsePacket(ts: Long, message: String, source: Int) {
        try {
            var packet = packetParser(message)
            if (packet.type == APRSTypes.T_THIRDPARTY) {
                onDebug("parsePacket: third-party packet from ${packet.sourceCall}")
                val inner = packet.aprsInformation.toString()
                packet = packetParser(inner.substring(1))
            }

            if (
                source == StorageDatabase.Companion.Post.TYPE_INCMG &&
                packet.sourceCall.equals(callSsid(), ignoreCase = true) &&
                packet.lastUsedDigi != null
            ) {
                onOwnDigipeat(
                    packet.lastUsedDigi.toString(),
                    packet.aprsInformation.toString(),
                )
                return
            }

            if (packet.aprsInformation == null) {
                onDebug("parsePacket() misses payload: $message")
                return
            }
            if (packet.hasFault()) {
                throw Exception("FAP fault")
            }

            when (val information = packet.aprsInformation) {
                is PositionPacket -> addPosition(
                    ts,
                    packet,
                    information,
                    information.position,
                    null,
                )

                is ObjectPacket -> addPosition(
                    ts,
                    packet,
                    information,
                    information.position,
                    information.objectName,
                )

                is MessagePacket -> onMessage(ts, packet, information)
            }
        } catch (e: Exception) {
            onParseFailure(message, e)
        }
    }

    fun courseAndSpeed(field: InformationField): CourseAndSpeedExtension? {
        return field.extension as? CourseAndSpeedExtension
    }

    fun addPosition(
        ts: Long,
        packet: APRSPacket,
        field: InformationField,
        position: AprsPosition,
        objectName: String?,
    ) {
        val courseAndSpeed = courseAndSpeed(field)
        repository.addPosition(ts, packet, position, courseAndSpeed, objectName)
        onPositionPersisted(ts, packet, position, courseAndSpeed, objectName)
    }
}
