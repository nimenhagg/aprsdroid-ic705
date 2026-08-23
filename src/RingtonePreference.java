package de.duenndns;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

public class RingtonePreference extends Preference {

	public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public RingtonePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public RingtonePreference(Context context) {
		super(context);
		init();
	}

	private void init() {
		setSummaryProvider(pref -> {
			String uriStr = PreferenceManager.getDefaultSharedPreferences(pref.getContext()).getString(pref.getKey(), null);
			if (uriStr == null) {
				return pref.getContext().getString(android.R.string.untitled);
			}
			if (uriStr.isEmpty()) {
				return "Silent";
			}
			try {
				Uri uri = Uri.parse(uriStr);
				Ringtone ringtone = RingtoneManager.getRingtone(pref.getContext(), uri);
				if (ringtone != null) {
					return ringtone.getTitle(pref.getContext());
				}
			} catch (Exception ignored) {}
			return uriStr;
		});
	}
}
