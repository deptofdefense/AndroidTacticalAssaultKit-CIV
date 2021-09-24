#include "platformthread.h"

using namespace atakmap::commoncommo::impl::thread;

/*****************************************************************************/
// CondVar definitions

PlatformCondVar::PlatformCondVar()
{}

PlatformCondVar::~PlatformCondVar()
{}

/*****************************************************************************/
// PlatformMutex definitions

PlatformMutex::PlatformMutex()
{}

PlatformMutex::~PlatformMutex()
{}

/*****************************************************************************/
// Thread definitions

ThreadID::ThreadID(PlatformThreadIDPtr &&impl_) :
    impl(std::move(impl_))
{}

ThreadID::ThreadID() :
    impl()
{}

ThreadID::ThreadID(const ThreadID &other) :
    impl()
{
    if (other.impl.get())
        other.impl->clone(impl);
}
    
ThreadID::~ThreadID()
{}

bool ThreadID::operator==(const ThreadID &other) const
{
    return (this->impl.get() && other.impl.get() && (*this->impl == *other.impl));
}

bool ThreadID::operator!=(const ThreadID &other) const
{
    return !(*this == other);
}

ThreadID &ThreadID::operator=(const ThreadID &other)
{
    if (!other.impl.get())
        this->impl.reset();
    else
        other.impl->clone(this->impl);
    return *this;
}

Thread::Thread()
{}

Thread::~Thread()
{}

ThreadErr Thread::sleep(const int64_t milliseconds)
{
    if (milliseconds <= 0)
        return Thread_Ok;
    ThreadErr code(Thread_Ok);
    Monitor monitor;
	MonitorLockPtr lock;
    code = MonitorLock::create(lock, monitor);
    THREAD_CHECKRETURN_CODE(code);
    code = lock->wait(milliseconds);
    if (code == Thread_TimedOut) // we are expecting timeout
        code = Thread_Ok;
    THREAD_CHECKRETURN_CODE(code);
    return code;
}

ThreadID Thread::getID() const
{
	PlatformThreadIDPtr id;
    this->getID(id);
    return ThreadID(std::move(id));
}

Thread::CreateParams::CreateParams() :
    name(), priority(Priority_Normal)
{}

PlatformThreadID::PlatformThreadID()
{}

PlatformThreadID::~PlatformThreadID()
{}




#ifdef WIN32

/*************************************************************************/
// Win32 implementations

#include <windows.h>

namespace
{
    class Windows32PlatformThreadID : public PlatformThreadID
    {
    public:
        Windows32PlatformThreadID(const DWORD tid);
    public:
        virtual void clone(PlatformThreadIDPtr &value);
    public:
        virtual bool operator==(const PlatformThreadID &other) const;
    private :
        DWORD tid;
    };

    class Windows32PlatformMutex : public PlatformMutex
    {
    public:
        Windows32PlatformMutex();
        ~Windows32PlatformMutex();
    public:
        virtual ThreadErr lock();
        virtual ThreadErr unlock();
    public:
        ThreadErr getMutex(CRITICAL_SECTION **value)
        {
            if (!mutex.get())
                return Thread_IllegalState;
            *value = mutex.get();
            return Thread_Ok;
        }
    private :
        static void critical_section_deleter(CRITICAL_SECTION *pointer);
    private:
        std::unique_ptr<CRITICAL_SECTION, void(*)(CRITICAL_SECTION *)> mutex;
    };

    class Windows32PlatformCondVar : public PlatformCondVar
    {
    public:
        Windows32PlatformCondVar();
    public:
        virtual ThreadErr broadcast(PlatformMutex &m);
        virtual ThreadErr signal(PlatformMutex &m);
        virtual ThreadErr wait(PlatformMutex &m, const int64_t milliSeconds);
    private:
        CONDITION_VARIABLE impl;
    };

    class Windows32Thread : public Thread
    {
    public:
        Windows32Thread();
        ~Windows32Thread();
    public:
        virtual ThreadErr start(void *(*entry)(void *), void *opaque,
                                const Thread::CreateParams &params);
    public:
        virtual ThreadErr join(const int64_t millis = 0LL);
        virtual ThreadErr detach();
    private :
        virtual void getID(PlatformThreadIDPtr &value) const;
    private :
        static void handle_deleter(HANDLE);
    private:
        std::unique_ptr<void, void(*)(HANDLE)> handle;
        DWORD ThreadID;
    };

