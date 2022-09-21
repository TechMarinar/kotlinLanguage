// Copyright 2022 Google LLC

#include "Heap.hpp"

#include <atomic>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "CustomAllocator.hpp"
#include "CustomLogging.hpp"
#include "KAssert.h"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "SmallPage.hpp"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"

namespace kotlin {
namespace alloc {
Heap Heap::instance_ [[clang::no_destroy]];

void Heap::PrepareForGC() noexcept {
    CustomDebug("Heap::PrepareForGC()");
    for (auto& thread : kotlin::mm::ThreadRegistry::Instance().LockForIter()) {
        thread.alloc().ReleasePages();
    }

    mediumPages_.PrepareForGC();
    largePages_.PrepareForGC();
    for (int blockSize = 0 ; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE ; ++blockSize) {
        smallPages_[blockSize].PrepareForGC();
    }
}

void Heap::Sweep() noexcept {
    CustomDebug("Heap::Sweep()");
    for (int blockSize = 0 ; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE ; ++blockSize) {
        smallPages_[blockSize].Sweep();
    }
    mediumPages_.Sweep();
    largePages_.Sweep();
}

MediumPage* Heap::GetMediumPage(uint32_t cellCount) noexcept {
    CustomDebug("Heap::GetMediumPage()");
    return mediumPages_.GetPage(cellCount);
}

SmallPage* Heap::GetSmallPage(uint32_t cellCount) noexcept {
    CustomDebug("Heap::GetSmallPage()");
    return smallPages_[cellCount].GetPage(cellCount);
}

LargePage* Heap::GetLargePage(uint32_t cellCount) noexcept {
    CustomInfo("CustomAllocator::AllocateInLargePage(%u)", cellCount);
    return largePages_.NewPage(cellCount);
}
} // namespace alloc
} // namespace kotlin
