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


#include <jni.h>
#include <malloc.h>
#include "GLES3/gl31.h"

#include <cstring>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#define EGL_EGLEXT_PROTOTYPES
#include "EGL/egl.h"
#include "EGL/eglext.h"
#define GL_GLEXT_PROTOTYPES
#include "GLES2/gl2.h"
#include "GLES2/gl2ext.h"
#define  LOG_TAG    "MainJNI"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("facepoint");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("facepoint")
//      }
//    }

void convert3DTo2DLUT(float* lut_data, int size) {
    int totalSize = size * size * size * 3;
    float* temp = (float*)malloc(totalSize * sizeof(float));  // Temporary buffer

    for (int b = 0; b < size; ++b) {
        for (int g = 0; g < size; ++g) {
            for (int r = 0; r < size; ++r) {
                int src_index = (b * size * size + g * size + r) * 3;
                int dest_index = (g * size + r + b * size) * 3;
                temp[dest_index + 0] = lut_data[src_index + 0];
                temp[dest_index + 1] = lut_data[src_index + 1];
                temp[dest_index + 2] = lut_data[src_index + 2];
            }
        }
    }

    // Copy back to the original array
    memcpy(lut_data, temp, totalSize * sizeof(float));
    free(temp);  // Free temp buffer
}


float* lut_data= nullptr;
int size = 0;
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_avoid_facepoint_render_VoidRender_00024Companion_loadLUT(JNIEnv *env, jobject thiz,jobject  assetManager,
                                                                     jstring mFile) {

    jboolean isCopy= false;
    const char* assetFileName=env->GetStringUTFChars(mFile,&isCopy);
    AAssetManager* aAssetManagerFromJava=AAssetManager_fromJava(env,assetManager);
    AAsset* asset =AAssetManager_open(aAssetManagerFromJava, assetFileName, AASSET_MODE_BUFFER);
    if (!asset) {
        printf("Could not open asset file \n");
        return false;
    }

    // Read entire file content
    size_t fileSize = AAsset_getLength(asset);
    char* fileContent = new char[fileSize + 1];
    AAsset_read(asset, fileContent, fileSize);
    fileContent[fileSize] = '\0';  // Null-terminate the string

    // Close asset
    AAsset_close(asset);

//    float* lut_data = nullptr;
//    int size = 0;

    // Process file content line by line
    char* line = strtok(fileContent, "\n");
    while (line) {
        if (strncmp(line, "#LUT size", 9) == 0) {
            // Read LUT size
            char* xline = strtok(nullptr, "\n");
            sscanf(xline,"LUT_3D_SIZE %d", &size);
            LOGE("DONE MASTER ++ strncmp %d line %s",size,xline);
            lut_data = new float[size * size * size * 3];
        }
        else if (strncmp(line, "#LUT data points", 16) == 0) {
            // Read colors
            int row = 0;
            line = strtok(nullptr, "\n");  // Move to first color line
            while (line && row < size * size * size) {
                float r, g, b;
                sscanf(line, "%f %f %f", &r, &g, &b);
                lut_data[row * 3 + 0] = r;
                lut_data[row * 3 + 1] = g;
                lut_data[row * 3 + 2] = b;
                row++;
                line = strtok(nullptr, "\n");
            }
            break;
        }
        line = strtok(nullptr, "\n");
    }

//    LOGE("DONE MASTER ++ %d",size);
//    for (int i=0;i<size*size*size;i++)
//        LOGE("DONE MASTER ++ R %f %f %f",lut_data[i*3+0],lut_data[i*3+1],lut_data[i*3+2]);
    // Cleanup
    delete[] fileContent;
    return lut_data != nullptr;
}
//int GL_TEXTURE_EXTERNAL_OES= 36197;
extern "C"
JNIEXPORT jint JNICALL
Java_com_avoid_facepoint_render_VoidRender_00024Companion_createTextureLUT2D(JNIEnv *env,
                                                                           jobject thiz,int textureID,int lutID) {
// Create texture
    convert3DTo2DLUT(lut_data,size);
    GLuint texture=lutID;
    GLuint texture2D=textureID;

    glBindTexture(GL_TEXTURE_2D, texture);


// Load data to texture
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGB,
            size, size,
            0,
            GL_RGB,
            GL_FLOAT,
            lut_data
    );


// Set sampling parameters
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_R,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);


    glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture2D);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MIN_FILTER,
            GL_LINEAR
    );
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MAG_FILTER,
            GL_LINEAR
    );
    return size;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_render_VoidRender_00024Companion_createTextureLUT(JNIEnv *env,
                                                                           jobject thiz,int textureID,int lutID) {
// Create texture
    GLuint texture=lutID;
    GLuint texture2D=textureID;

    glBindTexture(GL_TEXTURE_3D, texture);


// Load data to texture
    glTexImage3D(
            GL_TEXTURE_3D,
            0,
            GL_RGB,
            size, size, size,
            0,
            GL_RGB,
            GL_FLOAT,
            lut_data
    );


// Set sampling parameters
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R,     GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture2D);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MIN_FILTER,
            GL_LINEAR
    );
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MAG_FILTER,
            GL_LINEAR
    );
}
extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_MainActivity_00024Companion_destroy(JNIEnv *env, jobject thiz) {
    delete[] lut_data;
}


EGLImageKHR eglImage = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_render_VoidRender_00024Companion_makeKHR(JNIEnv *env, jobject thiz, jint texture_id) {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        LOGE("Failed to get EGL display");
        return;
    }

    EGLContext eglContext = eglGetCurrentContext();
    if (eglContext == EGL_NO_CONTEXT) {
        LOGE("Failed to get EGL context");
        return;
    }

    // Ensure the EGL API is set to OpenGL ES
    if (!eglBindAPI(EGL_OPENGL_ES_API)) {
        LOGE("Failed to bind EGL API");
        return;
    }

    EGLint imageAttributes[] = {
            EGL_GL_TEXTURE_LEVEL_KHR, 0, // mip map level to reference
            EGL_IMAGE_PRESERVED_KHR, EGL_FALSE,
            EGL_NONE
    };
    glBindTexture(GL_TEXTURE_2D,texture_id);
    eglImage = eglCreateImageKHR(eglDisplay, eglContext, EGL_GL_TEXTURE_2D_KHR, reinterpret_cast<EGLClientBuffer>(texture_id), imageAttributes);
    if (eglImage == EGL_NO_IMAGE_KHR) {
        LOGE("Failed to create EGL image");
        return;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_render_VoidRender_00024Companion_useKHR(JNIEnv *env, jobject thiz, jint texture_id) {
    if (eglImage != nullptr) {
        glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage);
        GLenum error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGE("glEGLImageTargetTexture2DOES failed with error: 0x%x", error);
        }
    } else {
        LOGE("EGL image is not created");
    }
}