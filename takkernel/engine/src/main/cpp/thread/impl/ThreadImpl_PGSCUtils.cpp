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

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Thread::Impl;

using namespace TAK::Engine::Util;

using namespace atakmap::util;

#ifdef TE_CHECKRETURN_CODE
#undef TE_CHECKRETURN_CODE
#endif
#ifdef TE_CHECKBREAK_CODE
#undef TE_CHECKBREAK_CODE
#endif
#ifdef TE_CHECKLOGRETURN_CODE
#undef TE_CHECKLOGRETURN_CODE
#endif
#ifdef TE_CHECKLOGRETURN_CODE2
#undef TE_CHECKLOGRETURN_CODE2
#endif

#ifdef Logger
#undef Logger
#endif
#ifdef Logger_log
#undef Logger_log
#endif

#define Logger Logging_is_prohibited_in_thread_impl
#define Logger_log Logging_is_prohibited_in_thread_impl
#define TE_CHECKRETURN_CODE Logging_is_prohibited_in_thread_impl
#define TE_CHECKBREAK_CODE Logging_is_prohibited_in_thread_impl
#define TE_CHECKLOGRETURN_CODE Logging_is_prohibited_in_thread_impl
#define TE_CHECKLOGRETURN_CODE2 Logging_is_prohibited_in_thread_impl

namespace
{
    class ThreadRuntimeCore
    {
    public :
        ThreadRuntimeCore() NOTHROWS;
        ThreadRuntimeCore(const pthread_t &tid) NOTHROWS;
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
        PGSCUtilThreadIDImpl(const pthread_t &) NOTHROWS;
        PGSCUtilThreadIDImpl(const std::shared_ptr<ThreadRuntimeCore> &) NOTHROWS;
    public:
        virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) NOTHROWS;
    public:
        virtual bool operator==(const ThreadIDImpl &other) const NOTHROWS;
    private:
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    class PGSCUtilCondVarImpl : public CondVarImpl
    {
    public:
        PGSCUtilCondVarImpl() NOTHROWS;
    public:
        virtual TAKErr broadcast(LockImpl &lock) NOTHROWS;
        virtual TAKErr signal(LockImpl &lock) NOTHROWS;
        virtual TAKErr wait(LockImpl &lock, const int64_t milliSeconds) NOTHROWS;
    private :
        TAKErr getImpl(PGSC::Cond **value, LockImpl &lock) NOTHROWS;
    private :
        PGSC::Mutex mutex;
        std::unique_ptr<PGSC::Cond> impl;
        PGSC::Mutex *implMutex;

        std::map<PGSC::Mutex *, std::unique_ptr<PGSC::Cond>> implOverflow;
    };

    class PGSCUtilLockImpl : public LockImpl
    {
    public:
        PGSCUtilLockImpl(PGSC::Mutex *mutex, std::unique_ptr<PGSC::Lock> &&impl) NOTHROWS;
    private :
        std::unique_ptr<PGSC::Lock> impl;
        PGSC::Mutex *mutex;

        friend class PGSCUtilCondVarImpl;
    };

    class PGSCUtilMutexImpl : public MutexImpl
    {
    public:
        PGSCUtilMutexImpl(std::unique_ptr<PGSC::Mutex> &&impl) NOTHROWS;
    public:
        virtual TAKErr lock() NOTHROWS;
        virtual TAKErr unlock() NOTHROWS;
    private :
        std::unique_ptr<PGSC::Mutex> impl;

        friend TAKErr TAK::Engine::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) NOTHROWS;
        friend TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl() NOTHROWS;
        ~ThreadImpl() NOTHROWS;
    public:
        virtual TAKErr start(void *(*entry)(void *), void *opaque) NOTHROWS;
    public:
        virtual TAKErr join(const int64_t millis = 0LL) NOTHROWS;
        virtual TAKErr detach() NOTHROWS;
    private :
        virtual void getID(ThreadIDImplPtr &value) const NOTHROWS;
    private:
        std::unique_ptr<PGSC::Thread> impl;
        std::shared_ptr<ThreadRuntimeCore> core;

        friend class PGSCUtilThreadIDImpl;
    };

    TAKErr translateMutexType(PGSC::Mutex::Attr::Type *value, const MutexType &type) NOTHROWS;
    void *threadCoreRun(void *opaque);
}

/*****************************************************************************/
// CondVar definitions

TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS
{
    value = CondVarImplPtr(new PGSCUtilCondVarImpl(), Memory_deleter_const<CondVarImpl, PGSCUtilCondVarImpl>);

    return TE_Ok;
}

/*****************************************************************************/
// Lock definitions

