// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_SMALLPAGE_HPP_
#define CUSTOM_ALLOC_CPP_SMALLPAGE_HPP_

#include <atomic>

#include "AtomicStack.hpp"
#include "GCApi.hpp"

namespace kotlin {
namespace alloc {

#define KiB 1024
#define SMALL_PAGE_SIZE (256*KiB)
#define SMALL_PAGE_MAX_BLOCK_SIZE 128
#define SMALL_PAGE_CELL_COUNT ((SMALL_PAGE_SIZE-sizeof(SmallPage))/sizeof(SmallCell))

struct SmallCell {
    void* Data() { return this; }

    uint64_t next;
};

class alignas(8) SmallPage {
public:
    SmallPage(uint32_t blockSize) noexcept;

    static SmallPage* Create(uint32_t blockSize) noexcept {
        CustomInfo("SmallPage::Create(%u)", blockSize);
        RuntimeAssert(blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE, "blockSize too large for small page");
        return new (alloc(SMALL_PAGE_SIZE)) SmallPage(blockSize);
    }

    // Tries to allocate in current page, returns null if no free block in page
    SmallCell* TryAllocate() noexcept;

    bool Sweep() noexcept;

private:
    friend class AtomicStack<SmallPage>;

    // Used for linking pages together in `pages` queue or in `unswept` queue.
    SmallPage* next_;
    uint32_t blockSize_;
    SmallCell free_;
    SmallCell cells_[];
};

static_assert(sizeof(SmallPage) % 8 == 0, "Page header size is not aligned");

} // namespace alloc
} // namespace kotlin

#endif
