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

package com.avoid.facepoint.render

import android.opengl.Matrix
import kotlin.math.max
import kotlin.math.min

/*
* renderResult: B[4.314815, 0.0, 0.0, 0.0, 0.0, -2.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, -2.1574075, 1.0, -0.0, 1.0]
* f W 1080 f H 1080 s W 1080 s H 2330
* */
class MatrixCalc {
    private var frameWidth: Int = 1920 // Example: Texture width
    private var frameHeight: Int = 1080 // Example: Texture height
    private var surfaceWidth: Int = 1280 // Example: Screen width
    private var surfaceHeight: Int = 720 // Example: Screen height
    private var alignmentHorizontal: Float = 0.5f // Center alignment
    private var alignmentVertical: Float = 0.5f // Center alignment
    private var zoomFactor: Float = 1.0f // No zoom
    private var zoomLocationX: Float = 0.5f // Default zoom location
    private var zoomLocationY: Float = 0.5f // Default zoom location
    private var scaleMode: String =
        "FIT_TO_WIDTH" // Options: "FILL", "FIT", "FIT_TO_WIDTH", "FIT_TO_HEIGHT"

    fun surface(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    fun frame(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
    }

    fun calculateTextureBoundary(
        frameWidth: Int, frameHeight: Int,
        surfaceWidth: Int, surfaceHeight: Int,
        alignmentHorizontal: Float, alignmentVertical: Float,
        zoomFactor: Float, zoomLocationX: Float, zoomLocationY: Float,
        scaleMode: String
    ): FloatArray {
        var scaleWidth = if (frameWidth > 0) surfaceWidth.toFloat() / frameWidth else 1.0f
        var scaleHeight = if (frameHeight > 0) surfaceHeight.toFloat() / frameHeight else 1.0f
        var scale = max(scaleWidth.toDouble(), scaleHeight.toDouble()).toFloat()

        if (scaleMode == "FIT" ||
            (scaleMode == "FIT_TO_WIDTH" && frameWidth > frameHeight) ||
            (scaleMode == "FIT_TO_HEIGHT" && frameHeight > frameWidth)
        ) {
            scale = min(scaleWidth.toDouble(), scaleHeight.toDouble()).toFloat()
        }

        scaleWidth /= scale
        scaleHeight /= scale

        var textureLeft = (1.0f - scaleWidth) * alignmentHorizontal
        var textureRight = textureLeft + scaleWidth
        var textureBottom = (1.0f - scaleHeight) * alignmentVertical
        var textureTop = textureBottom + scaleHeight

        // Apply zoom adjustments
        textureLeft = (textureLeft - 0.5f) / zoomFactor + zoomLocationX
        textureRight = (textureRight - 0.5f) / zoomFactor + zoomLocationX
        textureBottom = (textureBottom - 0.5f) / zoomFactor + zoomLocationY
        textureTop = (textureTop - 0.5f) / zoomFactor + zoomLocationY

        return floatArrayOf(textureLeft, textureRight, textureBottom, textureTop)
    }

    fun doIT(projectionMatrix:FloatArray): FloatArray {
        Matrix.setIdentityM(projectionMatrix, 0)
        /*
    * [4.314815, 0.0, 0.0, 0.0, 0.0, -2.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, -2.1574075, 1.0, -0.0, 1.0]
    * */
        val textureBoundary = calculateTextureBoundary(
            frameWidth, frameHeight,
            surfaceWidth, surfaceHeight,
            alignmentHorizontal, alignmentVertical,
            zoomFactor, zoomLocationX, zoomLocationY,
            scaleMode
        )

        Matrix.orthoM(
            projectionMatrix,
            0,
            textureBoundary[0],
            textureBoundary[1],
            textureBoundary[3],
            textureBoundary[2],
            -1.0f,
            1.0f
        )
        return projectionMatrix
    }
}