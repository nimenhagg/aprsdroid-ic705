package org.aprsdroid.app.radio

import com.felhr.usbserial.UsbSerialDevice
import java.io.InputStream
import java.io.OutputStream

/**
 * Adapts a [UsbSerialDevice] stream into a [RadioByteStream] for the local CAT bridge.
 */
class UsbSerialByteStream(
    private val usbSerialDevice: UsbSerialDevice,
    override val inputStream: InputStream,
    override val outputStream: OutputStream,
) : RadioByteStream {
    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { usbSerialDevice.close() }
    }
}
