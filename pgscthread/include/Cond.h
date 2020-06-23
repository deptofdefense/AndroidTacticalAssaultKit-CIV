#ifndef PGSCTHREAD_COND_H_INCLUDED
#define PGSCTHREAD_COND_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "Mutex.h"
#include "Lock.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {
        
        namespace Impl {
            class CondVarImpl;
        }

        /**
         * Defines a condition variable for use with the Mutex class.
         */
        class PGSCTHREAD_API CondVar
        {
        public :
            CondVar() PGSCT_NOTHROWS;
        private :
            CondVar(const CondVar &) PGSCT_NOTHROWS;
        public :
            ~CondVar() PGSCT_NOTHROWS;
        public :
            /**
             * Broadcasts a signal to all threads that are currently
             * waiting on this condition.
             *
             * @param lock  A lock that has been acquired on the Mutex
             *              specified during construction.
             *
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr broadcast(Lock &lock) PGSCT_NOTHROWS;
            /**
             * Signals a single threads that is currently waiting on this
             * condition.
             *
             * @param lock  A lock that has been acquired on the Mutex
             *              specified during construction.
             *
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr signal(Lock &lock) PGSCT_NOTHROWS;
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
            Util::ThreadErr wait(Lock &lock, const int64_t milliseconds = 0LL) PGSCT_NOTHROWS;
        private :
            std::unique_ptr<Impl::CondVarImpl, void(*)(const Impl::CondVarImpl *)> impl;
        };
    }
}

#endif
