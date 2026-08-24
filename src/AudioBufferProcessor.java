package com.jazzido.PacketDroid;

import java.util.concurrent.LinkedBlockingQueue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AudioBufferProcessor extends Thread {
	static final String LOG_TAG = "APRSdroid.AfskABP";

	private final AudioIn audioIn = new AudioIn();
	private final Context context;
	private final PacketCallback callback;
	
	private boolean inited = false;
	
	private final float[] fbuf = new float[16384];
	private int fbuf_cnt = 0;
	private final int overlap = 18; // overlap for AFSK DEMOD (FREQSAMP / BAUDRATE)
	
	native void init();
	native void processBuffer(float[] buf, int length);
	native void processBuffer2(byte[] buf);
	
	private final LinkedBlockingQueue<short[]> queue;
	
    static {
        System.loadLibrary("multimon");
    }
	
	public AudioBufferProcessor(Context context, PacketCallback cb) {
		super("AudioBufferProcessor");
		this.context = context.getApplicationContext();
		queue = new LinkedBlockingQueue<>();
		callback = cb;
	}
	
	@Override
	public void run() {
		Log.d(LOG_TAG, "thread started");
		if (!inited) { inited = true; init(); } // init native demodulators
		if (!audioIn.isAlive()) audioIn.start();
		
		while (!isInterrupted()) {
			try {
				decode(queue.take());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	public void startRecording() {
		audioIn.startRecording();
	}
	
	public void stopRecording() {
		audioIn.close();
		queue.clear();
	}
	
	void decode(short[] s) {
		short max = 0;
		for (int i = 0; i < s.length; i++) {
			fbuf[fbuf_cnt++] = s[i] * (1.0f / 32768.0f);
			if (max < s[i])
				max = s[i];
			else if (max < -s[i])
				max = (short) -s[i];
		}
		
		callback.peak(max);
		
		if (fbuf_cnt > overlap) {
			processBuffer(fbuf, fbuf_cnt - overlap);
			System.arraycopy(fbuf, fbuf_cnt - overlap, fbuf, 0, overlap);
			fbuf_cnt = overlap;
		}
	}
	
	public class AudioIn extends Thread {
		private AudioRecord recorder;
		private final short[][] buffers = new short[16][8192];

		public AudioIn() {
			super("AudioIn");
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		}

		public void startRecording() {
			if (recorder != null) {
				try {
					recorder.startRecording();
				} catch (IllegalStateException e) {
					Log.w(LOG_TAG, "AudioRecord startRecording error", e);
				}
			}
		}

		@Override
		public void run() {
			int ix = 0;
			if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
					!= PackageManager.PERMISSION_GRANTED) {
				Log.w(LOG_TAG, "Audio recording permission is not granted");
				return;
			}
			try {
				recorder = new AudioRecord(AudioSource.MIC, 22050,
						AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT, 16384);
				recorder.startRecording();

				while (!isInterrupted() && recorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
					short[] buffer = buffers[ix++ % buffers.length];
					int nRead = recorder.read(buffer, 0, buffer.length);
					if (nRead > 0) {
						queue.put(buffer);
					}
				}
			} catch (Throwable x) {
				Log.w(LOG_TAG, "Error reading audio", x);
			} 
		}

		public void close() {
			if (recorder != null) {
				try {
					recorder.stop();
					recorder.release();
				} catch (Exception e) {
					Log.w(LOG_TAG, "Error closing AudioRecord", e);
				}
				recorder = null;
			}
			interrupt();
		}
	}

	public void callback(byte[] data) {
		callback.received(data);
	}
}
