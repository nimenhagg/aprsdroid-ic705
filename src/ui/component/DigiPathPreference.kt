package org.aprsdroid.app.ui.component

import android.content.Context
import android.content.SharedPreferences
import android.text.InputFilter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.aprsdroid.app.R

class DigiPathPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private val defaultPresets = listOf(
        "WIDE1-1",
        "WIDE1-1,WIDE2-1",
        "WIDE2-1",
        "WIDE2-2",
        "ARISS",
        ""
    )

    private val customPresetsKey = "digi_path_user_presets"

    init {
        setOnPreferenceClickListener {
            showDialog()
            true
        }
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary(sharedPreferences?.getString(key, "WIDE1-1") ?: "WIDE1-1")
    }

    private fun updateSummary(path: String) {
        val display = if (path.isEmpty()) context.getString(R.string.digi_path_direct) else path
        summary = context.getString(
            R.string.digi_path_summary_format,
            context.getString(R.string.p_aprs_path_summary),
            display,
        )
    }

    private fun getUserPresets(): MutableSet<String> {
        val sp = sharedPreferences ?: return mutableSetOf()
        return HashSet(sp.getStringSet(customPresetsKey, emptySet()) ?: emptySet())
    }

    private fun saveUserPresets(presets: Set<String>) {
        sharedPreferences?.edit { putStringSet(customPresetsKey, presets) }
    }

    private fun showDialog() {
        val currentPath = sharedPreferences?.getString(key, "WIDE1-1") ?: "WIDE1-1"
        val customPresets = getUserPresets()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_digi_path, null, false)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.preset_chip_group)
        val customInput = dialogView.findViewById<EditText>(R.id.custom_path_input)
        val btnSavePreset = dialogView.findViewById<Button>(R.id.btn_save_preset)

        customInput.setText(currentPath)
        customInput.filters = arrayOf(
            InputFilter.AllCaps(),
            InputFilter { source, start, end, _, _, _ ->
                for (i in start until end) {
                    val c = source[i]
                    if (!c.isLetterOrDigit() && c != '-' && c != ',') {
                        return@InputFilter ""
                    }
                }
                null
            }
        )

        fun populateChips() {
            chipGroup.removeAllViews()

            // Built-in presets
            defaultPresets.forEach { preset ->
                val chip = Chip(context).apply {
                    text = if (preset.isEmpty()) context.getString(R.string.digi_path_direct) else preset
                    isCheckable = false
                    setOnClickListener {
                        customInput.setText(preset)
                    }
                }
                chipGroup.addView(chip)
            }

            // User custom presets
            customPresets.forEach { preset ->
                val chip = Chip(context).apply {
                    text = context.getString(R.string.digi_path_custom_chip, preset)
                    isCheckable = false
                    setOnClickListener {
                        customInput.setText(preset)
                    }
                    setOnLongClickListener {
                        customPresets.remove(preset)
                        saveUserPresets(customPresets)
                        populateChips()
                        Toast.makeText(
                            context,
                            context.getString(R.string.digi_path_preset_deleted, preset),
                            Toast.LENGTH_SHORT,
                        ).show()
                        true
                    }
                }
                chipGroup.addView(chip)
            }
        }

        populateChips()

        btnSavePreset.setOnClickListener {
            val text = customInput.text.toString().trim().uppercase()
            if (text.isNotEmpty() && !defaultPresets.contains(text)) {
                customPresets.add(text)
                saveUserPresets(customPresets)
                populateChips()
                Toast.makeText(
                    context,
                    context.getString(R.string.digi_path_preset_saved, text),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.p_aprs_path)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = customInput.text.toString().trim().uppercase()
                sharedPreferences?.edit { putString(key, chosen) }
                updateSummary(chosen)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
