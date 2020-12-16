
#include <new>

#include "util/SyncObject.h"

using namespace atakmap::util;

using namespace TAK::Engine::Thread;

SyncObject::SyncObject()
: mutex(TEMT_Recursive), lock(nullptr) { }

SyncObject::~SyncObject() { }

void SyncObject::wait() {
    cv.wait(*lock);
}

void SyncObject::notify() {
    cv.signal(*lock);
}

void SyncObject::notifyAll() {
    cv.broadcast(*lock);
}