#include "AsyncBitmapLoader2.h"


#include "renderer/BitmapFactory2.h"
#include "thread/Lock.h"
#include "util/NonHeapAllocatable.h"
#include "util/ProtocolHandler.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;


namespace
{
    FileProtocolHandler fileHandler;
    ZipProtocolHandler zipHandler;
}

RWMutex AsyncBitmapLoader2::decoderHandlerMutex;
std::map<std::string, AsyncBitmapLoader2::ProtocolHandlerEntry> AsyncBitmapLoader2::protoHandlers;

AsyncBitmapLoader2::AsyncBitmapLoader2(const std::size_t threadCount, bool notifyThreadsOnDestruct) NOTHROWS :
    threadCount(threadCount),
    notifyThreadsOnDestruct(notifyThreadsOnDestruct),
    shouldTerminate(false)
{
    registerProtocolHandler("file", &fileHandler, "LOCAL");
    registerProtocolHandler("zip", &zipHandler, "LOCAL_COMPRESSED");
    registerProtocolHandler("arc", &zipHandler, "LOCAL_COMPRESSED");
}

AsyncBitmapLoader2::~AsyncBitmapLoader2() NOTHROWS
{
    Lock lock(queuesMutex);

    // Firstly, mark as terminated so nothing new is accepted
    shouldTerminate = true;

    std::map<std::string, Queue *>::iterator iter;
    for (iter = queues.begin(); iter != queues.end(); iter++)
        delete iter->second;
    queues.clear();

    unregisterProtocolHandler(zipHandler);
    unregisterProtocolHandler(fileHandler);
}


TAKErr AsyncBitmapLoader2::loadBitmapUri(Task &task, const char *curi) NOTHROWS
{
    if (!curi)
        return TE_InvalidArg;

    Lock lock(queuesMutex);

    std::string uri(curi);

    std::size_t cloc = uri.find_first_of(':');
    // if the URI does not have a protocol, assume it's a file
    if (cloc == std::string::npos) {
        std::ostringstream strm;
        strm << "file://";
        strm << uri;

        uri = strm.str();
        cloc = 4;
    }

    std::string scheme = uri.substr(0, cloc);
    std::map<std::string, ProtocolHandlerEntry>::iterator handler;

    handler = protoHandlers.find(scheme);
    if (handler == protoHandlers.end()) {
        Logger::log(Logger::Error, "no protocol handler found: %s", scheme.c_str());
        return TE_Err;
    }


    if (!ensureThread(handler->second.queueHint.c_str())) {
        // Already in shutdown mode - don't allow the job
        return TE_IllegalState;
    }

    task.reset(new FutureTask<std::shared_ptr<Bitmap2>>(decodeUriFn, (void*)new std::string(uri)));

    Queue *queue = queues[handler->second.queueHint];
    Lock queueLock(queue->jobMutex);
    queue->jobQueue.push_back(Task(task));
    queue->jobCond.signal(queueLock);
    return TE_Ok;
}

TAKErr AsyncBitmapLoader2::loadBitmapTask(const Task &task, const char *queueHint) NOTHROWS
{
    if (!task.get())
        return TE_InvalidArg;

    Lock lock(queuesMutex);

    if (!ensureThread(queueHint)) {
        // Already in shutdown mode - don't allow the job
        return TE_IllegalState;
    }

    Queue *queue = queues[std::string(queueHint ? queueHint : "")];
    Lock queueLock(queue->jobMutex);
    queue->jobQueue.push_back(Task(task));
    queue->jobCond.signal(queueLock);
    return TE_Ok;
}


TAKErr AsyncBitmapLoader2::registerProtocolHandler(const char *scheme, ProtocolHandler *handler, const char *queueHint) NOTHROWS
{
    if (!scheme || !handler)
        return TE_InvalidArg;
    WriteLock lock(decoderHandlerMutex);
    protoHandlers[scheme] = ProtocolHandlerEntry{ handler, std::string(queueHint ? queueHint : "") };
    return TE_Ok;
}

TAKErr AsyncBitmapLoader2::unregisterProtocolHandler(const char *scheme) NOTHROWS
{
    if (!scheme)
        return TE_InvalidArg;
    WriteLock lock(decoderHandlerMutex);
    if (protoHandlers.find(scheme) == protoHandlers.end())
        return TE_InvalidArg;
    protoHandlers.erase(scheme);
    return TE_Ok;
}
TAKErr AsyncBitmapLoader2::unregisterProtocolHandler(const ProtocolHandler &handler) NOTHROWS
{
    TAKErr code(TE_Ok);
    WriteLock lock(decoderHandlerMutex);
    TE_CHECKRETURN_CODE(code);
    auto entry = protoHandlers.begin();
    code = TE_InvalidArg;
    while(entry != protoHandlers.end()) {
        if (entry->second.handler != &handler) {
            entry++;
        } else {
            entry = protoHandlers.erase(entry);
            code = TE_Ok;
        }
    }

    return code;
}

