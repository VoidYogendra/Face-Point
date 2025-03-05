package com.avoid.facepoint.render

import android.content.Context
import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.avoid.facepoint.model.FilterTypes
import com.avoid.facepoint.model.ShaderType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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
        private external fun read(width: Int, height: Int, channel: Int)
        private external fun write(textureID: Int, width: Int, height: Int)
        const val GL_TEXTURE_EXTERNAL_OES: Int = 36197
    }

    fun loadLUT(file: String) {
        Companion.loadLUT(context.assets, file)
    }

    var filterTypes: FilterTypes = FilterTypes.DEFAULT

    private var surfaceTexture: SurfaceTexture? = null

    var width = 0
    var height = 0
    private var textureWidth = 0
    private var textureHeight = 0

    var cameraWidth = 0
    var cameraHeight = 0
    var onDrawCallback: (() -> Unit?)? = null
    var eglContext: EGLContext? = null
        private set


    private val vertexData = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        1f, -1f, 0.0f, 0.0f, // Bottom-left
        -1f, -1f, 0.0f, 1.0f, // Bottom-right
        1f, 1f, 1.0f, 0.0f,// Top-left
        -1f, 1f, 1.0f, 1.0f // Top-right
    )

    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 1f, // Bottom-left
        1f, -1f, 1f, 1f, // Bottom-right
        -1f, 1f, 0f, 0f,// Top-left
        1f, 1f, 1f, 0f// Top-right
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

    val vao2D = IntArray(1)
    private val vbo2D = IntArray(1)

    private var textures = IntArray(2)
    private val textures2D = IntArray(1)

    var textureID = -1
    var textureID2D = -1

    var aspectMatrix = FloatArray(16)
    var aspectMatrix2D = FloatArray(16)

    fun onSurfaceCreated2D() {
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

        //generate vao vbo
        gl.glGenVertexArrays(1, vao2D, 0)
        gl.glGenBuffers(1, vbo2D, 0)
        //bind vao vbo
        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        val buffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
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

        textureID2D = createTexture()
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {

        createExternalTexture()

        if (surfaceTexture == null)
            surfaceTexture = SurfaceTexture(textureID)

        eglContext = EGL14.eglGetCurrentContext()
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        gl.glViewport(0, 0, width, height)

    }

    fun onSurfaceChanged(width: Int, height: Int) {
        gl.glViewport(0, 0, width, height)
        this.textureWidth = width
        this.textureHeight = height
        byteSize = textureWidth * textureHeight * 3
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix2D = scaleMatrix
        Matrix.setRotateM(aspectMatrix2D, 0, 180f , 0f, 0f, 1.0f)
        Matrix.scaleM(aspectMatrix2D, 0, -1.0f, 1.0f, 1.0f)
        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
    }

    private var byteSize = 0
    fun resize(textureWidth: Int, textureHeight: Int) {
        cameraWidth = textureWidth
        cameraHeight = textureHeight
        surfaceTexture?.setDefaultBufferSize(textureWidth, textureHeight)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix = scaleMatrix
        Matrix.scaleM(aspectMatrix, 0, 1.0f, -1.0f, 1.0f)
        Matrix.setRotateM(aspectMatrix, 0, 0f , 0f, 0f, 1.0f)
        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
    }

    fun rotate(flip:Boolean){
        onDrawCallback={

            if(flip)
            {
                Matrix.scaleM(aspectMatrix, 0, 1.0f, -1.0f, 1.0f)
                Matrix.setRotateM(aspectMatrix, 0, 0f , 0f, 0f, 1.0f)
            }
            else
            {
                Matrix.setRotateM(aspectMatrix, 0, 180f , 0f, 0f, 1.0f)
                Matrix.scaleM(aspectMatrix, 0, -1.0f, 1.0f, 1.0f)
            }

            gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
        }

    }

    override fun onDrawFrame(p0: GL10?) {
        //so it does not render during new setup
        if (onDrawCallback != null || textures[0] == 0) {
            onDrawCallback!!()
            onDrawCallback = null
            return
        }


        gl.glClear(gl.GL_COLOR_BUFFER_BIT or gl.GL_DEPTH_BUFFER_BIT)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])
//        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)

        when (filterTypes) {
            FilterTypes.DEFAULT -> {
                drawDefault()
            }

            FilterTypes.LUT -> {
                drawLUT()
            }

            FilterTypes.GRAIN -> {
                drawGRAIN()
            }
        }


        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
        if (byteSize > 0) {
            read(textureWidth, textureHeight, 4)
        }

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    fun onDraw() {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT or gl.GL_DEPTH_BUFFER_BIT)

        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])
//        gl.glUniformMatrix4fv(matrixHandle2D, 1, false, aspectMatrix2D, 0)
        gl.glActiveTexture(gl.GL_TEXTURE1)
        gl.glBindTexture(gl.GL_TEXTURE_2D, textures2D[0])
        gl.glUniform1i(textureHandle2D, 1)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)
        write(textures2D[0], textureWidth, textureHeight)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
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
            ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertexData)
                    position(0)
                }

        gl.glBufferData(
            gl.GL_ARRAY_BUFFER,
            vertexData.size * FLOAT_SIZE_BYTES,
            buffer,
            gl.GL_STATIC_DRAW
        )

        vertexData.size

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


    private fun drawDefault() {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0])

        surfaceTexture?.updateTexImage()

        gl.glUniform1i(textureHandle, 0)
    }

    /**----------------------------------------------------------------------------------------------------------------------------------**/


    /**----------------------------------------------------------------------------------------------------------------------------------**/

    fun createExternalTextureGRAIN() {

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


    private fun drawGRAIN() {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0])
        gl.glUniform2f(resHandle, cameraWidth.toFloat(), cameraHeight.toFloat())
        gl.glUniform1f(timeHandle, (0..10).random().toFloat())
        surfaceTexture?.updateTexImage()

        gl.glUniform1i(textureHandle, 0)
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
        gl.glGenTextures(2, textures, 0)
        if (shaderType == ShaderType.sampler2D) {
            val size = createTextureLUT2D(textures[0], textures[1])
            gl.glUniform1f(gl.glGetUniformLocation(programOES, "size"), size.toFloat())
            Log.e("AdrenoGLES-0", "createExternalTextureLUT: $size")
        } else createTextureLUT(textures[0], textures[1])
        textureID = textures[0]
        bindVAO()
    }

    private fun drawLUT() {
        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0])
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

    //for recording only
    private fun createTexture(): Int {
        gl.glGenTextures(1, textures2D, 0)
        gl.glActiveTexture(gl.GL_TEXTURE1) //not needed since it is by default
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
        return textures2D[0]
    }

    fun getSurfaceTexture(): SurfaceTexture {
        return surfaceTexture!!
    }
}