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


package com.avoid.facepoint

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.util.Range
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.avoid.facepoint.databinding.MainActivityBinding
import com.avoid.facepoint.model.FilterItem
import com.avoid.facepoint.model.FilterTypes
import com.avoid.facepoint.render.GLTextureManager
import com.avoid.facepoint.render.GPUFilter
import com.avoid.facepoint.render.VoidRender
import com.avoid.facepoint.render.egl.EglCore
import com.avoid.facepoint.render.egl.OffscreenSurface
import com.avoid.facepoint.render.encoder.Encoder
import com.avoid.facepoint.render.mpfilters.BulgeFilter
import com.avoid.facepoint.render.mpfilters.BulgeFilterEyes
import com.avoid.facepoint.render.mpfilters.Default2DFilter
import com.avoid.facepoint.render.mpfilters.DefaultOESFilter
import com.avoid.facepoint.render.mpfilters.EyeMouthMaskFilter
import com.avoid.facepoint.render.mpfilters.InverseOESFilter
import com.avoid.facepoint.render.mpfilters.LutOESFilter
import com.avoid.facepoint.ui.ButtonAdapter
import com.google.mediapipe.examples.facelandmarker.FaceLandmarkerHelper
import com.google.mediapipe.examples.facelandmarker.FaceLandmarkerHelper.Companion.DELEGATE_GPU
import com.google.mediapipe.examples.facelandmarker.FaceLandmarkerHelper.LandmarkerListener
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {
    private lateinit var glSurface: GLSurfaceView
    private lateinit var renderer: VoidRender
    private lateinit var context: Context
    private lateinit var mainActivityBinding: MainActivityBinding
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var rotation = 0
    private var isGrantedCam = false
    private var isGrantedStorage = false
    private var cameraProvider: ProcessCameraProvider? = null

    var lastItem = 0
    var currentItem = 0

    private val handlerThread = HandlerThread("TEST").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY) // Prefer 16:9, but fallback if needed
        .build()

    private val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .setTargetRotation(rotation)
        .setTargetFrameRate(Range(30, 60))
        .build()

    private val requestMultiplePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { request ->
        isGrantedCam = request[Manifest.permission.CAMERA] == true
        isGrantedStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else
            request[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true || request[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", this@MainActivity.packageName, null)
                }
                requestPermission.launch(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                requestPermission.launch(intent)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult ->
        if (Environment.isExternalStorageManager()) {
            //Manage External Storage Permissions Granted
            isGrantedStorage = true
            Log.d(TAG, "onActivityResult: Manage External Storage Permissions Granted")
        } else {
            isGrantedStorage = false
            Toast.makeText(this@MainActivity, "Storage Permissions Denied", Toast.LENGTH_SHORT)
                .show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        setContentView(mainActivityBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mainActivityBinding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        init()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        context = this
        glSurface = mainActivityBinding.GLSMainRender
        renderer = VoidRender(context)
        glSurface.setEGLContextClientVersion(GL_VERSION)
        glSurface.setRenderer(renderer)
        setupStreamingModePipeline()


        val dataSet = arrayOf(
            FilterItem(R.drawable.a, FilterTypes.DEFAULT, null),
            FilterItem(R.drawable.b, FilterTypes.EYE_MOUTH, null),
            FilterItem(R.drawable.e, FilterTypes.BULGE, null),
            FilterItem(R.drawable.d, FilterTypes.BULGE_DOUBLE, null),
//            FilterItem(R.drawable.c, FilterTypes.EYE_RECT,  null),
//            FilterItem(R.drawable.f, FilterTypes.GLASSES,  null),
            FilterItem(R.drawable.g, FilterTypes.INVERSE, null),
            FilterItem(
                R.drawable.h,
                FilterTypes.LUT,
                "lut/b&w.cube"
            ),
            FilterItem(
                R.drawable.i,
                FilterTypes.LUT,
                "lut/CineStill.cube"
            ),
            FilterItem(
                R.drawable.j,
                FilterTypes.LUT,

                "lut/Sunset.cube"
            ),
            FilterItem(
                R.drawable.k,
                FilterTypes.LUT,

                "lut/Sunset2.cube"
            ),
            FilterItem(
                R.drawable.l,
                FilterTypes.LUT,
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
        val linearLayoutManager = recyclerView.layoutManager!! as LinearLayoutManager
        val render = renderer
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount: Int = linearLayoutManager.childCount
                val firstVisibleItemPosition: Int =
                    linearLayoutManager.findFirstVisibleItemPosition()
                lastItem = firstVisibleItemPosition + visibleItemCount
                if (lastItem != currentItem) {
                    Log.e(TAG, "onScrolled: $lastItem")
                    val filter = adapter.dataSet[lastItem - 1]
                    render.onDrawCallback.add {
                        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        render.filterTypes = filter.filterTypes
                        setFilterItem(filter)
                        if (BuildConfig.DEMO)
                            renderer.rotateVideo()
                        else
                            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                render.rotate(false)
                            } else {
                                render.rotate(true)
                            }
                    }

                    currentItem = lastItem
                }
            }
        })

        var isLongPress = false
        var longPressJob: Job? = null

        mainActivityBinding.BtnRec.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                val btn = view as ImageButton
                btn.setColorFilter(Color.YELLOW)

                longPressJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(500) // 500ms delay for long press
                    btn.setColorFilter(Color.RED)
                    isLongPress = true
                    startRecording()
                }
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                val btn = view as ImageButton
                btn.setColorFilter(Color.WHITE)
                if (isLongPress) {
                    stopRecording()
                } else {
                    takePicture() // Call the function to take a picture
                }

                // Cancel the coroutine if the press was short
                longPressJob?.cancel()
                isLongPress = false
            }
            false
        }
        permissions()
        isGrantedCam = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        CoroutineScope(Dispatchers.Default).launch {
            while (!isGrantedCam || !isGrantedStorage) {
                delay(0)
                Log.e(TAG, "init:  $isGrantedCam $isGrantedStorage")
            }
            runOnUiThread {
                sourceContent()
            }
        }

        renderer.frameListener = {
            if (isRecordingInitialized) {
                val frameTimeNanos = System.nanoTime()
                handler.post {
                    if (isRecordingInitialized) {
                        recTextureManager?.onDrawForRecord(renderer.glTextureManager.recordTexture)
                        encoder.setPresentationTimeAndSwap(frameTimeNanos)
                    }
                }
            }
        }
    }

    fun setFilterItem(filter: FilterItem){
        var oesFilter: GPUFilter? = null
        var filter2D: GPUFilter? = null
        when (filter.filterTypes) {
            FilterTypes.DEFAULT -> {
                oesFilter = DefaultOESFilter(context)
                filter2D = Default2DFilter(context)
            }

            FilterTypes.LUT -> {
                renderer.loadLUT(filter.lutFileName!!)
                oesFilter = LutOESFilter(context, renderer)
                filter2D = Default2DFilter(context)
            }

            FilterTypes.INVERSE -> {
                oesFilter = InverseOESFilter(context, renderer)
                filter2D = Default2DFilter(context)
            }

            FilterTypes.BULGE -> {
                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                oesFilter = DefaultOESFilter(context)
                filter2D = BulgeFilter(context)
            }

            FilterTypes.BULGE_DOUBLE -> {
                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                oesFilter = DefaultOESFilter(context)
                filter2D = BulgeFilterEyes(context)
            }

            FilterTypes.GLASSES -> {
                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

            }

            FilterTypes.EYE_MOUTH -> {
                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                val source = BitmapFactory.decodeStream(assets.open("monke.jpg"))
                val matrix = Matrix()
                matrix.preScale(-1f, 1f)
                matrix.postRotate(180f)
                val overlayImageBitmap = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.width,
                    source.height,
                    matrix,
                    true
                )
                oesFilter = EyeMouthMaskFilter(context, overlayImageBitmap)
                filter2D = Default2DFilter(context)
            }

            FilterTypes.EYE_RECT -> {
                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        }
        if (oesFilter != null && filter2D != null)
            renderer.setFilters(oesFilter, filter2D)
    }

    val encoder = Encoder()
    private var recTextureManager: GLTextureManager? = null
    private var outputPath: String? = null

    @Volatile
    private var isRecordingInitialized = false

    fun startRecording() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = String.format(Locale.getDefault(), "%dX%d_%s.mp4", renderer.width, renderer.height, timeStamp)

        outputPath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        ).toString()

        handler.post {
            if (recTextureManager == null) {
                recTextureManager = GLTextureManager(context)
            }

            encoder.prepareEncoder(
                renderer.cameraHeight, renderer.cameraWidth, renderer.eglContext!!,
                GL_VERSION, outputPath!!
            )
            encoder.startRecording()
            encoder.mInputSurface!!.makeCurrent()

            recTextureManager!!.initForRecord(renderer.cameraHeight, renderer.cameraWidth)
            encoder.requestKeyFrame()

            isRecordingInitialized = true
        }
    }

    fun stopRecording() {
        isRecordingInitialized = false

        handler.post {
            encoder.stopRecording()
            recTextureManager?.release()
            recTextureManager = null

            outputPath?.let { path ->
                updateContent(path)
                runOnUiThread {
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private val executorImage: ExecutorService = Executors.newSingleThreadExecutor()
    private var mOffscreenSurfaceImage: OffscreenSurface? = null
    private var glSaveImage: GLTextureManager? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private fun takePicture() {
        mainActivityBinding.BtnRec.visibility = View.INVISIBLE
        mainActivityBinding.circleProgress.visibility = View.VISIBLE
        val width = if (!BuildConfig.DEBUG) renderer.cameraWidth else renderer.cameraHeight
        val height = if (!BuildConfig.DEBUG) renderer.cameraHeight else renderer.cameraWidth
        if (glSaveImage == null) {
            glSaveImage = GLTextureManager(context)
            mOffscreenSurfaceImage =
                OffscreenSurface(
                    EglCore(renderer.eglContext, EglCore.FLAG_TRY_GLES3),
                    width,
                    height
                )
            executorImage.execute {
                mOffscreenSurfaceImage!!.makeCurrent()
                glSaveImage!!.initForRecord(width, height)
            }
        }
        executorImage.execute {
            glSaveImage!!.rotate(true)
            glSaveImage!!.onDrawForRecord(renderer.glTextureManager.recordTexture)
//            mOffscreenSurfaceImage!!.swapBuffers()
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())


            // Create the filename with the timestamp
            val fileName: String = java.lang.String.format(
                Locale.getDefault(),
                "%dX%d_%s.png",
                renderer.width,
                renderer.height,
                timeStamp
            )

            renderer.saveFrame(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                ), width, height
            )
            updateContent(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                ).toString()
            )
            runOnUiThread {
                mainActivityBinding.BtnRec.visibility = View.VISIBLE
                mainActivityBinding.circleProgress.visibility = View.INVISIBLE
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        Log.e(TAG, "startCamera: XXXXXXXXX")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFuture.get()
        cameraProviderFuture.addListener({
            val surfaceProvider = Preview.SurfaceProvider { request ->
                while (renderer.getSurfaceTexture() == null) {
                    continue
                }
                val surfaceTexture = renderer.getSurfaceTexture()
                val res = preview.resolutionInfo!!.resolution
                val surface = Surface(surfaceTexture)

                val (mWidth, mHeight) = resizeToFitScreen(
                    res.width,
                    res.height,
                    renderer.width,
                    renderer.height
                )

                renderer.resize(res.width, res.height, renderer.width, renderer.height)
                Log.e(
                    TAG,
                    "startCamera: ${preview.resolutionInfo!!.resolution} ${renderer.width} ${renderer.height}  ${mWidth * 2} ${mHeight * 2}"
                )


                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) { _ ->
                    // Handle resource release
                }
            }
            preview.surfaceProvider = surfaceProvider

            imageAnalyzer =
                ImageAnalysis.Builder().setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(backgroundExecutor) { image ->
                            faceLandmarkerHelper.detectLiveStream(
                                imageProxy = image,
                                isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                            )
                        }
                    }


            cameraProvider!!.unbindAll()
            cameraProvider!!.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            mainActivityBinding.BtnSwitchCamera.setOnClickListener {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    renderer.rotate(true)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    renderer.rotate(false)
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider!!.unbindAll()
                cameraProvider!!.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            }
        }, ContextCompat.getMainExecutor(this))

    }

    fun updateContent(file: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file),
            null
        ) { path, uri -> Log.i("MediaScanner", "Scanned $path -> URI = $uri") }
    }

    private fun sourceContent() {
        if (BuildConfig.DEMO)
            startDemoVideo()
        else
            startCamera()
    }

    private fun startDemoVideo() {
        Log.e(TAG, "startCamera: XXXXXXXXX")
        val surfaceTexture = renderer.getSurfaceTexture()
        val surface = Surface(surfaceTexture)

        val player = ExoPlayer.Builder(context).build()
        player.setVideoSurface(surface)
        val fileName = "peakpx.mp4"
        val file = File(context.cacheDir, fileName)
            .also {
                if (!it.exists()) {
                    it.outputStream().use { cache ->
                        context.assets.open(fileName).use { inputStream ->
                            inputStream.copyTo(cache)
                        }
                    }
                }
            }
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        player.setMediaItem(mediaItem)

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(res: VideoSize) {
                super.onVideoSizeChanged(res)
                renderer.resize(res.width, res.height, renderer.width, renderer.height)

            }
        })
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()

    }
    private fun permissions() {

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            )
        else
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            permissions.indexOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE).let {
                permissions.removeAt(it)
            }
        Log.e(TAG, "permissions: $permissions")

        requestMultiplePermission.launch(permissions.toTypedArray())
    }

    private fun resizeToFitScreen(
        cameraWidth: Int, cameraHeight: Int,
        screenWidth: Int, screenHeight: Int
    ): Pair<Int, Int> {
        val cameraRatio = cameraWidth.toFloat() / cameraHeight
        val screenRatio = screenWidth.toFloat() / screenHeight

        return if (cameraRatio > screenRatio) {
            // Fit to screen width
            val newHeight = (screenWidth / cameraRatio).toInt()
            screenWidth to newHeight
        } else {
            // Fit to screen height
            val newWidth = (screenHeight * cameraRatio).toInt()
            newWidth to screenHeight
        }
    }

    private fun normalizeTouch(
        touchX: Float,
        touchY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val normalizedX = touchX / screenWidth // Now between 0 and 1
        val normalizedY = 1f - (touchY / screenHeight) // Flip Y but keep in 0-1 range
        return Pair(normalizedX, normalizedY)
    }

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var backgroundExecutor: ExecutorService


    private fun setupStreamingModePipeline() {
        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                currentDelegate = DELEGATE_GPU,
                faceLandmarkerHelperListener = object : LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {

                    }

                    override fun onEmpty() {
                        glSurface.requestRender()
                    }

                    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {

                        val faceLandmarkerResult = resultBundle.result
//                        if (faceLandmarkerResult.faceLandmarks().isNotEmpty()) {
//                            val x0 = faceLandmarkerResult.faceLandmarks()[0][0].x()
//                            val y0 = faceLandmarkerResult.faceLandmarks()[0][0].y()
//                            Log.e(TAG, "permissions: $x0  -   $y0  , ${renderer.filterTypes}")
//                        }

                        when (renderer.filterTypes) {
                            FilterTypes.BULGE -> {
                                val x0 = faceLandmarkerResult.faceLandmarks()[0][0].x()
                                val y0 = faceLandmarkerResult.faceLandmarks()[0][0].y()

                                val x152 = faceLandmarkerResult.faceLandmarks()[0][152].x()
                                val y152 = faceLandmarkerResult.faceLandmarks()[0][152].y()

                                // Calculate Euclidean distance
                                val faceScale =
                                    (sqrt(((x152 - x0) * (x152 - x0) + (y152 - y0) * (y152 - y0)).toDouble())) * 2

                                val (newX, newY) = normalizeTouch(
                                    x0 * renderer.width,
                                    y0 * renderer.height,
                                    renderer.width.toFloat(),
                                    renderer.height.toFloat()
                                )
                                val filter = renderer.current2DFilter
                                if (filter is BulgeFilter) {
                                    filter.setPosBulge(newX, newY)
                                    filter.setPosScale(faceScale.toFloat())
                                }
                            }

                            FilterTypes.BULGE_DOUBLE -> {

                                //468 right 473 left center
                                val x468 = faceLandmarkerResult.faceLandmarks()[0][468].x()
                                val y468 = faceLandmarkerResult.faceLandmarks()[0][468].y()
                                val x133 = faceLandmarkerResult.faceLandmarks()[0][133].x()
                                val y133 = faceLandmarkerResult.faceLandmarks()[0][133].y()

                                val x473 = faceLandmarkerResult.faceLandmarks()[0][473].x()
                                val y473 = faceLandmarkerResult.faceLandmarks()[0][473].y()
                                val x463 = faceLandmarkerResult.faceLandmarks()[0][463].x()
                                val y463 = faceLandmarkerResult.faceLandmarks()[0][463].y()
                                val faceScale1 =
                                    (sqrt(((x133 - x468) * (x133 - x468) + (y133 - y468) * (y133 - y468)).toDouble())) * 2
                                val faceScale2 =
                                    (sqrt(((x463 - x473) * (x463 - x473) + (y463 - y473) * (y463 - y473)).toDouble())) * 2

//                        println("Face Scale: $faceScale")


                                val (rX, rY) = normalizeTouch(
                                    x468 * renderer.width,
                                    y468 * renderer.height,
                                    renderer.width.toFloat(),
                                    renderer.height.toFloat()
                                )
                                val (lX, lY) = normalizeTouch(
                                    x473 * renderer.width,
                                    y473 * renderer.height,
                                    renderer.width.toFloat(),
                                    renderer.height.toFloat()
                                )
                                val filter = renderer.current2DFilter
                                if (filter is BulgeFilterEyes) {
                                    filter.setPosBULDGEDouble(rX, rY, lX, lY)
                                    filter.setPosSCALEDouble(
                                        faceScale1.toFloat(),
                                        faceScale2.toFloat()
                                    )
                                }
                            }


                            FilterTypes.GLASSES -> {
                                return
                            }

                            FilterTypes.EYE_MOUTH -> {
                                val landmarks = faceLandmarkerResult.faceLandmarks()[0]
                                val filter = renderer.currentOesFilter
                                if (filter is EyeMouthMaskFilter) {
                                    filter.updateFaceLandmarks(landmarks)
                                }
                            }

                            FilterTypes.EYE_RECT -> {
                                return
                            }

                            else -> {
                                return
                            }
                        }
                        glSurface.requestRender()
                    }
                }
            )
        }
    }


    private var isCameraInitialized = false
    override fun onPause() {
        super.onPause()
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            isCameraInitialized = false
        }
//        backgroundExecutor.execute {
//            if (faceLandmarkerHelper.isClose()) {
//                faceLandmarkerHelper.setupFaceLandmarker()
//            }
//        }
    }

    override fun onResume() {
        super.onResume()
        if (cameraProvider != null) {
            sourceContent()
        }
    }

    companion object {
        internal const val TAG = "MainActivity"
        private const val MP_FACE_LANDMARKER_TASK = "face_landmarker.task"
        const val GL_VERSION = 3

        init {
            System.loadLibrary("facepoint")
        }
    }
}