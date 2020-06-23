#ifndef PGSCTHREAD_THREAD_H_INCLUDED
#define PGSCTHREAD_THREAD_H_INCLUDED

#include <memory>
#include <string>

#include "ThreadPlatform.h"
#include "ThreadError.h"

namespace PGSC {
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

        class PGSCTHREAD_API ThreadID
        {
        private :
            ThreadID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &&impl) PGSCT_NOTHROWS;
        public :
            /**
             * Constructs a default ThreadID that will not match any other
             * ThreadIDs.
             */
            ThreadID() PGSCT_NOTHROWS;
            ThreadID(const ThreadID &other) PGSCT_NOTHROWS;
            ~ThreadID() PGSCT_NOTHROWS;
        public :
            bool operator==(const ThreadID &other) const PGSCT_NOTHROWS;
            bool operator!=(const ThreadID &other) const PGSCT_NOTHROWS;
            ThreadID &operator=(const ThreadID &other) PGSCT_NOTHROWS;
        private :
            std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> impl;

            friend class Thread;
            friend PGSCTHREAD_API ThreadID Thread_currentThreadID() PGSCT_NOTHROWS;
        };

        class PGSCTHREAD_API Thread
        {
        protected :
            Thread() PGSCT_NOTHROWS;
        private :
            Thread(const Thread &) PGSCT_NOTHROWS;
        protected :
            virtual ~Thread() PGSCT_NOTHROWS = 0;
        public :
            /**
            * Returns the ID of the thread.
            */
            ThreadID getID() const PGSCT_NOTHROWS;
        public :
            /**
             * Joins the thread, waiting indefinitely if 'millis' is 0LL.
             *
             * @return  Thread_Ok on successful join, Thread_TimedOut if the
             *          timeout elapsed or other codes on failure.
             */
            virtual Util::ThreadErr join(const int64_t millis = 0LL) PGSCT_NOTHROWS = 0;
            /**
             * Detaches the thread.
             */
            virtual Util::ThreadErr detach() PGSCT_NOTHROWS = 0;
        private :
            virtual void getID(std::unique_ptr<Impl::ThreadIDImpl, void(*)(const Impl::ThreadIDImpl *)> &value) const PGSCT_NOTHROWS = 0;
        };

        struct PGSCTHREAD_API ThreadCreateParams
        {
        public :
            /**
             * Creates a default parameter of NULL name and TETP_Normal priority
             */
            ThreadCreateParams() PGSCT_NOTHROWS;
        public :
            /** The desired thread name */
            std::string name;
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
         * @return  Thread_Ok on success, various codes on failure
         */
        PGSCTHREAD_API Util::ThreadErr Thread_start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const ThreadCreateParams params = ThreadCreateParams()) PGSCT_NOTHROWS;

        /**
         * Causes the currently executing thread to sleep for the specified
         * number of milliseconds.
         *
         * @param milliseconds  The milliseconds to sleep
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        PGSCTHREAD_API Util::ThreadErr Thread_sleep(const int64_t milliseconds) PGSCT_NOTHROWS;

        /**
         * Returns the ID of the currently executing thread.
         *
         * @return  The ID of the currently executing thread.
         */
        PGSCTHREAD_API ThreadID Thread_currentThreadID() PGSCT_NOTHROWS;
    }
}

#endif
