package com.activetheoryinc.playback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

public class PlaybackEngine implements SurfaceTexture.OnFrameAvailableListener{
	private static final String TAG = "BGPlaybackEngine";
	
	private static String mVideoURI = null;
	private PlayerThread mPlayer = null;
	private Surface mSurface = null;
	private SurfaceTexture mSurfaceTexture = null;
	private int mSurfaceTextureId = 0;
	private PlaybackStatus mPlaybackStatus;
	
	public PlaybackEngine (){ 
		mPlaybackStatus = new PlaybackStatus();	
	}
	
	public PlayerThread GetOrCreatePlayer(){
		if(mPlayer == null)
			mPlayer = new PlayerThread(mSurface);
		return mPlayer;
	}
	
	private void SetupGLIfNecessary(){
		if(mSurfaceTextureId > 0) return;
		
		checkGlError("precheck before binding texture");
		int[] textures = new int[1];
		// generate one texture pointer and bind it as an external texture.
		GLES20.glGenTextures(1, textures, 0);
		mSurfaceTextureId = textures[0];
		Log.i(TAG, "Generated texture for video playback with id: " + mSurfaceTextureId + " on thread: " + Thread.currentThread().getName());
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mSurfaceTextureId);
		
		checkGlError("glBindTexture mTextureID");

		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		mSurfaceTexture = new SurfaceTexture(mSurfaceTextureId);
		mSurfaceTexture.setOnFrameAvailableListener(this);

