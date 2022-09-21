// Copyright 2022 Google LLC

#include "GCApi.hpp"

#include "ConcurrentMarkAndSweep.hpp"
#include "CustomLogging.hpp"
#include "KAssert.h"
#include "ObjectFactory.hpp"

namespace kotlin {
namespace alloc {

bool TryResetMark(void* ptr) noexcept {
    using Node = typename
        kotlin::mm::ObjectFactory<kotlin::gc::ConcurrentMarkAndSweep>::Storage::Node;
    using NodeRef = typename
        kotlin::mm::ObjectFactory<kotlin::gc::ConcurrentMarkAndSweep>::NodeRef;
    ptr = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(ptr));
    Node& node = Node::FromData(ptr);
    NodeRef ref = NodeRef(node);
    auto& objectData = ref.ObjectData();
    if (!objectData.tryResetMark()) {
        auto* objHeader = ref.GetObjHeader();
        if (HasFinalizers(objHeader)) {
            CustomWarning("FINALIZER IGNORED");
        }
        return false;
    }
    return true;
}

void* alloc(uint64_t size) noexcept {
    void* memory;
    if (size > std::numeric_limits<size_t>::max() || !(memory = malloc(size))) {
        konan::consoleErrorf("Out of memory trying to allocate %" PRIu64
                "bytes. Aborting.\n", size);
        konan::abort();
    }
    return memory;
}

} // namespace alloc
} // namespace kotlin
