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
    external fun nativeDecryptWithPublicKey(
        cipher: ByteArray,
        publicKey: ByteArray,
        legacyProfile: Boolean,
    ): String
    external fun nativeInspectCipher(cipher: ByteArray): LongArray
    external fun nativeKeyCompatibility(publicKey: ByteArray): Int
    external fun nativePedersenCommit(amount: Long, blinding: ByteArray): ByteArray
    external fun nativeBlindingAdd(left: ByteArray, right: ByteArray): ByteArray
    external fun nativeBlindingSubtract(left: ByteArray, right: ByteArray): ByteArray
    external fun nativeEncrypt(amount: Long, randomSeed: ByteArray, blinding: ByteArray): Array<ByteArray>
    external fun nativeClaim(amount: Long, randomSeed: ByteArray, blinding: ByteArray): Array<ByteArray>
    external fun nativeBuildDecrypt(
        amount: Long,
        currentBalance: Long,
        currentCipher: ByteArray,
        randomSeed: ByteArray,
        blinding: ByteArray,
        rangeBlinding: ByteArray,
    ): Array<ByteArray>
    external fun nativeBuildStealth(
        amount: Long,
        currentBalance: Long,
        currentCipher: ByteArray,
        randomSeed: ByteArray,
        blinding: ByteArray,
        deltaRangeBlinding: ByteArray,
        balanceRangeBlinding: ByteArray,
    ): Array<ByteArray>
    external fun nativeBuildMigration(
        mode: Int,
        amount: Long,
        currentCipher: ByteArray?,
        oldPublicKey: ByteArray?,
        randomSeed: ByteArray,
        blinding: ByteArray,
    ): Array<ByteArray>
    external fun nativeClear()
}
