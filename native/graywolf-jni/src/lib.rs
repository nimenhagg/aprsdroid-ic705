use graywolfmodem::demod_afsk_multi::{MultiAfskDemodulator, RECOMMENDED_3DEMOD};
use graywolfmodem::types::{DEFAULT_BAUD, DEFAULT_MARK_FREQ, DEFAULT_SPACE_FREQ};

const SYMBOL_WINDOW_COUNT: u64 = 3;

struct DecoderState {
    demod: MultiAfskDemodulator,
}

impl DecoderState {
    fn new(sample_rate_hz: u32) -> Self {
        let mut demod = MultiAfskDemodulator::new(
            sample_rate_hz,
            DEFAULT_BAUD,
            DEFAULT_MARK_FREQ,
            DEFAULT_SPACE_FREQ,
            0,
            &RECOMMENDED_3DEMOD,
        );

        // Graywolf's default 110-sample dedup window is tuned for 44.1 kHz.
        // Keep the same three-symbol semantic at IC-705's negotiated 12 kHz.
        demod.set_window_samples(dedup_window_samples(sample_rate_hz));

        Self { demod }
    }

    fn process(&mut self, samples: &[i16]) -> Vec<Vec<u8>> {
        for &sample in samples {
            self.demod.process_sample(sample as i32);
        }
        self.demod
            .take_frames()
            .into_iter()
            .map(|frame| frame.data)
            .collect()
    }
}

fn dedup_window_samples(sample_rate_hz: u32) -> u64 {
    let numerator = SYMBOL_WINDOW_COUNT * sample_rate_hz as u64;
    ((numerator + DEFAULT_BAUD as u64 - 1) / DEFAULT_BAUD as u64).max(1)
}

#[cfg(target_os = "android")]
mod android_jni {
    use super::DecoderState;
    use graywolfmodem::types::{MAX_SAMPLES_PER_SEC, MIN_SAMPLES_PER_SEC};
    use jni::objects::{JObject, JShortArray};
    use jni::sys::{jint, jlong, jobjectArray};
    use jni::JNIEnv;
    use std::collections::HashMap;
    use std::panic::{catch_unwind, AssertUnwindSafe};
    use std::ptr;
    use std::sync::atomic::{AtomicI64, Ordering};
    use std::sync::{Mutex, OnceLock};

    static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);
    static DECODERS: OnceLock<Mutex<HashMap<i64, DecoderState>>> = OnceLock::new();

    fn decoders() -> &'static Mutex<HashMap<i64, DecoderState>> {
        DECODERS.get_or_init(|| Mutex::new(HashMap::new()))
    }

    fn throw(env: &mut JNIEnv<'_>, class: &str, message: &str) {
        let _ = env.throw_new(class, message);
    }

    #[no_mangle]
    pub extern "system" fn Java_org_aprsdroid_app_audio_GraywolfNative_nativeCreate(
        mut env: JNIEnv<'_>,
        _this: JObject<'_>,
        sample_rate_hz: jint,
    ) -> jlong {
        if sample_rate_hz < MIN_SAMPLES_PER_SEC as jint
            || sample_rate_hz > MAX_SAMPLES_PER_SEC as jint
        {
            throw(
                &mut env,
                "java/lang/IllegalArgumentException",
                "Graywolf sample rate is outside the supported range",
            );
            return 0;
        }

        let state = match catch_unwind(AssertUnwindSafe(|| DecoderState::new(sample_rate_hz as u32))) {
            Ok(state) => state,
            Err(_) => {
                throw(
                    &mut env,
                    "java/lang/IllegalStateException",
                    "Graywolf decoder initialization panicked",
                );
                return 0;
            }
        };

        let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
        let mut guard = decoders().lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        guard.insert(handle, state);
        handle as jlong
    }

    #[no_mangle]
    pub extern "system" fn Java_org_aprsdroid_app_audio_GraywolfNative_nativeProcess(
        mut env: JNIEnv<'_>,
        _this: JObject<'_>,
        handle: jlong,
        samples: JShortArray<'_>,
        offset: jint,
        length: jint,
    ) -> jobjectArray {
        if handle <= 0 || offset < 0 || length < 0 {
            throw(
                &mut env,
                "java/lang/IllegalArgumentException",
                "Invalid Graywolf decoder handle or PCM range",
            );
            return ptr::null_mut();
        }

        let array_length = match env.get_array_length(&samples) {
            Ok(value) => value,
            Err(_) => return ptr::null_mut(),
        };
        if offset > array_length || length > array_length - offset {
            throw(
                &mut env,
                "java/lang/IndexOutOfBoundsException",
                "PCM range exceeds the supplied ShortArray",
            );
            return ptr::null_mut();
        }

        let mut pcm = vec![0i16; length as usize];
        if env
            .get_short_array_region(&samples, offset, pcm.as_mut_slice())
            .is_err()
        {
            return ptr::null_mut();
        }

        let mut guard = decoders().lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        let frame_result = {
            let Some(state) = guard.get_mut(&(handle as i64)) else {
                drop(guard);
                throw(
                    &mut env,
                    "java/lang/IllegalStateException",
                    "Graywolf decoder handle is closed or unknown",
                );
                return ptr::null_mut();
            };
            catch_unwind(AssertUnwindSafe(|| state.process(&pcm)))
        };

        let frames = match frame_result {
            Ok(frames) => frames,
            Err(_) => {
                guard.remove(&(handle as i64));
                drop(guard);
                throw(
                    &mut env,
                    "java/lang/IllegalStateException",
                    "Graywolf decoder processing panicked; decoder was discarded",
                );
                return ptr::null_mut();
            }
        };
        drop(guard);

        let byte_array_class = match env.find_class("[B") {
            Ok(class) => class,
            Err(_) => return ptr::null_mut(),
        };
        let output = match env.new_object_array(
            frames.len() as jint,
            byte_array_class,
            JObject::null(),
        ) {
            Ok(array) => array,
            Err(_) => return ptr::null_mut(),
        };

        for (index, frame) in frames.iter().enumerate() {
            let byte_array = match env.byte_array_from_slice(frame) {
                Ok(array) => array,
                Err(_) => return ptr::null_mut(),
            };
            if env
                .set_object_array_element(&output, index as jint, byte_array)
                .is_err()
            {
                return ptr::null_mut();
            }
        }

        output.into_raw()
    }

    #[no_mangle]
    pub extern "system" fn Java_org_aprsdroid_app_audio_GraywolfNative_nativeDestroy(
        _env: JNIEnv<'_>,
        _this: JObject<'_>,
        handle: jlong,
    ) {
        if handle <= 0 {
            return;
        }
        let mut guard = decoders().lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        guard.remove(&(handle as i64));
    }
}

