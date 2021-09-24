#ifndef TAK_ENGINE_THREAD_THREADPOOL_H_INCLUDED
#define TAK_ENGINE_THREAD_THREADPOOL_H_INCLUDED

#include <set>
#include "port/Platform.h"
#include "util/Error.h"
#include "thread/Mutex.h"
#include "thread/Thread.h"
#include "thread/Lock.h"

namespace TAK {
	namespace Engine {
		namespace Thread {
			class ENGINE_API ThreadPool{
				std::set<TAK::Engine::Thread::ThreadPtr> threads;
				TAK::Engine::Thread::Mutex threadsMutex;
			private:
				ThreadPool() NOTHROWS;
            public :
				~ThreadPool() NOTHROWS;
            public :
                /**
                 * Detaches all threads.
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
				TAK::Engine::Util::TAKErr detachAll() NOTHROWS;
                /**
                 * Joins all threads.
                 *
                 * @return  TE_Ok on success, various codes on failure.
                 */
				TAK::Engine::Util::TAKErr joinAll() NOTHROWS;
            private :
                /**
                 * Initializes the thread pool.
                 *
                 * @param threadCount   The number of threads in the pool
                 * @param entry         The entry function pointer
                 * @param threadData    The data passed to the entry function
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
				TAK::Engine::Util::TAKErr initPool(const std::size_t threadCount, void *(*entry)(void *), void* threadData) NOTHROWS;
            private :
                friend ENGINE_API Util::TAKErr ThreadPool_create(std::unique_ptr<ThreadPool, void(*)(const ThreadPool *)> &, const std::size_t, void *(*entry)(void *), void*) NOTHROWS;;
			}; // Close Class ThreadPool

            typedef std::unique_ptr<ThreadPool, void(*)(const ThreadPool *)> ThreadPoolPtr;

            ENGINE_API Util::TAKErr ThreadPool_create(ThreadPoolPtr &value, const std::size_t threadCount, void *(*entry)(void *), void* threadData) NOTHROWS;
			
		} // Close Namespace Thread
	} // Close Namespace Engine
} // Close Namespace TAK
#endif