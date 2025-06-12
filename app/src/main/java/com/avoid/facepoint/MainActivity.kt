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
import android.net.Uri
import android.opengl.EGL14
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
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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
import com.avoid.facepoint.render.Encoder
import com.avoid.facepoint.render.GLTextureManager
import com.avoid.facepoint.render.VoidRender
import com.avoid.facepoint.render.egl.EglCore
import com.avoid.facepoint.render.egl.OffscreenSurface
import com.avoid.facepoint.ui.ButtonAdapter
import com.google.mediapipe.framework.AppTextureFrame
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
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
    private var isRecord = false
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var rotation = 0
    private var isGrantedCam = false
    private var isGrantedStorage = false
    private var cameraProvider: ProcessCameraProvider? = null


    private val handlerThread = HandlerThread("TEST").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY) // Prefer 16:9, but fallback if needed
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
            } catch (e: Exception) {
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
            Log.d(TAG, "onActivityResult: Manage External Storage Permissions Granted");
        } else {
            isGrantedStorage = false
            Toast.makeText(this@MainActivity, "Storage Permissions Denied", Toast.LENGTH_SHORT)
                .show();
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        setContentView(mainActivityBinding.root)
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
            FilterItem(R.drawable.a, FilterTypes.DEFAULT, renderer, null),
            FilterItem(R.drawable.b, FilterTypes.EYE_MOUTH, renderer, null),
            FilterItem(R.drawable.c, FilterTypes.EYE_RECT, renderer, null),
            FilterItem(R.drawable.d, FilterTypes.BULGE_DOUBLE, renderer, null),
            FilterItem(R.drawable.e, FilterTypes.BULGE, renderer, null),
            FilterItem(R.drawable.f, FilterTypes.GLASSES, renderer, null),
            FilterItem(R.drawable.g, FilterTypes.INVERSE, renderer, null),
            FilterItem(
                R.drawable.h,
                FilterTypes.LUT,
                renderer,
                "lut/b&w.cube"
            ),
            FilterItem(
                R.drawable.i,
                FilterTypes.LUT,
                renderer,
                "lut/CineStill.cube"
            ),
            FilterItem(
                R.drawable.j,
                FilterTypes.LUT,
                renderer,
                "lut/Sunset.cube"
            ),
            FilterItem(
                R.drawable.k,
                FilterTypes.LUT,
                renderer,
                "lut/Sunset2.cube"
            ),
            FilterItem(
                R.drawable.l,
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
                        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        when (filter.filterTypes) {
                            FilterTypes.DEFAULT -> {
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                render.createDefault2D()
                            }

                            FilterTypes.LUT -> {
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.loadLUT(filter.lutFileName!!)
                                render.createExternalTextureLUT()
                                render.createDefault2D()
                            }

                            FilterTypes.INVERSE -> {
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTextureINVERSE()
                                render.createDefault2D()
                            }

                            FilterTypes.BULGE -> {
                                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                render.create2DBULDGE()
                            }

                            FilterTypes.BULGE_DOUBLE -> {
                                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                render.create2DBULDGEDouble()
                            }

                            FilterTypes.GLASSES -> {
                                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                render.createDefault2D()
                            }

                            FilterTypes.EYE_MOUTH -> {
                                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                val source = BitmapFactory.decodeStream(assets.open("monke.jpg"))
                                val matrix = Matrix()
                                matrix.preScale(-1f, 1f)
                                matrix.postRotate(180f)
                                render.overlayImageBitmap = Bitmap.createBitmap(
                                    source,
                                    0,
                                    0,
                                    source.width,
                                    source.height,
                                    matrix,
                                    true
                                )
                                render.create2DMask()

                            }

                            FilterTypes.EYE_RECT -> {
                                glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                render.deleteCurrentProgram()
                                render.deleteCurrentProgram2D()

                                render.createExternalTexture()
                                render.createDefault2D()
                            }
                        }
                        if (BuildConfig.DEMO)
                            renderer.rotateVideo()
                        else
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
                    isRecord = true // Start recording
                }
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                val btn = view as ImageButton
                btn.setColorFilter(Color.WHITE)
                if (isLongPress) {
                    isRecord = false // Stop recording
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
                Log.e(TAG, "init: XXXXXXXXX $isGrantedCam $isGrantedStorage")
            }
            runOnUiThread {
                sourceContent()
            }
        }

        val encoder = Encoder()
        var record = false

        CoroutineScope(Dispatchers.IO).launch {
            val fps = 60
            val glTextureManager = GLTextureManager(context)
            var frame = 0
            while (true) {
                val frameTimeNanos = System.nanoTime()
                if (isRecord) {
                    if (!record) {

                        val timeStamp =
                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())


                        // Create the filename with the timestamp
                        val fileName = String.format(
                            Locale.getDefault(),
                            "%dX%d_%s.mp4",
                            renderer.width,
                            renderer.height,
                            timeStamp
                        )
                        val outputPath = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            fileName
                        ).toString()
                        encoder.prepareEncoder(
                            fps, renderer.cameraHeight, renderer.cameraWidth, renderer.eglContext!!,
                            GL_VERSION, outputPath
                        )
                        handler.post {
                            encoder.mInputSurface!!.makeCurrent()
                            glTextureManager.initForRecord(
                                renderer.cameraHeight,
                                renderer.cameraWidth
                            )
                        }
                        record = true
                    } else {
                        while (frame >= renderer.frame)
                            delay(1)
                        frame = renderer.frame
                        handler.post {
                            encoder.drainEncoder(false)
                            if (record) {
                                glTextureManager.onDrawForRecord(renderer.glTextureManager.recordTexture)
                                encoder.mInputSurface!!.setPresentationTime(frameTimeNanos)
                                encoder.mInputSurface!!.swapBuffers()
                            }
                        }
                    }
                }

                if (!isRecord && record) {
                    record = false
                    handler.post {
                        encoder.drainEncoder(true)
                        encoder.releaseEncoder()
                        Log.e(TAG, "startCamera: DONE")
                    }
                }
            }
        }

    }

    private val executorImage: ExecutorService = Executors.newSingleThreadExecutor()
    private var mOffscreenSurfaceImage: OffscreenSurface? = null
    private var glSaveImage: GLTextureManager? = null

    private fun takePicture() {
        mainActivityBinding.BtnRec.isEnabled = false
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
            mOffscreenSurfaceImage!!.swapBuffers()
            runOnUiThread {
                mainActivityBinding.BtnRec.isEnabled = true
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        if (isCameraInitialized) return
        isCameraInitialized = true
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


            cameraProvider!!.unbindAll()
            cameraProvider!!.bindToLifecycle(this, cameraSelector, preview)
            mainActivityBinding.BtnSwitchCamera.setOnClickListener {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    renderer.rotate(true)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    renderer.rotate(false)
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider!!.unbindAll()
                cameraProvider!!.bindToLifecycle(this, cameraSelector, preview)
            }
        }, ContextCompat.getMainExecutor(this))

    }

    private fun sourceContent() {
        if (BuildConfig.DEMO)
            startDemoVideo()
        else
            startCamera()
    }

    private fun startDemoVideo() {
        if (isCameraInitialized) return
        isCameraInitialized = true
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
                val (mWidth, mHeight) = resizeToFitScreen(
                    res.width,
                    res.height,
                    renderer.width,
                    renderer.height
                )
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

    fun normalizeTouch(
        touchX: Float,
        touchY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val normalizedX = touchX / screenWidth // Now between 0 and 1
        val normalizedY = 1f - (touchY / screenHeight) // Flip Y but keep in 0-1 range
        return Pair(normalizedX, normalizedY)
    }

    private var facemesh: FaceMesh? = null
    private var mOffscreenSurface: OffscreenSurface? = null
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    private fun setupStreamingModePipeline() {

        val glTextureManager = GLTextureManager(context)
        facemesh =
            FaceMesh(
                this,
                FaceMeshOptions.builder()
                    .setStaticImageMode(false)
                    .setRefineLandmarks(true)
                    .setRunOnGpu(true)
                    .build()
            )
        facemesh!!.setErrorListener { message: String, _: RuntimeException? ->
            Log.e(
                TAG,
                "MediaPipe Face Mesh error:$message"
            )
        }

        facemesh!!.setResultListener { faceMeshResult ->
            when (renderer.filterTypes) {
                FilterTypes.BULGE -> {
                    if (faceMeshResult.multiFaceLandmarks().size > 0) {
                        val x0 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[0].x
                        val y0 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[0].y

                        val x152 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[152].x
                        val y152 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[152].y

// Calculate Euclidean distance
                        val faceScale =
                            (sqrt(((x152 - x0) * (x152 - x0) + (y152 - y0) * (y152 - y0)).toDouble())) * 2

//                        println("Face Scale: $faceScale")


                        val (newX, newY) = normalizeTouch(
                            x0 * renderer.width,
                            y0 * renderer.height,
                            renderer.width.toFloat(),
                            renderer.height.toFloat()
                        )
                        renderer.setPosBULDGE(newX, newY)
                        renderer.setPosSCALE(faceScale.toFloat())
                    }
                }

                FilterTypes.BULGE_DOUBLE -> {
                    if (faceMeshResult.multiFaceLandmarks().size > 0) {
                        val x463 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[463].x
                        val y463 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[463].y

                        val x263 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[263].x
                        val y263 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[263].y

                        //468 right 473 left center
                        val x468 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[468].x
                        val y468 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[468].y

                        val x473 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[473].x
                        val y473 = faceMeshResult.multiFaceLandmarks()[0].landmarkList[473].y
                        val size = renderer.width / renderer.height
                        var faceScale =
                            (sqrt(((x263 - x463) * (x263 - x463) + (y263 - y463) * (y263 - y463)).toDouble()))
                        faceScale *= (size * size * size)


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

                        renderer.setPosBULDGEDouble(rX, rY, lX, lY)
                        renderer.setPosSCALEDouble(faceScale.toFloat())
                    }
                }


                FilterTypes.GLASSES -> {
                    renderer.faceMeshResult = faceMeshResult
                }

                FilterTypes.EYE_MOUTH -> {
                    renderer.faceMeshResult = faceMeshResult
                }

                FilterTypes.EYE_RECT -> {
                    renderer.faceMeshResult = faceMeshResult
                }

                else -> {
                    return@setResultListener
                }
            }
//            if (!isRecord)
            glSurface.requestRender()
        }

        //face mesh creates new context on the same thread
        val core = EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_TRY_GLES3)

        var temp: AppTextureFrame? = null

        renderer.readCallback = { w, h ->
            executor.execute {
                val frameTimeNanos = System.nanoTime()
                if (mOffscreenSurface == null) {
                    mOffscreenSurface = OffscreenSurface(core, w, h)
                    mOffscreenSurface!!.makeCurrent()
                    glTextureManager.initForUse(w, h)
                    temp = AppTextureFrame(glTextureManager.recordTexture, w, h)
                }

                glTextureManager.rotate(if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) true else false)
                glTextureManager.onDrawForKHR(
                    glTextureManager.recordTexture,
                )
                temp?.timestamp = frameTimeNanos
                facemesh!!.send(temp)
//              mOffscreenSurface!!.swapBuffers() //// Not Needed as facemesh will swap buffers
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroy()
    }

    private var isCameraInitialized = false
    override fun onPause() {
        super.onPause()
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            isCameraInitialized = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCameraInitialized && cameraProvider != null) {
            sourceContent()
            if (mainActivityBinding.RvFilterList.adapter != null)
                mainActivityBinding.RvFilterList.scrollToPosition(0)
        }
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