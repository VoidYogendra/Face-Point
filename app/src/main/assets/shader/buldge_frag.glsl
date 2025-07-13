precision mediump float;

varying vec2 vTexPosition;
uniform sampler2D uTexture;

uniform vec2 center;
uniform float radius;
uniform float scale;

void main() {
vec2 textureCoordinateToUse = vTexPosition;
float dist = distance(center, vTexPosition);
textureCoordinateToUse -= center;
if (dist < radius) {
float percent = 1.0 - ((radius - dist) / radius) * scale;
percent = percent * percent;
textureCoordinateToUse = textureCoordinateToUse * percent;
}
textureCoordinateToUse += center;

gl_FragColor = texture2D(uTexture, textureCoordinateToUse);
}