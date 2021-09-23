#include "commothread.h"
#include "platformthread.h"

using namespace atakmap::commoncommo::impl::thread;


// Mutex

Mutex::Mutex(const Mutex::Type type) :
    impl()
{
    if (PlatformMutex::create(impl, type) != Thread_Ok)
        impl.reset();
}

Mutex::~Mutex()
{}

ThreadErr Mutex::lock()
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->lock();
}

ThreadErr Mutex::unlock()
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->unlock();
}



// RWMutex

RWMutex::RWMutex(const RWMutex::Policy policy_) :
    monitor(Mutex::Type::Type_Default),
    policy(policy_),
    readers(0u),
    writers(0u),
    waitingWriters(0u)
{}

RWMutex::~RWMutex()
{}

ThreadErr RWMutex::lockRead()
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock;
    code = MonitorLock::create(lock, monitor);
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

ThreadErr RWMutex::unlockRead()
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock;
    code = MonitorLock::create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    if (!readers)
        return Thread_IllegalState;
    readers--;
    code = lock->broadcast();
    THREAD_CHECKRETURN_CODE(code);

    return code;
}

ThreadErr RWMutex::lockWrite()
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock;
    code = MonitorLock::create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    // signal that there is a waiting writer
    if (policy == Policy_Fair)
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
    if (policy == Policy::Policy_Fair)
        waitingWriters--;

    writers++;
    return code;
}

ThreadErr RWMutex::unlockWrite()
{
    ThreadErr code(Thread_Ok);
    MonitorLockPtr lock;
    code = MonitorLock::create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);

    if (!writers)
        return Thread_IllegalState;
    writers--;
    code = lock->broadcast();
    THREAD_CHECKRETURN_CODE(code);

    return code;
}


// ReadLock

ReadLock::ReadLock(RWMutex &mutex_) :
    status(mutex_.lockRead()),
    mutex(mutex_)
{}

ReadLock::~ReadLock()
{
    mutex.unlockRead();
}

ThreadErr ReadLock::create(ReadLockPtr &value, RWMutex &mutex)
{
    value = ReadLockPtr(new ReadLock(mutex));
    if (!value.get())
        return Thread_Err;
    return Thread_Ok;
}


// WriteLock

WriteLock::WriteLock(RWMutex &mutex_) :
    status(mutex_.lockWrite()),
    mutex(mutex_)
{}

WriteLock::~WriteLock()
{
    mutex.unlockWrite();
}


ThreadErr WriteLock::create(WriteLockPtr &value, RWMutex &mutex)
{
    value = WriteLockPtr(new WriteLock(mutex));
    if (!value.get())
        return Thread_Err;
    return Thread_Ok;
}


// Lock

Lock::Lock(Mutex &mutex_) :
    status(mutex_.lock()),
    mutex(mutex_)
{}

Lock::~Lock()
{
    if (status == Thread_Ok)
        mutex.unlock();
}

ThreadErr Lock::create(LockPtr &value, Mutex &mutex)
{
    value = LockPtr(new Lock(mutex));
    if (!value.get())
        return Thread_Err;
    return value->status;
}


// Monitor

Monitor::Monitor(const Mutex::Type type) :
    mutex(type)
{}

Monitor::~Monitor()
{}

MonitorLock::MonitorLock(Monitor &owner_) :
    owner(owner_),
    impl(owner.mutex),
    status(impl.status)
{}

MonitorLock::~MonitorLock()
{}

ThreadErr MonitorLock::wait(const int64_t millis)
{
    return this->owner.cond.wait(this->impl, millis);
}

ThreadErr MonitorLock::signal()
{
    return this->owner.cond.signal(this->impl);
}

ThreadErr MonitorLock::broadcast()
{
    return this->owner.cond.broadcast(this->impl);
}

ThreadErr MonitorLock::create(MonitorLockPtr &value, Monitor &monitor)
{
    value = MonitorLockPtr(new MonitorLock(monitor));
    if (!value.get())
        return Thread_Err;
    return value->status;
}



// CondVar

CondVar::CondVar() :
    impl()
{
    if (PlatformCondVar::create(impl) != Thread_Ok)
        impl.reset();
}

CondVar::~CondVar()
{}

ThreadErr CondVar::broadcast(Lock &lock)
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->broadcast(*lock.mutex.impl);
}

ThreadErr CondVar::signal(Lock &lock)
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->signal(*lock.mutex.impl);
}
                
ThreadErr CondVar::wait(Lock &lock, const int64_t milliseconds)
{
    if (!impl.get())
    return Thread_IllegalState;
    if (!lock.mutex.impl.get())
        return Thread_IllegalState;
    return impl->wait(*lock.mutex.impl, milliseconds);
}
