precision mediump float;

uniform sampler2D uTexture;
uniform sampler2D uMaskTex;
uniform sampler2D overlayTex;
uniform mat4 u_Matrix;
varying vec2 vTexPosition;

uniform vec2 uScreenSize;     // in pixels
uniform vec2 uOverlaySize;    // in pixels
uniform vec2 uOverlayOffset;  // in pixels

void main() {
    vec4 cameraColor = texture2D(uTexture, vTexPosition);

    vec2 screenPixel = vTexPosition * uScreenSize;
    vec2 overlayUV = (screenPixel-uOverlayOffset) / uOverlaySize;
    vec4 overlayColor = texture2D(overlayTex, overlayUV);

    vec4 maskColour = texture2D(uMaskTex, vTexPosition); // all channels will be either 255 or 0
    float mask=maskColour.r;
    mask = mask > 0.5 ? 1.0 : 0.0;
    //gl_FragColor = vec4(cameraColor.r,0.0,mask,0.0) ;
    if(mask>0.5)
    {
    gl_FragColor = cameraColor*mask;
    }
    else{
        if (overlayUV.x < 0.0 || overlayUV.x > 1.0 || overlayUV.y < 0.0 || overlayUV.y > 1.0) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); // black outside overlay
        } else {
            gl_FragColor = overlayColor;
        }
    }
}