#[cfg(test)]
mod tests {
    use super::{dedup_window_samples, DecoderState};
    use std::f64::consts::TAU;

    const SAMPLE_RATE_HZ: u32 = 12_000;
    const BAUD: u32 = 1_200;
    const MARK_HZ: f64 = 1_200.0;
    const SPACE_HZ: f64 = 2_200.0;
    const PCM_AMPLITUDE: f64 = 24_000.0;
    const IC705_SAMPLES_PER_PACKET: usize = 0xf0;

    #[test]
    fn synthetic_12khz_ax25_round_trip() {
        assert_eq!(30, dedup_window_samples(SAMPLE_RATE_HZ));

        let expected = build_ax25_ui_frame(
            "N0CALL",
            "APRS",
            b">GRAYWOLF 12K SYNTHETIC LOOPBACK",
        );
        let pcm = synthesize_bell202(&expected);
        assert!(!pcm.is_empty());

        let mut decoder = DecoderState::new(SAMPLE_RATE_HZ);
        let mut decoded = Vec::new();
        for chunk in pcm.chunks(IC705_SAMPLES_PER_PACKET) {
            decoded.extend(decoder.process(chunk));
        }

        assert_eq!(vec![expected], decoded);
    }

    fn build_ax25_ui_frame(source: &str, destination: &str, payload: &[u8]) -> Vec<u8> {
        let mut frame = Vec::with_capacity(16 + payload.len());
        frame.extend_from_slice(&encode_address(destination, 0, false));
        frame.extend_from_slice(&encode_address(source, 0, true));
        frame.push(0x03); // UI frame
        frame.push(0xf0); // no layer 3
        frame.extend_from_slice(payload);
        frame
    }

    fn encode_address(callsign: &str, ssid: u8, last: bool) -> [u8; 7] {
        assert!(callsign.len() <= 6);
        assert!(ssid <= 15);

        let mut out = [b' ' << 1; 7];
        for (index, byte) in callsign.as_bytes().iter().enumerate() {
            out[index] = byte.to_ascii_uppercase() << 1;
        }
        out[6] = 0x60 | ((ssid & 0x0f) << 1) | u8::from(last);
        out
    }

    fn synthesize_bell202(frame: &[u8]) -> Vec<i16> {
        let mut framed = frame.to_vec();
        let fcs = ax25_fcs(frame);
        framed.extend_from_slice(&fcs.to_le_bytes());

        let mut bits = Vec::new();
        append_flags(&mut bits, 40);
        append_stuffed_lsb_bits(&mut bits, &framed);
        append_flags(&mut bits, 6);

        let samples_per_bit = SAMPLE_RATE_HZ / BAUD;
        assert_eq!(10, samples_per_bit);

        let silence_samples = (SAMPLE_RATE_HZ / 25) as usize; // 40 ms
        let mut pcm = vec![0i16; silence_samples];
        let mut mark = true;
        let mut phase = 0.0f64;

        for bit in bits {
            if bit == 0 {
                mark = !mark;
            }
            let frequency = if mark { MARK_HZ } else { SPACE_HZ };
            let step = TAU * frequency / SAMPLE_RATE_HZ as f64;
            for _ in 0..samples_per_bit {
                pcm.push((phase.sin() * PCM_AMPLITUDE).round() as i16);
                phase += step;
                if phase >= TAU {
                    phase -= TAU;
                }
            }
        }

        pcm.extend(std::iter::repeat_n(0i16, silence_samples));
        pcm
    }

    fn append_flags(bits: &mut Vec<u8>, count: usize) {
        for _ in 0..count {
            append_lsb_byte(bits, 0x7e);
        }
    }

    fn append_lsb_byte(bits: &mut Vec<u8>, byte: u8) {
        for shift in 0..8 {
            bits.push((byte >> shift) & 1);
        }
    }

    fn append_stuffed_lsb_bits(bits: &mut Vec<u8>, bytes: &[u8]) {
        let mut consecutive_ones = 0;
        for &byte in bytes {
            for shift in 0..8 {
                let bit = (byte >> shift) & 1;
                bits.push(bit);
                if bit == 1 {
                    consecutive_ones += 1;
                    if consecutive_ones == 5 {
                        bits.push(0);
                        consecutive_ones = 0;
                    }
                } else {
                    consecutive_ones = 0;
                }
            }
        }
    }

    fn ax25_fcs(bytes: &[u8]) -> u16 {
        let mut crc = 0xffffu16;
        for &byte in bytes {
            let mut value = byte;
            for _ in 0..8 {
                let mix = (crc ^ value as u16) & 1;
                crc >>= 1;
                if mix != 0 {
                    crc ^= 0x8408;
                }
                value >>= 1;
            }
        }
        crc ^ 0xffff
    }
}
