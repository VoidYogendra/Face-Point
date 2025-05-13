package com.avoid.facepoint.render

import android.opengl.GLES31 as gl
import com.google.common.collect.ImmutableSet
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutioncore.ResultGlRenderer
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A custom implementation of [ResultGlRenderer] to render [FaceMeshResult].  */
class FaceMeshEyeRect : ResultGlRenderer<FaceMeshResult?> {

    private var program = 0
    private var positionHandle = 0
    private var projectionMatrixHandle = 0
    private var colorHandle = 0

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = gl.glCreateShader(type)
        gl.glShaderSource(shader, shaderCode)
        gl.glCompileShader(shader)
        return shader
    }

    init {
        setupRendering()
    }

    override fun setupRendering() {
        program = gl.glCreateProgram()
        val vertexShader = loadShader(gl.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(gl.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        gl.glAttachShader(program, vertexShader)
        gl.glAttachShader(program, fragmentShader)
        gl.glLinkProgram(program)
        positionHandle = gl.glGetAttribLocation(program, "vPosition")
        projectionMatrixHandle = gl.glGetUniformLocation(program, "uProjectionMatrix")
        colorHandle = gl.glGetUniformLocation(program, "uColor")
    }

    override fun renderResult(result: FaceMeshResult?, projectionMatrix: FloatArray) {
        if (result == null) {
            return
        }
        gl.glUseProgram(program)
        gl.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)

        val numFaces = result.multiFaceLandmarks().size
        for (i in 0 until numFaces) {
            val leftEyeBox = getBoundingBox(leftEyeLandmarks.map { result.multiFaceLandmarks()[0].landmarkList[it] })
            val rightEyeBox = getBoundingBox(rightEyeLandmarks.map { result.multiFaceLandmarks()[0].landmarkList[it] })

            val squareVertices = floatArrayOf(
                leftEyeBox.first[0], leftEyeBox.first[1],
                rightEyeBox.second[0], rightEyeBox.first[1],
                rightEyeBox.second[0], rightEyeBox.second[1],
                leftEyeBox.first[0], leftEyeBox.second[1]
            )

// Use OpenGL to draw the quad
            val vertexBuffer = ByteBuffer.allocateDirect(squareVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(squareVertices)
            vertexBuffer.position(0)

            gl.glEnableVertexAttribArray(positionHandle)
            gl.glVertexAttribPointer(positionHandle, 2, gl.GL_FLOAT, false, 0, vertexBuffer)
            gl.glDrawArrays(gl.GL_TRIANGLE_FAN, 0, 4)

        }
    }

    fun getBoundingBox(landmarks: List<NormalizedLandmark>): Pair<FloatArray, FloatArray> {
        landmarks
        val minX = landmarks.minByOrNull { it.x }!!.x
        val maxX = landmarks.maxByOrNull { it.x }!!.x
        val minY = landmarks.minByOrNull { it.y }!!.y
        val maxY = landmarks.maxByOrNull { it.y }!!.y
        return Pair(floatArrayOf(minX, minY), floatArrayOf(maxX, maxY))
    }


    /**
     * Deletes the shader program.
     *
     *
     * This is only necessary if one wants to release the program while keeping the context around.
     */
    fun release() {
        gl.glDeleteProgram(program)
    }

    companion object {
        private const val TAG = "FaceMeshResultGlRenderer"

        val leftEyeLandmarks = listOf(472, 133, 160, 470) // Example left eye points
        val rightEyeLandmarks = listOf(477, 263, 387, 475)

        private const val VERTEX_SHADER = ("uniform mat4 uProjectionMatrix;\n"
                + "attribute vec4 vPosition;\n"
                + "void main() {\n"
                + "  gl_Position = uProjectionMatrix * vPosition;\n"
                + "}")
        private const val FRAGMENT_SHADER = ("precision mediump float;\n"
                + "uniform vec4 uColor;\n"
                + "void main() {\n"
                + "  gl_FragColor = uColor;\n"
                + "}")

        //        private val TESSELATION_COLOR = floatArrayOf(0.75f, 0.75f, 0.75f, 0.5f)
//        private const val TESSELATION_THICKNESS = 5
        private val RIGHT_EYE_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
        private const val RIGHT_EYE_THICKNESS = 8

        //        private val RIGHT_EYEBROW_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
//        private const val RIGHT_EYEBROW_THICKNESS = 8
        private val LEFT_EYE_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)
        private const val LEFT_EYE_THICKNESS = 8

        //        private val LEFT_EYEBROW_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)
//        private const val LEFT_EYEBROW_THICKNESS = 8
//        private val FACE_OVAL_COLOR = floatArrayOf(0.9f, 0.9f, 0.9f, 1f)
//        private const val FACE_OVAL_THICKNESS = 8
        private val LIPS_COLOR = floatArrayOf(0.9f, 0.9f, 0.9f, 1f)
        private val LIPS_POINT_COLOR = floatArrayOf(1f, 0f, 0f, 1f)
        private const val LIPS_THICKNESS = 8
    }
}