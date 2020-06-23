#include "threadedhandler.h"
#include <Lock.h>

using namespace atakmap::commoncommo::impl;


ThreadedHandler::ThreadedHandler(size_t nThreads, const char **names) : contexts()
{
    for (size_t i = 0; i < nThreads; ++i)
        contexts.push_back(new ThreadContext(i, this, names ? names[i] : NULL));
}

ThreadedHandler::~ThreadedHandler()
{
    stopThreads();
    for (size_t i = 0; i < contexts.size(); ++i) {
        delete contexts[i];
        contexts[i] = NULL;
    }
}

void ThreadedHandler::startThreads()
{
    for (size_t i = 0; i < contexts.size(); ++i) {
        contexts[i]->start();
    }
}

void ThreadedHandler::stopThreads()
{
    for (size_t i = 0; i < contexts.size(); ++i) {
        contexts[i]->stop();
    }
}

bool ThreadedHandler::threadShouldStop(size_t threadNum)
{
    return contexts[threadNum]->shouldStop;
}

void* ThreadedHandler::threadEntry(void *ctxv)
{
    ThreadContext *ctx = (ThreadContext *)ctxv;
    ThreadedHandler *myobj = (ThreadedHandler *)ctx->owner;
    myobj->threadEntry(ctx->id);
    return NULL;
}

void ThreadedHandler::ThreadContext::start()
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, stateMutex);
    PGSC::Thread::ThreadCreateParams params;
    params.name = name;
    PGSC::Thread::Thread_start(thread, ThreadedHandler::threadEntry, this, params);
}


void ThreadedHandler::ThreadContext::stop()
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, stateMutex);
    if (thread) {
        shouldStop = true;
        owner->threadStopSignal(id);
        thread->join();
        thread = NULL;
    }
}
