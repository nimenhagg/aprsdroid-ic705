package org.aprsdroid.app

import _root_.android.database.Cursor
import _root_.android.content.Context
import _root_.android.graphics.Typeface
import _root_.android.text.SpannableStringBuilder
import _root_.android.text.Spanned
import _root_.android.text.style.ForegroundColorSpan
import _root_.android.text.style.StyleSpan
import _root_.android.view.View
import _root_.android.widget.SimpleCursorAdapter
import _root_.android.widget.SimpleCursorAdapter.ViewBinder
import _root_.android.widget.TextView

object PostListAdapter {
	val LIST_FROM = Array("TSS", StorageDatabase.Post.STATUS,
		StorageDatabase.Post.MESSAGE)
	val LIST_TO = Array(R.id.listts, R.id.liststatus, R.id.listmessage)
}

class PostListAdapter(context : Context)
		extends SimpleCursorAdapter(context, R.layout.listitem,
			null, PostListAdapter.LIST_FROM, PostListAdapter.LIST_TO) {

	setViewBinder(new PostViewBinder())
}

class PostViewBinder extends ViewBinder {

	val COLUMN_STATUS = 4

	// Status text colors: post (success), info (indigo), error (red), incoming (primary teal), tx (forest)
	val STATUS_COLORS = Array(0xff006d44, 0xff5b53a4, 0xffba1a1a, 0xff00677d, 0xff006d44)

	override def setViewValue (view : View, cursor : Cursor, columnIndex : Int) : Boolean = {
		import StorageDatabase.Post._
		columnIndex match {
		case COLUMN_STATUS =>
			val t = cursor.getInt(COLUMN_TYPE)
			val s = cursor.getString(COLUMN_STATUS)
			val v = view.asInstanceOf[TextView]
			v.setText(s)
			if (t >= 0 && t < STATUS_COLORS.length)
				v.setTextColor(STATUS_COLORS(t))
			else
				v.setTextColor(0xff00677d)
			true

		case COLUMN_MESSAGE =>
			val t = cursor.getInt(COLUMN_TYPE)
			val m = cursor.getString(COLUMN_MESSAGE)
			val v = view.asInstanceOf[TextView]

			if (m != null && m.contains(">") && (t == TYPE_POST || t == TYPE_INCMG || t == TYPE_TX)) {
				val ssb = new SpannableStringBuilder(m)
				val colonIdx = m.indexOf(':')
				val headerEnd = if (colonIdx > 0) colonIdx + 1 else m.length()

				// Callsign + Path header in High-Contrast Primary Teal + Bold
				ssb.setSpan(new ForegroundColorSpan(0xff00677d), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

				// Payload in High-Contrast Dark Text
				if (headerEnd < m.length()) {
					ssb.setSpan(new ForegroundColorSpan(0xff191c1e), headerEnd, m.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				v.setText(ssb)
				v.setTypeface(Typeface.MONOSPACE)
			} else {
				v.setText(m)
				if (t == TYPE_ERROR) {
					v.setTextColor(0xffba1a1a)
				} else if (t == TYPE_INFO) {
					v.setTextColor(0xff40484c)
				} else {
					v.setTextColor(0xff191c1e)
				}
				v.setTypeface(if (t == TYPE_POST || t == TYPE_INCMG || t == TYPE_TX) Typeface.MONOSPACE else Typeface.DEFAULT)
			}
			true

		case _ => false
		}
	}
}
