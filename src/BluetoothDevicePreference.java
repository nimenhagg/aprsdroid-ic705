package de.duenndns;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;

import java.util.Set;

public class BluetoothDevicePreference extends ListPreference {

    public BluetoothDevicePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BluetoothDevicePreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onClick() {
        // Refresh the paired-device list before showing the dialog.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            setEntries(new CharSequence[0]);
            setEntryValues(new CharSequence[0]);
            super.onClick();
            return;
        }

        BluetoothManager manager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = (manager != null) ? manager.getAdapter() : null;
        Set<BluetoothDevice> pairedDevices = (adapter != null) ? adapter.getBondedDevices() : null;
        if (pairedDevices != null) {
            CharSequence[] entries = new CharSequence[pairedDevices.size()];
            CharSequence[] entryValues = new CharSequence[pairedDevices.size()];
            int i = 0;
            for (BluetoothDevice device : pairedDevices) {
                if (device.getAddress() != null) {
                    entries[i] = device.getName();
                    if (entries[i] == null) {
                        entries[i] = "(null)";
                    }
                    entryValues[i] = device.getAddress();
                    i++;
                }
            }
            setEntries(entries);
            setEntryValues(entryValues);
        }
        super.onClick();
    }

}
