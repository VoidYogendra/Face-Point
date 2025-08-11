#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTexPosition;
uniform samplerExternalOES uTexture;
uniform sampler2D lut;
uniform float size;
void main() {
    vec3 color = texture2D(uTexture, vTexPosition).rgb;

        // Calculate LUT lookup coordinates
        float blueIndex = color.b * 15.0; // Scale B to range [0, 15]
        float row = floor(blueIndex);
        float fraction = fract(blueIndex);

        // Compute UVs for the LUT lookup
        vec2 lutUV1 = vec2(color.r * 0.5375, (color.g * 0.5375) + (row * 0.0625));
        vec2 lutUV2 = vec2(color.r * 0.5375, (color.g * 0.5375) + ((row + 1.0) * 0.0625));

        // Sample LUT colors from both layers
        vec3 lutColor1 = texture2D(lut, lutUV1).rgb;
        vec3 lutColor2 = texture2D(lut, lutUV2).rgb;

        // Interpolate between layers
        vec3 finalColor = mix(lutColor1, lutColor2, fraction);

        gl_FragColor = vec4(finalColor, 0.5);
    //gl_FragColor = texture2D(uTexture, vTexPosition);
}
