#include "Thread.h"
#include "impl/CondImpl.h"
#include "impl/LockImpl.h"
#include "impl/MutexImpl.h"
#include "impl/ThreadImpl.h"

#include <ctime>

#include <pthread.h>
#ifndef MSVC
#include <sys/time.h>
#endif

#include "Monitor.h"

using namespace PGSC::Thread;
using namespace PGSC::Thread::Impl;

using namespace PGSC::Util;

namespace
{
    class ThreadRuntimeCore
    {
    public:
        ThreadRuntimeCore() PGSCT_NOTHROWS;
    public:
        /** 'true' if the thread has started execution, 'false' otherwise */
        bool started;
        /** 'true' if the thread has terminated, successfully or otherwise, 'false' otherwise */
        bool terminated;
        /** */
        Monitor monitor;
        ThreadCreateParams params;

        void *(*entry)(void *);
        void *opaque;
    };

    class PThreadsThreadIDImpl : public ThreadIDImpl
    {
    public:
        PThreadsThreadIDImpl(const pthread_t &) PGSCT_NOTHROWS;
    public:
        virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) PGSCT_NOTHROWS;
    public:
        virtual bool operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS;
    private :
        pthread_t tid;
    };

    class PThreadsMutexImpl : public MutexImpl
    {
    public:
        PThreadsMutexImpl(const MutexType type) PGSCT_NOTHROWS;
        ~PThreadsMutexImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr lock() PGSCT_NOTHROWS;
        virtual ThreadErr unlock() PGSCT_NOTHROWS;
    public:
        ThreadErr getMutex(pthread_mutex_t **value)
        {
            if (!mutex.get())
                return Thread_IllegalState;
            *value = mutex.get();
            return Thread_Ok;
        }
    private:
        std::unique_ptr<pthread_mutex_t, int(*)(pthread_mutex_t *)> mutex;
        std::unique_ptr<pthread_mutexattr_t, int(*)(pthread_mutexattr_t *)> attr;
        pthread_mutex_t mutex_s;
        pthread_mutexattr_t attr_s;

        friend ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS;
        friend ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS;
    };

    class PThreadsLockImpl : public LockImpl
    {
    public:
        PThreadsLockImpl(PThreadsMutexImpl &mutex) PGSCT_NOTHROWS;
        ~PThreadsLockImpl() PGSCT_NOTHROWS;
    private:
        PThreadsMutexImpl &mutex;

        friend class PThreadsCondVarImpl;
    };

    class PThreadsCondVarImpl : public CondVarImpl
    {
    public:
        PThreadsCondVarImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr broadcast(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr signal(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr wait(LockImpl &lock, const int64_t milliSeconds) PGSCT_NOTHROWS;
    private:
        std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)> impl;
        pthread_cond_t impl_s;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl() PGSCT_NOTHROWS;
        ~ThreadImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr start(void *(*entry)(void *), void *opaque, const ThreadCreateParams params) PGSCT_NOTHROWS;
    public:
        virtual ThreadErr join(const int64_t millis = 0LL) PGSCT_NOTHROWS;
        virtual ThreadErr detach() PGSCT_NOTHROWS;
    private :
        virtual void getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS;
    private:
        std::unique_ptr<pthread_t, void(*)(pthread_t *)> thread;
        pthread_t thread_s;
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    void *threadCoreRun(void *opaque);
}

/*****************************************************************************/
// CondVar definitions

ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS
{
    value = CondVarImplPtr(new PThreadsCondVarImpl(), Memory_deleter_const<CondVarImpl, PThreadsCondVarImpl>);
    // XXX - could check initialization here

    return Thread_Ok;
}

/*****************************************************************************/
// Lock definitions

ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    PThreadsMutexImpl &mutexImpl = static_cast<PThreadsMutexImpl &>(mutex);

    code = mutexImpl.lock();
    if (code != Thread_Ok)
        return code;

    value = LockImplPtr(new PThreadsLockImpl(mutexImpl), Memory_deleter_const<LockImpl, PThreadsLockImpl>);

    return code;
}

/*****************************************************************************/
// Mutex definitions

ThreadErr PGSC::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) PGSCT_NOTHROWS
{
    std::unique_ptr<PThreadsMutexImpl> retval(new PThreadsMutexImpl(type));
    // XXX - could check initialization here
    value = MutexImplPtr(retval.release(), Memory_deleter_const<MutexImpl, PThreadsMutexImpl>);
    return Thread_Ok;
}

