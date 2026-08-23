package org.aprsdroid.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.widget.FilterQueryProvider
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.Position as AprsPosition
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

class StorageDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val TAG = "APRSdroid.Storage"
        const val DB_VERSION = 4
        const val DB_NAME = "storage.db"

        const val TSS_COL = "DATETIME(TS/1000, 'unixepoch', 'localtime') as TSS"
        const val TABLE_INDEX = "CREATE INDEX idx_%s_%s ON %s (%s)"

        object Post {
            const val TABLE = "posts"
            const val _ID = "_id"
            const val TS = "ts"
            const val TYPE = "type"
            const val STATUS = "status"
            const val MESSAGE = "message"

            val TABLE_CREATE = "CREATE TABLE $TABLE ($_ID INTEGER PRIMARY KEY AUTOINCREMENT, $TS LONG, $TYPE INTEGER, $STATUS TEXT, $MESSAGE TEXT)"
            val COLUMNS = arrayOf(_ID, TS, TSS_COL, TYPE, STATUS, MESSAGE)

            const val TYPE_POST = 0
            const val TYPE_INFO = 1
            const val TYPE_ERROR = 2
            const val TYPE_INCMG = 3
            const val TYPE_TX = 4

            const val COLUMN_TS = 1
            const val COLUMN_TSS = 2
            const val COLUMN_TYPE = 3
            const val COLUMN_MESSAGE = 5

            var trimCounter = 0
        }

        object Station {
            const val TABLE = "stations"
            const val _ID = "_id"
            const val TS = "ts"
            const val CALL = "call"
            const val LAT = "lat"
            const val LON = "lon"
            const val SPEED = "speed"
            const val COURSE = "course"
            const val ALT = "alt"
            const val SYMBOL = "symbol"
            const val COMMENT = "comment"
            const val ORIGIN = "origin"
            const val QRG = "qrg"
            const val FLAGS = "flags"

            val TABLE_CREATE = "CREATE TABLE $TABLE ($_ID INTEGER PRIMARY KEY AUTOINCREMENT, $TS LONG, $CALL TEXT UNIQUE, $LAT INTEGER, $LON INTEGER, $SPEED INTEGER, $COURSE INTEGER, $ALT INTEGER, $SYMBOL TEXT, $COMMENT TEXT, $ORIGIN TEXT, $QRG TEXT, $FLAGS INTEGER)"
            const val TABLE_DROP = "DROP TABLE stations"
            val COLUMNS = arrayOf(_ID, TS, CALL, LAT, LON, SYMBOL, COMMENT, SPEED, COURSE, ALT, ORIGIN, QRG)
            const val COL_DIST = "((lat - %d)*(lat - %d) + (lon - %d)*(lon - %d)*%d/100) as dist"

            const val COLUMN_TS = 1
            const val COLUMN_CALL = 2
            const val COLUMN_LAT = 3
            const val COLUMN_LON = 4
            const val COLUMN_SYMBOL = 5
            const val COLUMN_COMMENT = 6
            const val COLUMN_SPEED = 7
            const val COLUMN_COURSE = 8
            const val COLUMN_ALT = 9
            const val COLUMN_ORIGIN = 10
            const val COLUMN_QRG = 11
            const val COLUMN_FLAGS = 12

            val COLUMNS_MAP = arrayOf(_ID, CALL, LAT, LON, SYMBOL, ORIGIN, QRG, COMMENT, SPEED, COURSE)
            const val COLUMN_MAP_CALL = 1
            const val COLUMN_MAP_LAT = 2
            const val COLUMN_MAP_LON = 3
            const val COLUMN_MAP_SYMBOL = 4
            const val COLUMN_MAP_ORIGIN = 5
            const val COLUMN_MAP_QRG = 6
            const val COLUMN_MAP_COMMENT = 7
            const val COLUMN_MAP_SPEED = 8
            const val COLUMN_MAP_CSE = 9

            const val FLAG_MSGCAPABLE = 1
            const val FLAG_OBJECT = 2
            const val FLAG_MOVING = 4
        }

        object Position {
            const val TABLE = "positions"
            const val _ID = "_id"
            const val TS = "ts"
            const val CALL = "call"
            const val LAT = "lat"
            const val LON = "lon"

            val TABLE_CREATE = "CREATE TABLE $TABLE ($_ID INTEGER PRIMARY KEY AUTOINCREMENT, $TS LONG, $CALL TEXT, $LAT INTEGER, $LON INTEGER)"
            val COLUMNS = arrayOf(_ID, TS, CALL, LAT, LON)
            const val COLUMN_TS = 1
            const val COLUMN_CALL = 2
            const val COLUMN_LAT = 3
            const val COLUMN_LON = 4
        }

        object Message {
            const val TABLE = "messages"
            const val _ID = "_id"
            const val TS = "ts"
            const val RETRYCNT = "retrycnt"
            const val CALL = "call"
            const val MSGID = "msgid"
            const val TYPE = "type"
            const val TEXT = "text"

            val TABLE_CREATE = "CREATE TABLE $TABLE ($_ID INTEGER PRIMARY KEY AUTOINCREMENT, $TS LONG, $RETRYCNT INT, $CALL TEXT, $MSGID TEXT, $TYPE INTEGER, $TEXT TEXT)"
            val COLUMNS = arrayOf(_ID, TS, TSS_COL, RETRYCNT, CALL, MSGID, TYPE, TEXT)

            const val COLUMN_TS = 1
            const val COLUMN_TTS = 2
            const val COLUMN_RETRYCNT = 3
            const val COLUMN_CALL = 4
            const val COLUMN_MSGID = 5
            const val COLUMN_TYPE = 6
            const val COLUMN_TEXT = 7

            const val TYPE_INCOMING = 1
            const val TYPE_OUT_NEW = 2
            const val TYPE_OUT_ACKED = 3
            const val TYPE_OUT_REJECTED = 4
            const val TYPE_OUT_ABORTED = 5
        }

        @Volatile
        private var singleton: StorageDatabase? = null

        @JvmStatic
        fun open(context: Context): StorageDatabase {
            return singleton ?: synchronized(this) {
                singleton ?: StorageDatabase(context.applicationContext).also { singleton = it }
            }
        }

        @JvmStatic
        fun cursor2call(c: Cursor): String? {
            val msgidx = c.getColumnIndex(Post.MESSAGE)
            val callidx = c.getColumnIndex(Station.CALL)
            return if (msgidx != -1 && callidx == -1) {
                val t = c.getInt(Post.COLUMN_TYPE)
                if (t == Post.TYPE_POST || t == Post.TYPE_INCMG) {
                    c.getString(msgidx).split(">")[0]
                } else null
            } else {
                if (callidx != -1) c.getString(callidx) else null
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "onCreate(): creating new database $DB_NAME")
        db.execSQL(Post.TABLE_CREATE)
        db.execSQL(Station.TABLE_CREATE)
        arrayOf("lat", "lon").forEach { col ->
            db.execSQL(String.format(Locale.US, TABLE_INDEX, Station.TABLE, col, Station.TABLE, col))
        }
        db.execSQL(Position.TABLE_CREATE)
        db.execSQL(Message.TABLE_CREATE)
        arrayOf(Position.TABLE, Station.TABLE).forEach { tab ->
            db.execSQL(String.format(Locale.US, TABLE_INDEX, tab, "ts", tab, "ts"))
        }
        arrayOf("call", "type").forEach { col ->
            db.execSQL(String.format(Locale.US, TABLE_INDEX, Message.TABLE, col, Message.TABLE, col))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, from: Int, to: Int) {
        if (from <= 1 && to <= 3) {
            db.execSQL(Message.TABLE_CREATE)
        }
        if (from == 2 && to <= 3) {
            db.execSQL("ALTER TABLE message RENAME TO messages")
        }
        if (from <= 2 && to <= 3) {
            db.execSQL("DROP TABLE position")
            db.execSQL(Station.TABLE_CREATE)
            db.execSQL(Position.TABLE_CREATE)
        }
        if (to <= 4) {
            arrayOf(Position.TABLE, Station.TABLE).forEach { tab ->
                db.execSQL(String.format(Locale.US, TABLE_INDEX, tab, "ts", tab, "ts"))
            }
            arrayOf("call", "type").forEach { col ->
                db.execSQL(String.format(Locale.US, TABLE_INDEX, Message.TABLE, col, Message.TABLE, col))
            }
        }
    }

    @JvmOverloads
    fun trimPosts(ts: Long = System.currentTimeMillis() - 2L * 24 * 3600 * 1000) {
        writableDatabase.execSQL("DELETE FROM ${Post.TABLE} WHERE ${Post.TS} < ?", arrayOf(ts.toString()))
        writableDatabase.execSQL("DELETE FROM ${Position.TABLE} WHERE ${Position.TS} < ?", arrayOf(ts.toString()))
        if (ts == Long.MAX_VALUE) {
            writableDatabase.execSQL("DELETE FROM ${Station.TABLE} WHERE ${Station.TS} < ?", arrayOf(ts.toString()))
        }
    }

    fun addPosition(ts: Long, ap: APRSPacket, pos: AprsPosition, cse: CourseAndSpeedExtension?, objectname: String?) {
        val cv = ContentValues()
        val call = ap.sourceCall
        val lat: Int = (pos.latitude * 1000000).toInt()
        val lon: Int = (pos.longitude * 1000000).toInt()
        val sym = "${pos.symbolTable}${pos.symbolCode}"
        val comment = ap.aprsInformation.comment
        val qrg = AprsPacket.parseQrg(comment)
        cv.put(Position.TS, ts)
        cv.put(Position.CALL, objectname ?: call)
        cv.put(Position.LAT, lat)
        cv.put(Position.LON, lon)
        writableDatabase.insertOrThrow(Position.TABLE, Position.CALL, cv)

        if (objectname != null) cv.put(Station.ORIGIN, call)
        cv.put(Station.SYMBOL, sym)
        cv.put(Station.COMMENT, comment)
        cv.put(Station.QRG, qrg)
        if (cse != null) {
            cv.put(Station.SPEED, cse.speed)
            cv.put(Station.COURSE, cse.course)
        }
        Log.d(TAG, String.format(Locale.US, "got %s(%d, %d)%s -> %s", call, lat, lon, sym, comment))
        writableDatabase.replaceOrThrow(Station.TABLE, Station.CALL, cv)
    }

    fun isMessageDuplicate(call: String, msgid: String, text: String): Boolean {
        val c = readableDatabase.query(
            Message.TABLE, Message.COLUMNS,
            "type = 1 AND call = ? AND msgid = ? AND text = ?",
            arrayOf(call, msgid, text),
            null, null, null, null
        )
        val result = c.count > 0
        c.close()
        return result
    }

    fun addMessage(ts: Long, srccall: String, msg: MessagePacket): Boolean {
        if (isMessageDuplicate(srccall, msg.messageNumber, msg.messageBody)) {
            Log.i(TAG, String.format(Locale.US, "received duplicate message from %s: %s", srccall, msg))
            return false
        }
        val cv = ContentValues().apply {
            put(Message.TS, ts)
            put(Message.RETRYCNT, 0)
            put(Message.CALL, srccall)
            put(Message.MSGID, msg.messageNumber)
            put(Message.TYPE, Message.TYPE_INCOMING)
            put(Message.TEXT, msg.messageBody)
        }
        addMessage(cv)
        return true
    }

    fun getStations(sel: String?, selArgs: Array<String>?, limit: String?): Cursor {
        return readableDatabase.query(Station.TABLE, Station.COLUMNS_MAP, sel, selArgs, null, null, "CALL", limit)
    }

    fun getRectStations(lat1: Int, lon1: Int, lat2: Int, lon2: Int, limit: String?): Cursor {
        Log.d(TAG, String.format(Locale.US, "StorageDatabase.getRectStations: %d,%d - %d,%d", lat1, lon1, lat2, lon2))
        val query = if (lon1 <= lon2) "LAT >= ? AND LAT <= ? AND LON >= ? AND LON <= ?"
        else "LAT >= ? AND LAT <= ? AND (LON <= ? OR LON >= ?)"
        return getStations(query, arrayOf(lat1.toString(), lat2.toString(), lon1.toString(), lon2.toString()), limit)
    }

    fun getStaPosition(call: String): Cursor {
        return readableDatabase.query(Station.TABLE, Station.COLUMNS, "call LIKE ?", arrayOf(call), null, null, "_ID DESC", "1")
    }

    fun getAllStaPositions(limit: String?): Cursor {
        return readableDatabase.query(Position.TABLE, Position.COLUMNS, "TS > ?", arrayOf(limit), null, null, "CALL, _ID", null)
    }

    fun getAllSsids(call: String): Cursor {
        val barecall = call.split(Regex("[- _]+"))[0]
        val wildcard = "$barecall-%"
        return readableDatabase.query(Station.TABLE, Station.COLUMNS,
            "call = ? OR call LIKE ? OR origin = ? OR origin LIKE ?",
            arrayOf(barecall, wildcard, barecall, wildcard),
            null, null, null, null
        )
    }

    fun getNeighbors(mycall: String, lat: Int, lon: Int, ts: Long, limit: String?): Cursor {
        val corr = (cos(PI * lat / 180000000.0) * cos(PI * lat / 180000000.0) * 100).toInt()
        val distCol = String.format(Locale.US, Station.COL_DIST, lat, lat, lon, lon, corr)
        val newcols = Station.COLUMNS + distCol
        return readableDatabase.query(Station.TABLE, newcols, "ts > ? or call = ?", arrayOf(ts.toString(), mycall), null, null, "dist", limit)
    }

    fun getNeighborsLike(call: String, lat: Int, lon: Int, ts: Long, limit: String?): Cursor {
        val corr = (cos(PI * lat / 180000000.0) * cos(PI * lat / 180000000.0) * 100).toInt()
        val distCol = String.format(Locale.US, Station.COL_DIST, lat, lat, lon, lon, corr)
        val newcols = Station.COLUMNS + distCol
        return readableDatabase.query(Station.TABLE, newcols, "call like ?", arrayOf(call), null, null, "dist", limit)
    }

    fun addPost(ts: Long, posttype: Int, status: String, message: String) {
        val cv = ContentValues().apply {
            put(Post.TS, ts)
            put(Post.TYPE, posttype)
            put(Post.STATUS, status)
            put(Post.MESSAGE, message)
        }
        writableDatabase.insertOrThrow(Post.TABLE, Post.MESSAGE, cv)
        if (Post.trimCounter == 0) {
            trimPosts()
            Post.trimCounter = 100
        } else {
            Post.trimCounter -= 1
        }
    }

    fun getPosts(sel: String?, selArgs: Array<String>?, limit: String?): Cursor {
        return writableDatabase.query(Post.TABLE, Post.COLUMNS, sel, selArgs, null, null, "_ID DESC", limit)
    }

    fun getPosts(limit: String?): Cursor = getPosts(null, null, limit)

    fun getStaPosts(call: String, limit: String?): Cursor {
        val start = "$call%"
        val obj1 = "%;$call%"
        val obj2 = "%) $call%"
        return getPosts("message LIKE ? OR message LIKE ? OR message LIKE ?", arrayOf(start, obj1, obj2), limit)
    }

    fun getExportPosts(call: String?): Cursor {
        return if (call != null) {
            writableDatabase.query(Post.TABLE, Post.COLUMNS, "type in (0, 3) and message LIKE ?", arrayOf("$call%"), null, null, null, null)
        } else {
            writableDatabase.query(Post.TABLE, Post.COLUMNS, "type in (0, 3)", null, null, null, null, null)
        }
    }

    fun getPostFilter(limit: String?): FilterQueryProvider {
        return FilterQueryProvider { constraint ->
            getPosts("MESSAGE LIKE ?", arrayOf("%$constraint%"), limit)
        }
    }

    fun getMessages(call: String): Cursor {
        return readableDatabase.query(Message.TABLE, Message.COLUMNS, "call = ?", arrayOf(call), null, null, null, null)
    }

    fun getPendingMessages(retries: Int): Cursor {
        return readableDatabase.query(Message.TABLE, Message.COLUMNS, "type = 2 and retrycnt <= ?", arrayOf(retries.toString()), null, null, null, null)
    }

    fun addMessage(cv: ContentValues): Long {
        return writableDatabase.insertOrThrow(Message.TABLE, "_id", cv)
    }

    fun updateMessage(id: Long, cv: ContentValues): Int {
        return writableDatabase.update(Message.TABLE, cv, "_id = ?", arrayOf(id.toString()))
    }

    fun updateMessageType(id: Long, msgType: Int): Int {
        val cv = ContentValues().apply { put(Message.TYPE, msgType) }
        return updateMessage(id, cv)
    }

    fun updateMessageAcked(call: String, msgid: String, newType: Int): Int {
        val cv = ContentValues().apply { put(Message.TYPE, newType) }
        return writableDatabase.update(Message.TABLE, cv, "type = 2 AND call = ? AND msgid = ?", arrayOf(call, msgid))
    }

    fun createMsgId(call: String): Int {
        val c = readableDatabase.query(Message.TABLE, arrayOf("MAX(CAST(msgid AS INTEGER))"), "call = ? AND type != ?", arrayOf(call, Message.TYPE_INCOMING.toString()), null, null, null, null)
        c.moveToFirst()
        val result = if (c.count == 0 || c.isNull(0)) 0 else c.getInt(0) + 1
        Log.d(TAG, String.format(Locale.US, "createMsgId(%s) = %d", call, result))
        c.close()
        return result
    }

    fun deleteMessage(id: Long) {
        writableDatabase.execSQL("DELETE FROM ${Message.TABLE} WHERE ${Message._ID} = ?", arrayOf(id.toString()))
    }

    fun deleteMessages(call: String) {
        writableDatabase.execSQL("DELETE FROM ${Message.TABLE} WHERE ${Message.CALL} = ?", arrayOf(call))
    }

    fun deleteAllMessages() {
        writableDatabase.execSQL("DELETE FROM ${Message.TABLE}")
    }

    fun getConversations(): Cursor {
        return readableDatabase.query("messages", Message.COLUMNS, "_id IN (SELECT MAX(_id) FROM messages GROUP BY call)", null, "call", null, "_id DESC")
    }
}
