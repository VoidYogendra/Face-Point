#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform vec2      iResolution;           // viewport resolution (in pixels)
uniform float     iTime;                 // shader playback time (in seconds)
uniform samplerExternalOES uTexture;
varying vec2 vTexPosition;
void main() {
vec2 uv = vTexPosition.xy / iResolution.xy;

    vec4 color = texture2D(uTexture, uv);

    float strength = 16.0;

    float x = (uv.x + 4.0 ) * (uv.y + 4.0 ) * (iTime * 10.0);
	vec4 grain = vec4(mod((mod(x, 13.0) + 1.0) * (mod(x, 123.0) + 1.0), 0.01)-0.005) * strength;

    if(abs(uv.x - 0.5) < 0.002)
        color = vec4(0.0);
	gl_FragColor = color + grain;
}