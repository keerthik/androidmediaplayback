/*
 * This activity is pretty dumb, it's meant for the server-client-architecture
 */

package com.activetheoryinc.videoplaybacktest;

import android.app.Activity;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.activetheoryinc.playback.PlaybackEngine;

public class VideoActivity extends Activity {
	private static final String TAG = "VideoActivity";

	private PlaybackEngine BGPlayer;
	
	private TextureView mainv;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		mainv = (TextureView) findViewById(R.id.t_main_video);
		
		BGPlayer = new PlaybackEngine(mainv);
		BGPlayer.PreRollVideo(0);
		//BGPlayer.play(null, 0, 1);
		
		((Button) findViewById(R.id.btn_play)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				BGPlayer.play(99000, 1f);
				((TextView) findViewById(R.id.resuming)).setText("This app is alive before this instants");
			}
		});
		((Button) findViewById(R.id.btn_pause)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				BGPlayer.pauseResume();
			}
		});
		((Button) findViewById(R.id.btn_stop)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				BGPlayer.stop();
			}
		});
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	protected void onPause() {
		super.onPause();
	}	

}