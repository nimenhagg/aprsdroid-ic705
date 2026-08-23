package de.duenndns;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

public class PreferenceWithValue extends Preference {
	private CharSequence mInitialSummary = null;
	private boolean mInitialSummaryInitialized = false;

	public PreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		initSummary();
	}

	public PreferenceWithValue(Context context) {
		super(context);
		initSummary();
	}

	private void initSummary() {
		if (!mInitialSummaryInitialized) {
			mInitialSummary = getSummary();
			mInitialSummaryInitialized = true;
		}
	}

	private void setSummaryToText(String text) {
		initSummary();
		if (text == null || text.length() == 0) {
			setSummary(mInitialSummary);
		} else if (mInitialSummary == null || mInitialSummary.length() == 0) {
			setSummary(text);
		} else {
			setSummary(mInitialSummary + ": " + text);
		}
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		setSummaryToText(getSharedPreferences().getString(getKey(), ""));
	}

}
