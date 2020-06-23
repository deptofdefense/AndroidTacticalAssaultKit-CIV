
#include "util/AtomicRefCountable.h"

using namespace atakmap::util;

AtomicRefCountable::~AtomicRefCountable() { }

void AtomicRefCountable::incRef() {
    this->counter.add(1);
}

void AtomicRefCountable::decRef() {
    if (this->counter.add(-1) == 0) {
        delete this;
    }
}