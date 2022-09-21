// Copyright 2022 Google LLC

#include "Cell.hpp"
#include "CustomAllocator.hpp"
#include "CustomLogging.hpp"
#include "KAssert.h"

namespace kotlin {

void initObjectPool() noexcept {
    CustomInfo("initObjectPool()");
}

void* allocateInObjectPool(size_t size) noexcept {
    void* ret = alloc::CustomAllocator::Instance().Alloc(size);
    CustomDebug("allocateInObjectPool(%lu) = %p", size, ret);
    return ret;
}

void freeInObjectPool(void* ptr) noexcept {
    CustomWarning("free (%p) not supported", ptr);
}

void compactObjectPoolInMainThread() noexcept {}
void compactObjectPoolInCurrentThread() noexcept {}

} // namespace kotlin
