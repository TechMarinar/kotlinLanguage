// Copyright 2022 Google LLC

#include "SmallPage.hpp"

#include <atomic>
#include <cstring>
#include <random>

#include "CustomLogging.hpp"
#include "GCApi.hpp"

namespace kotlin {
namespace alloc {

SmallPage::SmallPage(uint32_t blockSize) noexcept : blockSize_(blockSize) {
    CustomInfo("SmallPage(%p)::SmallPage(%u)", this, blockSize);
    free_.next = 0;
    uint64_t end = SMALL_PAGE_CELL_COUNT + 1 - blockSize_;
    for (uint64_t i = 0 ; i < end ; i += blockSize) {
        cells_[i].next = i + blockSize;
    }
}

SmallCell* SmallPage::TryAllocate() noexcept {
    if (free_.next + blockSize_ > SMALL_PAGE_CELL_COUNT) {
        return nullptr;
    }
    SmallCell* freeBlock = cells_ + free_.next;
    free_.next = freeBlock->next;
    CustomDebug("SmallPage(%p){%u}::TryAllocate() = %p", this, blockSize_, freeBlock);
    return freeBlock;
}

bool SmallPage::Sweep() noexcept {
    CustomInfo("SmallPage(%p)::Sweep()", this);
    // `end` is after the last legal allocation of a block, but does not
    // necessarily match an actual block starting point.
    uint64_t end = SMALL_PAGE_CELL_COUNT + 1 - blockSize_;
    bool alive = false;
    uint64_t block = 0;
    uint64_t* nextFree = &free_.next;
    while (block < end) {
        while (block != *nextFree) {
            SmallCell* cell = cells_ + block;
            if (!TryResetMark(cells_ + block)) {
                cell->next = *nextFree;
                *nextFree = block;
                nextFree = &cell->next;
            } else {
                alive = true;
            }
            block += blockSize_;
        }
        if (block >= end) break;
        nextFree = &cells_[block].next;
        block += blockSize_;
    }
    return alive;
}

} // namespace alloc
} // namespace kotlin
