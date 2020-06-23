#include "thread/Thread.h"
#include "thread/impl/CondImpl.h"
#include "thread/impl/LockImpl.h"
#include "thread/impl/MutexImpl.h"
#include "thread/impl/ThreadImpl.h"

#include <map>
#include <time.h>
#ifndef MSVC
#include <sys/time.h>
#endif

#include <threads/Cond.hh>
#include <threads/Lock.hh>
#include <threads/Mutex.hh>
#include <threads/Thread.hh>
#include <threads/WriteLock.hh>

#include "thread/Monitor.h"
#include "util/Memory.h"

using namespace PGSC::Thread;
using namespace PGSC::Thread::Impl;

using namespace PGSC::Util;

using namespace atakmap::util;

namespace
{
    class ThreadRuntimeCore
    {
    public :
        ThreadRuntimeCore() PGSCT_NOTHROWS;
        ThreadRuntimeCore(const pthread_t &tid) PGSCT_NOTHROWS;
    public :
        /** the owning thread ID */
        pthread_t tid;
        /** 'true' if the thread has started execution, 'false' otherwise */
        bool started;
        /** 'true' if the thread has terminated, successfully or otherwise, 'false' otherwise */
        bool terminated;
        /** */
        Monitor monitor;

        void *(*entry)(void *);
        void *opaque;
    };

