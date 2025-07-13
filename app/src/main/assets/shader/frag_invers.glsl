precision mediump float;
uniform sampler2D uTexture;
varying vec2 vTexPosition;
void main() {
    vec4 color = texture2D(uTexture, vTexPosition);
     gl_FragColor = vec4((1.0 - color.rgb), color.w);
}