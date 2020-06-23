#ifndef PGSCTHREAD_MUTEXIMPL_H_INCLUDED
#define PGSCTHREAD_MUTEXIMPL_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "Mutex.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {
        namespace Impl {
            class MutexImpl
            {
            protected:
                MutexImpl() PGSCT_NOTHROWS;
            private:
                MutexImpl(const MutexImpl &) PGSCT_NOTHROWS;
            protected:
                virtual ~MutexImpl() PGSCT_NOTHROWS = 0;
            public:
                /**
                 * Locks the mutex.
                 *
                 * @return  Thread_Ok on success, other codes on failure.
                 */
                virtual Util::ThreadErr lock() PGSCT_NOTHROWS = 0;
                /**
                 * Unlocks the mutex.
                 *
                 * @return  Thread_Ok on success, other codes on failure.
                 */
                virtual Util::ThreadErr unlock() PGSCT_NOTHROWS = 0;
            };

            typedef std::unique_ptr<MutexImpl, void(*)(const MutexImpl *)> MutexImplPtr;

            /**
             * Creates a new Mutex.
             *
             * @param value Returns the newly created Mutex
             * @param type  The desired mutex type
             *
             * @return  Thread_Ok on success, various codes on failure.
             */
            Util::ThreadErr MutexImpl_create(MutexImplPtr &value, const PGSC::Thread::MutexType type = PGSC::Thread::TEMT_Default) PGSCT_NOTHROWS;
        }
    }
}

#endif
