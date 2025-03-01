package com.avoid.facepoint.render

import android.view.Choreographer

class VsyncCallBack(
    private val onFrame: (Long) -> Unit
) : Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()

    fun start() {
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        onFrame(frameTimeNanos)
        choreographer.postFrameCallback(this) // Schedule next frame
    }

}