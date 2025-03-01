package com.avoid.facepoint

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.avoid.facepoint.render.Encoder
import com.avoid.facepoint.databinding.MainActivityBinding
import com.avoid.facepoint.model.FilterItem
import com.avoid.facepoint.render.VoidRender
import com.avoid.facepoint.render.VsyncCallBack
import com.avoid.facepoint.ui.ButtonAdapter


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


        val dataSet= arrayOf(
            FilterItem(R.drawable.ic_launcher_background,0,0,0),
            FilterItem(R.drawable.ic_launcher_foreground,1,0,0),
            FilterItem(R.drawable.ic_launcher_background,2,0,0),
            FilterItem(R.drawable.ic_launcher_background,3,0,0),
            FilterItem(R.drawable.ic_launcher_background,4,0,0),
            FilterItem(R.drawable.ic_launcher_background,5,0,0),
            FilterItem(R.drawable.ic_launcher_background,6,0,0),
            FilterItem(R.drawable.ic_launcher_background,7,0,0),
            FilterItem(R.drawable.ic_launcher_background,8,0,0),
        )
        val adapter=ButtonAdapter(dataSet)

        val recyclerView=mainActivityBinding.RvFilterList
        val linearLayout=LinearLayoutManager(context)
        val snap=LinearSnapHelper()

        linearLayout.orientation= RecyclerView.HORIZONTAL
        recyclerView.layoutManager=linearLayout
        snap.attachToRecyclerView(recyclerView)
        recyclerView.adapter=adapter


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
        permissions()
        startCamera()
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
                /**
                 * My FBO Approach Does not Work But this might
                 * */
// TODO: ("https://github.com/MasayukiSuda/GPUVideo-android/blob/ae37d7a2e33e9f8e390752b8db6b9edbced0544f/gpuv/src/main/java/com/daasuu/gpuv/egl/GlFramebufferObject.java#L83")

                val frameCallback = VsyncCallBack { frameTimeNanos ->
                    if (isRecord) {
                        if (!record) {
                            encoder.prepareEncoder(res.height, res.width, EGL14.EGL_NO_CONTEXT)
                            handler.post {
                                encoder.mInputSurface!!.makeCurrent()
                                renderer.onSurfaceCreated2D()
                                renderer.onSurfaceChanged(res.height, res.width)
                            }
                            record = true
                        } else {
                            encoder.drainEncoder(false)
                            handler.post {
//                                Log.e(TAG, "startCamera:x ${GLES31.glIsTexture(renderer.textureID2D)} ID ${renderer.textureID2D}", )
                                renderer.onDraw()
                                encoder.mInputSurface!!.setPresentationTime(frameTimeNanos)
                                encoder.mInputSurface!!.swapBuffers()
                            }
                        }
                        x++
                    }

                    if (!isRecord && record) {
                        record = false
                        encoder.drainEncoder(true)
                        encoder.releaseEncoder()
                        Log.e(TAG, "startCamera: DONE")
                    }
                }

// Start listening for frame updates
                frameCallback.start()

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