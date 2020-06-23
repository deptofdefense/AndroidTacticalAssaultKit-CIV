#ifndef PGSCTHREAD_LOCK_H_INCLUDED
#define PGSCTHREAD_LOCK_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "Mutex.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {
        
        class CondVar;

        namespace Impl {
            class LockImpl;
        }

        /**
         * RAII Mutex lock construct.
         */
        class PGSCTHREAD_API Lock
        {
        private :
            Lock(std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> &&impl) PGSCT_NOTHROWS;
            Lock(const Lock &) PGSCT_NOTHROWS;
        public :
            ~Lock() PGSCT_NOTHROWS;
        private :
            std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> impl;

            friend class CondVar;
            friend PGSCTHREAD_API Util::ThreadErr Lock_create(std::unique_ptr<Lock, void(*)(const Lock *)> &value, Mutex &mutex) PGSCT_NOTHROWS;
        };

        typedef std::unique_ptr<Lock, void(*)(const Lock *)> LockPtr;

        /**
         * Acquires a lock on the specified mutex.
         *
         * @param value Returns the newly acquired lock
         * @param mutex The mutex to acquire a lock on
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        PGSCTHREAD_API Util::ThreadErr Lock_create(LockPtr &value, Mutex &mutex) PGSCT_NOTHROWS;
    }
}

#endif
