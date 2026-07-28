#include <array>
#include <chrono>
#include <cstdint>
#include <exception>
#include <future>
#include <iomanip>
#include <iostream>
#include <stdexcept>
#include <string>

#include "pvac_c_api.h"
#include "pvac/crypto/bulletproofs/generators.hpp"
#include "pvac/crypto/ristretto255.hpp"

namespace {

using Clock = std::chrono::steady_clock;

class Timer {
public:
    explicit Timer(std::string scenario)
        : scenario_(std::move(scenario)), started_(Clock::now()), last_(started_) {}

    void mark(const char* phase) {
        const auto now = Clock::now();
        const auto phase_ms =
            std::chrono::duration<double, std::milli>(now - last_).count();
        const auto total_ms =
            std::chrono::duration<double, std::milli>(now - started_).count();
        std::cout << "bench scenario=" << scenario_
                  << " phase=" << phase
                  << " phase_ms=" << std::fixed << std::setprecision(3) << phase_ms
                  << " total_ms=" << total_ms << '\n';
        last_ = now;
    }

private:
    std::string scenario_;
    Clock::time_point started_;
    Clock::time_point last_;
};

std::array<uint8_t, 32> seeded(uint8_t domain) {
    std::array<uint8_t, 32> out{};
    for (size_t i = 0; i < out.size(); ++i)
        out[i] = static_cast<uint8_t>(domain + i * 29U);
    return out;
}

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

void run_arithmetic_selftest() {
    Timer timer("selftest");
    for (uint32_t sample = 0; sample < 4096; ++sample) {
        std::array<uint8_t, 32> field_bytes{};
        std::array<uint8_t, 32> scalar_bytes{};
        std::array<uint8_t, 32> point_scalar_bytes{};
        for (size_t i = 0; i < field_bytes.size(); ++i) {
            field_bytes[i] = static_cast<uint8_t>(
                0x35U + sample * 73U + i * 29U
            );
            scalar_bytes[i] = static_cast<uint8_t>(
                0xa7U + sample * 19U + i * 47U
            );
            point_scalar_bytes[i] = static_cast<uint8_t>(
                0x61U + sample * 31U + i * 11U
            );
        }

        const pvac::Fe25519 field = pvac::fe_frombytes(field_bytes.data());
        std::array<uint8_t, 32> square_bytes{};
        std::array<uint8_t, 32> reference_square_bytes{};
        pvac::fe_tobytes(square_bytes.data(), pvac::fe_sq(field));
        pvac::fe_tobytes(
            reference_square_bytes.data(),
            pvac::fe_mul(field, field)
        );
        require(
            square_bytes == reference_square_bytes,
            "specialized field squaring mismatch"
        );

        const pvac::Scalar scalar = pvac::sc_reduce256(scalar_bytes.data());
        const pvac::Scalar point_scalar =
            pvac::sc_reduce256(point_scalar_bytes.data());
        const pvac::ExtPoint point = pvac::fixed_base_scalarmul(
            pvac::fixed_base_G_table(),
            point_scalar
        );
        require(
            pvac::rist_encode(pvac::ext_scalarmul(point, scalar)) ==
                pvac::rist_encode(pvac::ext_scalarmul_binary(point, scalar)),
            "windowed scalar multiplication mismatch"
        );

        if (sample < 256) {
            const pvac::ExtPoint direct =
                pvac::bp::hash_to_ristretto_extpoint(
                    "pvac.bp.gen.selftest",
                    sample
                );
            const pvac::RistrettoPoint encoded = pvac::rist_encode(direct);
            const pvac::ExtPoint decoded =
                pvac::rist_decode_or_throw(encoded);
            require(
                pvac::rist_encode(pvac::ext_scalarmul(direct, scalar)) ==
                    pvac::rist_encode(pvac::ext_scalarmul(decoded, scalar)),
                "direct generator representative mismatch"
            );
        }
    }
    timer.mark("4096_vectors");
}

struct Context {
    pvac_params params = nullptr;
    pvac_pubkey public_key = nullptr;
    pvac_seckey secret_key = nullptr;

    Context() {
        const auto key_seed = seeded(0x11);
        params = pvac_default_params();
        require(params != nullptr, "default params unavailable");
        pvac_keygen_from_seed(
            params,
            key_seed.data(),
            &public_key,
            &secret_key
        );
        require(public_key != nullptr && secret_key != nullptr, "keygen failed");
    }

