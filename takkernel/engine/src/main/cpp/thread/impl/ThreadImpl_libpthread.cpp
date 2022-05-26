#include "thread/Thread.h"
#include "thread/impl/CondImpl.h"
#include "thread/impl/MutexImpl.h"
#include "thread/impl/ThreadImpl.h"

#include <ctime>
#include <errno.h>
#include <pthread.h>
#ifndef MSVC
#include <sys/time.h>
#endif

#include "thread/Monitor.h"
#include "util/Memory.h"

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Thread::Impl;

using namespace TAK::Engine::Util;

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
    public:
        ThreadRuntimeCore() NOTHROWS;
    public:
        /** 'true' if the thread has started execution, 'false' otherwise */
        bool started;
        /** 'true' if the thread has terminated, successfully or otherwise, 'false' otherwise */
        bool terminated;
        /** */
        Monitor monitor;

        void *(*entry)(void *);
        void *opaque;
    };

    class PThreadsThreadIDImpl : public ThreadIDImpl
    {
    public:
        PThreadsThreadIDImpl(const pthread_t &) NOTHROWS;
    public:
        virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) NOTHROWS;
    public:
        virtual bool operator==(const ThreadIDImpl &other) const NOTHROWS;
    private :
        pthread_t tid;
    };

    class PThreadsMutexImpl : public MutexImpl
    {
    public:
        PThreadsMutexImpl(const MutexType type) NOTHROWS;
        ~PThreadsMutexImpl() NOTHROWS;
    public:
        virtual TAKErr lock() NOTHROWS;
        virtual TAKErr unlock() NOTHROWS;
    public:
        TAKErr getMutex(pthread_mutex_t **value)
        {
            if (!mutex.get())
                return TE_IllegalState;
            *value = mutex.get();
            return TE_Ok;
        }
    private:
        std::unique_ptr<pthread_mutex_t, int(*)(pthread_mutex_t *)> mutex;
        std::unique_ptr<pthread_mutexattr_t, int(*)(pthread_mutexattr_t *)> attr;
        pthread_mutex_t mutex_s;
        pthread_mutexattr_t attr_s;

        friend TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS;
    };

    class PThreadsCondVarImpl : public CondVarImpl
    {
    public:
        PThreadsCondVarImpl() NOTHROWS;
    public:
        virtual TAKErr broadcast(MutexImpl &lock) NOTHROWS;
        virtual TAKErr signal(MutexImpl &lock) NOTHROWS;
        virtual TAKErr wait(MutexImpl &lock, const int64_t milliSeconds) NOTHROWS;
    private:
        std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)> impl;
        pthread_cond_t impl_s;
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
        std::unique_ptr<pthread_t, void(*)(pthread_t *)> thread;
        pthread_t thread_s;
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    void *threadCoreRun(void *opaque);
}

/*****************************************************************************/
// CondVar definitions

TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS
{
    value = CondVarImplPtr(new PThreadsCondVarImpl(), Memory_deleter_const<CondVarImpl, PThreadsCondVarImpl>);
    // XXX - could check initialization here

    return TE_Ok;
}

/*****************************************************************************/
// Mutex definitions

