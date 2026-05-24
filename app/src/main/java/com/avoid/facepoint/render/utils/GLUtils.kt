package com.avoid.facepoint.render.utils

import android.content.res.AssetManager
import android.opengl.GLES31 as gl

object GLUtils {
    fun compileShader(assets: AssetManager, type: Int, file: String): Int {
        val code = assets.open(file).bufferedReader().useLines {
            it.joinToString("\n")
        }
        val shader = gl.glCreateShader(type)
        gl.glShaderSource(shader, code)
        gl.glCompileShader(shader)
        val status = IntArray(1)
        gl.glGetShaderiv(shader, gl.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            gl.glDeleteShader(shader)
            val shaderType =
                if (type == gl.GL_VERTEX_SHADER) "GL_VERTEX_SHADER" else "GL_FRAGMENT_SHADER"
            throw RuntimeException("Failed: ${gl.glGetShaderInfoLog(shader)} type $shaderType \n $code")
        }
        return shader
    }
}