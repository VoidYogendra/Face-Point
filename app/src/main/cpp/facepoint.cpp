#include <jni.h>
#include <malloc.h>
#include "GLES3/gl31.h"
#include <cstring>
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
size_t numBytes=0;
extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_VoidRender_00024Companion_read(JNIEnv *env, jobject thiz, int width,
                                                        int height, int channel) {
    if (pixelsA == nullptr) {
        numBytes=width * height * channel;
        pixelsA = (GLubyte *) malloc(numBytes);
        pixelsB= (GLubyte *) malloc(numBytes);
    }
    glReadPixels(
            0,
            0,
            width,
            height,
            GL_RGB,
            GL_UNSIGNED_BYTE,
            pixelsA
    );
}
GLuint pbo = -1;
extern "C"
JNIEXPORT void JNICALL
Java_com_avoid_facepoint_VoidRender_00024Companion_write(JNIEnv *env, jobject thiz, int textureID,int width,int height) {
    memcpy(pixelsB,pixelsA,numBytes);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGB,
            width,
            height,
            0,
            GL_RGB,
            GL_UNSIGNED_BYTE,
            pixelsB
    );
//    if (pbo == -1)
//        glGenBuffers(1, &pbo);
//    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
//    glBufferData(GL_PIXEL_UNPACK_BUFFER, numBytes, nullptr, GL_STREAM_COPY);
//    auto* ptr = static_cast<GLubyte*>(
//            glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, numBytes, GL_MAP_WRITE_BIT)
//    );
//
//    if (ptr) {
//        // Optionally, inspect some values before unmapping:
//        // std::cout << "Before unmap: " << static_cast<int>(ptr[0]) << ", " << static_cast<int>(ptr[1]) << "\n";
//
//        // Copy the pixel data into the mapped buffer
//        memcpy(ptr, pixels, numBytes);
//
//        // Unmap the buffer once data is written.
//        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
//    }
//
//    // --- Bind texture and update it from PBO ---
//    glBindTexture(GL_TEXTURE_2D, textureID);
//    // Passing a null pointer tells OpenGL to use the data from the PBO
//    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, ptr);
//
//    // Unbind both the PBO and the texture
//    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
}