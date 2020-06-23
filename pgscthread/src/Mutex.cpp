#include "Mutex.h"

#include "Lock.h"
#include "impl/MutexImpl.h"

using namespace PGSC::Thread;

using namespace PGSC::Thread::Impl;
using namespace PGSC::Util;

// Mutex

Mutex::Mutex(const MutexType type) PGSCT_NOTHROWS :
    impl(NULL, NULL)
{
    if (MutexImpl_create(impl, type) != Thread_Ok)
        impl.reset();
}
Mutex::~Mutex() PGSCT_NOTHROWS
{}
ThreadErr Mutex::lock() PGSCT_NOTHROWS
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->lock();
}
ThreadErr Mutex::unlock() PGSCT_NOTHROWS
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->unlock();
}
