#include "thread/Mutex.h"

#include "thread/Lock.h"
#include "thread/impl/MutexImpl.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Thread::Impl;
using namespace TAK::Engine::Util;

// Mutex

Mutex::Mutex(const MutexType type) NOTHROWS :
    impl(nullptr, nullptr)
{
    if (MutexImpl_create(impl, type) != TE_Ok)
        impl.reset();
}
Mutex::~Mutex() NOTHROWS
{}
TAKErr Mutex::lock() NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;
    return impl->lock();
}
TAKErr Mutex::unlock() NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;
    return impl->unlock();
}