    class PGSCUtilThreadIDImpl : public ThreadIDImpl
    {
    public:
        PGSCUtilThreadIDImpl(const pthread_t &) PGSCT_NOTHROWS;
        PGSCUtilThreadIDImpl(const std::shared_ptr<ThreadRuntimeCore> &) PGSCT_NOTHROWS;
    public:
        virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) PGSCT_NOTHROWS;
    public:
        virtual bool operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS;
    private:
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    class PGSCUtilCondVarImpl : public CondVarImpl
    {
    public:
        PGSCUtilCondVarImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr broadcast(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr signal(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr wait(LockImpl &lock, const int64_t milliSeconds) PGSCT_NOTHROWS;
    private :
        ThreadErr getImpl(PGSC::Cond **value, LockImpl &lock) PGSCT_NOTHROWS;
    private :
        PGSC::Mutex mutex;
        std::unique_ptr<PGSC::Cond> impl;
        PGSC::Mutex *implMutex;

        std::map<PGSC::Mutex *, std::unique_ptr<PGSC::Cond>> implOverflow;
    };

    class PGSCUtilLockImpl : public LockImpl
    {
    public:
        PGSCUtilLockImpl(PGSC::Mutex *mutex, std::unique_ptr<PGSC::Lock> &&impl) PGSCT_NOTHROWS;
    private :
        std::unique_ptr<PGSC::Lock> impl;
        PGSC::Mutex *mutex;

        friend class PGSCUtilCondVarImpl;
    };

    class PGSCUtilMutexImpl : public MutexImpl
    {
    public:
        PGSCUtilMutexImpl(std::unique_ptr<PGSC::Mutex> &&impl) PGSCT_NOTHROWS;
    public:
        virtual ThreadErr lock() PGSCT_NOTHROWS;
        virtual ThreadErr unlock() PGSCT_NOTHROWS;
    private :
        std::unique_ptr<PGSC::Mutex> impl;

        friend ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS;
        friend ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl() PGSCT_NOTHROWS;
        ~ThreadImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr start(void *(*entry)(void *), void *opaque) PGSCT_NOTHROWS;
    public:
        virtual ThreadErr join(const int64_t millis = 0LL) PGSCT_NOTHROWS;
        virtual ThreadErr detach() PGSCT_NOTHROWS;
    private :
        virtual void getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS;
    private:
        std::unique_ptr<PGSC::Thread> impl;
        std::shared_ptr<ThreadRuntimeCore> core;

        friend class PGSCUtilThreadIDImpl;
    };

    ThreadErr translateMutexType(PGSC::Mutex::Attr::Type *value, const MutexType &type) PGSCT_NOTHROWS;
    void *threadCoreRun(void *opaque);
}

/*****************************************************************************/
// CondVar definitions

ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS
{
    value = CondVarImplPtr(new PGSCUtilCondVarImpl(), Memory_deleter_const<CondVarImpl, PGSCUtilCondVarImpl>);

    return Thread_Ok;
}

/*****************************************************************************/
// Lock definitions

ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS
{
    PGSCUtilMutexImpl &mutexImpl = static_cast<PGSCUtilMutexImpl &>(mutex);

    std::unique_ptr<PGSC::Lock> impl;
    try {
        impl.reset(new PGSC::Lock(*mutexImpl.impl));
    } catch (PGSC::MutexBusy &e) {
        fprintf(stderr, "LockImpl_create: mutex busy, errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "LockImpl_create: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }
    value = LockImplPtr(new PGSCUtilLockImpl(mutexImpl.impl.get(), std::move(impl)), Memory_deleter_const<LockImpl, PGSCUtilLockImpl>);

    return Thread_Ok;
}

/*****************************************************************************/
// Mutex definitions

ThreadErr PGSC::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    PGSC::Mutex::Attr::Type attr;
    code = translateMutexType(&attr, type);
    if (code != Thread_Ok)
        return code;

    std::unique_ptr<PGSC::Mutex> impl;
    try {
        impl.reset(new PGSC::Mutex(PGSC::Mutex::Attr(attr)));
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "MutexImpl_create: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    value = MutexImplPtr(new PGSCUtilMutexImpl(std::move(impl)), Memory_deleter_const<MutexImpl, PGSCUtilMutexImpl>);

    return code;
}

/*****************************************************************************/
// Thread definitions

ThreadErr PGSC::Thread::Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    std::auto_ptr<ThreadImpl> retval(new ThreadImpl());
    code = retval->start(entry, opaque);
    if (code != Thread_Ok)
        return code;

    value = ThreadPtr(retval.release(), Memory_deleter_const<Thread, ThreadImpl>);
    return code;
}

ThreadID PGSC::Thread::Thread_currentThreadID() PGSCT_NOTHROWS
{
    return ThreadID(std::move(ThreadIDImplPtr(new PGSCUtilThreadIDImpl(pthread_self()), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>)));
}

/*****************************************************************************/

namespace
{

ThreadRuntimeCore::ThreadRuntimeCore() PGSCT_NOTHROWS :
    started(false),
    terminated(false),
    entry(NULL),
    opaque(NULL)
{}

ThreadRuntimeCore::ThreadRuntimeCore(const pthread_t &tid_) PGSCT_NOTHROWS :
    tid(tid_),
    started(true),
    terminated(false),
    entry(NULL),
    opaque(NULL)
{}

PGSCUtilThreadIDImpl::PGSCUtilThreadIDImpl(const pthread_t &tid_) PGSCT_NOTHROWS :
    core(std::shared_ptr<ThreadRuntimeCore>(new ThreadRuntimeCore(tid_)))
{}

PGSCUtilThreadIDImpl::PGSCUtilThreadIDImpl(const std::shared_ptr<ThreadRuntimeCore> &core_) PGSCT_NOTHROWS :
    core(core_)
{}

void PGSCUtilThreadIDImpl::clone(ThreadIDImplPtr &value) PGSCT_NOTHROWS
{
    value = ThreadIDImplPtr(new PGSCUtilThreadIDImpl(core), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>);
}

bool PGSCUtilThreadIDImpl::operator==(const ThreadIDImpl &other_) const PGSCT_NOTHROWS
{
    const PGSCUtilThreadIDImpl &other = static_cast<const PGSCUtilThreadIDImpl &>(other_);
    return ((this->core.get() && this->core->started) &&
            (other.core.get() && other.core->started) &&
            pthread_equal(this->core->tid, other.core->tid));
}

PGSCUtilCondVarImpl::PGSCUtilCondVarImpl() PGSCT_NOTHROWS :
    implMutex(NULL)
{}

ThreadErr PGSCUtilCondVarImpl::broadcast(LockImpl &lock) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != Thread_Ok)
        return code;

    try {
        cond->broadcast(*static_cast<PGSCUtilLockImpl &>(lock).impl);
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "PGSCUtilCondVarImpl::broadcast: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    return code;
}

ThreadErr PGSCUtilCondVarImpl::signal(LockImpl &lock) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != Thread_Ok)
        return code;

