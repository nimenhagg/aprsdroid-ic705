package org.aprsdroid.app

internal object PostCallResolver {
    /**
     * Resolves the station callsign associated with an APRS post.
     * If an explicit call is provided, it is returned.
     * Otherwise, for POST, INCMG, and TX types, the callsign is parsed
     * from the standard AX.25 / APRS message header prefix (before '>').
     */
    fun resolve(cleanMessage: String, postType: Int, explicitCall: String? = null): String? {
        if (!explicitCall.isNullOrBlank()) {
            return explicitCall.trim()
        }
        if (postType == StorageDatabase.Companion.Post.TYPE_POST ||
            postType == StorageDatabase.Companion.Post.TYPE_INCMG ||
            postType == StorageDatabase.Companion.Post.TYPE_TX
        ) {
            val idx = cleanMessage.indexOf('>')
            if (idx > 0) {
                val call = cleanMessage.substring(0, idx).trim()
                if (call.isNotEmpty()) return call
            }
        }
        return null
    }
}