TAKErr TAK::Engine::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) NOTHROWS
{
    std::auto_ptr<PThreadsMutexImpl> retval(new PThreadsMutexImpl(type));
    // XXX - could check initialization here
    value = MutexImplPtr(retval.release(), Memory_deleter_const<MutexImpl, PThreadsMutexImpl>);
    return TE_Ok;
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
    return ThreadID(std::move(ThreadIDImplPtr(new PThreadsThreadIDImpl(pthread_self()), Memory_deleter_const<ThreadIDImpl, PThreadsThreadIDImpl>)));
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

    PThreadsThreadIDImpl::PThreadsThreadIDImpl(const pthread_t &tid_) NOTHROWS :
        tid(tid_)
    {}

    void PThreadsThreadIDImpl::clone(ThreadIDImplPtr &value) NOTHROWS
    {
        value = ThreadIDImplPtr(new PThreadsThreadIDImpl(tid), Memory_deleter_const<ThreadIDImpl, PThreadsThreadIDImpl>);
    }

    bool PThreadsThreadIDImpl::operator==(const ThreadIDImpl &other) const NOTHROWS
    {
        return pthread_equal(tid, static_cast<const PThreadsThreadIDImpl &>(other).tid);
    }

    PThreadsCondVarImpl::PThreadsCondVarImpl() NOTHROWS :
        impl(NULL, NULL)
    {
        int err = pthread_cond_init(&impl_s, NULL);
        if (!err)
            impl = std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)>(&impl_s, pthread_cond_destroy);
    }

    TAKErr PThreadsCondVarImpl::broadcast(MutexImpl &lock) NOTHROWS
    {
        if (!impl.get())
            return TE_IllegalState;
        int err = pthread_cond_broadcast(impl.get());
        if (err)
            return TE_Err;
        return TE_Ok;
    }

    TAKErr PThreadsCondVarImpl::signal(MutexImpl &lock) NOTHROWS
    {
        if (!impl.get())
            return TE_IllegalState;
        int err = pthread_cond_signal(impl.get());
        if (err)
            return TE_Err;
        return TE_Ok;
    }

    TAKErr PThreadsCondVarImpl::wait(MutexImpl &mutex, const int64_t milliseconds) NOTHROWS
    {
        TAKErr code(TE_Ok);
        PThreadsMutexImpl &pthreadMutexImpl = static_cast<PThreadsMutexImpl &>(mutex);
        pthread_mutex_t *mutexImpl(NULL);
        code = pthreadMutexImpl.getMutex(&mutexImpl);
        if (code != TE_Ok)
            return code;

        if (!impl.get())
            return TE_IllegalState;
        int err;
        if (milliseconds) {
#ifdef MSVC
            // XXX - I *think* we can do it this way on all platforms, but need
            //       to test on iOS/Android
            int64_t epochWakeup = TAK::Engine::Port::Platform_systime_millis() + milliseconds;
            struct timespec ts;
            ts.tv_sec = (epochWakeup / 1000LL);
            ts.tv_nsec = ((epochWakeup % 1000LL) * 1000000LL);
#else
            // derived from https://stackoverflow.com/questions/17166083/how-to-use-pthread-cond-timedwait-with-millisecond
            struct timespec ts;
            struct timeval tp;
            int rc = gettimeofday(&tp, NULL);

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
            return (err == ETIMEDOUT) ? TE_TimedOut : TE_Err;
        return TE_Ok;
    }

    // PThreadsMutexImpl

    PThreadsMutexImpl::PThreadsMutexImpl(const MutexType type) NOTHROWS :
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

    PThreadsMutexImpl::~PThreadsMutexImpl()
    {
        mutex.reset();
        attr.reset();
    }

    TAKErr PThreadsMutexImpl::lock() NOTHROWS
    {
        if (!mutex.get())
            return TE_IllegalState;
        int err = pthread_mutex_lock(mutex.get());
        if (err)
            return TE_Err;
        return TE_Ok;
    }

    TAKErr PThreadsMutexImpl::unlock() NOTHROWS
    {
        if (!mutex.get())
            return TE_IllegalState;
        int err = pthread_mutex_unlock(mutex.get());
        if (err)
            return TE_Err;
        return TE_Ok;
    }

    // ThreadImpl
    inline ThreadImpl::ThreadImpl() NOTHROWS :
        thread(NULL, NULL)
    {}

    ThreadImpl::~ThreadImpl() NOTHROWS
    {
        join();
    }

    inline TAKErr ThreadImpl::start(void *(*entry)(void *), void *opaque) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (thread.get())
            return TE_IllegalState;

        core.reset(new ThreadRuntimeCore());
        core->entry = entry;
        core->opaque = opaque;
        int err = pthread_create(&thread_s, NULL, threadCoreRun, new std::shared_ptr<ThreadRuntimeCore>(core));
        if (err)
            return TE_Err;

        thread = std::unique_ptr<pthread_t, void(*)(pthread_t *)>(&thread_s, Memory_leaker<pthread_t>);

        return code;
    }

    inline TAKErr ThreadImpl::join(const int64_t millis) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (!thread.get())
            return TE_IllegalState;

        if (!millis) {
            void *result;
            int err = pthread_join(*thread, &result);
            thread.reset();
            core.reset();
            if (err)
                return TE_Err;
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

    inline TAKErr ThreadImpl::detach() NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (!thread.get())
            return TE_IllegalState;

        int err = pthread_detach(*thread);
        thread.reset();
        core.reset();
        if (err)
            return TE_Err;

        return code;
    }

    void ThreadImpl::getID(ThreadIDImplPtr &value) const NOTHROWS
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
        core->started = true;
        return core->entry(core->opaque);
    }
}
