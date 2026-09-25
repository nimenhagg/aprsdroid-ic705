package org.aprsdroid.app.radio

import com.felhr.usbserial.SerialInputStream
import com.felhr.usbserial.SerialOutputStream
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

    constructor(usbSerialDevice: UsbSerialDevice) : this(
        usbSerialDevice = usbSerialDevice,
        inputStream = SerialInputStream(usbSerialDevice),
        outputStream = SerialOutputStream(usbSerialDevice),
    )

    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { usbSerialDevice.close() }
    }
}
