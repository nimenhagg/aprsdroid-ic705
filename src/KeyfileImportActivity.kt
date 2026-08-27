package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import org.aprsdroid.app.ui.theme.AprsTheme
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate

class KeyfileImportActivity : ComponentActivity() {
    companion object {
        const val TAG = "APRSdroid.KeyImport"
        val KEYSTORE_PASS = "APRS".toCharArray()
        const val KEYSTORE_DIR = "keystore"
        val CALL_RE = Regex(".*CALLSIGN=([0-9A-Za-z]+).*")
    }

    val db: StorageDatabase by lazy { StorageDatabase.open(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "created: $intent")
        showPasswordDialog()
    }

    private fun showPasswordDialog() {
        val dialog = object : ComponentDialog(this) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val composeView = ComposeView(this@KeyfileImportActivity).apply {
                    setContent {
                        AprsTheme {
                            var password by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = {
                                    dismiss()
                                    finish()
                                },
                                shape = RoundedCornerShape(28.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.ssl_import_activity),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    }
                                },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ssl_import_password),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    dismiss()
                                                    importKey(password)
                                                }
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            dismiss()
                                            importKey(password)
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(stringResource(android.R.string.ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            dismiss()
                                            finish()
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
                setContentView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        dialog.setOnCancelListener { finish() }
        dialog.show()
    }

    private fun importKey(password: String) {
        try {
            val dataUri = intent.data ?: throw IllegalArgumentException("Missing data URI")
            val ks = KeyStore.getInstance("PKCS12")
            contentResolver.openInputStream(dataUri)?.use { input ->
                ks.load(input, password.toCharArray())
            } ?: throw IllegalArgumentException("Cannot open stream for $dataUri")

            var callsign: String? = null
            for (alias in ks.aliases()) {
                if (ks.isKeyEntry(alias)) {
                    val c = ks.getCertificate(alias) as X509Certificate
                    c.checkValidity()
                    val dn = c.subjectX500Principal.toString().replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
                    Log.d(TAG, "Loaded key: $dn")
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

                PrefsWrapper.defaultSharedPreferences(this)
                    .edit { putString("callsign", callsign) }

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
