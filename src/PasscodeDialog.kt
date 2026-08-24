package org.aprsdroid.app

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.content.edit
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

class PasscodeDialog(
    private val act: Activity,
    private val firstrun: Boolean
) : AlertDialog(act),
    DialogInterface.OnClickListener,
    DialogInterface.OnCancelListener,
    TextWatcher,
    View.OnFocusChangeListener {

    val prefs: PrefsWrapper by lazy { PrefsWrapper(act) }

    private val fr_view: View = LayoutInflater.from(act).inflate(
        R.layout.firstrunview,
        act.findViewById<ViewGroup>(android.R.id.content),
        false,
    )
    private val inputCall: EditText = fr_view.findViewById(R.id.callsign)
    private val inputPass: EditText = fr_view.findViewById(R.id.passcode)
    private val okButton: Button? get() = getButton(DialogInterface.BUTTON_POSITIVE)
    private var movedAwayFromCallsign = false

    init {
        setTitle(act.getString(if (firstrun) R.string.fr_title else R.string.p_passcode_entry))
        if (!firstrun) {
            fr_view.findViewById<View>(R.id.fr_text).visibility = View.GONE
            fr_view.findViewById<View>(R.id.fr_text2).visibility = View.GONE
        }
        setView(fr_view)

        inputCall.setText(prefs.getCallsign())
        inputCall.addTextChangedListener(this)
        inputCall.filters = arrayOf(InputFilter.AllCaps())
        inputCall.onFocusChangeListener = this

        inputPass.setText(prefs.getString("passcode", ""))
        inputPass.addTextChangedListener(this)

        setButton(DialogInterface.BUTTON_POSITIVE, act.getString(android.R.string.ok), this)
        setButton(DialogInterface.BUTTON_NEUTRAL, act.getString(R.string.p_passreq), this)
        setOnCancelListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (inputCall.text.toString().isEmpty()) {
            okButton?.isEnabled = false
        }
        if (!firstrun) {
            inputPass.requestFocus()
        }
    }

    override fun onClick(d: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> saveFirstRun(true)
            DialogInterface.BUTTON_NEUTRAL -> {
                saveFirstRun(false)
                act.startActivity(Intent(Intent.ACTION_VIEW, act.getString(R.string.passcode_url).toUri()))
            }
            else -> cancelled()
        }
    }

    override fun onCancel(d: DialogInterface?) {
        cancelled()
    }

    override fun afterTextChanged(s: Editable?) {
        verifyInput()
    }
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        if (!hasFocus) {
            movedAwayFromCallsign = true
            verifyInput()
        }
    }

    fun passOK(call: String, pass: String): Boolean {
        return if (pass.isNotEmpty()) AprsPacket.passcodeAllowed(call, pass, true) else true
    }

    fun verifyInput() {
        val call = inputCall.text.toString()
        val pass = inputPass.text.toString()
        val callError = if (call.isNotEmpty() || !movedAwayFromCallsign) null else act.getString(R.string.p_callsign_entry)
        val passError = if (passOK(call, pass)) null else act.getString(R.string.wrongpasscode)
        inputCall.error = callError
        inputPass.error = passError
        okButton?.isEnabled = call.isNotEmpty() && callError == null && passError == null
    }

    fun saveFirstRun(completed: Boolean) {
        val call = inputCall.text.toString()
        val passcode = inputPass.text.toString()
        val parts = call.split("-")
        if (parts.size !in 1..2) {
            Log.d("PasscodeDialog", "could not split callsign")
            act.finish()
            return
        }
        prefs.prefs.edit {
            putString("callsign", parts[0])
            if (parts.size == 2) {
                putString("ssid", parts[1])
            }
            if (passOK(call, passcode)) {
                putString("passcode", passcode)
            }
            putBoolean("firstrun", !completed)
        }
    }

    fun cancelled() {
        if (firstrun) {
            Log.d("PasscodeDialog", "closing parent activity")
            act.finish()
        }
    }
}
