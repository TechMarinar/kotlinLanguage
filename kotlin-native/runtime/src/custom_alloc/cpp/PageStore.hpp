// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_PAGESTORE_HPP_
#define CUSTOM_ALLOC_CPP_PAGESTORE_HPP_

#include <atomic>

#include "AtomicStack.hpp"

namespace kotlin {
namespace alloc {

template<class T>
class PageStore {
public:
    void PrepareForGC() noexcept {
        unswept_.NonatomicTransferAllFrom(ready_);
        unswept_.NonatomicTransferAllFrom(used_);
    }

    T* SweepAndFreeEmpty(AtomicStack<T> &from, AtomicStack<T> &to) noexcept {
        T* page;
        while ((page = from.Pop())) {
            if (!page->Sweep()) {
                CustomInfo("SweepAndFreeEmpty free(%p)", page);
                free(page);
            } else {
                to.Push(page);
                return page;
            }
        }
        return nullptr;
    }

    void Sweep() noexcept {
        while (SweepAndFreeEmpty(unswept_, ready_));
    }

    T* GetPage(uint32_t cellCount) noexcept {
        T* page;
        if ((page = SweepAndFreeEmpty(unswept_, used_))) {
            return page;
        }
        if ((page = ready_.Pop())) {
            used_.Push(page);
            return page;
        }
        return NewPage(cellCount);
    }

    T* NewPage(uint32_t cellCount) noexcept {
        T* page = T::Create(cellCount);
        used_.Push(page);
        return page;
    }

private:
    AtomicStack<T> ready_;
    AtomicStack<T> used_;
    AtomicStack<T> unswept_;
};

} // namespace alloc
} // namespace kotlin

#endif
