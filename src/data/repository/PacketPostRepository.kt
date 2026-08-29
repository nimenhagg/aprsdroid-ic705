package org.aprsdroid.app.data.repository

import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Position as AprsPosition
import org.aprsdroid.app.StorageDatabase

/**
 * Narrow persistence boundary for APRS posts and decoded positions.
 *
 * The current implementation delegates to the legacy StorageDatabase unchanged;
 * this interface exists so service orchestration no longer depends directly on
 * SQLite details.
 */
internal interface PacketPostRepository {
    fun addPost(ts: Long, postType: Int, status: String, message: String)

    fun addPosition(
        ts: Long,
        packet: APRSPacket,
        position: AprsPosition,
        courseAndSpeed: CourseAndSpeedExtension?,
        objectName: String?,
    )
}

internal class StorageDatabasePacketPostRepository(
    private val database: StorageDatabase,
) : PacketPostRepository {
    override fun addPost(ts: Long, postType: Int, status: String, message: String) {
        database.addPost(ts, postType, status, message)
    }

    override fun addPosition(
        ts: Long,
        packet: APRSPacket,
        position: AprsPosition,
        courseAndSpeed: CourseAndSpeedExtension?,
        objectName: String?,
    ) {
        database.addPosition(ts, packet, position, courseAndSpeed, objectName)
    }
}
