#include "ThreadPool.h"

using namespace PGSC::Thread;

using namespace PGSC::Thread::Impl;
using namespace PGSC::Util;

ThreadPool::ThreadPool() PGSCT_NOTHROWS
{
}
ThreadPool::~ThreadPool() PGSCT_NOTHROWS
{
    joinAll();
}
ThreadErr ThreadPool::detachAll() PGSCT_NOTHROWS
{
    ThreadErr rc(Thread_Ok);

    LockPtr lock(NULL, NULL);
    rc = Lock_create(lock, threadsMutex);
    THREAD_CHECKRETURN_CODE(rc);

    std::set<ThreadPtr>::iterator i;
    for (i = threads.begin(); i != threads.end(); ++i){
        ThreadErr te = (*i)->detach();
        if (te != Thread_Ok)
            rc = te;
    }
    threads.clear();
    THREAD_CHECKRETURN_CODE(rc);

    return Thread_Ok;
}
ThreadErr ThreadPool::joinAll() PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, threadsMutex);
    THREAD_CHECKRETURN_CODE(code);

    std::set<ThreadPtr>::iterator i;
    for (i = threads.begin(); i != threads.end(); ++i){
        ThreadErr te = (*i)->join();
        THREAD_CHECKRETURN_CODE(te)
    }
    threads.clear();
    return Thread_Ok;
}
ThreadErr ThreadPool::initPool(const std::size_t threadCount, void *(*entry)(void *), void* threadData) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, threadsMutex);
    THREAD_CHECKRETURN_CODE(code);

    if (!threads.empty())
        return Thread_IllegalState;

    for (std::size_t i = 0u; i < threadCount; ++i){
        ThreadPtr thread(NULL, NULL);
        code = Thread_start(thread, entry, threadData);
        THREAD_CHECKBREAK_CODE(code);
        threads.insert(std::move(thread));
    }
    if (code != Thread_Ok)
        detachAll();
    THREAD_CHECKRETURN_CODE(code);

    return code;
}

PGSCTHREAD_API ThreadErr PGSC::Thread::ThreadPool_create(ThreadPoolPtr &value, const std::size_t threadCount, void *(*entry)(void *), void* threadData) PGSCT_NOTHROWS
{
    ThreadErr code(Thread_Ok);
    ThreadPoolPtr pool(new ThreadPool(), Memory_deleter_const<ThreadPool>);
    code = pool->initPool(threadCount, entry, threadData);
    THREAD_CHECKRETURN_CODE(code);

    value = std::move(pool);
    return code;
}
