package com.avoid.facepoint

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.MotionEvent
import android.view.Surface
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.avoid.facepoint.databinding.MainActivityBinding
import com.avoid.facepoint.model.FilterItem
import com.avoid.facepoint.model.FilterTypes
import com.avoid.facepoint.render.Encoder
import com.avoid.facepoint.render.VoidRender
import com.avoid.facepoint.ui.ButtonAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch


class MainActivity : AppCompatActivity() {
    private lateinit var glSurface: GLSurfaceView
    private lateinit var renderer: VoidRender
    private lateinit var context: Context
    private lateinit var mainActivityBinding: MainActivityBinding
    private var isRecord = false
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var rotation = 0
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
        glSurface.setEGLContextClientVersion(GL_VERSION)

        renderer = VoidRender(context)
        glSurface.setRenderer(renderer)


        val dataSet = arrayOf(
            FilterItem(R.drawable.ic_launcher_background, FilterTypes.DEFAULT, renderer, null),
            FilterItem(R.drawable.ic_launcher_background, FilterTypes.GRAIN, renderer, null),
            FilterItem(
                R.drawable.ic_launcher_background,
                FilterTypes.LUT,
                renderer,
                "lut/b&w.cube"
            ),
            FilterItem(
                R.drawable.ic_launcher_background,
                FilterTypes.LUT,
                renderer,
                "lut/CineStill.cube"
            ),
            FilterItem(
                R.drawable.ic_launcher_background,
                FilterTypes.LUT,
                renderer,
                "lut/Sunset.cube"
            ),
            FilterItem(
                R.drawable.ic_launcher_background,
                FilterTypes.LUT,
                renderer,
                "lut/Sunset2.cube"
            ),
            FilterItem(
                R.drawable.ic_launcher_background,
                FilterTypes.LUT,
                renderer,
                "lut/BW1.cube"
            ),
        )
        val adapter = ButtonAdapter(dataSet)

        val recyclerView = mainActivityBinding.RvFilterList
        val linearLayout = LinearLayoutManager(context)
        val snap = PagerSnapHelper()

        linearLayout.orientation = RecyclerView.HORIZONTAL
        recyclerView.layoutManager = linearLayout
        recyclerView.adapter = adapter
        snap.attachToRecyclerView(recyclerView)
        var item = 0
        val linearLayoutManager = recyclerView.layoutManager!! as LinearLayoutManager
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount: Int = linearLayoutManager.childCount
                val firstVisibleItemPosition: Int =
                    linearLayoutManager.findFirstVisibleItemPosition()
                val lastItem = firstVisibleItemPosition + visibleItemCount
                if (lastItem != item) {
                    Log.e(TAG, "onScrolled: $lastItem")
                    val filter = adapter.dataSet[lastItem - 1]
                    val render = filter.render
                    render.onDrawCallback.add {
                        render.filterTypes = filter.filterTypes

                        when (filter.filterTypes) {
                            FilterTypes.DEFAULT -> {
                                render.deleteCurrentProgram()

                                render.createExternalTexture()
                                render.resize(render.cameraWidth, render.cameraHeight)
                            }

                            FilterTypes.LUT -> {

                                render.deleteCurrentProgram()

                                render.loadLUT(filter.lutFileName!!)
                                render.createExternalTextureLUT()
                                render.resize(render.cameraWidth, render.cameraHeight)
                            }

                            FilterTypes.GRAIN -> {
                                render.deleteCurrentProgram()

                                render.createExternalTextureGRAIN()
                                render.resize(render.cameraWidth, render.cameraHeight)
                            }
                        }
                        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                            render.rotate(false)
                        } else {
                            render.rotate(true)
                        }
                    }

                    item = lastItem
                }
            }
        })



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
        var cameraProvider = cameraProviderFuture.get()
        cameraProviderFuture.addListener({

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY) // Prefer 16:9, but fallback if needed
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(rotation)
                .setTargetFrameRate(Range(30, 60))
                .build()


            val surfaceProvider = Preview.SurfaceProvider { request ->
                val surfaceTexture = renderer.getSurfaceTexture()
                val surface = Surface(surfaceTexture)
                val res = preview.resolutionInfo!!.resolution
                renderer.resize(res.width, res.height)
                Log.e(TAG, "startCamera: ${preview.resolutionInfo!!.resolution}")
                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) { _ ->
                    // Handle resource release
                }
            }
            preview.surfaceProvider = surfaceProvider


            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            mainActivityBinding.BtnSwitchCamera.setOnClickListener {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    renderer.rotate(true)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    renderer.rotate(false)
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            }
        }, ContextCompat.getMainExecutor(this))


        val encoder = Encoder()
        var record = false

        /**
         * My FBO Approach Does not Work But this might
         * */
// TODO: ("https://github.com/MasayukiSuda/GPUVideo-android/blob/ae37d7a2e33e9f8e390752b8db6b9edbced0544f/gpuv/src/main/java/com/daasuu/gpuv/egl/GlFramebufferObject.java#L83")

        CoroutineScope(Dispatchers.IO).launch {
            val fps = 60
            val frameInterval = 1000L / fps   // 33ms for ~30FPS
            Log.e(TAG, "startCamera: Interval $frameInterval")
            val videoRender = VoidRender(context)
            while (true) {
                val frameTimeNanos = System.nanoTime()
                if (isRecord) {
                    if (!record) {
                        encoder.prepareEncoder(
                            fps, renderer.cameraHeight, renderer.cameraWidth, renderer.eglContext!!,
                            GL_VERSION
                        )
                        glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        handler.post {
                            encoder.mInputSurface!!.makeCurrent()
                            videoRender.onSurfaceCreated2D()
                            videoRender.onSurfaceChanged(
                                renderer.cameraHeight,
                                renderer.cameraWidth
                            )
                        }
                        record = true
                    } else {
                        glSurface.requestRender()
                        val latch = CountDownLatch(1)
                        glSurface.queueEvent {
                            handler.post {
                                encoder.drainEncoder(false)
                                if (record) {
                                    videoRender.onDraw(renderer.textureID2D)
                                    encoder.mInputSurface!!.setPresentationTime(frameTimeNanos)
                                    encoder.mInputSurface!!.swapBuffers()
                                }
                                latch.countDown()
                            }
                        }
                        latch.await()
                    }
                }

                if (!isRecord && record) {
                    record = false
                    handler.post {

                        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        encoder.drainEncoder(true)
                        encoder.releaseEncoder()
                        Log.e(TAG, "startCamera: DONE")
                    }
                }
                delay(frameInterval) // Wait for the next frame (instead of busy looping)
            }
        }

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

    override fun onDestroy() {
        super.onDestroy()
        destroy()
    }

    companion object {
        internal const val TAG = "MainActivity"
        private external fun destroy()
        const val GL_VERSION = 3

        init {
            System.loadLibrary("facepoint")
        }
    }
}