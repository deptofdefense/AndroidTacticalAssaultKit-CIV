#include "Cond.h"

#include "impl/CondImpl.h"

using namespace PGSC::Thread;

using namespace PGSC::Thread::Impl;
using namespace PGSC::Util;

CondVar::CondVar() PGSCT_NOTHROWS :
    impl(NULL, NULL)
{
    if (CondVarImpl_create(impl) != Thread_Ok)
        impl.reset();
}

CondVar::~CondVar() PGSCT_NOTHROWS
{}

ThreadErr CondVar::broadcast(Lock &lock) PGSCT_NOTHROWS
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->broadcast(*lock.impl);
}

ThreadErr CondVar::signal(Lock &lock) PGSCT_NOTHROWS
{
    if (!impl.get())
        return Thread_IllegalState;
    return impl->signal(*lock.impl);
}
                
ThreadErr CondVar::wait(Lock &lock, const int64_t milliseconds) PGSCT_NOTHROWS
{
    if (!impl.get())
    return Thread_IllegalState;
    if (!lock.impl.get())
        return Thread_IllegalState;
    return impl->wait(*lock.impl, milliseconds);
}
