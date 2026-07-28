#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdlib>
#include <exception>
#include <mutex>
#include <thread>
#include <vector>

namespace pvac {
namespace detail {

inline size_t configured_worker_count() {
    static const size_t count = []() {
        size_t detected = static_cast<size_t>(std::thread::hardware_concurrency());
        if (detected == 0) detected = 1;

        const char* value = std::getenv("PVAC_THREADS");
        if (!value || !*value) return detected;

        char* end = nullptr;
        const unsigned long parsed = std::strtoul(value, &end, 10);
        if (end == value || parsed == 0) return detected;
        return std::min<size_t>(static_cast<size_t>(parsed), 64);
    }();
    return count;
}

inline std::atomic<size_t>& available_worker_tokens() {
    static std::atomic<size_t> tokens{
        configured_worker_count() > 1 ? configured_worker_count() - 1 : 0
    };
    return tokens;
}

inline std::atomic<size_t>& active_proof_jobs() {
    static std::atomic<size_t> jobs{0};
    return jobs;
}

class ParallelProofScope {
public:
    ParallelProofScope() {
        active_proof_jobs().fetch_add(1, std::memory_order_acq_rel);
    }

    ~ParallelProofScope() {
        active_proof_jobs().fetch_sub(1, std::memory_order_acq_rel);
    }

    ParallelProofScope(const ParallelProofScope&) = delete;
    ParallelProofScope& operator=(const ParallelProofScope&) = delete;
};

inline size_t acquire_worker_tokens(size_t requested) {
    auto& available = available_worker_tokens();
    size_t current = available.load(std::memory_order_relaxed);
    while (current != 0) {
        const size_t acquired = std::min(current, requested);
        if (available.compare_exchange_weak(
                current,
                current - acquired,
                std::memory_order_acquire,
                std::memory_order_relaxed))
            return acquired;
    }
    return 0;
}

inline void release_worker_tokens(size_t count) {
    available_worker_tokens().fetch_add(count, std::memory_order_release);
}

template <typename Function>
void parallel_for(
    size_t begin,
    size_t end,
    size_t minimum_grain,
    Function&& function
) {
    const size_t count = end > begin ? end - begin : 0;
    if (count == 0) return;

    const size_t jobs = std::max<size_t>(
        1,
        active_proof_jobs().load(std::memory_order_acquire)
    );
    const size_t max_workers = std::max<size_t>(
        1,
        configured_worker_count() / jobs
    );
    if (max_workers <= 1 || count <= minimum_grain) {
        for (size_t i = begin; i < end; ++i) function(i);
        return;
    }

    const size_t desired_workers = std::min(
        max_workers,
        (count + minimum_grain - 1) / minimum_grain
    );
    const size_t acquired = acquire_worker_tokens(desired_workers - 1);
    const size_t workers = acquired + 1;
    if (workers == 1) {
        for (size_t i = begin; i < end; ++i) function(i);
        return;
    }

    const size_t target_chunks = std::max<size_t>(workers * 8, 1);
    const size_t balanced_chunk =
        (count + target_chunks - 1) / target_chunks;
    const size_t chunk_size = std::max<size_t>(
        minimum_grain,
        balanced_chunk
    );
    std::atomic<size_t> next{begin};
    std::exception_ptr error;
    std::mutex error_mutex;
    std::vector<std::thread> threads;
    threads.reserve(acquired);

    const auto run = [&]() {
        try {
            while (true) {
                const size_t chunk_begin =
                    next.fetch_add(chunk_size, std::memory_order_relaxed);
                if (chunk_begin >= end) break;
                const size_t chunk_end = std::min(end, chunk_begin + chunk_size);
                for (size_t index = chunk_begin; index < chunk_end; ++index)
                    function(index);
            }
        } catch (...) {
            std::lock_guard<std::mutex> lock(error_mutex);
            if (!error) error = std::current_exception();
            next.store(end, std::memory_order_relaxed);
        }
    };

    try {
        for (size_t i = 0; i < acquired; ++i)
            threads.emplace_back(run);
        run();
        for (auto& thread : threads) thread.join();
    } catch (...) {
        for (auto& thread : threads) {
            if (thread.joinable()) thread.join();
        }
        release_worker_tokens(acquired);
        throw;
    }

    release_worker_tokens(acquired);
    if (error) std::rethrow_exception(error);
}

}  // namespace detail
}  // namespace pvac
