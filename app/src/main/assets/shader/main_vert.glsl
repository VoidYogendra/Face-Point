attribute vec4 aPosition;
attribute vec2 aTexPosition;
varying vec2 vTexPosition;
uniform mat4 u_Matrix;
void main() {
gl_Position = u_Matrix*aPosition;
vTexPosition = aTexPosition;
}