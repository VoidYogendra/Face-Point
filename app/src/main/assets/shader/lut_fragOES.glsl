#extension GL_OES_EGL_image_external : require
#extension GL_OES_texture_3D : require
precision mediump float;
//precision mediump sampler3D;

// Screen UV
varying vec2 vTexPosition;
// Resulting color
//layout (location = 0) out vec4 out_color;
// Sampling textures
uniform samplerExternalOES uTexture;
uniform sampler3D lut;

void main(){
    // Sample original color
    vec3 color = texture2D(uTexture, vTexPosition).rgb;


    // Sample LUT
    gl_FragColor = texture3D(lut, color);
    //gl_FragColor = texture2D(uTexture, vTexPosition);
}