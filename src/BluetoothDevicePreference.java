	package de.duenndns;

	import android.bluetooth.*;
	import android.content.Context;
	import android.util.AttributeSet;

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
			// refresh Bluetooth device list before showing dialog
			BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
			Set<BluetoothDevice> pairedDevices = (bta != null) ? bta.getBondedDevices() : null;
			if (pairedDevices != null) {
				CharSequence[] entries = new CharSequence[pairedDevices.size()];
				CharSequence[] entryValues = new CharSequence[pairedDevices.size()];
				int i = 0;
				for (BluetoothDevice dev : pairedDevices) {
					if (dev.getAddress() != null) {
						entries[i] = dev.getName();
						if (entries[i] == null)
							entries[i] = "(null)";
						entryValues[i] = dev.getAddress();
						i++;
					}
				}
				setEntries(entries);
				setEntryValues(entryValues);
			}
			super.onClick();
		}

	}
