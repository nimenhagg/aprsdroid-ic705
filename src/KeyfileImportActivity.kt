package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate

class KeyfileImportActivity : Activity() {
    val TAG = "APRSdroid.KeyImport"
    val KEYSTORE_PASS = "APRS".toCharArray()
    val KEYSTORE_DIR = "keystore"
    val CALL_RE = Regex(".*CALLSIGN=([0-9A-Za-z]+).*")

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "created: " + intent)
        query_for_password()
    }

    fun query_for_password() {
        val pwd = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val listener = DialogInterface.OnClickListener { _, which ->
            if (which == DialogInterface.BUTTON_POSITIVE) {
                import_key(pwd.text.toString())
            } else {
                finish()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ssl_import_activity)
            .setMessage(R.string.ssl_import_password)
            .setView(pwd)
            .setPositiveButton(android.R.string.ok, listener)
            .setNegativeButton(android.R.string.cancel, listener)
            .setOnCancelListener { finish() }
            .show()
    }

    fun import_key(password: String) {
        try {
            val dataUri = intent.data ?: throw IllegalArgumentException("Missing data URI")
            val ks = KeyStore.getInstance("PKCS12")
            val isStream = contentResolver.openInputStream(dataUri)
                ?: throw IllegalArgumentException("Cannot open stream for " + dataUri)
            ks.load(isStream, password.toCharArray())

            var callsign: String? = null
            for (alias in ks.aliases()) {
                if (ks.isKeyEntry(alias)) {
                    val c = ks.getCertificate(alias) as X509Certificate
                    c.checkValidity()
                    val dn = c.subjectX500Principal.toString().replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
                    Log.d(TAG, "Loaded key: " + dn)
                    val match = CALL_RE.find(dn)
                    if (match != null) {
                        callsign = match.groupValues[1]
                    }
                }
            }

            if (callsign != null) {
                val dir = applicationContext.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE)
                val keyStoreFile = File(dir.toString() + File.separator + callsign + ".p12")
                val fos = FileOutputStream(keyStoreFile)
                ks.store(fos, KEYSTORE_PASS)
                fos.close()

                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putString("callsign", callsign).apply()

                val msg = getString(R.string.ssl_import_ok, callsign)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                db.addPost(System.currentTimeMillis(), StorageDatabase.Companion.Post.TYPE_INFO, getString(R.string.post_info), msg)
                startActivity(Intent(this, LogActivity::class.java))
            }
        } catch (e: Exception) {
            val errmsg = getString(R.string.ssl_import_error, e.message)
            Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
            db.addPost(System.currentTimeMillis(), StorageDatabase.Companion.Post.TYPE_ERROR, getString(R.string.post_error), errmsg)
            e.printStackTrace()
        }
        finish()
    }
}
