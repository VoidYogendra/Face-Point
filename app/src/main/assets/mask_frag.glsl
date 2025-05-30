precision mediump float;

uniform sampler2D uTexture;
uniform sampler2D uMaskTex;
varying vec2 vTexPosition;

void main() {
    vec4 cameraColor = texture2D(uTexture, vTexPosition);
    vec4 maskColour = texture2D(uMaskTex, vTexPosition); // all channels will be either 255 or 0
    float mask=maskColour.r;
    mask = mask > 0.5 ? 1.0 : 0.0;
    //gl_FragColor = vec4(cameraColor.r,0.0,mask,0.0) ;
    gl_FragColor = cameraColor*mask;
}