#include "commothread.h"
#include "commoutils.h"

#ifndef IMPL_PLATFORMTHREAD_H_
#define IMPL_PLATFORMTHREAD_H_

namespace atakmap {
namespace commoncommo {
namespace impl {
namespace thread
{

    class PlatformMutex
    {
    public:
        virtual ~PlatformMutex() = 0;

        /**
         * Locks the mutex.
         *
         * @return  Thread_Ok on success, other codes on failure.
         */
        virtual ThreadErr lock() = 0;
        /**
         * Unlocks the mutex.
         *
         * @return  Thread_Ok on success, other codes on failure.
         */
        virtual ThreadErr unlock() = 0;

        /**
         * Creates a new Mutex.
         *
         * @param value Returns the newly created Mutex
         * @param type  The desired mutex type
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        static ThreadErr create(PlatformMutexPtr &value, const Mutex::Type type = Mutex::Type::Type_Default);

    protected:
        PlatformMutex();

    private:
        COMMO_DISALLOW_COPY(PlatformMutex);
    };


    /**
     * Defines a condition variable for use with the Mutex class.
     */
    class PlatformCondVar
    {
    public:
        virtual ~PlatformCondVar() = 0;

        /**
         * Broadcasts a signal to all threads that are currently
         * waiting on this condition.
         *
         * @param lock  A lock that has been acquired on the Mutex
         *              specified during construction.
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        virtual ThreadErr broadcast(PlatformMutex &lock) = 0;
        /**
         * Signals a single threads that is currently waiting on this
         * condition.
         *
         * @param lock  A lock that has been acquired on the Mutex
         *              specified during construction.
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        virtual ThreadErr signal(PlatformMutex &lock) = 0;
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
        virtual ThreadErr wait(PlatformMutex &lockImpl, const int64_t milliseconds = 0LL) = 0;

        /**
         * Creates a new condition variable.
         *
         * @param value Returns the new CondVar instance
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        static ThreadErr create(PlatformCondVarPtr &value);

    protected:
        PlatformCondVar();
    private:
        COMMO_DISALLOW_COPY(PlatformCondVar);

    };


    class PlatformThreadID
    {
    public :
        virtual ~PlatformThreadID() = 0;
        virtual void clone(PlatformThreadIDPtr &value) = 0;
        virtual bool operator==(const PlatformThreadID &other) const = 0;
    protected :
        PlatformThreadID();
    private :
        COMMO_DISALLOW_COPY(PlatformThreadID);
    };



}
}
}
}

#endif

