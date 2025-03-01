#include <jni.h>
#include <malloc.h>
#include "GLES3/gl31.h"
#include <cstring>

#include <android/log.h>

#define  LOG_TAG    "Main"

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

extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_MainActivity_00024Companion_nativeInit(JNIEnv *env, jobject thiz) {

}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_avoid_facepoint_MainActivity_00024Companion_nativeInitTest(JNIEnv *env, jobject thiz,
                                                                    jstring wow) {

    const char *x = env->GetStringUTFChars(wow, nullptr);;
    return env->NewStringUTF(x);
}
GLubyte *pixelsA = nullptr;
GLubyte *pixelsB = nullptr;
GLsizei numBytes = 0;
GLuint pboRead = 0;
extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_VoidRender_00024Companion_read(JNIEnv *env, jobject thiz, int textureID,
                                                        int width,
                                                        int height, int channel) {
    if (pixelsA == nullptr) {
        numBytes = width * height * channel;
        pixelsA = (GLubyte *) malloc(numBytes);
        pixelsB = (GLubyte *) malloc(numBytes);
        glGenBuffers(1, &pboRead);

        glBindBuffer(GL_PIXEL_PACK_BUFFER, pboRead);
        glBufferData(GL_PIXEL_PACK_BUFFER, numBytes, nullptr,
                     GL_STREAM_READ);
    }

    glBindBuffer(GL_PIXEL_PACK_BUFFER, pboRead);
//    glBufferData(GL_PIXEL_PACK_BUFFER, numBytes, nullptr,
//                 GL_STREAM_READ);
    glReadPixels(
            0,
            0,
            width,
            height,
            GL_RGB,
            GL_UNSIGNED_BYTE,
//            pixelsA
            nullptr
    );
    auto pboMemory1 = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, numBytes, GL_MAP_READ_BIT);
    if (pboMemory1) {
//        pixelsA = static_cast<GLubyte *>(pboMemory1);
        memcpy( pixelsA,pboMemory1, numBytes);
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
    } else
        LOGE("FAILED");

    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
}
GLuint pboWrite = -1;

extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_VoidRender_00024Companion_write(JNIEnv *env, jobject thiz, int textureID,
                                                         int width, int height) {
//    memcpy(pixelsB,pixelsA,numBytes);
//if(pixelsA)
//    glTexImage2D(
//            GL_TEXTURE_2D,
//            0,
//            GL_RGB,
//            width,
//            height,
//            0,
//            GL_RGB,
//            GL_UNSIGNED_BYTE,
//            pixelsA
//    );
    if (pboWrite == -1) {
        glGenBuffers(1, &pboWrite);
    }
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboWrite);
    glBufferData(GL_PIXEL_UNPACK_BUFFER, numBytes, nullptr, GL_STREAM_DRAW);

    GLint align=-1;

    glGetIntegerv(GL_UNPACK_ALIGNMENT,&align);
    LOGE("C++ %d",align);

    if(align!=1) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGB,
                width,
                height,
                0,
                GL_RGB,
                GL_UNSIGNED_BYTE,
                nullptr
        );
        auto *ptr =
                glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, numBytes, GL_MAP_WRITE_BIT);

        if (ptr) {

            memcpy(ptr, pixelsA, numBytes);
            glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
            LOGE("DONE");
        }
    }


    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, nullptr);
    auto *ptr =
            glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, numBytes, GL_MAP_WRITE_BIT);

    if (ptr) {

        memcpy(ptr, pixelsA, numBytes);
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
        LOGE("DONE");
    }
    GLenum err;
    while ((err = glGetError()) != GL_NO_ERROR) {
        LOGE("OpenGL Error: %d", err);
    }

    // Unbind both the PBO and the texture
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
}