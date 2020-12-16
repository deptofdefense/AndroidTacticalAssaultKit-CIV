
#ifndef TAK_ENGINE_UTIL_WORK_H_INCLUDED
#define TAK_ENGINE_UTIL_WORK_H_INCLUDED

#include <vector>
#include <deque>
#include "thread/ThreadPool.h"
#include "thread/Monitor.h"

namespace TAK {
	namespace Engine {
		namespace Util {
			/**
			 * Base for objects that represent a unit of async work.
			 */
			class Work {
			protected:
				ENGINE_API Work() NOTHROWS;

			public:
				ENGINE_API virtual ~Work() NOTHROWS;

				/**
				 * Invoke execution of work on calling thread
				 *
				 * @return TE_Ok when work is completed
				 *         TE_Done when work is already done or preempted
				 *         TE_Busy when work is aready underway on another thread
				 */
				ENGINE_API TAKErr signalWork() NOTHROWS;

				/**
				 * Stop the work from happening before it starts. If work is currently happening an external
				 * mechanism is required to stop it (if desired).
				 *
				 * @return TE_Ok when work is stopped
				 *         TE_Done when work is already done or preempted
				 *         TE_Busy when work is aready underway
				 */
				ENGINE_API TAKErr preempt(TAKErr because) NOTHROWS;

				/**
				 * Check if the work is done already and receive optional result code
				 *
				 * @param done true when done
				 * @param resultCode optional out param for work return code (when done is true)
				 */
				ENGINE_API TAKErr isDone(bool &done, TAKErr *resultCode = nullptr) const NOTHROWS;

				/**
				 * Block until the work is done
				 */
				ENGINE_API TAKErr awaitDone(TAKErr &err) NOTHROWS;

			protected:
				/**
				 * Enter working state if first caller.
				 *
				 * <p>Called by derived implementation classes</p>
				 *
				 * @param lockPtr OUT a reference to a monitor lock for the object only set when TE_Ok is returned.
				 *
				 * @return TE_Ok when first and only to enter working state
				 *         TE_Done when work is already done
				 *         TE_Busy when work is aready underway
				 */
				ENGINE_API TAKErr beginWorking(Thread::MonitorLockPtr &lockPtr) NOTHROWS;

