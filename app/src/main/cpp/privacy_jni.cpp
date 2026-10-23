#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <type_traits>
#include <vector>

#if defined(__aarch64__)
#include <asm/hwcap.h>
#include <sys/auxv.h>
#endif

#include "pvac/pvac_c_api.h"

namespace {

constexpr const char* LOG_TAG = "PoctraPrivacy";
// A Decrypt or Stealth spend appends a base layer to the remaining balance.
// Keep this synchronized with webcli's private_spend_refresh_base_layers.
constexpr size_t PRIVATE_SPEND_REFRESH_BASE_LAYERS = 4;
constexpr size_t MaxCipherBytes = 1'048'576;

std::mutex engine_mutex;
pvac_params params = nullptr;
pvac_pubkey public_key = nullptr;
pvac_seckey secret_key = nullptr;
std::array<uint8_t, 32> active_seed{};
bool initialized = false;

void secure_zero(uint8_t* data, size_t length) {
    volatile uint8_t* cursor = data;
    while (length-- > 0) *cursor++ = 0;
}

class ScopedWipe {
public:
    explicit ScopedWipe(std::vector<uint8_t>& bytes) : bytes_(bytes) {}
    ~ScopedWipe() { secure_zero(bytes_.data(), bytes_.size()); }

    ScopedWipe(const ScopedWipe&) = delete;
    ScopedWipe& operator=(const ScopedWipe&) = delete;

private:
    std::vector<uint8_t>& bytes_;
};

bool hardware_supported() {
#if defined(__aarch64__)
    return (getauxval(AT_HWCAP) & HWCAP_AES) != 0;
#elif defined(__i386__) || defined(__x86_64__)
    return __builtin_cpu_supports("aes");
#else
    return false;
#endif
}

// Wipe all sensitive key material from the serialized representation of the secret key
// before releasing the heap allocation. pvac_serialize_seckey produces a byte buffer
// containing the scalar, PRF state, and vector-backed fields; secure_zero overwrites it
// using a volatile write loop that is resistant to compiler dead-store elimination.
// This ensures that no secret bytes remain in freed heap pages after the call returns.
void wipe_and_free_seckey(pvac_seckey sk) {
    if (sk == nullptr) return;
    size_t length = 0;
    uint8_t* raw = pvac_serialize_seckey(sk, &length);
    if (raw != nullptr && length > 0) {
        secure_zero(raw, length);
        pvac_free_bytes(raw);
    }
    pvac_free_seckey(sk);
}

void clear_engine_locked() {
    if (secret_key != nullptr) wipe_and_free_seckey(secret_key);
    if (public_key != nullptr) pvac_free_pubkey(public_key);
    if (params != nullptr) pvac_free_params(params);
    secret_key = nullptr;
    public_key = nullptr;
    params = nullptr;
    secure_zero(active_seed.data(), active_seed.size());
    initialized = false;
}

void require_initialized() {
    if (!initialized || params == nullptr || public_key == nullptr || secret_key == nullptr) {
        throw std::runtime_error("Privacy keys are not initialized");
    }
}

std::vector<uint8_t> read_bytes(JNIEnv* env, jbyteArray input, size_t expected = 0, size_t max_size = 0) {
    if (input == nullptr) throw std::invalid_argument("Missing byte array");
    const auto length = static_cast<size_t>(env->GetArrayLength(input));
    if (expected != 0 && length != expected) throw std::invalid_argument("Invalid byte array length");
    if (max_size != 0 && length > max_size) throw std::invalid_argument("Input byte array exceeds maximum allowed size");
    std::vector<uint8_t> bytes(length);
    if (length != 0) {
        env->GetByteArrayRegion(input, 0, static_cast<jsize>(length), reinterpret_cast<jbyte*>(bytes.data()));
    }
    return bytes;
}

jbyteArray make_bytes(JNIEnv* env, const uint8_t* bytes, size_t length) {
    auto result = env->NewByteArray(static_cast<jsize>(length));
    if (result == nullptr) throw std::runtime_error("Unable to allocate native result");
    if (length != 0) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(length), reinterpret_cast<const jbyte*>(bytes));
    }
    return result;
}

