#ifndef PGSCTHREAD_MONITOR_H_INCLUDED
#define PGSCTHREAD_MONITOR_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "Cond.h"
#include "Lock.h"
#include "Mutex.h"
#include "ThreadError.h"

namespace PGSC {
    namespace Thread {

        class PGSCTHREAD_API Monitor
        {
        public :
            class Lock;
        public:
            /**
             * Creates a new Monitor instance.
             */
            Monitor(const MutexType type = TEMT_Default) PGSCT_NOTHROWS;
        private :
            Monitor(const Monitor &) PGSCT_NOTHROWS;
        public :
            ~Monitor() PGSCT_NOTHROWS;
        private :
            Mutex mutex;
            CondVar cond;

            friend PGSCTHREAD_API Util::ThreadErr MonitorLock_create(std::unique_ptr<Monitor::Lock, void(*)(const Monitor::Lock *)> &, Monitor &) PGSCT_NOTHROWS;
        };

        /**
         * RAII monitor locking construct, providing for wait and signal.
         */
        class PGSCTHREAD_API Monitor::Lock
        {
        private :
            Lock(Monitor &owner, LockPtr &&impl) PGSCT_NOTHROWS;
            Lock(const Lock &) PGSCT_NOTHROWS;
        public :
            ~Lock() PGSCT_NOTHROWS;
        public :
            /**
             * Releases the lock on the associated Monitor and causes the
             * currently executing thread to block until the Monitor is
             * signaled from another thread or the specified period in
             * milliseconds has elapsed. The thread will have reaquired
             * the lock before this function returns.
             *
             * @param milliseconds  The maximum number of milliseconds to
             *                      wait for a signal before resuming, or
             *                      zero to wait indefinitely
             *
             * @return  Thread_Ok on success, Thread_TimedOut if the timeout
             *          elapsed or various codes on failure.
             */
            Util::ThreadErr wait(const int64_t millis = 0LL) PGSCT_NOTHROWS;
            /**
             * Signals a single threads that is currently waiting to
             * acquire the associated Monitor.
             *
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr signal() PGSCT_NOTHROWS;
            /**
             * Signals all waiting threads that are currently waiting to
             * acquire the associated Monitor.
             * 
             * @return  Thread_Ok on success, various codes on failure
             */
            Util::ThreadErr broadcast() PGSCT_NOTHROWS;
        private :
            Monitor &owner;
            LockPtr impl;

            friend PGSCTHREAD_API Util::ThreadErr MonitorLock_create(std::unique_ptr<Monitor::Lock, void(*)(const Monitor::Lock *)> &, Monitor &) PGSCT_NOTHROWS;
        };

        typedef std::unique_ptr<Monitor::Lock, void(*)(const Monitor::Lock *)> MonitorLockPtr;

        /**
         * Acquires a new lock on the specified Monitor.
         *
         * @param value     Returns the newly acquired lock
         * @param monitor   The monitor to acquire a lock on
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        PGSCTHREAD_API Util::ThreadErr MonitorLock_create(MonitorLockPtr &value, Monitor &monitor) PGSCT_NOTHROWS;
    }
}

#endif
