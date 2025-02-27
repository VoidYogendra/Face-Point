#include <jni.h>

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
    // TODO: implement nativeInit()
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_avoid_facepoint_MainActivity_00024Companion_nativeInitTest(JNIEnv *env, jobject thiz,jstring wow) {

    const char* x=env->GetStringUTFChars(wow, nullptr);;
    return env->NewStringUTF(x);
}