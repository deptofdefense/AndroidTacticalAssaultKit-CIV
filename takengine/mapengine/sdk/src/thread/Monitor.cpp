#include "thread/Monitor.h"

#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Util;

Monitor::Monitor(const MutexType type) NOTHROWS :
    mutex(type)
{}

Monitor::~Monitor() NOTHROWS
{}

Monitor::Lock::Lock(Monitor &owner_, LockPtr &&impl_) NOTHROWS :
    owner(owner_),
    impl(std::move(impl_))
{}

Monitor::Lock::~Lock() NOTHROWS
{}

TAKErr Monitor::Lock::wait(const int64_t millis) NOTHROWS
{
    return this->owner.cond.wait(*this->impl, millis);
}

TAKErr Monitor::Lock::signal() NOTHROWS
{
    return this->owner.cond.signal(*this->impl);
}

TAKErr Monitor::Lock::broadcast() NOTHROWS
{
    return this->owner.cond.broadcast(*this->impl);
}

TAKErr TAK::Engine::Thread::MonitorLock_create(MonitorLockPtr &value, Monitor &monitor) NOTHROWS
{
    TAKErr code(TE_Ok);

    LockPtr impl(NULL, NULL);
    code = Lock_create(impl, monitor.mutex);
    TE_CHECKRETURN_CODE(code);

    value = MonitorLockPtr(new Monitor::Lock(monitor, std::move(impl)), Memory_deleter_const<Monitor::Lock>);
    return code;
}
