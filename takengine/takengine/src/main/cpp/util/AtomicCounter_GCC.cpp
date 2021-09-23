#include "util/AtomicCounter.h"

using namespace atakmap::util;

int32_t AtomicCounter::add(int32_t amount) {
    return std::atomic_fetch_add(&this->value, amount);
}

int32_t AtomicCounter::currentValue() const {
    return std::atomic_load(&this->value);
}
