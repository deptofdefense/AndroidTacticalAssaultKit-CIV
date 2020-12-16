#include "thread/Thread.h"
#include "thread/impl/CondImpl.h"
#include "thread/impl/MutexImpl.h"
#include "thread/impl/ThreadImpl.h"

#include <windows.h>

#include <codecvt>
#include <string>

#include "util/Logging2.h"
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
    class Windows32ThreadIDImpl : public ThreadIDImpl
    {
    public:
        Windows32ThreadIDImpl(const DWORD tid) NOTHROWS;
    public:
        void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) NOTHROWS override;
    public:
        bool operator==(const ThreadIDImpl &other) const NOTHROWS override;
    private :
        DWORD tid;
    };

    class Windows32MutexImpl : public MutexImpl
    {
    public:
        Windows32MutexImpl() NOTHROWS;
        ~Windows32MutexImpl() NOTHROWS override;
    public:
        TAKErr lock() NOTHROWS override;
        TAKErr unlock() NOTHROWS override;
    public:
        TAKErr getMutex(CRITICAL_SECTION **value)
        {
            if (!mutex.get())
                return TE_IllegalState;
            *value = mutex.get();
            return TE_Ok;
        }
    private :
        static void critical_section_deleter(CRITICAL_SECTION *pointer);
    private:
        std::unique_ptr<CRITICAL_SECTION, void(*)(CRITICAL_SECTION *)> mutex;

        friend TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS;
    };

    class Windows32CondVarImpl : public CondVarImpl
    {
    public:
        Windows32CondVarImpl() NOTHROWS;
    public:
        TAKErr broadcast(MutexImpl &lock) NOTHROWS override;
        TAKErr signal(MutexImpl &lock) NOTHROWS override;
        TAKErr wait(MutexImpl &lock, const int64_t milliSeconds) NOTHROWS override;
    private:
        CONDITION_VARIABLE impl;
    };

    class ThreadImpl : public Thread
    {
    public:
        ThreadImpl() NOTHROWS;
        ThreadImpl(TAK::Engine::Port::String name) NOTHROWS;
        ~ThreadImpl() NOTHROWS override;
    public:
        virtual TAKErr start(void *(*entry)(void *), void *opaque) NOTHROWS;
    public:
        TAKErr join(const int64_t millis = 0LL) NOTHROWS override;
        TAKErr detach() NOTHROWS override;
    private :
        void getID(ThreadIDImplPtr &value) const NOTHROWS override;
    private :
        static void handle_deleter(HANDLE);
    private:
        std::unique_ptr<void, void(*)(HANDLE)> handle_;
        TAK::Engine::Port::String name_;
        DWORD thread_id_;
    };

}

/*****************************************************************************/
// CondVar definitions

TAKErr TAK::Engine::Thread::Impl::CondVarImpl_create(CondVarImplPtr &value) NOTHROWS
{
    value = CondVarImplPtr(new Windows32CondVarImpl(), Memory_deleter_const<CondVarImpl, Windows32CondVarImpl>);

    return TE_Ok;
}

/*****************************************************************************/
// Mutex definitions

TAKErr TAK::Engine::Thread::Impl::MutexImpl_create(MutexImplPtr &value, const MutexType type) NOTHROWS
{
    TAKErr code(TE_Ok);

    // CRITICAL_SECTION is always recursive
    value = MutexImplPtr(new Windows32MutexImpl(), Memory_deleter_const<MutexImpl, Windows32MutexImpl>);

    return code;
}

/*****************************************************************************/
// Thread definitions

TAKErr TAK::Engine::Thread::Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::unique_ptr<ThreadImpl> retval(new ThreadImpl(params.name));
    code = retval->start(entry, opaque);
    if (code != TE_Ok)
        return code;

    value = ThreadPtr(retval.release(), Memory_deleter_const<Thread, ThreadImpl>);
    return code;
}

ThreadID TAK::Engine::Thread::Thread_currentThreadID() NOTHROWS
{
    return ThreadID(std::move(ThreadIDImplPtr(new Windows32ThreadIDImpl(GetCurrentThreadId()), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>)));
}

/*****************************************************************************/

