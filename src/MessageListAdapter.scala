package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{AsyncTask, Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}
import _root_.androidx.core.content.ContextCompat

object MessageListAdapter {
	import StorageDatabase.Message._
	val LIST_FROM = Array("TSS", CALL, TEXT)
	val LIST_TO = Array(R.id.listts, R.id.liststatus, R.id.listmessage)

	val NUM_OF_RETRIES = 7
	// null, incoming (primary), out-new (amber), out-acked (forest green), out-rejected (red), out-aborted (slate)
	val STATUS_COLORS = Array(0, 0xff00677d, 0xff7d5700, 0xff006d44, 0xffba1a1a, 0xff70787d)
}

class MessageListAdapter(context : Context, prefs : PrefsWrapper,
	mycall : String, targetcall : String)
		extends SimpleCursorAdapter(context, R.layout.listitem, null, MessageListAdapter.LIST_FROM, MessageListAdapter.LIST_TO) {

	lazy val storage = StorageDatabase.open(context)

	reload()

	lazy val locReceiver = new LocationReceiver2(load_cursor,
		replace_cursor, cancel_cursor)

	ContextCompat.registerReceiver(context, locReceiver, new IntentFilter(AprsService.MESSAGE), Context.RECEIVER_EXPORTED)

	override def bindView(view : View, context : Context, cursor : Cursor) {
		import StorageDatabase.Message._
		val msgtype = cursor.getInt(COLUMN_TYPE)
		val retrycnt = cursor.getInt(COLUMN_RETRYCNT)
		
		val msgView = view.findViewById(R.id.listmessage).asInstanceOf[TextView]
		msgView.setTextColor(0xff191c1e)

		val statusview = view.findViewById(R.id.liststatus).asInstanceOf[TextView]
		if (msgtype >= 0 && msgtype < MessageListAdapter.STATUS_COLORS.length) {
			statusview.setTextColor(MessageListAdapter.STATUS_COLORS(msgtype))
		} else {
			statusview.setTextColor(0xff00677d)
		}

		super.bindView(view, context, cursor)
		val status = msgtype match {
		case TYPE_INCOMING =>
			targetcall
		case TYPE_OUT_NEW =>
			"%s %d/%d".format(mycall, retrycnt, MessageListAdapter.NUM_OF_RETRIES)
		case TYPE_OUT_ACKED =>
			mycall
		case TYPE_OUT_REJECTED =>
			"%s %s".format(mycall, context.getString(R.string.msg_type_rejected))
		case TYPE_OUT_ABORTED =>
			"%s %s".format(mycall, context.getString(R.string.msg_type_aborted))
		}
		statusview.setText(status)
	}

	def load_cursor(i : Intent) = {
		val c = storage.getMessages(targetcall)
		c.getCount()
		c
	}

	def replace_cursor(c : Cursor) {
		changeCursor(c)
		context.asInstanceOf[LoadingIndicator].onStopLoading()
	}
	def cancel_cursor(c : Cursor) {
		c.close()
	}

	def reload() {
		locReceiver.startTask(null)
	}

	def onDestroy() {
		context.unregisterReceiver(locReceiver)
		changeCursor(null)
	}
}
