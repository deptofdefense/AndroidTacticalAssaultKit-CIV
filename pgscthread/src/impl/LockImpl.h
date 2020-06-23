#ifndef PGSCTHREAD_LOCKIMPL_H_INCLUDED
#define PGSCTHREAD_LOCKIMPL_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "MutexImpl.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {
        namespace Impl {
            /**
             * RAII Mutex lock construct.
             *
             * <P>Corresponds to a read lock for RWMutex instances.
             */
            class LockImpl
            {
            protected:
                LockImpl() PGSCT_NOTHROWS;
            private:
                LockImpl(const LockImpl &) PGSCT_NOTHROWS;
            protected:
                virtual ~LockImpl() PGSCT_NOTHROWS = 0;
            };

            typedef std::unique_ptr<LockImpl, void(*)(const LockImpl *)> LockImplPtr;

            /**
             * Acquires a lock on the specified mutex.
             *
             * @param value Returns the newly acquired lock
             * @param mutex The mutex to acquire a lock on
             *
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr LockImpl_create(LockImplPtr &value, MutexImpl &mutex) PGSCT_NOTHROWS;
        }
    }
}

#endif
