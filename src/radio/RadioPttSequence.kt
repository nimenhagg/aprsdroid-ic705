package org.aprsdroid.app.radio

/**
 * Coordinates the generic PTT/audio ordering without owning an audio device.
 *
 * The audio callback must write and drain the already-prepared PCM data. This
 * class deliberately requires PTT readback before audio is allowed and never
 * changes frequency or mode.
 */
class RadioPttSequence(
    private val radio: RadioControl,
) {
    sealed interface Result {
        data object PttOnNotConfirmed : Result
        data object AudioFailed : Result
        data object PttOffNotConfirmed : Result
        data object Completed : Result
    }

    fun transmit(writeAndDrainAudio: () -> Unit): Result {
        radio.setPtt(true)
        val result = if (!radio.capabilities.canGetPtt || !radio.isPttOn()) {
            Result.PttOnNotConfirmed
        } else {
            try {
                writeAndDrainAudio()
                Result.Completed
            } catch (_: Exception) {
                Result.AudioFailed
            }
        }

        // Always request PTT OFF, including when ON readback or audio fails.
        radio.setPtt(false)
        return if (radio.capabilities.canGetPtt && radio.isPttOn()) {
            Result.PttOffNotConfirmed
        } else {
            result
        }
    }
}
