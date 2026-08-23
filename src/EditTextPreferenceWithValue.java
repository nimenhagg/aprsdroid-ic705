package de.duenndns;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

public class EditTextPreferenceWithValue extends EditTextPreference {
	private CharSequence mInitialSummary = null;
	private boolean mInitialSummaryInitialized = false;

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public EditTextPreferenceWithValue(Context context) {
		super(context);
		init();
	}

	private void init() {
		mInitialSummary = getSummary();
		mInitialSummaryInitialized = true;

		setOnBindEditTextListener(editText -> {
			if ((editText.getInputType() & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
				InputFilter[] filters = editText.getFilters();
				InputFilter[] newFilters = new InputFilter[filters.length + 1];
				System.arraycopy(filters, 0, newFilters, 0, filters.length);
				newFilters[filters.length] = new InputFilter.AllCaps();
				editText.setFilters(newFilters);
			}
		});

		setSummaryProvider(pref -> {
			EditTextPreferenceWithValue etp = (EditTextPreferenceWithValue) pref;
			return etp.buildSummary(etp.getText());
		});
	}

	private CharSequence buildSummary(String text) {
		if (!mInitialSummaryInitialized) {
			mInitialSummary = getSummary();
			mInitialSummaryInitialized = true;
		}
		if (text == null || text.length() == 0) {
			return mInitialSummary;
		}
		String display = text;
		if (mInitialSummary == null || mInitialSummary.length() == 0) {
			return display;
		}
		return mInitialSummary + ": " + display;
	}
}
