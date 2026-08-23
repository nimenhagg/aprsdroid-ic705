package de.duenndns;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

public class EditTextPreferenceWithValue extends EditTextPreference {
	private CharSequence mInitialSummary = null;
	private boolean mInitialSummaryInitialized = false;

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
		initSummary();
	}

	public EditTextPreferenceWithValue(Context context) {
		super(context);
		initSummary();
	}

	private void initSummary() {
		if (!mInitialSummaryInitialized) {
			mInitialSummary = getSummary();
			mInitialSummaryInitialized = true;
		}
	}

	private void fixupCaps() {
		EditText et = getEditText();
		if ((et.getInputType() & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
			// append AllCaps filter
			InputFilter[] filters = et.getFilters();
			InputFilter[] newFilters = new InputFilter[filters.length + 1];
			System.arraycopy(filters, 0, newFilters, 0, filters.length);
			newFilters[filters.length] = new InputFilter.AllCaps();
			et.setFilters(newFilters);
		}
	}
	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		fixupCaps();
	}

	private void setSummaryToText(String text) {
		initSummary();
		if (text == null || text.length() == 0) {
			setSummary(mInitialSummary);
		} else {
			String display = text;
			if ((getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
			    (getEditText().getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0 ||
			    getEditText().getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod) {
				display = "••••••••";
			}
			if (mInitialSummary == null || mInitialSummary.length() == 0) {
				setSummary(display);
			} else {
				setSummary(mInitialSummary + ": " + display);
			}
		}
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		setSummaryToText(getText());
	}

	@Override
	public void setText(String text) {
		super.setText(text);
		setSummaryToText(text);
	}

	@Override
	protected void showDialog(android.os.Bundle state) {
		try {
			super.showDialog(state);
		} catch (Throwable t) {
			android.util.Log.w("EditTextPreference", "Safely caught showDialog exception", t);
			try {
				android.app.Dialog dialog = getDialog();
				if (dialog != null && !dialog.isShowing()) {
					dialog.show();
				}
			} catch (Throwable ignored) {}
		}
	}

}
