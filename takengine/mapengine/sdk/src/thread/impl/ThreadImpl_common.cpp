#include "thread/Thread.h"
#include "thread/impl/CondImpl.h"
#include "thread/impl/LockImpl.h"
#include "thread/impl/MutexImpl.h"
#include "thread/impl/ThreadImpl.h"

#include "thread/Monitor.h"

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Thread::Impl;

using namespace TAK::Engine::Util;

/*****************************************************************************/
// CondVar definitions

CondVarImpl::CondVarImpl() NOTHROWS
{}

CondVarImpl::~CondVarImpl() NOTHROWS
{}

/*****************************************************************************/
// Lock definitions

LockImpl::LockImpl() NOTHROWS
{}

LockImpl::~LockImpl() NOTHROWS
{}

/*****************************************************************************/
// MutexImpl definitions

MutexImpl::MutexImpl() NOTHROWS
{}

MutexImpl::~MutexImpl() NOTHROWS
{}

/*****************************************************************************/
// Thread definitions

ThreadID::ThreadID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &&impl_) NOTHROWS :
    impl(std::move(impl_))
{}

ThreadID::ThreadID() NOTHROWS :
    impl(NULL, NULL)
{}

ThreadID::ThreadID(const ThreadID &other) NOTHROWS :
    impl(NULL, NULL)
{
    if (other.impl.get())
        other.impl->clone(impl);
}
    
ThreadID::~ThreadID() NOTHROWS
{}

bool ThreadID::operator==(const ThreadID &other) const NOTHROWS
{
    return (this->impl.get() && other.impl.get() && (*this->impl == *other.impl));
}

bool ThreadID::operator!=(const ThreadID &other) const NOTHROWS
{
    return !(*this == other);
}

ThreadID &ThreadID::operator=(const ThreadID &other) NOTHROWS
{
    if (!other.impl.get())
        this->impl.reset();
    else
        other.impl->clone(this->impl);
    return *this;
}

Thread::Thread() NOTHROWS
{}

Thread::~Thread() NOTHROWS
{}

ThreadID Thread::getID() const NOTHROWS
{
    ThreadIDImplPtr id(NULL, NULL);
    this->getID(id);
    return ThreadID(std::move(id));
}

ThreadCreateParams::ThreadCreateParams() NOTHROWS :
    priority(TETP_Normal)
{}

ThreadIDImpl::ThreadIDImpl() NOTHROWS
{}

ThreadIDImpl::~ThreadIDImpl() NOTHROWS
{}

TAKErr TAK::Engine::Thread::Thread_sleep(const int64_t milliseconds) NOTHROWS
{
    if (milliseconds <= 0)
        return TE_Ok;
    TAKErr code(TE_Ok);
    Monitor monitor;
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    TE_CHECKRETURN_CODE(code);
    code = lock->wait(milliseconds);
    if (code == TE_TimedOut) // we are expecting timeout
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);
    return code;
}
