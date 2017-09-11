package com.musicplus.app.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

import android.app.ProgressDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.musicplus.R;
import com.musicplus.app.MainApplication;
import com.musicplus.entry.AudioEntry;
import com.musicplus.media.AudioDecoder;
import com.musicplus.media.AudioDecoder.OnAudioDecoderListener;
import com.musicplus.media.MultiRawAudioPlayer;
import com.musicplus.media.MultiRawAudioPlayer.OnRawAudioPlayListener;
import com.musicplus.media.VideoMuxer;
import com.musicplus.media.VideoRecorder;
import com.musicplus.media.VideoRecorder.OnVideoRecordListener;
import com.musicplus.utils.MD5Util;

/**
 * 
 * follow the steps below:<br>
 * <ol>
 * <li>choose music as video's audio track</li>
 * <li>begin to record video</li>
 * <li>finish the recording</li>
 * </ol>
 * @author Darcy
 */
public class ComposeActivity extends BaseActivity implements View.OnClickListener, OnRawAudioPlayListener{
	
	private final static String TEMP_RECORD_VIDEO_FILE = MainApplication.TEMP_VIDEO_PATH + "/temp_record_video";
	private final static String FINAL_MIX_VIDEO_FILE = MainApplication.RECORD_VIDEO_PATH + "/final_mix_video.mp4";
	
	private final static int RECORD_STATE_INITIAL = 0x0;
	private final static int RECORD_STATE_PREPARING = 0x1;
	private final static int RECORD_STATE_RECORDING = 0x2;
	private final static int RECORD_STATE_DONE = 0x3;
	
	private final static int REQUEST_CODE_ADD_MUSIC = 0x1;
	
	public final static String EX_INCLUDE_VIDEO_AUDIO = "include-video-audio";

	
	private SurfaceView svVideoPreview;
	private Button btnRecord;
	private Button btnAddMusic;
	private Button btnPreview;
	private Button btnRedoRecord;
	private LinearLayout containerAudioTracks;
	private VideoRecorder videoRecorder;
	private Set<AudioEntry> addAudioTracks = new HashSet<AudioEntry>();
	private MultiRawAudioPlayer mBackMisicPlayer;
	private String mTempMixAudioFile;
	private  CyclicBarrier recordBarrier = new CyclicBarrier(2);
	private ProgressDialog dlgDecoding;
	private ProgressDialog dlgMuxing;
	private MediaPlayer videoPlayer;
	private String finalMixVideo;
	
