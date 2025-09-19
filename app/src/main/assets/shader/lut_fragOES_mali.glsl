#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vTexPosition;
uniform samplerExternalOES uTexture;
uniform sampler2D lut;
uniform float size;

void main() {
    vec3 color = texture2D(uTexture, vTexPosition).rgb;

    // Normalized LUT coordinates in [0 .. size-1]
    float sizeM1 = size - 1.0;
    float rIndex = clamp(color.r * sizeM1, 0.0, sizeM1);
    float gIndex = clamp(color.g * sizeM1, 0.0, sizeM1);
    float bIndex = clamp(color.b * sizeM1, 0.0, sizeM1);

    float b0 = floor(bIndex);
    float b1 = min(b0 + 1.0, sizeM1);
    float f = bIndex - b0;

    // Normalize coordinates for 2D LUT
    float invSize = 1.0 / size;
    float invSize2D = 1.0 / (size * size);

    // Remove 0.5 offset to align with 3D texture sampling
    float x = rIndex * invSize;
    float y0 = (gIndex + b0 * size) * invSize2D;
    float y1 = (gIndex + b1 * size) * invSize2D;

    vec3 c0 = texture2D(lut, vec2(x, y0)).rgb;
    vec3 c1 = texture2D(lut, vec2(x, y1)).rgb;
    vec3 finalColor = mix(c0, c1, f);

    gl_FragColor = vec4(finalColor, 1.0);
}