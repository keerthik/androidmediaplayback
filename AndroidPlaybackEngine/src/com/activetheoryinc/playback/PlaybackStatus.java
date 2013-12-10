package com.activetheoryinc.playback;

public class PlaybackStatus {
	public int playbackTime;
	public int videoWidth;
	public int videoHeight;
	public int downloadBitrate;
	public int playbackBitrate;
	public boolean videoTrackFound; // If the connection is stupidly slow, a
									// video stream may revert to an audio-only
									// stream
	public int millisecondsUntilReady; // negative number means we don't know,
										// 0.0f means we're ready to play
	String errorMessage;
}