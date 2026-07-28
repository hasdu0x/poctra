#pragma once

#include <cstdint>
#include <vector>
#include <algorithm>
#include "simd.hpp"

namespace pvac {

struct BitVec {
    size_t nbits;
    std::vector<uint64_t> w;

    static BitVec make(size_t n) {
        BitVec v;
        v.nbits = n;
        v.w.assign((n + 63) / 64, 0);
        return v;
    }

    void xor_with(const BitVec & b) {
        size_t L = std::min(w.size(), b.w.size());
        simd::xor_u64(w.data(), b.w.data(), L);
    }

    size_t popcnt() const {
        return simd::popcnt_u64(w.data(), w.size());
    }
};

    inline int parity64(uint64_t x) {
        x ^= x >> 32;
        x ^= x >> 16;
        x ^= x >> 8;
        x ^= x >> 4;
        x &= 0xF;
        return (0x6996 >> x) & 1;
    }
}