namespace
{

Windows32ThreadIDImpl::Windows32ThreadIDImpl(const DWORD tid_) NOTHROWS :
    tid(tid_)
{}

void Windows32ThreadIDImpl::clone(ThreadIDImplPtr &value) NOTHROWS
{
    value = ThreadIDImplPtr(new Windows32ThreadIDImpl(tid), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>);
}

bool Windows32ThreadIDImpl::operator==(const ThreadIDImpl &other) const NOTHROWS
{
    return (tid == static_cast<const Windows32ThreadIDImpl &>(other).tid);
}

Windows32CondVarImpl::Windows32CondVarImpl() NOTHROWS
{
    InitializeConditionVariable(&impl); 
}

TAKErr Windows32CondVarImpl::broadcast(MutexImpl &ignored) NOTHROWS
{
    WakeAllConditionVariable(&impl);
    return TAKErr::TE_Ok;

}

TAKErr Windows32CondVarImpl::signal(MutexImpl &ignored) NOTHROWS
{
    WakeConditionVariable(&impl);
    return TAKErr::TE_Ok;
}

TAKErr Windows32CondVarImpl::wait(MutexImpl &mutex, const int64_t milliseconds) NOTHROWS
{
    TAKErr code(TE_Ok);
    CRITICAL_SECTION *cs;
    code = static_cast<Windows32MutexImpl &>(mutex).getMutex(&cs);
    if (code != TE_Ok)
        return code;

    int ret =
    SleepConditionVariableCS(&impl,
    cs
    , static_cast<DWORD>(milliseconds ? milliseconds : INFINITE));

    if (ret == 0){
        printf("Sleep timed out or failed\n ");
        return TAKErr::TE_Err;
    }
    return TAKErr::TE_Ok;
}

// Windows32MutexImpl

Windows32MutexImpl::Windows32MutexImpl() NOTHROWS :
    mutex(new CRITICAL_SECTION(), critical_section_deleter)
{
    // NOTE: per MSDN, this function always succeeds with Windows Vista and
    // later
    InitializeCriticalSection(mutex.get());
}

Windows32MutexImpl::~Windows32MutexImpl()
{}
TAKErr Windows32MutexImpl::lock() NOTHROWS
{
    if (!mutex.get())
        return TE_IllegalState;
    EnterCriticalSection(mutex.get());
    return TE_Ok;
}

TAKErr Windows32MutexImpl::unlock() NOTHROWS
{
    if (!mutex.get())
        return TE_IllegalState;
    LeaveCriticalSection(mutex.get());
    return TE_Ok;
}

void Windows32MutexImpl::critical_section_deleter(CRITICAL_SECTION *value)
{
    if (!value)
        return;
    DeleteCriticalSection(value);
    delete value;
}

// ThreadImpl
inline ThreadImpl::ThreadImpl() NOTHROWS : handle_(nullptr, nullptr), name_(nullptr), thread_id_(0) {}

inline ThreadImpl::ThreadImpl(const TAK::Engine::Port::String name) NOTHROWS : handle_(nullptr, nullptr), name_(name), thread_id_(0) {}

ThreadImpl::~ThreadImpl() NOTHROWS
{
    join();
}

inline TAKErr ThreadImpl::start(void *(*entry)(void *), void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (handle_.get())
        return TE_IllegalState;

    try {
        handle_ = std::unique_ptr<void, void(*)(HANDLE)>(CreateThread(
            nullptr,       // default security attributes
            0,          // default stack size
            (LPTHREAD_START_ROUTINE)entry,
            opaque,       //thread function arguments
            0,          // run thread immediately
            &thread_id_), // receive thread identifier
            handle_deleter);
        //TODO: Handle Return cases and errors
        if (!handle_.get()){
            fprintf(stderr, "Error Creating Thread");
            return TE_Err;
        }
        if (name_) {
            using convert_type = std::codecvt_utf8<wchar_t>;
            std::wstring_convert<convert_type, wchar_t> converter;
            std::wstring wname = converter.from_bytes(name_.get());
            SetThreadDescription(handle_.get(), (PCWSTR)wname.c_str());
        }
    } catch (...) {
        return TE_Err;
    }

    return code;
}

inline TAKErr ThreadImpl::join(const int64_t millis) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!handle_.get())
        return TE_IllegalState;

    DWORD result = WaitForSingleObject(handle_.get(), static_cast<DWORD>(millis ? millis : INFINITE));
    if (result != WAIT_OBJECT_0){
        return TE_Err;
    }

    handle_.reset();

    return code;
}

inline TAKErr ThreadImpl::detach() NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!handle_.get())
        return TE_IllegalState;

    handle_.reset();
    return code;
}

void ThreadImpl::getID(ThreadIDImplPtr &value) const NOTHROWS
{
    if (!handle_.get())
        value = ThreadIDImplPtr(nullptr, nullptr);
    else
        value = ThreadIDImplPtr(new Windows32ThreadIDImpl(thread_id_), Memory_deleter_const<ThreadIDImpl, Windows32ThreadIDImpl>);
}

void ThreadImpl::handle_deleter(HANDLE value)
{
    if (!value)
        return;
    CloseHandle(value);
}
}