    // Thread name is copied internally so no need to preserve.
    // Taken from https://msdn.microsoft.com/en-us/library/xcb2z8hs.aspx
    //  
    const DWORD MS_VC_EXCEPTION = 0x406D1388;  
#pragma pack(push,8)  
    typedef struct tagTHREADNAME_INFO  
    {  
        DWORD dwType; // Must be 0x1000.  
        LPCSTR szName; // Pointer to name (in user addr space).  
        DWORD dwThreadID; // Thread ID (-1=caller thread).  
        DWORD dwFlags; // Reserved for future use, must be zero.  
     } THREADNAME_INFO;  
#pragma pack(pop)  
    void SetThreadName(DWORD dwThreadID, const char* threadName) {  
        THREADNAME_INFO info;  
        info.dwType = 0x1000;  
        info.szName = threadName;  
        info.dwThreadID = dwThreadID;  
        info.dwFlags = 0;  
#pragma warning(push)  
#pragma warning(disable: 6320 6322)  
        __try{  
            RaiseException(MS_VC_EXCEPTION, 0, sizeof(info) / sizeof(ULONG_PTR), (ULONG_PTR*)&info);  
        }  
        __except (EXCEPTION_EXECUTE_HANDLER){  
        }  
#pragma warning(pop)  
    }  

}

/*****************************************************************************/
// CondVar definitions

ThreadErr PlatformCondVar::create(PlatformCondVarPtr &value)
{
    value = PlatformCondVarPtr(new Windows32PlatformCondVar());

    return Thread_Ok;
}

/*****************************************************************************/
// Mutex definitions

ThreadErr PlatformMutex::create(PlatformMutexPtr &value, const Mutex::Type type)
{
    ThreadErr code(Thread_Ok);

    // CRITICAL_SECTION is always recursive
    value = PlatformMutexPtr(new Windows32PlatformMutex());

    return code;
}

/*****************************************************************************/
// Thread definitions

ThreadErr Thread::start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const Thread::CreateParams params)
{
    ThreadErr code(Thread_Ok);

    std::auto_ptr<Windows32Thread> retval(new Windows32Thread());
    code = retval->start(entry, opaque, params);
    if (code != Thread_Ok)
        return code;

    value = ThreadPtr(retval.release());
    return code;
}

ThreadID ThreadID::currentThreadID()
{
    return ThreadID(std::move(PlatformThreadIDPtr(new Windows32PlatformThreadID(GetCurrentThreadId()))));
}

/*****************************************************************************/

