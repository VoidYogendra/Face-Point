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
import android.graphics.Bitmap
import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import com.avoid.facepoint.render.VoidRender.Companion.makeKHR
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLTextureManager(private val context: Context) {
    companion object {
        private const val TAG = "GLRecord"
        private const val FLOAT_SIZE_BYTES = 4
        private const val STRIDES = 4 * FLOAT_SIZE_BYTES
    }
    private val vertexData2D = floatArrayOf(
        // Position (x, y)   // Texture (u, v)
        -1f, -1f, 0f, 0f,  // Bottom-left
        1f, -1f, 1f, 0f,  // Bottom-right
        -1f, 1f, 0f, 1f,  // Top-left
        1f, 1f, 1f, 1f   // Top-right
    )

    private var vertexShader2DRec = 0


    private var fragmentShaderRec = 0


    private var program2DRec = 0


    private var positionHandle2DRec = 0

    private var texturePositionHandle2DRec = 0

    private var textureHandle2DRec = 0

    private var matrixHandle2DRec = 0

    val vao2DRec = IntArray(1)
    val vbo2DRec = IntArray(1)


    var aspectMatrix2DRec = FloatArray(16)

    var framebufferRecord = -1
    var recordTexture = -1

    fun initForRecord(width: Int, height: Int) {
        vertexShader2DRec = compileShader(GLES31.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        fragmentShaderRec = compileShader(GLES31.GL_FRAGMENT_SHADER, "shader/main_frag.glsl")
        program2DRec = GLES31.glCreateProgram()
        GLES31.glAttachShader(program2DRec, vertexShader2DRec)
        GLES31.glAttachShader(program2DRec, fragmentShaderRec)
        GLES31.glLinkProgram(program2DRec)
        GLES31.glUseProgram(program2DRec) //use it in Codec

        positionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aPosition")
        texturePositionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aTexPosition")
        textureHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "uTexture")
        matrixHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "u_Matrix")


        val args = IntArray(1)

        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]


        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        GLES31.glGenFramebuffers(args.size, args, 0)
        framebufferRecord = args[0]

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, framebufferRecord)

        GLES31.glGenTextures(1, args, 0)
        recordTexture = args[0]

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, recordTexture)
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MIN_FILTER,
            GLES31.GL_LINEAR
        )
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MAG_FILTER,
            GLES31.GL_LINEAR
        )

        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D,
            0,
            GLES31.GL_RGBA,
            width,
            height,
            0,
            GLES31.GL_RGBA,
            GLES31.GL_UNSIGNED_BYTE,
            null
        )
        GLES31.glFramebufferTexture2D(
            GLES31.GL_FRAMEBUFFER,
            GLES31.GL_COLOR_ATTACHMENT0,
            GLES31.GL_TEXTURE_2D,
            recordTexture,
            0
        )

        val status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)
        if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            throw java.lang.RuntimeException("Failed to initialize framebuffer object $status")
        }

        //-----------------------------------------------------------------------
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //generate vao vbo
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //bind vao vbo
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

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
            positionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        GLES31.glEnableVertexAttribArray(positionHandle2DRec)

        GLES31.glVertexAttribPointer(
            texturePositionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        GLES31.glEnableVertexAttribArray(texturePositionHandle2DRec)

        //-----------------------------------------------------------------------

//        GLES31.glViewport(0, 0, width, height)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix2DRec = scaleMatrix
        GLES31.glUniformMatrix4fv(matrixHandle2DRec, 1, false, aspectMatrix2DRec, 0)

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, saveFramebuffer)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, saveTexName)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
    }

    fun initForKHR(width: Int, height: Int) {
        vertexShader2DRec = compileShader(GLES31.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        fragmentShaderRec = compileShader(GLES31.GL_FRAGMENT_SHADER, "shader/main_frag.glsl")
        program2DRec = GLES31.glCreateProgram()
        GLES31.glAttachShader(program2DRec, vertexShader2DRec)
        GLES31.glAttachShader(program2DRec, fragmentShaderRec)
        GLES31.glLinkProgram(program2DRec)
        GLES31.glUseProgram(program2DRec) //use it in Codec

        positionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aPosition")
        texturePositionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aTexPosition")
        textureHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "uTexture")
        matrixHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "u_Matrix")


        val args = IntArray(1)

        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        val saveFramebuffer = args[0]
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        val saveTexName = args[0]


        GLES31.glGetIntegerv(GLES31.GL_FRAMEBUFFER_BINDING, args, 0)
        GLES31.glGetIntegerv(GLES31.GL_TEXTURE_BINDING_2D, args, 0)
        GLES31.glGenFramebuffers(args.size, args, 0)
        framebufferRecord = args[0]

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, framebufferRecord)

        GLES31.glGenTextures(1, args, 0)
        recordTexture = args[0]

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, recordTexture)
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MIN_FILTER,
            GLES31.GL_LINEAR
        )
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MAG_FILTER,
            GLES31.GL_LINEAR
        )

        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D,
            0,
            GLES31.GL_RGBA,
            width,
            height,
            0,
            GLES31.GL_RGBA,
            GLES31.GL_UNSIGNED_BYTE,
            null
        )
        GLES31.glFramebufferTexture2D(
            GLES31.GL_FRAMEBUFFER,
            GLES31.GL_COLOR_ATTACHMENT0,
            GLES31.GL_TEXTURE_2D,
            recordTexture,
            0
        )

        val status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)
        if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            throw java.lang.RuntimeException("Failed to initialize framebuffer object $status")
        }

        //-----------------------------------------------------------------------
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //generate vao vbo
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //bind vao vbo
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

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
            positionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        GLES31.glEnableVertexAttribArray(positionHandle2DRec)

        GLES31.glVertexAttribPointer(
            texturePositionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        GLES31.glEnableVertexAttribArray(texturePositionHandle2DRec)

        //-----------------------------------------------------------------------

//        GLES31.glViewport(0, 0, width, height)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        aspectMatrix2DRec = scaleMatrix
        GLES31.glUniformMatrix4fv(matrixHandle2DRec, 1, false, aspectMatrix2DRec, 0)
        makeKHR(recordTexture)
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, saveFramebuffer)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, saveTexName)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
    }


    fun initForUse(width: Int, height: Int) {
        vertexShader2DRec = compileShader(GLES31.GL_VERTEX_SHADER, "shader/main_vert.glsl")
        fragmentShaderRec = compileShader(GLES31.GL_FRAGMENT_SHADER, "shader/main_frag.glsl")
        program2DRec = GLES31.glCreateProgram()
        GLES31.glAttachShader(program2DRec, vertexShader2DRec)
        GLES31.glAttachShader(program2DRec, fragmentShaderRec)
        GLES31.glLinkProgram(program2DRec)
        GLES31.glUseProgram(program2DRec) //use it in Codec

        positionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aPosition")
        texturePositionHandle2DRec = GLES31.glGetAttribLocation(program2DRec, "aTexPosition")
        textureHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "uTexture")
        matrixHandle2DRec = GLES31.glGetUniformLocation(program2DRec, "u_Matrix")


        val args = IntArray(1)
        GLES31.glGenTextures(1, args, 0)
        recordTexture = args[0]

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, recordTexture)
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MIN_FILTER,
            GLES31.GL_LINEAR
        )
        GLES31.glTexParameteri(
            GLES31.GL_TEXTURE_2D,
            GLES31.GL_TEXTURE_MAG_FILTER,
            GLES31.GL_LINEAR
        )

        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D,
            0,
            GLES31.GL_RGBA,
            width,
            height,
            0,
            GLES31.GL_RGBA,
            GLES31.GL_UNSIGNED_BYTE,
            null
        )

        //-----------------------------------------------------------------------
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //generate vao vbo
        GLES31.glGenVertexArrays(1, vao2DRec, 0)
        GLES31.glGenBuffers(1, vbo2DRec, 0)
        //bind vao vbo
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

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
            positionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            0
        )
        GLES31.glEnableVertexAttribArray(positionHandle2DRec)

        GLES31.glVertexAttribPointer(
            texturePositionHandle2DRec,
            2,
            GLES31.GL_FLOAT,
            false,
            STRIDES,
            2 * FLOAT_SIZE_BYTES
        )
        GLES31.glEnableVertexAttribArray(texturePositionHandle2DRec)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
        //-----------------------------------------------------------------------

        GLES31.glViewport(0, 0, width, height)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)

        aspectMatrix2DRec = scaleMatrix
        GLES31.glUniformMatrix4fv(matrixHandle2DRec, 1, false, aspectMatrix2DRec, 0)
    }

    fun rotate(rotate:Boolean){
        if(rotate){
            Matrix.setRotateM(aspectMatrix2DRec, 0, 0f, 0f, 0f, 1.0f)
            Matrix.scaleM(aspectMatrix2DRec, 0, 1.0f, -1.0f, 1.0f)
        }
        else{
            Matrix.setRotateM(aspectMatrix2DRec, 0, 0f, 0f, 0f, 1.0f)
            Matrix.scaleM(aspectMatrix2DRec, 0, 1.0f, -1.0f, 1.0f)
        }
        GLES31.glUniformMatrix4fv(matrixHandle2DRec, 1, false, aspectMatrix2DRec, 0)
    }

    fun onDrawForRecord(texID: Int) {
        GLES31.glUseProgram(program2DRec)

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texID)
        GLES31.glUniform1i(textureHandle2DRec, 0)
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)

        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
    }

    fun onDrawForRecordBitmap(texID: Int,bitmap: Bitmap) {
        GLES31.glUseProgram(program2DRec)

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT or GLES31.GL_DEPTH_BUFFER_BIT)
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texID)
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D,0,bitmap,0)
        GLES31.glUniform1i(textureHandle2DRec, 0)
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)


        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
    }

    fun onDrawForBuffer(texID: Int,byteBuffer: ByteBuffer,width: Int,height: Int) {
        GLES31.glUseProgram(program2DRec)

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT or GLES31.GL_DEPTH_BUFFER_BIT)
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texID)
        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D,
            0,
            GLES31.GL_RGBA,
            width,
            height,
            0,
            GLES31.GL_RGBA,
            GLES31.GL_UNSIGNED_BYTE,
            byteBuffer
        )
        GLES31.glUniform1i(textureHandle2DRec, 0)
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)


        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0)
        GLES31.glBindVertexArray(0)
    }

    fun onDrawForKHR(texID: Int) {
        GLES31.glUseProgram(program2DRec)

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
        GLES31.glBindVertexArray(vao2DRec[0])
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo2DRec[0])

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texID)
        VoidRender.useKHR(0)
        GLES31.glUniform1i(textureHandle2DRec, 0)
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)

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