TAKErr TAK::Engine::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) NOTHROWS
{
    PGSCUtilMutexImpl &mutexImpl = static_cast<PGSCUtilMutexImpl &>(mutex);

    std::unique_ptr<PGSC::Lock> impl;
    try {
        impl.reset(new PGSC::Lock(*mutexImpl.impl));
    } catch (PGSC::MutexBusy &e) {
        fprintf(stderr, "LockImpl_create: mutex busy, errno=%d, %s", e.errorNumber(), e.description());
        return TE_Err;
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "LockImpl_create: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }
    value = LockImplPtr(new PGSCUtilLockImpl(mutexImpl.impl.get(), std::move(impl)), Memory_deleter_const<LockImpl, PGSCUtilLockImpl>);

    return TE_Ok;
}

/*****************************************************************************/
// Mutex definitions

TAKErr TAK::Engine::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) NOTHROWS
{
    TAKErr code(TE_Ok);

    PGSC::Mutex::Attr::Type attr;
    code = translateMutexType(&attr, type);
    if (code != TE_Ok)
        return code;

    std::unique_ptr<PGSC::Mutex> impl;
    try {
        impl.reset(new PGSC::Mutex(PGSC::Mutex::Attr(attr)));
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "MutexImpl_create: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    value = MutexImplPtr(new PGSCUtilMutexImpl(std::move(impl)), Memory_deleter_const<MutexImpl, PGSCUtilMutexImpl>);

    return code;
}

/*****************************************************************************/
// Thread definitions

TAKErr TAK::Engine::Thread::Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::auto_ptr<ThreadImpl> retval(new ThreadImpl());
    code = retval->start(entry, opaque);
    if (code != TE_Ok)
        return code;

    value = ThreadPtr(retval.release(), Memory_deleter_const<Thread, ThreadImpl>);
    return code;
}

ThreadID TAK::Engine::Thread::Thread_currentThreadID() NOTHROWS
{
    return ThreadID(std::move(ThreadIDImplPtr(new PGSCUtilThreadIDImpl(pthread_self()), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>)));
}

/*****************************************************************************/

