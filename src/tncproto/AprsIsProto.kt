package org.aprsdroid.app

import java.io.InputStream
import java.io.OutputStream

class AprsIsProto(val service: AprsService, isStream: InputStream, osStream: OutputStream) : Tnc2Proto(isStream, osStream) {
    init {
        val loginfilter = service.prefs.getLoginString() + service.prefs.getFilterString(service)
        service.postAddPost(StorageDatabase.Companion.Post.TYPE_TX, R.string.p_conn_aprsis, loginfilter)
        writer.println(loginfilter)
    }
}
