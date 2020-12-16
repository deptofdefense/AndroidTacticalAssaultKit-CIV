#include "AsyncBitmapLoader.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace atakmap
{
    namespace renderer
    {
        namespace {
            Bitmap NULL_BITMAP = {
                0, 0, nullptr, 0, 0, 0, nullptr, nullptr
            };
        }

        AsyncBitmapLoader::AsyncBitmapLoader(int threadCount) :
            threadCount(threadCount),
            decoderHandlerMutex(),
            decoders(),
            protoHandlers(),
            jobMutex(),
            shouldTerminate(false),
            jobQueue(),
            jobCounter(0),
            threadPool(nullptr, nullptr)
        {
        }

        AsyncBitmapLoader::~AsyncBitmapLoader()
        {
            LockPtr lock(nullptr, nullptr);
            Lock_create(lock, jobMutex);

            // Firstly, mark as terminated so nothing new is accepted
            shouldTerminate = true;
            
            if (threadPool.get()) {
                ThreadPoolPtr ltp = std::move(threadPool);
                threadPool.reset();
                
                // kill threads first - release lock and join
                // In-progress jobs may complete; unstarted jobs will linger in queue
                jobCond.broadcast(*lock);
                lock.reset();
                ltp->joinAll();
                
                Lock_create(lock, jobMutex);
                ltp.reset();
            }

            // Now look at remaining jobs and clean them out with callback indicating
            // they are dead due to termination of the loader
            while (!jobQueue.empty()) {
                Job job = jobQueue.front();
                jobQueue.pop_front();
                lock.reset();
                job.callback->bitmapLoadComplete(job.id, BITMAP_LOADER_TERMINATED, NULL_BITMAP);
                Lock_create(lock, jobMutex);
            }
        }


        int AsyncBitmapLoader::loadBitmap(std::string uri, Listener *callback)
        {
            Lock lock(jobMutex);

            if (!ensureThread()) {
                // Already in shutdown mode - don't allow the job
                return JOB_REJECTED;
            }
            
            Job job(getNextId(), uri, callback);
            jobQueue.push_back(job);
            jobCond.signal(lock);
            return job.id;
        }
        
        int AsyncBitmapLoader::loadBitmap(const util::FutureTask<Bitmap> &task) {
            Lock lock(jobMutex);
            
            if (!ensureThread()) {
                // Already in shutdown mode - don't allow the job
                return JOB_REJECTED;
            }
            
            Job job(getNextId(), task);
            jobQueue.push_back(job);
            jobCond.signal(lock);
            return job.id;
        }


        void AsyncBitmapLoader::registerProtocolHandler(std::string scheme, ProtocolHandler *handler)
        {
            WriteLock lock(decoderHandlerMutex);
            protoHandlers[scheme] = handler;
        }

        void AsyncBitmapLoader::unregisterProtocolHandler(std::string scheme)
        {
            WriteLock lock(decoderHandlerMutex);
            protoHandlers.erase(scheme);
        }

        void AsyncBitmapLoader::registerDecoder(Decoder *decoder)
        {
            WriteLock lock(decoderHandlerMutex);
            decoders.push_front(decoder);
        }

        void AsyncBitmapLoader::unregisterDecoder(Decoder *decoder)
        {
            WriteLock lock(decoderHandlerMutex);
            std::list<Decoder *>::iterator iter;
            for (iter = decoders.begin(); iter != decoders.end(); ++iter) {
                Decoder *d = *iter;
                if (decoder == d) {
                    decoders.erase(iter);
                    break;
                }
            }
        }
        
        bool AsyncBitmapLoader::ensureThread() {
            
            if (threadPool.get() == nullptr) {
                if (shouldTerminate)
                    return false;
                
                // init threads
                return ( ThreadPool_create(threadPool, threadCount, threadProcessEntry, this) == TE_Ok );
            }
            
            return true;
        }

        int AsyncBitmapLoader::getNextId()
        {
            ++jobCounter;
            if (jobCounter < 0)
                jobCounter = 0;
            return jobCounter;
        }

        void *AsyncBitmapLoader::threadProcessEntry(void *opaque)
        {
            auto *obj = (AsyncBitmapLoader *)opaque;
            obj->threadProcess();
            return nullptr;
        }

        void AsyncBitmapLoader::threadProcess()
        {
            while (true) {
                Job job(-2, "", nullptr);
                {
                    Lock lock(jobMutex);
                    if (shouldTerminate)
                        break;

                    if (jobQueue.empty()) {
                        jobCond.wait(lock);
                        continue;
                    }

                    job = jobQueue.front();
                    jobQueue.pop_front();
                }
                threadTryDecode(job);
            }
        }

        void AsyncBitmapLoader::threadTryDecode(Job job)
        {
            ReadLock lock(decoderHandlerMutex);

            if (job.task.valid()) {
                job.task.run();
                return;
            }
            
            // First try to handle the protocol
            size_t cloc = job.uri.find_first_of(':');
            if (cloc == std::string::npos)
                job.callback->bitmapLoadComplete(job.id, BITMAP_INVALID_URI, NULL_BITMAP);
            
            std::string scheme = job.uri.substr(0, cloc);
            ProtocolHandler *handler;
            try {
                handler = protoHandlers.at(scheme);
            } catch (std::out_of_range e) {
                job.callback->bitmapLoadComplete(job.id, BITMAP_UNKNOWN_PROTOCOL, NULL_BITMAP);
                return;
            }

            IOContext ctx = handler->handleURI(job.uri);
            if (ctx == nullptr) {
                job.callback->bitmapLoadComplete(job.id, BITMAP_MISC_ERROR, NULL_BITMAP);
                return;
            }

            // Now try to decode
            std::list<Decoder *>::iterator iter;
            Bitmap b;
            bool success = false;
            for (iter = decoders.begin(); iter != decoders.end(); ++iter) {
                Decoder *d = *iter;
                if (d->decode(ctx, &b)) {
                    success = true;
                    break;
                }
            }
            // Close the IO Context out
            handler->closeIOContext(ctx);

            if (success)
                job.callback->bitmapLoadComplete(job.id, BITMAP_OK, b);
            else
                job.callback->bitmapLoadComplete(job.id, BITMAP_NO_DECODER, NULL_BITMAP);
        }
        

        AsyncBitmapLoader::Job::Job(int id, std::string uri, Listener *cb) :
            id(id), uri(uri), callback(cb)
        {

        }
        
        AsyncBitmapLoader::Job::Job(int id, const util::FutureTask<Bitmap> &task)
        : id(id), task(task), callback(nullptr) { }
    }
}

