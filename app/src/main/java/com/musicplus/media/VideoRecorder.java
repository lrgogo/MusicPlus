package com.musicplus.media;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.Activity;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.view.Surface;
import android.view.SurfaceView;

/**
 * video recoder
 * 
 * @author Darcy
 */
public class VideoRecorder {

	private Activity mActivity;
	private MediaRecorder mMediaRecorder;
	private SurfaceView mPreview;
	private int mVidoeWidth,mVideoHeight;
	private Camera mCamera;
	private String mOutputFile;

	private OnVideoRecordListener mListener;
	private volatile boolean isRecording = false;
	private boolean isValidCamera;
	
	private CyclicBarrier mRecordBarrier; //同步背景音乐

	public VideoRecorder(Activity activity , SurfaceView preview, String outputFile, CyclicBarrier recordBarrier) {
		this.mActivity = activity;
		this.mPreview = preview;
		this.mOutputFile = outputFile;
		this.mRecordBarrier = recordBarrier;
	}

	public void setOnVideoRecordListener(OnVideoRecordListener l){
		this.mListener = l;
	}
	
	public void startPreview(){
		try {
			prepareCamera();
			mCamera.startPreview();
			isValidCamera = true;
		} catch (IOException e) {
			e.printStackTrace();
			isValidCamera = false;
		}
	}
	
	public void startRecord() {
		if(isValidCamera && !isRecording){
			isRecording = true;
			new MediaRecordTask().execute();
		}
	}

	class MediaRecordTask extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... voids) {
			if (prepareVideoRecorder()) {
				if(mRecordBarrier != null){
					try {
						mRecordBarrier.await();
						mMediaRecorder.start();
						return true;
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (BrokenBarrierException e) {
						e.printStackTrace();
					}
					return false;
				}else{
					mMediaRecorder.start();
					return true;
				}
			} else {
				isRecording = false;
				release();
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if(result){
				if(mListener!= null)
					mListener.onStarted();
			}
		}
	}

	private void prepareCamera() throws IOException{
		mCamera = Camera.open();
		setCameraDisplayOrientation(mActivity, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
		
		int previewWidth = mPreview.getWidth();
		int previewHeight = mPreview.getHeight();
		Camera.Parameters parameters = mCamera.getParameters();
		List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
		parameters.setPreviewSize(640,480);
		parameters.setRotation(90);
		mCamera.setParameters(parameters);
		mVidoeWidth = 640;
		mVideoHeight = 480;
		
        mCamera.setPreviewDisplay(mPreview.getHolder());
	}
	
	private void setCameraDisplayOrientation(Activity activity,
	         int cameraId, android.hardware.Camera camera) {
	     android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
	     android.hardware.Camera.getCameraInfo(cameraId, info);
	     int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
	     int degrees = 0;
	     switch (rotation) {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }

	     int result;
	     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	         result = (info.orientation + degrees) % 360;
	         result = (360 - result) % 360;  // compensate the mirror
	     } else {  // back-facing
	         result = (info.orientation - degrees + 360) % 360;
	     }
	     camera.setDisplayOrientation(result);
	 }
	
	private boolean prepareVideoRecorder() {
		
		mMediaRecorder = new MediaRecorder();
		
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);
		mMediaRecorder.setOrientationHint(90);
		
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

		mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		mMediaRecorder.setAudioChannels(MediaConstants.AUDIO_CHANNEL);
		mMediaRecorder.setAudioSamplingRate(MediaConstants.AUDIO_SAMPLE_RATE);

		mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mMediaRecorder.setVideoFrameRate(MediaConstants.VIDEO_FRAME_RATE);
		mMediaRecorder.setVideoSize(mVidoeWidth, mVideoHeight);
		mMediaRecorder.setVideoEncodingBitRate(8000000);
		
		mMediaRecorder.setOutputFile(mOutputFile);

		try {
			mMediaRecorder.prepare();
		} catch (IllegalStateException e) {
			release();
			return false;
		} catch (IOException e) {
			release();
			return false;
		}
		return true;
	}

	public void release() {
		if (mMediaRecorder != null) {
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
		
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
		
		isRecording = false;
	}
	
	public interface OnVideoRecordListener{
		void onStarted();
		void onError(int errorCode);
	}
}