		mSurface = new Surface(mSurfaceTexture);
	}
	
	private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
	
	

	public void play(long timeMs, float playbackMultiplier) {
		SetupGLIfNecessary();
		if (mSurface == null) {
			Log.i(TAG, "Cannot start video playback yet, will when ready");
			return;
		}
		if (mPlayer == null) {
			Log.i(TAG, "Creating new player on play request");
			mPlayer = new PlayerThread(mSurface);
		} else {
			Log.e(TAG, "NO NEW PLAYER CREATED");
		}
		
		mPlayer.playbackRate = playbackMultiplier;
		mPlayer.startOffset = timeMs;
		if (!mPlayer.isAlive()) {
			mPlayer.start();
		} else {
			Log.i(TAG, "Player is already running. Start Offset parameter ignored. Call stop before calling this");			
		}
		mPlayer.vResume();
	}

	public void pause() {
		GetOrCreatePlayer().vPause();
	}

	public void resume() {
		GetOrCreatePlayer().vResume();
	}

	public void pauseResume() {
		GetOrCreatePlayer().vPauseResume();
	}

	public void stop() {
		Log.i(TAG, "Player stop requested. Safe to start a new one");
		if (mPlayer == null) {
			Log.e(TAG, "Calling stop when already null!");
		} else {
			if (mPlayer.isAlive()) {
				mPlayer.queueTermination();
				Log.i(TAG, "Player is still alive even though we asked to stop it! Maybe too soon to check?");
			}
			mPlayer = null;
			Log.i(TAG, "We NULLing the player anyway");
		}
	}
	
	private class PlayerThread extends Thread {
		
		private MediaExtractor extractor;
		private MediaCodec decoder;
		private Surface surface;
		private long elapsedVideoMs = 0;
		private float playbackRate = 1;
		private long startOffset = 0;
		private int msTillReady = 0;
		private int msMaxCachedToPause = 150;
		private int msMinCachedToResume = 150;
		
		private volatile boolean runnin = false;
		
		private boolean paused = false;

		public boolean isPlaying() {
			return runnin;
		}
		
		public PlayerThread(Surface surface) {
			this.surface = surface;
		}
		
		public void setSurface(Surface surface) {
			Log.i(TAG, "Calling setSurface instead of init");
			this.surface = surface;
			if (isPlaying()) ConfigureDecoder();
		}
		
		public void vPause() {
			paused = true;
		}

		public void vPauseResume() {
			paused = !paused;
		}
		
		public void vResume() {
			Log.i(TAG, "Resuming video playback");
			paused = false;
		}

		private void ConfigureDecoder() {
			if (extractor == null) {
				Log.e(TAG, "Inappropriate configuration request");
				return;
			}
			mPlaybackStatus.videoTrackFound = false;
			for (int i = 0; i < extractor.getTrackCount(); i++) {
				MediaFormat format = extractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				Log.v(TAG, "Mime: " + mime);
				if (mime.startsWith("video/")) {
					extractor.selectTrack(i);
					decoder = MediaCodec.createDecoderByType(mime);
					decoder.configure(format, surface, null, 0);
					decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
					mPlaybackStatus.videoWidth = extractor.getTrackFormat(i).getInteger(MediaFormat.KEY_WIDTH);
					mPlaybackStatus.videoHeight = extractor.getTrackFormat(i).getInteger(MediaFormat.KEY_HEIGHT);
					mPlaybackStatus.videoTrackFound = true;
					break;
				}
			}			
		}
		
		@Override
		public void run() {
			extractor = new MediaExtractor();
			runnin = true;
			Log.v(TAG, mVideoURI);
			try {
				extractor.setDataSource(mVideoURI);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Log.v(TAG, "Track count: " + extractor.getTrackCount());
			ConfigureDecoder();
			if (decoder == null) {
				Log.e(TAG, "Can't find video info!");
				return;
			}

			decoder.start();

			ByteBuffer[] inputBuffers = decoder.getInputBuffers();
//			ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			boolean isEOS = false;
			
			long lastUpdateTime = System.currentTimeMillis();

			extractor.seekTo(startOffset*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
			Log.i(TAG, "Skipping by " + extractor.getSampleTime() + "ms into the video ");
			elapsedVideoMs = startOffset;
			decoder.flush();

			while (!interrupted() && runnin) {
				if (!isEOS) {
					int inIndex = decoder.dequeueInputBuffer(10000);
					if (inIndex >= 0) {
						ByteBuffer buffer = inputBuffers[inIndex];
						int sampleSize = extractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							// We shouldn't stop the playback at this point,
							// just pass the EOS
							// flag to decoder, we will get it again from the
							// dequeueOutputBuffer
							Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
							decoder.queueInputBuffer(inIndex, 0, 0, 0,
									MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							isEOS = true;
						} else {
							decoder.queueInputBuffer(inIndex, 0, sampleSize,
									extractor.getSampleTime(), 0);
							extractor.advance();
						}
					}
				}

				
				int outIndex = decoder.dequeueOutputBuffer(info, 10000);
				switch (outIndex) {
					case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
						Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
//						outputBuffers = decoder.getOutputBuffers();
						break;
					case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
						Log.d(TAG, "New format " + decoder.getOutputFormat());
						break;
					case MediaCodec.INFO_TRY_AGAIN_LATER:
						msTillReady = (int)(msMinCachedToResume - (extractor.getCachedDuration()/1000));
						Log.d(TAG, "Ms till ready: " + msTillReady);
						break;
					default:
						if (msTillReady > 0) {
							msTillReady = (int)(msMinCachedToResume - (extractor.getCachedDuration()/1000));
						} else {
							msTillReady = 0;
						}
						//ByteBuffer buffer = outputBuffers[outIndex];
						decoder.releaseOutputBuffer(outIndex, true);
	
						// We use a very simple clock to keep the video FPS, or the
						// video playback will be too fast
						long presTime = info.presentationTimeUs / 1000;
						long realTimeSinceUpdate = 0;
						long renderTime = (System.currentTimeMillis() - lastUpdateTime)/2;
//						Log.i(TAG, "Render time? " + renderTime);

						while (presTime > elapsedVideoMs + renderTime && runnin) {
							realTimeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
							lastUpdateTime = System.currentTimeMillis();
							elapsedVideoMs += (long) (playbackRate * realTimeSinceUpdate);
							try {
								sleep(10);
							} catch (InterruptedException e) {
								e.printStackTrace();
								break;
							}
						}
						break;
				}

				while (paused && runnin) {
					try {
						sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}
					lastUpdateTime = System.currentTimeMillis();
				}
				// All decoded frames have been rendered, we can stop playing
				// now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.i(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}

			decoder.stop();
			decoder.release();
			extractor.release();
			runnin = false;
		}
		
		public void queueTermination() {
			runnin = false;
		}
	}

	protected void onPause() {
		if (mPlayer != null) {
			mPlayer.vPause();
		}
	}

	protected void onResume() {
		if (mPlayer != null) {
			//mPlayer.vResume();
		}		
	}
	
	// Public API stuff
	public void SetSource(String stageId) {
		//SAMPLE = "http://edge.bitgym.com/v3-content/providers/virtual-active/ATBKPK0101/standalone/mp4/640x360_2048k_1.33x.mp4";
		mVideoURI = stageId;
		Log.i(TAG, "Setting source to: " + mVideoURI);

	}
	
	public void PreRollVideo(long offsetTime) {
		Log.i(TAG, "We are starting playback of the video at " + offsetTime + "ms");
		play(offsetTime, 1);
		
		//pause();
	}

	public PlaybackStatus SetRateAndGetStatus(float rate) {
		updateVideoFrameIfNew(); //hack sort of
		
		if (mPlayer == null) {
			Log.e(TAG, "Requesting status from null player");
			return null;
		}
		mPlayer.playbackRate = rate;
		if (mPlayer.msTillReady != 0) 
			Log.i(TAG, "Milliseconds until ready: " + mPlayer.msTillReady);
		mPlaybackStatus.downloadBitrate = 1000;
		//Log.i(TAG, "elapsed time: " + mPlayer.elapsedVideoMs);
		mPlaybackStatus.playbackTime = (int) (mPlayer.elapsedVideoMs);
		mPlaybackStatus.millisecondsUntilReady = (int) (mPlayer.msTillReady);
		return mPlaybackStatus;
	}


	public int getTextureId() {
		Log.d(TAG, "getTextureid returning texture  of: " + mSurfaceTextureId);
		return mSurfaceTextureId;
	}
	
	public float[] surfaceMatrix;
	
	public float[] getTextureTransformMatrix(){
		return surfaceMatrix;
	}
	
	private boolean hasNewFrame = false;

	public void updateVideoFrameIfNew() {
		synchronized (this) {
			if (hasNewFrame) {
				if(surfaceMatrix == null){
					surfaceMatrix = new float[16];
				}
				mSurfaceTexture.updateTexImage();
				mSurfaceTexture.getTransformMatrix(surfaceMatrix);
				hasNewFrame = false;
			}
		}
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		synchronized (this) {
			hasNewFrame = true;
		}

	}
	
	public void BlackOutGLTexture(int textureId){
		int w = 1;
		int h = 1;
		ByteBuffer image = ByteBuffer.allocateDirect(w*h*3);
		image.order(ByteOrder.nativeOrder());
		for (int i = 0; i < w*h*3; i++) {
			image.put(i, (byte)0);
		}
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
				GLES20.GL_REPEAT);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
				GLES20.GL_REPEAT);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
						w, h, 0, GLES20.GL_RGB,
						GLES20.GL_UNSIGNED_BYTE, image);
		checkGlError("glTexImage2D (blacking out texture)");
	}
	



}