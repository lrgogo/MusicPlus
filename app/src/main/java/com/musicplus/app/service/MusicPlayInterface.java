package com.musicplus.app.service;

import android.net.Uri;

public interface MusicPlayInterface {
	/**
	 *  to play an audio
	 * @param audioUri
	 */
	void play(Uri audioUri);
	
	/**
	 *  to pause the play
	 */
	void pause();
	
	/**
	 * Starts or resumes playback. If playback had previously been paused,
	 *  playback will continue from where it was paused. If playback had been stopped, 
	 *  or never started before, playback will start at the beginning.
	 */
	void start();
	
	/**
	 * to release the resource
	 */
	void close();
}
