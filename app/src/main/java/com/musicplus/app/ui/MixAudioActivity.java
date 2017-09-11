package com.musicplus.app.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.musicplus.R;
import com.musicplus.app.MainApplication;
import com.musicplus.app.service.LocalMusicPlayService;
import com.musicplus.app.service.MusicPlayInterface;
import com.musicplus.entry.AudioEntry;
import com.musicplus.media.AudioDecoder;
import com.musicplus.media.AudioEncoder;
import com.musicplus.media.AudioDecoder.OnAudioDecoderListener;
import com.musicplus.media.MultiAudioMixer;
import com.musicplus.media.MultiAudioMixer.OnAudioMixListener;
import com.musicplus.utils.MD5Util;

public class MixAudioActivity extends BaseActivity implements
		View.OnClickListener {

	private final static int REQUEST_CODE_ADD_MUSIC = 0x1;
	private static final int MAX_PROGRESS = 100;

	private Button btnAddMusic;
	private Button btnMix;
	private Button btnPlayMixAudio;
	private LinearLayout containerAudioTracks;
	private ProgressDialog dlgDecoding;
	private ProgressDialog dlgMixing;
	private String mFinalMixAudioFile;

	private Set<AudioEntry> addAudioTracks = new HashSet<AudioEntry>();

	private MusicPlayInterface mMisPlayService;
	
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mix_audio);
		btnAddMusic = findView(R.id.btn_add_music);
		btnMix = findView(R.id.btn_mix_audio);
		containerAudioTracks = findView(R.id.container_musics);
		btnPlayMixAudio = findView(R.id.btn_play_mix_audio);
		btnAddMusic.setOnClickListener(this);
		btnMix.setOnClickListener(this);
		btnPlayMixAudio.setOnClickListener(this);
		
		dlgDecoding = new ProgressDialog(this);
		dlgDecoding.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dlgDecoding.setCancelable(false);
		dlgDecoding.setCanceledOnTouchOutside(false);
		dlgDecoding.setMax(MAX_PROGRESS);
		
		dlgMixing = new ProgressDialog(this);
		dlgMixing.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dlgMixing.setCancelable(false);
		dlgMixing.setCanceledOnTouchOutside(false);
		
		Intent misIntent = new Intent(this,LocalMusicPlayService.class);
		bindService(misIntent, mMisSerciveConn, BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mMisSerciveConn);
	}
	
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_add_music:
			performAddMusic();
			break;
		case R.id.btn_mix_audio:
			performMixAudio();
			break;
		case R.id.btn_play_mix_audio:
			performPlayMixAudio();
			break;
		}
	}

	private void performAddMusic() {
		Intent addMusicIntent = new Intent(this, AudioChooserActivity.class);
		startActivityForResult(addMusicIntent, REQUEST_CODE_ADD_MUSIC);
	}

	private void performMixAudio() {
		if(!addAudioTracks.isEmpty()){
			dlgMixing.setMessage(getString(R.string.please_wait));
			dlgMixing.show();
			new MixAudioTask().execute();
		}
	}

	private void performPlayMixAudio(){
		
		if(mFinalMixAudioFile == null)
			return;
		
		File accFile = new File(mFinalMixAudioFile);
		if(accFile.exists()){
			mMisPlayService.play(Uri.fromFile(accFile));
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CODE_ADD_MUSIC && resultCode == RESULT_OK) {
			dlgDecoding.show();
			AudioEntry audioEntry = (AudioEntry) data.getSerializableExtra("audio");
			new DecodeAudioTask(audioEntry).execute();
		}
	}

	class MixAudioTask extends AsyncTask<Void, Double, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {
			int audioSize = addAudioTracks.size();
			
			boolean isSingleAudio = false;
			String rawAudioFile = null;
			if(audioSize == 1){
				isSingleAudio = true;
				rawAudioFile = addAudioTracks.iterator().next().fileUrl;
			}
			
			if(!isSingleAudio){
				File[] rawAudioFiles = new File[audioSize];
				StringBuilder sbMix = new StringBuilder();
				int index = 0;
				for (AudioEntry audioEntry : addAudioTracks) {
					rawAudioFiles[index++] = new File(audioEntry.fileUrl);
					sbMix.append(audioEntry.id);
				}
				final String mixFilePath = MainApplication.TEMP_AUDIO_PATH + "/"
						+ MD5Util.getMD5Str(sbMix.toString());

				try {
					MultiAudioMixer audioMixer = MultiAudioMixer.createAudioMixer();

					audioMixer.setOnAudioMixListener(new OnAudioMixListener() {

						FileOutputStream fosRawMixAudio = new FileOutputStream(mixFilePath);

						@Override
						public void onMixing(byte[] mixBytes) throws IOException {
							fosRawMixAudio.write(mixBytes);
						}

						@Override
						public void onMixError(int errorCode) {
							try {
								if(fosRawMixAudio != null)
									fosRawMixAudio.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

						@Override
						public void onMixComplete() {
							try {
								if(fosRawMixAudio != null)
									fosRawMixAudio.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

					});
					audioMixer.mixAudios(rawAudioFiles);
					rawAudioFile = mixFilePath;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
			AudioEncoder accEncoder =  AudioEncoder.createAccEncoder(rawAudioFile);
			String finalMixPath = MainApplication.RECORD_AUDIO_PATH + "/MixAudioTest.acc";
			accEncoder.encodeToFile(finalMixPath);
			mFinalMixAudioFile = finalMixPath;
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			Toast.makeText(MixAudioActivity.this, "混音成功，并编码成ACC格式", Toast.LENGTH_SHORT).show();
			dlgMixing.cancel();
		}
	}

	class DecodeAudioTask extends AsyncTask<Void, Double, Boolean> {

		AudioEntry decAudio;

		DecodeAudioTask(AudioEntry decAudio) {
			this.decAudio = decAudio;
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			String decodeFilePath = MainApplication.TEMP_AUDIO_PATH + "/"
					+ MD5Util.getMD5Str(decAudio.fileUrl);
			File decodeFile = new File(decodeFilePath);
			if (decodeFile.exists()) {
				publishProgress(1.0);
				decAudio.fileUrl = decodeFilePath;
				return true;
			}

			if (decAudio.mime.contains("x-ms-wma")) {
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
					while ((readCount = fisWavFile.read(rawBuf)) != -1) {
						fosRawAudioFile.write(rawBuf, 0, readCount);
						totalReadCount += readCount;
						publishProgress(totalReadCount / audioFileSize);
					}
					publishProgress(1.0);
					return true;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if (fisWavFile != null)
							fisWavFile.close();

						if (fosRawAudioFile != null)
							fosRawAudioFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			} else {
				AudioDecoder audioDec = AudioDecoder.createDefualtDecoder(decAudio.fileUrl);
				try {
					decAudio.fileUrl = decodeFilePath;
					audioDec.setOnAudioDecoderListener(new OnAudioDecoderListener() {
						@Override
						public void onDecode(byte[] decodedBytes,
								double progress) throws IOException {
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
			if (result) {
				addMisicTrack(decAudio);
			}
			dlgDecoding.cancel();
		}

		private void addMisicTrack(final AudioEntry decAudio) {
			if (addAudioTracks.contains(decAudio))
				return;

			addAudioTracks.add(decAudio);
			final View viewTrack = View.inflate(MixAudioActivity.this,
					R.layout.listitem_audio_info, null);
			TextView tvName = (TextView) viewTrack
					.findViewById(R.id.tv_file_name);
			TextView tvArtist = (TextView) viewTrack
					.findViewById(R.id.tv_artist);
			ImageView tvDel = (ImageView) viewTrack.findViewById(R.id.iv_play);
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
}
