// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_CELL_HPP_
#define CUSTOM_ALLOC_CPP_CELL_HPP_

#include <cstdint>
#include <cstring>

namespace kotlin {
namespace alloc {

// All allocations are whole units of cells.
class Cell {
public:
    Cell(uint32_t size) noexcept;

    // Allocate `cellsNeeded` blocks at the end of this block, possibly the
    // whole block, or null if it doesn't fit.
    Cell* TryAllocate(uint32_t cellsNeeded) noexcept;

    // Marks block as no longer allocated.
    void Deallocate() noexcept;

    // Returns the address for the client memory payload.
    Cell* Data() noexcept { return this + 1; }

    // Reverse of Data(); returns the header of a given payload address.
    Cell* Header() noexcept { return this - 1; }

    // The next block.
    Cell* Next() noexcept { return this + size_; }

private:
    friend class MediumPage;

    uint32_t isAllocated_;
    uint32_t size_;
};

static_assert(sizeof(Cell) == 8, "Cell size is wrong");

} // namespace alloc
} // namespace kotlin

#endif