namespace
{

ThreadRuntimeCore::ThreadRuntimeCore() NOTHROWS :
    started(false),
    terminated(false),
    entry(NULL),
    opaque(NULL)
{}

ThreadRuntimeCore::ThreadRuntimeCore(const pthread_t &tid_) NOTHROWS :
    tid(tid_),
    started(true),
    terminated(false),
    entry(NULL),
    opaque(NULL)
{}

PGSCUtilThreadIDImpl::PGSCUtilThreadIDImpl(const pthread_t &tid_) NOTHROWS :
    core(std::shared_ptr<ThreadRuntimeCore>(new ThreadRuntimeCore(tid_)))
{}

PGSCUtilThreadIDImpl::PGSCUtilThreadIDImpl(const std::shared_ptr<ThreadRuntimeCore> &core_) NOTHROWS :
    core(core_)
{}

void PGSCUtilThreadIDImpl::clone(ThreadIDImplPtr &value) NOTHROWS
{
    value = ThreadIDImplPtr(new PGSCUtilThreadIDImpl(core), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>);
}

bool PGSCUtilThreadIDImpl::operator==(const ThreadIDImpl &other_) const NOTHROWS
{
    const PGSCUtilThreadIDImpl &other = static_cast<const PGSCUtilThreadIDImpl &>(other_);
    return ((this->core.get() && this->core->started) &&
            (other.core.get() && other.core->started) &&
            pthread_equal(this->core->tid, other.core->tid));
}

PGSCUtilCondVarImpl::PGSCUtilCondVarImpl() NOTHROWS :
    implMutex(NULL)
{}

TAKErr PGSCUtilCondVarImpl::broadcast(LockImpl &lock) NOTHROWS
{
    TAKErr code(TE_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != TE_Ok)
        return code;

    try {
        cond->broadcast(*static_cast<PGSCUtilLockImpl &>(lock).impl);
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "PGSCUtilCondVarImpl::broadcast: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    return code;
}

TAKErr PGSCUtilCondVarImpl::signal(LockImpl &lock) NOTHROWS
{
    TAKErr code(TE_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != TE_Ok)
        return code;

    try {
        cond->signal(*static_cast<PGSCUtilLockImpl &>(lock).impl);
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, "PGSCUtilCondVarImpl::broadcast: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    return code;
}

TAKErr PGSCUtilCondVarImpl::wait(LockImpl &lock, const int64_t milliseconds) NOTHROWS
{
    TAKErr code(TE_Ok);

    PGSC::Cond *cond(NULL);
    code = getImpl(&cond, lock);
    if (code != TE_Ok)
        return code;

    try {
        if (!milliseconds) {
            cond->wait(*static_cast<PGSCUtilLockImpl &>(lock).impl);
        } else {
#ifdef MSVC
            // XXX - I *think* we can do it this way on all platforms, but need
            //       to test on iOS/Android
            int64_t epochWakeup = TAK::Engine::Port::Platform_systime_millis() + milliseconds;
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
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    return code;
}

TAKErr PGSCUtilCondVarImpl::getImpl(PGSC::Cond **value, LockImpl &lock) NOTHROWS
{
    TAKErr code(TE_Ok);
    PGSCUtilLockImpl &lockImpl = static_cast<PGSCUtilLockImpl &>(lock);

    PGSC::Lock sync(mutex);

    if (!impl.get()) {
        try {
            impl.reset(new PGSC::Cond(*lockImpl.mutex));
        } catch (PGSC::ThreadError &e) {
            fprintf(stderr, "CondVar: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
            return TE_Err;
        } catch (...) {
            return TE_Err;
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
            return TE_Ok;
        } else {
            std::unique_ptr<PGSC::Cond> retval;
            try {
                retval.reset(new PGSC::Cond(*lockImpl.mutex));
            } catch (PGSC::ThreadError &e) {
                fprintf(stderr, "CondVar: thread error occurred. errno=%d, %s", e.errorNumber(), e.description());
                return TE_Err;
            } catch (...) {
                return TE_Err;
            }
            *value = retval.get();
            implOverflow.insert(std::pair<PGSC::Mutex *, std::unique_ptr<PGSC::Cond>>(lockImpl.mutex, std::move(retval)));
            return code;
        }
    }
}

// PGSCUtilLockImpl

PGSCUtilLockImpl::PGSCUtilLockImpl(PGSC::Mutex *mutex_, std::unique_ptr<PGSC::Lock> &&impl_) NOTHROWS :
    mutex(mutex_),
    impl(std::move(impl_))
{}

// PGSCUtilMutexImpl

PGSCUtilMutexImpl::PGSCUtilMutexImpl(std::unique_ptr<PGSC::Mutex> &&impl_) NOTHROWS :
    impl(std::move(impl_))
{}

TAKErr PGSCUtilMutexImpl::lock() NOTHROWS
{
    TAKErr code(TE_Ok);

    int err = impl->lock();
    if (err) {
        fprintf(stderr, "PGSCUtilMutexImpl::lock: error occurred. errno=%d", err);
        return TE_Err;
    }

    return code;
}

TAKErr PGSCUtilMutexImpl::unlock() NOTHROWS
{
    TAKErr code(TE_Ok);

    int err = impl->unlock();
    if (err) {
        fprintf(stderr, "PGSCUtilMutexImpl::unlock: error occurred. errno=%d", err);
        return TE_Err;
    }

    return code;
}

// ThreadImpl
ThreadImpl::ThreadImpl() NOTHROWS
{}

ThreadImpl::~ThreadImpl() NOTHROWS
{
    join();
}

TAKErr ThreadImpl::start(void *(*entry)(void *), void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (impl.get())
        return TE_IllegalState;

    try {
        core.reset(new ThreadRuntimeCore());
        core->entry = entry;
        core->opaque = opaque;
        impl = std::unique_ptr<PGSC::Thread>(new PGSC::Thread(threadCoreRun, new std::shared_ptr<ThreadRuntimeCore>(this->core)));
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    return code;
}

TAKErr ThreadImpl::join(const int64_t millis) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!impl.get())
        return TE_IllegalState;

    if (!millis) {
        try {
            impl->join();
            return TE_Ok;
        } catch (PGSC::ThreadError &e) {
            fprintf(stderr, e.description());
            return TE_Err;
        } catch (...) {
            return TE_Err;
        }
    } else {
        if (!core.get())
            return TE_IllegalState;

        MonitorLockPtr lock(NULL, NULL);
        code = MonitorLock_create(lock, core->monitor);
        if (code != TE_Ok)
            return code;
        while (!core->terminated) {
            code = lock->wait(millis);
            if (code != TE_Ok)
                break;
        }
        if (code != TE_Ok)
            return code;
    }

    return code;
}

TAKErr ThreadImpl::detach() NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!impl.get())
        return TE_IllegalState;

    try {
        impl->detach();
        impl.reset();
        core.reset();
    } catch (PGSC::ThreadError &e) {
        fprintf(stderr, e.description());
        return TE_Err;
    } catch (...) {
        return TE_Err;
    }

    return code;
}

void ThreadImpl::getID(ThreadIDImplPtr &value) const NOTHROWS
{
    if (!core.get())
        value = ThreadIDImplPtr(NULL, NULL);
    else
        value = ThreadIDImplPtr(new PGSCUtilThreadIDImpl(core), Memory_deleter_const<ThreadIDImpl, PGSCUtilThreadIDImpl>);
}

TAKErr translateMutexType(PGSC::Mutex::Attr::Type *value, const MutexType &type) NOTHROWS
{
    TAKErr code(TE_Ok);

    switch (type) {
    case TEMT_Default :
        *value = PGSC::Mutex::Attr::DEFAULT;
        break;
    case TEMT_Recursive :
        *value = PGSC::Mutex::Attr::RECURSIVE;
        break;
    default :
        return TE_InvalidArg;
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
