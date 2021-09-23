#ifndef TAK_ENGINE_THREAD_COND_H_INCLUDED
#define TAK_ENGINE_THREAD_COND_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            
            namespace Impl {
                class CondVarImpl;
            }

            /**
             * Defines a condition variable for use with the Mutex class.
             */
            class ENGINE_API CondVar
            {
            public :
                CondVar() NOTHROWS;
            private :
                CondVar(const CondVar &) NOTHROWS;
            public :
                ~CondVar() NOTHROWS;
            public :
                /**
                 * Broadcasts a signal to all threads that are currently
                 * waiting on this condition.
                 *
                 * @param lock  A lock that has been acquired on the Mutex
                 *              specified during construction.
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr broadcast(Lock &lock) NOTHROWS;
                /**
                 * Signals a single threads that is currently waiting on this
                 * condition.
                 *
                 * @param lock  A lock that has been acquired on the Mutex
                 *              specified during construction.
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr signal(Lock &lock) NOTHROWS;
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
                 * @return  TE_Ok on success, TE_TimedOut if the timeout
                 *          elapsed or various codes on failure.
                 */
                Util::TAKErr wait(Lock &lock, const int64_t milliseconds = 0LL) NOTHROWS;
            private :
                std::unique_ptr<Impl::CondVarImpl, void(*)(const Impl::CondVarImpl *)> impl;
            };
        }
    }
}

#endif
