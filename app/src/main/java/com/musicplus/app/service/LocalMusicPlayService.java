package com.musicplus.app.service;

import java.io.IOException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

/**
 * Local Music Play Service
 * @author Darcy
 *
 */
public class LocalMusicPlayService extends Service implements MusicPlayInterface , OnPreparedListener{
    
	private AudioFocusHelper mAudioFocusHelper;
	private MediaPlayer mAudioPlayer;
	private Uri mAudioUri;
	
	private boolean mPlaying;
	private boolean mPrepared;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mAudioPlayer = new MediaPlayer();
		mAudioFocusHelper = new AudioFocusHelper(this,mAudioPlayer);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if(mAudioPlayer != null){
			mAudioPlayer.release();
			mAudioPlayer = null;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return new LocalMusicPlayBinder(this);
	}

	public static class LocalMusicPlayBinder extends Binder {
		
		LocalMusicPlayService service;
		
		LocalMusicPlayBinder(LocalMusicPlayService service){
			this.service = service;
		}
		
		public MusicPlayInterface getService() {
            return service;
        }
    }

	@Override
	public void play(Uri audioUri) {
		
		if(mAudioUri != null && mAudioUri.equals(audioUri) && mAudioPlayer.isPlaying()){
			mAudioPlayer.start();// start at the beginning
			return;
		}else{
			mAudioUri = audioUri;
		}
		
		mPlaying = false;
		mPrepared = false;
		mAudioPlayer.reset();
		mAudioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		try {
			mAudioPlayer.setOnPreparedListener(this);
			mAudioPlayer.setDataSource(this, audioUri);
			mAudioPlayer.prepareAsync();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void pause() {
		if(mPlaying){
			mAudioFocusHelper.abandonFocus();
			mAudioPlayer.pause();
			mPlaying = false;
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mAudioFocusHelper.requestFocus();
		mAudioPlayer.start();
		mPlaying = true;
		mPrepared = true;
	}

	@Override
	public void close() {
		mAudioPlayer.stop();
		mAudioPlayer.release();
		mPlaying = false;
		mPrepared = false;
	}

	@Override
	public void start() {
		if(mPrepared){
			mAudioPlayer.start();
		}
	}
	
	/**
	 * Audio Focus 
	 * @author Darcy
	 * @version android.os.Build.VERSION.SDK_INT >= 8
	 */
	static class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
	   
		private AudioManager mAudioManager;
		private MediaPlayer mMediaPlayer;
		private Context mContext;


	    public AudioFocusHelper(Context ctx , MediaPlayer player) {
	    	this.mContext = ctx;
	        this.mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
	        this.mMediaPlayer = player;
	    }

	    public boolean requestFocus() {
	        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
	            mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
	            AudioManager.AUDIOFOCUS_GAIN);
	    }

	    public boolean abandonFocus() {
	        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == mAudioManager.abandonAudioFocus(this);
	    }

	    @Override
	    public void onAudioFocusChange(int focusChange) {
	    	switch (focusChange) {
	        case AudioManager.AUDIOFOCUS_GAIN:
	            if (mMediaPlayer == null){
	            	return;
	            }else if (!mMediaPlayer.isPlaying()) {
	            	mMediaPlayer.start();
	            }
	            mMediaPlayer.setVolume(0.5f, 0.5f);
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS:
	            if (mMediaPlayer.isPlaying()) 
	            	mMediaPlayer.stop();
	            mMediaPlayer.release();
	            break;
	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
	            if (mMediaPlayer.isPlaying()) 
	            	mMediaPlayer.pause();
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
	            if (mMediaPlayer.isPlaying()) 
	            	mMediaPlayer.setVolume(0.1f, 0.1f);
	            break;
	    	}

	    }
	}
}
