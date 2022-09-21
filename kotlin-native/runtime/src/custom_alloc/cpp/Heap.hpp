// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_HEAP_HPP_
#define CUSTOM_ALLOC_CPP_HEAP_HPP_

#include <atomic>
#include <cstring>

#include "AtomicStack.hpp"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "PageStore.hpp"
#include "SmallPage.hpp"

namespace kotlin {
namespace alloc {

class Heap {
public:
    static Heap& Instance() noexcept { return instance_; }

    // Called once by the GC thread after all mutators have been suspended
    void PrepareForGC() noexcept;

    // Sweep through all remaining pages, freeing those blocks where CanReclaim
    // returns true. If multiple sweepers are active, each page will only be
    // seen by one sweeper.
    void Sweep() noexcept;

    SmallPage* GetSmallPage(uint32_t cellCount) noexcept;
    MediumPage* GetMediumPage(uint32_t cellCount) noexcept;
    LargePage* GetLargePage(uint32_t cellCount) noexcept;

private:
    static Heap instance_;

    PageStore<SmallPage> smallPages_[SMALL_PAGE_MAX_BLOCK_SIZE+1];
    PageStore<MediumPage> mediumPages_;
    PageStore<LargePage> largePages_;
};

} // namespace alloc
} // namespace kotlin

#endif
