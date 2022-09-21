// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_
#define CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_

#include <atomic>

#include "AtomicStack.hpp"
#include "Cell.hpp"
#include "ConcurrentMarkAndSweep.hpp"
#include "GCApi.hpp"
#include "KAssert.h"
#include "ObjectFactory.hpp"

namespace kotlin {
namespace alloc {

#define KiB 1024
#define MEDIUM_PAGE_SIZE (256*KiB)
#define MEDIUM_PAGE_CELL_COUNT ((MEDIUM_PAGE_SIZE - sizeof(MediumPage)) / sizeof(Cell))

class alignas(8) MediumPage {
public:
    class Iterator {
        public:
            Cell& operator*() noexcept { return *cell_; }
            Cell* operator->() noexcept { return cell_; }

            Iterator& operator++() noexcept {
                cell_ = cell_->Next();
                return *this;
            }

            bool operator==(const Iterator& rhs) const noexcept { return cell_ == rhs.cell_; }
            bool operator!=(const Iterator& rhs) const noexcept { return cell_ != rhs.cell_; }

        private:
            friend class MediumPage;
            explicit Iterator(Cell* cell) noexcept : cell_(cell) {}

            Cell* cell_;
    };

    Iterator begin() noexcept { return Iterator(cells_); }
    Iterator end() noexcept { return Iterator(nullptr); }

    MediumPage(uint32_t cellCount) noexcept : curBlock_(cells_), kZeroBlock_(0) {
        cells_[0] = Cell(cellCount);
    }

    static MediumPage* Create(uint32_t cellCount) noexcept {
        CustomInfo("MediumPage::Create(%u)", cellCount);
        RuntimeAssert(cellCount <= MEDIUM_PAGE_CELL_COUNT, "cellCount is too large for medium page");
        return new (alloc(MEDIUM_PAGE_SIZE)) MediumPage(cellCount);
    }

    // Tries to allocate in current page, returns null if no free block in page is big enough
    Cell* TryAllocate(uint32_t cellsNeeded) noexcept;

    bool Sweep() noexcept;

private:
    // Coalesces adjecent unallocated blocks and sets cur_block to the largest one.
    void UpdateCurBlock(uint32_t cellsNeeded) noexcept;

    friend class AtomicStack<MediumPage>;
    MediumPage* next_;

    Cell* curBlock_;
    Cell kZeroBlock_; // simplifies code to have a dummy empty cell in the same address neighborhood
    Cell cells_[];
};
} // namespace alloc
} // namespace kotlin

#endif
