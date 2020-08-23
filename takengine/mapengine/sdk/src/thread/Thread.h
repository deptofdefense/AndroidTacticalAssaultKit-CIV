#ifndef TAK_ENGINE_THREAD_THREAD_H_INCLUDED
#define TAK_ENGINE_THREAD_THREAD_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {

            namespace Impl {
                class ThreadIDImpl;
            }

            /** Thread priorities */
            enum ThreadPriority
            {
                TETP_Lowest,
                TETP_Low,
                TETP_Normal,
                TETP_High,
                TETP_Highest,
            };

            class ENGINE_API ThreadID
            {
            private :
                ThreadID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &&impl) NOTHROWS;
            public :
                /**
                 * Constructs a default ThreadID that will not match any other
                 * ThreadIDs.
                 */
                ThreadID() NOTHROWS;
                ThreadID(const ThreadID &other) NOTHROWS;
                ~ThreadID() NOTHROWS;
            public :
                bool operator==(const ThreadID &other) const NOTHROWS;
                bool operator!=(const ThreadID &other) const NOTHROWS;
                ThreadID &operator=(const ThreadID &other) NOTHROWS;
            private :
                std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> impl;

                friend class ENGINE_API Thread;
                friend ENGINE_API ThreadID Thread_currentThreadID() NOTHROWS;
            };

            class ENGINE_API Thread
            {
            protected :
                Thread() NOTHROWS;
            private :
                Thread(const Thread &) NOTHROWS;
            protected :
                virtual ~Thread() NOTHROWS = 0;
            public :
                /**
                * Returns the ID of the thread.
                */
                ThreadID getID() const NOTHROWS;
            public :
                /**
                 * Joins the thread, waiting indefinitely if 'millis' is 0LL.
                 *
                 * @return  TE_Ok on successful join, TE_TimedOut if the
                 *          timeout elapsed or other codes on failure.
                 */
                virtual Util::TAKErr join(const int64_t millis = 0LL) NOTHROWS = 0;
                /**
                 * Detaches the thread.
                 */
                virtual Util::TAKErr detach() NOTHROWS = 0;
            private :
                virtual void getID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &value) const NOTHROWS = 0;
            };

            struct ENGINE_API ThreadCreateParams
            {
            public :
                /**
                 * Creates a default parameter of NULL name and TETP_Normal priority
                 */
                ThreadCreateParams() NOTHROWS;
            public :
                /** The desired thread name */
                Port::String name;
                /** The desired thread priority */
                ThreadPriority priority;
            };

            typedef std::unique_ptr<Thread, void(*)(const Thread *)> ThreadPtr;

            /**
             * Creates and starts a new thread
             *
             * @param value     Returns the thread
             * @param entry     Defines the entry point for the thread
             * @param opaque    The parameter to be passed to the thread's
             *                  entry function
             * @param params    The (optional) thread create params
             *
             * @return  TE_Ok on success, various codes on failure
             */
            ENGINE_API Util::TAKErr Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params = ThreadCreateParams()) NOTHROWS;

            /**
             * Causes the currently executing thread to sleep for the specified
             * number of milliseconds.
             *
             * @param milliseconds  The milliseconds to sleep
             *
             * @return  TE_Ok on success, various codes on failure.
             */
            ENGINE_API Util::TAKErr Thread_sleep(const int64_t milliseconds) NOTHROWS;

            /**
             * Returns the ID of the currently executing thread.
             *
             * @return  The ID of the currently executing thread.
             */
            ENGINE_API ThreadID Thread_currentThreadID() NOTHROWS;
        }
    }
}

#endif