/*****************************************************************************/
// Thread definitions

ThreadErr PGSC::Thread::Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    std::unique_ptr<ThreadImpl> retval(new ThreadImpl());
    code = retval->start(entry, opaque, params);
    if (code != Thread_Ok)
        return code;

    value = ThreadPtr(retval.release(), Memory_deleter_const<Thread, ThreadImpl>);
    return code;
}

ThreadID PGSC::Thread::Thread_currentThreadID() PGSCT_NOTHROWS
{
    return ThreadID(ThreadIDImplPtr(new PThreadsThreadIDImpl(pthread_self()), Memory_deleter_const<ThreadIDImpl, PThreadsThreadIDImpl>));
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

    PThreadsThreadIDImpl::PThreadsThreadIDImpl(const pthread_t &tid_) PGSCT_NOTHROWS :
        tid(tid_)
    {}

    void PThreadsThreadIDImpl::clone(ThreadIDImplPtr &value) PGSCT_NOTHROWS
    {
        value = ThreadIDImplPtr(new PThreadsThreadIDImpl(tid), Memory_deleter_const<ThreadIDImpl, PThreadsThreadIDImpl>);
    }

    bool PThreadsThreadIDImpl::operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS
    {
        return pthread_equal(tid, static_cast<const PThreadsThreadIDImpl &>(other).tid);
    }

    PThreadsCondVarImpl::PThreadsCondVarImpl() PGSCT_NOTHROWS :
        impl(NULL, NULL)
    {
        int err = pthread_cond_init(&impl_s, NULL);
        if (!err)
            impl = std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)>(&impl_s, pthread_cond_destroy);
    }

    ThreadErr PThreadsCondVarImpl::broadcast(LockImpl &lock) PGSCT_NOTHROWS
    {
        if (!impl.get())
            return Thread_IllegalState;
        int err = pthread_cond_broadcast(impl.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsCondVarImpl::signal(LockImpl &lock) PGSCT_NOTHROWS
    {
        if (!impl.get())
            return Thread_IllegalState;
        int err = pthread_cond_signal(impl.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsCondVarImpl::wait(LockImpl &lock, const int64_t milliseconds) PGSCT_NOTHROWS
    {
        ThreadErr code(Thread_Ok);
        PThreadsLockImpl &lockImpl = static_cast<PThreadsLockImpl &>(lock);
        pthread_mutex_t *mutexImpl(NULL);
        code = lockImpl.mutex.getMutex(&mutexImpl);
        if (code != Thread_Ok)
            return code;

        if (!impl.get())
            return Thread_IllegalState;
        int err;
        if (milliseconds) {
#ifdef MSVC
            // XXX - I *think* we can do it this way on all platforms, but need
            //       to test on iOS/Android
            int64_t epochWakeup = PGSC::Port::Platform_systime_millis() + milliseconds;
            struct timespec ts;
            ts.tv_sec = (epochWakeup / 1000LL);
            ts.tv_nsec = ((epochWakeup % 1000LL) * 1000000LL);
#else
            // derived from https://stackoverflow.com/questions/17166083/how-to-use-pthread-cond-timedwait-with-millisecond
            struct timespec ts;
            struct timeval tp;
            gettimeofday(&tp, NULL);

            ts.tv_sec = tp.tv_sec + (milliseconds / 1000LL);
            ts.tv_nsec = ((tp.tv_usec * 1000LL) + ((milliseconds % 1000LL) * 1000000LL));
            if (ts.tv_nsec > 1000000000LL) {
                ts.tv_sec++;
                ts.tv_nsec -= 1000000000LL;
            }
#endif
            err = pthread_cond_timedwait(impl.get(), mutexImpl, &ts);
        } else {
            err = pthread_cond_wait(impl.get(), mutexImpl);
        }
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    // PThreadsLockImpl

    PThreadsLockImpl::PThreadsLockImpl(PThreadsMutexImpl &mutex_) PGSCT_NOTHROWS :
        mutex(mutex_)
    {}

    PThreadsLockImpl::~PThreadsLockImpl() PGSCT_NOTHROWS
    {
        mutex.unlock();
    }

    // PThreadsMutexImpl

    PThreadsMutexImpl::PThreadsMutexImpl(const MutexType type) PGSCT_NOTHROWS :
        mutex(NULL, NULL),
        attr(NULL, NULL)
    {
        int err;
        do {
            int pthreadMutexType;
            switch (type)
            {
            case TEMT_Default :
                pthreadMutexType = PTHREAD_MUTEX_DEFAULT;
                err = 0;
                break;
            case TEMT_Recursive :
                pthreadMutexType = PTHREAD_MUTEX_RECURSIVE;
                err = 0;
                break;
            default :
                err = 1;
                break;
            }
            if (err)
                break;

            // initialize the mutex attributes and set the mutex type
            err = pthread_mutexattr_init(&attr_s);
            if (err)
                break;
            attr = std::unique_ptr<pthread_mutexattr_t, int(*)(pthread_mutexattr_t *)>(&attr_s, pthread_mutexattr_destroy);
            err = pthread_mutexattr_settype(&attr_s, pthreadMutexType);
            if (err)
                break;

            err = pthread_mutex_init(&mutex_s, &attr_s);
            if (err)
                break;
            mutex = std::unique_ptr<pthread_mutex_t, int(*)(pthread_mutex_t *)>(&mutex_s, pthread_mutex_destroy);

        } while (false);

        if (err) {
            mutex.reset();
            attr.reset();
        }
    }

    PThreadsMutexImpl::~PThreadsMutexImpl() PGSCT_NOTHROWS
    {
        mutex.reset();
        attr.reset();
    }

    ThreadErr PThreadsMutexImpl::lock() PGSCT_NOTHROWS
    {
        if (!mutex.get())
            return Thread_IllegalState;
        int err = pthread_mutex_lock(mutex.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsMutexImpl::unlock() PGSCT_NOTHROWS
    {
        if (!mutex.get())
            return Thread_IllegalState;
        int err = pthread_mutex_unlock(mutex.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    // ThreadImpl
    inline ThreadImpl::ThreadImpl() PGSCT_NOTHROWS :
        thread(NULL, NULL)
    {}

    ThreadImpl::~ThreadImpl() PGSCT_NOTHROWS
    {
        join();
    }

    inline ThreadErr ThreadImpl::start(void *(*entry)(void *), void *opaque, const ThreadCreateParams params) PGSCT_NOTHROWS
    {
        ThreadErr code(Thread_Ok);

        if (thread.get())
            return Thread_IllegalState;

        core.reset(new ThreadRuntimeCore());
        core->entry = entry;
        core->opaque = opaque;
        core->params = params;
        int err = pthread_create(&thread_s, NULL, threadCoreRun, new std::shared_ptr<ThreadRuntimeCore>(core));
        if (err)
            return Thread_Err;

        thread = std::unique_ptr<pthread_t, void(*)(pthread_t *)>(&thread_s, Memory_leaker<pthread_t>);

        return code;
    }

    inline ThreadErr ThreadImpl::join(const int64_t millis) PGSCT_NOTHROWS
    {
        ThreadErr code(Thread_Ok);

        if (!thread.get())
            return Thread_IllegalState;

        if (!millis) {
            void *result;
            int err = pthread_join(*thread, &result);
            thread.reset();
            core.reset();
            if (err)
                return Thread_Err;
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

    inline ThreadErr ThreadImpl::detach() PGSCT_NOTHROWS
    {
        ThreadErr code(Thread_Ok);

        if (!thread.get())
            return Thread_IllegalState;

        int err = pthread_detach(*thread);
        thread.reset();
        core.reset();
        if (err)
            return Thread_Err;

        return code;
    }

    void ThreadImpl::getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS
    {
        if (!thread.get())
            value = ThreadIDImplPtr(NULL, NULL);
        else
            value = ThreadIDImplPtr(new PThreadsThreadIDImpl(*thread), Memory_deleter_const<ThreadIDImpl, PThreadsThreadIDImpl>);
    }

    void *threadCoreRun(void *opaque)
    {
        std::shared_ptr<ThreadRuntimeCore> core;
        {
            std::unique_ptr<std::shared_ptr<ThreadRuntimeCore>> arg(static_cast<std::shared_ptr<ThreadRuntimeCore> *>(opaque));
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
        core->started = true;
        if (core->params.name.length() > 0) {
#if defined(__linux__)
            pthread_setname_np(pthread_self(), core->params.name.c_str());
#elif defined(__APPLE__)
            pthread_setname_np(core->params.name.c_str());
#endif            
        }
        return core->entry(core->opaque);
    }
}
