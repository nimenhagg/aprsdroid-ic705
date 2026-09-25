package org.aprsdroid.app.hamlib

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Lifecycle and error handling of the real JNI boundary.
 *
 * These tests need a native build of Hamlib plus `libaprs_hamlib.so`. CI builds
 * both for the host and points at them with `APRSDROID_HAMLIB_NATIVE`; when the
 * library is missing the tests are skipped instead of failing, so a plain
 * `./gradlew test` on a machine without Hamlib still works.
 *
 * All rig operations use Hamlib's built-in dummy backend, so nothing here
 * touches USB, a serial port or an antenna.
 */
class HamlibNativeLifecycleTest {

    /**
     * Skips when no native build is present, but fails when the environment
     * demands one ([REQUIRE_NATIVE_ENV]) so a broken native build can never be
     * mistaken for a green run.
     */
    @Before
    fun requireNativeLibrary() {
        val available = HamlibLibrary.isAvailable()
        if (!available && System.getenv(REQUIRE_NATIVE_ENV) == "1") {
            fail(
                "Hamlib native library is required by $REQUIRE_NATIVE_ENV=1 but " +
                    "could not be loaded from ${HamlibLibrary.describeSource()}",
            )
        }
        assumeTrue(
            "Hamlib native library not available (${HamlibLibrary.describeSource()})",
            available,
        )
    }

    @Test
    fun reportsHamlibVersionAndPinnedBackendRevision() {
        val version = HamlibRigCatalog.version()
        assertTrue("unexpected version string: $version", version.contains("Hamlib"))

        val revision = HamlibRigCatalog.backendRevision()
        assertTrue("unexpected revision string: $revision", revision.contains("4.7.2@"))
    }

    @Test
    fun enumeratesBackendsIncludingTheDummyRig() {
        val raw = HamlibNative.listRigs()
        val rigs = HamlibRigCatalog.list()
        println("Hamlib registered ${raw.size} rig entries, ${rigs.size} decoded")

        assertTrue("Hamlib registered no rig entries", raw.isNotEmpty())
        // Every enumerated line must decode: this is the JNI/Kotlin encoding
        // contract, independent of how many backends a build registers.
        assertEquals("the catalog dropped entries it should decode", raw.size, rigs.size)
        assertEquals(raw.size, HamlibRigCatalog.count())

        val dummy = HamlibRigCatalog.findByModelId(HamlibRigCatalog.DUMMY_MODEL_ID)
        assertTrue("dummy backend missing from the catalog", dummy != null)
        assertTrue(dummy!!.capabilities.canControlFrequency)
        assertTrue(dummy.capabilities.canControlPtt)

        assertTrue("Hamlib catalog should enumerate full backend catalog, got ${raw.size}", raw.size >= 100)
        val ic705 = HamlibRigCatalog.findByModelId(3085)
        assertTrue("IC-705 (model 3085) must be present in Hamlib catalog", ic705 != null)
        assertEquals("Icom", ic705?.manufacturer)
        assertTrue(ic705?.model?.contains("705") == true)

        val ft891 = HamlibRigCatalog.findByModelId(1036)
        assertTrue("FT-891 (model 1036) must be present in Hamlib catalog", ft891 != null)
        assertEquals("Yaesu", ft891?.manufacturer)
        assertTrue(ft891?.model?.contains("891") == true)
    }

    @Test
    fun openSetAndReadBackFrequency() {
        HamlibHandle.createDummy().use { handle ->
            handle.open()
            assertTrue(handle.isOpen)

            handle.setFrequencyHz(14_074_000.0)
            assertEquals(14_074_000.0, handle.frequencyHz(), 1.0)
        }
    }

    @Test
    fun setAndReadBackMode() {
        // Use a mode the dummy backend actually advertises instead of assuming
        // it accepts a packet mode.
        val advertised = HamlibRigCatalog
            .findByModelId(HamlibRigCatalog.DUMMY_MODEL_ID)
            ?.capabilities
            ?.supportedModesMask ?: 0L
        val mode = if (advertised == 0L) HamlibModes.PKTUSB else advertised and -advertised

        HamlibHandle.createDummy().use { handle ->
            handle.open()
            handle.setMode(mode)
            assertEquals(mode, handle.mode().mode)
        }
    }

    @Test
    fun pttRoundTrip() {
        HamlibHandle.createDummy().use { handle ->
            handle.open()
            handle.setPtt(true)
            assertTrue(handle.isPttOn())
            handle.setPtt(false)
            assertFalse(handle.isPttOn())
        }
    }

