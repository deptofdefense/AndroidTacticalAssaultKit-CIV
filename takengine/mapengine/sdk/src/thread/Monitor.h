#ifndef TAK_ENGINE_THREAD_MONITOR_H_INCLUDED
#define TAK_ENGINE_THREAD_MONITOR_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Cond.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {

            class ENGINE_API Monitor
            {
            public :
                class ENGINE_API Lock;
            public:
                /**
                 * Creates a new Monitor instance.
                 */
                Monitor(const MutexType type = TEMT_Default) NOTHROWS;
            private :
                Monitor(const Monitor &) NOTHROWS;
            public :
                ~Monitor() NOTHROWS;
            private :
                Mutex mutex;
                CondVar cond;
            };

            /**
             * RAII monitor locking construct, providing for wait and signal.
             */
            class ENGINE_API Monitor::Lock
            {
            public :
                Lock(Monitor &owner) NOTHROWS;
            private :
                Lock(const Lock &) NOTHROWS;
            public :
                ~Lock() NOTHROWS;
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
                 * @return  TE_Ok on success, TE_TimedOut if the timeout
                 *          elapsed or various codes on failure.
                 */
                Util::TAKErr wait(const int64_t millis = 0LL) NOTHROWS;
                /**
                 * Signals a single threads that is currently waiting to
                 * acquire the associated Monitor.
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr signal() NOTHROWS;
                /**
                 * Signals all waiting threads that are currently waiting to
                 * acquire the associated Monitor.
                 * 
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr broadcast() NOTHROWS;
            private :
                Monitor &owner;
                TAK::Engine::Thread::Lock impl;
            public :
                const Util::TAKErr status;
            };

            typedef std::unique_ptr<Monitor::Lock, void(*)(const Monitor::Lock *)> MonitorLockPtr;

            /**
             * Acquires a new lock on the specified Monitor.
             *
             * @param value     Returns the newly acquired lock
             * @param monitor   The monitor to acquire a lock on
             *
             * @return  TE_Ok on success, various codes on failure.
             */
            ENGINE_API Util::TAKErr MonitorLock_create(MonitorLockPtr &value, Monitor &monitor) NOTHROWS;
        }
    }
}

#endif
