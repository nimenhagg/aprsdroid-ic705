package org.aprsdroid.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class APRSdroidApplication extends Application {

	override def onCreate() {
		super.onCreate()
		DynamicColors.applyToActivitiesIfAvailable(this)
		ServiceNotifier.instance.setupChannels(this)
		MapModes.initialize(this)
	}
}