    ~Context() {
        if (secret_key != nullptr) pvac_free_seckey(secret_key);
        if (public_key != nullptr) pvac_free_pubkey(public_key);
        if (params != nullptr) pvac_free_params(params);
    }
};

struct CipherHandle {
    pvac_cipher value = nullptr;
    ~CipherHandle() {
        if (value != nullptr) pvac_free_cipher(value);
    }
};

struct ProofHandle {
    pvac_zero_proof value = nullptr;
    ~ProofHandle() {
        if (value != nullptr) pvac_free_zero_proof(value);
    }
};

CipherHandle encrypted(
    const Context& context,
    uint64_t amount,
    uint8_t domain
) {
    const auto seed = seeded(domain);
    CipherHandle out;
    out.value = pvac_enc_value_seeded(
        context.public_key,
        context.secret_key,
        amount,
        seed.data()
    );
    require(out.value != nullptr, "encryption failed");
    return out;
}

std::array<uint8_t, 32> commitment(uint64_t amount, const uint8_t* blinding) {
    std::array<uint8_t, 32> out{};
    size_t written = 0;
    require(
        pvac_pedersen_commit_v2(
            amount,
            blinding,
            out.data(),
            out.size(),
            &written
        ) == 0 && written == out.size(),
        "commitment failed"
    );
    return out;
}

void run_decrypt(const Context& context) {
    constexpr uint64_t balance = 20;
    constexpr uint64_t amount = 2;
    Timer timer("decrypt");
    auto current = encrypted(context, balance, 0x21);
    auto delta = encrypted(context, amount, 0x31);
    CipherHandle remaining;
    remaining.value = pvac_ct_sub(context.public_key, current.value, delta.value);
    require(remaining.value != nullptr, "remaining balance failed");
    timer.mark("cipher_setup");

    const auto amount_blinding = seeded(0x41);
    ProofHandle zero;
    zero.value = pvac_make_zero_proof_bound(
        context.public_key,
        context.secret_key,
        delta.value,
        amount,
        amount_blinding.data()
    );
    require(zero.value != nullptr, "decrypt zero proof failed");
    timer.mark("zero_proof");

    const auto balance_blinding = seeded(0x51);
    ProofHandle range;
    range.value = pvac_make_bound_range_proof(
        context.public_key,
        context.secret_key,
        remaining.value,
        balance - amount,
        balance_blinding.data()
    );
    require(range.value != nullptr, "decrypt range proof failed");
    timer.mark("range_proof");

    const auto amount_commitment = commitment(amount, amount_blinding.data());
    require(
        pvac_verify_zero_bound(
            context.public_key,
            delta.value,
            zero.value,
            amount_commitment.data()
        ) == 1,
        "decrypt zero proof verification failed"
    );
    require(
        pvac_verify_bound_range(
            context.public_key,
            remaining.value,
            range.value
        ) == 1,
        "decrypt range proof verification failed"
    );
    timer.mark("verification");
}

void run_decrypt_parallel(const Context& context) {
    constexpr uint64_t balance = 20;
    constexpr uint64_t amount = 2;
    Timer timer("decrypt_parallel");
    auto current = encrypted(context, balance, 0x22);
    auto delta = encrypted(context, amount, 0x32);
    CipherHandle remaining;
    remaining.value = pvac_ct_sub(context.public_key, current.value, delta.value);
    require(remaining.value != nullptr, "remaining balance failed");
    timer.mark("cipher_setup");

    const auto amount_blinding = seeded(0x42);
    const auto balance_blinding = seeded(0x52);
    ProofHandle zero;
    ProofHandle range;
    auto zero_future = std::async(std::launch::async, [&]() {
        zero.value = pvac_make_zero_proof_bound(
            context.public_key,
            context.secret_key,
            delta.value,
            amount,
            amount_blinding.data()
        );
        require(zero.value != nullptr, "decrypt zero proof failed");
    });
    auto range_future = std::async(std::launch::async, [&]() {
        range.value = pvac_make_bound_range_proof(
            context.public_key,
            context.secret_key,
            remaining.value,
            balance - amount,
            balance_blinding.data()
        );
        require(range.value != nullptr, "decrypt range proof failed");
    });
    zero_future.get();
    range_future.get();
    timer.mark("proofs");

    const auto amount_commitment = commitment(amount, amount_blinding.data());
    auto zero_verify_future = std::async(std::launch::async, [&]() {
        require(
            pvac_verify_zero_bound(
                context.public_key,
                delta.value,
                zero.value,
                amount_commitment.data()
            ) == 1,
            "decrypt zero proof verification failed"
        );
    });
    auto range_verify_future = std::async(std::launch::async, [&]() {
        require(
            pvac_verify_bound_range(
                context.public_key,
                remaining.value,
                range.value
            ) == 1,
            "decrypt range proof verification failed"
        );
    });
    zero_verify_future.get();
    range_verify_future.get();
    timer.mark("verification");
}

void run_zero(const Context& context) {
    constexpr uint64_t amount = 2;
    Timer timer("zero");
    auto cipher = encrypted(context, amount, 0xe1);
    const auto blinding = seeded(0xe2);
    timer.mark("cipher_setup");

    ProofHandle proof;
    proof.value = pvac_make_zero_proof_bound(
        context.public_key,
        context.secret_key,
        cipher.value,
        amount,
        blinding.data()
    );
    require(proof.value != nullptr, "zero proof failed");
    timer.mark("proof");

    const auto amount_commitment = commitment(amount, blinding.data());
    require(
        pvac_verify_zero_bound(
            context.public_key,
            cipher.value,
            proof.value,
            amount_commitment.data()
        ) == 1,
        "zero proof verification failed"
    );
    timer.mark("verification");
}

void run_range(const Context& context) {
    constexpr uint64_t amount = 18;
    Timer timer("range");
    auto cipher = encrypted(context, amount, 0xe3);
    const auto blinding = seeded(0xe4);
    timer.mark("cipher_setup");

    ProofHandle proof;
    proof.value = pvac_make_bound_range_proof(
        context.public_key,
        context.secret_key,
        cipher.value,
        amount,
        blinding.data()
    );
    require(proof.value != nullptr, "range proof failed");
    timer.mark("proof");

    require(
        pvac_verify_bound_range(
            context.public_key,
            cipher.value,
            proof.value
        ) == 1,
        "range proof verification failed"
    );
    timer.mark("verification");
}

void run_stealth(const Context& context) {
    constexpr uint64_t balance = 20;
    constexpr uint64_t amount = 2;
    Timer timer("stealth");
    auto current = encrypted(context, balance, 0x61);
    auto delta = encrypted(context, amount, 0x71);
    CipherHandle remaining;
    remaining.value = pvac_ct_sub(context.public_key, current.value, delta.value);
    require(remaining.value != nullptr, "remaining balance failed");
    timer.mark("cipher_setup");

    const auto delta_range_blinding = seeded(0x81);
    ProofHandle delta_range;
    delta_range.value = pvac_make_bound_range_proof(
        context.public_key,
        context.secret_key,
        delta.value,
        amount,
        delta_range_blinding.data()
    );
    require(delta_range.value != nullptr, "stealth delta range proof failed");
    timer.mark("delta_range_proof");

    const auto balance_range_blinding = seeded(0x91);
    ProofHandle balance_range;
    balance_range.value = pvac_make_bound_range_proof(
        context.public_key,
        context.secret_key,
        remaining.value,
        balance - amount,
        balance_range_blinding.data()
    );
    require(balance_range.value != nullptr, "stealth balance range proof failed");
    timer.mark("balance_range_proof");

    const auto amount_blinding = seeded(0xa1);
    ProofHandle zero;
    zero.value = pvac_make_zero_proof_bound(
        context.public_key,
        context.secret_key,
        delta.value,
        amount,
        amount_blinding.data()
    );
    require(zero.value != nullptr, "stealth zero proof failed");
    timer.mark("zero_proof");

    const auto amount_commitment = commitment(amount, amount_blinding.data());
    require(
        pvac_verify_bound_range(
            context.public_key,
            delta.value,
            delta_range.value
        ) == 1,
        "stealth delta range verification failed"
    );
    require(
        pvac_verify_bound_range(
            context.public_key,
            remaining.value,
            balance_range.value
        ) == 1,
        "stealth balance range verification failed"
    );
    require(
        pvac_verify_zero_bound(
            context.public_key,
            delta.value,
            zero.value,
            amount_commitment.data()
        ) == 1,
        "stealth zero verification failed"
    );
    timer.mark("verification");
}

void run_migration(const Context& context) {
    constexpr uint64_t amount = 20;
    Timer timer("migration");
    auto old_cipher = encrypted(context, amount, 0xb1);
    auto new_cipher = encrypted(context, amount, 0xc1);
    timer.mark("cipher_setup");

    const auto blinding = seeded(0xd1);
    const auto amount_commitment = commitment(amount, blinding.data());
    ProofHandle old_proof;
    old_proof.value = pvac_make_zero_proof_bound(
        context.public_key,
        context.secret_key,
        old_cipher.value,
        amount,
        blinding.data()
    );
    require(old_proof.value != nullptr, "old migration proof failed");
    timer.mark("old_proof");

    ProofHandle new_proof;
    new_proof.value = pvac_make_zero_proof_bound(
        context.public_key,
        context.secret_key,
        new_cipher.value,
        amount,
        blinding.data()
    );
    require(new_proof.value != nullptr, "new migration proof failed");
    timer.mark("new_proof");

    require(
        pvac_verify_zero_bound(
            context.public_key,
            old_cipher.value,
            old_proof.value,
            amount_commitment.data()
        ) == 1,
        "old migration proof verification failed"
    );
    require(
        pvac_verify_zero_bound(
            context.public_key,
            new_cipher.value,
            new_proof.value,
            amount_commitment.data()
        ) == 1,
        "new migration proof verification failed"
    );
    timer.mark("verification");
}

}  // namespace

int main(int argc, char** argv) {
    try {
        const std::string scenario = argc > 1 ? argv[1] : "decrypt";
        Timer setup("setup");
        Context context;
        setup.mark("keygen");

        if (scenario == "zero") {
            run_zero(context);
        } else if (scenario == "selftest") {
            run_arithmetic_selftest();
        } else if (scenario == "range") {
            run_range(context);
        } else if (scenario == "decrypt") {
            run_decrypt(context);
        } else if (scenario == "decrypt-parallel") {
            run_decrypt_parallel(context);
        } else if (scenario == "stealth") {
            run_stealth(context);
        } else if (scenario == "migration") {
            run_migration(context);
        } else {
            std::cerr << "usage: poctra_privacy_bench "
                      << "[selftest|zero|range|decrypt|decrypt-parallel|stealth|migration]\n";
            return 2;
        }
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "benchmark failed: " << error.what() << '\n';
        return 1;
    }
}
