package de.duenndns

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.EditTextPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.aprsdroid.app.R
import java.util.Locale

open class EditTextPreferenceWithValue @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle,
    defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var initialSummary: CharSequence? = null
    private var isInitialSummarySet = false

    init {
        initialSummary = summary
        isInitialSummarySet = true
        summaryProvider = SummaryProvider<EditTextPreferenceWithValue> { pref ->
            pref.buildSummary(pref.text)
        }
    }

    open fun isPassword(): Boolean {
        val k = key ?: return false
        val norm = k.lowercase(Locale.ROOT)
        return norm.contains("password") || norm.contains("passcode")
    }

    override fun onClick() {
        showMaterialDialog()
    }

    private fun showMaterialDialog() {
        val ctx = context
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_m3_edittext, null, false)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.m3_text_input_layout)
        val inputEdit = view.findViewById<TextInputEditText>(R.id.m3_text_input_edit)

        val dialogTitle = dialogTitle ?: title ?: ""
        val dialogMessage = dialogMessage

        if (!dialogMessage.isNullOrEmpty()) {
            inputLayout.helperText = dialogMessage
        }

        inputEdit.setText(text)
        if (isPassword()) {
            inputEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            val k = key?.lowercase(Locale.ROOT) ?: ""
            if (k.contains("call") || k.contains("dest") || k.contains("origin")) {
                inputEdit.filters = arrayOf(InputFilter.AllCaps())
            }
        }

        inputEdit.setSelection(inputEdit.text?.length ?: 0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(dialogTitle)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = inputEdit.text?.toString() ?: ""
                if (callChangeListener(newValue)) {
                    text = newValue
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildSummary(t: String?): CharSequence? {
        if (!isInitialSummarySet) {
            initialSummary = summary
            isInitialSummarySet = true
        }
        if (t.isNullOrEmpty()) {
            return initialSummary
        }
        val display = if (isPassword()) "•".repeat(t.length) else t
        return if (initialSummary.isNullOrEmpty()) display else "$initialSummary: $display"
    }
}