bool AsyncBitmapLoader2::ensureThread(const char *queueHint)
{
    if (shouldTerminate)
        return false;

    Queue *queue;

    std::map<std::string, Queue *>::iterator entry;
    entry = queues.find(std::string(queueHint ? queueHint : ""));
    if (entry == queues.end()) {
        std::unique_ptr<Queue> queuePtr(queue=new Queue(*this));
        queues[std::string(queueHint ? queueHint : "")] = queuePtr.release();
    } else {
        queue = entry->second;
    }

    if (queue->threadPool.get() == nullptr) {

        // init threads
        return ( ThreadPool_create(queue->threadPool, threadCount, threadProcessEntry, queue) == TE_Ok );
    }

    return true;
}

void *AsyncBitmapLoader2::threadProcessEntry(void *opaque)
{
    auto *obj = (Queue *)opaque;
    obj->owner.threadProcess(*obj);
    return nullptr;
}

void AsyncBitmapLoader2::threadProcess(Queue &queue)
{
    while (true) {
        Task job;
        {
            Lock lock(queue.jobMutex);
            if (shouldTerminate)
                break;

            if (queue.jobQueue.empty()) {
                queue.jobCond.wait(lock);
                continue;
            }

            job = queue.jobQueue.front();
            queue.jobQueue.pop_front();
        }
        threadTryDecode(job);
    }
}

void AsyncBitmapLoader2::threadTryDecode(const Task &job)
{
    ReadLock lock(decoderHandlerMutex);

    if(job->valid()) {
        job->run();
        return;
    }
}

std::shared_ptr<Bitmap2> AsyncBitmapLoader2::decodeUriFn(void *opaque)
{
    TAKErr code;

    std::unique_ptr<std::string> uri(static_cast<std::string *>(opaque));

    // First try to handle the protocol
    size_t cloc = uri->find_first_of(':');
    if (cloc == std::string::npos)
        throw std::invalid_argument("no protocol defined");

    std::string scheme = uri->substr(0, cloc);
    std::map<std::string, ProtocolHandlerEntry>::iterator handler;

    handler = protoHandlers.find(scheme);
    if (handler == protoHandlers.end())
        throw std::out_of_range("no protocol handler found");

    DataInput2Ptr ctx(nullptr, nullptr);
    code = handler->second.handler->handleURI(ctx, uri->c_str());
    if (code != TE_Ok)
        throw std::runtime_error("failed to handle URI");

    BitmapPtr b(nullptr, nullptr);
    code = BitmapFactory2_decode(b, *ctx, nullptr);
    const bool success = (code == TE_Ok && b.get());
    ctx.reset();

    if (!success)
        throw std::runtime_error("failed to decode bitmap");

    return std::shared_ptr<Bitmap2>(std::move(b));
}

AsyncBitmapLoader2::Queue::Queue(AsyncBitmapLoader2 &owner_) NOTHROWS :
    owner(owner_),
    shouldTerminate(false),
    threadPool(nullptr, nullptr)
{}

AsyncBitmapLoader2::Queue::~Queue() NOTHROWS
{
    TAK::Engine::Thread::ThreadPoolPtr ltp(nullptr, nullptr);
    {
        Lock lock(jobMutex);

        // Firstly, mark as terminated so nothing new is accepted
        shouldTerminate = true;

        if (threadPool.get()) {
            ltp = std::move(threadPool);
            threadPool.reset();

            // kill threads first - release lock and join
            // In-progress jobs may complete; unstarted jobs will linger in queue
            if (owner.notifyThreadsOnDestruct)
                jobCond.broadcast(lock);           
        }
    }

    // wait for threads to exit
    if (owner.notifyThreadsOnDestruct && ltp.get())
        ltp->joinAll();

    {
        Lock lock(jobMutex);
        ltp.reset();

        // Now look at remaining jobs and clean them out with callback indicating
        // they are dead due to termination of the loader
        while (!jobQueue.empty()) {
            Task job = jobQueue.front();
            job->getFuture().cancel();
            jobQueue.pop_front();
        }
    }
}
