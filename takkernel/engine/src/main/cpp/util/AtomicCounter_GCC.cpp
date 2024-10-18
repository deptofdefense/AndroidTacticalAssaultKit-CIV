#include "util/AtomicCounter.h"

using namespace atakmap::util;

int32_t AtomicCounter::add(int32_t amount) {
    return this->value.fetch_add(amount);
}

int32_t AtomicCounter::currentValue() const {
    return this->value.load();
}