    try {
        cond->signal(*static_cast<PGSCUtilLockImpl &>(lock).impl);
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "PGSCUtilCondVarImpl::broadcast: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    return code;
}

ThreadErr PGSCUtilCondVarImpl::wait(LockImpl &lock, const int64_t milliseconds) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != Thread_Ok)
        return code;

    try {
        if (!milliseconds) {
            cond->wait(*static_cast<PGSCUtilLockImpl &>(lock).impl);
        } else {
#ifdef MSVC
            // XXX - I *think* we can do it this way on all platforms, but need
            //       to test on iOS/Android
            int64_t epochWakeup = PGSC::Port::Platform_systime_millis() + milliseconds;
            struct timespec time;
            time.tv_sec = (epochWakeup / 1000LL);
            time.tv_nsec = ((epochWakeup % 1000LL) * 1000000LL);
#else
            // derived from https://stackoverflow.com/questions/17166083/how-to-use-pthread-cond-timedwait-with-millisecond
            struct timespec time;
            struct timeval tp;
            int rc = gettimeofday(&tp, NULL);

            time.tv_sec = tp.tv_sec + (milliseconds / 1000LL);
            time.tv_nsec = ((tp.tv_usec * 1000LL) + ((milliseconds % 1000LL) * 1000000LL));
            if (time.tv_nsec > 1000000000LL) {
                time.tv_sec++;
                time.tv_nsec -= 1000000000LL;
            }
#endif
            cond->wait(*static_cast<PGSCUtilLockImpl &>(lock).impl, time);
        }
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "PGSCUtilCondVarImpl::broadcast: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    return code;
}

ThreadErr PGSCUtilCondVarImpl::getImpl(PGSC::Cond **value, LockImpl &lock) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    PGSCUtilLockImpl &lockImpl = static_cast<PGSCUtilLockImpl &>(lock);

    PGSC::Lock sync(mutex);

    if (!impl.get()) {
        try {
            impl.reset(new PGSC::Cond(*lockImpl.mutex));
        } catch (PGSC::ThreadError &e) {
            fprintf(stderr, "CondVar: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
            return Thread_Err;
        } catch (...) {
            return Thread_Err;
        }
        implMutex = lockImpl.mutex;
        *value = impl.get();
        return code;
    } else if (implMutex == lockImpl.mutex) {
        *value = impl.get();
        return code;
    } else {
        std::map<PGSC::Mutex *, std::unique_ptr<PGSC::Cond>>::iterator entry;
        entry = implOverflow.find(lockImpl.mutex);
        if (entry != implOverflow.end()) {
            *value = entry->second.get();
            return Thread_Ok;
        } else {
            std::unique_ptr<PGSC::Cond> retval;
            try {
                retval.reset(new PGSC::Cond(*lockImpl.mutex));
            } catch (PGSC::ThreadError &e) {
                fprintf(stderr, "CondVar: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
                return Thread_Err;
            } catch (...) {
                return Thread_Err;
            }
            *value = retval.get();
            implOverflow.insert(std::pair<PGSC::Mutex *, std::unique_ptr<PGSC::Cond>>(lockImpl.mutex, std::move(retval)));
            return code;
        }
    }
}

// PGSCUtilLockImpl

PGSCUtilLockImpl::PGSCUtilLockImpl(PGSC::Mutex *mutex_, std::unique_ptr<PGSC::Lock> &&impl_) PGSCT_NOTHROWS :
    mutex(mutex_),
    impl(std::move(impl_))
{}

// PGSCUtilMutexImpl

PGSCUtilMutexImpl::PGSCUtilMutexImpl(std::unique_ptr<PGSC::Mutex> &&impl_) PGSCT_NOTHROWS :
    impl(std::move(impl_))
{}

ThreadErr PGSCUtilMutexImpl::lock() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    int err = impl->lock();
    if (err) {
        fprintf(stderr, "PGSCUtilMutexImpl::lock: error occurred. errno=%d", err);
        return Thread_Err;
    }

    return code;
}

