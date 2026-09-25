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
        if (!radio.capabilities.canGetPtt || !radio.isPttOn()) {
            return Result.PttOnNotConfirmed
        }

        return try {
            writeAndDrainAudio()
            Result.Completed
        } catch (_: Throwable) {
            Result.AudioFailed
        } finally {
            radio.setPtt(false)
        }.let { result ->
            if (radio.capabilities.canGetPtt && radio.isPttOn()) {
                Result.PttOffNotConfirmed
            } else {
                result
            }
        }
    }
}
