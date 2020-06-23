#include "thread/Cond.h"

#include "thread/impl/CondImpl.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Thread::Impl;
using namespace TAK::Engine::Util;

CondVar::CondVar() NOTHROWS :
    impl(NULL, NULL)
{
    if (CondVarImpl_create(impl) != TE_Ok)
        impl.reset();
}

CondVar::~CondVar() NOTHROWS
{}

TAKErr CondVar::broadcast(Lock &lock) NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;
    return impl->broadcast(*lock.impl);
}

TAKErr CondVar::signal(Lock &lock) NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;
    return impl->signal(*lock.impl);
}
                
TAKErr CondVar::wait(Lock &lock, const int64_t milliseconds) NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;
    if (!lock.impl.get())
        return TE_IllegalState;
    return impl->wait(*lock.impl, milliseconds);
}