	private int recordState;
	private boolean isIncludeVideoAudio = false;
	private static final int MAX_PROGRESS = 100;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_compose);
		svVideoPreview = findView(R.id.sv_video_preview);
		btnRecord = findView(R.id.btn_record);
		btnRecord.setOnClickListener(this);
		btnAddMusic = findView(R.id.btn_add_music);
		btnAddMusic.setOnClickListener(this); 
		containerAudioTracks = findView(R.id.container_musics);
		btnPreview = findView(R.id.btn_preview);
		btnPreview.setOnClickListener(this);
		btnRedoRecord = findView(R.id.btn_re_record);
		btnRedoRecord.setOnClickListener(this);
		
		videoRecorder = new VideoRecorder(this, svVideoPreview, TEMP_RECORD_VIDEO_FILE,recordBarrier);
		recordState = RECORD_STATE_INITIAL;
		
		isIncludeVideoAudio = getIntent().getBooleanExtra(EX_INCLUDE_VIDEO_AUDIO, false);
		
		LayoutParams lpPre = svVideoPreview.getLayoutParams();
		lpPre.height = 1080 * 640 / 480;
		svVideoPreview.setLayoutParams(lpPre);
		
		dlgDecoding = new ProgressDialog(this);
		dlgDecoding.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dlgDecoding.setCancelable(false);
		dlgDecoding.setCanceledOnTouchOutside(false);
		dlgDecoding.setMax(MAX_PROGRESS);
		
		dlgMuxing = new ProgressDialog(this);
		dlgMuxing.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dlgMuxing.setCancelable(false);
		dlgMuxing.setCanceledOnTouchOutside(false);
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.btn_record:
			performRecord();
			break;
		case R.id.btn_add_music:
			performAddMusic();
			break;
		case R.id.btn_preview:
			performPreview();
			break;
		case R.id.btn_re_record:
			performRedoRecord();
			break;
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		videoRecorder.release();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		svVideoPreview.postDelayed(new Runnable(){
			@Override
			public void run() {
				videoRecorder.startPreview();
			}
		}, 100);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		stopBackgroundMusic();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_CODE_ADD_MUSIC && resultCode == RESULT_OK){
			dlgDecoding.show();
			AudioEntry audioEntry = (AudioEntry) data.getSerializableExtra("audio");
			new DecodeAudioTask(audioEntry).execute();
		}
	}
	
	private void performRecord(){
		if(recordState == RECORD_STATE_INITIAL){
			recordState = RECORD_STATE_PREPARING;
			videoRecorder.setOnVideoRecordListener(new OnVideoRecordListener() {
				@Override
				public void onStarted() {
					recordState = RECORD_STATE_RECORDING;
					btnRecord.setText(R.string.complete_record);
				}
				
				public void onError(int errorCode) {
				}
			});
			videoRecorder.startRecord();
			playBackgroundMusic();
		}else if(recordState == RECORD_STATE_RECORDING){
			recordState = RECORD_STATE_DONE;
			dlgMuxing.setMessage(getString(R.string.please_wait));
			dlgMuxing.show();
			videoRecorder.release();
			recordBarrier.reset();
			stopBackgroundMusic();
			btnRecord.postDelayed(new Runnable() {
				@Override
				public void run() {
					final boolean hasBackgroundMisic = hasBackgroundMisic();
					if(hasBackgroundMisic){
						new MixVideoAndAudioTask().execute();
					}else{
						finalMixVideo = TEMP_RECORD_VIDEO_FILE;
					}
				}
			}, 500);
		}
	}
	
	@Override
	public void onPlayStart() {
		videoRecorder.startRecord();
	}

	@Override
	public void onPlayStop(String tempMixFile) {
		mTempMixAudioFile = tempMixFile;
	}

	@Override
	public void onPlayComplete(String tempMixFile) {
		mTempMixAudioFile = tempMixFile;
	}
	
	private void performAddMusic(){
		Intent addMusicIntent = new Intent(this,AudioChooserActivity.class);
		startActivityForResult(addMusicIntent, REQUEST_CODE_ADD_MUSIC);
	}
	
	private void performRedoRecord(){
		recordState = RECORD_STATE_INITIAL;
		btnRecord.setText(R.string.record);
		videoRecorder.release();
		videoRecorder.startPreview();
	}
	
	private void performPreview(){
		if(svVideoPreview != null && finalMixVideo != null){
			new VideoPlayTask(svVideoPreview, finalMixVideo).execute();
		}
	}
	
	private void playBackgroundMusic(){
		int trackSize = addAudioTracks.size();
		if(trackSize > 0){
			mBackMisicPlayer = new MultiRawAudioPlayer(addAudioTracks.toArray(new AudioEntry[trackSize]),recordBarrier);
			mBackMisicPlayer.setOnRawAudioPlayListener(this);
			mBackMisicPlayer.play();
		}
	}
	
	private void stopBackgroundMusic(){
		if(mBackMisicPlayer!=null)
			mBackMisicPlayer.stop();
	}
	
	private boolean hasBackgroundMisic(){
		return addAudioTracks != null && addAudioTracks.size() > 0;
	}
	
	class MixVideoAndAudioTask extends AsyncTask<Void, Long, Boolean>{

		@Override
		protected Boolean doInBackground(Void... params) {
			finalMixVideo = FINAL_MIX_VIDEO_FILE;
			VideoMuxer videoMuxer = VideoMuxer.createVideoMuxer(finalMixVideo);
			videoMuxer.mixRawAudio(new File(TEMP_RECORD_VIDEO_FILE), new File(mTempMixAudioFile), isIncludeVideoAudio);
			return true;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			dlgMuxing.cancel();
			Toast.makeText(ComposeActivity.this, getString(R.string.mix_video_success), Toast.LENGTH_SHORT).show();
		}
	}
	
	class DecodeAudioTask extends AsyncTask<Void, Double, Boolean>{

		AudioEntry decAudio;
		
		DecodeAudioTask(AudioEntry decAudio){
			this.decAudio = decAudio;
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			String decodeFilePath = MainApplication.TEMP_AUDIO_PATH + "/" + MD5Util.getMD5Str(decAudio.fileUrl);
			File decodeFile = new File(decodeFilePath);
			if(decodeFile.exists()){
				publishProgress(1.0);
				decAudio.fileUrl = decodeFilePath;
				return true;
			}
			
			if(decAudio.mime.contains("x-ms-wma")){
				FileInputStream fisWavFile = null;
				FileOutputStream fosRawAudioFile = null;
				try {
					 File srcAudioFile = new File(decAudio.fileUrl);
					 long audioFileSize = srcAudioFile.length();
					 fisWavFile = new FileInputStream(srcAudioFile);
					 fosRawAudioFile = new FileOutputStream(decodeFile);
					 fisWavFile.read(new byte[44]);
					 byte[] rawBuf = new byte[1024];
					 int readCount;
					 double totalReadCount = 44;
					 while((readCount = fisWavFile.read(rawBuf)) != -1){
						 fosRawAudioFile.write(rawBuf, 0, readCount);
						 totalReadCount += readCount;
						 publishProgress(totalReadCount/audioFileSize);
					 }
					 publishProgress(1.0);
					 return true;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}finally{
					try {
					if(fisWavFile != null)
						fisWavFile.close();
						
					if(fosRawAudioFile != null)
						fosRawAudioFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}else{
				AudioDecoder audioDec = AudioDecoder.createDefualtDecoder(decAudio.fileUrl);
				try {
					decAudio.fileUrl = decodeFilePath;
					audioDec.setOnAudioDecoderListener(new OnAudioDecoderListener() {
						@Override
						public void onDecode(byte[] decodedBytes, double progress)throws IOException {
							publishProgress(progress);
						}
					});
					audioDec.decodeToFile(decodeFilePath);
					return true;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		
		@Override
		protected void onProgressUpdate(Double... values) {
			super.onProgressUpdate(values);
			dlgDecoding.setProgress((int) (MAX_PROGRESS * values[0]));
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if(result){
				addMisicTrack(decAudio);
			}
			dlgDecoding.cancel();
		}
		
		private void addMisicTrack(final AudioEntry decAudio){
			if(addAudioTracks.contains(decAudio))
				return;
			
			addAudioTracks.add(decAudio);
			final View viewTrack = View.inflate(ComposeActivity.this, R.layout.listitem_audio_info, null);
			TextView tvName = (TextView)viewTrack.findViewById(R.id.tv_file_name);
			TextView tvArtist = (TextView)viewTrack.findViewById(R.id.tv_artist);
			ImageView tvDel = (ImageView)viewTrack.findViewById(R.id.iv_play);
			tvName.setText(decAudio.fileName);
			tvArtist.setText(decAudio.artist);
			tvDel.setImageResource(R.drawable.ic_delete);
			tvDel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					addAudioTracks.remove(decAudio);
					containerAudioTracks.removeView(viewTrack);
				}
			});
			
			containerAudioTracks.addView(viewTrack);
		}
	}
	
	class VideoPlayTask extends AsyncTask<Void, Long, Boolean>{
		
		private SurfaceView surfaceView;
		
		private String vidoeFile;
		
		VideoPlayTask(SurfaceView surfaceView,String vidoeFile){
			this.surfaceView = surfaceView;
			this.vidoeFile = vidoeFile;
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
            try {
            	if(videoPlayer == null){
            		videoPlayer = new MediaPlayer();
            	}else{
            		videoPlayer.reset();
            	}
    			videoPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    			videoPlayer.setDisplay(surfaceView.getHolder());
            	videoPlayer.setDataSource(vidoeFile);
            	videoPlayer.prepare();
            	return true;
            } catch (Exception e) {
                e.printStackTrace();
                videoPlayer.release();
                return false;
            }
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if(result){
				videoPlayer.start();
			}
		}
	}
}
