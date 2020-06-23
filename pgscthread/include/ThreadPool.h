#ifndef PGSCTHREAD_THREADPOOL_H_INCLUDED
#define PGSCTHREAD_THREADPOOL_H_INCLUDED

#include <set>
#include "ThreadPlatform.h"
#include "ThreadError.h"
#include "Mutex.h"
#include "Thread.h"
#include "Lock.h"

namespace PGSC {
    namespace Thread {
        class PGSCTHREAD_API ThreadPool{
            std::set<PGSC::Thread::ThreadPtr> threads;
            PGSC::Thread::Mutex threadsMutex;
        private:
            ThreadPool() PGSCT_NOTHROWS;
        public :
            ~ThreadPool() PGSCT_NOTHROWS;
        public :
	    /**
	     * Detaches all threads.
	     *
	     * @return  Thread_Ok on success, various codes on failure
	     */
            PGSC::Util::ThreadErr detachAll() PGSCT_NOTHROWS;
	    /**
	     * Joins all threads.
	     *
	     * @return  Thread_Ok on success, various codes on failure.
	     */
            PGSC::Util::ThreadErr joinAll() PGSCT_NOTHROWS;
        private :
	    /**
	     * Initializes the thread pool.
	     *
	     * @param threadCount   The number of threads in the pool
	     * @param entry         The entry function pointer
	     * @param threadData    The data passed to the entry function
	     *
	     * @return  Thread_Ok on success, various codes on failure
	     */
            PGSC::Util::ThreadErr initPool(const std::size_t threadCount, void *(*entry)(void *), void* threadData) PGSCT_NOTHROWS;
        private :
            friend PGSCTHREAD_API Util::ThreadErr ThreadPool_create(std::unique_ptr<ThreadPool, void(*)(const ThreadPool *)> &, const std::size_t, void *(*entry)(void *), void*) PGSCT_NOTHROWS;;
        }; // Close Class ThreadPool

        typedef std::unique_ptr<ThreadPool, void(*)(const ThreadPool *)> ThreadPoolPtr;

        PGSCTHREAD_API Util::ThreadErr ThreadPool_create(ThreadPoolPtr &value, const std::size_t threadCount, void *(*entry)(void *), void* threadData) PGSCT_NOTHROWS;

    } // Close Namespace Thread
} // Close Namespace TAK
#endif
