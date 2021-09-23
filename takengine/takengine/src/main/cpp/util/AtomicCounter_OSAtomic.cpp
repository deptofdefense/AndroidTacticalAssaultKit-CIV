
#include <libkern/OSAtomic.h>
#include "util/AtomicCounter.h"

using namespace atakmap::util;

int32_t AtomicCounter::add(int32_t amount) {
    return OSAtomicAdd32Barrier(amount, &this->value);
}

int32_t AtomicCounter::currentValue() const {
    return this->value;
}