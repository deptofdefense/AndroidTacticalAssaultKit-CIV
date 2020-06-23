#ifndef IMPL_THREADEDHANDLER_H_
#define IMPL_THREADEDHANDLER_H_

#include <Thread.h>
#include <Mutex.h>
#include "commoutils.h"
#include <vector>
#include <string>

namespace atakmap {
namespace commoncommo {
namespace impl
{

class ThreadedHandler
{
public:
    ThreadedHandler(size_t nThreads, const char **names = NULL);
    virtual ~ThreadedHandler();

protected:
    void startThreads();
    void stopThreads();

    // Subclass can override to unblock a thread
    // after it has been flagged for shutdown
    virtual void threadStopSignal(size_t threadNum) {};

    bool threadShouldStop(size_t threadNum);

    virtual void threadEntry(size_t threadNum) = 0;

private:
    COMMO_DISALLOW_COPY(ThreadedHandler);

    struct ThreadContext
    {
        size_t id;
        ThreadedHandler *owner;
        PGSC::Thread::ThreadPtr thread;
        PGSC::Thread::Mutex stateMutex;
        std::string name;
        bool shouldStop;

        ThreadContext(size_t id, ThreadedHandler *owner, const char *name) : id(id), owner(owner), thread(NULL, NULL), name(name ? name : ""), shouldStop(false) {};
        void start();
        void stop();
    private:
        COMMO_DISALLOW_COPY(ThreadContext);
    };

    std::vector<ThreadContext *> contexts;
    static void *threadEntry(void *);
};

}
}
}

#endif /* IMPL_THREADEDHANDLER_H_ */
