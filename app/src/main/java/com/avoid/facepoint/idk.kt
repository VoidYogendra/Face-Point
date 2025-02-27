package com.avoid.facepoint


import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.Camera
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Environment
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


//20131106: removed unnecessary glFinish(), removed hard-coded "/sdcard"
//20131205: added alpha to EGLConfig
//20131210: demonstrate un-bind and re-bind of texture, for apps with shared EGL contexts
//20140123: correct error checks on glGet*Location() and program creation (they don't set error)
/**
 * Record video from the camera preview and encode it as an MP4 file.  Demonstrates the use
 * of MediaMuxer and MediaCodec with Camera input.  Does not record audio.
 *
 *
 * Generally speaking, it's better to use MediaRecorder for this sort of thing.  This example
 * demonstrates one possible advantage: editing of video as it's being encoded.  A GLES 2.0
 * fragment shader is used to perform a silly color tweak every 15 frames.
 *
 *
 * This uses various features first available in Android "Jellybean" 4.3 (API 18).  There is
 * no equivalent functionality in previous releases.  (You can send the Camera preview to a
 * byte buffer with a fully-specified format, but MediaCodec encoders want different input
 * formats on different devices, and this use case wasn't well exercised in CTS pre-4.3.)
 *
 *
 * The output file will be something like "/sdcard/test.640x480.mp4".
 *
 *
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */
class CameraToMpegTest {
    // encoder / muxer state
    private var mEncoder: MediaCodec? = null
    private var mInputSurface: CodecInputSurface? = null
    private var mMuxer: MediaMuxer? = null
    private var mTrackIndex = 0
    private var mMuxerStarted = false

    // camera state
    private var mCamera: Camera? = null
    private var mStManager: SurfaceTextureManager? = null

    // allocate one of these up front so we don't need to do it every time
    private var mBufferInfo: MediaCodec.BufferInfo? = null

    /** test entry point  */
    @Throws(Throwable::class)
    fun testEncodeCameraToMp4() {
        CameraToMpegWrapper.runTest(this)
    }

    /**
     * Wraps encodeCameraToMpeg().  This is necessary because SurfaceTexture will try to use
     * the looper in the current thread if one exists, and the CTS tests create one on the
     * test thread.
     *
     * The wrapper propagates exceptions thrown by the worker thread back to the caller.
     */
    private class CameraToMpegWrapper private constructor(private val mTest: CameraToMpegTest) :
        Runnable {
        private var mThrowable: Throwable? = null

        override fun run() {
            try {
                mTest.encodeCameraToMpeg()
            } catch (th: Throwable) {
                mThrowable = th
            }
        }

        companion object {
            /** Entry point.  */
            @Throws(Throwable::class)
            fun runTest(obj: CameraToMpegTest) {
                val wrapper = CameraToMpegWrapper(obj)
                val th = Thread(wrapper, "codec test")
                th.start()
                th.join()
                if (wrapper.mThrowable != null) {
                    throw wrapper.mThrowable!!
                }
            }
        }
    }

