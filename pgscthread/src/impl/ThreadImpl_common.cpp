#include "Thread.h"
#include "CondImpl.h"
#include "LockImpl.h"
#include "MutexImpl.h"
#include "ThreadImpl.h"

#include "Monitor.h"

using namespace PGSC::Thread;
using namespace PGSC::Thread::Impl;

using namespace PGSC::Util;

/*****************************************************************************/
// CondVar definitions

CondVarImpl::CondVarImpl() PGSCT_NOTHROWS
{}

CondVarImpl::~CondVarImpl() PGSCT_NOTHROWS
{}

/*****************************************************************************/
// Lock definitions

LockImpl::LockImpl() PGSCT_NOTHROWS
{}

LockImpl::~LockImpl() PGSCT_NOTHROWS
{}

/*****************************************************************************/
// MutexImpl definitions

MutexImpl::MutexImpl() PGSCT_NOTHROWS
{}

MutexImpl::~MutexImpl() PGSCT_NOTHROWS
{}

/*****************************************************************************/
// Thread definitions

ThreadID::ThreadID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &&impl_) PGSCT_NOTHROWS :
    impl(std::move(impl_))
{}

ThreadID::ThreadID() PGSCT_NOTHROWS :
    impl(NULL, NULL)
{}

ThreadID::ThreadID(const ThreadID &other) PGSCT_NOTHROWS :
    impl(NULL, NULL)
{
    if (other.impl.get())
        other.impl->clone(impl);
}
    
ThreadID::~ThreadID() PGSCT_NOTHROWS
{}

bool ThreadID::operator==(const ThreadID &other) const PGSCT_NOTHROWS
{
    return (this->impl.get() && other.impl.get() && (*this->impl == *other.impl));
}

bool ThreadID::operator!=(const ThreadID &other) const PGSCT_NOTHROWS
{
    return !(*this == other);
}

ThreadID &ThreadID::operator=(const ThreadID &other) PGSCT_NOTHROWS
{
    if (!other.impl.get())
        this->impl.reset();
    else
        other.impl->clone(this->impl);
    return *this;
}

Thread::Thread() PGSCT_NOTHROWS
{}

Thread::~Thread() PGSCT_NOTHROWS
{}

ThreadID Thread::getID() const PGSCT_NOTHROWS
{
    ThreadIDImplPtr id(NULL, NULL);
    this->getID(id);
    return ThreadID(std::move(id));
}

ThreadCreateParams::ThreadCreateParams() PGSCT_NOTHROWS :
    name(), priority(TETP_Normal)
{}

ThreadIDImpl::ThreadIDImpl() PGSCT_NOTHROWS
{}

ThreadIDImpl::~ThreadIDImpl() PGSCT_NOTHROWS
{}

ThreadErr PGSC::Thread::Thread_sleep(const int64_t milliseconds) PGSCT_NOTHROWS
{
    if (milliseconds <= 0)
        return Thread_Ok;
    ThreadErr code(Thread_Ok);
    Monitor monitor;
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);
    code = lock->wait(milliseconds);
    if (code == Thread_TimedOut) // we are expecting timeout
        code = Thread_Ok;
    THREAD_CHECKRETURN_CODE(code);
    return code;
}
