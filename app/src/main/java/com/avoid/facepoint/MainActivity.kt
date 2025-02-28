package com.avoid.facepoint

import android.Manifest
import android.R.attr.height
import android.R.attr.width
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.Void.gifencoder.Recorder.Encoder
import com.avoid.facepoint.databinding.MainActivityBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var glSurface: GLSurfaceView
    private lateinit var renderer: VoidRender
    private lateinit var context: Context
    private lateinit var mainActivityBinding: MainActivityBinding
    private var isRecord = false
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    private val requestStoragePermissionLauncherBeforeR = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied
            Toast.makeText(this, "Not Granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        setContentView(mainActivityBinding.root)
        init()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        context = this
        glSurface = mainActivityBinding.GLSMainRender
        glSurface.setEGLContextClientVersion(3)

        renderer = VoidRender(context)
        glSurface.setRenderer(renderer)
        permissions()
        startCamera()
        mainActivityBinding.BtnRec.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                val btn = view as ImageButton
                btn.setColorFilter(Color.RED)
                isRecord = true
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                val btn = view as ImageButton
                btn.setColorFilter(Color.WHITE)
                isRecord = false
            }
            false
        }
    }

    private val handlerThread = HandlerThread("TEST").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY) // Prefer 16:9, but fallback if needed
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()


            val surfaceProvider = Preview.SurfaceProvider { request ->
                val surfaceTexture = renderer.getSurfaceTexture()
                val surface = Surface(surfaceTexture)
                val res = preview.resolutionInfo!!.resolution


                val encoder = Encoder()
                var record = false
                var x = 0
//                val boi = BitmapFactory.decodeStream(context.assets.open("deku.jpeg"))
                CoroutineScope(Dispatchers.IO).launch {
                    var lastFrameTime = 0L
                    val frameInterval = 33_333_333L
                    while (true) {
                        val frameTimeNanos = System.nanoTime()

                        if (isRecord) {
                            val tempt = x
                            if (!record) {
                                // TODO: implement pass fbo and fix rotation
                                encoder.prepareEncoder(
                                    res.height ,
                                    res.width ,
                                    EGL14.EGL_NO_CONTEXT
                                )
                                handler.post {
                                    encoder.mInputSurface!!.makeCurrent()
                                    renderer.onSurfaceCreated2D()
                                    //since camera will return in rotated res ie if portrait res is w 1080 h 1920 it will come as w 1920 h 1080
                                    renderer.onSurfaceChanged(res.height, res.width)
                                    Log.e(TAG, "startCamera: INIT  render ${renderer.width} ${renderer.height}  res ${res.width} ${res.height}")
                                }
                                record = true
                            } else {
                                if (frameTimeNanos - lastFrameTime >= frameInterval) {
                                    lastFrameTime = frameTimeNanos
                                    encoder.drainEncoder(false)
                                    handler.post {

//                                        Log.e(TAG, "startCamera: WTF")
//                                        Log.e(TAG, "startCamera: ${renderer.textureID2D} ${GLES31.glIsTexture(renderer.textureID2D)}", )
//                                        encoder.generateSurfaceFrame(tempt)
                                        renderer.onDraw()


                                        encoder.mInputSurface!!.setPresentationTime(
                                            frameTimeNanos
                                        )
                                        encoder.mInputSurface!!.swapBuffers()
                                    }
                                }
                                x++

                            }
                        }
                        if (!isRecord && record) {
                            record = false
                            encoder.drainEncoder(true)
                            encoder.releaseEncoder()
                            Log.e(TAG, "startCamera: DONE")
                        }
                    }
                }

                renderer.resize(res.width, res.height)
                Log.e(TAG, "startCamera: ${preview.resolutionInfo!!.resolution}")
                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) { _ ->
                    // Handle resource release
                }
            }
            preview.surfaceProvider = surfaceProvider

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)
        }, ContextCompat.getMainExecutor(this))

    }

    private fun permissions() {
        checkPermission(Manifest.permission.CAMERA, requestCameraPermissionLauncher)

        checkPermission(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            requestStoragePermissionLauncherBeforeR
        )
        checkPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            requestStoragePermissionLauncherBeforeR
        )
    }

    private fun checkPermission(permission: String, launcher: ActivityResultLauncher<String>) {
        launcher.launch(permission)
    }

    companion object {
        private external fun nativeInit()
        private external fun nativeInitTest(wow: String): String
        internal const val TAG = "MainActivity"

        init {
            System.loadLibrary("facepoint")
            nativeInit()
            Log.e(TAG, ":XYZ ${nativeInitTest("OYYYY")}")
        }
    }
}