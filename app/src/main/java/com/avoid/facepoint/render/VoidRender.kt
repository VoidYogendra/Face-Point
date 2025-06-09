package com.avoid.facepoint.render

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.util.SizeF
import androidx.compose.ui.graphics.ImageBitmap
import com.avoid.facepoint.model.FilterTypes
import com.avoid.facepoint.model.ShaderType
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import java.util.Queue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES31 as gl

class VoidRender(val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "VoidRender"
        private const val FLOAT_SIZE_BYTES = 4
        private const val STRIDES = 4 * FLOAT_SIZE_BYTES
        private external fun loadLUT(assetManager: AssetManager, mFile: String): Boolean
        private external fun createTextureLUT2D(textureID: Int, lutID: Int): Int
        private external fun createTextureLUT(textureID: Int, lutID: Int)
        external fun makeKHR(textureID: Int)
        external fun useKHR(textureID: Int)
        const val GL_TEXTURE_EXTERNAL_OES: Int = 36197
    }

    fun loadLUT(file: String) {
        Companion.loadLUT(context.assets, file)
    }

    var frame = 0

    var filterTypes: FilterTypes = FilterTypes.DEFAULT

    private var surfaceTexture: SurfaceTexture? = null

    var width = 0
    var height = 0
    private var textureWidth = 0
    private var textureHeight = 0

    var cameraWidth = 0
    var cameraHeight = 0
    var onDrawCallback: Queue<Runnable> = LinkedList()
    var eglContext: EGLContext? = null
        private set


    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 0f,  // Bottom-left
        1f, -1f, 1f, 0f,  // Bottom-right
        -1f, 1f, 0f, 1f,  // Top-left
        1f, 1f, 1f, 1f   // Top-right
    )

    private var vertexShader = -1
    private var vertexShader2D = 0

    private var fragmentShaderOES = -1
    private var fragmentShader = 0

    private var programOES = -1
    private var program2D = 0

    private var positionHandle = 0
    private var positionHandle2D = 0
    private var texturePositionHandle = 0
    private var texturePositionHandle2D = 0
    private var textureHandle = 0
    private var textureHandle2D = 0
    private var matrixHandle = 0
    private var matrixHandle2D = 0

    //lut
    private var lutHandle = 0

    //grain
    private var resHandle = 0
    private var timeHandle = 0

    private val vao = IntArray(1)
    private val vbo = IntArray(1)

    private val vao2D = IntArray(1)
    private val vbo2D = IntArray(1)

    private var textures = IntArray(1)
    private val textures2D = IntArray(1)

    var textureID = -1
    private var textureID2D = -1

    private var aspectMatrix = FloatArray(16)
    private var aspectMatrix2D = FloatArray(16)

    val glTextureManager = GLTextureManager(context)
    val glToKHR = GLTextureManager(context)

    var faceMeshResult: FaceMeshResult? = null
    private var faceGLRender: FaceMeshResultGlRenderer? = null
    private var faceGLRenderEyeMouth: FaceMeshEyeMouth? = null
    private var faceGLRenderEyeRect: FaceMeshEyeRect? = null
    private var matrix = MatrixCalc()
    private var maskMatrix = FloatArray(16)
    var overlayImageBitmap: Bitmap? = null

    private fun onSurfaceCreated2D() {
        vertexShader2D = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "main_frag.glsl")
        program2D = gl.glCreateProgram()
        gl.glAttachShader(program2D, vertexShader2D)
        gl.glAttachShader(program2D, fragmentShader)
        gl.glLinkProgram(program2D)
        gl.glUseProgram(program2D) //use it in Codec

        positionHandle2D = gl.glGetAttribLocation(program2D, "aPosition")
        texturePositionHandle2D = gl.glGetAttribLocation(program2D, "aTexPosition")
        textureHandle2D = gl.glGetUniformLocation(program2D, "uTexture")
        matrixHandle2D = gl.glGetUniformLocation(program2D, "u_Matrix")

        textureID2D = createRenderTexture()

        bindVAO2D()
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {

        createExternalTexture()

        if (surfaceTexture == null)
            surfaceTexture = SurfaceTexture(textureID)

        eglContext = EGL14.eglGetCurrentContext()

        faceGLRender = FaceMeshResultGlRenderer()
        faceGLRenderEyeMouth = FaceMeshEyeMouth()
        faceGLRenderEyeRect = FaceMeshEyeRect()
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        gl.glViewport(0, 0, width, height)

        onSurfaceCreated2D()
        matrix.surface(width, height)
        matrix.frame(width, height)
        maskMatrix = matrix.doIT(maskMatrix)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
//        gl.glViewport(0, 0, width, height)
        this.textureWidth = width
        this.textureHeight = height
//        byteSize = textureWidth * textureHeight * 3
        gl.glTexImage2D(
            gl.GL_TEXTURE_2D,
            0,
            gl.GL_RGBA,
            textureWidth,
            textureHeight,
            0,
            gl.GL_RGBA,
            gl.GL_UNSIGNED_BYTE,
            null
        )
        gl.glFramebufferTexture2D(
            gl.GL_FRAMEBUFFER,
            gl.GL_COLOR_ATTACHMENT0,
            gl.GL_TEXTURE_2D,
            textures2D[0],
            0
        )


        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix2D = scaleMatrix
        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)

        glTextureManager.initForRecord(width, height)
        glToKHR.initForKHR(width, height)
    }

    //    private var byteSize = 0
    fun resize(textureWidth: Int, textureHeight: Int, screenWidth: Int, screenHeight: Int) {
        cameraWidth = textureWidth
        cameraHeight = textureHeight


        onDrawCallback.add {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)
            onSurfaceChanged(screenWidth, screenHeight)
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0)
        }

        surfaceTexture?.setDefaultBufferSize(textureWidth, textureHeight)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix = scaleMatrix
        gl.glUseProgram(programOES)
