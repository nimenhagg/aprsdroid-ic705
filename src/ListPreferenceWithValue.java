package de.duenndns;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

public class ListPreferenceWithValue extends ListPreference {

	public ListPreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public ListPreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public ListPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public ListPreferenceWithValue(Context context) {
		super(context);
		init();
	}

	private void init() {
		setSummaryProvider(SimpleSummaryProvider.getInstance());
	}
}
