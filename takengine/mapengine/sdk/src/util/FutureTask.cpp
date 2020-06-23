#include <algorithm>

#include "util/FutureTask.h"

#include "thread/Lock.h"

using namespace atakmap::util;

using namespace TAK::Engine::Thread;

SharedState::SharedState()
: state(Initial) { }

SharedState::SharedState(int state)
: state(state) { }

SharedState::~SharedState() throw()
{ }

int SharedState::getState() const {
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mutex);
    return this->state;
}

bool SharedState::setState(int state) {
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mutex);
    bool result;
    if ((result = this->supportsStateNoSync(state))) {
        this->state = state;
        changeCV.broadcast(*lock);
    }
    return result;
}

bool SharedState::supportsState(int state) const {
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mutex);
    return this->supportsStateNoSync(state);
}

bool SharedState::supportsStateNoSync(int state) const {
    switch (this->state) {
        case Initial: return state == Processing || state == Canceled;
        case Processing: return state != Initial && state != Processing;
    }
    return false;
}

int SharedState::awaitAny(const int *states, int count) {
    const int *end = states + count;
    const int *result;
    {
        LockPtr lock(NULL, NULL);
        Lock_create(lock, mutex);
        while ((result = std::find(states, end, this->state)) == end) {
            changeCV.wait(*lock);
        }
    }
    return *result;
}

CancelationToken::CancelationToken()
{ }

CancelationToken::CancelationToken(const std::shared_ptr<SharedState> &sharedState)
: sharedState(sharedState) { }

bool CancelationToken::isCanceled() const {
    return sharedState.get() &&
           sharedState->getState() == SharedState::Canceled;
}

bool CancelationToken::isCancelSupported() const {
    return sharedState.get() &&
           sharedState->supportsState(SharedState::Canceled);
}

FutureError::FutureError()
: message("future error: unspecified") { }

FutureError::FutureError(const char *message)
: message("future error: ") {
    this->message.append(message);
}

FutureError::~FutureError() { }

const char *FutureError::what() const NOTHROWS {
    return this->message.c_str();
}
