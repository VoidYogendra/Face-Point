/*

* Copyright 2025 VoidYogendra
        *
        * Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* ```
http://www.apache.org/licenses/LICENSE-2.0
```
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
        * limitations under the License.
*
* Project: Face Point
* Repository: [https://github.com/VoidYogendra/Face-Point](https://github.com/VoidYogendra/Face-Point)
*/

package com.avoid.facepoint.render.mpfilters

import android.opengl.GLES31 as gl
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutioncore.ResultGlRenderer
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
        gl.glClear(gl.GL_COLOR_BUFFER_BIT or gl.GL_DEPTH_BUFFER_BIT)
        gl.glUseProgram(program)
        gl.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)

        val numFaces = result.multiFaceLandmarks().size
        for (i in 0 until numFaces) {

            drawFilledPolygon(
                result.multiFaceLandmarks()[i].landmarkList,
                faceMeshLipsPoints,
                LIPS_COLOR
            )
            drawFilledPolygon(
                result.multiFaceLandmarks()[i].landmarkList,
                faceMeshLeftEyePoints,
                LIPS_COLOR
            )
            drawFilledPolygon(
                result.multiFaceLandmarks()[i].landmarkList,
                faceMeshRightEyePoints,
                LIPS_COLOR
            )
        }
    }

//    private fun drawLandmarks(
//        faceLandmarkList: List<NormalizedLandmark>,
//        connections: ImmutableSet<FaceMeshConnections.Connection>,
//        colorArray: FloatArray,
//        thickness: Int
//    ) {
//        gl.glUniform4fv(colorHandle, 1, colorArray, 0)
//        gl.glLineWidth(thickness.toFloat())
//        for (c in connections) {
//            val start = faceLandmarkList[c.start()]
//            val end = faceLandmarkList[c.end()]
//            val vertex = floatArrayOf(start.x, start.y, end.x, end.y)
//
//            //      Log.e(TAG, "drawLandmarks: "+vertex[0]+" "+vertex[1]+" "+vertex[2]+" "+vertex[3] );
//            val vertexBuffer =
//                ByteBuffer.allocateDirect(vertex.size * 4)
//                    .order(ByteOrder.nativeOrder())
//                    .asFloatBuffer()
//                    .put(vertex)
//            vertexBuffer.position(0)
//            gl.glEnableVertexAttribArray(positionHandle)
//            gl.glVertexAttribPointer(positionHandle, 2, gl.GL_FLOAT, false, 0, vertexBuffer)
//            gl.glDrawArrays(gl.GL_LINES, 0, 2)
//        }
//    }
//
//    private fun drawLandmarksInPairs(
//        faceLandmarkList: List<NormalizedLandmark>,
//        connections: ImmutableSet<Pair<Int, Int>>,
//        colorArray: FloatArray,
//        thickness: Int
//    ) {
//        gl.glUniform4fv(colorHandle, 1, colorArray, 0)
//        gl.glLineWidth(thickness.toFloat())
//        for (c in connections) {
//            val (a, b) = c
//            val start = faceLandmarkList[a]
//            val end = faceLandmarkList[b]
//            val vertex = floatArrayOf(start.x, start.y, end.x, end.y)
//
//            //      Log.e(TAG, "drawLandmarks: "+vertex[0]+" "+vertex[1]+" "+vertex[2]+" "+vertex[3] );
//            val vertexBuffer =
//                ByteBuffer.allocateDirect(vertex.size * 4)
//                    .order(ByteOrder.nativeOrder())
//                    .asFloatBuffer()
//                    .put(vertex)
//            vertexBuffer.position(0)
//            gl.glEnableVertexAttribArray(positionHandle)
//            gl.glVertexAttribPointer(positionHandle, 2, gl.GL_FLOAT, false, 0, vertexBuffer)
//            gl.glDrawArrays(gl.GL_LINES, 0, 2)
//        }
//    }
//    private fun drawPointInPairs(
//        faceLandmarkList: List<NormalizedLandmark>,
//        connections: ImmutableSet<Pair<Int, Int>>,
//        colorArray: FloatArray,
//        thickness: Int
//    ) {
//        gl.glUniform4fv(colorHandle, 1, colorArray, 0)
//        gl.glLineWidth(thickness.toFloat())
//        for (c in connections) {
//            val (a, b) = c
//            val start = faceLandmarkList[a]
//            val end = faceLandmarkList[b]
//            val vertex = floatArrayOf(0.5f, 0.5f,0.5f, 0.5f)
//
//            val vertexBuffer =
//                ByteBuffer.allocateDirect(vertex.size * 4)
//                    .order(ByteOrder.nativeOrder())
//                    .asFloatBuffer()
//                    .put(vertex)
//            vertexBuffer.position(0)
//            gl.glEnableVertexAttribArray(positionHandle)
//            gl.glVertexAttribPointer(positionHandle, 2, gl.GL_FLOAT, false, 0, vertexBuffer)
//            gl.glDrawArrays(gl.GL_POINTS, 0, 1)
//        }
//    }
    private fun drawFilledPolygon(
        faceLandmarkList: List<NormalizedLandmark>,
        points: List<Int>,
        colorArray: FloatArray
    ) {
        gl.glUniform4fv(colorHandle, 1, colorArray, 0)

        val vertex = FloatArray(points.size * 2)
        for (i in points.indices) {
            val landmark = faceLandmarkList[points[i]]
            vertex[i * 2] = landmark.x
            vertex[i * 2 + 1] = landmark.y
        }

        val vertexBuffer = ByteBuffer.allocateDirect(vertex.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertex)
        vertexBuffer.position(0)

        gl.glEnableVertexAttribArray(positionHandle)
        gl.glVertexAttribPointer(positionHandle, 2, gl.GL_FLOAT, false, 0, vertexBuffer)
        gl.glDrawArrays(gl.GL_TRIANGLE_FAN, 0, points.size)
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

        val faceMeshLipsPoints = listOf(
            61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
            185, 40, 39, 37, 0, 267, 269, 270, 409,291
        )
        val faceMeshLeftEyePoints = listOf(
            263, 249, 390, 373, 374, 380, 381, 382, 362,
            466, 388, 387, 386, 385, 384, 398,362
        )
        val faceMeshRightEyePoints = listOf(
            33, 7, 163, 144, 145, 153, 154, 155, 133,
            246, 161, 160, 159, 158, 157, 173,133
        )


//        val FACE_MESH_LIPS: ImmutableSet<Pair<Int, Int>> = ImmutableSet.of(
//            Pair(61, 146),
//            Pair(146, 91),
//            Pair(91, 181),
//            Pair(181, 84),
//            Pair(84, 17),
//            Pair(17, 314),
//            Pair(314, 405),
//            Pair(405, 321),
//            Pair(321, 375),
//
//            Pair(375, 291),
//            Pair(61, 185),
//            Pair(185, 40),
//            Pair(40, 39),
//            Pair(39, 37),
//            Pair(37, 0),
//            Pair(0, 267),
//
//            Pair(267, 269),
//            Pair(269, 270),
//            Pair(270, 409),
//            Pair(409, 291),
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
//        )

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

        private val LIPS_COLOR = floatArrayOf(0.9f, 0.9f, 0.9f, 1f)//white
    }
}