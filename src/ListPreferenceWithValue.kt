package de.duenndns

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class ListPreferenceWithValue @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0
) : ListPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        summaryProvider = SimpleSummaryProvider.getInstance()
    }

    override fun onClick() {
        showMaterialDialog()
    }

    private fun showMaterialDialog() {
        val ctx = context
        val dialogTitle = dialogTitle ?: title ?: ""
        val entryList = entries ?: return
        val entryValueList = entryValues ?: return
        val currentIndex = findIndexOfValue(value)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(dialogTitle)
            .setSingleChoiceItems(entryList, currentIndex) { dialog, which ->
                if (which in entryValueList.indices) {
                    val newValue = entryValueList[which].toString()
                    if (callChangeListener(newValue)) {
                        value = newValue
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