    /**
     * Tests encoding of AVC video from Camera input.  The output is saved as an MP4 file.
     */
    private fun encodeCameraToMpeg() {
        // arbitrary but popular values
        val encWidth = 640
        val encHeight = 480
        val encBitRate = 6000000 // Mbps
        Log.d(TAG, MIME_TYPE + " output " + encWidth + "x" + encHeight + " @" + encBitRate)

        try {
            prepareCamera(encWidth, encHeight)
            prepareEncoder(encWidth, encHeight, encBitRate)
            mInputSurface!!.makeCurrent()
            prepareSurfaceTexture()

            mCamera!!.startPreview()

            val startWhen = System.nanoTime()
            val desiredEnd = startWhen + DURATION_SEC * 1000000000L
            val st = mStManager!!.surfaceTexture
            var frameCount = 0

            while (System.nanoTime() < desiredEnd) {
                // Feed any pending encoder output into the muxer.
                drainEncoder(false)

                // Switch up the colors every 15 frames.  Besides demonstrating the use of
                // fragment shaders for video editing, this provides a visual indication of
                // the frame rate: if the camera is capturing at 15fps, the colors will change
                // once per second.
                if ((frameCount % 15) == 0) {
                    var fragmentShader: String? = null
                    if ((frameCount and 0x01) != 0) {
                        fragmentShader = SWAPPED_FRAGMENT_SHADER
                    }
                    mStManager!!.changeFragmentShader(fragmentShader)
                }
                frameCount++

                // Acquire a new frame of input, and render it to the Surface.  If we had a
                // GLSurfaceView we could switch EGL contexts and call drawImage() a second
                // time to render it on screen.  The texture can be shared between contexts by
                // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context
                // argument.
                mStManager!!.awaitNewImage()
                mStManager!!.drawImage()

                // Set the presentation time stamp from the SurfaceTexture's time stamp.  This
                // will be used by MediaMuxer to set the PTS in the video.
                if (VERBOSE) {
                    Log.d(
                        TAG, "present: " +
                                ((st!!.timestamp - startWhen) / 1000000.0) + "ms"
                    )
                }
                mInputSurface!!.setPresentationTime(st!!.timestamp)

                // Submit it to the encoder.  The eglSwapBuffers call will block if the input
                // is full, which would be bad if it stayed full until we dequeued an output
                // buffer (which we can't do, since we're stuck here).  So long as we fully drain
                // the encoder before supplying additional input, the system guarantees that we
                // can supply another frame without blocking.
                if (VERBOSE) Log.d(TAG, "sending frame to encoder")
                mInputSurface!!.swapBuffers()
            }

            // send end-of-stream to encoder, and drain remaining output
            drainEncoder(true)
        } finally {
            // release everything we grabbed
            releaseCamera()
            releaseEncoder()
            releaseSurfaceTexture()
        }
    }

