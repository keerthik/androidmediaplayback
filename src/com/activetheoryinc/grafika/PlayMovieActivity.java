/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.activetheoryinc.grafika;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;

import com.activetheoryinc.playback.NexusPlaybackEngine;
import com.activetheoryinc.playback.PlayMovieThread.MovieThreadListener;

/**
 * Play a movie from a file on disk.  Output goes to a TextureView.
 * <p>
 * Currently video-only.
 */
public class PlayMovieActivity extends Activity implements OnItemSelectedListener, OnSeekBarChangeListener, MovieThreadListener {
    private static final String TAG = "Media Test";

    private TextureView mTextureView;
    private String[] mMovieFiles;
    private int mSelectedMovie;
    private boolean mShowStopLabel;
    private NexusPlaybackEngine player = null;
    //private PlayMovieTask mPlayTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_movie);

        mTextureView = (TextureView) findViewById(R.id.movie_texture_view);

        // Populate file-selection spinner.
        Spinner spinner = (Spinner) findViewById(R.id.playMovieFile_spinner);
        SeekBar seekbar = (SeekBar) findViewById(R.id.playMovie_speed);
        
        // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
        // for the widget itself.
        String [] _mMovieFiles = FileUtils.getFiles(getFilesDir(), "*.mp4");
        int added = 3;
        int n = _mMovieFiles.length + added;
        mMovieFiles = new String[n];
        for (int i = added; i < n; i++) {
        	mMovieFiles[i] = _mMovieFiles[i-added];
        }
        mMovieFiles[0] = Environment.getExternalStorageDirectory().getPath() + "/Movies/videoviewdemo.mp4";
        mMovieFiles[1] = "http://edge.bitgym.com/blank_640x360_2048k_1.33x.mp4";
        mMovieFiles[2] = "http://bgstreaming.s3.amazonaws.com/providers/virtual-active/ATRNPK0110/full-video.mp4";
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, mMovieFiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        
        seekbar.setOnSeekBarChangeListener(this);
        
        if (player == null) player = new NexusPlaybackEngine(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "Just a new config");
        setContentView(R.layout.activity_play_movie);
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		player.onPause();
	}
	
    @Override
	protected void onResume() {
		super.onResume();
		if (savedSurfaceTexture != null)
			mTextureView.setSurfaceTexture(savedSurfaceTexture);
		player.onResume();
	}

    @Override
    protected void onDestroy() {
    	player.EndPlayback();
    	super.onDestroy();
    }

    /*
     * Called when the movie Spinner gets touched.
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        mSelectedMovie = spinner.getSelectedItemPosition();
        Log.d(TAG, "onItemSelected: " + mSelectedMovie + " '" + mMovieFiles[mSelectedMovie] + "'");
        if (player.isCurrentlyPlaying()) {
        	Log.i(TAG, "New selection while playing");
        }
    }

    @Override public void onNothingSelected(AdapterView<?> parent) {}
    
    public void clickPause(View unused) {
    	player.flipPause();
    }
    
    /**
     * onClick handler for "play"/"stop" button.
     */
    private SurfaceTexture savedSurfaceTexture = null;
    private void startMovie() {
        /*
    	if (mPlayTask != null) {
            Log.w(TAG, "movie already playing");
            return;
        }
        */
        Log.d(TAG, "starting movie");
        if (savedSurfaceTexture == null) savedSurfaceTexture = mTextureView.getSurfaceTexture();
        player.SetSurfaceTexture(savedSurfaceTexture);
        player.SetSource(mMovieFiles[mSelectedMovie]);
        /*
        if (((CheckBox) findViewById(R.id.loopPlayback_checkbox)).isChecked()) {
            mPlayTask.setLoopMode(true);
        }
        if (((CheckBox) findViewById(R.id.locked60fps_checkbox)).isChecked()) {
            callback.setFixedPlaybackRate(15);
            //mPlayTask.SetRateAndGetStatus(1f);
        }
        */
        mShowStopLabel = true;
        updateControls();
        player.PreRollVideo(0);    	
    }
    
    public void clickPlayStop(View unused) {
        if (mShowStopLabel) {
            stopPlayback();
            // Don't update the controls here -- let the async task do it after the movie has
            // actually stopped.
        } else {
        	startMovie();
        }
    }

    /**
     * Requests stoppage if a movie is currently playing.
     */
    private void stopPlayback() {
    	player.EndPlayback();
    	mShowStopLabel = false;
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button play = (Button) findViewById(R.id.play_stop_button);
        if (mShowStopLabel) {
            play.setText(R.string.stop_button_text);
        } else {
            play.setText(R.string.play_button_text);
        }

        // We don't support changes mid-play, so dim these.
        CheckBox check = (CheckBox) findViewById(R.id.locked60fps_checkbox);
        check.setEnabled(!mShowStopLabel);
        check = (CheckBox) findViewById(R.id.loopPlayback_checkbox);
        check.setEnabled(!mShowStopLabel);
    }

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if (!fromUser) {
			Log.i(TAG, "Progress not doing anything");
			return;
		}
		player.SetRateAndGetStatus(0.01f*progress);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFinishMovie(int threadIndex) {
		runOnUiThread(new Runnable() {
	        public void run() {
	        	updateControls();
	        }
	    });
	}

	@Override
	public void onMovieBuffered(int threadIndex) {
		// TODO Auto-generated method stub
		
	}

    /**
     * Plays a movie in an async task thread.  Updates the UI when the movie finishes.
     */
	
}
