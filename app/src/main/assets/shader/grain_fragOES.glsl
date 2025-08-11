#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform vec2      iResolution;           // viewport resolution (in pixels)
uniform float     iTime;                 // shader playback time (in seconds)
uniform samplerExternalOES uTexture;
varying vec2 vTexPosition;
void main() {
    vec4 color = texture2D(uTexture, vTexPosition);
     gl_FragColor = vec4((1.0 - color.rgb), color.w);
}