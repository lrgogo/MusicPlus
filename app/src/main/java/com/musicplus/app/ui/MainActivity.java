package com.musicplus.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.musicplus.R;

public class MainActivity extends BaseActivity{
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	public void onExcludeClick(View v){
		Intent composeIntent  = new Intent(this, ComposeActivity.class);
		composeIntent.putExtra(ComposeActivity.EX_INCLUDE_VIDEO_AUDIO, false);
		startActivity(composeIntent);
	}
	
	public void onIncludeClick(View v){
		Intent composeIntent  = new Intent(this, ComposeActivity.class);
		composeIntent.putExtra(ComposeActivity.EX_INCLUDE_VIDEO_AUDIO, true);
		startActivity(composeIntent);
	}
	
	public void onMixAudioClick(View v){
		Intent mixAudioIntent  = new Intent(this, MixAudioActivity.class);
		startActivity(mixAudioIntent);
	}
}
