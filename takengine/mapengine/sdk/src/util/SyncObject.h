
#ifndef ATAKMAP_UTIL_SYNCOBJECT_H
#define ATAKMAP_UTIL_SYNCOBJECT_H

#include <exception>

#include "thread/Mutex.h"
#include "thread/Cond.h"
#include "thread/Lock.h"

#include "util/AtomicRefCountable.h"

namespace atakmap {
    namespace util {
        
        class SyncLock;
        
        /**
         * A sharable synchronization object.
         *
         * Use with SyncLock to scope lock.
         */
        //XXX- verify RefCountable is atomic
        class SyncObject : public AtomicRefCountable {
            
            friend SyncLock;
            
        public:
            SyncObject();
            virtual ~SyncObject();
            
            void notify();
            void notifyAll();
            void wait();

			/*
			* Noncopyable
			*/
			SyncObject(const SyncObject&) = delete;
			SyncObject &operator=(const SyncObject&) = delete;
            
        private:
            TAK::Engine::Thread::Mutex mutex;
            TAK::Engine::Thread::CondVar cv;
            
            TAK::Engine::Thread::Lock *lock;
        };
        
        class SyncLock {
        public:
            inline SyncLock(SyncObject &syncObj)
            : syncObj(syncObj), lock(NULL, NULL)
            {
                TAK::Engine::Thread::Lock_create(lock, syncObj.mutex);
                syncObj.lock = lock.get();
            }
            inline ~SyncLock() { syncObj.lock = NULL; }
			/*
			* Noncopyable
			*/
			SyncLock(const SyncLock&) = delete;
			SyncLock &operator=(const SyncLock&) = delete;
        private:
            // only intended for stack allocation
            void *operator new(size_t);
            void *operator new[](size_t);
            
            SyncObject &syncObj;
            TAK::Engine::Thread::LockPtr lock;
        };
        
#ifdef __APPLE__
        class SyncException : public std::runtime_error {
        public:
            SyncException(const char *message)
            : runtime_error(message) { }
        };
#else
        class SyncException : public std::exception {
        public:
            SyncException(const char *message)
            : std::exception(message) { }
        };
#endif
    }
}

#endif