				/**
				 * Finish working must be paired with a beginWorking.
				 *
				 * <p>Called by derived implementation classes</p>
				 *
				 * @param lockPtr IN a valid lockPtr from a call to beginWorking
				 * @param result the result code from work done
				 */
				ENGINE_API TAKErr finishWorking(Thread::MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS;

				/**
				 * Block until the done state.
				 *
				 * <p>Called by derived implementation classes</p>
				 *
				 * @param lockPtr OUT a lock when done state is reached (and TE_Ok is returned)
				 * @param resultCode OUT the result code from work if already done
				 */
				ENGINE_API TAKErr awaitDone(Thread::MonitorLockPtr &lockPtr, TAKErr &resultCode) NOTHROWS;

				/**
				 * Lock the monitor if Undone state
				 */
				ENGINE_API TAKErr syncUndone(Thread::MonitorLockPtr &lockPtr) NOTHROWS;

				/**
				 * Handles the signalWork signal
				 */
				ENGINE_API virtual TAKErr onSignalWork(Thread::MonitorLockPtr &lockPtr) NOTHROWS;

				/**
				 * Handles entering of done state
				 */
				ENGINE_API virtual TAKErr onDone(Thread::MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS;

			private:
				TAKErr beginSync(Thread::MonitorLockPtr &lockPtr) const NOTHROWS;

				enum {
					Pending,
					Working,
					Done
				};

			private:
				mutable Thread::Monitor monitor_;
				TAKErr result_code_;
				unsigned int state_;
			};

			/**
			 * Work that can have subsequent attached work
			 */
			class ExtendableWork : public Work {
			public:
				ENGINE_API virtual ~ExtendableWork() NOTHROWS;

				/**
				 * Attach more work on to this Work to be signaled when this work is Done.
				 *
				 * @param work Work to attach
				 */
				ENGINE_API TAKErr attachWork(const std::shared_ptr<Work> &work) NOTHROWS;

			protected:
				ENGINE_API virtual TAKErr onDone(Thread::MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS;

			private:
				std::vector<std::shared_ptr<Work>> attachedWork;
			};

			/**
			 * Shared pointer to a Worker
			 */
			typedef std::shared_ptr<class Worker> SharedWorkerPtr;

			/**
			 * Work that when signaled schedules another Work on a Worker
			 */
			class TransferWork : public Work {
			public:
				/**
				 * Construct a TransferWork
				 *
				 * @param work the Work to be scheduled
				 * @param worker the Worker to schedule the work on
				 */
				ENGINE_API TransferWork(std::shared_ptr<Work> work, std::shared_ptr<class Worker> worker) NOTHROWS;

				ENGINE_API virtual ~TransferWork() NOTHROWS;

			protected:
				ENGINE_API virtual TAKErr onSignalWork(Thread::MonitorLockPtr &lockPtr) NOTHROWS;

			private:
				std::shared_ptr<Work> work;
				std::shared_ptr<Worker> worker;
			};

			/**
			 * Represents resource(s) that can do work. They can also be thought of as work-queues 
			 * with specific resources assigned to them.
			 */
			class Worker {
			public:
				ENGINE_API virtual ~Worker() NOTHROWS;

				/**
				 * Schedule the work.
				 *
				 * @param work the work to be scheduled
				 */
				ENGINE_API virtual TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS = 0;
			};

			/**
			 * A special Worker that may be externally controlled.
			 */
			class ControlWorker : public Worker {
			public:
				ENGINE_API virtual ~ControlWorker() NOTHROWS;

				/**
				 * Do work until no work exists or millisecondsLimit is met or exceeded.
				 */
				ENGINE_API virtual TAKErr doAnyWork(int64_t millisecondLimit) NOTHROWS = 0;

				/**
				 * Do work until millesecondsLimit is met or exceeded.
				 */
				ENGINE_API virtual TAKErr doAllWork(int64_t millisecondLimit) NOTHROWS = 0;

				/**
				 * Interrupt the work being done.
				 */
				ENGINE_API virtual TAKErr interrupt() NOTHROWS = 0;
			};


			/**
			 * Create a worker backed by a single thread
			 *
			 * @param worker OUT the resulting worker
			 *
			 * @return TE_Ok on success
			 */
			ENGINE_API TAKErr Worker_createThread(SharedWorkerPtr &worker) NOTHROWS;
			
			/**
			 * Create a fixed thread pool worker
			 *
			 * @param worker OUT the resulting worker
			 * @param threadCount the number of desired threads
			 *
			 * @return TE_Ok on success
			 */
			ENGINE_API TAKErr Worker_createFixedThreadPool(SharedWorkerPtr &worker, size_t threadCount) NOTHROWS;

			/**
			 * Create a worker backed by a set of threads
			 *
			 * @param worker OUT the resulting worker
			 *
			 * @return TE_Ok on success
			 */
			ENGINE_API TAKErr Worker_createThreadPool(SharedWorkerPtr &worker, size_t minThreadCount, size_t maxThreadCount, int64_t keepAliveMillis) NOTHROWS;

			/**
			 * Create worker that may be externally controlled
			 *
			 * @param worker OUT the resulting worker
			 *
			 * @return TE_Ok on success
			 */
			ENGINE_API TAKErr Worker_createControlWorker(std::shared_ptr<ControlWorker> &controlWorker) NOTHROWS;

			/**
			 * A thread-pool based worker that can "flex" up and down based on need. It is best to
			 * use this for tasks that block and wait on things (like IO), or very short lived non-taxing
			 * tasks.
			 */
			ENGINE_API const SharedWorkerPtr& GeneralWorkers_flex() NOTHROWS;

			/**
			 * A single thread always ready. It is good to use this for tasks that must be serialized, or
			 * expensive tasks when GeneralWorkers_cpu() chokes out performance too much.
			 */
			ENGINE_API const SharedWorkerPtr& GeneralWorkers_single() NOTHROWS;

			/**
			 * General purpose Worker for handling more expensive computation type tasks that are
			 * bound to the CPU and therefore a backlog develops when the CPU (this worker) is busy 
			 * doing other work. Generally, this is a fixed thread-pool with the same number of threads
			 * as CPU cores.
			 */
			ENGINE_API const SharedWorkerPtr& GeneralWorkers_cpu() NOTHROWS;

			/**
			 * Create a new thread for each task.
			 */
			ENGINE_API SharedWorkerPtr GeneralWorkers_newThread() NOTHROWS;

			/**
			 * Do the task immediately within the context it arrives.
			 */
                        ENGINE_API const SharedWorkerPtr& GeneralWorkers_immediate() NOTHROWS;
		}
	}
}

#endif