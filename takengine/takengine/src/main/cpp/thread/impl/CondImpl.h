#ifndef TAK_ENGINE_THREAD_CONDIMPL_H_INCLUDED
#define TAK_ENGINE_THREAD_CONDIMPL_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/impl/MutexImpl.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            namespace Impl {
                /**
                 * Defines a condition variable for use with the Mutex class.
                 */
                class CondVarImpl
                {
                protected:
                    CondVarImpl() NOTHROWS;
                private:
                    CondVarImpl(const CondVarImpl &) NOTHROWS;
                protected:
                    virtual ~CondVarImpl() NOTHROWS = 0;
                public:
                    /**
                     * Broadcasts a signal to all threads that are currently
                     * waiting on this condition.
                     *
                     * @param lock  A lock that has been acquired on the Mutex
                     *              specified during construction.
                     *
                     * @return  TE_Ok on success, various codes on failure
                     */
                    virtual Util::TAKErr broadcast(MutexImpl &lock) NOTHROWS = 0;
                    /**
                     * Signals a single threads that is currently waiting on this
                     * condition.
                     *
                     * @param lock  A lock that has been acquired on the Mutex
                     *              specified during construction.
                     *
                     * @return  TE_Ok on success, various codes on failure
                     */
                    virtual Util::TAKErr signal(MutexImpl &lock) NOTHROWS = 0;
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
                    virtual Util::TAKErr wait(MutexImpl &lockImpl, const int64_t milliseconds = 0LL) NOTHROWS = 0;
                };

                typedef std::unique_ptr<CondVarImpl, void(*)(const CondVarImpl *)> CondVarImplPtr;

                /**
                 * Creates a new condition variable.
                 *
                 * @param value Returns the new CondVar instance
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr CondVarImpl_create(CondVarImplPtr &value) NOTHROWS;
            }
        }
    }
}

#endif
