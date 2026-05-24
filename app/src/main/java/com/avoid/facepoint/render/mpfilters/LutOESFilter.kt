package com.avoid.facepoint.render.mpfilters

import android.content.Context
import android.util.Log
import com.avoid.facepoint.render.GPUFilter
import com.avoid.facepoint.render.VoidRender
import com.avoid.facepoint.render.VoidRender.Companion.FLOAT_SIZE_BYTES
import com.avoid.facepoint.render.VoidRender.Companion.GL_TEXTURE_EXTERNAL_OES
import com.avoid.facepoint.render.VoidRender.Companion.STRIDES
import com.avoid.facepoint.render.VoidRender.Companion.createTextureLUT2D
import com.avoid.facepoint.render.utils.GLUtils.compileShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES31 as gl

class LutOESFilter(private val context: Context,private val render: VoidRender) : GPUFilter {
    private var program = 0
    private var positionHandle = 0
    private var texturePositionHandle = 0
    private var textureHandle = 0
    private var lutHandle = 0
    private var matrixHandle = 0
    private var aspectMatrix = FloatArray(16)
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private var lutTextureId = -1

    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 0f,  // Bottom-left
        1f, -1f, 1f, 0f,  // Bottom-right
        -1f, 1f, 0f, 1f,  // Top-left
        1f, 1f, 1f, 1f   // Top-right
    )



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

    override fun onSurfaceCreated() {
        val vertexShader = compileShader(context.assets,gl.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        val fragmentShader = compileShader(context.assets,gl.GL_FRAGMENT_SHADER, "shader/lut_fragOES_mali.glsl")

        program = gl.glCreateProgram()
        gl.glAttachShader(program, vertexShader)
        gl.glAttachShader(program, fragmentShader)
        gl.glLinkProgram(program)

        gl.glDeleteShader(vertexShader)
        gl.glDeleteShader(fragmentShader)

        positionHandle = gl.glGetAttribLocation(program, "aPosition")
        texturePositionHandle = gl.glGetAttribLocation(program, "aTexPosition")
        textureHandle = gl.glGetUniformLocation(program, "uTexture")
        lutHandle = gl.glGetUniformLocation(program, "lut")
        matrixHandle = gl.glGetUniformLocation(program, "u_Matrix")

        val generatedTextures = IntArray(1)
        gl.glGenTextures(1, generatedTextures, 0)
        lutTextureId = generatedTextures[0]

        val size = createTextureLUT2D(render.textureID, lutTextureId)

        gl.glUseProgram(program)
        gl.glUniform1f(gl.glGetUniformLocation(program, "size"), size.toFloat())
        gl.glUseProgram(0)

        Log.e("AdrenoGLES-0", "createExternalTextureLUT: $size")

        bindVAO()
    }

    override fun onSurfaceChanged(width: Int, height: Int, aspectMatrix: FloatArray) {
        this.aspectMatrix = aspectMatrix
        gl.glUseProgram(program)
        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
    }

    override fun onDraw(textureID: Int) {
        gl.glUseProgram(program)
        gl.glClear(gl.GL_COLOR_BUFFER_BIT or gl.GL_DEPTH_BUFFER_BIT)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)

        gl.glActiveTexture(gl.GL_TEXTURE1)
        gl.glBindTexture(
            gl.GL_TEXTURE_2D,
            lutTextureId
        )

        gl.glUniform1i(textureHandle, 0)
        gl.glUniform1i(lutHandle, 1)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    override fun release() {
        if (program != 0) {
            gl.glDeleteProgram(program)
            program = 0
        }
    }

}