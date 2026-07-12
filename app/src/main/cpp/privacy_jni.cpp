#include <jni.h>

#include <algorithm>
#include <array>
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

void clear_engine_locked() {
    if (secret_key != nullptr) pvac_free_seckey(secret_key);
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

std::vector<uint8_t> read_bytes(JNIEnv* env, jbyteArray input, size_t expected = 0) {
    if (input == nullptr) throw std::invalid_argument("Missing byte array");
    const auto length = static_cast<size_t>(env->GetArrayLength(input));
    if (expected != 0 && length != expected) throw std::invalid_argument("Invalid byte array length");
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

std::vector<uint8_t> serialize_range_proof(pvac_range_proof proof) {
    size_t length = 0;
    uint8_t* raw = pvac_serialize_range_proof(proof, &length);
    if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize range proof");
    std::vector<uint8_t> result(raw, raw + length);
    pvac_free_bytes(raw);
    return result;
}

std::vector<uint8_t> serialize_aggregated_range_proof(pvac_agg_range_proof proof) {
    size_t length = 0;
    uint8_t* raw = pvac_serialize_agg_range_proof(proof, &length);
    if (raw == nullptr || length == 0) throw std::runtime_error("Unable to serialize aggregated range proof");
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

void throw_java(JNIEnv* env, const char* message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (exception_class != nullptr) env->ThrowNew(exception_class, message);
}

template <typename Callback>
auto guard(JNIEnv* env, Callback callback) -> decltype(callback()) {
    try {
        return callback();
    } catch (const std::exception& error) {
        throw_java(env, error.what());
    } catch (...) {
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
        const auto raw = read_bytes(env, cipher_array);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();
        pvac_cipher cipher = pvac_deserialize_cipher(raw.data(), raw.size());
        if (cipher == nullptr) throw std::invalid_argument("Encrypted balance is invalid");
        uint64_t low = 0;
        uint64_t high = 0;
        pvac_dec_value_fp(public_key, secret_key, cipher, &low, &high);
        pvac_free_cipher(cipher);
        if (high != 0) throw std::runtime_error("Encrypted balance is outside the supported range");
        const std::string value = std::to_string(low);
        return env->NewStringUTF(value.c_str());
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
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        if (amount <= 0 || current_balance < amount) {
            throw std::invalid_argument("Insufficient encrypted balance");
        }
        auto current_cipher_raw = read_bytes(env, current_cipher_array);
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher amount_cipher = nullptr;
        pvac_cipher current_cipher = nullptr;
        pvac_cipher new_balance_cipher = nullptr;
        pvac_zero_proof zero_proof = nullptr;
        pvac_agg_range_proof range_proof = nullptr;
        auto cleanup = [&]() {
            if (range_proof != nullptr) pvac_free_agg_range_proof(range_proof);
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

            range_proof = pvac_make_aggregated_range_proof(
                public_key,
                secret_key,
                new_balance_cipher,
                static_cast<uint64_t>(current_balance - amount)
            );
            if (range_proof == nullptr) throw std::runtime_error("Unable to create balance range proof");

            const auto result = make_result(env, {
                serialize_cipher(amount_cipher),
                std::vector<uint8_t>(amount_commitment.begin(), amount_commitment.end()),
                serialize_zero_proof(zero_proof),
                blinding,
                serialize_aggregated_range_proof(range_proof),
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
    jbyteArray blinding_array
) {
    return guard(env, [&]() -> jobjectArray {
        if (amount <= 0 || current_balance < amount) {
            throw std::invalid_argument("Insufficient encrypted balance");
        }
        auto current_cipher_raw = read_bytes(env, current_cipher_array);
        auto random_seed = read_bytes(env, random_seed_array, 32);
        auto blinding = read_bytes(env, blinding_array, 32);
        ScopedWipe random_seed_wipe(random_seed);
        ScopedWipe blinding_wipe(blinding);
        std::lock_guard<std::mutex> lock(engine_mutex);
        require_initialized();

        pvac_cipher delta_cipher = nullptr;
        pvac_cipher current_cipher = nullptr;
        pvac_cipher new_balance_cipher = nullptr;
        pvac_range_proof delta_range_proof = nullptr;
        pvac_range_proof balance_range_proof = nullptr;
        pvac_zero_proof zero_proof = nullptr;
        auto cleanup = [&]() {
            if (zero_proof != nullptr) pvac_free_zero_proof(zero_proof);
            if (balance_range_proof != nullptr) pvac_free_range_proof(balance_range_proof);
            if (delta_range_proof != nullptr) pvac_free_range_proof(delta_range_proof);
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

            std::exception_ptr delta_error;
            std::exception_ptr balance_error;
            std::thread delta_thread([&]() {
                try {
                    delta_range_proof = pvac_make_range_proof(
                        public_key,
                        secret_key,
                        delta_cipher,
                        static_cast<uint64_t>(amount)
                    );
                } catch (...) {
                    delta_error = std::current_exception();
                }
            });
            std::thread balance_thread;
            try {
                balance_thread = std::thread([&]() {
                    try {
                        balance_range_proof = pvac_make_range_proof(
                            public_key,
                            secret_key,
                            new_balance_cipher,
                            static_cast<uint64_t>(current_balance - amount)
                        );
                    } catch (...) {
                        balance_error = std::current_exception();
                    }
                });
            } catch (...) {
                delta_thread.join();
                throw;
            }
            delta_thread.join();
            balance_thread.join();
            if (delta_error) std::rethrow_exception(delta_error);
            if (balance_error) std::rethrow_exception(balance_error);
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

            const auto result = make_result(env, {
                serialize_cipher(delta_cipher),
                std::vector<uint8_t>(cipher_commitment.begin(), cipher_commitment.end()),
                serialize_range_proof(delta_range_proof),
                serialize_range_proof(balance_range_proof),
                std::vector<uint8_t>(amount_commitment.begin(), amount_commitment.end()),
                serialize_zero_proof(zero_proof),
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
