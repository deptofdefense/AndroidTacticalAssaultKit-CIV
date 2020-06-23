#include "Lock.h"

#include "impl/LockImpl.h"

using namespace PGSC::Thread;

using namespace PGSC::Thread::Impl;
using namespace PGSC::Util;


Lock::Lock(std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> &&impl_) PGSCT_NOTHROWS :
    impl(std::move(impl_))
{}

Lock::~Lock() PGSCT_NOTHROWS
{}

PGSCTHREAD_API ThreadErr PGSC::Thread::Lock_create(LockPtr &value, Mutex &mutex) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);

    LockImplPtr impl(NULL, NULL);
    code = LockImpl_create(impl, *mutex.impl);
    THREAD_CHECKRETURN_CODE(code);

    value = LockPtr(new Lock(std::move(impl)), Memory_deleter_const<Lock>);
    return code;
}
