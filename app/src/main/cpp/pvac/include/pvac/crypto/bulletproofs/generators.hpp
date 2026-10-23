#pragma once

#include <cstdint>
#include <vector>
#include <mutex>
#include <memory>
#include <limits>
#include <stdexcept>
#include "../../core/hash.hpp"
#include "../../core/parallel.hpp"
#include "../ristretto255.hpp"

namespace pvac {
namespace bp {

inline constexpr size_t BP_MAX_VECTOR_SIZE = static_cast<size_t>(1) << 20;

inline ExtPoint hash_to_ristretto_extpoint(const char* domain, uint64_t index) {

    Sha256 h;
    h.init();
    size_t dlen = strlen(domain);
    h.update(reinterpret_cast<const uint8_t*>(domain), dlen);
    uint8_t idx_bytes[8];
    for (int i = 0; i < 8; i++) idx_bytes[i] = (uint8_t)(index >> (i * 8));
    h.update(idx_bytes, 8);
    uint8_t hash[32];
    h.finish(hash);

    hash[31] &= 0x7f;

    Fe25519 r0 = fe_frombytes(hash);
    Fe25519 d = ed_d();
    Fe25519 sqrtm1 = fe_sqrtm1();
    Fe25519 one_minus_d_sq = rist_one_minus_d_sq();
    Fe25519 d_minus_one_sq = rist_d_minus_one_sq();
    Fe25519 sqrt_ad_minus_one = rist_sqrt_ad_minus_one();

    Fe25519 r = fe_mul(sqrtm1, fe_sq(r0));
    Fe25519 u = fe_mul(fe_add(r, fe_one()), one_minus_d_sq);
    Fe25519 v = fe_mul(fe_sub(fe_neg(fe_one()), fe_mul(r, d)), fe_add(r, d));

    auto [was_square, s_result] = fe_invsqrt(u, v);

    Fe25519 s_prime = fe_neg(fe_abs(fe_mul(s_result, r0)));
    s_result = fe_cmov(s_prime, s_result, was_square);
    Fe25519 c = fe_cmov(r, fe_neg(fe_one()), was_square);

    Fe25519 N = fe_sub(fe_mul(fe_mul(c, fe_sub(r, fe_one())), d_minus_one_sq), v);
    Fe25519 w0 = fe_mul(fe_add(s_result, s_result), v);
    Fe25519 w1 = fe_mul(N, sqrt_ad_minus_one);
    Fe25519 w2 = fe_sub(fe_one(), fe_sq(s_result));
    Fe25519 w3 = fe_add(fe_one(), fe_sq(s_result));

    return {
        fe_mul(w0, w3),
        fe_mul(w2, w1),
        fe_mul(w1, w3),
        fe_mul(w0, w2)
    };
}

inline RistrettoPoint hash_to_ristretto_point(const char* domain, uint64_t index) {
    return rist_encode(hash_to_ristretto_extpoint(domain, index));
}

class GeneratorTable {
public:
    struct Snapshot {
        std::vector<ExtPoint> G_ext;
        std::vector<ExtPoint> H_ext;
    };

private:
    mutable std::shared_ptr<const Snapshot> snapshot_;
    mutable std::mutex mtx_;

    std::shared_ptr<const Snapshot> ensure_size(size_t n) const {
        if (n > BP_MAX_VECTOR_SIZE)
            throw std::runtime_error("pvac: generator size rejected");
        std::lock_guard<std::mutex> lock(mtx_);
        const auto current = snapshot_;
        if (!current)
            throw std::runtime_error("pvac: generator table unavailable");
        if (current->G_ext.size() != current->H_ext.size())
            throw std::runtime_error("pvac: generator table shape rejected");
        if (current->G_ext.size() >= n) return current;

        const size_t old = current->G_ext.size();
        auto next = std::make_shared<Snapshot>(*current);
        next->G_ext.resize(n);
        next->H_ext.resize(n);
        detail::parallel_for(old, n, 256, [&](size_t i) {
            next->G_ext[i] = hash_to_ristretto_extpoint("pvac.bp.gen.G", i);
            next->H_ext[i] = hash_to_ristretto_extpoint("pvac.bp.gen.H", i);
        });
        snapshot_ = next;
        return next;
    }

public:
    GeneratorTable() : snapshot_(std::make_shared<Snapshot>()) {}

    RistrettoPoint G(size_t i) const {
        const auto table = ensure_size(i + 1);
        return rist_encode(table->G_ext[i]);
    }

    RistrettoPoint H(size_t i) const {
        const auto table = ensure_size(i + 1);
        return rist_encode(table->H_ext[i]);
    }

    void precompute(size_t n) const {
        ensure_size(n);
    }

    std::shared_ptr<const Snapshot> snapshot(size_t n) const {
        return ensure_size(n);
    }

    std::vector<RistrettoPoint> G_vec(size_t n) const {
        const auto table = ensure_size(n);
        std::vector<RistrettoPoint> out(n);
        detail::parallel_for(0, n, 256, [&](size_t i) {
            out[i] = rist_encode(table->G_ext[i]);
        });
        return out;
    }

    std::vector<RistrettoPoint> H_vec(size_t n) const {
        const auto table = ensure_size(n);
        std::vector<RistrettoPoint> out(n);
        detail::parallel_for(0, n, 256, [&](size_t i) {
            out[i] = rist_encode(table->H_ext[i]);
        });
        return out;
    }

    std::vector<ExtPoint> G_ext_vec(size_t n) const {
        const auto table = ensure_size(n);
        return std::vector<ExtPoint>(
            table->G_ext.begin(),
            table->G_ext.begin() + n
        );
    }

    std::vector<ExtPoint> H_ext_vec(size_t n) const {
        const auto table = ensure_size(n);
        return std::vector<ExtPoint>(
            table->H_ext.begin(),
            table->H_ext.begin() + n
        );
    }
};

inline const GeneratorTable& generators() {
    static GeneratorTable table;
    return table;
}

inline const RistrettoPoint& pedersen_B() {
    static RistrettoPoint B = rist_G();
    return B;
}

inline const RistrettoPoint& pedersen_B_blinding() {
    static RistrettoPoint B_bl = rist_H();
    return B_bl;
}

inline const ExtPoint& pedersen_B_ext() {
    static const ExtPoint B = rist_decode_or_throw(pedersen_B());
    return B;
}

inline const ExtPoint& pedersen_B_blinding_ext() {
    static const ExtPoint B_bl = rist_decode_or_throw(pedersen_B_blinding());
    return B_bl;
}

inline size_t next_power_of_2(size_t n) {
    if (n == 0) return 1;
    if (n > BP_MAX_VECTOR_SIZE)
        throw std::runtime_error("pvac: vector size rejected");
    size_t out = 1;
    while (out < n) {
        if (out > BP_MAX_VECTOR_SIZE / 2)
            throw std::runtime_error("pvac: vector size overflow");
        out <<= 1;
    }
    return out;
}

inline size_t log2_size(size_t n) {
    if (n == 0 || n > BP_MAX_VECTOR_SIZE || (n & (n - 1)))
        throw std::runtime_error("pvac: log2 size rejected");
    size_t r = 0;
    while ((1ULL << r) < n) r++;
    return r;
}

}
}
