// Copyright 2022 Google LLC

#include "Cell.hpp"

#include <cstring>

#include "CustomLogging.hpp"
#include "KAssert.h"

namespace kotlin {
namespace alloc {

Cell::Cell(uint32_t size) noexcept : isAllocated_(false), size_(size) {
    CustomDebug("Cell@%p::Cell(%u)", this, size);
}

Cell* Cell::TryAllocate(uint32_t cellsNeeded) noexcept {
    CustomDebug("Cell@%p{ allocated = %d, size = %u }::TryAllocate(%u)",
            this, isAllocated_, size_, cellsNeeded);
    if (isAllocated_ || cellsNeeded > size_) {
        CustomDebug("Failed to allocate in Cell");
        return nullptr;
    }
    uint32_t oldSize = size_;
    uint32_t remainingSize = size_ - cellsNeeded;
    Cell* newBlock = this + remainingSize;
    size_ = remainingSize;
    newBlock->isAllocated_ = true;
    newBlock->size_ = cellsNeeded;
    RuntimeAssert(remainingSize == 0 || size_ + newBlock->size_ == oldSize,
            "sizes don't add up");
    return newBlock;
}

void Cell::Deallocate() noexcept {
    CustomDebug("Cell@%p{ allocated = %d, size = %u }::Deallocate()",
            this, isAllocated_, size_);
    RuntimeAssert(isAllocated_, "Cell is not currently allocated");
    isAllocated_ = false;
}

} // namespace alloc
} // namespace kotlin
