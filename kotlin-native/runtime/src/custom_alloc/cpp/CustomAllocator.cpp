// Copyright 2022 Google LLC

#include "CustomAllocator.hpp"

#include <atomic>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "CustomLogging.hpp"
#include "KAssert.h"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "SmallPage.hpp"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"

namespace kotlin {
namespace alloc {

thread_local ThreadData tld;

ThreadData& GetThreadData() noexcept {
    return tld;
}

CustomAllocator CustomAllocator::instance_ [[clang::no_destroy]];

void* CustomAllocator::Allocate(uint32_t cellCount) noexcept {
    CustomDebug("CustomAllocator::Allocate(%u)", cellCount);
    if (cellCount <= SMALL_PAGE_MAX_BLOCK_SIZE) {
        return AllocateInSmallPage(cellCount);
    }
    if (cellCount > LARGE_PAGE_SIZE_THRESHOLD) {
        return AllocateInLargePage(cellCount);
    }
    return AllocateInMediumPage(cellCount);
}

void* CustomAllocator::AllocateInLargePage(uint32_t cellCount) noexcept {
    CustomInfo("CustomAllocator::AllocateInLargePage(%u)", cellCount);
    void* block = heap_.GetLargePage(cellCount)->Data();
    return block;
}

void* CustomAllocator::AllocateInMediumPage(uint32_t cellCount) noexcept {
    CustomInfo("CustomAllocator::AllocateInMediumPage(%u)", cellCount);
    // +1 accounts for header, since cell->size also includes header cell
    ++cellCount;
    if (tld.mediumPage) {
        Cell* block = tld.mediumPage->TryAllocate(cellCount);
        if (block) return block->Data();
    }
    CustomDebug("Failed to allocate in curPage");
    while (true) {
        tld.mediumPage = heap_.GetMediumPage(cellCount);
        Cell* block = tld.mediumPage->TryAllocate(cellCount);
        if (block) return block->Data();
    }
}

void* CustomAllocator::AllocateInSmallPage(uint32_t cellCount) noexcept {
    CustomInfo("CustomAllocator::AllocateInSmallPage(%u)", cellCount);
    SmallPage* page = tld.smallPages[cellCount];
    if (page) {
        SmallCell* block = page->TryAllocate();
        if (block) return block->Data();
    }
    CustomDebug("Failed to allocate in current SmallPage");
    while ((page = heap_.GetSmallPage(cellCount))) {
        SmallCell* block = page->TryAllocate();
        if (block) {
            tld.smallPages[cellCount] = page;
            return block->Data();
        }
    }
    return nullptr;
}

} // namespace alloc
} // namespace kotlin
