precision mediump float;

varying vec2 vTexPosition;
uniform sampler2D uTexture;

uniform vec2 center1;  // Right eye bulge center
uniform vec2 center2;  // Left eye bulge center
uniform float radius;
uniform float scale;

void main() {
    vec2 texCoord = vTexPosition;

    // Apply bulge for the first center (right eye)
    vec2 offset1 = texCoord - center1;
    float dist1 = length(offset1);
    if (dist1 < radius) {
        float percent1 = 1.0 - ((radius - dist1) / radius) * scale;
        percent1 = percent1 * percent1;
        offset1 *= percent1;
    }

    // Apply bulge for the second center (left eye)
    vec2 offset2 = texCoord - center2;
    float dist2 = length(offset2);
    if (dist2 < radius) {
        float percent2 = 1.0 - ((radius - dist2) / radius) * scale;
        percent2 = percent2 * percent2;
        offset2 *= percent2;
    }

    // Blend both bulge effects using a weighted average
    vec2 finalOffset = (offset1 + offset2) * 0.5;
    texCoord = vTexPosition + finalOffset;

    gl_FragColor = texture2D(uTexture, texCoord);
}
