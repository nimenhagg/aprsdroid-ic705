package de.duenndns;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

public class PreferenceWithValue extends Preference {
	private CharSequence mInitialSummary = null;
	private boolean mInitialSummaryInitialized = false;

	public PreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public PreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public PreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public PreferenceWithValue(Context context) {
		super(context);
		init();
	}

	private void init() {
		mInitialSummary = getSummary();
		mInitialSummaryInitialized = true;

		setSummaryProvider(pref -> {
			String text = PreferenceManager.getDefaultSharedPreferences(pref.getContext())
					.getString(pref.getKey(), "");
			if (text == null || text.length() == 0) {
				return mInitialSummary;
			} else if (mInitialSummary == null || mInitialSummary.length() == 0) {
				return text;
			} else {
				return mInitialSummary + ": " + text;
			}
		});
	}
}
