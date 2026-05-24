package com.avoid.facepoint.render.mpfilters

import android.content.Context
import android.util.SizeF
import android.opengl.GLES31 as gl
import com.avoid.facepoint.render.GPUFilter
import com.avoid.facepoint.render.VoidRender.Companion.FLOAT_SIZE_BYTES
import com.avoid.facepoint.render.VoidRender.Companion.STRIDES
import com.avoid.facepoint.render.utils.GLUtils.compileShader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BulgeFilterEyes(private val context: Context) : GPUFilter  {
    private var program = 0
    private var positionHandle = 0
    private var texturePositionHandle = 0
    private var textureHandle = 0
    private var matrixHandle = 0

    // Bulge specific uniforms
    private var centerHandle = 0
    private var radiusHandle = 0
    private var scaleHandle = 0

    private var scale = 0.5f
    private var x = 0.5f
    private var y = 0.5f

    private var centerHandle2bulge1 = 0
    private var centerHandle2bulge2 = 0
    private var radiusHandle1bulge = 0
    private var radiusHandle2bulge = 0
    private var scaleHandle2bulge = 0
    private var scale1bulge = 0.5f
    private var scale2bulge = 0.5f
    private var center1 = SizeF(0.5f, 0.5f)
    private var center2 = SizeF(0.5f, 0.5f)
    private var aspectMatrix = FloatArray(16)

    private val vao = IntArray(1)
    private val vbo = IntArray(1)

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
        val fragmentShader = compileShader(context.assets,gl.GL_FRAGMENT_SHADER, "shader/two_buldge.glsl")
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

        centerHandle2bulge1 = gl.glGetUniformLocation(program, "center1")
        centerHandle2bulge2 = gl.glGetUniformLocation(program, "center2")

        radiusHandle1bulge = gl.glGetUniformLocation(program, "radius1")
        radiusHandle2bulge = gl.glGetUniformLocation(program, "radius2")

        scaleHandle2bulge = gl.glGetUniformLocation(program, "scale")

        bindVAO()
    }

    override fun onSurfaceChanged(
        width: Int,
        height: Int,
        aspectMatrix: FloatArray
    ) {
        this.aspectMatrix = aspectMatrix
        gl.glUseProgram(program)
        gl.glUniformMatrix4fv(matrixHandle, 1, false, aspectMatrix, 0)
    }

    override fun onDraw(textureID: Int) {
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)

        gl.glUseProgram(program)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, textureID)

        gl.glUniform2f(centerHandle2bulge1, center1.width, center1.height)
        gl.glUniform2f(centerHandle2bulge2, center2.width, center2.height)
        gl.glUniform1f(radiusHandle1bulge, scale1bulge)
        gl.glUniform1f(radiusHandle2bulge, scale2bulge)
        gl.glUniform1f(scaleHandle2bulge, 0.5f)

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
    fun setPosBULDGEDouble(x1: Float, y1: Float, x2: Float, y2: Float) {
        center1 = SizeF(x1, y1)
        center2 = SizeF(x2, y2)
    }

    fun setPosSCALEDouble(scale1: Float, scale2: Float) {
        this.scale1bulge = scale1
        this.scale2bulge = scale2
    }

}