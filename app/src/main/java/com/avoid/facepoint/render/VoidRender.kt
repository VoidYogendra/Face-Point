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

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import androidx.core.graphics.createBitmap
import com.avoid.facepoint.model.FilterTypes
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import java.util.Queue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES31 as gl

class VoidRender(val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "VoidRender"
        const val FLOAT_SIZE_BYTES = 4
        const val STRIDES = 4 * FLOAT_SIZE_BYTES
        private external fun loadLUT(assetManager: AssetManager, mFile: String): Boolean
        external fun createTextureLUT2D(textureID: Int, lutID: Int): Int
        external fun makeKHR(textureID: Int)
        external fun useKHR(textureID: Int)
        const val GL_TEXTURE_EXTERNAL_OES: Int = 36197
        const val DEFAULT_FRAMEBUFFER: Int = 0
    }

    fun loadLUT(file: String) {
        loadLUT(context.assets, file)
    }

    var frame = 0
    var frameListener: (() -> Unit)? = null

    var filterTypes: FilterTypes = FilterTypes.DEFAULT

    private var surfaceTexture: SurfaceTexture? = null

    var width = 0
    var height = 0
    private var textureWidth = 0
    private var textureHeight = 0

    var cameraWidth = 0
    var cameraHeight = 0
    var onDrawCallback: Queue<Runnable> = LinkedList()
    var eglContext: EGLContext? = null
        private set


    private var matrixHandle2D = 0 /*yes this is dangling value but since later created test*/

    var textures = IntArray(1)
    private val textures2D = IntArray(1)

    var textureID = -1
    private var textureID2D = -1

    private var aspectMatrix = FloatArray(16)

    val glTextureManager = GLTextureManager(context)

    private var matrix = MatrixCalc()
    private var maskMatrix = FloatArray(16)

    var currentOesFilter: GPUFilter? = null
    var current2DFilter: GPUFilter? = null

    fun setFilters(oes: GPUFilter, twoD: GPUFilter) {
        onDrawCallback.add {
            currentOesFilter?.release()
            current2DFilter?.release()

            currentOesFilter = oes
            current2DFilter = twoD

            currentOesFilter?.onSurfaceCreated()
            current2DFilter?.onSurfaceCreated()

            currentOesFilter?.onSurfaceChanged(cameraWidth, cameraHeight, aspectMatrix)
            val scaleMatrix = FloatArray(16)
            Matrix.setIdentityM(scaleMatrix, 0)
            current2DFilter?.onSurfaceChanged(width, height, scaleMatrix)
        }
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        textures = IntArray(1)
        gl.glGenTextures(1, textures, 0)
        textureID = textures[0]

        gl.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)
        gl.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR)
        gl.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR)

        if (surfaceTexture == null) {
            surfaceTexture = SurfaceTexture(textureID)
        }

        eglContext = EGL14.eglGetCurrentContext()
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        gl.glViewport(0, 0, width, height)

        textureID2D = createRenderTexture()

        matrix.surface(width, height)
        matrix.frame(width, height)
        maskMatrix = matrix.doIT(maskMatrix)
    }

    fun onSurfaceChanged(width: Int, height: Int) {

        this.textureWidth = width
        this.textureHeight = height

        gl.glTexImage2D(
            gl.GL_TEXTURE_2D,
            0,
            gl.GL_RGBA,
            textureWidth,
            textureHeight,
            0,
            gl.GL_RGBA,
            gl.GL_UNSIGNED_BYTE,
            null
        )
        gl.glFramebufferTexture2D(
            gl.GL_FRAMEBUFFER,
            gl.GL_COLOR_ATTACHMENT0,
            gl.GL_TEXTURE_2D,
            textures2D[0],
            0
        )

        glTextureManager.initForRecord(width, height)
    }

    fun resize(textureWidth: Int, textureHeight: Int, screenWidth: Int, screenHeight: Int) {
        cameraWidth = textureWidth
        cameraHeight = textureHeight

        onDrawCallback.add {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)
            onSurfaceChanged(screenWidth, screenHeight)
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, DEFAULT_FRAMEBUFFER)
        }

        surfaceTexture?.setDefaultBufferSize(textureWidth, textureHeight)
        Matrix.setIdentityM(aspectMatrix, 0)

        currentOesFilter?.onSurfaceChanged(cameraWidth, cameraHeight, aspectMatrix)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        current2DFilter?.onSurfaceChanged(width, height, scaleMatrix)
    }

    fun rotate(flip: Boolean) {
        onDrawCallback.add {
            if (flip) {
                Matrix.scaleM(aspectMatrix, 0, -1.0f, 1.0f, 1.0f)
                Matrix.setRotateM(aspectMatrix, 0, 90f, 0f, 0f, 1.0f)
            } else {
                Matrix.setRotateM(aspectMatrix, 0, -90f, 0f, 0f, 1.0f)
                Matrix.scaleM(aspectMatrix, 0, 1.0f, -1.0f, 1.0f)
            }
            currentOesFilter?.onSurfaceChanged(cameraWidth, cameraHeight, aspectMatrix)
        }
    }

    fun rotateVideo() {
        onDrawCallback.add {
            Matrix.setRotateM(aspectMatrix, 0, 180f, 0f, 0f, 1.0f)
            Matrix.scaleM(aspectMatrix, 0, -1.0f, 1.0f, 1.0f)

            currentOesFilter?.onSurfaceChanged(cameraWidth, cameraHeight, aspectMatrix)
        }
    }

    override fun onDrawFrame(p0: GL10?) {
        //so it does not render during new setup
        synchronized(onDrawCallback) {
            while (onDrawCallback.isNotEmpty()) {
                onDrawCallback.poll()?.run()
            }
        }
        if (framebufferName != DEFAULT_FRAMEBUFFER) { // <- draw to fbo
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)
            onDrawToFbo(textures[0])
        }
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, DEFAULT_FRAMEBUFFER)// <- drawFBO() contains camera texture pixels which fbo (framebufferName/framebufferRecord)
        // drew to textureID2D and then draw textureID2D to screen(DEFAULT_FRAMEBUFFER)
        drawFBO()
        glTextureManager.onDrawForRecord(glTextureManager.recordTexture)
        if (glTextureManager.framebufferRecord != DEFAULT_FRAMEBUFFER) {
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, glTextureManager.framebufferRecord)
            drawFBO()
        }
        frame++
        frameListener?.invoke()
    }

    private fun drawFBO() {
        current2DFilter?.onDraw(textureID2D)
    }


    fun onDrawToFbo(texID: Int) {
        surfaceTexture?.updateTexImage()
        currentOesFilter?.onDraw(texID)
    }

    fun saveFrame(file: File, width: Int, height: Int) {
        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.
        val filename = file.toString()
        val buf = ByteBuffer.allocateDirect(width * height * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        GLES20.glReadPixels(
            0, 0, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
        )
//        checkGlError("glReadPixels")
        buf.rewind()

        val bos = BufferedOutputStream(FileOutputStream(filename))
        bos.use { bos ->
            val bmp = createBitmap(width, height)
            bmp.copyPixelsFromBuffer(buf)
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            bmp.recycle()
        }
        Log.d("IDK GG", "Saved " + width + "x" + height + " frame as '" + filename + "'")
    }

    //for recording only
    private var framebufferName = 0
    private fun createRenderTexture(): Int {
        val args = IntArray(1)

        gl.glGetIntegerv(gl.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        gl.glGetIntegerv(gl.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]

        gl.glGenFramebuffers(args.size, args, 0)
        framebufferName = args[0]
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebufferName)

        gl.glGenTextures(1, textures2D, 0)

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

        gl.glTexImage2D(
            gl.GL_TEXTURE_2D,
            0,
            gl.GL_RGBA,
            512,
            512,
            0,
            gl.GL_RGBA,
            gl.GL_UNSIGNED_BYTE,
            null
        )
        gl.glFramebufferTexture2D(
            gl.GL_FRAMEBUFFER,
            gl.GL_COLOR_ATTACHMENT0,
            gl.GL_TEXTURE_2D,
            textures2D[0],
            0
        )

        val status = gl.glCheckFramebufferStatus(gl.GL_FRAMEBUFFER)
        if (status != gl.GL_FRAMEBUFFER_COMPLETE) {
            throw java.lang.RuntimeException("Failed to initialize framebuffer object $status")
        }

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, saveFramebuffer)
        gl.glBindTexture(gl.GL_TEXTURE_2D, saveTexName)

        return textures2D[0]
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        return surfaceTexture
    }
}

interface GPUFilter {
    fun onSurfaceCreated()

    fun onSurfaceChanged(width: Int, height: Int, aspectMatrix: FloatArray)

    fun onDraw(textureID: Int)

    fun release()
}