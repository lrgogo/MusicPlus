package com.musicplus.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;

import com.musicplus.app.MainApplication;
import com.musicplus.entry.AudioEntry;
import com.musicplus.media.MultiAudioMixer.AudioMixException;
import com.musicplus.media.MultiAudioMixer.OnAudioMixListener;
import com.musicplus.utils.MD5Util;

/**
 * 多路音频同时播放器，原理是通过{@link MultiAudioMixer}进行混音成一条音轨进行播放
 * @author Darcy
 */
public class MultiRawAudioPlayer {
	
	private final static int sampleRateInHz = 44100;
	private final static int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
	private final static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
	
	private volatile boolean playing = false;
	private volatile boolean stopped = false;
	private final Object mLock = new Object();
	
	private static final int MSG_PLAY_START = 0x1;
	private static final int MSG_PLAY_STOP = 0x2;
	private static final int MSG_PLAY_COMPLETE = 0x3;
	
	private EventHandler mEventHandler = new EventHandler();
	private CyclicBarrier mRecordBarrier; //同步录像
	
	private AudioEntry[] mAudioEntries;
	
	private static class EventHandler extends Handler{
		
		private OnRawAudioPlayListener event;
		
		public void setOnRawAudioPlayListener(OnRawAudioPlayListener l){
			event = l;
		}
		
		public void handleMessage(android.os.Message msg) {
			String tempMixFile = (String) msg.obj;
			switch(msg.what){
			case MSG_PLAY_START:
				if(event != null)
					event.onPlayStart();
				break;
			case MSG_PLAY_STOP:
				if(event != null)
					event.onPlayStop(tempMixFile);
				break;
			case MSG_PLAY_COMPLETE:
				if(event != null)
					event.onPlayComplete(tempMixFile);
				break;
			}
		};
	}
	
	public MultiRawAudioPlayer(AudioEntry[] audioEntries,CyclicBarrier recordBarrier){
		this.mAudioEntries = audioEntries;
		this.mRecordBarrier = recordBarrier;
	}
	
	public void setOnRawAudioPlayListener(OnRawAudioPlayListener l){
		mEventHandler.setOnRawAudioPlayListener(l);
	}
	
	/**
	 * 调用该方法开始播放或者从头播放
	 */
	public void play(){
		
		if(mAudioEntries == null || mAudioEntries.length == 0)
			return;
		
		if(playing){
			stop();
			play();
		}else{
			stopped = false;
			playing = true;
			new PlayThread().start();
		}
	}
	
	public void testPlaySync(){
		new PlayThread().run();
	}
	
	/**
	 * 停止播放
	 */
	public void stop(){
		stopped = true;
		if(playing){
			synchronized (mLock) {
				try {
					mLock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	 AudioTrack createTrack(){
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
		return  new  AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM);
	}
	
	private void waitForRecordStart(){
		if(mRecordBarrier != null){
			try {
				mRecordBarrier.await();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	 
	private class PlayThread extends Thread{
		
		@Override
		public void run() {
			int audioSize = mAudioEntries.length;
			File[] rawAudioFiles = new File[audioSize];
			StringBuilder sbMix = new StringBuilder();
			for(int index = 0; index < audioSize ; ++index){
				AudioEntry audioEntry = mAudioEntries[index];
				rawAudioFiles[index] = new File(audioEntry.fileUrl);
				sbMix.append(audioEntry.id);
			}
			
			boolean hasMixBefore = false;
			final String mixFilePath = MainApplication.TEMP_AUDIO_PATH + "/" +MD5Util.getMD5Str(sbMix.toString());
			final File tempMixFile = new File(mixFilePath);
			
			final AudioTrack audioTrack = createTrack();
			audioTrack.play();
			
			try{
				final FileOutputStream fosTempMixAudio = new FileOutputStream(tempMixFile);
				//single audio
				if(audioSize == 1 || hasMixBefore){
					FileInputStream audioInput = null;
					try {
						File singleAudioFile = hasMixBefore ? tempMixFile : rawAudioFiles[0];
						audioInput = new FileInputStream(singleAudioFile);
						byte[] audioData = new byte[512];
						int readCount=0, totalSize=0;
						
						if(!stopped && (readCount = audioInput.read(audioData))!= -1){
							fosTempMixAudio.write(audioData, 0, readCount);
							mEventHandler.sendEmptyMessage(MSG_PLAY_START);
							waitForRecordStart();
							audioTrack.write(audioData, 0, readCount);
							totalSize += readCount;
						}
						
						while(!stopped && (readCount = audioInput.read(audioData))!= -1){
							fosTempMixAudio.write(audioData, 0, readCount);
							audioTrack.write(audioData, 0, readCount);
							totalSize += readCount;
						}
						
						if(totalSize >= singleAudioFile.length()){
							Message completeMsg = mEventHandler.obtainMessage(MSG_PLAY_COMPLETE, mixFilePath);
							mEventHandler.sendMessage(completeMsg);
						}
						
						if(stopped){
							Message stopMsg = mEventHandler.obtainMessage(MSG_PLAY_STOP, mixFilePath);
							mEventHandler.sendMessage(stopMsg);
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}finally{
						try {
							if(audioInput != null)
								audioInput.close();
							
							if(fosTempMixAudio != null)
								fosTempMixAudio.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					return;
				}
				
				// multi audios
				MultiAudioMixer mixer = MultiAudioMixer.createAudioMixer();
				mixer.setOnAudioMixListener(new OnAudioMixListener() {
					
					boolean isFirst = false;
					
					public void onMixing(byte[] mixBytes) throws IOException {
						if(stopped){
							fosTempMixAudio.close();
							Message stopMsg = mEventHandler.obtainMessage(MSG_PLAY_STOP, mixFilePath);
							mEventHandler.sendMessage(stopMsg);
							throw new AudioMixException("stop play the mix audios.");
						}else{
							fosTempMixAudio.write(mixBytes);
							if(!isFirst){
								isFirst = true;
								mEventHandler.sendEmptyMessage(MSG_PLAY_START);
								waitForRecordStart();
							}
							audioTrack.write(mixBytes, 0, mixBytes.length);
						}
					}
					
					@Override
					public void onMixError(int errorCode) {
						
						if(fosTempMixAudio != null){
							try {
								fosTempMixAudio.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					
					public void onMixComplete() {
						
						if(fosTempMixAudio != null){
							try {
								fosTempMixAudio.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						
						Message completeMsg = mEventHandler.obtainMessage(MSG_PLAY_COMPLETE, mixFilePath);
						mEventHandler.sendMessage(completeMsg);
					}
				});
				mixer.mixAudios(rawAudioFiles);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}finally{
				audioTrack.stop();
				audioTrack.release();
				playing = false;
				synchronized (mLock) {
					mLock.notifyAll();
				}
			}
		}
	}
	
	public interface OnRawAudioPlayListener{
		void onPlayStart();
		void onPlayStop(String tempMixFile);
		void onPlayComplete(String tempMixFile);
	}
}