namespace
{

Windows32PlatformThreadID::Windows32PlatformThreadID(const DWORD tid_) :
    tid(tid_)
{}

void Windows32PlatformThreadID::clone(PlatformThreadIDPtr &value)
{
    value = PlatformThreadIDPtr(new Windows32PlatformThreadID(tid));
}

bool Windows32PlatformThreadID::operator==(const PlatformThreadID &other) const
{
    return (tid == static_cast<const Windows32PlatformThreadID &>(other).tid);
}

Windows32PlatformCondVar::Windows32PlatformCondVar()
{
    InitializeConditionVariable(&impl); 
}

ThreadErr Windows32PlatformCondVar::broadcast(PlatformMutex &lock)
{
    WakeAllConditionVariable(&impl);
    return ThreadErr::Thread_Ok;

}

ThreadErr Windows32PlatformCondVar::signal(PlatformMutex &lock)
{
    WakeConditionVariable(&impl);
    return ThreadErr::Thread_Ok;
}

ThreadErr Windows32PlatformCondVar::wait(PlatformMutex &mutex, const int64_t milliseconds)
{
    ThreadErr code(Thread_Ok);
    CRITICAL_SECTION *cs;
    code = static_cast<Windows32PlatformMutex &>(mutex).getMutex(&cs);
    if (code != Thread_Ok)
        return code;

	if (milliseconds > static_cast<int64_t>(MAXDWORD) || milliseconds < 0)
		return Thread_Err;

    int ret =
    SleepConditionVariableCS(&impl,
    cs
    , milliseconds ? (DWORD)milliseconds : INFINITE);

    if (ret == 0){
        return (GetLastError() == ERROR_TIMEOUT) ? ThreadErr::Thread_TimedOut : ThreadErr::Thread_Err;
    }
    return ThreadErr::Thread_Ok;
}

// Windows32PlatformMutex

Windows32PlatformMutex::Windows32PlatformMutex() :
    mutex(new CRITICAL_SECTION(), critical_section_deleter)
{
    // NOTE: per MSDN, this function always succeeds with Windows Vista and
    // later
    InitializeCriticalSection(mutex.get());
}

Windows32PlatformMutex::~Windows32PlatformMutex()
{}

ThreadErr Windows32PlatformMutex::lock()
{
    if (!mutex.get())
        return Thread_IllegalState;
    EnterCriticalSection(mutex.get());
    return Thread_Ok;
}

ThreadErr Windows32PlatformMutex::unlock()
{
    if (!mutex.get())
        return Thread_IllegalState;
    LeaveCriticalSection(mutex.get());
    return Thread_Ok;
}

void Windows32PlatformMutex::critical_section_deleter(CRITICAL_SECTION *value)
{
    if (!value)
        return;
    DeleteCriticalSection(value);
    delete value;
}

// Windows32Thread
inline Windows32Thread::Windows32Thread(): handle(NULL, NULL)
{
}

Windows32Thread::~Windows32Thread()
{
    join();
}

inline ThreadErr Windows32Thread::start(void *(*entry)(void *), void *opaque, const Thread::CreateParams &params)
{
    ThreadErr code(Thread_Ok);

    if (handle.get())
        return Thread_IllegalState;

    try {
        handle = std::unique_ptr<void, void(*)(HANDLE)>(CreateThread(
            NULL,       // default security attributes
            0,          // default stack size
            (LPTHREAD_START_ROUTINE)entry,
            opaque,       //thread function arguments
            0,          // run thread immediately
            &ThreadID), // receive thread identifier
            handle_deleter);
        //TODO: Handle Return cases and errors
        if (!handle.get()){
            fprintf(stderr, "Error Creating Thread");
            return Thread_Err;
        }
    } catch (...) {
        return Thread_Err;
    }
    if (params.name.length() > 0)
        SetThreadName(ThreadID, params.name.c_str());

    return code;
}

inline ThreadErr Windows32Thread::join(const int64_t millis)
{
    ThreadErr code(Thread_Ok);

    if (!handle.get())
        return Thread_IllegalState;

	if (millis > static_cast<int64_t>(MAXDWORD) || millis < 0)
		return Thread_Err;

	DWORD result = WaitForSingleObject(handle.get(), millis ? (DWORD)millis : INFINITE);
    if (result != WAIT_OBJECT_0){
        return Thread_Err;
    }

    handle.reset();

    return code;
}

inline ThreadErr Windows32Thread::detach()
{
    ThreadErr code(Thread_Ok);

    if (!handle.get())
        return Thread_IllegalState;

    handle.reset();
    return code;
}

void Windows32Thread::getID(PlatformThreadIDPtr &value) const
{
    if (!handle.get())
        value = PlatformThreadIDPtr();
    else
        value = PlatformThreadIDPtr(new Windows32PlatformThreadID(ThreadID));
}

void Windows32Thread::handle_deleter(HANDLE value)
{
    if (!value)
        return;
    CloseHandle(value);
}

}

#else // WIN32


/*************************************************************************/
// pthreads implementations



#include <ctime>
#include <pthread.h>
#include <sys/time.h>

namespace
{
    class ThreadRuntimeCore
    {
    public:
        ThreadRuntimeCore();
    public:
        /** 'true' if the thread has started execution, 'false' otherwise */
        bool started;
        /** 'true' if the thread has terminated, successfully or otherwise, 'false' otherwise */
        bool terminated;
        /** */
        Monitor monitor;
        Thread::CreateParams params;

