#ifndef ATAKMAP_RENDERER_ASYNCBITMAPLOADER_H_INCLUDED
#define ATAKMAP_RENDERER_ASYNCBITMAPLOADER_H_INCLUDED

#include <string>
#include <list>
#include <deque>
#include <map>
#include "thread/Thread.h"
#include "thread/ThreadPool.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "thread/Cond.h"
#include "thread/RWMutex.h"
#include "Bitmap.h"
#include "util/FutureTask.h"

namespace atakmap
{
    namespace renderer
    {

        class AsyncBitmapLoader
        {
        public:

            typedef enum {
                BITMAP_OK = 0,
                BITMAP_INVALID_URI,
                BITMAP_UNKNOWN_PROTOCOL,
                BITMAP_NO_DECODER,
                BITMAP_MISC_ERROR,
                BITMAP_LOADER_TERMINATED,
            } ERRCODE;

            static const int JOB_REJECTED = -1;

            // A language binding-level-specific IO Context
            // IOContexts MUST support seeking, at least enough that
            // they may be rewound back to the original point they were at when
            // originally opened.
            typedef void * IOContext;

            class ProtocolHandler {
            public:
                virtual ~ProtocolHandler() {};
                // Return NULL if not able to handle, else return pointer to 
                // binding-level-specific IO context.
                virtual IOContext handleURI(std::string uri) = 0;
                // Only needs to support closing IOCOntexts created by this Handler.
                virtual void closeIOContext(IOContext io) = 0;
            };

            class Decoder {
            public:
                virtual ~Decoder() {};
                // Return false if not able to handle, else fill in bitmap and return
                // true
                // If the method fails to decode a bitmap, it MUST reset the IOContext
                // back to its original state (read pointer same as on entry).
                virtual bool decode(IOContext io, Bitmap *b) = 0;
            };

            class Listener {
            public:
                virtual ~Listener() {};
                virtual void bitmapLoadComplete(int jobid, ERRCODE errcode, Bitmap b) = 0;
            };

            AsyncBitmapLoader(int threadCount);
            ~AsyncBitmapLoader();

            // Returns a job identifier or JOB_REJECTED if
            // this bitmap loader is already in shutdown mode
            // Listener is assumed valid until either the job is completed or
            // this bitmaploader is destroyed.
            int loadBitmap(std::string uri, Listener *callback);
            
            // Returns a job identifier or JOB_REJECTED if
            // this bitmap loader is already in shutdown mode
            // FutureTask will be executed or canceled if the bitmaploader is destroyed
            int loadBitmap(const util::FutureTask<Bitmap> &task);

            // Replaces any existing.  **does not clean up handler in any circumstance!**
            void registerProtocolHandler(std::string scheme, ProtocolHandler *);
            void unregisterProtocolHandler(std::string scheme);
            // Prepends to existing decoder list - each is checked in order for
            // ability to decode
            void registerDecoder(Decoder *);
            void unregisterDecoder(Decoder *);

        private:
            bool ensureThread();
            
        private:
            struct Job {
                int id;
                std::string uri;
                Listener *callback;
                util::FutureTask<Bitmap> task;
                
                Job(int id, std::string uri, Listener *cb);
                Job(int id, const util::FutureTask<Bitmap> &task);
            };
            int threadCount;
            TAK::Engine::Thread::RWMutex decoderHandlerMutex;
            std::list<Decoder *> decoders;
            std::map<std::string, ProtocolHandler *> protoHandlers;

            TAK::Engine::Thread::Mutex jobMutex;
            TAK::Engine::Thread::CondVar jobCond;
            bool shouldTerminate;
            int jobCounter;
            std::deque<Job> jobQueue;

            TAK::Engine::Thread::ThreadPoolPtr threadPool;

            // Only callable when holding jobMutex
            int getNextId();

            static void *threadProcessEntry(void *opaque);
            void threadProcess();
            void threadTryDecode(Job job);
        };

    }
}



#endif

