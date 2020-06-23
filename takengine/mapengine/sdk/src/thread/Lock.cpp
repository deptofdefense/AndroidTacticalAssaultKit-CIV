#include "thread/Lock.h"

#include "thread/impl/LockImpl.h"
#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Thread::Impl;
using namespace TAK::Engine::Util;


Lock::Lock(std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> &&impl_) NOTHROWS :
    impl(std::move(impl_))
{}

Lock::~Lock() NOTHROWS
{}

ENGINE_API TAKErr TAK::Engine::Thread::Lock_create(LockPtr &value, Mutex &mutex) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!mutex.impl.get())
        return TE_InvalidArg;

    LockImplPtr impl(NULL, NULL);
    code = LockImpl_create(impl, *mutex.impl);
    TE_CHECKRETURN_CODE(code);

    value = LockPtr(new Lock(std::move(impl)), Memory_deleter_const<Lock>);
    return code;
}
