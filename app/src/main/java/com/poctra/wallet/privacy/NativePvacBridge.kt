package com.poctra.wallet.privacy

internal object NativePvacBridge {
    init {
        System.loadLibrary("poctra_privacy")
    }

    external fun nativeSupported(): Boolean
    external fun nativeInitialize(seed: ByteArray)
    external fun nativePublicKey(): ByteArray
    external fun nativeAesKat(): ByteArray
    external fun nativeDecrypt(cipher: ByteArray): String
    external fun nativeEncrypt(amount: Long, randomSeed: ByteArray, blinding: ByteArray): Array<ByteArray>
    external fun nativeClaim(amount: Long, randomSeed: ByteArray, blinding: ByteArray): Array<ByteArray>
    external fun nativeBuildDecrypt(
        amount: Long,
        currentBalance: Long,
        currentCipher: ByteArray,
        randomSeed: ByteArray,
        blinding: ByteArray,
    ): Array<ByteArray>
    external fun nativeBuildStealth(
        amount: Long,
        currentBalance: Long,
        currentCipher: ByteArray,
        randomSeed: ByteArray,
        blinding: ByteArray,
    ): Array<ByteArray>
    external fun nativeClear()
}
