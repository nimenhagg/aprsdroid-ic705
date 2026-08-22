package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.content._
import _root_.android.database.Cursor
import _root_.android.os.{AsyncTask, Bundle, Handler}
import _root_.android.text.format.DateUtils
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.{SimpleCursorAdapter, TextView}
import _root_.android.widget.FilterQueryProvider
import _root_.androidx.core.content.ContextCompat

object StationListAdapter {
	import StorageDatabase.Station._
	val LIST_FROM = Array(CALL, COMMENT, QRG)
	val LIST_TO = Array(R.id.station_call, R.id.listmessage, R.id.station_qrg)

	val SINGLE = 0
	val NEIGHBORS = 1
	val SSIDS = 2
}

class StationListAdapter(context : Context, prefs : PrefsWrapper,
	mycall : String, targetcall : String, mode : Int)
		extends SimpleCursorAdapter(context, R.layout.stationview, null, StationListAdapter.LIST_FROM, StationListAdapter.LIST_TO) {

	var my_lat = 0
	var my_lon = 0
	var reload_pending = 0
	lazy val storage = StorageDatabase.open(context)

	if (mode == StationListAdapter.NEIGHBORS)
		setFilterQueryProvider(getNeighborFilter())

	reload()

	lazy val locReceiver = new LocationReceiver2(load_cursor,
		replace_cursor, cancel_cursor)

	ContextCompat.registerReceiver(context, locReceiver, new IntentFilter(AprsService.UPDATE), Context.RECEIVER_EXPORTED)

	// return compass bearing for a given value
	private val LETTERS = Array("N", "NE", "E", "SE", "S", "SW", "W", "NW")
	def getBearing(b : Double) = LETTERS(((b.toInt + 22 + 720) % 360) / 45)

	override def bindView(view : View, context : Context, cursor : Cursor) {
		import StorageDatabase.Station._

		val distage = view.findViewById(R.id.station_distage).asInstanceOf[TextView]
		val call = cursor.getString(COLUMN_CALL)
		val ts = cursor.getLong(COLUMN_TS)
		val age = DateUtils.getRelativeTimeSpanString(context, ts)
		val lat = cursor.getInt(COLUMN_LAT)
		val lon = cursor.getInt(COLUMN_LON)
		val qrg = cursor.getString(COLUMN_QRG)
		val symbol = cursor.getString(COLUMN_SYMBOL)
		val dist = Array[Float](0, 0)

		if (call == mycall) {
			view.setBackgroundColor(0x2600677d)
		} else if (call == targetcall) {
			view.setBackgroundColor(0x265b53a4)
		} else {
			view.setBackgroundColor(0)
		}

		distage.setTextColor(0xff40484c)
		view.findViewById(R.id.station_call).asInstanceOf[TextView].setTextColor(0xff00677d)
		view.findViewById(R.id.station_qrg).asInstanceOf[TextView].setTextColor(0xff006874)
		val qrg_visible = if (qrg != null && qrg != "") View.VISIBLE else View.GONE
		view.findViewById(R.id.station_qrg).asInstanceOf[View].setVisibility(qrg_visible)
		val MCD = 1000000.0
		android.location.Location.distanceBetween(my_lat/MCD, my_lon/MCD,
			lat/MCD, lon/MCD, dist)
		distage.setText("%1.1f km %s\n%s".format(dist(0)/1000.0, getBearing(dist(1)), age))
		view.findViewById(R.id.station_symbol).asInstanceOf[SymbolView].setSymbol(symbol)
		super.bindView(view, context, cursor)
	}

	def getNeighborFilter() = new FilterQueryProvider() {
		def runQuery(constraint : CharSequence) = {
			if (constraint.length() > 0)
				storage.getNeighborsLike("%s%%".format(constraint),
					my_lat, my_lon, System.currentTimeMillis - prefs.getShowAge(), "300")
			else
				storage.getNeighbors(mycall, my_lat, my_lon,
					System.currentTimeMillis - prefs.getShowAge(), "300")
		}
	}

	def load_cursor(i : Intent) = {
		import StationListAdapter._
		val cursor = storage.getStaPosition(mycall)
		if (cursor.getCount() > 0) {
			cursor.moveToFirst()
			my_lat = cursor.getInt(StorageDatabase.Station.COLUMN_LAT)
			my_lon = cursor.getInt(StorageDatabase.Station.COLUMN_LON)
		}
		cursor.close()
		val c = mode match {
			case SINGLE	=> storage.getStaPosition(targetcall)
			case NEIGHBORS	=> storage.getNeighbors(mycall, my_lat, my_lon,
				System.currentTimeMillis - prefs.getShowAge(), "300")
			case SSIDS	=> storage.getAllSsids(targetcall)
		}
		c.getCount()
		c
	}

	def replace_cursor(c : Cursor) {
		if (!context.asInstanceOf[ListActivity].getListView().hasTextFilter())
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
