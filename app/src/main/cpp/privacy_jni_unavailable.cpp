#include <jni.h>

namespace {

void unavailable(JNIEnv* env) {
    jclass exception_class = env->FindClass("java/lang/UnsupportedOperationException");
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, "Privacy requires a 64-bit device with hardware AES support");
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeSupported(JNIEnv*, jobject) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeInitialize(JNIEnv* env, jobject, jbyteArray) {
    unavailable(env);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativePublicKey(JNIEnv* env, jobject) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeAesKat(JNIEnv* env, jobject) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeDecrypt(JNIEnv* env, jobject, jbyteArray) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeDecryptWithPublicKey(
    JNIEnv* env, jobject, jbyteArray, jbyteArray, jboolean
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeInspectCipher(JNIEnv* env, jobject, jbyteArray) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeKeyCompatibility(JNIEnv* env, jobject, jbyteArray) {
    unavailable(env);
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativePedersenCommit(
    JNIEnv* env, jobject, jlong, jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBlindingAdd(
    JNIEnv* env, jobject, jbyteArray, jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBlindingSubtract(
    JNIEnv* env, jobject, jbyteArray, jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeEncrypt(
    JNIEnv* env,
    jobject,
    jlong,
    jbyteArray,
    jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeClaim(
    JNIEnv* env,
    jobject,
    jlong,
    jbyteArray,
    jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildDecrypt(
    JNIEnv* env,
    jobject,
    jlong,
    jlong,
    jbyteArray,
    jbyteArray,
    jbyteArray,
    jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildStealth(
    JNIEnv* env,
    jobject,
    jlong,
    jlong,
    jbyteArray,
    jbyteArray,
    jbyteArray,
    jbyteArray,
    jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildMigration(
    JNIEnv* env,
    jobject,
    jint,
    jlong,
    jbyteArray,
    jbyteArray,
    jbyteArray,
    jbyteArray
) {
    unavailable(env);
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeClear(JNIEnv*, jobject) {}