    @Test
    fun portCanBeClosedAndReopenedOnTheSameHandle() {
        HamlibHandle.createDummy().use { handle ->
            handle.open()
            handle.closePort()
            assertFalse(handle.isOpen)
            handle.open()
            assertTrue(handle.isOpen)
        }
    }

    @Test
    fun useAfterCloseIsRejected() {
        val handle = HamlibHandle.createDummy()
        handle.open()
        handle.close()
        assertTrue(handle.isClosed)

        val failure = try {
            handle.frequencyHz()
            null
        } catch (thrown: HamlibClosedException) {
            thrown
        }
        assertTrue("HamlibClosedException expected", failure != null)
    }

    @Test
    fun closeIsIdempotent() {
        val handle = HamlibHandle.createDummy()
        handle.open()
        handle.close()
        handle.close()
        assertTrue(handle.isClosed)
    }

    @Test
    fun nativeLayerRejectsDestroyingTheSameHandleTwice() {
        val handle = HamlibHandle.createDummy()
        val id = nativeIdOf(handle)
        handle.close()

        assertEquals(HamlibError.UNKNOWN_HANDLE, HamlibNative.destroy(id))
        assertEquals(HamlibError.UNKNOWN_HANDLE, HamlibNative.closeRig(id))
    }

    @Test
    fun veryLargeModelIdIsRejectedWithoutLeakingAHandle() {
        val failure = try {
            HamlibHandle.create(999_999).close()
            null
        } catch (thrown: HamlibException) {
            thrown
        }
        assertTrue("HamlibException expected for an unknown model", failure != null)

        // The registry must still accept new handles after the rejected create.
        HamlibHandle.createDummy().use { handle -> handle.open() }
    }

    @Test
    fun registrySurvivesManyCreateDestroyCycles() {
        // The native registry holds 16 handles; 40 cycles would fail if either
        // side leaked a slot.
        repeat(40) { index ->
            HamlibHandle.createDummy().use { handle ->
                handle.open()
                handle.setFrequencyHz(144_640_000.0 + index)
                assertEquals(144_640_000.0 + index, handle.frequencyHz(), 1.0)
            }
        }
    }

    @Test
    fun concurrentUseOfOneHandleIsSerialized() {
        HamlibHandle.createDummy().use { handle ->
            handle.open()
            val threads = 4
            val iterations = 25
            val pool = Executors.newFixedThreadPool(threads)
            val failures = AtomicInteger(0)
            val start = CountDownLatch(1)
            try {
                val tasks = (0 until threads).map { slot ->
                    pool.submit {
                        start.await()
                        repeat(iterations) { step ->
                            val hz = 7_000_000.0 + slot * 1_000 + step
                            try {
                                handle.setFrequencyHz(hz)
                                handle.frequencyHz()
                                handle.mode()
                            } catch (_: Throwable) {
                                failures.incrementAndGet()
                            }
                        }
                    }
                }
                start.countDown()
                tasks.forEach { it.get(60, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }
            assertEquals("concurrent access failed", 0, failures.get())
            assertTrue(handle.isOpen)
        }
    }

    @Test
    fun concurrentCloseIsRejectedOnEitherLayerButNeverCrashes() {
        val handle = HamlibHandle.createDummy()
        handle.open()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val unexpected = AtomicInteger(0)
        try {
            val reader = pool.submit {
                start.await()
                repeat(200) {
                    try {
                        handle.frequencyHz()
                    } catch (_: HamlibClosedException) {
                        // Expected once the writer wins the race.
                    } catch (_: HamlibException) {
                        // Native-side rejection of a retired handle.
                    } catch (_: Throwable) {
                        unexpected.incrementAndGet()
                    }
                }
            }
            val closer = pool.submit {
                start.await()
                handle.close()
            }
            start.countDown()
            reader.get(60, TimeUnit.SECONDS)
            closer.get(60, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
        assertEquals("unexpected failure while closing under load", 0, unexpected.get())
        assertTrue(handle.isClosed)
    }

    @Test
    fun modeNamesComeFromHamlib() {
        assertTrue(HamlibModes.name(HamlibModes.PKTUSB).isNotBlank())
    }

    /**
     * Reads the private native id through reflection so the test can exercise
     * the native layer directly; the public API intentionally hides it.
     */
    private fun nativeIdOf(handle: HamlibHandle): Long {
        val field = HamlibHandle::class.java.getDeclaredField("nativeId")
        field.isAccessible = true
        return field.get(handle) as Long
    }

    private companion object {
        const val REQUIRE_NATIVE_ENV = "APRSDROID_HAMLIB_REQUIRE_NATIVE"
    }
}