        void *(*entry)(void *);
        void *opaque;
    };

    class PThreadsPlatformThreadID : public PlatformThreadID
    {
    public:
        PThreadsPlatformThreadID(const pthread_t &);
        virtual ~PThreadsPlatformThreadID();
    public:
        virtual void clone(PlatformThreadIDPtr &value);
    public:
        virtual bool operator==(const PlatformThreadID &other) const;
    private :
        pthread_t tid;
    };

    class PThreadsPlatformMutex : public PlatformMutex
    {
    public:
        PThreadsPlatformMutex(const Mutex::Type type);
        virtual ~PThreadsPlatformMutex();
    public:
        virtual ThreadErr lock();
        virtual ThreadErr unlock();
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

    };

    class PThreadsPlatformCondVar : public PlatformCondVar
    {
    public:
        PThreadsPlatformCondVar();
        virtual ~PThreadsPlatformCondVar();
    public:
        virtual ThreadErr broadcast(PlatformMutex &lock);
        virtual ThreadErr signal(PlatformMutex &lock);
        virtual ThreadErr wait(PlatformMutex &lock, const int64_t milliSeconds);
    private:
        std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)> impl;
        pthread_cond_t impl_s;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl();
        virtual ~ThreadImpl();
    public:
        virtual ThreadErr start(void *(*entry)(void *), void *opaque, const Thread::CreateParams params);
    public:
        virtual ThreadErr join(const int64_t millis = 0LL);
        virtual ThreadErr detach();
    private :
        virtual void getID(PlatformThreadIDPtr &value) const;
    private:
        pthread_t *thread;
        pthread_t thread_s;
        std::shared_ptr<ThreadRuntimeCore> core;
    };

    void *threadCoreRun(void *opaque);
}

/*****************************************************************************/
// CondVar definitions

ThreadErr PlatformCondVar::create(PlatformCondVarPtr &value)
{
    value = PlatformCondVarPtr(new PThreadsPlatformCondVar());
    // XXX - could check initialization here

    return Thread_Ok;
}

/*****************************************************************************/
// Mutex definitions

ThreadErr PlatformMutex::create(PlatformMutexPtr &value, const Mutex::Type type)
{
    std::unique_ptr<PThreadsPlatformMutex> retval(new PThreadsPlatformMutex(type));
    // XXX - could check initialization here
    value = PlatformMutexPtr(retval.release());
    return Thread_Ok;
}

/*****************************************************************************/
// Thread definitions

ThreadErr Thread::start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const Thread::CreateParams params)
{
    ThreadErr code(Thread_Ok);

    std::unique_ptr<ThreadImpl> retval(new ThreadImpl());
    code = retval->start(entry, opaque, params);
    if (code != Thread_Ok)
        return code;

    value = ThreadPtr(retval.release());
    return code;
}

ThreadID ThreadID::currentThreadID()
{
    return ThreadID(PlatformThreadIDPtr(new PThreadsPlatformThreadID(pthread_self())));
}

/*****************************************************************************/

namespace
{

    ThreadRuntimeCore::ThreadRuntimeCore() :
        started(false),
        terminated(false),
        entry(NULL),
        opaque(NULL)
    {}

    PThreadsPlatformThreadID::PThreadsPlatformThreadID(const pthread_t &tid_) :
        tid(tid_)
    {}

    PThreadsPlatformThreadID::~PThreadsPlatformThreadID()
    {}

    void PThreadsPlatformThreadID::clone(PlatformThreadIDPtr &value)
    {
        value = PlatformThreadIDPtr(new PThreadsPlatformThreadID(tid));
    }

    bool PThreadsPlatformThreadID::operator==(const PlatformThreadID &other) const
    {
        return pthread_equal(tid, static_cast<const PThreadsPlatformThreadID &>(other).tid);
    }

    PThreadsPlatformCondVar::PThreadsPlatformCondVar() :
        impl(NULL, NULL)
    {
        int err = pthread_cond_init(&impl_s, NULL);
        if (!err)
            impl = std::unique_ptr<pthread_cond_t, int(*)(pthread_cond_t *)>(&impl_s, pthread_cond_destroy);
    }

