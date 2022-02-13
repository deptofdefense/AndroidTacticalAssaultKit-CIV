#ifndef TAK_ENGINE_RENDERER_ASYNCBITMAPLOADER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_ASYNCBITMAPLOADER2_H_INCLUDED

#include <string>
#include <list>
#include <deque>
#include <map>
#include <memory>

#include "thread/Thread.h"
#include "thread/ThreadPool.h"

#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "thread/Cond.h"
#include "thread/RWMutex.h"
#include "util/DataInput2.h"
#include "util/Error.h"
#include "util/FutureTask.h"
#include "util/NonCopyable.h"

namespace TAK
{
    namespace Engine
    {
        namespace Util
        {
            class ProtocolHandler;
        }
        namespace Renderer
        {
            class ENGINE_API AsyncBitmapLoader2
            {
            public :
                typedef std::shared_ptr<atakmap::util::FutureTask<std::shared_ptr<Bitmap2>>> Task;
            private :
                struct Queue;

            public :
                // Normally deleting the loader will wait on queue threads to exit, but
                // specifically on Windows, if the thread has exited during process shutdown
                // (when our DLL unloads) and this bitmaploader is being deleted after the those
                // threads are dead then trying to notify those threads to exit (Condvar broadcast)
                // will cause a crash or deadlock. So setting notifyThreadsOnDestruct to false will
                // avoid this (and thus is assuming that this bitmaploader is only being destroyed at process
                // termination or dll unloading). Use for any static/globally held objects!
                // See https://stackoverflow.com/questions/49309366/access-violation-on-wakeallconditionvariable-in-dll-shutdown
                AsyncBitmapLoader2(const std::size_t threadCount, bool notifyThreadsOnDestruct = true) NOTHROWS;
            public :
                ~AsyncBitmapLoader2() NOTHROWS;
            public :
                // Returns a job identifier or JOB_REJECTED if
                // this bitmap loader is already in shutdown mode
                // Listener is assumed valid until either the job is completed or
                // this bitmaploader is destroyed.
                Util::TAKErr loadBitmapUri(Task &task, const char *uri) NOTHROWS;

                // Returns a job identifier or JOB_REJECTED if
                // this bitmap loader is already in shutdown mode
                // FutureTask will be executed or canceled if the bitmaploader is destroyed
                Util::TAKErr loadBitmapTask(const Task &task, const char *queue) NOTHROWS;
            public :
                // Replaces any existing.  **does not clean up handler in any circumstance!**
                static Util::TAKErr registerProtocolHandler(const char *scheme, Util::ProtocolHandler *, const char *queueHint = nullptr) NOTHROWS;
                static Util::TAKErr unregisterProtocolHandler(const char *scheme) NOTHROWS;
                static Util::TAKErr unregisterProtocolHandler(const Util::ProtocolHandler &) NOTHROWS;
            private:
                bool ensureThread(const char *queue);
            private :
                static Thread::RWMutex decoderHandlerMutex;
                static std::map<std::string, std::string> protoSchemeQueueHints;
            private :
                const std::size_t threadCount;
                const bool notifyThreadsOnDestruct;

                Thread::Mutex queuesMutex;
                bool shouldTerminate;
                std::map<std::string, Queue *> queues;

                static void *threadProcessEntry(void *opaque);
                void threadProcess(Queue &queue);
                void threadTryDecode(const Task &job);

                static std::shared_ptr<Bitmap2> decodeUriFn(void *opaque);
            };

            struct AsyncBitmapLoader2::Queue : TAK::Engine::Util::NonCopyable
            {
                AsyncBitmapLoader2 &owner;
                Thread::Mutex jobMutex;
                Thread::CondVar jobCond;
                bool shouldTerminate;
                std::deque<AsyncBitmapLoader2::Task> jobQueue;

                Thread::ThreadPoolPtr threadPool;

                Queue(AsyncBitmapLoader2 &owner) NOTHROWS;
                ~Queue() NOTHROWS;
            };
        }
    }
}


#endif

