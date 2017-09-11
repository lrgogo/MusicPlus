package com.musicplus.app.ui;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.musicplus.R;
import com.musicplus.app.service.LocalMusicPlayService;
import com.musicplus.app.service.MusicPlayInterface;
import com.musicplus.entry.AudioEntry;
import com.musicplus.media.MediaUtils;

/**
 * 选择合成音频
 * 
 * @author Darcy
 */
public class AudioChooserActivity extends BaseActivity {

	private static final String[] SUPPORT_DECODE_AUDIOFORMAT = {"audio/mpeg", "audio/x-ms-wma","audio/mp4a-latm" ,"audio/x-wav"};
	
	private RecyclerView mRvAudio;
	private MusicPlayInterface mMisPlayService;

	private static final int PAGE_SIZE = 20;
	private int mPage = 1;
	private boolean mHasMore = true;
	
	private ServiceConnection mMisSerciveConn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mMisPlayService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mMisPlayService = ((LocalMusicPlayService.LocalMusicPlayBinder)service).getService();
		}
	};
	
	private RecyclerView.OnScrollListener mRvScrollListener = new RecyclerView.OnScrollListener(){
		public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
			if(newState == RecyclerView.SCROLL_STATE_SETTLING){
				//加载下页
				if(mHasMore){
					mPage++;
					new GetAudiosTask(AudioChooserActivity.this).execute();
				}
			}
		};
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
		mRvAudio = findView(R.id.recycler_view);
		mRvAudio.setLayoutManager(new LinearLayoutManager(this));
		mRvAudio.addOnScrollListener(mRvScrollListener);
		new GetAudiosTask(this).execute();
		Intent misIntent = new Intent(this,LocalMusicPlayService.class);
		bindService(misIntent, mMisSerciveConn, BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if(mMisPlayService != null){
			mMisPlayService.start();
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		if(mMisPlayService != null){
			mMisPlayService.pause();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mMisSerciveConn);
	}
	
	class GetAudiosTask extends AsyncTask<Void, Void, List<AudioEntry>> {
		
		Context context;
		
		GetAudiosTask(Context context){
			this.context = context;
		}
		
		@Override
		protected List<AudioEntry> doInBackground(Void... params) {
			return getLocalAudioes(context, mPage, PAGE_SIZE);
		}
		
		@Override
		protected void onPostExecute(List<AudioEntry> result) {
			super.onPostExecute(result);
			if(result == null || result.isEmpty()){
				mHasMore = false;
			}else{
				mRvAudio.setAdapter(new AudioInfoAdapter(context,result));
			}
		}
		
		/**
		 * 获取sd卡所有的音频文件
		 * 
		 * @param context
		 * @param page 从1开始
		 * @param context
		 * @return
		 * @throws Exception
		 */
		private ArrayList<AudioEntry> getLocalAudioes(Context context , int page , int pageSize) {

			ArrayList<AudioEntry> audios = null;

			StringBuilder selectionBuilder = new StringBuilder();
			int size = SUPPORT_DECODE_AUDIOFORMAT.length;
			for(int i = 0; i != size ; ++i){
				selectionBuilder.append("mime_type=? or ");
			}
			int sbLen = selectionBuilder.length();
			selectionBuilder.delete(sbLen - 3, sbLen);
			
			final String selection = selectionBuilder.toString();
			final String orderBy = String.format("%s LIMIT %s , %s ",MediaStore.Audio.Media.DEFAULT_SORT_ORDER, (page-1)*pageSize , pageSize);
			
			Cursor cursor = context.getContentResolver().query(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					new String[] { MediaStore.Audio.Media._ID,
							MediaStore.Audio.Media.DISPLAY_NAME,
							MediaStore.Audio.Media.TITLE,
							MediaStore.Audio.Media.DURATION,
							MediaStore.Audio.Media.ARTIST,
							MediaStore.Audio.Media.ALBUM,
							MediaStore.Audio.Media.YEAR,
							MediaStore.Audio.Media.MIME_TYPE,
							MediaStore.Audio.Media.SIZE,
							MediaStore.Audio.Media.DATA },
							selection,
							SUPPORT_DECODE_AUDIOFORMAT, orderBy);

			audios = new ArrayList<AudioEntry>();

			if (cursor.moveToFirst()) {

				AudioEntry audioEntry = null;

				String fileUrl = null;
				boolean isMatchAudioFormat = false;
				do {
					fileUrl = cursor.getString(9);
					isMatchAudioFormat = MediaUtils.isMatchAudioFormat(fileUrl, 44100, 2);
					if(!isMatchAudioFormat){
						continue;
					}
					
					audioEntry = new AudioEntry();
					audioEntry.id = cursor.getLong(0);
					// 文件名
					audioEntry.fileName = cursor.getString(1);
					// 歌曲名
					audioEntry.title = cursor.getString(2);
					// 时长
					audioEntry.duration = cursor.getInt(3);
					// 歌手名
					audioEntry.artist = cursor.getString(4);
					// 专辑名
					audioEntry.album = cursor.getString(5);
					// 年代
					audioEntry.year = cursor.getString(6);
					// 歌曲格式
					audioEntry.mime = cursor.getString(7).trim();
					// 文件大小
					audioEntry.size = cursor.getString(8);
					// 文件路径
					audioEntry.fileUrl = fileUrl;
					audios.add(audioEntry);
				} while (cursor.moveToNext());

				cursor.close();

			}
			return audios;
		}
	}
	
	class AudioInfoAdapter extends RecyclerView.Adapter<AudioInfoAdapter.AudioInfoViewHolder>{

		List<AudioEntry> audioList;
		LayoutInflater layoutInflater;
		AudioEntry lastPlayingAudio;
		AudioInfoViewHolder lastPlayingViewHodler;
		
		AudioInfoAdapter(Context context , List<AudioEntry> audioList){
			this.audioList = audioList;
			this.layoutInflater= LayoutInflater.from(context);
		}
		
		@Override
		public int getItemCount() {
			return audioList == null ? 0 :audioList.size();
		}

		@Override
		public void onBindViewHolder(AudioInfoViewHolder viewHolder, int position) {
			AudioEntry audioEntry = audioList.get(position);
			viewHolder.tvArtist.setText(audioEntry.artist);
			viewHolder.tvFileName.setText(audioEntry.fileName);
			viewHolder.playClickListener.audioEntry = audioEntry;
			viewHolder.playClickListener.viewHoler = viewHolder;
			viewHolder.itemClickListener.audioEntry = audioEntry;
			if(audioEntry.isPlaying){
				viewHolder.ivPlay.setImageResource(R.drawable.ic_stop);
			}else{
				viewHolder.ivPlay.setImageResource(R.drawable.ic_play);
			}
		}

		@Override
		public AudioInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			return new AudioInfoViewHolder(layoutInflater.inflate(R.layout.listitem_audio_info, parent, false));
		}
		
		class AudioInfoViewHolder extends RecyclerView.ViewHolder {
			
			TextView tvFileName;
			TextView tvArtist;
			ImageView ivPlay;
			OnPlayClickListener playClickListener;
			OnItemClickListener itemClickListener;
			
			public AudioInfoViewHolder(View view) {
				super(view);
				tvFileName = (TextView) view.findViewById(R.id.tv_file_name);
				tvArtist = (TextView) view.findViewById(R.id.tv_artist);
				ivPlay = (ImageView) view.findViewById(R.id.iv_play);
				playClickListener = new OnPlayClickListener();
				ivPlay.setOnClickListener(playClickListener);
				itemClickListener = new OnItemClickListener();
				view.setOnClickListener(itemClickListener);
			}

		}
		
		 class OnPlayClickListener implements View.OnClickListener{
			
			AudioEntry audioEntry;
			
			AudioInfoViewHolder viewHoler;
			
			void setItemHolder(AudioEntry audio,AudioInfoViewHolder viewHolder){
				this.audioEntry = audio;
				this.viewHoler = viewHolder;
			}
			
			@Override
			public void onClick(View v) {
				if(audioEntry.isPlaying){
					mMisPlayService.pause();
					audioEntry.isPlaying = false;
					viewHoler.ivPlay.setImageResource(R.drawable.ic_play);
				}else{
					Uri audioUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioEntry.id);
					mMisPlayService.play(audioUri);
					audioEntry.isPlaying = true;
					if(lastPlayingAudio != null && lastPlayingViewHodler != null){
						lastPlayingAudio.isPlaying = false;
						lastPlayingViewHodler.ivPlay.setImageResource(R.drawable.ic_play);
					}
					lastPlayingAudio = audioEntry;
					lastPlayingViewHodler = viewHoler;
					viewHoler.ivPlay.setImageResource(R.drawable.ic_stop);
				}
			}
			
		}
		 
		 class OnItemClickListener implements View.OnClickListener{
			AudioEntry audioEntry;
			
			void setItemHolder(AudioEntry audio){
				this.audioEntry = audio;
			}
			
			@Override
			public void onClick(View v) {
				Activity context = AudioChooserActivity.this;
				Intent okData = new Intent();
				okData.putExtra("audio", audioEntry);
				context.setResult(RESULT_OK, okData);
				context.finish();
			}
		 }
	}
	
}
