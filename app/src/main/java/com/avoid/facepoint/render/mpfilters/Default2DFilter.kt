package com.avoid.facepoint.render.mpfilters

import android.content.Context
import com.avoid.facepoint.render.GPUFilter
import com.avoid.facepoint.render.VoidRender.Companion.FLOAT_SIZE_BYTES
import com.avoid.facepoint.render.VoidRender.Companion.STRIDES
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES31 as gl

class Default2DFilter(private val context: Context) : GPUFilter {
    private var program = 0
    private var positionHandle = 0
    private var texturePositionHandle = 0
    private var textureHandle = 0
    private var matrixHandle = 0
    private var aspectMatrix = FloatArray(16)
    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 0f,  // Bottom-left
        1f, -1f, 1f, 0f,  // Bottom-right
        -1f, 1f, 0f, 1f,  // Top-left
        1f, 1f, 1f, 1f   // Top-right
    )
    private val vao2D = IntArray(1)
    private val vbo2D = IntArray(1)

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
        val vertexShader = compileShader(gl.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        val fragmentShader = compileShader(gl.GL_FRAGMENT_SHADER, "shader/main_frag.glsl")

        program = gl.glCreateProgram()
        gl.glAttachShader(program, vertexShader)
        gl.glAttachShader(program, fragmentShader)
        gl.glLinkProgram(program)

        gl.glDeleteShader(vertexShader)
        gl.glDeleteShader(fragmentShader)

        positionHandle = gl.glGetAttribLocation(program, "aPosition")
        texturePositionHandle = gl.glGetAttribLocation(program, "aTexPosition")
        textureHandle = gl.glGetUniformLocation(program, "uTexture")
        matrixHandle = gl.glGetUniformLocation(program, "u_Matrix")

        bindVAO2D()
    }

    override fun onSurfaceChanged(width: Int, height: Int, aspectMatrix: FloatArray) {
        this.aspectMatrix = aspectMatrix
        gl.glUseProgram(program)
        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
    }

    override fun onDraw(textureID: Int) {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)
        gl.glUseProgram(program)

        gl.glBindVertexArray(vao2D[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, textureID)
        gl.glUniform1i(textureHandle, 0)

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
            throw RuntimeException("Failed: ${gl.glGetShaderInfoLog(shader)}")
        }
        return shader
    }
}