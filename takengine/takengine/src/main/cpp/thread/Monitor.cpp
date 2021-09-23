#include "thread/Monitor.h"

#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Util;

Monitor::Monitor(const MutexType type) NOTHROWS :
    mutex(type)
{}

Monitor::~Monitor() NOTHROWS
{}

Monitor::Lock::Lock(Monitor &owner_) NOTHROWS :
    owner(owner_),
    impl(owner.mutex),
    status(impl.status)
{}

Monitor::Lock::~Lock() NOTHROWS
{}

TAKErr Monitor::Lock::wait(const int64_t millis) NOTHROWS
{
    return this->owner.cond.wait(this->impl, millis);
}

TAKErr Monitor::Lock::signal() NOTHROWS
{
    return this->owner.cond.signal(this->impl);
}

TAKErr Monitor::Lock::broadcast() NOTHROWS
{
    return this->owner.cond.broadcast(this->impl);
}

TAKErr TAK::Engine::Thread::MonitorLock_create(MonitorLockPtr &value, Monitor &monitor) NOTHROWS
{
    value = MonitorLockPtr(new(std::nothrow) Monitor::Lock(monitor), Memory_deleter_const<Monitor::Lock>);
    if (!value.get())
        return TE_OutOfMemory;
    TE_CHECKRETURN_CODE(value->status);

    return TE_Ok;
}
