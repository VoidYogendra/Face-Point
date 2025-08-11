package com.avoid.facepoint.render.encoder


import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGLContext
import android.util.Log
import com.avoid.facepoint.render.CodecInputSurface
import java.io.IOException


class Encoder {

    private var mWidth = -1
    private var mHeight = -1
    private var mEncoder: MediaCodec? = null
    var mInputSurface: CodecInputSurface? = null
    private var mMuxer: MediaMuxer? = null
    private var mTrackIndex = 0
    private var mMuxerStarted = false
    private var mBufferInfo: MediaCodec.BufferInfo? = null

    fun prepareEncoder(frameRate:Int, width: Int, height: Int, eglContext: EGLContext,glVersion:Int,outputPath:String) {
        mWidth=width
        mHeight=height

        val bitrate =(BPP * frameRate * width * height).toInt()
        mBufferInfo = MediaCodec.BufferInfo()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)


        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Log.d(
            TAG,
            "format: $format"
        )


        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mInputSurface = CodecInputSurface(mEncoder!!.createInputSurface(), eglContext,glVersion)
        mEncoder!!.start()

        Log.d(TAG, "output file is $outputPath")


        try {
            mMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (ioe: IOException) {
            throw RuntimeException("MediaMuxer creation failed", ioe)
        }

        mTrackIndex = -1
        mMuxerStarted = false
    }


    fun releaseEncoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        if (mInputSurface != null) {
            mInputSurface!!.release()
            mInputSurface = null
        }
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
    }

    fun drainEncoder(endOfStream: Boolean) {
        val TIMEOUT_USEC = 10000
        if (VERBOSE) Log.d(
            TAG,
            "drainEncoder($endOfStream)"
        )

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder")
            mEncoder!!.signalEndOfInputStream()
        }

        var encoderOutputBuffers = mEncoder!!.outputBuffers
        while (true) {
            val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {

                if (!endOfStream) {
                    break
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                encoderOutputBuffers = mEncoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = mEncoder!!.outputFormat
                Log.d(
                    TAG,
                    "encoder output format changed: $newFormat"
                )


                mTrackIndex = mMuxer!!.addTrack(newFormat)
                mMuxer!!.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.w(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )

            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException(
                        "encoderOutputBuffer " + encoderStatus +
                                " was null"
                    )

                if ((mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {


                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo!!.size = 0
                }

                if (mBufferInfo!!.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }


                    encodedData.position(mBufferInfo!!.offset)
                    encodedData.limit(mBufferInfo!!.offset + mBufferInfo!!.size)

                    mMuxer!!.writeSampleData(mTrackIndex, encodedData, mBufferInfo!!)
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo!!.size + " bytes to muxer")
                }

                mEncoder!!.releaseOutputBuffer(encoderStatus, false)

                if ((mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached")
                    }
                    break
                }
            }
        }
    }

    companion object {
        private const val TAG = "EncodeAndMuxTest"
        private const val VERBOSE = false

        private const val MIME_TYPE = "video/avc"
        private const val IFRAME_INTERVAL = 3
        private const val BPP = 0.25f
    }
}