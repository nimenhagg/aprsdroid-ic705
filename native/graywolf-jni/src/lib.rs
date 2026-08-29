use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

use graywolfmodem::demod_afsk_multi::{MultiAfskDemodulator, RECOMMENDED_3DEMOD};
use graywolfmodem::types::{
    DEFAULT_BAUD, DEFAULT_MARK_FREQ, DEFAULT_SPACE_FREQ, MAX_SAMPLES_PER_SEC,
    MIN_SAMPLES_PER_SEC,
};
use jni::objects::{JObject, JShortArray};
use jni::sys::{jint, jlong, jobjectArray};
use jni::JNIEnv;

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
        let numerator = SYMBOL_WINDOW_COUNT * sample_rate_hz as u64;
        let window_samples = (numerator + DEFAULT_BAUD as u64 - 1) / DEFAULT_BAUD as u64;
        demod.set_window_samples(window_samples.max(1));

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
