// Copyright 2022 Google LLC

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "SmallPage.hpp"

namespace {

using SmallPage = typename kotlin::alloc::SmallPage;

TEST(CustomAllocatorTest, SmallPageAlloc) {
    uint8_t mem[SMALL_PAGE_SIZE] = {};
    SmallPage* page = new (&mem) SmallPage(1);
    void* p = page->TryAllocate();
    EXPECT_TRUE(p == page + 1);
}

TEST(CustomAllocatorTest, SmallPageConsequtive) {
    uint8_t mem[SMALL_PAGE_SIZE] = {};
    SmallPage* page = new (&mem) SmallPage(5);
    uint64_t* p1 = reinterpret_cast<uint64_t*>(page->TryAllocate());
    uint64_t* p2 = reinterpret_cast<uint64_t*>(page->TryAllocate());
    EXPECT_TRUE(p2 - p1 == 5);
} //
}
