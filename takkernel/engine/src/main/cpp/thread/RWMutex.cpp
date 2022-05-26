#include "thread/RWMutex.h"

#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Util;

// RWMutex

RWMutex::RWMutex(const RWMutexPolicy policy_) NOTHROWS :
    monitor(TEMT_Default),
    policy(policy_),
    readers(0u),
    writers(0u),
    waitingWriters(0u)
{}
RWMutex::~RWMutex() NOTHROWS
{}
TAKErr RWMutex::lockRead() NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    while (writers || waitingWriters) {
        code = lock.wait();
        if (code == TE_Interrupted)
            code = TE_Ok;
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    readers++;
    return code;
}
TAKErr RWMutex::unlockRead() NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!readers)
        return TE_IllegalState;
    readers--;
    code = lock.broadcast();
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr RWMutex::lockWrite() NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    // signal that there is a waiting writer
    if (policy == TERW_Fair)
        waitingWriters++;

    // writer needs to wait until there are no writers or readers currently
    // accessing the resource
    while (writers || readers) {
        code = lock.wait();
        if (code == TE_Interrupted)
            code = TE_Ok;
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    // signal that the writer has entered
    if (policy == TERW_Fair)
        waitingWriters--;

    writers++;
    return code;
}
TAKErr RWMutex::unlockWrite() NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!writers)
        return TE_IllegalState;
    writers--;
    code = lock.broadcast();
    TE_CHECKRETURN_CODE(code);

    return code;
}

// ReadLock

ReadLock::ReadLock(RWMutex &mutex_) NOTHROWS :
    status(mutex_.lockRead()),
    mutex(mutex_)
{}
ReadLock::~ReadLock() NOTHROWS
{
    mutex.unlockRead();
}

// WriteLock

WriteLock::WriteLock(RWMutex &mutex_) NOTHROWS :
    status(mutex_.lockWrite()),
    mutex(mutex_)
{}
WriteLock::~WriteLock() NOTHROWS
{
    mutex.unlockWrite();
}


TAKErr TAK::Engine::Thread::ReadLock_create(ReadLockPtr &value, RWMutex &mutex) NOTHROWS
{
    value = ReadLockPtr(new(std::nothrow) ReadLock(mutex), Memory_deleter_const<ReadLock>);
    if (!value.get())
        return TE_OutOfMemory;
    TE_CHECKRETURN_CODE(value->status);
    return TE_Ok;
}

TAKErr TAK::Engine::Thread::WriteLock_create(WriteLockPtr &value, RWMutex &mutex) NOTHROWS
{
    value = WriteLockPtr(new(std::nothrow) WriteLock(mutex), Memory_deleter_const<WriteLock>);
    if (!value.get())
        return TE_OutOfMemory;
    TE_CHECKRETURN_CODE(value->status);
    return TE_Ok;
}
