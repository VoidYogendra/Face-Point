/*

* Copyright 2025 VoidYogendra
        *
        * Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* ```
http://www.apache.org/licenses/LICENSE-2.0
```
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
        * limitations under the License.
*
* Project: Face Point
* Repository: [https://github.com/VoidYogendra/Face-Point](https://github.com/VoidYogendra/Face-Point)
*/

package com.avoid.facepoint.render.encoder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.opengl.EGLContext
import android.os.Bundle
import android.util.Log
import com.avoid.facepoint.render.CodecInputSurface
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class Encoder {
    companion object {
        private const val TAG = "EncoderNew"
        private const val VERBOSE = false

        // Video params
        private const val VIDEO_MIME = "video/avc"
        private const val IFRAME_INTERVAL = 3
        private const val BPP = 0.25f

        // Audio params
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE = 44100
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BITRATE = 128_000
    }

    // Video
    private var mWidth = -1
    private var mHeight = -1
    private var mVideoEncoder: MediaCodec? = null
    var mInputSurface: CodecInputSurface? = null // keep public for your GL/EGL swap calls

    // Audio
    private var mAudioEncoder: MediaCodec? = null
    private var mAudioRecord: AudioRecord? = null

    // Muxer
    private var mMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    // Flags & locks
    private val muxerLock = Object()
    private var videoTrackAdded = false
    private var audioTrackAdded = false

    // Threads
    private var videoDrainThread: Thread? = null
    private var audioThread: Thread? = null

    // Control
    private val isRecording = AtomicBoolean(false)
    private val isAudioCapturing = AtomicBoolean(false)
    private val videoDrainRunning = AtomicBoolean(false)
    private val audioDrainRunning = AtomicBoolean(false)

    // BufferInfo objects
    private val videoBufferInfo = MediaCodec.BufferInfo()
    private val audioBufferInfo = MediaCodec.BufferInfo()

    /**
     * Prepare both encoders and the muxer.
     *
     * - frameRate: video fps
     * - width/height: video resolution (encoder input surface size)
     * - eglContext: current EGLContext to attach CodecInputSurface
     * - glVersion: GLES version used by CodecInputSurface
     * - outputPath: final mp4 path (must be writable)
     */
    @Synchronized
    fun prepareEncoder(
        frameRate: Int,
        width: Int,
        height: Int,
        eglContext: EGLContext,
        glVersion: Int,
        outputPath: String
    ) {
        mWidth = width
        mHeight = height

        // VIDEO encoder (surface)
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, width, height)
        val bitrate = (BPP * frameRate * width * height).toInt()
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

        if (VERBOSE) Log.d(TAG, "video format: $videoFormat")
        mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME)
        mVideoEncoder!!.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create input surface wrapper for EGL rendering
        mInputSurface = CodecInputSurface(mVideoEncoder!!.createInputSurface(), eglContext, glVersion)

        mVideoEncoder!!.start()

        // Create muxer
        try {
            mMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (ioe: IOException) {
            throw RuntimeException("MediaMuxer creation failed", ioe)
        }

        // AUDIO encoder (AAC)
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, AUDIO_CHANNEL_COUNT)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME)
        mAudioEncoder!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mAudioEncoder!!.start()

        // reset flags
        videoTrackAdded = false
        audioTrackAdded = false
        muxerStarted = false
    }

    /**
     * Call after prepareEncoder to begin threads.
     * The GL/EGL thread must call mInputSurface!!.makeCurrent() and then render frames,
     * and call mInputSurface!!.setPresentationTime(...) and swapBuffers() for each frame.
     */
    fun startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return
        }
        isRecording.set(true)

        startVideoDrainThread()
        startAudioThread()
    }

    fun requestKeyFrame() {
        val params = Bundle()
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        mVideoEncoder?.setParameters(params)
    }

    /**
     * Signal stop: tells encoders to finish, waits for threads, releases resources.
     */
    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)

        // For video (surface), signal end of stream to encoder so it outputs EOS.
        try {
            mVideoEncoder?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(TAG, "signalEndOfInputStream failed: ${e.message}")
        }

        // For audio, stop capturing. We'll queue EOS via queueInputBuffer in audio thread.
        isAudioCapturing.set(false)

        // wait for threads to finish
        videoDrainThread?.join(5000)
        audioThread?.join(5000)

        // Release everything
        release()
    }

    private fun startVideoDrainThread() {
        videoDrainRunning.set(true)
        videoDrainThread = Thread {
            try {
                while (videoDrainRunning.get()) {
                    val TIMEOUT_USEC = 10000L
                    val encoder = mVideoEncoder ?: break
                    val status = encoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_USEC)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // nothing yet
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = encoder.outputFormat
                        synchronized(muxerLock) {
                            if (!videoTrackAdded) {
                                videoTrackIndex = mMuxer!!.addTrack(newFormat)
                                videoTrackAdded = true
                                Log.d(TAG, "Video track added: $videoTrackIndex")
                            }
                            // start muxer only when both tracks are added
                            if (audioTrackAdded && !muxerStarted) {
                                mMuxer!!.start()
                                muxerStarted = true
                                Log.d(TAG, "Muxer started (video thread)")
                            }
                        }
                    } else if (status >= 0) {
                        val encodedData: ByteBuffer? = encoder.outputBuffers[status]
                        if (encodedData != null && videoBufferInfo.size > 0) {
                            if (!muxerStarted) {
                                // can't write until muxer started
                                Log.v(TAG, "Video - muxer not started, dropping until start")
                            } else {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                synchronized(muxerLock) {
                                    mMuxer!!.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                                }
                            }
                        }

                        encoder.releaseOutputBuffer(status, false)

                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "Video EOS reached")
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Video drain thread error", t)
            } finally {
                videoDrainRunning.set(false)
                Log.d(TAG, "Video drain thread exiting")
            }
        }
        videoDrainThread!!.name = "Encoder-Video-Drain"
        videoDrainThread!!.start()
    }

    @SuppressLint("MissingPermission")
    private fun startAudioThread() {
        audioDrainRunning.set(true)
        isAudioCapturing.set(true)
        audioThread = Thread {
            var audioRecord: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    throw RuntimeException("AudioRecord initialization failed")
                }

                audioRecord.startRecording()
                mAudioRecord = audioRecord

                val audioEncoder = mAudioEncoder ?: throw RuntimeException("Audio encoder null")
                val inputBuffers = audioEncoder.inputBuffers
                val outputBuffers = audioEncoder.outputBuffers

                val pcmBuffer = ByteArray(minBuf)

                // Loop: read mic -> queue to encoder input -> drain encoder output
                var sawInputEOS = false
                while (isAudioCapturing.get() || !sawInputEOS) {
                    // Read mic
                    val read = if (isAudioCapturing.get()) audioRecord.read(pcmBuffer, 0, pcmBuffer.size) else 0

                    if (read > 0) {
                        val inIndex = audioEncoder.dequeueInputBuffer(10000)
                        if (inIndex >= 0) {
                            val inputBuffer = inputBuffers[inIndex]
                            inputBuffer.clear()
                            inputBuffer.put(pcmBuffer, 0, read)
                            val pts = System.nanoTime() / 1000L
                            audioEncoder.queueInputBuffer(inIndex, 0, read, pts, 0)
                        }
                    } else if (!isAudioCapturing.get()) {
                        // queue EOS
                        val inIndex = audioEncoder.dequeueInputBuffer(10000)
                        if (inIndex >= 0) {
                            audioEncoder.queueInputBuffer(inIndex, 0, 0, System.nanoTime() / 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        }
                    }

                    // Drain audio encoder output
                    var encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 10000)
                    while (encoderStatus >= 0) {
                        val encodedData = outputBuffers[encoderStatus]
                        if (encodedData != null && audioBufferInfo.size > 0) {
                            if (!muxerStarted) {
                                // If video hasn't added track yet, we must add audio track then start when ready
                                // But add track is handled in format-changed below
                                Log.v(TAG, "Audio - muxer not started yet")
                            } else {
                                encodedData.position(audioBufferInfo.offset)
                                encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                synchronized(muxerLock) {
                                    mMuxer!!.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
                                }
                            }
                        }
                        audioEncoder.releaseOutputBuffer(encoderStatus, false)
                        if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "Audio EOS reached")
                            break
                        }
                        encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0)
                    }

                    if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // outputBuffers updated, refresh
                        Log.v(TAG, "Audio INFO_OUTPUT_BUFFERS_CHANGED")
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = audioEncoder.outputFormat
                        synchronized(muxerLock) {
                            if (!audioTrackAdded) {
                                audioTrackIndex = mMuxer!!.addTrack(newFormat)
                                audioTrackAdded = true
                                Log.d(TAG, "Audio track added: $audioTrackIndex")
                            }
                            if (videoTrackAdded && !muxerStarted) {
                                mMuxer!!.start()
                                muxerStarted = true
                                Log.d(TAG, "Muxer started (audio thread)")
                            }
                        }
                    }
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Audio thread error", t)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {}
                try {
                    audioRecord?.release()
                } catch (e: Exception) {}
                audioDrainRunning.set(false)
                Log.d(TAG, "Audio thread exiting")
            }
        }
        audioThread!!.name = "Encoder-Audio-Thread"
        audioThread!!.start()
    }

    /**
     * Call when you want to set presentation time & swap buffers for video frames (optional helper).
     * You can still call mInputSurface!!.setPresentationTime(...) and swapBuffers() yourself.
     */
    fun setPresentationTimeAndSwap(presentationTimeNs: Long) {
        mInputSurface?.setPresentationTime(presentationTimeNs)
        mInputSurface?.swapBuffers()
    }

    /**
     * Release encoders and muxer. Called from stopRecording()
     */
    private fun release() {
        Log.d(TAG, "Releasing encoder resources")

        try {
            mVideoEncoder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "video encoder stop failed: ${e.message}")
        }
        try {
            mVideoEncoder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "video encoder release failed: ${e.message}")
        }
        mVideoEncoder = null

        try {
            mAudioEncoder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "audio encoder stop failed: ${e.message}")
        }
        try {
            mAudioEncoder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "audio encoder release failed: ${e.message}")
        }
        mAudioEncoder = null

        try {
            mInputSurface?.release()
        } catch (e: Exception) {
            Log.w(TAG, "input surface release failed: ${e.message}")
        }
        mInputSurface = null

        try {
            synchronized(muxerLock) {
                if (muxerStarted) {
                    mMuxer?.stop()
                }
                mMuxer?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "muxer release failed: ${e.message}")
        }
        mMuxer = null
        muxerStarted = false
        videoTrackAdded = false
        audioTrackAdded = false

        Log.d(TAG, "Released")
    }
}
