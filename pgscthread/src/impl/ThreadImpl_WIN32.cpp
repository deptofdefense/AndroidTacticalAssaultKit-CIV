#include "Thread.h"
#include "CondImpl.h"
#include "LockImpl.h"
#include "MutexImpl.h"
#include "ThreadImpl.h"

#include <windows.h>

using namespace PGSC::Thread;
using namespace PGSC::Thread::Impl;

using namespace PGSC::Util;

namespace
{
    class Windows32ThreadIDImpl : public ThreadIDImpl
    {
    public:
        Windows32ThreadIDImpl(const DWORD tid) PGSCT_NOTHROWS;
    public:
        virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) PGSCT_NOTHROWS;
    public:
        virtual bool operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS;
    private :
        DWORD tid;
    };

    class Windows32MutexImpl : public MutexImpl
    {
    public:
        Windows32MutexImpl() PGSCT_NOTHROWS;
        ~Windows32MutexImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr lock() PGSCT_NOTHROWS;
        virtual ThreadErr unlock() PGSCT_NOTHROWS;
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

        friend ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS;
        friend ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS;
    };

    class Windows32LockImpl : public LockImpl
    {
    public:
        Windows32LockImpl(Windows32MutexImpl &mutex) PGSCT_NOTHROWS;
        ~Windows32LockImpl() PGSCT_NOTHROWS;
    private:
        Windows32MutexImpl &mutex;

        friend class Windows32CondVarImpl;
    };

    class Windows32CondVarImpl : public CondVarImpl
    {
    public:
        Windows32CondVarImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr broadcast(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr signal(LockImpl &lock) PGSCT_NOTHROWS;
        virtual ThreadErr wait(LockImpl &lock, const int64_t milliSeconds) PGSCT_NOTHROWS;
    private:
        CONDITION_VARIABLE impl;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl() PGSCT_NOTHROWS;
        ~ThreadImpl() PGSCT_NOTHROWS;
    public:
        virtual ThreadErr start(void *(*entry)(void *), void *opaque,
                                const ThreadCreateParams &params) PGSCT_NOTHROWS;
    public:
        virtual ThreadErr join(const int64_t millis = 0LL) PGSCT_NOTHROWS;
        virtual ThreadErr detach() PGSCT_NOTHROWS;
    private :
        virtual void getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS;
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

ThreadErr PGSC::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS
{
    value = CondVarImplPtr(new Windows32CondVarImpl(), Memory_deleter_const<CondVarImpl, Windows32CondVarImpl>);

    return Thread_Ok;
}

/*****************************************************************************/
// Lock definitions

ThreadErr PGSC::Thread::Impl::LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    Windows32MutexImpl &mutexImpl = static_cast<Windows32MutexImpl &>(mutex);

    code = mutexImpl.lock();
    if (code != Thread_Ok)
        return code;

    value = LockImplPtr(new Windows32LockImpl(mutexImpl), Memory_deleter_const<LockImpl, Windows32LockImpl>);

    return code;
}

/*****************************************************************************/
// Mutex definitions

ThreadErr PGSC::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    // CRITICAL_SECTION is always recursive
    value = MutexImplPtr(new Windows32MutexImpl(), Memory_deleter_const<MutexImpl, Windows32MutexImpl>);

    return code;
}

/*****************************************************************************/
// Thread definitions

ThreadErr PGSC::Thread::Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    std::auto_ptr<ThreadImpl> retval(new ThreadImpl());
    code = retval->start(entry, opaque, params);
    if (code != Thread_Ok)
        return code;

    value = ThreadPtr(retval.release(), Memory_deleter_const<Thread, ThreadImpl>);
    return code;
}

PGSCTHREAD_API ThreadID PGSC::Thread::Thread_currentThreadID() PGSCT_NOTHROWS
{
    return ThreadID(std::move(ThreadIDImplPtr(new Windows32ThreadIDImpl(GetCurrentThreadId()), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>)));
}

/*****************************************************************************/

