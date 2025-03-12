package com.avoid.facepoint.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLFace(private val context: Context) {
    companion object {
        private const val TAG = "GLRecord"
        private const val FLOAT_SIZE_BYTES = 4
        private const val STRIDES = 4 * FLOAT_SIZE_BYTES
        const val GL_TEXTURE_EXTERNAL_OES: Int = 36197
    }
    var surfaceTexture: SurfaceTexture? = null
    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 0f,  // Bottom-left
        1f, -1f, 1f, 0f,  // Bottom-right
        -1f, 1f, 0f, 1f,  // Top-left
        1f, 1f, 1f, 1f   // Top-right
    )

    private var vertexShaderOES = 0


    private var fragmentShaderOES = 0


    private var programOES = 0


    private var positionHandleOES = 0

    private var texturePositionHandleOES = 0

    private var textureHandleOES = 0

    private var matrixHandleOES = 0

    val vaoOES = IntArray(1)
    val vboOES = IntArray(1)


    var aspectMatrixOES = FloatArray(16)

    var framebufferOES = -1
    var mTexture = -1

    fun initForFace(width: Int, height: Int) {
        vertexShaderOES = compileShader(GLES31.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShaderOES = compileShader(GLES31.GL_FRAGMENT_SHADER, "main_fragOES.glsl")
        programOES = GLES31.glCreateProgram()
        GLES31.glAttachShader(programOES, vertexShaderOES)
        GLES31.glAttachShader(programOES, fragmentShaderOES)
        GLES31.glLinkProgram(programOES)
        GLES31.glUseProgram(programOES) //use it in Codec

        positionHandleOES = GLES31.glGetAttribLocation(programOES, "aPosition")
        texturePositionHandleOES = GLES31.glGetAttribLocation(programOES, "aTexPosition")
        textureHandleOES = GLES31.glGetUniformLocation(programOES, "uTexture")
        matrixHandleOES = GLES31.glGetUniformLocation(programOES, "u_Matrix")


        val args = IntArray(1)

        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]


        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        GLES31.glGenFramebuffers(args.size, args, 0)
        framebufferOES = args[0]

        GLES11Ext.glBindFramebufferOES(GLES11Ext.GL_FRAMEBUFFER_OES, framebufferOES)

        GLES31.glGenTextures(1, args, 0)
        mTexture = args[0]

        GLES31.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexture)
        GLES31.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES31.GL_TEXTURE_MIN_FILTER,
            GLES31.GL_LINEAR
        )
        GLES31.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES31.GL_TEXTURE_MAG_FILTER,
            GLES31.GL_LINEAR
        )

        GLES31.glTexImage2D(
            GL_TEXTURE_EXTERNAL_OES,
            0,
            GLES31.GL_RGBA,
            width,
            height,
            0,
            GLES31.GL_RGBA,
            GLES31.GL_UNSIGNED_BYTE,
            null
        )
        GLES11Ext.glFramebufferTexture2DOES(GLES11Ext.GL_FRAMEBUFFER_OES,
            GLES11Ext.GL_COLOR_ATTACHMENT0_OES, GL_TEXTURE_EXTERNAL_OES,
            mTexture,0)

        surfaceTexture=SurfaceTexture(mTexture)
        surfaceTexture!!.setDefaultBufferSize(width, height)

        val status = GLES31.glCheckFramebufferStatus(GLES11Ext.GL_FRAMEBUFFER_OES)
        if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            throw java.lang.RuntimeException("Failed to initialize framebuffer object $status")
        }

        //-----------------------------------------------------------------------
        GLES31.glGenVertexArrays(1, vaoOES, 0)
        GLES31.glGenBuffers(1, vboOES, 0)
        //generate vao vbo
        GLES31.glGenVertexArrays(1, vaoOES, 0)
        GLES31.glGenBuffers(1, vboOES, 0)
        //bind vao vbo
        GLES31.glBindVertexArray(vaoOES[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vboOES[0])

        val buffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData2D.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertexData2D)
                    position(0)
                }

        GLES31.glBufferData(
            GLES31.GL_ARRAY_BUFFER,
            vertexData2D.size * FLOAT_SIZE_BYTES,
            buffer,
            GLES31.GL_STATIC_DRAW
        )

        GLES31.glVertexAttribPointer(
            positionHandleOES,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        GLES31.glEnableVertexAttribArray(positionHandleOES)

        GLES31.glVertexAttribPointer(
            texturePositionHandleOES,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        GLES31.glEnableVertexAttribArray(texturePositionHandleOES)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, saveFramebuffer)
        GLES31.glBindTexture(GL_TEXTURE_EXTERNAL_OES, saveTexName)
        //-----------------------------------------------------------------------

        GLES31.glViewport(0, 0, width, height)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrixOES = scaleMatrix
        GLES31.glUniformMatrix4fv(matrixHandleOES, 1, false, aspectMatrixOES, 0)
    }

    fun initForUse(width: Int, height: Int) {
        vertexShaderOES = compileShader(GLES31.GL_VERTEX_SHADER, "main_vert.glsl")
        fragmentShaderOES = compileShader(GLES31.GL_FRAGMENT_SHADER, "main_fragOES.glsl")
        programOES = GLES31.glCreateProgram()
        GLES31.glAttachShader(programOES, vertexShaderOES)
        GLES31.glAttachShader(programOES, fragmentShaderOES)
        GLES31.glLinkProgram(programOES)
        GLES31.glUseProgram(programOES) //use it in Codec

        positionHandleOES = GLES31.glGetAttribLocation(programOES, "aPosition")
        texturePositionHandleOES = GLES31.glGetAttribLocation(programOES, "aTexPosition")
        textureHandleOES = GLES31.glGetUniformLocation(programOES, "uTexture")
        matrixHandleOES = GLES31.glGetUniformLocation(programOES, "u_Matrix")


        val args = IntArray(1)

        GLES31.glGenTextures(1, args, 0)
        mTexture = args[0]

        GLES31.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexture)
        GLES31.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES31.GL_TEXTURE_MIN_FILTER,
            GLES31.GL_LINEAR
        )
        GLES31.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES31.GL_TEXTURE_MAG_FILTER,
            GLES31.GL_LINEAR
        )

        surfaceTexture=SurfaceTexture(mTexture)
        surfaceTexture!!.setDefaultBufferSize(width, height)

        //-----------------------------------------------------------------------
        GLES31.glGenVertexArrays(1, vaoOES, 0)
        GLES31.glGenBuffers(1, vboOES, 0)
        //generate vao vbo
        GLES31.glGenVertexArrays(1, vaoOES, 0)
        GLES31.glGenBuffers(1, vboOES, 0)
        //bind vao vbo
        GLES31.glBindVertexArray(vaoOES[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vboOES[0])

        val buffer: FloatBuffer =
            ByteBuffer.allocateDirect(vertexData2D.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertexData2D)
                    position(0)
                }

        GLES31.glBufferData(
            GLES31.GL_ARRAY_BUFFER,
            vertexData2D.size * FLOAT_SIZE_BYTES,
            buffer,
            GLES31.GL_STATIC_DRAW
        )

        GLES31.glVertexAttribPointer(
            positionHandleOES,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        GLES31.glEnableVertexAttribArray(positionHandleOES)

        GLES31.glVertexAttribPointer(
            texturePositionHandleOES,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        GLES31.glEnableVertexAttribArray(texturePositionHandleOES)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
        //-----------------------------------------------------------------------

        GLES31.glViewport(0, 0, width, height)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrixOES = scaleMatrix
        GLES31.glUniformMatrix4fv(matrixHandleOES, 1, false, aspectMatrixOES, 0)
    }

    fun onDrawForRecord(texID: Int) {
        if(surfaceTexture==null) return
        GLES31.glUseProgram(programOES)

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
        GLES31.glBindVertexArray(vaoOES[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vboOES[0])

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texID)
        GLES31.glUniform1i(textureHandleOES, 0)
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)
        surfaceTexture!!.updateTexImage()
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
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
        val shader = GLES31.glCreateShader(type)
        GLES31.glShaderSource(shader, code)
        GLES31.glCompileShader(shader)
        val status = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES31.glDeleteShader(shader)
            val shaderType =
                if (type == GLES31.GL_VERTEX_SHADER) "GL_VERTEX_SHADER" else "GL_FRAGMENT_SHADER"
            throw RuntimeException("Failed: ${GLES31.glGetShaderInfoLog(shader)} type $shaderType \n $code")
        }
        return shader
    }

}