    PThreadsPlatformCondVar::~PThreadsPlatformCondVar()
    {
    }

    ThreadErr PThreadsPlatformCondVar::broadcast(PlatformMutex &lock)
    {
        if (!impl.get())
            return Thread_IllegalState;
        int err = pthread_cond_broadcast(impl.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsPlatformCondVar::signal(PlatformMutex &lock)
    {
        if (!impl.get())
            return Thread_IllegalState;
        int err = pthread_cond_signal(impl.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsPlatformCondVar::wait(PlatformMutex &lock, const int64_t milliseconds)
    {
        ThreadErr code(Thread_Ok);
        PThreadsPlatformMutex &lockImpl = static_cast<PThreadsPlatformMutex &>(lock);
        pthread_mutex_t *mutexImpl(NULL);
        code = lockImpl.getMutex(&mutexImpl);
        if (code != Thread_Ok)
            return code;

        if (!impl.get())
            return Thread_IllegalState;
        int err;
        if (milliseconds) {
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
            err = pthread_cond_timedwait(impl.get(), mutexImpl, &ts);
        } else {
            err = pthread_cond_wait(impl.get(), mutexImpl);
        }
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    // PThreadsPlatformMutex

    PThreadsPlatformMutex::PThreadsPlatformMutex(const Mutex::Type type) :
        mutex(NULL, NULL),
        attr(NULL, NULL)
    {
        int err;
        do {
            int pthreadMutexType;
            switch (type)
            {
            case Mutex::Type_Default :
                pthreadMutexType = PTHREAD_MUTEX_DEFAULT;
                err = 0;
                break;
            case Mutex::Type_Recursive :
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

    PThreadsPlatformMutex::~PThreadsPlatformMutex()
    {
        mutex.reset();
        attr.reset();
    }

    ThreadErr PThreadsPlatformMutex::lock()
    {
        if (!mutex.get())
            return Thread_IllegalState;
        int err = pthread_mutex_lock(mutex.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    ThreadErr PThreadsPlatformMutex::unlock()
    {
        if (!mutex.get())
            return Thread_IllegalState;
        int err = pthread_mutex_unlock(mutex.get());
        if (err)
            return Thread_Err;
        return Thread_Ok;
    }

    // ThreadImpl
    inline ThreadImpl::ThreadImpl() :
        thread(NULL)
    {}

    ThreadImpl::~ThreadImpl()
    {
        join();
    }

    inline ThreadErr ThreadImpl::start(void *(*entry)(void *), void *opaque, const Thread::CreateParams params)
    {
        ThreadErr code(Thread_Ok);

        if (thread)
            return Thread_IllegalState;

        core.reset(new ThreadRuntimeCore());
        core->entry = entry;
        core->opaque = opaque;
        core->params = params;
        int err = pthread_create(&thread_s, NULL, threadCoreRun, new std::shared_ptr<ThreadRuntimeCore>(core));
        if (err)
            return Thread_Err;

        thread = &thread_s;

        return code;
    }

    inline ThreadErr ThreadImpl::join(const int64_t millis)
    {
        ThreadErr code(Thread_Ok);

        if (!thread)
            return Thread_IllegalState;

        if (!millis) {
            void *result;
            int err = pthread_join(*thread, &result);
            thread = NULL;
            core.reset();
            if (err)
                return Thread_Err;
        } else {
            if (!core.get())
                return Thread_IllegalState;

            MonitorLockPtr lock;
            code = MonitorLock::create(lock, core->monitor);
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

    inline ThreadErr ThreadImpl::detach()
    {
        ThreadErr code(Thread_Ok);

        if (!thread)
            return Thread_IllegalState;

        int err = pthread_detach(*thread);
        thread = NULL;
        core.reset();
        if (err)
            return Thread_Err;

        return code;
    }

    void ThreadImpl::getID(PlatformThreadIDPtr &value) const
    {
        if (!thread)
            value = PlatformThreadIDPtr();
        else
            value = PlatformThreadIDPtr(new PThreadsPlatformThreadID(*thread));
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
                MonitorLockPtr lock;
                MonitorLock::create(lock, core->monitor);
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

#endif
