# Structured diagnostics

APRSdroid now keeps a small rotating JSONL event history under `noBackupFilesDir` in addition to Android logcat. The files survive process restarts and are bundled with a human-readable report when the user chooses **Share diagnostic logs**.

The persistent event log is intended for lifecycle, crash, Android network and IC-705 session events. Sensitive fields such as passwords, passcodes, secrets, tokens and precise latitude/longitude values are automatically redacted.
