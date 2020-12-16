#include <algorithm>

#include "util/FutureTask.h"

#include "thread/Lock.h"

using namespace atakmap::util;

using namespace TAK::Engine::Thread;

SharedState::SharedState() : state_(Initial) {}

SharedState::SharedState(int state) : state_(state) {}

SharedState::~SharedState() NOTHROWS
{ }

int SharedState::getState() const {
    Lock lock(mutex_);
    return this->state_;
}

bool SharedState::setState(int state) {
    Lock lock(mutex_);
    bool result;
    if ((result = this->supportsStateNoSync(state)), result) {
        this->state_ = state;
        change_cv_.broadcast(lock);
    }
    return result;
}

bool SharedState::supportsState(int state) const {
    Lock lock(mutex_);
    return this->supportsStateNoSync(state);
}

bool SharedState::supportsStateNoSync(int state) const {
    switch (this->state_) {
        case Initial: return state == Processing || state == Canceled;
        case Processing: return state != Initial && state != Processing;
    }
    return false;
}

int SharedState::awaitAny(const int *states, int count) {
    const int *end = states + count;
    const int *result;
    {
        Lock lock(mutex_);
        while ((result = std::find(states, end, this->state_)) == end) {
            change_cv_.wait(lock);
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
