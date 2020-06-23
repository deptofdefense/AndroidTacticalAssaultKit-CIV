#ifndef PGSCTHREAD_CONDIMPL_H_INCLUDED
#define PGSCTHREAD_CONDIMPL_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "LockImpl.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {
        namespace Impl {
            /**
             * Defines a condition variable for use with the Mutex class.
             */
            class CondVarImpl
            {
            protected:
                CondVarImpl() PGSCT_NOTHROWS;
            private:
                CondVarImpl(const CondVarImpl &) PGSCT_NOTHROWS;
            protected:
                virtual ~CondVarImpl() PGSCT_NOTHROWS = 0;
            public:
                /**
                 * Broadcasts a signal to all threads that are currently
                 * waiting on this condition.
                 *
                 * @param lock  A lock that has been acquired on the Mutex
                 *              specified during construction.
                 *
                 * @return  Thread_Ok on success, various codes on failure
                 */
                virtual Util::ThreadErr broadcast(LockImpl &lock) PGSCT_NOTHROWS = 0;
                /**
                 * Signals a single threads that is currently waiting on this
                 * condition.
                 *
                 * @param lock  A lock that has been acquired on the Mutex
                 *              specified during construction.
                 *
                 * @return  Thread_Ok on success, various codes on failure
                 */
                virtual Util::ThreadErr signal(LockImpl &lock) PGSCT_NOTHROWS = 0;
                /**
                 * Releases the specified lock and causes the currently
                 * executing thread to block until this condition is signaled
                 * from another thread or the specified period in milliseconds
                 * has elapsed. The thread will have reaquired the lock before
                 * this function returns.
                 *
                 * @param lock          A lock that has been acquired on the
                 *                      Mutex specified during construction.
                 * @param milliseconds  The maximum number of milliseconds to
                 *                      wait for a signal before resuming, or
                 *                      zero to wait indefinitely
                 *
                 * @return  Thread_Ok on success, Thread_TimedOut if the timeout
                 *          elapsed or various codes on failure.
                 */
                virtual Util::ThreadErr wait(LockImpl &lockImpl, const int64_t milliseconds = 0LL) PGSCT_NOTHROWS = 0;
            };

            typedef std::unique_ptr<CondVarImpl, void(*)(const CondVarImpl *)> CondVarImplPtr;

            /**
             * Creates a new condition variable.
             *
             * @param value Returns the new CondVar instance
             *
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr CondVarImpl_create(CondVarImplPtr &value) PGSCT_NOTHROWS;
        }
    }
}

#endif
