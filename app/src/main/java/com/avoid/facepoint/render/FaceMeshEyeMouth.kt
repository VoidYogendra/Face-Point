package com.avoid.facepoint.render

import android.opengl.GLES20
import com.google.common.collect.ImmutableSet
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutioncore.ResultGlRenderer
import com.google.mediapipe.solutions.facemesh.FaceMeshConnections
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A custom implementation of [ResultGlRenderer] to render [FaceMeshResult].  */
class FaceMeshEyeMouth : ResultGlRenderer<FaceMeshResult?> {

    private var program = 0
    private var positionHandle = 0
    private var projectionMatrixHandle = 0
    private var colorHandle = 0

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    init {
        setupRendering()
    }

    override fun setupRendering() {
        program = GLES20.glCreateProgram()
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    override fun renderResult(result: FaceMeshResult?, projectionMatrix: FloatArray) {
        if (result == null) {
            return
        }
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)

        val numFaces = result.multiFaceLandmarks().size
        for (i in 0 until numFaces) {
//            drawLandmarks(
//                result.multiFaceLandmarks()[i].landmarkList,
//                FaceMeshConnections.FACEMESH_TESSELATION,
//                TESSELATION_COLOR,
//                TESSELATION_THICKNESS
//            )
            drawLandmarks(
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_RIGHT_EYE,
                RIGHT_EYE_COLOR,
                RIGHT_EYE_THICKNESS
            )
//            drawLandmarks(
//                result.multiFaceLandmarks()[i].landmarkList,
//                FaceMeshConnections.FACEMESH_RIGHT_EYEBROW,
//                RIGHT_EYEBROW_COLOR,
//                RIGHT_EYEBROW_THICKNESS
//            )
            drawLandmarks(
                result.multiFaceLandmarks()[i].landmarkList,
                FaceMeshConnections.FACEMESH_LEFT_EYE,
                LEFT_EYE_COLOR,
                LEFT_EYE_THICKNESS
            )
//            drawLandmarks(
//                result.multiFaceLandmarks()[i].landmarkList,
//                FaceMeshConnections.FACEMESH_LEFT_EYEBROW,
//                LEFT_EYEBROW_COLOR,
//                LEFT_EYEBROW_THICKNESS
//            )
//            drawLandmarks(
//                result.multiFaceLandmarks()[i].landmarkList,
//                FaceMeshConnections.FACEMESH_FACE_OVAL,
//                FACE_OVAL_COLOR,
//                FACE_OVAL_THICKNESS
//            )
            drawLandmarksInPairs(
                result.multiFaceLandmarks()[i].landmarkList,
                FACEMESH_LIPS,
                LIPS_COLOR,
                LIPS_THICKNESS
            )
//            if (result.multiFaceLandmarks()[i].landmarkCount
//                == FaceMesh.FACEMESH_NUM_LANDMARKS_WITH_IRISES
//            ) {
//                drawLandmarks(
//                    result.multiFaceLandmarks()[i].landmarkList,
//                    FaceMeshConnections.FACEMESH_RIGHT_IRIS,
//                    RIGHT_EYE_COLOR,
//                    RIGHT_EYE_THICKNESS
//                )
//                drawLandmarks(
//                    result.multiFaceLandmarks()[i].landmarkList,
//                    FaceMeshConnections.FACEMESH_LEFT_IRIS,
//                    LEFT_EYE_COLOR,
//                    LEFT_EYE_THICKNESS
//                )
//            }
        }
    }

    private fun drawLandmarks(
        faceLandmarkList: List<NormalizedLandmark>,
        connections: ImmutableSet<FaceMeshConnections.Connection>,
        colorArray: FloatArray,
        thickness: Int
    ) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        GLES20.glLineWidth(thickness.toFloat())
        for (c in connections) {
            val start = faceLandmarkList[c.start()]
            val end = faceLandmarkList[c.end()]
            val vertex = floatArrayOf(start.x, start.y, end.x, end.y)

            //      Log.e(TAG, "drawLandmarks: "+vertex[0]+" "+vertex[1]+" "+vertex[2]+" "+vertex[3] );
            val vertexBuffer =
                ByteBuffer.allocateDirect(vertex.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertex)
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }
    }

    private fun drawLandmarksInPairs(
        faceLandmarkList: List<NormalizedLandmark>,
        connections: ImmutableSet<Pair<Int, Int>>,
        colorArray: FloatArray,
        thickness: Int
    ) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        GLES20.glLineWidth(thickness.toFloat())
        for (c in connections) {
            val (a, b) = c
            val start = faceLandmarkList[a]
            val end = faceLandmarkList[b]
            val vertex = floatArrayOf(start.x, start.y, end.x, end.y)

            //      Log.e(TAG, "drawLandmarks: "+vertex[0]+" "+vertex[1]+" "+vertex[2]+" "+vertex[3] );
            val vertexBuffer =
                ByteBuffer.allocateDirect(vertex.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertex)
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }
    }

    /**
     * Deletes the shader program.
     *
     *
     * This is only necessary if one wants to release the program while keeping the context around.
     */
    fun release() {
        GLES20.glDeleteProgram(program)
    }

    companion object {
        private const val TAG = "FaceMeshResultGlRenderer"

        val FACEMESH_LIPS: ImmutableSet<Pair<Int, Int>> = ImmutableSet.of(
            Pair(61, 146),
            Pair(146, 91),
            Pair(91, 181),
            Pair(181, 84),
            Pair(84, 17),
            Pair(17, 314),
            Pair(314, 405),
            Pair(405, 321),
            Pair(321, 375),
            Pair(375, 291),
            Pair(61, 185),
            Pair(185, 40),
            Pair(40, 39),
            Pair(39, 37),
            Pair(37, 0),
            Pair(0, 267),
            Pair(267, 269),
            Pair(269, 270),
            Pair(270, 409),
            Pair(409, 291),
//            Pair(78, 95),
//            Pair(95, 88),
//            Pair(88, 178),
//            Pair(178, 87),
//            Pair(87, 14),
//            Pair(14, 317),
//            Pair(317, 402),
//            Pair(402, 318),
//            Pair(318, 324),
//            Pair(324, 308),
//            Pair(78, 191),
//            Pair(191, 80),
//            Pair(80, 81),
//            Pair(81, 82),
//            Pair(82, 13),
//            Pair(13, 312),
//            Pair(312, 311),
//            Pair(311, 310),
//            Pair(310, 415),
//            Pair(415, 308)
        )

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
        private const val LIPS_THICKNESS = 8
    }
}