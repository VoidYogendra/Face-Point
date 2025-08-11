/*

* Copyright 2025 VoidYogendra
        *
        * Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* ```
http://www.apache.org/licenses/LICENSE-2.0
```
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
        * limitations under the License.
*
* Project: Face Point
* Repository: [https://github.com/VoidYogendra/Face-Point](https://github.com/VoidYogendra/Face-Point)
*/

package com.avoid.facepoint.render.mpfilters;

import android.opengl.GLES20;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/** A custom implementation of {@link ResultGlRenderer} to render {@link FaceMeshResult}. */
public class FaceMeshResultGlRenderer implements ResultGlRenderer<FaceMeshResult> {
  private static final String TAG = "FaceMeshResultGlRenderer";

  private static final float[] GLASSES_COLOR = new float[] {0f, 0f, 0f, 1f};
  private static final int GLASSES_THICKNESS = 5;
  private static final int CIRCLE_SEGMENTS = 32;

  private static final String VERTEX_SHADER =
          "uniform mat4 uProjectionMatrix;\n"
                  + "attribute vec4 vPosition;\n"
                  + "void main() {\n"
                  + "  gl_Position = uProjectionMatrix * vPosition;\n"
                  + "}";
  private static final String FRAGMENT_SHADER =
          "precision mediump float;\n"
                  + "uniform vec4 uColor;\n"
                  + "void main() {\n"
                  + "  gl_FragColor = uColor;\n"
                  + "}";
  private int program;
  private int positionHandle;
  private int projectionMatrixHandle;
  private int colorHandle;

  private int loadShader(int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    return shader;
  }

  public FaceMeshResultGlRenderer(){
    setupRendering();
  }

  @Override
  public void setupRendering() {
    program = GLES20.glCreateProgram();
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
    int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
    projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
    colorHandle = GLES20.glGetUniformLocation(program, "uColor");
  }

  @Override
  public void renderResult(FaceMeshResult result, float[] projectionMatrix) {
    if (result == null) {
      return;
    }
    GLES20.glUseProgram(program);
    GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);

    for (int i = 0; i < result.multiFaceLandmarks().size(); ++i) {
      drawGlasses(result.multiFaceLandmarks().get(i).getLandmarkList());
    }
  }

  private void drawGlasses(List<NormalizedLandmark> faceLandmarkList) {
    GLES20.glUniform4fv(colorHandle, 1, GLASSES_COLOR, 0);
    GLES20.glLineWidth(GLASSES_THICKNESS);

    // Calculate the centers of the left and right eye
    NormalizedLandmark leftEyeCenter = faceLandmarkList.get(473);
    NormalizedLandmark rightEyeCenter = faceLandmarkList.get(468);

    // Calculate the distance between the eyes
    float eyeDistance = calculateDistance(leftEyeCenter, rightEyeCenter);

    // Adjust the size of the glasses based on the eye distance
    float lensRadius = eyeDistance * 0.3f; // Adjust the scaling factor as needed
    float bridgeWidth = eyeDistance * 0.2f; // Adjust the bridge width as needed

    // Adjust the coordinates to add space between the lenses
    float leftEyeX = leftEyeCenter.getX() - bridgeWidth / 2;
    float rightEyeX = rightEyeCenter.getX() + bridgeWidth / 2;

    // Draw left eye lens
    drawCircle(leftEyeX, leftEyeCenter.getY(), lensRadius);

    // Draw right eye lens
    drawCircle(rightEyeX, rightEyeCenter.getY(), lensRadius);

    // Draw bridge of the glasses
    drawLine(leftEyeX - lensRadius, leftEyeCenter.getY(), rightEyeX + lensRadius, rightEyeCenter.getY());
  }

  private void drawCircle(float centerX, float centerY, float radius) {
    float[] vertices = new float[(CIRCLE_SEGMENTS + 1) * 2];
    for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
      double angle = 2 * Math.PI * i / CIRCLE_SEGMENTS;
      vertices[i * 2] = centerX + (float) Math.cos(angle) * radius;
      vertices[i * 2 + 1] = centerY + (float) Math.sin(angle) * radius;
    }
    FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, CIRCLE_SEGMENTS + 1);
    GLES20.glDisableVertexAttribArray(positionHandle);
  }

  private void drawLine(float startX, float startY, float endX, float endY) {
    float[] vertices = {startX, startY, endX, endY};
    FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    GLES20.glDisableVertexAttribArray(positionHandle);
  }

  private float calculateDistance(NormalizedLandmark point1, NormalizedLandmark point2) {
    float dx = point1.getX() - point2.getX();
    float dy = point1.getY() - point2.getY();
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * Deletes the shader program.
   *
   * <p>This is only necessary if one wants to release the program while keeping the context around.
   */
  public void release() {
    GLES20.glDeleteProgram(program);
  }
}