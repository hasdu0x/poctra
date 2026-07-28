#pragma once

#include <cstdint>
#include <vector>
#include <cassert>
#include <cstdlib>
#include "transcript.hpp"
#include "generators.hpp"
#include "../../core/parallel.hpp"
#include "../ristretto255.hpp"

namespace pvac {
namespace bp {

struct InnerProductProof {
    std::vector<RistrettoPoint> L;
    std::vector<RistrettoPoint> R;
    Scalar a;
    Scalar b;
};

inline size_t msm_window_for_size(size_t n) {
    static int forced = []() {
        const char* env = std::getenv("PVAC_MSM_WINDOW");
        if (!env || !*env) return 0;
        char* end = nullptr;
        long v = std::strtol(env, &end, 10);
        if (end != env && v >= 2 && v <= 12)
            return static_cast<int>(v);
        return 0;
    }();
    if (forced > 0)
        return static_cast<size_t>(forced);

    if (n < 32) return 3;
    if (n < 128) return 4;
    if (n < 512) return 5;
    if (n < 2048) return 6;
    if (n < 8192) return 7;
    return 9;
}

inline ExtPoint two_scalar_mul(
    const Scalar& a,
    const ExtPoint& P,
    const Scalar& b,
    const ExtPoint& Q
) {
    uint8_t ab[32], bb[32];
    sc_tobytes(ab, a);
    sc_tobytes(bb, b);

    std::array<ExtPoint, 4> ptab = {
        ext_identity(),
        P,
        ext_double(P),
        ext_identity()
    };
    ptab[3] = ext_add(ptab[1], ptab[2]);

    std::array<ExtPoint, 4> qtab = {
        ext_identity(),
        Q,
        ext_double(Q),
        ext_identity()
    };
    qtab[3] = ext_add(qtab[1], qtab[2]);

    std::array<ExtPoint, 16> table;
    std::array<Fe25519, 16> table_t2d;
    table[0] = ext_identity();
    for (uint8_t ai = 0; ai < 4; ++ai) {
        for (uint8_t bi = 0; bi < 4; ++bi) {
            uint8_t idx = ai | (bi << 2);
            if (idx == 0) continue;
            if (ai == 0) table[idx] = qtab[bi];
            else if (bi == 0) table[idx] = ptab[ai];
            else table[idx] = ext_add(ptab[ai], qtab[bi]);
        }
    }
    for (size_t i = 0; i < table.size(); ++i)
        table_t2d[i] = fe_mul(table[i].T, ed_2d());

    ExtPoint acc = ext_identity();
    for (int chunk = 127; chunk >= 0; --chunk) {
        acc = ext_double(acc);
        acc = ext_double(acc);
        int bit = chunk * 2;
        uint8_t ai = (ab[bit >> 3] >> (bit & 7)) & 3;
        uint8_t bi = (bb[bit >> 3] >> (bit & 7)) & 3;
        uint8_t idx = ai | (bi << 2);
        if (idx)
            acc = ext_add_with_t2d(acc, table[idx], table_t2d[idx]);
    }
    return acc;
}

inline ExtPoint multi_scalar_mul_extpoint(
    const std::vector<Scalar>& scalars,
    const std::vector<ExtPoint>& points
) {
    assert(scalars.size() == points.size());
    size_t n = scalars.size();
    if (n == 0) return ext_identity();

    if (n == 1)
        return ext_scalarmul(points[0], scalars[0]);

    if (n == 2)
        return two_scalar_mul(scalars[0], points[0], scalars[1], points[1]);

    std::vector<std::array<uint8_t, 32>> sbytes(n);
    for (size_t i = 0; i < n; i++)
        sc_tobytes(sbytes[i].data(), scalars[i]);
    std::vector<Fe25519> point_t2d(n);
    detail::parallel_for(0, n, 256, [&](size_t i) {
        point_t2d[i] = fe_mul(points[i].T, ed_2d());
    });

    size_t w = msm_window_for_size(n);
    size_t num_buckets = (1ULL << w) - 1;
    size_t num_windows = (256 + w - 1) / w;

    const ExtPoint identity = ext_identity();
    std::vector<ExtPoint> window_sums(num_windows, identity);
    detail::parallel_for(0, num_windows, 1, [&](size_t window) {
        std::vector<ExtPoint> buckets(num_buckets, identity);
        const size_t bit_start = window * w;

        for (size_t i = 0; i < n; i++) {
            uint32_t bucket_idx = 0;
            for (size_t b = 0; b < w; b++) {
                size_t bit = bit_start + b;
                if (bit >= 256) break;
                size_t byte_idx = bit >> 3;
                size_t bit_in_byte = bit & 7;
                if ((sbytes[i][byte_idx] >> bit_in_byte) & 1)
                    bucket_idx |= (1U << b);
            }

            if (bucket_idx == 0) continue;
            buckets[bucket_idx - 1] = ext_add_with_t2d(
                buckets[bucket_idx - 1],
                points[i],
                point_t2d[i]
            );
        }

        ExtPoint running_sum = ext_identity();
        ExtPoint window_sum = ext_identity();

        for (size_t k = num_buckets; k > 0; k--) {
            running_sum = ext_add(running_sum, buckets[k - 1]);
            window_sum = ext_add(window_sum, running_sum);
        }
        window_sums[window] = window_sum;
    });

    ExtPoint result = identity;
    for (size_t window = num_windows; window > 0; --window) {
        for (size_t d = 0; d < w; ++d)
            result = ext_double(result);
        result = ext_add(result, window_sums[window - 1]);
    }

    return result;
}

inline RistrettoPoint multi_scalar_mul_ext(
    const std::vector<Scalar>& scalars,
    const std::vector<ExtPoint>& points
) {
    return rist_encode(multi_scalar_mul_extpoint(scalars, points));
}

inline RistrettoPoint multi_scalar_mul(
    const std::vector<Scalar>& scalars,
    const std::vector<RistrettoPoint>& points
) {
    assert(scalars.size() == points.size());
    size_t n = scalars.size();
    if (n == 0) return rist_identity();

    std::vector<ExtPoint> decoded(n);
    for (size_t i = 0; i < n; i++)
        decoded[i] = rist_decode_or_throw(points[i]);

    return multi_scalar_mul_ext(scalars, decoded);
}

inline Scalar inner_product(const std::vector<Scalar>& a, const std::vector<Scalar>& b) {
    assert(a.size() == b.size());
    Scalar result = sc_zero();
    for (size_t i = 0; i < a.size(); i++)
        result = sc_add(result, sc_mul(a[i], b[i]));
    return result;
}

inline InnerProductProof ipp_prove_ext(
    Transcript& transcript,
    const ExtPoint& Q,
    std::vector<Scalar> a,
    std::vector<Scalar> b,
    std::vector<ExtPoint> G_vec,
    std::vector<ExtPoint> H_vec,
    std::vector<Scalar> H_scale = {}
) {
    size_t n = a.size();
    assert(n == b.size());
    assert(n == G_vec.size());
    assert(n == H_vec.size());
    assert(H_scale.empty() || n == H_scale.size());
    assert(n > 0 && (n & (n - 1)) == 0);

    size_t lg = log2_size(n);
    InnerProductProof proof;
    proof.L.resize(lg);
    proof.R.resize(lg);

    for (size_t k = 0; k < lg; k++) {
        size_t half = n / 2;
        const bool has_H_scale = !H_scale.empty();

        Scalar c_L = sc_zero();
        Scalar c_R = sc_zero();
        for (size_t i = 0; i < half; i++) {
            c_L = sc_add(c_L, sc_mul(a[i], b[half + i]));
            c_R = sc_add(c_R, sc_mul(a[half + i], b[i]));
        }

        {
            std::vector<Scalar> scalars;
            std::vector<ExtPoint> points;
            scalars.reserve(n + 1);
            points.reserve(n + 1);

            for (size_t i = 0; i < half; i++) {
                scalars.push_back(a[i]);
                points.push_back(G_vec[half + i]);
            }
            for (size_t i = 0; i < half; i++) {
                scalars.push_back(
                    has_H_scale
                        ? sc_mul(b[half + i], H_scale[i])
                        : b[half + i]
                );
                points.push_back(H_vec[i]);
            }
            scalars.push_back(c_L);
            points.push_back(Q);

            proof.L[k] = multi_scalar_mul_ext(scalars, points);
        }

        {
            std::vector<Scalar> scalars;
            std::vector<ExtPoint> points;
            scalars.reserve(n + 1);
            points.reserve(n + 1);

            for (size_t i = 0; i < half; i++) {
                scalars.push_back(a[half + i]);
                points.push_back(G_vec[i]);
            }
            for (size_t i = 0; i < half; i++) {
                scalars.push_back(
                    has_H_scale
                        ? sc_mul(b[i], H_scale[half + i])
                        : b[i]
                );
                points.push_back(H_vec[half + i]);
            }
            scalars.push_back(c_R);
            points.push_back(Q);

            proof.R[k] = multi_scalar_mul_ext(scalars, points);
        }

        transcript.append_point("L", proof.L[k]);
        transcript.append_point("R", proof.R[k]);
        Scalar u_k = transcript.challenge_scalar("u");
        Scalar u_k_inv = sc_inv(u_k);

        std::vector<Scalar> a_new(half);
        std::vector<Scalar> b_new(half);
        std::vector<ExtPoint> G_new(half);
        std::vector<ExtPoint> H_new(half);

        detail::parallel_for(0, half, 128, [&](size_t i) {
            a_new[i] = sc_add(sc_mul(a[i], u_k), sc_mul(a[half + i], u_k_inv));
            b_new[i] = sc_add(sc_mul(b[i], u_k_inv), sc_mul(b[half + i], u_k));

            G_new[i] = two_scalar_mul(u_k_inv, G_vec[i], u_k, G_vec[half + i]);
            if (has_H_scale) {
                H_new[i] = two_scalar_mul(
                    sc_mul(u_k, H_scale[i]),
                    H_vec[i],
                    sc_mul(u_k_inv, H_scale[half + i]),
                    H_vec[half + i]
                );
            } else {
                H_new[i] = two_scalar_mul(
                    u_k,
                    H_vec[i],
                    u_k_inv,
                    H_vec[half + i]
                );
            }
        });

        a = std::move(a_new);
        b = std::move(b_new);
        G_vec = std::move(G_new);
        H_vec = std::move(H_new);
        H_scale.clear();
        n = half;
    }

    assert(a.size() == 1 && b.size() == 1);
    proof.a = a[0];
    proof.b = b[0];
    return proof;
}

inline InnerProductProof ipp_prove(
    Transcript& transcript,
    const RistrettoPoint& Q,
    std::vector<Scalar> a,
    std::vector<Scalar> b,
    std::vector<RistrettoPoint> G_vec,
    std::vector<RistrettoPoint> H_vec
) {
    ExtPoint Q_ext = rist_decode_or_throw(Q);

    std::vector<ExtPoint> G_ext(G_vec.size());
    std::vector<ExtPoint> H_ext(H_vec.size());
    for (size_t i = 0; i < G_vec.size(); i++)
        G_ext[i] = rist_decode_or_throw(G_vec[i]);
    for (size_t i = 0; i < H_vec.size(); i++)
        H_ext[i] = rist_decode_or_throw(H_vec[i]);

    return ipp_prove_ext(
        transcript,
        Q_ext,
        std::move(a),
        std::move(b),
        std::move(G_ext),
        std::move(H_ext)
    );
}

inline std::vector<Scalar> ipp_verification_scalars(
    const std::vector<Scalar>& challenges
) {
    size_t lg = challenges.size();
    size_t n = 1ULL << lg;

    std::vector<Scalar> u_inv(lg);
    for (size_t k = 0; k < lg; k++)
        u_inv[k] = sc_inv(challenges[k]);

    std::vector<Scalar> s(n);
    s[0] = Scalar{{1, 0, 0, 0}};
    for (size_t k = 0; k < lg; k++)
        s[0] = sc_mul(s[0], u_inv[k]);

    for (size_t i = 1; i < n; i++) {

        size_t k = 0;
        size_t tmp = i;
        while (tmp >>= 1) k++;

        size_t challenge_idx = lg - 1 - k;
        Scalar u_sq = sc_mul(challenges[challenge_idx], challenges[challenge_idx]);
        s[i] = sc_mul(s[i ^ (1ULL << k)], u_sq);
    }

    return s;
}

inline std::vector<Scalar> batch_sc_inv(const std::vector<Scalar>& vals) {
    size_t n = vals.size();
    if (n == 0) return {};

    std::vector<Scalar> prefix(n);
    prefix[0] = vals[0];
    for (size_t i = 1; i < n; i++)
        prefix[i] = sc_mul(prefix[i-1], vals[i]);

    Scalar inv_all = sc_inv(prefix[n-1]);

    std::vector<Scalar> result(n);
    for (size_t i = n - 1; i > 0; i--) {
        result[i] = sc_mul(inv_all, prefix[i-1]);
        inv_all = sc_mul(inv_all, vals[i]);
    }
    result[0] = inv_all;

    return result;
}

inline bool ipp_verify(
    Transcript& transcript,
    const RistrettoPoint& P,
    const RistrettoPoint& Q,
    const InnerProductProof& proof,
    size_t n
) {
    size_t lg = proof.L.size();
    if (proof.R.size() != lg) return false;
    if ((1ULL << lg) != n) return false;

    std::vector<Scalar> challenges(lg);
    for (size_t k = 0; k < lg; k++) {
        transcript.append_point("L", proof.L[k]);
        transcript.append_point("R", proof.R[k]);
        challenges[k] = transcript.challenge_scalar("u");
    }

    auto s = ipp_verification_scalars(challenges);

    auto s_inv = batch_sc_inv(s);

    std::vector<Scalar> scalars;
    std::vector<RistrettoPoint> points;
    scalars.reserve(2 * n + 2 * lg + 2);
    points.reserve(2 * n + 2 * lg + 2);

    Scalar ab = sc_mul(proof.a, proof.b);

    const auto& gen = generators();
    for (size_t i = 0; i < n; i++) {
        scalars.push_back(sc_mul(proof.a, s[i]));
        points.push_back(gen.G(i));
    }

    for (size_t i = 0; i < n; i++) {
        scalars.push_back(sc_mul(proof.b, s_inv[i]));
        points.push_back(gen.H(i));
    }

    scalars.push_back(ab);
    points.push_back(Q);

    std::vector<Scalar> u_sqs(lg);
    for (size_t k = 0; k < lg; k++)
        u_sqs[k] = sc_mul(challenges[k], challenges[k]);
    auto u_inv_sqs = batch_sc_inv(u_sqs);

    for (size_t k = 0; k < lg; k++) {
        scalars.push_back(sc_neg(u_sqs[k]));
        points.push_back(proof.L[k]);

        scalars.push_back(sc_neg(u_inv_sqs[k]));
        points.push_back(proof.R[k]);
    }

    RistrettoPoint expected = multi_scalar_mul(scalars, points);
    return expected == P;
}

}
}