    /**
     * Configures Camera for video capture.  Sets mCamera.
     *
     *
     * Opens a Camera and sets parameters.  Does not start preview.
     */
    private fun prepareCamera(encWidth: Int, encHeight: Int) {
        if (mCamera != null) {
            throw RuntimeException("camera already initialized")
        }

        val info = Camera.CameraInfo()

        // Try to find a front-facing camera (e.g. for videoconferencing).
        val numCameras = Camera.getNumberOfCameras()
        for (i in 0 until numCameras) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i)
                break
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default")
            mCamera = Camera.open() // opens first back-facing camera
        }
        if (mCamera == null) {
            throw RuntimeException("Unable to open camera")
        }

        val parms = mCamera!!.parameters

        choosePreviewSize(parms, encWidth, encHeight)
        // leave the frame rate set to default
        mCamera!!.parameters = parms

        val size = parms.previewSize
        Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height)
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private fun releaseCamera() {
        if (VERBOSE) Log.d(TAG, "releasing camera")
        if (mCamera != null) {
            mCamera!!.stopPreview()
            mCamera!!.release()
            mCamera = null
        }
    }

    /**
     * Configures SurfaceTexture for camera preview.  Initializes mStManager, and sets the
     * associated SurfaceTexture as the Camera's "preview texture".
     *
     *
     * Configure the EGL surface that will be used for output before calling here.
     */
    private fun prepareSurfaceTexture() {
        mStManager = SurfaceTextureManager()
        val st = mStManager!!.surfaceTexture
        try {
            mCamera!!.setPreviewTexture(st)
        } catch (ioe: IOException) {
            throw RuntimeException("setPreviewTexture failed", ioe)
        }
    }

    /**
     * Releases the SurfaceTexture.
     */
    private fun releaseSurfaceTexture() {
        if (mStManager != null) {
            mStManager!!.release()
            mStManager = null
        }
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface.  Initializes
     * mEncoder, mMuxer, mInputSurface, mBufferInfo, mTrackIndex, and mMuxerStarted.
     */
    private fun prepareEncoder(width: Int, height: Int, bitRate: Int) {
        mBufferInfo = MediaCodec.BufferInfo()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Log.d(
            TAG,
            "format: $format"
        )

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of CodecInputSurface until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mInputSurface = CodecInputSurface(mEncoder!!.createInputSurface())
        mEncoder!!.start()

        // Output filename.  Ideally this would use Context.getFilesDir() rather than a
        // hard-coded output directory.
        val outputPath = File(
            OUTPUT_DIR,
            "test." + width + "x" + height + ".mp4"
        ).toString()
        Log.i(TAG, "Output file is $outputPath")


        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        try {
            mMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (ioe: IOException) {
            throw RuntimeException("MediaMuxer creation failed", ioe)
        }

        mTrackIndex = -1
        mMuxerStarted = false
    }

    /**
     * Releases encoder resources.
     */
    private fun releaseEncoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        if (mInputSurface != null) {
            mInputSurface!!.release()
            mInputSurface = null
        }
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     *
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     *
     *
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    private fun drainEncoder(endOfStream: Boolean) {
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
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = mEncoder!!.outputFormat
                Log.d(
                    TAG,
                    "encoder output format changed: $newFormat"
                )

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer!!.addTrack(newFormat)
                mMuxer!!.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.w(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException(
                        "encoderOutputBuffer " + encoderStatus +
                                " was null"
                    )

                if ((mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo!!.size = 0
                }

                if (mBufferInfo!!.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
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
                    break // out of while
                }
            }
        }
    }


    /**
     * Holds state associated with a Surface used for MediaCodec encoder input.
     *
     *
     * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses
     * that to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to
     * be sent to the video encoder.
     *
     *
     * This object owns the Surface -- releasing this will release the Surface too.
     */
    private class CodecInputSurface(surface: Surface?) {
        private var mEGLDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var mEGLContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var mEGLSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var mSurface: Surface?

        /**
         * Creates a CodecInputSurface from a Surface.
         */
        init {
            if (surface == null) {
                throw NullPointerException()
            }
            mSurface = surface

            eglSetup()
        }

        /**
         * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
         */
        private fun eglSetup() {
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
                throw RuntimeException("unable to initialize EGL14")
            }

            // Configure EGL for recording and OpenGL ES 2.0.
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(
                mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
            checkEglError("eglCreateContext RGB888+recordable ES2")

            // Configure context for OpenGL ES 2.0.
            val attrib_list = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            mEGLContext = EGL14.eglCreateContext(
                mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0
            )
            checkEglError("eglCreateContext")

            // Create a window surface, and attach it to the Surface we received.
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_NONE
            )
            mEGLSurface = EGL14.eglCreateWindowSurface(
                mEGLDisplay, configs[0], mSurface,
                surfaceAttribs, 0
            )
            checkEglError("eglCreateWindowSurface")
        }

        /**
         * Discards all resources held by this class, notably the EGL context.  Also releases the
         * Surface that was passed to our constructor.
         */
        fun release() {
            if (mEGLDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(mEGLDisplay)
            }
            mSurface!!.release()

            mEGLDisplay = EGL14.EGL_NO_DISPLAY
            mEGLContext = EGL14.EGL_NO_CONTEXT
            mEGLSurface = EGL14.EGL_NO_SURFACE

            mSurface = null
        }

        /**
         * Makes our EGL context and surface current.
         */
        fun makeCurrent() {
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
            checkEglError("eglMakeCurrent")
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         */
        fun swapBuffers(): Boolean {
            val result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
            checkEglError("eglSwapBuffers")
            return result
        }

        /**
         * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
         */
        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
            checkEglError("eglPresentationTimeANDROID")
        }

        /**
         * Checks for EGL errors.  Throws an exception if one is found.
         */
        private fun checkEglError(msg: String) {
            var error: Int
            if ((EGL14.eglGetError().also { error = it }) != EGL14.EGL_SUCCESS) {
                throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
            }
        }

        companion object {
            private const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }


    /**
     * Manages a SurfaceTexture.  Creates SurfaceTexture and TextureRender objects, and provides
     * functions that wait for frames and render them to the current EGL surface.
     *
     *
     * The SurfaceTexture can be passed to Camera.setPreviewTexture() to receive camera output.
     */
    private class SurfaceTextureManager

        : OnFrameAvailableListener {
        /**
         * Returns the SurfaceTexture.
         */
        var surfaceTexture: SurfaceTexture?
            private set
        private var mTextureRender: STextureRender?

        private val mFrameSyncObject = Any() // guards mFrameAvailable
        private var mFrameAvailable = false

        /**
         * Creates instances of TextureRender and SurfaceTexture.
         */
        init {
            mTextureRender = STextureRender()
            mTextureRender!!.surfaceCreated()

            if (VERBOSE) Log.d(TAG, "textureID=" + mTextureRender!!.textureId)
            surfaceTexture = SurfaceTexture(mTextureRender!!.textureId)

            // This doesn't work if this object is created on the thread that CTS started for
            // these test cases.
            //
            // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
            // create a Handler that uses it.  The "frame available" message is delivered
            // there, but since we're not a Looper-based thread we'll never see it.  For
            // this to do anything useful, OutputSurface must be created on a thread without
            // a Looper, so that SurfaceTexture uses the main application Looper instead.
            //
            // Java language note: passing "this" out of a constructor is generally unwise,
            // but we should be able to get away with it here.
            surfaceTexture!!.setOnFrameAvailableListener(this)
        }

        fun release() {
            // this causes a bunch of warnings that appear harmless but might confuse someone:
            //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
            //mSurfaceTexture.release();

            mTextureRender = null
            surfaceTexture = null
        }

        /**
         * Replaces the fragment shader.
         */
        fun changeFragmentShader(fragmentShader: String?) {
            mTextureRender!!.changeFragmentShader(fragmentShader)
        }

        /**
         * Latches the next buffer into the texture.  Must be called from the thread that created
         * the OutputSurface object.
         */
        fun awaitNewImage() {
            val TIMEOUT_MS = 2500

            synchronized(mFrameSyncObject) {
                while (!mFrameAvailable) {
                    try {
                        // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                        // stalling the test if it doesn't arrive.
                        (mFrameSyncObject as Object).wait(TIMEOUT_MS.toLong())
                        if (!mFrameAvailable) {
                            // TODO: if "spurious wakeup", continue while loop
                            throw RuntimeException("Camera frame wait timed out")
                        }
                    } catch (ie: InterruptedException) {
                        // shouldn't happen
                        throw RuntimeException(ie)
                    }
                }
                mFrameAvailable = false
            }

            // Latch the data.
            mTextureRender!!.checkGlError("before updateTexImage")
            surfaceTexture!!.updateTexImage()
        }

        /**
         * Draws the data from SurfaceTexture onto the current EGL surface.
         */
        fun drawImage() {
            mTextureRender!!.drawFrame(surfaceTexture)
        }

        override fun onFrameAvailable(st: SurfaceTexture) {
            if (VERBOSE) Log.d(TAG, "new frame available")
            synchronized(mFrameSyncObject) {
                if (mFrameAvailable) {
                    throw RuntimeException("mFrameAvailable already set, frame could be dropped")
                }
                mFrameAvailable = true
                (mFrameSyncObject as Object).notifyAll()
            }
        }
    }


    /**
     * Code for rendering a texture onto a surface using OpenGL ES 2.0.
     */
    private class STextureRender {
        private val mTriangleVerticesData = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f, 1.0f, 0f, 0f, 1f,
            1.0f, 1.0f, 0f, 1f, 1f,
        )

        private val mTriangleVertices: FloatBuffer

        private val mMVPMatrix = FloatArray(16)
        private val mSTMatrix = FloatArray(16)

        private var mProgram = 0
        var textureId: Int = -12345
            private set
        private var muMVPMatrixHandle = 0
        private var muSTMatrixHandle = 0
        private var maPositionHandle = 0
        private var maTextureHandle = 0

        init {
            mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.size * FLOAT_SIZE_BYTES
            )
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            mTriangleVertices.put(mTriangleVerticesData).position(0)

            Matrix.setIdentityM(mSTMatrix, 0)
        }

        fun drawFrame(st: SurfaceTexture?) {
            checkGlError("onDrawFrame start")
            st!!.getTransformMatrix(mSTMatrix)

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(mProgram)
            checkGlError("glUseProgram")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
            GLES20.glVertexAttribPointer(
                maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices
            )
            checkGlError("glVertexAttribPointer maPosition")
            GLES20.glEnableVertexAttribArray(maPositionHandle)
            checkGlError("glEnableVertexAttribArray maPositionHandle")

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
            GLES20.glVertexAttribPointer(
                maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices
            )
            checkGlError("glVertexAttribPointer maTextureHandle")
            GLES20.glEnableVertexAttribArray(maTextureHandle)
            checkGlError("glEnableVertexAttribArray maTextureHandle")

            Matrix.setIdentityM(mMVPMatrix, 0)
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("glDrawArrays")

            // IMPORTANT: on some devices, if you are sharing the external texture between two
            // contexts, one context may not see updates to the texture unless you un-bind and
            // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
            // texture 0 here.
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        fun surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            if (mProgram == 0) {
                throw RuntimeException("failed creating program")
            }
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
            checkLocation(maPositionHandle, "aPosition")
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
            checkLocation(maTextureHandle, "aTextureCoord")

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
            checkLocation(muMVPMatrixHandle, "uMVPMatrix")
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")
            checkLocation(muSTMatrixHandle, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)

            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            checkGlError("glBindTexture mTextureID")

            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
            checkGlError("glTexParameter")
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        fun changeFragmentShader(fragmentShader: String?) {
            var fragmentShader = fragmentShader
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER
            }
            GLES20.glDeleteProgram(mProgram)
            mProgram = createProgram(VERTEX_SHADER, fragmentShader)
            if (mProgram == 0) {
                throw RuntimeException("failed creating program")
            }
        }

        private fun loadShader(shaderType: Int, source: String?): Int {
            var shader = GLES20.glCreateShader(shaderType)
            checkGlError("glCreateShader type=$shaderType")
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(
                    TAG,
                    "Could not compile shader $shaderType:"
                )
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
            return shader
        }

        private fun createProgram(vertexSource: String, fragmentSource: String?): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            if (vertexShader == 0) {
                return 0
            }
            val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (pixelShader == 0) {
                return 0
            }

            var program = GLES20.glCreateProgram()
            if (program == 0) {
                Log.e(TAG, "Could not create program")
            }
            GLES20.glAttachShader(program, vertexShader)
            checkGlError("glAttachShader")
            GLES20.glAttachShader(program, pixelShader)
            checkGlError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ")
                Log.e(TAG, GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
            return program
        }

        fun checkGlError(op: String) {
            var error: Int
            while ((GLES20.glGetError().also { error = it }) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "$op: glError $error")
                throw RuntimeException("$op: glError $error")
            }
        }

        companion object {
            private const val FLOAT_SIZE_BYTES = 4
            private val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
            private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
            private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3
            private const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n"

            private const val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +  // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n"

            fun checkLocation(location: Int, label: String) {
                if (location < 0) {
                    throw RuntimeException("Unable to locate '$label' in program")
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraToMpegTest"
        private const val VERBOSE = false // lots of logging

        // where to put the output file (note: /sdcard requires WRITE_EXTERNAL_STORAGE permission)
        private val OUTPUT_DIR: File = Environment.getExternalStorageDirectory()

        // parameters for the encoder
        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        private const val FRAME_RATE = 30 // 30fps
        private const val IFRAME_INTERVAL = 5 // 5 seconds between I-frames
        private const val DURATION_SEC: Long = 8 // 8 seconds of video

        // Fragment shader that swaps color channels around.
        private const val SWAPPED_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n" +
                    "}\n"

        /**
         * Attempts to find a preview size that matches the provided width and height (which
         * specify the dimensions of the encoded video).  If it fails to find a match it just
         * uses the default preview size.
         *
         *
         * TODO: should do a best-fit match.
         */
        private fun choosePreviewSize(parms: Camera.Parameters, width: Int, height: Int) {
            // We should make sure that the requested MPEG size is less than the preferred
            // size, and has the same aspect ratio.
            val ppsfv = parms.preferredPreviewSizeForVideo
            if (VERBOSE && ppsfv != null) {
                Log.d(
                    TAG, "Camera preferred preview size for video is " +
                            ppsfv.width + "x" + ppsfv.height
                )
            }

            for (size in parms.supportedPreviewSizes) {
                if (size.width == width && size.height == height) {
                    parms.setPreviewSize(width, height)
                    return
                }
            }

            Log.w(TAG, "Unable to set preview size to " + width + "x" + height)
            if (ppsfv != null) {
                parms.setPreviewSize(ppsfv.width, ppsfv.height)
            }
        }
    }
}