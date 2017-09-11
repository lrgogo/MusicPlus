package com.musicplus.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.musicplus.app.MainApplication;
import com.musicplus.utils.DLog;

/**
 * 视频混合音频
 * @author Darcy
 */
public abstract class VideoMuxer {
	
	private static final String TAG = "VideoMuxer";
	
	String mOutputVideo;
	
	private VideoMuxer(String outputVideo){
		this.mOutputVideo = outputVideo;
	}
	
	public final static  VideoMuxer createVideoMuxer(String outputVideo){
		return new Mp4Muxer(outputVideo);
	}
	
	/**
	 * mix raw audio into video
	 * @param videoFile
	 * @param rawAudioFile 
	 * @param includeAudioInVideo
	 */
	public abstract void mixRawAudio(File videoFile,File rawAudioFile,boolean includeAudioInVideo);
	
	/**
	 * use android sdk MediaMuxer
	 * @author Darcy
	 * @version API >= 18
	 */
	private static class Mp4Muxer extends VideoMuxer{

		private final static String AUDIO_MIME = "audio/mp4a-latm";
		private final static long audioBytesPerSample = 44100*16/8;

		private long rawAudioSize;
		
		public Mp4Muxer(String outputVideo) {
			super(outputVideo);
		}

		@Override
		public void mixRawAudio(File videoFile, File rawAudioFile,boolean includeAudioInVideo) {
			final String videoFilePath = videoFile.getAbsolutePath();

			MediaMuxer videoMuxer = null;
			try {
				
				final String outputVideo = mOutputVideo;
				videoMuxer = new MediaMuxer(outputVideo,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
				
				MediaFormat videoFormat = null;
				MediaExtractor videoExtractor = new MediaExtractor();
				videoExtractor.setDataSource(videoFilePath);
				
				for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
					MediaFormat format = videoExtractor.getTrackFormat(i);
					String mime = format.getString(MediaFormat.KEY_MIME);
					if (mime.startsWith("video/")) {
						videoExtractor.selectTrack(i);
						videoFormat = format;
						break;
					}
				}

				int videoTrackIndex = videoMuxer.addTrack(videoFormat);
				int audioTrackIndex = 0;
				
				//extract and decode audio
				FileInputStream fisExtractAudio = null;
				if(includeAudioInVideo){
					AndroidAudioDecoder audioDecoder = new AndroidAudioDecoder(videoFilePath);
					String extractAudioFilePath = MainApplication.RECORD_AUDIO_PATH + "/" + System.currentTimeMillis();
					audioDecoder.decodeToFile(extractAudioFilePath);
					
					File extractAudioFile = new File(extractAudioFilePath);
					fisExtractAudio = new FileInputStream(extractAudioFile);
				}
				FileInputStream fisMixAudio = new FileInputStream(rawAudioFile);
				
				boolean readExtractAudioEOS = includeAudioInVideo ? false : true;
				boolean readMixAudioEOS = false;
				byte[] extractAudioBuffer = new byte[4096];
				byte[] mixAudioBuffer = new byte[4096];
				int extractAudioReadCount = 0;
				int mixAudioReadCount = 0;
				
				final MultiAudioMixer audioMixer = MultiAudioMixer.createAudioMixer();
				final byte[][] twoAudioBytes = new byte[2][];
				
				final MediaCodec audioEncoder = createACCAudioDecoder();
				audioEncoder.start();
				
				ByteBuffer[] audioInputBuffers = audioEncoder.getInputBuffers();
				ByteBuffer[] audioOutputBuffers = audioEncoder.getOutputBuffers();
				boolean sawInputEOS = false;
		        boolean sawOutputEOS = false;
		        long audioTimeUs = 0 ;
				BufferInfo outBufferInfo = new BufferInfo();
		        
				int inputBufIndex, outputBufIndex;
		        while(!sawOutputEOS){
		        	if (!sawInputEOS) {
		        		 inputBufIndex = audioEncoder.dequeueInputBuffer(10000);
					     if (inputBufIndex >= 0) {
					           ByteBuffer inputBuffer = audioInputBuffers[inputBufIndex];
					           inputBuffer.clear();
					           
					           int bufferSize = inputBuffer.remaining();
					           if(bufferSize != extractAudioBuffer.length){
					        	   extractAudioBuffer = new byte[bufferSize];
					        	   mixAudioBuffer = new byte[bufferSize];
					           }
					           
					           if(!readExtractAudioEOS){
					        	   extractAudioReadCount = fisExtractAudio.read(extractAudioBuffer);
					        	   if(extractAudioReadCount == -1){
					        		   readExtractAudioEOS = true;
					        	   }
					           }
					           
					           if(!readMixAudioEOS){
					        	   mixAudioReadCount = fisMixAudio.read(mixAudioBuffer);
					        	   if(mixAudioReadCount == -1){
					        		   readMixAudioEOS = true;
					        	   }
					           }
					           
					           if(readExtractAudioEOS && readMixAudioEOS){
				        		   audioEncoder.queueInputBuffer(inputBufIndex,0 , 0 , 0 ,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					        	   sawInputEOS = true;
					           }else{
						           
						           byte[] mixAudioBytes;
						           if(!readExtractAudioEOS && !readMixAudioEOS){
						        	   if(extractAudioReadCount  == mixAudioReadCount){
						        		   twoAudioBytes[0] = extractAudioBuffer;
								           twoAudioBytes[1] = mixAudioBuffer;
						        	   }else if(extractAudioReadCount > mixAudioReadCount){
						        		   twoAudioBytes[0] = extractAudioBuffer;
						        		   Arrays.fill(mixAudioBuffer, mixAudioReadCount -1, bufferSize, (byte)0);
						        	   }else{
						        		   Arrays.fill(extractAudioBuffer, extractAudioReadCount -1, bufferSize, (byte)0);
						        	   }
						        	   mixAudioBytes = audioMixer.mixRawAudioBytes(twoAudioBytes);
						        	   if(mixAudioBytes == null){
						        		   DLog.e(TAG, "mix audio : null");
						        	   }
						        	   inputBuffer.put(mixAudioBytes);
							           rawAudioSize += mixAudioBytes.length;
							           audioEncoder.queueInputBuffer(inputBufIndex, 0, mixAudioBytes.length, audioTimeUs, 0);
						           }else if(!readExtractAudioEOS && readMixAudioEOS){
						        	   inputBuffer.put(extractAudioBuffer, 0, extractAudioReadCount);
							           rawAudioSize += extractAudioReadCount;
							           audioEncoder.queueInputBuffer(inputBufIndex, 0, extractAudioReadCount, audioTimeUs, 0);
						           }else{
						        	   inputBuffer.put(mixAudioBuffer, 0, mixAudioReadCount);
							           rawAudioSize += mixAudioReadCount;
							           audioEncoder.queueInputBuffer(inputBufIndex, 0, mixAudioReadCount, audioTimeUs, 0);
						           }
						           
						           audioTimeUs = (long) (1000000 * (rawAudioSize / 2.0) / audioBytesPerSample);
					           }
					     }
		        	}
		        	
		        	outputBufIndex = audioEncoder.dequeueOutputBuffer(outBufferInfo, 10000);
		        	if(outputBufIndex >= 0){
		        		
		        		// Simply ignore codec config buffers.
		        		if ((outBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!= 0) {
		                     DLog.i(TAG, "audio encoder: codec config buffer");
		                     audioEncoder.releaseOutputBuffer(outputBufIndex, false);
		                     continue;
		                 }
		        		 
		        		if(outBufferInfo.size != 0){
			        		 ByteBuffer outBuffer = audioOutputBuffers[outputBufIndex];
			        		 outBuffer.position(outBufferInfo.offset);
			        		 outBuffer.limit(outBufferInfo.offset + outBufferInfo.size);
			        		 DLog.i(TAG, String.format(" writing audio sample : size=%s , presentationTimeUs=%s", outBufferInfo.size, outBufferInfo.presentationTimeUs));
			        		 if(lastAudioPresentationTimeUs < outBufferInfo.presentationTimeUs){
			        			 videoMuxer.writeSampleData(audioTrackIndex, outBuffer, outBufferInfo);
				        		 lastAudioPresentationTimeUs = outBufferInfo.presentationTimeUs;
			        		 }else{
			        			 DLog.e(TAG, "error sample! its presentationTimeUs should not lower than before.");
			        		 }
		        		}
		                
		        		audioEncoder.releaseOutputBuffer(outputBufIndex, false);
		                 
		                 if ((outBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					           sawOutputEOS = true;
					     }
		        	}else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
		        		audioOutputBuffers = audioEncoder.getOutputBuffers();
				    } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				        MediaFormat audioFormat = audioEncoder.getOutputFormat();
				        audioTrackIndex = videoMuxer.addTrack(audioFormat);
				        videoMuxer.start(); //start muxer
				    }
		        }
		        
				if(fisExtractAudio != null){
					 fisExtractAudio.close();
				}
		       
		        fisMixAudio.close();
		        audioEncoder.stop();
			    audioEncoder.release();
		        
				//mix video
				boolean videoMuxDone = false;
				// 压缩帧大小 < 原始图片大小
				int videoWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
				int videoHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
				ByteBuffer videoSampleBuffer = ByteBuffer.allocateDirect(videoWidth * videoHeight); 
				BufferInfo videoBufferInfo = new BufferInfo();
				int sampleSize;
				while (!videoMuxDone) {
					videoSampleBuffer.clear();
					sampleSize = videoExtractor.readSampleData(videoSampleBuffer, 0);
					if (sampleSize < 0) {
						videoMuxDone = true;
					} else {
						videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
						videoBufferInfo.flags = videoExtractor.getSampleFlags();
						videoBufferInfo.size = sampleSize;
						videoSampleBuffer.limit(sampleSize);
						videoMuxer.writeSampleData(videoTrackIndex, videoSampleBuffer,videoBufferInfo);
						videoExtractor.advance();
					}
				}
				
				videoExtractor.release();
			} catch (IOException e) {
				e.printStackTrace();
			}finally{
				if(videoMuxer != null){
					videoMuxer.stop();
					videoMuxer.release();
					DLog.i(TAG, "video mix complete.");
				}
			}
		}
		
		private MediaCodec createACCAudioDecoder() throws IOException {
			MediaCodec	codec = MediaCodec.createEncoderByType(AUDIO_MIME);
			MediaFormat format = new MediaFormat();
			format.setString(MediaFormat.KEY_MIME, AUDIO_MIME);
			format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
			format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
			format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
			format.setInteger(MediaFormat.KEY_AAC_PROFILE,
					MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			return codec;
		}

		private long lastAudioPresentationTimeUs = -1;
	}
}
