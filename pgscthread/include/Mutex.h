#ifndef PGSCTHREAD_MUTEX_H_INCLUDED
#define PGSCTHREAD_MUTEX_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {

        class CondVar;
        class Lock;

        namespace Impl {
            class MutexImpl;
        }

        enum MutexType
        {
            /** Default, non-recursive behavior */
            TEMT_Default,
            /** Allows recursive locks by owning thread */
            TEMT_Recursive,
        };

        class PGSCTHREAD_API Mutex
        {
        public :
            Mutex(const MutexType type = TEMT_Default) PGSCT_NOTHROWS;
        private :
            Mutex(const Mutex &) PGSCT_NOTHROWS;
        public :
            ~Mutex() PGSCT_NOTHROWS;
        public :
            /**
             * Locks the mutex.
             *
             * @return  Thread_Ok on success, other codes on failure.
             */
            Util::ThreadErr lock() PGSCT_NOTHROWS;
            /**
             * Unlocks the mutex.
             * 
             * @return  Thread_Ok on success, other codes on failure.
             */
            Util::ThreadErr unlock() PGSCT_NOTHROWS;
        private :
            std::unique_ptr<Impl::MutexImpl, void(*)(const Impl::MutexImpl *)> impl;

            friend class CondVar;
            friend PGSCTHREAD_API Util::ThreadErr Lock_create(std::unique_ptr<Lock, void(*)(const Lock *)> &value, Mutex &mutex) PGSCT_NOTHROWS;
        };
    }
}

#endif
