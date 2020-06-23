#include "Monitor.h"

using namespace PGSC::Thread;

using namespace PGSC::Util;

Monitor::Monitor(const MutexType type) PGSCT_NOTHROWS :
    mutex(type)
{}

Monitor::~Monitor() PGSCT_NOTHROWS
{}

Monitor::Lock::Lock(Monitor &owner_, LockPtr &&impl_) PGSCT_NOTHROWS :
    owner(owner_),
    impl(std::move(impl_))
{}

Monitor::Lock::~Lock() PGSCT_NOTHROWS
{}

ThreadErr Monitor::Lock::wait(const int64_t millis) PGSCT_NOTHROWS
{
    return this->owner.cond.wait(*this->impl, millis);
}

ThreadErr Monitor::Lock::signal() PGSCT_NOTHROWS
{
    return this->owner.cond.signal(*this->impl);
}

ThreadErr Monitor::Lock::broadcast() PGSCT_NOTHROWS
{
    return this->owner.cond.broadcast(*this->impl);
}

PGSCTHREAD_API ThreadErr PGSC::Thread::MonitorLock_create(MonitorLockPtr &value, Monitor &monitor) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    LockPtr impl(NULL, NULL);
    code = Lock_create(impl, monitor.mutex);
    THREAD_CHECKRETURN_CODE(code);

    value = MonitorLockPtr(new Monitor::Lock(monitor, std::move(impl)), Memory_deleter_const<Monitor::Lock>);
    return code;
}
