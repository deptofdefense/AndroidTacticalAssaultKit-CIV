#include "RWMutex.h"

using namespace PGSC::Thread;

using namespace PGSC::Util;

// RWMutex

RWMutex::RWMutex(const RWMutexPolicy policy_) PGSCT_NOTHROWS :
    monitor(TEMT_Default),
    policy(policy_),
    readers(0u),
    writers(0u),
    waitingWriters(0u)
{}
RWMutex::~RWMutex() PGSCT_NOTHROWS
{}
ThreadErr RWMutex::lockRead() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    while (writers || waitingWriters) {
        code = lock->wait();
        if (code == Thread_Interrupted)
            code = Thread_Ok;
        THREAD_CHECKBREAK_CODE(code);
    }
    THREAD_CHECKRETURN_CODE(code);

    readers++;
    return code;
}
ThreadErr RWMutex::unlockRead() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    if (!readers)
        return Thread_IllegalState;
    readers--;
    code = lock->broadcast();
    THREAD_CHECKRETURN_CODE(code);

    return code;
}
ThreadErr RWMutex::lockWrite() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    // signal that there is a waiting writer
    if (policy == TERW_Fair)
        waitingWriters++;

    // writer needs to wait until there are no writers or readers currently
    // accessing the resource
    while (writers || readers) {
        code = lock->wait();
        if (code == Thread_Interrupted)
            code = Thread_Ok;
        THREAD_CHECKBREAK_CODE(code);
    }
    THREAD_CHECKRETURN_CODE(code);

    // signal that the writer has entered
    if (policy == TERW_Fair)
        waitingWriters--;

    writers++;
    return code;
}
ThreadErr RWMutex::unlockWrite() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock(NULL, NULL);
    code = MonitorLock_create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    if (!writers)
        return Thread_IllegalState;
    writers--;
    code = lock->broadcast();
    THREAD_CHECKRETURN_CODE(code);

    return code;
}

// ReadLock

ReadLock::ReadLock(RWMutex &mutex_) PGSCT_NOTHROWS :
    mutex(mutex_)
{}
ReadLock::~ReadLock() PGSCT_NOTHROWS
{
    mutex.unlockRead();
}

// WriteLock

WriteLock::WriteLock(RWMutex &mutex_) PGSCT_NOTHROWS :
    mutex(mutex_)
{}
WriteLock::~WriteLock() PGSCT_NOTHROWS
{
    mutex.unlockWrite();
}


ThreadErr PGSC::Thread::ReadLock_create(ReadLockPtr &value, RWMutex &mutex) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    code = mutex.lockRead();
    THREAD_CHECKRETURN_CODE(code);

    value = ReadLockPtr(new ReadLock(mutex), Memory_deleter_const<ReadLock>);
    return code;
}

ThreadErr PGSC::Thread::WriteLock_create(WriteLockPtr &value, RWMutex &mutex) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    code = mutex.lockWrite();
    THREAD_CHECKRETURN_CODE(code);

    value = WriteLockPtr(new WriteLock(mutex), Memory_deleter_const<WriteLock>);
    return code;
}
