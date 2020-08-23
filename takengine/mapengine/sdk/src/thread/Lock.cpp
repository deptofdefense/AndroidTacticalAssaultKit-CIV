#include "thread/Lock.h"

#include "thread/impl/LockImpl.h"
#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Thread::Impl;
using namespace TAK::Engine::Util;


Lock::Lock(Mutex &mutex_) NOTHROWS :
    status(mutex_.lock()),
    mutex(mutex_)
{}

Lock::~Lock() NOTHROWS
{
    if(status == TE_Ok)
        mutex.unlock();
}

TAKErr TAK::Engine::Thread::Lock_create(LockPtr &value, Mutex &mutex) NOTHROWS
{
    value = LockPtr(new(std::nothrow) Lock(mutex), Memory_deleter_const<Lock>);
    if (!value.get())
        return TE_OutOfMemory;
    return value->status;
}
