package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.os.Bundle

class LoadingListActivity extends ListActivity
		with UIHelper {

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setupModernActionBar()
	}

	override def onStartLoading() {
	}

	override def onStopLoading() {
	}

	override def onResume() {
		super.onResume()
		setupModernActionBar()
		setKeepScreenOn()
		setVolumeControls()
	}
}