namespace
{

Windows32ThreadIDImpl::Windows32ThreadIDImpl(const DWORD tid_) PGSCT_NOTHROWS :
    tid(tid_)
{}

void Windows32ThreadIDImpl::clone(ThreadIDImplPtr &value) PGSCT_NOTHROWS
{
    value = ThreadIDImplPtr(new Windows32ThreadIDImpl(tid), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>);
}

bool Windows32ThreadIDImpl::operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS
{
    return (tid == static_cast<const Windows32ThreadIDImpl &>(other).tid);
}

Windows32CondVarImpl::Windows32CondVarImpl() PGSCT_NOTHROWS
{
    InitializeConditionVariable(&impl); 
}

ThreadErr Windows32CondVarImpl::broadcast(LockImpl &lock) PGSCT_NOTHROWS
{
    WakeAllConditionVariable(&impl);
    return ThreadErr::Thread_Ok;

}

ThreadErr Windows32CondVarImpl::signal(LockImpl &lock) PGSCT_NOTHROWS
{
    WakeConditionVariable(&impl);
    return ThreadErr::Thread_Ok;
}

ThreadErr Windows32CondVarImpl::wait(LockImpl &lock, const int64_t milliseconds) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    CRITICAL_SECTION *cs;
    code = static_cast<Windows32LockImpl &>(lock).mutex.getMutex(&cs);
    if (code != Thread_Ok)
        return code;

    int ret =
    SleepConditionVariableCS(&impl,
    cs
    , milliseconds ? milliseconds : INFINITE);

    if (ret == 0){
        printf("Sleep timed out or failed\n ");
        return ThreadErr::Thread_Err;
    }
    return ThreadErr::Thread_Ok;
}

// Windows32LockImpl

Windows32LockImpl::Windows32LockImpl(Windows32MutexImpl &mutex_) PGSCT_NOTHROWS :
    mutex(mutex_)
{}

Windows32LockImpl::~Windows32LockImpl() PGSCT_NOTHROWS
{
    mutex.unlock();
}

// Windows32MutexImpl

Windows32MutexImpl::Windows32MutexImpl() PGSCT_NOTHROWS :
    mutex(new CRITICAL_SECTION(), critical_section_deleter)
{
    // NOTE: per MSDN, this function always succeeds with Windows Vista and
    // later
    InitializeCriticalSection(mutex.get());
}

Windows32MutexImpl::~Windows32MutexImpl()
{}
ThreadErr Windows32MutexImpl::lock() PGSCT_NOTHROWS
{
    if (!mutex.get())
        return Thread_IllegalState;
    EnterCriticalSection(mutex.get());
    return Thread_Ok;
}

ThreadErr Windows32MutexImpl::unlock() PGSCT_NOTHROWS
{
    if (!mutex.get())
        return Thread_IllegalState;
    LeaveCriticalSection(mutex.get());
    return Thread_Ok;
}

void Windows32MutexImpl::critical_section_deleter(CRITICAL_SECTION *value)
{
    if (!value)
        return;
    DeleteCriticalSection(value);
    delete value;
}

// ThreadImpl
inline ThreadImpl::ThreadImpl() PGSCT_NOTHROWS: handle(NULL, NULL)
{
}

ThreadImpl::~ThreadImpl() PGSCT_NOTHROWS
{
    join();
}

inline ThreadErr ThreadImpl::start(void *(*entry)(void *), void *opaque, const ThreadCreateParams &params) PGSCT_NOTHROWS
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

inline ThreadErr ThreadImpl::join(const int64_t millis) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    if (!handle.get())
        return Thread_IllegalState;

    DWORD result = WaitForSingleObject(handle.get(), millis ? millis : INFINITE);
    if (result != WAIT_OBJECT_0){
        return Thread_Err;
    }

    handle.reset();

    return code;
}

inline ThreadErr ThreadImpl::detach() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    if (!handle.get())
        return Thread_IllegalState;

    handle.reset();
    return code;
}

void ThreadImpl::getID(ThreadIDImplPtr &value) const PGSCT_NOTHROWS
{
    if (!handle.get())
        value = ThreadIDImplPtr(NULL, NULL);
    else
        value = ThreadIDImplPtr(new Windows32ThreadIDImpl(ThreadID), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>);
}

void ThreadImpl::handle_deleter(HANDLE value)
{
    if (!value)
        return;
    CloseHandle(value);
}
}
