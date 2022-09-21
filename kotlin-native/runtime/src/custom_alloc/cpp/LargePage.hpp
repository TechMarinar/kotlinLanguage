// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_
#define CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_

#include <atomic>

#include "AtomicStack.hpp"
#include "CustomLogging.hpp"
#include "GCApi.hpp"
#include "MediumPage.hpp"

namespace kotlin {
namespace alloc {

#define LARGE_PAGE_SIZE_THRESHOLD (MEDIUM_PAGE_CELL_COUNT-1)

class alignas(8) LargePage {
public:
    static LargePage* Create(uint64_t cellCount) noexcept {
        CustomInfo("LargePage::Create(%" PRIu64 ")", cellCount);
        RuntimeAssert(cellCount > LARGE_PAGE_SIZE_THRESHOLD, "blockSize too small for large page");
        uint64_t size = sizeof(LargePage) + cellCount * sizeof(uint64_t);
        return new (alloc(size)) LargePage();
    }

    void* Data() noexcept { return this + 1; }

    bool Sweep() noexcept {
        CustomDebug("LargePage@%p::Sweep()", this);
        return TryResetMark(Data());
    }

private:
    friend class AtomicStack<LargePage>;
    LargePage* next_;
};

} // namespace alloc
} // namespace kotlin

#endif
