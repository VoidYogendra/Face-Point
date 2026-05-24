package com.avoid.facepoint.render.mpfilters

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.Matrix
import android.util.Log
import com.avoid.facepoint.render.GPUFilter
import com.avoid.facepoint.render.VoidRender.Companion.FLOAT_SIZE_BYTES
import com.avoid.facepoint.render.VoidRender.Companion.STRIDES
import com.avoid.facepoint.render.utils.GLUtils.compileShader
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES31 as gl
import android.opengl.GLUtils as AndroidGLUtils

class EyeMouthMaskFilter(
    private val context: Context,
    private val overlayBitmap: Bitmap
) : GPUFilter {

    private val vertexData2D = floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )

    private val vao = IntArray(1)
    private val vbo = IntArray(1)

    private val faceMeshLipsPoints = listOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
        185, 40, 39, 37, 0, 267, 269, 270, 409, 291
    )
    private val faceMeshLeftEyePoints = listOf(
        263, 249, 390, 373, 374, 380, 381, 382, 362,
        466, 388, 387, 386, 385, 384, 398, 362
    )
    private val faceMeshRightEyePoints = listOf(
        33, 7, 163, 144, 145, 153, 154, 155, 133,
        246, 161, 160, 159, 158, 157, 173, 133
    )
    private val lipsColor = floatArrayOf(1f, 1f, 1f, 1f)

    private var oesProgram = 0
    private var oesPositionHandle = 0
    private var oesTexPositionHandle = 0
    private var oesTextureHandle = 0
    private var oesMatrixHandle = 0
    private var cameraFbo = IntArray(1)
    private var cameraTex2D = IntArray(1)

    private var polygonProgram = 0
    private var polyPositionHandle = 0
    private var polyColorHandle = 0

    private var compositeProgram = 0
    private var compPositionHandle = 0
    private var compTexPositionHandle = 0
    private var compTextureHandle = 0
    private var compMaskHandle = 0
    private var compOverlayHandle = 0
    private var compMatrixHandle = 0
    private var uScreenSizeHandle = 0
    private var uOverlaySizeHandle = 0
    private var uOverlayOffsetHandle = 0

    private var maskFbo = IntArray(1)
    private var maskTexture = IntArray(1)
    private var overlayTexture = IntArray(1)
    private var aspectMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private var currentLandmarks: List<NormalizedLandmark>? = null
    private var polygonBuffer: FloatBuffer? = null
    private var texWidth = 0
    private var texHeight = 0

    fun updateFaceLandmarks(landmarks: List<NormalizedLandmark>) {
        currentLandmarks = landmarks
    }

    private fun bindVAO() {
        if (vao[0] == 0) gl.glGenVertexArrays(1, vao, 0)
        if (vbo[0] == 0) gl.glGenBuffers(1, vbo, 0)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        val buffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData2D.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData2D)
                position(0)
            }

        gl.glBufferData(gl.GL_ARRAY_BUFFER, vertexData2D.size * FLOAT_SIZE_BYTES, buffer, gl.GL_STATIC_DRAW)

        gl.glVertexAttribPointer(compPositionHandle, 2, gl.GL_FLOAT, false, STRIDES, 0)
        gl.glEnableVertexAttribArray(compPositionHandle)

        gl.glVertexAttribPointer(compTexPositionHandle, 2, gl.GL_FLOAT, false, STRIDES, 2 * FLOAT_SIZE_BYTES)
        gl.glEnableVertexAttribArray(compTexPositionHandle)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    override fun onSurfaceCreated() {
        val oesVert = "uniform mat4 u_Matrix;\nattribute vec4 aPosition;\nattribute vec2 aTexPosition;\nvarying vec2 vTexPosition;\nvoid main() {\n  gl_Position = u_Matrix * aPosition;\n  vTexPosition = aTexPosition;\n}"
        val oesFrag = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nuniform samplerExternalOES uTexture;\nvarying vec2 vTexPosition;\nvoid main() {\n  gl_FragColor = texture2D(uTexture, vTexPosition);\n}"

        val vertShaderOES = gl.glCreateShader(gl.GL_VERTEX_SHADER)
        gl.glShaderSource(vertShaderOES, oesVert)
        gl.glCompileShader(vertShaderOES)

        val fragShaderOES = gl.glCreateShader(gl.GL_FRAGMENT_SHADER)
        gl.glShaderSource(fragShaderOES, oesFrag)
        gl.glCompileShader(fragShaderOES)

        oesProgram = gl.glCreateProgram()
        gl.glAttachShader(oesProgram, vertShaderOES)
        gl.glAttachShader(oesProgram, fragShaderOES)
        gl.glLinkProgram(oesProgram)
        gl.glDeleteShader(vertShaderOES)
        gl.glDeleteShader(fragShaderOES)

        oesPositionHandle = gl.glGetAttribLocation(oesProgram, "aPosition")
        oesTexPositionHandle = gl.glGetAttribLocation(oesProgram, "aTexPosition")
        oesTextureHandle = gl.glGetUniformLocation(oesProgram, "uTexture")
        oesMatrixHandle = gl.glGetUniformLocation(oesProgram, "u_Matrix")

        val polyVert = "attribute vec4 vPosition;\nvoid main() {\n  gl_Position = vPosition;\n}"
        val polyFrag = "precision mediump float;\nuniform vec4 uColor;\nvoid main() {\n  gl_FragColor = uColor;\n}"

        val vertShaderPoly = gl.glCreateShader(gl.GL_VERTEX_SHADER)
        gl.glShaderSource(vertShaderPoly, polyVert)
        gl.glCompileShader(vertShaderPoly)

        val fragShaderPoly = gl.glCreateShader(gl.GL_FRAGMENT_SHADER)
        gl.glShaderSource(fragShaderPoly, polyFrag)
        gl.glCompileShader(fragShaderPoly)

        polygonProgram = gl.glCreateProgram()
        gl.glAttachShader(polygonProgram, vertShaderPoly)
        gl.glAttachShader(polygonProgram, fragShaderPoly)
        gl.glLinkProgram(polygonProgram)
        gl.glDeleteShader(vertShaderPoly)
        gl.glDeleteShader(fragShaderPoly)

        polyPositionHandle = gl.glGetAttribLocation(polygonProgram, "vPosition")
        polyColorHandle = gl.glGetUniformLocation(polygonProgram, "uColor")

        val vertShaderComp = compileShader(context.assets, gl.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        val fragShaderComp = compileShader(context.assets, gl.GL_FRAGMENT_SHADER, "shader/mask_frag.glsl")

        compositeProgram = gl.glCreateProgram()
        gl.glAttachShader(compositeProgram, vertShaderComp)
        gl.glAttachShader(compositeProgram, fragShaderComp)
        gl.glLinkProgram(compositeProgram)
        gl.glDeleteShader(vertShaderComp)
        gl.glDeleteShader(fragShaderComp)

        compPositionHandle = gl.glGetAttribLocation(compositeProgram, "aPosition")
        compTexPositionHandle = gl.glGetAttribLocation(compositeProgram, "aTexPosition")
        compTextureHandle = gl.glGetUniformLocation(compositeProgram, "uTexture")
        compMaskHandle = gl.glGetUniformLocation(compositeProgram, "uMaskTex")
        compOverlayHandle = gl.glGetUniformLocation(compositeProgram, "overlayTex")
        compMatrixHandle = gl.glGetUniformLocation(compositeProgram, "u_Matrix")
        uScreenSizeHandle = gl.glGetUniformLocation(compositeProgram, "uScreenSize")
        uOverlaySizeHandle = gl.glGetUniformLocation(compositeProgram, "uOverlaySize")
        uOverlayOffsetHandle = gl.glGetUniformLocation(compositeProgram, "uOverlayOffset")

        bindVAO()

        gl.glGenTextures(1, overlayTexture, 0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, overlayTexture[0])
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE)
        AndroidGLUtils.texImage2D(gl.GL_TEXTURE_2D, 0, overlayBitmap, 0)
    }

    override fun onSurfaceChanged(width: Int, height: Int, aspectMatrix: FloatArray) {
        this.aspectMatrix = aspectMatrix
        this.texWidth = width
        this.texHeight = height

        if (cameraTex2D[0] != 0) {
            gl.glDeleteTextures(1, cameraTex2D, 0)
            gl.glDeleteFramebuffers(1, cameraFbo, 0)
        }

        gl.glGenFramebuffers(1, cameraFbo, 0)
        gl.glGenTextures(1, cameraTex2D, 0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, cameraTex2D[0])
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)
        gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, width, height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, null)
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, cameraFbo[0])
        gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER, gl.GL_COLOR_ATTACHMENT0, gl.GL_TEXTURE_2D, cameraTex2D[0], 0)
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0)

        if (maskTexture[0] != 0) {
            gl.glDeleteTextures(1, maskTexture, 0)
            gl.glDeleteFramebuffers(1, maskFbo, 0)
        }

        gl.glGenFramebuffers(1, maskFbo, 0)
        gl.glGenTextures(1, maskTexture, 0)

        gl.glBindTexture(gl.GL_TEXTURE_2D, maskTexture[0])
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)
        gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA, width, height, 0, gl.GL_RGBA, gl.GL_UNSIGNED_BYTE, null)

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, maskFbo[0])
        gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER, gl.GL_COLOR_ATTACHMENT0, gl.GL_TEXTURE_2D, maskTexture[0], 0)
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0)

        gl.glUseProgram(compositeProgram)
        val ff = height.toFloat() / overlayBitmap.width.toFloat()
        val gg = width.toFloat() / overlayBitmap.height.toFloat()

        gl.glUniform2fv(uScreenSizeHandle, 1, floatArrayOf(height.toFloat(), width.toFloat()), 0)
        gl.glUniform2fv(uOverlaySizeHandle, 1, floatArrayOf(overlayBitmap.width.toFloat() * ff, overlayBitmap.height.toFloat() * gg), 0)
        gl.glUniform2fv(uOverlayOffsetHandle, 1, floatArrayOf(ff, 0f), 0)
        gl.glUniformMatrix4fv(compMatrixHandle, 1, false, identityMatrix, 0)
    }

    override fun onDraw(textureID: Int) {
        val currentFbo = IntArray(1)
        gl.glGetIntegerv(gl.GL_FRAMEBUFFER_BINDING, currentFbo, 0)

        val currentViewport = IntArray(4)
        gl.glGetIntegerv(gl.GL_VIEWPORT, currentViewport, 0)

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, cameraFbo[0])
        gl.glViewport(0, 0, texWidth, texHeight)
        gl.glUseProgram(oesProgram)

        gl.glUniformMatrix4fv(oesMatrixHandle, 1, false, aspectMatrix, 0)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])
        gl.glVertexAttribPointer(oesPositionHandle, 2, gl.GL_FLOAT, false, STRIDES, 0)
        gl.glEnableVertexAttribArray(oesPositionHandle)
        gl.glVertexAttribPointer(oesTexPositionHandle, 2, gl.GL_FLOAT, false, STRIDES, 2 * FLOAT_SIZE_BYTES)
        gl.glEnableVertexAttribArray(oesTexPositionHandle)

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID)
        gl.glUniform1i(oesTextureHandle, 0)
        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glDisableVertexAttribArray(oesPositionHandle)
        gl.glDisableVertexAttribArray(oesTexPositionHandle)

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, maskFbo[0])
        gl.glViewport(0, 0, texWidth, texHeight)
        gl.glClearColor(0f, 0f, 0f, 1f)
        gl.glClear(gl.GL_COLOR_BUFFER_BIT)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)

        currentLandmarks?.let { landmarks ->
            gl.glUseProgram(polygonProgram)
            drawFilledPolygon(landmarks, faceMeshLipsPoints, lipsColor)
            drawFilledPolygon(landmarks, faceMeshLeftEyePoints, lipsColor)
            drawFilledPolygon(landmarks, faceMeshRightEyePoints, lipsColor)
        }

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, currentFbo[0])
        gl.glViewport(currentViewport[0], currentViewport[1], currentViewport[2], currentViewport[3])

        gl.glUseProgram(compositeProgram)

        gl.glBindVertexArray(vao[0])
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vbo[0])

        gl.glActiveTexture(gl.GL_TEXTURE0)
        gl.glBindTexture(gl.GL_TEXTURE_2D, cameraTex2D[0])

        gl.glActiveTexture(gl.GL_TEXTURE1)
        gl.glBindTexture(gl.GL_TEXTURE_2D, maskTexture[0])

        gl.glActiveTexture(gl.GL_TEXTURE2)
        gl.glBindTexture(gl.GL_TEXTURE_2D, overlayTexture[0])
        AndroidGLUtils.texSubImage2D(gl.GL_TEXTURE_2D, 0, 0, 0, overlayBitmap)

        gl.glUniform1i(compTextureHandle, 0)
        gl.glUniform1i(compMaskHandle, 1)
        gl.glUniform1i(compOverlayHandle, 2)

        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4)

        gl.glBindBuffer(gl.GL_ARRAY_BUFFER, 0)
        gl.glBindVertexArray(0)
    }

    private fun drawFilledPolygon(
        faceLandmarks: List<NormalizedLandmark>,
        points: List<Int>,
        colorArray: FloatArray
    ) {
        gl.glUniform4fv(polyColorHandle, 1, colorArray, 0)

        val requiredCapacity = points.size * 2
        if (polygonBuffer == null || polygonBuffer!!.capacity() < requiredCapacity) {
            polygonBuffer = ByteBuffer.allocateDirect(requiredCapacity * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }

//        polygonBuffer!!.clear()
        for (i in points.indices) {
            val landmark = faceLandmarks[points[i]]
            polygonBuffer!!.put((landmark.x() * 2f) - 1f)
            polygonBuffer!!.put(1f - (landmark.y() * 2f))
        }
        polygonBuffer!!.position(0)

        gl.glEnableVertexAttribArray(polyPositionHandle)
        gl.glVertexAttribPointer(polyPositionHandle, 2, gl.GL_FLOAT, false, 0, polygonBuffer)
        gl.glDrawArrays(gl.GL_TRIANGLE_FAN, 0, points.size)
        gl.glDisableVertexAttribArray(polyPositionHandle)
    }

    override fun release() {
        if (oesProgram != 0) {
            gl.glDeleteProgram(oesProgram)
            oesProgram = 0
        }
        if (polygonProgram != 0) {
            gl.glDeleteProgram(polygonProgram)
            polygonProgram = 0
        }
        if (compositeProgram != 0) {
            gl.glDeleteProgram(compositeProgram)
            compositeProgram = 0
        }
        if (cameraTex2D[0] != 0) {
            gl.glDeleteTextures(1, cameraTex2D, 0)
            cameraTex2D[0] = 0
        }
        if (cameraFbo[0] != 0) {
            gl.glDeleteFramebuffers(1, cameraFbo, 0)
            cameraFbo[0] = 0
        }
        if (maskTexture[0] != 0) {
            gl.glDeleteTextures(1, maskTexture, 0)
            maskTexture[0] = 0
        }
        if (overlayTexture[0] != 0) {
            gl.glDeleteTextures(1, overlayTexture, 0)
            overlayTexture[0] = 0
        }
        if (maskFbo[0] != 0) {
            gl.glDeleteFramebuffers(1, maskFbo, 0)
            maskFbo[0] = 0
        }
        if (vao[0] != 0) {
            gl.glDeleteVertexArrays(1, vao, 0)
            vao[0] = 0
        }
        if (vbo[0] != 0) {
            gl.glDeleteBuffers(1, vbo, 0)
            vbo[0] = 0
        }
    }
}