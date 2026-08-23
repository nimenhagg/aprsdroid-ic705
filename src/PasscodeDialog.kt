package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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

    private val fr_view: View = LayoutInflater.from(act).inflate(R.layout.firstrunview, null, false)
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
                act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(act.getString(R.string.passcode_url))))
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
        val pe = prefs.prefs.edit()
        val parts = call.split("-")
        when (parts.size) {
            1 -> pe.putString("callsign", parts[0])
            2 -> {
                pe.putString("callsign", parts[0])
                pe.putString("ssid", parts[1])
            }
            else -> {
                Log.d("PasscodeDialog", "could not split callsign")
                act.finish()
                return
            }
        }
        if (passOK(call, passcode)) {
            pe.putString("passcode", passcode)
        }
        pe.putBoolean("firstrun", !completed)
        pe.apply()
    }

    fun cancelled() {
        if (firstrun) {
            Log.d("PasscodeDialog", "closing parent activity")
            act.finish()
        }
    }
}
