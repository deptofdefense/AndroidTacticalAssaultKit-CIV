#include "thread/ThreadPool.h"

#include "util/Memory.h"

using namespace TAK::Engine::Thread;

using namespace TAK::Engine::Thread::Impl;
using namespace TAK::Engine::Util;

ThreadPool::ThreadPool() NOTHROWS
{
}
ThreadPool::~ThreadPool() NOTHROWS
{
    joinAll();
}
TAKErr ThreadPool::detachAll() NOTHROWS
{
    TAKErr rc(TE_Ok);

    Lock lock(threadsMutex);
    rc = lock.status;
    TE_CHECKRETURN_CODE(rc);

    std::set<ThreadPtr>::iterator i;
    for (i = threads.begin(); i != threads.end(); ++i){
        TAKErr te = (*i)->detach();
        if (te != TE_Ok)
            rc = te;
    }
    threads.clear();
    TE_CHECKRETURN_CODE(rc);

    return TE_Ok;
}
TAKErr ThreadPool::joinAll() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(threadsMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<ThreadPtr>::iterator i;
    for (i = threads.begin(); i != threads.end(); ++i){
        TAKErr te = (*i)->join();
        TE_CHECKRETURN_CODE(te)
    }
    threads.clear();
    return TE_Ok;
}
TAKErr ThreadPool::initPool(const std::size_t threadCount, void *(*entry)(void *), void* threadData) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(threadsMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!threads.empty())
        return TE_IllegalState;

    for (std::size_t i = 0u; i < threadCount; ++i){
        ThreadPtr thread(nullptr, nullptr);
        code = Thread_start(thread, entry, threadData);
        TE_CHECKBREAK_CODE(code);
        threads.insert(std::move(thread));
    }
    if (code != TE_Ok)
        detachAll();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Thread::ThreadPool_create(ThreadPoolPtr &value, const std::size_t threadCount, void *(*entry)(void *), void* threadData) NOTHROWS
{
    TAKErr code(TE_Ok);
    ThreadPoolPtr pool(new ThreadPool(), Memory_deleter_const<ThreadPool>);
    code = pool->initPool(threadCount, entry, threadData);
    TE_CHECKRETURN_CODE(code);

    value = std::move(pool);
    return code;
}
