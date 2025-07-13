precision mediump float;

varying vec2 vTexPosition;
uniform sampler2D uTexture;

uniform vec2 center1;
uniform vec2 center2;
uniform float radius1;
uniform float radius2;
uniform float scale;

void main() {
    vec2 texCoord = vTexPosition;
    vec2 offset = vec2(0.0);
    float weight = 0.0;

    // Bulge from center1
    vec2 d1 = texCoord - center1;
    float dist1 = length(d1);
    if (dist1 < radius1) {
        float percent1 = 1.0 - ((radius1 - dist1) / radius1) * scale;
        percent1 *= percent1;
        offset += d1 * (percent1 - 1.0);
        weight += 1.0;
    }

    // Bulge from center2
    vec2 d2 = texCoord - center2;
    float dist2 = length(d2);
    if (dist2 < radius2) {
        float percent2 = 1.0 - ((radius2 - dist2) / radius2) * scale;
        percent2 *= percent2;
        offset += d2 * (percent2 - 1.0);
        weight += 1.0;
    }

    if (weight > 0.0) {
        texCoord += offset / weight;
    }

    gl_FragColor = texture2D(uTexture, texCoord);
}
