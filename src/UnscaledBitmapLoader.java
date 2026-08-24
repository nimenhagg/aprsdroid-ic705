// (C) http://blog.tomgibara.com/post/190539066/android-unscaled-bitmaps

package org.aprsdroid.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public final class UnscaledBitmapLoader {
	private UnscaledBitmapLoader() {}

	public static Bitmap loadFromResource(Resources resources, int resId, BitmapFactory.Options options) {
		if (options == null) options = new BitmapFactory.Options();
		options.inScaled = false;
		return BitmapFactory.decodeResource(resources, resId, options);
	}
}

