package com.musicplus.utils;

import android.util.Log;

public class DLog {
	
	private static boolean debug = true;
	
	private static final String TAG = "com.darcymusic";
	
	public static void i(String tag, String msg){
		if(debug)
			Log.i(tag, msg);
	}
	
	public static void i(String msg){
		if(debug)
			Log.i(TAG, msg);
	}
	
	public static void w(String tag, String msg){
		if(debug)
			Log.w(tag, msg);
	}
	
	public static void w(String msg){
		if(debug)
			Log.w(TAG, msg);
	}
	
	public static void e(String tag, String msg){
		if(debug)
			Log.e(tag, msg);
	}
	
	public static void e(String msg){
		if(debug)
			Log.e(TAG, msg);
	}
}
