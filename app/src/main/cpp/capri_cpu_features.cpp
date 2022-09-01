#include <jni.h>
#if defined(__aarch64__)
#include <sys/auxv.h>
#include <asm/hwcap.h>
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qwen_tts_studio_engine_CpuFeatures_supportsFastArm(JNIEnv *, jobject) {
#if defined(__aarch64__)
    const unsigned long flags = getauxval(AT_HWCAP);
    return (flags & HWCAP_ASIMDDP) && (flags & HWCAP_ASIMDHP) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
