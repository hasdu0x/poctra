#pragma once

#include <cstddef>
#include <cstdint>

#if defined(__AVX2__)
#include <immintrin.h>
#elif defined(__wasm_simd128__)
#include <wasm_simd128.h>
#elif defined(__SSE2__)
#include <emmintrin.h>
#endif

#if defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace pvac {
namespace simd {

inline const char* feature_string() {
#if defined(__AVX2__)
    return "avx2";
#elif defined(__wasm_simd128__)
    return "wasm_simd128";
#elif defined(__aarch64__)
    return "neon";
#elif defined(__SSE2__)
    return "sse2";
#else
    return "scalar";
#endif
}

inline void xor_u64(uint64_t* destination, const uint64_t* source, size_t count) {
    size_t i = 0;
#if defined(__AVX2__)
    for (; i + 4 <= count; i += 4) {
        const __m256i a =
            _mm256_loadu_si256(reinterpret_cast<const __m256i*>(destination + i));
        const __m256i b =
            _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(
            reinterpret_cast<__m256i*>(destination + i),
            _mm256_xor_si256(a, b)
        );
    }
#elif defined(__wasm_simd128__)
    for (; i + 2 <= count; i += 2) {
        const v128_t a = wasm_v128_load(destination + i);
        const v128_t b = wasm_v128_load(source + i);
        wasm_v128_store(destination + i, wasm_v128_xor(a, b));
    }
#elif defined(__aarch64__)
    for (; i + 2 <= count; i += 2) {
        const uint64x2_t a = vld1q_u64(destination + i);
        const uint64x2_t b = vld1q_u64(source + i);
        vst1q_u64(destination + i, veorq_u64(a, b));
    }
#elif defined(__SSE2__)
    for (; i + 2 <= count; i += 2) {
        const __m128i a =
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(destination + i));
        const __m128i b =
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(
            reinterpret_cast<__m128i*>(destination + i),
            _mm_xor_si128(a, b)
        );
    }
#endif
    for (; i < count; ++i) destination[i] ^= source[i];
}

inline uint64_t and_xor_reduce(
    const uint64_t* a,
    const uint64_t* b,
    size_t count
) {
    size_t i = 0;
    uint64_t result = 0;
#if defined(__AVX2__)
    __m256i accumulator = _mm256_setzero_si256();
    for (; i + 4 <= count; i += 4) {
        const __m256i av =
            _mm256_loadu_si256(reinterpret_cast<const __m256i*>(a + i));
        const __m256i bv =
            _mm256_loadu_si256(reinterpret_cast<const __m256i*>(b + i));
        accumulator = _mm256_xor_si256(
            accumulator,
            _mm256_and_si256(av, bv)
        );
    }
    alignas(32) uint64_t lanes[4];
    _mm256_storeu_si256(reinterpret_cast<__m256i*>(lanes), accumulator);
    result = lanes[0] ^ lanes[1] ^ lanes[2] ^ lanes[3];
#elif defined(__wasm_simd128__)
    v128_t accumulator = wasm_i64x2_splat(0);
    for (; i + 2 <= count; i += 2) {
        accumulator = wasm_v128_xor(
            accumulator,
            wasm_v128_and(wasm_v128_load(a + i), wasm_v128_load(b + i))
        );
    }
    alignas(16) uint64_t lanes[2];
    wasm_v128_store(lanes, accumulator);
    result = lanes[0] ^ lanes[1];
#elif defined(__aarch64__)
    uint64x2_t accumulator = vdupq_n_u64(0);
    for (; i + 2 <= count; i += 2) {
        accumulator = veorq_u64(
            accumulator,
            vandq_u64(vld1q_u64(a + i), vld1q_u64(b + i))
        );
    }
    uint64_t lanes[2];
    vst1q_u64(lanes, accumulator);
    result = lanes[0] ^ lanes[1];
#elif defined(__SSE2__)
    __m128i accumulator = _mm_setzero_si128();
    for (; i + 2 <= count; i += 2) {
        accumulator = _mm_xor_si128(
            accumulator,
            _mm_and_si128(
                _mm_loadu_si128(reinterpret_cast<const __m128i*>(a + i)),
                _mm_loadu_si128(reinterpret_cast<const __m128i*>(b + i))
            )
        );
    }
    alignas(16) uint64_t lanes[2];
    _mm_store_si128(reinterpret_cast<__m128i*>(lanes), accumulator);
    result = lanes[0] ^ lanes[1];
#endif
    for (; i < count; ++i) result ^= a[i] & b[i];
    return result;
}

inline uint64_t or_reduce_u64(const uint64_t* values, size_t count) {
    size_t i = 0;
    uint64_t result = 0;
#if defined(__AVX2__)
    __m256i accumulator = _mm256_setzero_si256();
    for (; i + 4 <= count; i += 4) {
        accumulator = _mm256_or_si256(
            accumulator,
            _mm256_loadu_si256(reinterpret_cast<const __m256i*>(values + i))
        );
    }
    alignas(32) uint64_t lanes[4];
    _mm256_storeu_si256(reinterpret_cast<__m256i*>(lanes), accumulator);
    result = lanes[0] | lanes[1] | lanes[2] | lanes[3];
#elif defined(__wasm_simd128__)
    v128_t accumulator = wasm_i64x2_splat(0);
    for (; i + 2 <= count; i += 2)
        accumulator = wasm_v128_or(accumulator, wasm_v128_load(values + i));
    alignas(16) uint64_t lanes[2];
    wasm_v128_store(lanes, accumulator);
    result = lanes[0] | lanes[1];
#elif defined(__aarch64__)
    uint64x2_t accumulator = vdupq_n_u64(0);
    for (; i + 2 <= count; i += 2)
        accumulator = vorrq_u64(accumulator, vld1q_u64(values + i));
    uint64_t lanes[2];
    vst1q_u64(lanes, accumulator);
    result = lanes[0] | lanes[1];
#elif defined(__SSE2__)
    __m128i accumulator = _mm_setzero_si128();
    for (; i + 2 <= count; i += 2) {
        accumulator = _mm_or_si128(
            accumulator,
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(values + i))
        );
    }
    alignas(16) uint64_t lanes[2];
    _mm_store_si128(reinterpret_cast<__m128i*>(lanes), accumulator);
    result = lanes[0] | lanes[1];
#endif
    for (; i < count; ++i) result |= values[i];
    return result;
}

inline size_t popcnt_u64(const uint64_t* values, size_t count) {
    size_t result = 0;
    size_t i = 0;
    for (; i + 4 <= count; i += 4) {
        result += static_cast<size_t>(__builtin_popcountll(values[i]));
        result += static_cast<size_t>(__builtin_popcountll(values[i + 1]));
        result += static_cast<size_t>(__builtin_popcountll(values[i + 2]));
        result += static_cast<size_t>(__builtin_popcountll(values[i + 3]));
    }
    for (; i < count; ++i)
        result += static_cast<size_t>(__builtin_popcountll(values[i]));
    return result;
}

}  // namespace simd
}  // namespace pvac