//        Matrix.scaleM(aspectMatrix, 0, 1.0f, -1.0f, 1.0f)
//        Matrix.setRotateM(aspectMatrix, 0, 180f, 0f, 0f, 1.0f)
        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
        gl.glUseProgram(0)
    }

    fun rotate(flip: Boolean) {
        onDrawCallback.add {
            gl.glUseProgram(programOES)
            if (flip) {
                Matrix.scaleM(aspectMatrix, 0, -1.0f, 1.0f, 1.0f)
                Matrix.setRotateM(aspectMatrix, 0, 90f, 0f, 0f, 1.0f)
            } else {
                Matrix.setRotateM(aspectMatrix, 0, -90f, 0f, 0f, 1.0f)
                Matrix.scaleM(aspectMatrix, 0, 1.0f, -1.0f, 1.0f)
            }
            gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
            gl.glUseProgram(0)
        }
    }

    fun rotateVideo() {
        onDrawCallback.add {
            gl.glUseProgram(programOES)

            Matrix.setRotateM(aspectMatrix, 0, 180f, 0f, 0f, 1.0f)
            Matrix.scaleM(aspectMatrix, 0, -1.0f, 1.0f, 1.0f)

            gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
            gl.glUseProgram(0)
        }
    }

    //    var buffer: ByteBuffer? = null
    var readCallback: ((width: Int, height: Int) -> Unit)? = null

    override fun onDrawFrame(p0: GL10?) {
        //so it does not render during new setup
        synchronized(onDrawCallback) {
            while (onDrawCallback.isNotEmpty()) {
                onDrawCallback.poll()?.run()
            }
        }
        //TODO: either sync FaceMeshResult with this camera texture or use FaceMeshResult's texture instead of original camera texture
        if (glToKHR.framebufferRecord != 0) {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, glToKHR.framebufferRecord)
            onDrawToFbo(textures[0])
        }
        gl.glFinish()
        if (filterTypes.faceMesh) {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)
            glToKHR.onDrawForRecord(glToKHR.recordTexture)
            sendToInference()
        } else {
            if (framebufferName != 0) {
                gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)
                onDrawToFbo(textures[0])

            }
        }


        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0)
        drawFBO()
        glTextureManager.onDrawForRecord(glTextureManager.recordTexture)
        if (glTextureManager.framebufferRecord != 0) {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, glTextureManager.framebufferRecord)
            drawFBO()
        }

        frame++
    }
    //TODO Replace whole pipeline to interface Style,
    // so every filter can have own class that implements all basic rendering logic
    // i.e create , draw , delete
    // for more cleaner code
    private fun sendToInference() {
        if (width <= 0) return
        when (filterTypes) {
            FilterTypes.BULGE -> {
                readCallback?.invoke(this.width, this.height)
            }

            FilterTypes.BULGE_DOUBLE -> {
                readCallback?.invoke(this.width, this.height)
            }

            FilterTypes.GLASSES -> {
                readCallback?.invoke(this.width, this.height)
                if (faceMeshResult != null) {
                    faceGLRender!!.renderResult(faceMeshResult, maskMatrix)
                }
            }

            FilterTypes.EYE_MOUTH -> {
                readCallback?.invoke(this.width, this.height)
                if (faceMeshResult != null) {
                    faceGLRenderEyeMouth!!.renderResult(faceMeshResult, maskMatrix)
                }
            }

            FilterTypes.EYE_RECT -> {
                readCallback?.invoke(this.width, this.height)
                if (faceMeshResult != null) {
                    faceGLRenderEyeRect!!.renderResult(faceMeshResult, maskMatrix)
                }
            }

            else -> {}
        }
    }

    private fun drawFBO() {
        when (filterTypes) {

            FilterTypes.BULGE -> {
                drawBULDGE(textureID2D)
            }

            FilterTypes.BULGE_DOUBLE -> {
                drawBULDGEDouble(textureID2D)
            }

            FilterTypes.EYE_MOUTH -> {
                overlayImageBitmap?.let {
                    drawMask(glToKHR.recordTexture, textureID2D, it)
                }
            }

            else -> {
                onDraw(textureID2D)
            }
        }
    }


    fun onDrawToFbo(texID: Int) {
        gl.glUseProgram(programOES)
        gl.glClear(gl.GL_COLOR_BUFFER_BIT or gl.GL_DEPTH_BUFFER_BIT)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        when (filterTypes) {
            FilterTypes.DEFAULT -> {
                drawDefault(texID)
            }

            FilterTypes.LUT -> {
                drawLUT(texID)
            }

            FilterTypes.INVERSE -> {
                drawINVERSE(texID)
            }

            FilterTypes.BULGE -> {
                drawDefault(texID)
            }

            else -> drawDefault(texID)
        }

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    private fun onDraw(texID: Int) {

        gl.glUseProgram(program2D)

        gl.glClear(gl.GL_COLOR_BUFFER_BIT)
        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, texID)
        gl.glUniform1i(textureHandle2D, 0)
        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    fun saveFrame(file: File, width: Int, height: Int) {
        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.
        val filename = file.toString()
        val buf = ByteBuffer.allocateDirect(width * height * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(
            0, 0, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
        )
//        checkGlError("glReadPixels")
        buf.rewind()

        var bos: BufferedOutputStream? = null
        try {
            bos = BufferedOutputStream(FileOutputStream(filename))
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
            bmp.recycle()
        } finally {
            bos?.close()
        }
        Log.d("IDK GG", "Saved " + width + "x" + height + " frame as '" + filename + "'")
    }


    /**
     * Shader Crash On ->
     * CPU Mediatek
     * GPU Mali
     * uniform sampler3D lut
     * https://registry.khronos.org/OpenGL/extensions/OES/OES_texture_3D.txt
     * */
    private fun compileShader(type: Int, file: String): Int {
        val code = context.assets.open(file).bufferedReader().useLines {
            it.joinToString("\n")
        }
        val shader = gl.glCreateShader(type)
        gl.glShaderSource(shader, code)
        gl.glCompileShader(shader)
        val status = IntArray(1)
        gl.glGetShaderiv(shader, gl.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            gl.glDeleteShader(shader)
            val shaderType =
                if (type == gl.GL_VERTEX_SHADER) "GL_VERTEX_SHADER" else "GL_FRAGMENT_SHADER"
            throw RuntimeException("Failed: ${gl.glGetShaderInfoLog(shader)} type $shaderType \n $code")
        }
        return shader
    }

    fun bindVAO2D() {
        if (vao2D[0] == 0)
            gl.glGenVertexArrays(1, vao2D, 0)
        if (vbo2D[0] == 0)
            gl.glGenBuffers(1, vbo2D, 0)
        //generate vao vbo
        gl.glGenVertexArrays(1, vao2D, 0)
        gl.glGenBuffers(1, vbo2D, 0)
        //bind vao vbo
        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        val buffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData2D.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertexData2D)
                    position(0)
                }

        gl.glBufferData(
            gl.GL_ARRAY_BUFFER,
            vertexData2D.size * FLOAT_SIZE_BYTES,
            buffer,
            gl.GL_STATIC_DRAW
        )

        gl.glVertexAttribPointer(
            positionHandle2D,
            2,
            gl.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        gl.glEnableVertexAttribArray(positionHandle2D)

        gl.glVertexAttribPointer(
            texturePositionHandle2D,
            2,
            gl.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        gl.glEnableVertexAttribArray(texturePositionHandle2D)
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    fun deleteCurrentProgram2D() {
        if (program2D != -1 && vertexShader2D != -1 && fragmentShader != -1) {

            gl.glDeleteShader(vertexShader2D)
            gl.glDeleteShader(fragmentShader)
            gl.glDeleteProgram(program2D)
        }
    }

    fun bindVAO() {
        //generate vao vbo
        if (vao[0] == 0)
            gl.glGenVertexArrays(1, vao, 0)
        if (vbo[0] == 0)
            gl.glGenBuffers(1, vbo, 0)


        //bind vao vbo
        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        val buffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData2D.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertexData2D)
                    position(0)
                }

        gl.glBufferData(
            gl.GL_ARRAY_BUFFER,
            vertexData2D.size * FLOAT_SIZE_BYTES,
            buffer,
            gl.GL_STATIC_DRAW
        )

        gl.glVertexAttribPointer(
            positionHandle,
            2,
            gl.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        gl.glEnableVertexAttribArray(positionHandle)

        gl.glVertexAttribPointer(
            texturePositionHandle,
            2,
            gl.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        gl.glEnableVertexAttribArray(texturePositionHandle)
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)

    }

    fun deleteCurrentProgram() {
        if (programOES != -1 && vertexShader != -1 && fragmentShaderOES != -1) {

            gl.glDeleteShader(vertexShader)
            gl.glDeleteShader(fragmentShaderOES)
            gl.glDeleteProgram(programOES)
        }
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/

    fun createExternalTexture() {

        vertexShader = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShaderOES = compileShader(gl.GL_FRAGMENT_SHADER, "main_fragOES.glsl")

        programOES = gl.glCreateProgram()
        gl.glAttachShader(programOES, vertexShader)
        gl.glAttachShader(programOES, fragmentShaderOES)
        gl.glLinkProgram(programOES)
        gl.glUseProgram(programOES)



        positionHandle = gl.glGetAttribLocation(programOES, "aPosition")
        texturePositionHandle = gl.glGetAttribLocation(programOES, "aTexPosition")
        textureHandle = gl.glGetUniformLocation(programOES, "uTexture")
        matrixHandle = gl.glGetUniformLocation(programOES, "u_Matrix")

        textures = IntArray(1)
        gl.glGenTextures(1, textures, 0)
        gl.glActiveTexture(gl.GL_TEXTURE0) //not needed since it is by default
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0])
        gl.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            gl.GL_TEXTURE_MIN_FILTER,
            gl.GL_LINEAR
        )
        gl.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            gl.GL_TEXTURE_MAG_FILTER,
            gl.GL_LINEAR
        )
        textureID = textures[0]
        bindVAO()
    }


    private fun drawDefault(texID: Int) {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texID)

        surfaceTexture?.updateTexImage()

        gl.glUniform1i(textureHandle, 0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/

    fun createExternalTextureINVERSE() {

        vertexShader = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShaderOES = compileShader(gl.GL_FRAGMENT_SHADER, "grain_fragOES.glsl")

        programOES = gl.glCreateProgram()
        gl.glAttachShader(programOES, vertexShader)
        gl.glAttachShader(programOES, fragmentShaderOES)
        gl.glLinkProgram(programOES)
        gl.glUseProgram(programOES)



        positionHandle = gl.glGetAttribLocation(programOES, "aPosition")
        texturePositionHandle = gl.glGetAttribLocation(programOES, "aTexPosition")
        textureHandle = gl.glGetUniformLocation(programOES, "uTexture")
        matrixHandle = gl.glGetUniformLocation(programOES, "u_Matrix")
        resHandle = gl.glGetUniformLocation(programOES, "iResolution")
        timeHandle = gl.glGetUniformLocation(programOES, "iTime")

        bindVAO()
    }


    private fun drawINVERSE(texID: Int) {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texID)
        gl.glUniform2f(resHandle, cameraWidth.toFloat(), cameraHeight.toFloat())
        gl.glUniform1f(timeHandle, (0..10).random().toFloat())
        surfaceTexture?.updateTexImage()

        gl.glUniform1i(textureHandle, 0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/
    fun createDefault2D() {
        vertexShader2D = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "main_frag.glsl")
        program2D = gl.glCreateProgram()
        gl.glAttachShader(program2D, vertexShader2D)
        gl.glAttachShader(program2D, fragmentShader)
        gl.glLinkProgram(program2D)
        gl.glUseProgram(program2D) //use it in Codec

        positionHandle2D = gl.glGetAttribLocation(program2D, "aPosition")
        texturePositionHandle2D = gl.glGetAttribLocation(program2D, "aTexPosition")
        textureHandle2D = gl.glGetUniformLocation(program2D, "uTexture")
        matrixHandle2D = gl.glGetUniformLocation(program2D, "u_Matrix")

        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
    }


    private var centerHandle = 0
    private var radiusHandle = 0
    private var scaleHandle = 0
    private var scale = 0.5f
    private var x = 0.5f
    private var y = 0.5f
    fun create2DBULDGE() {
        vertexShader2D = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "buldge_frag.glsl")
        program2D = gl.glCreateProgram()
        gl.glAttachShader(program2D, vertexShader2D)
        gl.glAttachShader(program2D, fragmentShader)
        gl.glLinkProgram(program2D)
        gl.glUseProgram(program2D) //use it in Codec

        positionHandle2D = gl.glGetAttribLocation(program2D, "aPosition")
        texturePositionHandle2D = gl.glGetAttribLocation(program2D, "aTexPosition")
        textureHandle2D = gl.glGetUniformLocation(program2D, "uTexture")
        matrixHandle2D = gl.glGetUniformLocation(program2D, "u_Matrix")

        centerHandle = gl.glGetUniformLocation(program2D, "center")
        radiusHandle = gl.glGetUniformLocation(program2D, "radius")
        scaleHandle = gl.glGetUniformLocation(program2D, "scale")
        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
    }

    fun setPosBULDGE(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun setPosSCALE(scale: Float) {
        this.scale = scale
    }

    private fun drawBULDGE(texID: Int) {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)

        gl.glUseProgram(program2D)

        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, texID)

        gl.glUniform2f(centerHandle, x, y)
        gl.glUniform1f(radiusHandle, scale)
        gl.glUniform1f(scaleHandle, 0.5f)

        gl.glUniform1i(textureHandle2D, 0)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/


    private var centerHandle2bulge1 = 0
    private var centerHandle2bulge2 = 0
    private var radiusHandle2bulge = 0
    private var scaleHandle2bulge = 0
    private var scale2bulge = 0.5f
    private var center1 = SizeF(0.5f, 0.5f)
    private var center2 = SizeF(0.5f, 0.5f)
    fun create2DBULDGEDouble() {
        vertexShader2D = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "two_buldge.glsl")
        program2D = gl.glCreateProgram()
        gl.glAttachShader(program2D, vertexShader2D)
        gl.glAttachShader(program2D, fragmentShader)
        gl.glLinkProgram(program2D)
        gl.glUseProgram(program2D) //use it in Codec

        positionHandle2D = gl.glGetAttribLocation(program2D, "aPosition")
        texturePositionHandle2D = gl.glGetAttribLocation(program2D, "aTexPosition")
        textureHandle2D = gl.glGetUniformLocation(program2D, "uTexture")
        matrixHandle2D = gl.glGetUniformLocation(program2D, "u_Matrix")

        centerHandle2bulge1 = gl.glGetUniformLocation(program2D, "center1")
        centerHandle2bulge2 = gl.glGetUniformLocation(program2D, "center2")
        radiusHandle2bulge = gl.glGetUniformLocation(program2D, "radius")
        scaleHandle2bulge = gl.glGetUniformLocation(program2D, "scale")
        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
    }

    fun setPosBULDGEDouble(x1: Float, y1: Float, x2: Float, y2: Float) {
        center1 = SizeF(x1, y1)
        center2 = SizeF(x2, y2)
    }

    fun setPosSCALEDouble(scale: Float) {
        this.scale2bulge = scale
    }

    private fun drawBULDGEDouble(texID: Int) {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)

        gl.glUseProgram(program2D)

        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, texID)

        gl.glUniform2f(centerHandle2bulge1, center1.width, center1.height)
        gl.glUniform2f(centerHandle2bulge2, center2.width, center2.height)
        gl.glUniform1f(radiusHandle2bulge, scale)
        gl.glUniform1f(scaleHandle2bulge, 0.5f)

        gl.glUniform1i(textureHandle2D, 0)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/
    //lut
    private var shaderType = ShaderType.sampler3D
    fun createExternalTextureLUT() {

        vertexShader = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")

        val extensions = gl.glGetString(gl.GL_EXTENSIONS)
        if (!extensions.contains("GL_OES_texture_3D")) {
            fragmentShaderOES = compileShader(gl.GL_FRAGMENT_SHADER, "lut_fragOES_MKT.glsl")
            Log.e("GL ERROR", "GL_OES_texture_3D NOT SUPPORTED")
            shaderType = ShaderType.sampler2D
        } else
            fragmentShaderOES = compileShader(gl.GL_FRAGMENT_SHADER, "lut_fragOES.glsl")


        programOES = gl.glCreateProgram()
        gl.glAttachShader(programOES, vertexShader)
        gl.glAttachShader(programOES, fragmentShaderOES)
        gl.glLinkProgram(programOES)
        gl.glUseProgram(programOES)



        positionHandle = gl.glGetAttribLocation(programOES, "aPosition")
        texturePositionHandle = gl.glGetAttribLocation(programOES, "aTexPosition")
        textureHandle = gl.glGetUniformLocation(programOES, "uTexture")
        lutHandle = gl.glGetUniformLocation(programOES, "lut")
        matrixHandle = gl.glGetUniformLocation(programOES, "u_Matrix")

        textures = IntArray(2)
        gl.glGenTextures(1, textures, 1)
        textures[0] = textureID
        if (shaderType == ShaderType.sampler2D) {
            val size = createTextureLUT2D(textures[0], textures[1])
            gl.glUniform1f(gl.glGetUniformLocation(programOES, "size"), size.toFloat())
            Log.e("AdrenoGLES-0", "createExternalTextureLUT: $size")
        } else createTextureLUT(textures[0], textures[1])

        bindVAO()
    }

    private fun drawLUT(texID: Int) {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texID)
        surfaceTexture?.updateTexImage()

        gl.glActiveTexture(gl.GL_TEXTURE1)
        gl.glBindTexture(
            if (shaderType == ShaderType.sampler2D) gl.GL_TEXTURE_2D else gl.GL_TEXTURE_3D,
            textures[1]
        )

        gl.glUniform1i(textureHandle, 0)
        gl.glUniform1i(lutHandle, 1)
    }
    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/
    private var maskHandle2D = 0
    private var overlayHandle2D = 0
    private val overlay = IntArray(1)

    private var uScreenSizeHandle2D = 0
    private var uOverlaySizeSizeHandle2D = 0
    private var uOverlayOffsetSizeHandle2D = 0
    fun create2DMask() {
        vertexShader2D = compileShader(gl.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "mask_frag.glsl")
        program2D = gl.glCreateProgram()
        gl.glAttachShader(program2D, vertexShader2D)
        gl.glAttachShader(program2D, fragmentShader)
        gl.glLinkProgram(program2D)
        gl.glUseProgram(program2D) //use it in Codec

        positionHandle2D = gl.glGetAttribLocation(program2D, "aPosition")
        texturePositionHandle2D = gl.glGetAttribLocation(program2D, "aTexPosition")
        textureHandle2D = gl.glGetUniformLocation(program2D, "uTexture")
        maskHandle2D = gl.glGetUniformLocation(program2D, "uMaskTex")
        overlayHandle2D = gl.glGetUniformLocation(program2D, "overlayTex")
        matrixHandle2D = gl.glGetUniformLocation(program2D, "u_Matrix")

        uScreenSizeHandle2D = gl.glGetUniformLocation(program2D, "uScreenSize")
        uOverlaySizeSizeHandle2D = gl.glGetUniformLocation(program2D, "uOverlaySize")
        uOverlayOffsetSizeHandle2D = gl.glGetUniformLocation(program2D, "uOverlayOffset")

        gl.glUniform2fv(
            uScreenSizeHandle2D,
            1,
            floatArrayOf(cameraHeight.toFloat(), cameraWidth.toFloat()),
            0
        )

        Log.e(TAG, "create2DMask: $cameraWidth $cameraHeight ")

        val ff =
            cameraHeight.toFloat() / overlayImageBitmap!!.width.toFloat()//cameraHeight is actually weight
        val gg =
            cameraWidth.toFloat() / overlayImageBitmap!!.height.toFloat()//cameraWidth is actually height

        gl.glUniform2fv(
            uOverlaySizeSizeHandle2D,
            1,
            floatArrayOf(
                overlayImageBitmap!!.width.toFloat() * (ff),
                overlayImageBitmap!!.height.toFloat() * (gg)
            ),
            0
        )
        gl.glUniform2fv(uOverlayOffsetSizeHandle2D, 1, floatArrayOf(ff, 0f), 0)

        gl.glGenTextures(1, overlay, 0)
        gl.glActiveTexture(gl.GL_TEXTURE2)
        gl.glBindTexture(gl.GL_TEXTURE_2D, overlay[0])
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(gl.GL_TEXTURE_2D, 0, overlayImageBitmap, 0)

        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
    }

    private fun drawMask(camID: Int, maskID: Int, overlayImageBitmap: Bitmap) {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)

        gl.glUseProgram(program2D)

        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, camID)

        gl.glActiveTexture(gl.GL_TEXTURE1)
        gl.glBindTexture(gl.GL_TEXTURE_2D, maskID)

        gl.glActiveTexture(gl.GL_TEXTURE2)
        gl.glBindTexture(gl.GL_TEXTURE_2D, overlay[0])

        GLUtils.texSubImage2D(gl.GL_TEXTURE_2D, 0, 0, 0, overlayImageBitmap)


        gl.glUniform1i(textureHandle2D, 0)
        gl.glUniform1i(maskHandle2D, 1)
        gl.glUniform1i(overlayHandle2D, 2)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    //for recording only
    private var framebufferName = 0
    private fun createRenderTexture(): Int {
        val args = IntArray(1)

        gl.glGetIntegerv(gl.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        gl.glGetIntegerv(gl.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]

        gl.glGenFramebuffers(args.size, args, 0)
        framebufferName = args[0]
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)

        gl.glGenTextures(1, textures2D, 0)

        gl.glBindTexture(gl.GL_TEXTURE_2D, textures2D[0])
        gl.glTexParameteri(
            gl.GL_TEXTURE_2D,
            gl.GL_TEXTURE_MIN_FILTER,
            gl.GL_LINEAR
        )
        gl.glTexParameteri(
            gl.GL_TEXTURE_2D,
            gl.GL_TEXTURE_MAG_FILTER,
            gl.GL_LINEAR
        )

        gl.glTexImage2D(
            gl.GL_TEXTURE_2D,
            0,
            gl.GL_RGBA,
            512,
            512,
            0,
            gl.GL_RGBA,
            gl.GL_UNSIGNED_BYTE,
            null
        )
        gl.glFramebufferTexture2D(
            gl.GL_FRAMEBUFFER,
            gl.GL_COLOR_ATTACHMENT0,
            gl.GL_TEXTURE_2D,
            textures2D[0],
            0
        )

        val status = gl.glCheckFramebufferStatus(gl.GL_FRAMEBUFFER)
        if (status != gl.GL_FRAMEBUFFER_COMPLETE) {
            throw java.lang.RuntimeException("Failed to initialize framebuffer object $status")
        }

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, saveFramebuffer)
        gl.glBindTexture(gl.GL_TEXTURE_2D, saveTexName)

        return textures2D[0]
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        return surfaceTexture
    }
}