std::vector<uint8_t> serialize_cipher(pvac_cipher cipher) {
    size_t length = 0;
    uint8_t* raw = pvac_serialize_cipher(cipher, &length);
    if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize privacy cipher");
    std::vector<uint8_t> result(raw, raw + length);
    pvac_free_bytes(raw);
    return result;
}

std::vector<uint8_t> serialize_zero_proof(pvac_zero_proof proof) {
    size_t length = 0;
    uint8_t* raw = pvac_serialize_zero_proof(proof, &length);
    if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize privacy proof");
    std::vector<uint8_t> result(raw, raw + length);
    pvac_free_bytes(raw);
    return result;
}

std::vector<uint8_t> serialize_bound_range_proof(pvac_zero_proof proof) {
    size_t length = 0;
    uint8_t* raw = pvac_serialize_bound_range_proof(proof, &length);
    if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize bound range proof");
    std::vector<uint8_t> result(raw, raw + length);
    pvac_free_bytes(raw);
    return result;
}

jobjectArray make_result(JNIEnv* env, const std::vector<std::vector<uint8_t>>& values) {
    jclass byte_array_class = env->FindClass("[B");
    if (byte_array_class == nullptr) throw std::runtime_error("Unable to resolve byte array class");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(values.size()), byte_array_class, nullptr);
    if (result == nullptr) throw std::runtime_error("Unable to allocate native payload");
    for (size_t index = 0; index < values.size(); ++index) {
        jbyteArray value = make_bytes(env, values[index].data(), values[index].size());
        env->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<uint8_t> empty_bytes() {
    return {};
}

pvac_pubkey deserialize_pubkey(const std::vector<uint8_t>& raw) {
    if (raw.empty()) return nullptr;
    return pvac_deserialize_pubkey(raw.data(), raw.size());
}

std::array<uint8_t, 32> pedersen_commit(uint64_t amount, const uint8_t blinding[32]) {
    std::array<uint8_t, 32> commitment{};
    size_t length = 0;
    if (pvac_pedersen_commit_v2(
            amount,
            blinding,
            commitment.data(),
            commitment.size(),
            &length
        ) != 0 || length != commitment.size()) {
        throw std::runtime_error("Unable to create amount commitment");
    }
    return commitment;
}

void throw_java(JNIEnv* env, const char* message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (exception_class != nullptr) env->ThrowNew(exception_class, message);
}

template <typename Callback>
auto guard(JNIEnv* env, Callback callback) -> decltype(callback()) {
    try {
        return callback();
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Native operation failed: %s", error.what());
        throw_java(env, error.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Native operation failed with an unknown error");
        throw_java(env, "Native privacy operation failed");
    }
    using ReturnType = decltype(callback());
    if constexpr (!std::is_void_v<ReturnType>) return ReturnType{};
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeSupported(JNIEnv*, jobject) {
    return hardware_supported() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeInitialize(
    JNIEnv* env,
    jobject,
    jbyteArray seed_array
) {
    guard(env, [&]() {
        if (!hardware_supported()) {
            throw std::runtime_error("Privacy requires a device with hardware AES support");
        }
        auto seed = read_bytes(env, seed_array, 32);
        ScopedWipe wipe(seed);
        std::lock_guard<std::mutex> lock(engine_mutex);
        if (initialized && std::memcmp(active_seed.data(), seed.data(), active_seed.size()) == 0) return;
        clear_engine_locked();
        params = pvac_default_params();
        if (params == nullptr) throw std::runtime_error("Unable to initialize privacy parameters");
        pvac_keygen_from_seed(params, seed.data(), &public_key, &secret_key);
        if (public_key == nullptr || secret_key == nullptr) {
            clear_engine_locked();
            throw std::runtime_error("Unable to derive privacy keys");
        }
        std::copy(seed.begin(), seed.end(), active_seed.begin());
        initialized = true;
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativePublicKey(JNIEnv* env, jobject) {
    return guard(env, [&]() -> jbyteArray {
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        size_t length = 0;
        uint8_t* raw = pvac_serialize_pubkey(public_key, &length);
        if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize privacy public key");
        jbyteArray result = make_bytes(env, raw, length);
        pvac_free_bytes(raw);
        return result;
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeAesKat(JNIEnv* env, jobject) {
    return guard(env, [&]() -> jbyteArray {
        std::array<uint8_t, 16> output{};
        pvac_aes_kat(output.data());
        return make_bytes(env, output.data(), output.size());
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeDecrypt(
    JNIEnv* env,
    jobject,
    jbyteArray cipher_array
) {
    return guard(env, [&]() -> jstring {
        const auto raw = read_bytes(env, cipher_array, 0, MaxCipherBytes);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        pvac_cipher cipher = pvac_deserialize_cipher(raw.data(), raw.size());
        if (cipher == nullptr) throw std::invalid_argument("Encrypted balance is invalid");
        uint64_t low = 0;
        uint64_t high = 0;
        const int dec_ok = pvac_dec_value_fp(public_key, secret_key, cipher, &low, &high);
        pvac_free_cipher(cipher);
        if (dec_ok == 0) throw std::runtime_error("Decryption failed: cipher may be malformed or key mismatch");
        if (high != 0) throw std::runtime_error("Encrypted balance is outside the supported range");
        const std::string value = std::to_string(low);
        return env->NewStringUTF(value.c_str());
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeDecryptWithPublicKey(
    JNIEnv* env,
    jobject,
    jbyteArray cipher_array,
    jbyteArray public_key_array,
    jboolean legacy_profile
) {
    return guard(env, [&]() -> jstring {
        const auto cipher_raw = read_bytes(env, cipher_array, 0, MaxCipherBytes);
        const auto key_raw = read_bytes(env, public_key_array);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        pvac_pubkey remote_key = deserialize_pubkey(key_raw);
        if (remote_key == nullptr) throw std::invalid_argument("Registered Privacy key is invalid");
        pvac_cipher cipher = pvac_deserialize_cipher(cipher_raw.data(), cipher_raw.size());
        if (cipher == nullptr) {
            pvac_free_pubkey(remote_key);
            throw std::invalid_argument("Encrypted balance is invalid");
        }
        int64_t value = 0;
        const int decoded = legacy_profile
            ? pvac_dec_value_legacy_i64(remote_key, secret_key, cipher, &value)
            : pvac_dec_value_i64(remote_key, secret_key, cipher, &value);
        pvac_free_cipher(cipher);
        pvac_free_pubkey(remote_key);
        if (decoded == 0 || value < 0) {
            throw std::runtime_error("Registered Privacy key cannot decrypt this balance");
        }
        const std::string output = std::to_string(value);
        return env->NewStringUTF(output.c_str());
    });
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeInspectCipher(
    JNIEnv* env,
    jobject,
    jbyteArray cipher_array
) {
    return guard(env, [&]() -> jlongArray {
        const auto raw = read_bytes(env, cipher_array);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        pvac_cipher cipher = pvac_deserialize_cipher(raw.data(), raw.size());
        if (cipher == nullptr) throw std::invalid_argument("Encrypted balance is invalid");
        const jlong values[] = {
            pvac_cipher_has_key_bound_material(cipher) == 1 ? 1L : 0L,
            pvac_cipher_has_canonical_r_com(public_key, cipher) == 1 ? 1L : 0L,
            static_cast<jlong>(pvac_cipher_base_layer_count(cipher)),
        };
        pvac_free_cipher(cipher);
        jlongArray result = env->NewLongArray(3);
        if (result == nullptr) throw std::runtime_error("Unable to allocate cipher diagnostics");
        env->SetLongArrayRegion(result, 0, 3, values);
        return result;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeKeyCompatibility(
    JNIEnv* env,
    jobject,
    jbyteArray public_key_array
) {
    return guard(env, [&]() -> jint {
        const auto raw = read_bytes(env, public_key_array);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        pvac_pubkey remote_key = deserialize_pubkey(raw);
        if (remote_key == nullptr) return 0;
        int flags = 0;
        if (pvac_pubkey_is_key_bound_extension(remote_key, public_key) == 1) flags |= 1;
        if (pvac_pubkey_is_legacy_v1_profile(remote_key, public_key) == 1) flags |= 2;
        if (pvac_pubkey_is_proof_profile(remote_key, public_key) == 1) flags |= 4;
        if (pvac_pubkey_matches_secret_profile(remote_key, public_key, secret_key) == 1) flags |= 8;
        pvac_free_pubkey(remote_key);
        return flags;
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativePedersenCommit(
    JNIEnv* env,
    jobject,
    jlong amount,
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jbyteArray {
        if (amount < 0) throw std::invalid_argument("Privacy amount is invalid");
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe wipe(blinding);
        const auto commitment = pedersen_commit(static_cast<uint64_t>(amount), blinding.data());
        return make_bytes(env, commitment.data(), commitment.size());
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBlindingAdd(
    JNIEnv* env,
    jobject,
    jbyteArray left_array,
    jbyteArray right_array
) {
    return guard(env, [&]() -> jbyteArray {
        auto left = read_bytes(env, left_array, 32);
        auto right = read_bytes(env, right_array, 32);
        ScopedWipe left_wipe(left);
        ScopedWipe right_wipe(right);
        std::array<uint8_t, 32> output{};
        if (pvac_blinding_add(left.data(), right.data(), output.data()) != 0) {
            throw std::runtime_error("Unable to add Privacy blindings");
        }
        return make_bytes(env, output.data(), output.size());
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBlindingSubtract(
    JNIEnv* env,
    jobject,
    jbyteArray left_array,
    jbyteArray right_array
) {
    return guard(env, [&]() -> jbyteArray {
        auto left = read_bytes(env, left_array, 32);
        auto right = read_bytes(env, right_array, 32);
        ScopedWipe left_wipe(left);
        ScopedWipe right_wipe(right);
        std::array<uint8_t, 32> output{};
        if (pvac_blinding_sub(left.data(), right.data(), output.data()) != 0) {
            throw std::runtime_error("Unable to subtract Privacy blindings");
        }
        return make_bytes(env, output.data(), output.size());
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeEncrypt(
    JNIEnv* env,
    jobject,
    jlong amount,
    jbyteArray random_seed_array,
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        if (amount < 0) throw std::invalid_argument("Privacy amount must be positive");
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher cipher = pvac_enc_value_seeded(
            public_key,
            secret_key,
            static_cast<uint64_t>(amount),
            random_seed.data()
        );
        if (cipher == nullptr) throw std::runtime_error("Unable to encrypt amount");
        pvac_zero_proof proof = nullptr;
        try {
            std::array<uint8_t, 32> commitment{};
            size_t commitment_length = 0;
            if (pvac_pedersen_commit_v2(
                    static_cast<uint64_t>(amount),
                    blinding.data(),
                    commitment.data(),
                    commitment.size(),
                    &commitment_length
                ) != 0 || commitment_length != commitment.size()) {
                throw std::runtime_error("Unable to create amount commitment");
            }
            proof = pvac_make_zero_proof_bound(
                public_key,
                secret_key,
                cipher,
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (proof == nullptr) throw std::runtime_error("Unable to create privacy proof");
            const auto result = make_result(env, {
                serialize_cipher(cipher),
                std::vector<uint8_t>(commitment.begin(), commitment.end()),
                serialize_zero_proof(proof),
                blinding,
            });
            pvac_free_zero_proof(proof);
            pvac_free_cipher(cipher);
            return result;
        } catch (...) {
            if (proof != nullptr) pvac_free_zero_proof(proof);
            pvac_free_cipher(cipher);
            throw;
        }
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeClaim(
    JNIEnv* env,
    jobject,
    jlong amount,
    jbyteArray random_seed_array,
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        if (amount < 0) throw std::invalid_argument("Claim amount must be positive");
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher cipher = pvac_enc_value_seeded(
            public_key,
            secret_key,
            static_cast<uint64_t>(amount),
            random_seed.data()
        );
        if (cipher == nullptr) throw std::runtime_error("Unable to encrypt claim");
        pvac_zero_proof proof = nullptr;
        try {
            std::array<uint8_t, 32> commitment{};
            size_t commitment_length = 0;
            if (pvac_commit_ct_v2(
                    public_key,
                    cipher,
                    commitment.data(),
                    commitment.size(),
                    &commitment_length
                ) != 0 || commitment_length != commitment.size()) {
                throw std::runtime_error("Unable to commit claim cipher");
            }
            proof = pvac_make_zero_proof_bound(
                public_key,
                secret_key,
                cipher,
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (proof == nullptr) throw std::runtime_error("Unable to create claim proof");
            const auto amount_commitment = pedersen_commit(
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (pvac_verify_zero_bound(
                    public_key,
                    cipher,
                    proof,
                    amount_commitment.data()
                ) == 0) {
                throw std::runtime_error("Local Claim proof verification failed");
            }
            const auto result = make_result(env, {
                serialize_cipher(cipher),
                std::vector<uint8_t>(commitment.begin(), commitment.end()),
                serialize_zero_proof(proof),
            });
            pvac_free_zero_proof(proof);
            pvac_free_cipher(cipher);
            return result;
        } catch (...) {
            if (proof != nullptr) pvac_free_zero_proof(proof);
            pvac_free_cipher(cipher);
            throw;
        }
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildDecrypt(
    JNIEnv* env,
    jobject,
    jlong amount,
    jlong current_balance,
    jbyteArray current_cipher_array,
    jbyteArray random_seed_array,
    jbyteArray blinding_array,
    jbyteArray range_blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        if (amount <= 0 || current_balance < amount) {
            throw std::invalid_argument("Insufficient encrypted balance");
        }
        auto current_cipher_raw = read_bytes(env, current_cipher_array, 0, MaxCipherBytes);
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        auto range_blinding = read_bytes(env, range_blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        ScopedWipe range_blinding_wipe(range_blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher amount_cipher = nullptr;
        pvac_cipher current_cipher = nullptr;
        pvac_cipher new_balance_cipher = nullptr;
        pvac_zero_proof zero_proof = nullptr;
        pvac_zero_proof range_proof = nullptr;
        auto cleanup = [&]() {
            if (range_proof != nullptr) pvac_free_zero_proof(range_proof);
            if (zero_proof != nullptr) pvac_free_zero_proof(zero_proof);
            if (new_balance_cipher != nullptr) pvac_free_cipher(new_balance_cipher);
            if (current_cipher != nullptr) pvac_free_cipher(current_cipher);
            if (amount_cipher != nullptr) pvac_free_cipher(amount_cipher);
        };

        try {
            amount_cipher = pvac_enc_value_seeded(
                public_key,
                secret_key,
                static_cast<uint64_t>(amount),
                random_seed.data()
            );
            current_cipher = pvac_deserialize_cipher(current_cipher_raw.data(), current_cipher_raw.size());
            if (amount_cipher == nullptr || current_cipher == nullptr) {
                throw std::runtime_error("Unable to decode encrypted balance");
            }
            if (pvac_cipher_base_layer_count(current_cipher) >=
                PRIVATE_SPEND_REFRESH_BASE_LAYERS) {
                throw std::runtime_error(
                    "Encrypted balance compact refresh is required before Decrypt"
                );
            }
            new_balance_cipher = pvac_ct_sub(public_key, current_cipher, amount_cipher);
            if (new_balance_cipher == nullptr) throw std::runtime_error("Unable to compute remaining balance");

            std::array<uint8_t, 32> amount_commitment{};
            size_t commitment_length = 0;
            if (pvac_pedersen_commit_v2(
                    static_cast<uint64_t>(amount),
                    blinding.data(),
                    amount_commitment.data(),
                    amount_commitment.size(),
                    &commitment_length
                ) != 0 || commitment_length != amount_commitment.size()) {
                throw std::runtime_error("Unable to create amount commitment");
            }
            zero_proof = pvac_make_zero_proof_bound(
                public_key,
                secret_key,
                amount_cipher,
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (zero_proof == nullptr) throw std::runtime_error("Unable to create Decrypt proof");

            range_proof = pvac_make_bound_range_proof(
                public_key,
                secret_key,
                new_balance_cipher,
                static_cast<uint64_t>(current_balance - amount),
                range_blinding.data()
            );
            if (range_proof == nullptr) throw std::runtime_error("Unable to create balance range proof");
            if (pvac_verify_zero_bound(
                    public_key,
                    amount_cipher,
                    zero_proof,
                    amount_commitment.data()
                ) == 0) {
                throw std::runtime_error("Local Decrypt proof verification failed");
            }
            const auto balance_commitment = pedersen_commit(
                static_cast<uint64_t>(current_balance - amount),
                range_blinding.data()
            );
            if (pvac_verify_bound_range_commitment(
                    public_key,
                    new_balance_cipher,
                    range_proof,
                    balance_commitment.data()
                ) == 0) {
                throw std::runtime_error("Encrypted balance refresh is required before Decrypt");
            }

            const auto result = make_result(env, {
                serialize_cipher(amount_cipher),
                std::vector<uint8_t>(amount_commitment.begin(), amount_commitment.end()),
                serialize_zero_proof(zero_proof),
                blinding,
                serialize_bound_range_proof(range_proof),
            });
            cleanup();
            return result;
        } catch (...) {
            cleanup();
            throw;
        }
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildStealth(
    JNIEnv* env,
    jobject,
    jlong amount,
    jlong current_balance,
    jbyteArray current_cipher_array,
    jbyteArray random_seed_array,
    jbyteArray blinding_array,
    jbyteArray delta_range_blinding_array,
    jbyteArray balance_range_blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        const auto started_at = std::chrono::steady_clock::now();
        const auto log_stage = [&](const char* stage) {
            const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started_at
            ).count();
            __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "Stealth proof stage=%s elapsed_ms=%lld",
                stage,
                static_cast<long long>(elapsed)
            );
        };
        if (amount <= 0 || current_balance < amount) {
            throw std::invalid_argument("Insufficient encrypted balance");
        }
        auto current_cipher_raw = read_bytes(env, current_cipher_array, 0, MaxCipherBytes);
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        auto delta_range_blinding = read_bytes(env, delta_range_blinding_array, 32);
        auto balance_range_blinding = read_bytes(env, balance_range_blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        ScopedWipe delta_range_blinding_wipe(delta_range_blinding);
        ScopedWipe balance_range_blinding_wipe(balance_range_blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher delta_cipher = nullptr;
        pvac_cipher current_cipher = nullptr;
        pvac_cipher new_balance_cipher = nullptr;
        pvac_zero_proof delta_range_proof = nullptr;
        pvac_zero_proof balance_range_proof = nullptr;
        pvac_zero_proof zero_proof = nullptr;
        auto cleanup = [&]() {
            if (zero_proof != nullptr) pvac_free_zero_proof(zero_proof);
            if (balance_range_proof != nullptr) pvac_free_zero_proof(balance_range_proof);
            if (delta_range_proof != nullptr) pvac_free_zero_proof(delta_range_proof);
            if (new_balance_cipher != nullptr) pvac_free_cipher(new_balance_cipher);
            if (current_cipher != nullptr) pvac_free_cipher(current_cipher);
            if (delta_cipher != nullptr) pvac_free_cipher(delta_cipher);
        };

        try {
            delta_cipher = pvac_enc_value_seeded(
                public_key,
                secret_key,
                static_cast<uint64_t>(amount),
                random_seed.data()
            );
            current_cipher = pvac_deserialize_cipher(current_cipher_raw.data(), current_cipher_raw.size());
            if (delta_cipher == nullptr || current_cipher == nullptr) {
                throw std::runtime_error("Unable to decode encrypted balance");
            }
            if (pvac_cipher_base_layer_count(current_cipher) >=
                PRIVATE_SPEND_REFRESH_BASE_LAYERS) {
                throw std::runtime_error(
                    "Encrypted balance compact refresh is required before Stealth"
                );
            }
            new_balance_cipher = pvac_ct_sub(public_key, current_cipher, delta_cipher);
            if (new_balance_cipher == nullptr) throw std::runtime_error("Unable to compute remaining balance");

            std::array<uint8_t, 32> cipher_commitment{};
            size_t cipher_commitment_length = 0;
            if (pvac_commit_ct_v2(
                    public_key,
                    delta_cipher,
                    cipher_commitment.data(),
                    cipher_commitment.size(),
                    &cipher_commitment_length
                ) != 0 || cipher_commitment_length != cipher_commitment.size()) {
                throw std::runtime_error("Unable to commit Stealth cipher");
            }

            // Each bound proof already uses the bounded worker pool. Running two
            // complete provers concurrently causes a large temporary-memory spike
            // on Android and can exhaust native thread or heap resources.
            log_stage("delta_range_start");
            delta_range_proof = pvac_make_bound_range_proof(
                public_key,
                secret_key,
                delta_cipher,
                static_cast<uint64_t>(amount),
                delta_range_blinding.data()
            );
            log_stage("delta_range_ready");
            balance_range_proof = pvac_make_bound_range_proof(
                public_key,
                secret_key,
                new_balance_cipher,
                static_cast<uint64_t>(current_balance - amount),
                balance_range_blinding.data()
            );
            log_stage("balance_range_ready");
            if (delta_range_proof == nullptr || balance_range_proof == nullptr) {
                throw std::runtime_error("Unable to create Stealth range proofs");
            }

            std::array<uint8_t, 32> amount_commitment{};
            size_t amount_commitment_length = 0;
            if (pvac_pedersen_commit_v2(
                    static_cast<uint64_t>(amount),
                    blinding.data(),
                    amount_commitment.data(),
                    amount_commitment.size(),
                    &amount_commitment_length
                ) != 0 || amount_commitment_length != amount_commitment.size()) {
                throw std::runtime_error("Unable to create Stealth amount commitment");
            }
            zero_proof = pvac_make_zero_proof_bound(
                public_key,
                secret_key,
                delta_cipher,
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (zero_proof == nullptr) throw std::runtime_error("Unable to create Stealth zero proof");
            log_stage("zero_proof_ready");
            const auto delta_range_commitment = pedersen_commit(
                static_cast<uint64_t>(amount),
                delta_range_blinding.data()
            );
            const auto balance_range_commitment = pedersen_commit(
                static_cast<uint64_t>(current_balance - amount),
                balance_range_blinding.data()
            );
            if (pvac_verify_bound_range_commitment(
                    public_key,
                    delta_cipher,
                    delta_range_proof,
                    delta_range_commitment.data()
                ) == 0 ||
                pvac_verify_bound_range_commitment(
                    public_key,
                    new_balance_cipher,
                    balance_range_proof,
                    balance_range_commitment.data()
                ) == 0) {
                throw std::runtime_error("Encrypted balance refresh is required before Stealth");
            }
            if (pvac_verify_zero_bound(
                    public_key,
                    delta_cipher,
                    zero_proof,
                    amount_commitment.data()
                ) == 0) {
                throw std::runtime_error("Local Stealth proof verification failed");
            }
            log_stage("verification_ready");

            const auto result = make_result(env, {
                serialize_cipher(delta_cipher),
                std::vector<uint8_t>(cipher_commitment.begin(), cipher_commitment.end()),
                serialize_bound_range_proof(delta_range_proof),
                serialize_bound_range_proof(balance_range_proof),
                std::vector<uint8_t>(amount_commitment.begin(), amount_commitment.end()),
                serialize_zero_proof(zero_proof),
            });
            log_stage("payload_ready");
            cleanup();
            return result;
        } catch (...) {
            cleanup();
            throw;
        }
    });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeBuildMigration(
    JNIEnv* env,
    jobject,
    jint mode,
    jlong amount,
    jbyteArray current_cipher_array,
    jbyteArray old_public_key_array,
    jbyteArray random_seed_array,
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        constexpr jint key_bound_mode = 1;
        constexpr jint legacy_public_mode = 2;
        constexpr jint legacy_zero_mode = 4;
        if (mode < key_bound_mode || mode > legacy_zero_mode || amount < 0) {
            throw std::invalid_argument("Privacy migration parameters are invalid");
        }
        const bool key_bound = mode == key_bound_mode;
        const bool expose_blinding = mode == legacy_public_mode || mode == legacy_zero_mode;
        auto current_cipher_raw = current_cipher_array == nullptr
            ? std::vector<uint8_t>{}
            : read_bytes(env, current_cipher_array, 0, MaxCipherBytes);
        auto old_key_raw = old_public_key_array == nullptr
            ? std::vector<uint8_t>{}
            : read_bytes(env, old_public_key_array);
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher current_cipher = nullptr;
        pvac_cipher new_cipher = nullptr;
        pvac_pubkey old_public_key = nullptr;
        pvac_zero_proof old_proof = nullptr;
        pvac_zero_proof new_proof = nullptr;
        auto cleanup = [&]() {
            if (new_proof != nullptr) pvac_free_zero_proof(new_proof);
            if (old_proof != nullptr) pvac_free_zero_proof(old_proof);
            if (old_public_key != nullptr) pvac_free_pubkey(old_public_key);
            if (new_cipher != nullptr) pvac_free_cipher(new_cipher);
            if (current_cipher != nullptr) pvac_free_cipher(current_cipher);
        };

        try {
            if (key_bound) {
                current_cipher = pvac_deserialize_cipher(
                    current_cipher_raw.data(),
                    current_cipher_raw.size()
                );
                old_public_key = deserialize_pubkey(old_key_raw);
                if (current_cipher == nullptr || old_public_key == nullptr) {
                    throw std::invalid_argument("Current encrypted balance key material is invalid");
                }
                if (pvac_pubkey_matches_secret_profile(
                        old_public_key,
                        public_key,
                        secret_key
                    ) != 1) {
                    throw std::invalid_argument("Registered Privacy key uses an incompatible proof profile");
                }
                int64_t old_amount = 0;
                if (pvac_dec_value_i64(old_public_key, secret_key, current_cipher, &old_amount) == 0 ||
                    old_amount != amount) {
                    throw std::runtime_error("Registered Privacy key cannot open the current balance");
                }
            }
            new_cipher = pvac_enc_value_seeded(
                public_key,
                secret_key,
                static_cast<uint64_t>(amount),
                random_seed.data()
            );
            if (new_cipher == nullptr) throw std::runtime_error("Unable to encrypt migrated balance");
            const auto commitment = pedersen_commit(
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if (key_bound) {
                old_proof = pvac_make_zero_proof_bound_key_switch(
                    old_public_key,
                    secret_key,
                    current_cipher,
                    static_cast<uint64_t>(amount),
                    blinding.data()
                );
            }
            new_proof = pvac_make_zero_proof_bound(
                public_key,
                secret_key,
                new_cipher,
                static_cast<uint64_t>(amount),
                blinding.data()
            );
            if ((key_bound && old_proof == nullptr) || new_proof == nullptr) {
                throw std::runtime_error("Unable to prove encrypted balance migration");
            }
            if (key_bound && pvac_verify_zero_bound_key_switch(
                    old_public_key,
                    current_cipher,
                    old_proof,
                    commitment.data()
                ) == 0) {
                throw std::runtime_error("Local old-balance proof verification failed");
            }
            if (pvac_verify_zero_bound(
                    public_key,
                    new_cipher,
                    new_proof,
                    commitment.data()
                ) == 0) {
                throw std::runtime_error("Local migrated-balance proof verification failed");
            }
            const auto result = make_result(env, {
                serialize_cipher(new_cipher),
                serialize_zero_proof(new_proof),
                std::vector<uint8_t>(commitment.begin(), commitment.end()),
                key_bound ? serialize_zero_proof(old_proof) : empty_bytes(),
                expose_blinding ? blinding : empty_bytes(),
            });
            cleanup();
            return result;
        } catch (...) {
            cleanup();
            throw;
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_poctra_wallet_privacy_NativePvacBridge_nativeClear(JNIEnv* env, jobject) {
    guard(env, [&]() {
        std::lock_guard<std::mutex> lock(engine_mutex);
        clear_engine_locked();
    });
}