ThreadErr PGSCUtilMutexImpl::unlock() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    int err = impl->unlock();
    if (err) {
        fprintf(stderr, "PGSCUtilMutexImpl::unlock: error occurred. errno=%d", err);
        return Thread_Err;
    }

    return code;
}

// ThreadImpl
ThreadImpl::ThreadImpl() PGSCT_NOTHROWS
{}

ThreadImpl::~ThreadImpl() PGSCT_NOTHROWS
{
    join();
}

ThreadErr ThreadImpl::start(void *(*entry)(void *), void *opaque) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    if (impl.get())
        return Thread_IllegalState;

    try {
        core.reset(new ThreadRuntimeCore());
        core->entry = entry;
        core->opaque = opaque;
        impl = std::unique_ptr<PGSC::Thread>(new PGSC::Thread(threadCoreRun, new std::shared_ptr<ThreadRuntimeCore>(this->core)));
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    return code;
}

ThreadErr ThreadImpl::join(const int64_t millis) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    if (!impl.get())
        return Thread_IllegalState;

    if (!millis) {
        try {
            impl->join();
            return Thread_Ok;
        } catch (PGSC::ThreadError &e) {
            fprintf(stderr, e.description());
            return Thread_Err;
        } catch (...) {
            return Thread_Err;
        }
    } else {
        if (!core.get())
            return Thread_IllegalState;

        MonitorLockPtr lock(NULL, NULL);
        code = MonitorLock_create(lock, core->monitor);
        if (code != Thread_Ok)
            return code;
        while (!core->terminated) {
            code = lock->wait(millis);
            if (code != Thread_Ok)
                break;
        }
        if (code != Thread_Ok)
            return code;
    }

    return code;
}

ThreadErr ThreadImpl::detach() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    if (!impl.get())
        return Thread_IllegalState;

    try {
        impl->detach();
        impl.reset();
        core.reset();
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, e.description());
        return Thread_Err;
    } catch (...) {
        return Thread_Err;
    }

    return code;
}

void ThreadImpl::getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS
{
    if (!core.get())
        value = ThreadIDImplPtr(NULL, NULL);
    else
        value = ThreadIDImplPtr(new PGSCUtilThreadIDImpl(core), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>);
}

ThreadErr translateMutexType(PGSC::Mutex::Attr::Type *value, const MutexType &type) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    switch (type) {
    case TEMT_Default :
        *value = PGSC::Mutex::Attr::DEFAULT;
        break;
    case TEMT_Recursive :
        *value = PGSC::Mutex::Attr::RECURSIVE;
        break;
    default :
        return Thread_InvalidArg;
    }

    return code;
}

void *threadCoreRun(void *opaque)
{
    std::shared_ptr<ThreadRuntimeCore> core;
    {
        std::auto_ptr<std::shared_ptr<ThreadRuntimeCore>> arg(static_cast<std::shared_ptr<ThreadRuntimeCore> *>(opaque));
        core = *arg;
    }

    class TerminatedSignal
    {
    public:
        TerminatedSignal(const std::shared_ptr<ThreadRuntimeCore> &core_) : core(core_)
        {}
        ~TerminatedSignal()
        {
            MonitorLockPtr lock(NULL, NULL);
            MonitorLock_create(lock, core->monitor);
            core->terminated = true;
            if (lock.get())
                lock->broadcast();
        }
    private:
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    TerminatedSignal signal(core);
    core->tid = pthread_self();
    core->started = true;
    return core->entry(core->opaque);
}

}
