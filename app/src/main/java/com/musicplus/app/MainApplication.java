package com.musicplus.app;

import java.io.File;

import android.app.Application;
import android.os.Environment;

public class MainApplication extends Application{
	
	private static final String SD_ROOT = Environment.getExternalStorageDirectory().getPath();
	public static final String APP_EXTERNAL_ROOT_PATH = SD_ROOT + "/MusicPlus";
	public static final String TEMP_FILE_PATH = APP_EXTERNAL_ROOT_PATH + "/temp";
	public static final String TEMP_AUDIO_PATH = TEMP_FILE_PATH + "/audio";
	public static final String TEMP_VIDEO_PATH = TEMP_FILE_PATH + "/video";
	public static final String RECORD_AUDIO_PATH = APP_EXTERNAL_ROOT_PATH + "/audio";
	public static final String RECORD_VIDEO_PATH = APP_EXTERNAL_ROOT_PATH + "/video";
	
	@Override
	public void onCreate() {
		super.onCreate();
		createStoreDirs();
	}
	
	private void createStoreDirs(){
		File tempAudioDir = new File(TEMP_AUDIO_PATH);
		if(!tempAudioDir.exists()){
			tempAudioDir.mkdirs();
		}
		File tempVideoDir = new File(TEMP_VIDEO_PATH);
		if(!tempVideoDir.exists()){
			tempVideoDir.mkdir();
		}
		File recordAudioPath = new File(RECORD_AUDIO_PATH);
		if(!recordAudioPath.exists()){
			recordAudioPath.mkdir();
		}
		File recordVideoPath = new File(RECORD_VIDEO_PATH);
		if(!recordVideoPath.exists()){
			recordVideoPath.mkdir();
		}
	}
}
