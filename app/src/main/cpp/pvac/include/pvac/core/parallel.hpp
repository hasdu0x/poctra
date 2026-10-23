#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdlib>
#include <condition_variable>
#include <deque>
#include <exception>
#include <functional>
#include <memory>
#include <mutex>
#include <stdexcept>
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

class ReusableWorkerPool {
public:
    ReusableWorkerPool() {
        const size_t count =
            configured_worker_count() > 1 ? configured_worker_count() - 1 : 0;
        workers_.reserve(count);
        for (size_t i = 0; i < count; ++i)
            workers_.emplace_back([this]() { worker_loop(); });
    }

    ~ReusableWorkerPool() {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            stopping_ = true;
        }
        ready_.notify_all();
        for (auto& worker : workers_) {
            if (worker.joinable()) worker.join();
        }
    }

    ReusableWorkerPool(const ReusableWorkerPool&) = delete;
    ReusableWorkerPool& operator=(const ReusableWorkerPool&) = delete;

    void submit(std::function<void()> task) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopping_)
                throw std::runtime_error("pvac: worker pool is stopping");
            tasks_.push_back(std::move(task));
        }
        ready_.notify_one();
    }

private:
    void worker_loop() {
        while (true) {
            std::function<void()> task;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                ready_.wait(lock, [this]() {
                    return stopping_ || !tasks_.empty();
                });
                if (stopping_ && tasks_.empty()) return;
                task = std::move(tasks_.front());
                tasks_.pop_front();
            }
            task();
        }
    }

    std::mutex mutex_;
    std::condition_variable ready_;
    std::deque<std::function<void()>> tasks_;
    std::vector<std::thread> workers_;
    bool stopping_ = false;
};

inline ReusableWorkerPool& reusable_worker_pool() {
    static ReusableWorkerPool pool;
    return pool;
}

struct ParallelCompletion {
    explicit ParallelCompletion(size_t count) : remaining(count) {}

    void complete() {
        if (remaining.fetch_sub(1, std::memory_order_acq_rel) == 1) {
            std::lock_guard<std::mutex> lock(mutex);
            done.notify_one();
        }
    }

    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        done.wait(lock, [this]() {
            return remaining.load(std::memory_order_acquire) == 0;
        });
    }

    std::atomic<size_t> remaining;
    std::mutex mutex;
    std::condition_variable done;
};

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
#if defined(PVAC_EPHEMERAL_THREADS)
    std::vector<std::thread> threads;
    threads.reserve(acquired);
#else
    auto completion = std::make_shared<ParallelCompletion>(acquired);
    size_t submitted = 0;
#endif

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
#if defined(PVAC_EPHEMERAL_THREADS)
        for (size_t i = 0; i < acquired; ++i)
            threads.emplace_back(run);
#else
        for (size_t i = 0; i < acquired; ++i) {
            reusable_worker_pool().submit([run, completion]() {
                run();
                completion->complete();
            });
            ++submitted;
        }
#endif
        run();
#if defined(PVAC_EPHEMERAL_THREADS)
        for (auto& thread : threads) thread.join();
#else
        while (submitted < acquired) {
            completion->complete();
            ++submitted;
        }
        completion->wait();
#endif
    } catch (...) {
#if defined(PVAC_EPHEMERAL_THREADS)
        for (auto& thread : threads) {
            if (thread.joinable()) thread.join();
        }
#else
        completion->wait();
#endif
        release_worker_tokens(acquired);
        throw;
    }

    release_worker_tokens(acquired);
    if (error) std::rethrow_exception(error);
}

}  // namespace detail
}  // namespace pvac
