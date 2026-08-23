package de.duenndns;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;

public class ListPreferenceWithValue extends ListPreference {
	private CharSequence mInitialSummary = null;
	private boolean mInitialSummaryInitialized = false;

	public ListPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		initSummary();
	}

	public ListPreferenceWithValue(Context context) {
		super(context);
		initSummary();
	}

	private void initSummary() {
		if (!mInitialSummaryInitialized) {
			mInitialSummary = getSummary();
			mInitialSummaryInitialized = true;
		}
	}

	private void setSummaryToText(CharSequence text) {
		initSummary();
		if (text == null || text.length() == 0) {
			setSummary(mInitialSummary);
		} else {
			setSummary(text);
		}
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		setSummaryToText(getEntry());
	}

	@Override
	public void setValue(String text) {
		super.setValue(text);
		setSummaryToText(getEntry());
	}

}
