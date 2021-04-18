
//#include <chrono>
#include "thread/Thread.h"
#include "util/Work.h"
#include "port/Platform.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Thread;

//
// Work
//

Work::Work() NOTHROWS : state_(Pending), result_code_(TE_Ok) {}

Work::~Work() NOTHROWS
{ }

TAKErr Work::signalWork() NOTHROWS {
	MonitorLockPtr lockPtr(nullptr, nullptr);
	TAKErr code = beginWorking(lockPtr);
	if (code == TE_Ok) {
		TAKErr resCode = this->onSignalWork(lockPtr);
		if (!lockPtr.get())
			MonitorLock_create(lockPtr, this->monitor_);
		code = finishWorking(lockPtr, resCode);
	}
	return code;
}

TAKErr Work::preempt(TAKErr because) NOTHROWS {
	MonitorLockPtr lockPtr(nullptr, nullptr);
	TAKErr code = beginWorking(lockPtr);
	if (code == TE_Ok) {
		code = finishWorking(lockPtr, because);
	}
	return code;
}

TAKErr Work::isDone(bool &done, TAKErr *resultCode) const NOTHROWS {

	Thread::MonitorLockPtr lockPtr(nullptr, nullptr);
	TAKErr code = beginSync(lockPtr);
	if (code != TE_Ok)
		return code;

	done = (this->state_ == Done);
	if (resultCode)
		*resultCode = this->result_code_;

	return TE_Ok;
}

TAKErr Work::awaitDone(TAKErr &err) NOTHROWS {
	Thread::MonitorLockPtr lockPtr(nullptr, nullptr);
	return this->awaitDone(lockPtr, err);
}

TAKErr Work::beginWorking(MonitorLockPtr &lockPtr) NOTHROWS {
	TAKErr code = beginSync(lockPtr);
	if (code != TE_Ok)
		return code;

	int st = this->state_;
	if (st != Pending) {
		lockPtr.reset(nullptr);
		return st == Working ? TE_Busy : TE_Done;
	}

	this->state_ = Working;
	return TE_Ok;
}

TAKErr Work::awaitDone(MonitorLockPtr &lockPtr, TAKErr &resultCode) NOTHROWS {
	TAKErr code = this->beginSync(lockPtr);
	if (code != TE_Ok)
		return code;

	while (this->state_ != Done)
		lockPtr->wait();

	resultCode = this->result_code_;
	return code;
}

TAKErr Work::syncUndone(MonitorLockPtr &lockPtr) NOTHROWS {
	TAKErr code = this->beginSync(lockPtr);
	if (code != TE_Ok)
		return code;

	if (this->state_ != Done)
		return TE_Ok;

	lockPtr.reset(nullptr);
	return TE_Done;
}

TAKErr Work::beginSync(MonitorLockPtr &lockPtr) const NOTHROWS {
	TAKErr code = Thread::MonitorLock_create(lockPtr, this->monitor_);
	TE_CHECKRETURN_CODE(code);
	return code;
}

TAKErr Work::finishWorking(MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS {

	if (lockPtr.get() == nullptr || this->state_ != Working)
		return TE_IllegalState;

	this->state_ = Done;
	this->result_code_ = result;
	lockPtr->broadcast();
	return this->onDone(lockPtr, result);
}

TAKErr Work::onSignalWork(MonitorLockPtr &lockPtr) NOTHROWS {
	return TE_Ok;
}

TAKErr Work::onDone(MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS {
	return TE_Ok;
}

//
// ExtendableWork
//

ExtendableWork::~ExtendableWork() NOTHROWS
{ }

TAKErr ExtendableWork::attachWork(const std::shared_ptr<Work> &work) NOTHROWS {

	TAKErr code = TE_Ok;

	{
		MonitorLockPtr lockPtr(nullptr, nullptr);

		code = syncUndone(lockPtr);
		if (code == TE_Ok) {
			// Most only have one attachment
			if (this->attachedWork.size() == 0)
				this->attachedWork.reserve(1);

			this->attachedWork.push_back(work);
		}
	}

	if (code == TE_Done)
		code = work->signalWork();

	return code;
}

TAKErr ExtendableWork::onDone(MonitorLockPtr &lockPtr, TAKErr result) NOTHROWS {
	std::vector<std::shared_ptr<Work>> work(std::move(this->attachedWork));
	lockPtr.reset(nullptr);

	if (result != TE_Canceled) {
		for (auto &w : work) {
			w->signalWork();
		}
	} else {
		for (auto &w : work) {
			w->preempt(TE_Canceled);
		}
	}
	
	return TE_Ok;

}

//
// TransferWork
//

TransferWork::TransferWork(std::shared_ptr<Work> work, std::shared_ptr<Worker> worker) NOTHROWS :
work(work),
worker(worker)
{ }

TransferWork::~TransferWork() NOTHROWS
{ }

TAKErr TransferWork::onSignalWork(MonitorLockPtr &lockPtr) NOTHROWS {
	return worker->scheduleWork(this->work);
}

//
// Worker
//

Worker::~Worker() NOTHROWS
{ }

//
// ControlWorker
//

ControlWorker::~ControlWorker() NOTHROWS
{ }

namespace {
	class ControlQueue {
	public:
		struct Stats {
			size_t waitingCount;
			size_t threadCount;
		};

		ControlQueue() NOTHROWS;
		~ControlQueue() NOTHROWS;
		TAKErr queueWork(std::shared_ptr<Work> &&work, Stats *optStats = nullptr) NOTHROWS;
		TAKErr awaitWork(std::shared_ptr<Work> &workPtr, int64_t milliLimit) NOTHROWS;
		TAKErr takeWork(std::shared_ptr<Work> &workPtr) NOTHROWS;
		TAKErr interrupt() NOTHROWS;
		TAKErr cap() NOTHROWS;
		TAKErr capAndInterrupt() NOTHROWS;
		TAKErr attachThread() NOTHROWS;
		TAKErr detachThread() NOTHROWS;

	private:
		Monitor monitor;
		std::deque<std::shared_ptr<Work>> workQueue;
		Stats stats;
		bool interrupted;
		bool capped;
	};

	class ControlWorkerImpl : public ControlWorker {
	public:
		ControlWorkerImpl(const std::shared_ptr<ControlQueue> &controlQueue) NOTHROWS;

		~ControlWorkerImpl() NOTHROWS override;
		TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS override;
		TAKErr doAnyWork(int64_t millisecondLimit) NOTHROWS override;
		TAKErr doAllWork(int64_t millisecondLimit) NOTHROWS override;

		ENGINE_API TAKErr interrupt() NOTHROWS override;

	private:
		std::shared_ptr<ControlQueue> controlQueue;
	};

	class ThreadWorker : public Worker {
	public:
		static TAKErr create(std::shared_ptr<ThreadWorker> &workerPtr, const std::shared_ptr<ControlQueue> &controlQueue, 
			int64_t keepAliveMillis = INT64_MAX,
			bool shouldCap = true,
			bool shouldInterrupt = false) NOTHROWS;
		TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS override;
		~ThreadWorker() NOTHROWS override;

		static TAKErr spawnThread(ThreadPtr &thread, const std::shared_ptr<ControlQueue> &controlQueue, int64_t keepAliveMillis) NOTHROWS;

	private:
		ThreadWorker(const std::shared_ptr<ControlQueue> &queue, int64_t keepAliveMillis, bool shouldCap, bool shouldInterrupt) NOTHROWS;
		static void *threadStart(void *threadData);
		ThreadPtr threadPtr;
		std::shared_ptr<ControlQueue> controlQueue;
		int64_t keepAliveMillis;
		bool shouldCap;
		bool shouldInterrupt;

		struct ThreadArgs {
			std::shared_ptr<ControlQueue> controlQueue;
			int64_t keepAliveMillis;
		};
	};

	class ThreadPoolWorker : public Worker {
	public:
		~ThreadPoolWorker() NOTHROWS override;

		static TAKErr create(std::shared_ptr<ThreadPoolWorker> &worker, 
			size_t minThreadCount, 
			size_t maxThreadCount,
			int64_t keepAliveMillis,
			const std::shared_ptr<ControlQueue> &controlQueue) NOTHROWS;

		TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS override;

	private:
		ThreadPoolWorker(size_t minThreadCount,
			size_t maxThreadCount,
			int64_t keepAliveMillis, 
			const std::shared_ptr<ControlQueue> &queue) NOTHROWS;

		const std::shared_ptr<ControlQueue> controlQueue;
		const size_t minThreadCount;
		const size_t maxThreadCount;
		const int64_t keepAliveMillis;
	};

	class OverrideWorker : public Worker {
	public:
		explicit OverrideWorker(const SharedWorkerPtr& bw) NOTHROWS
			: backing_worker_(bw), task_num_(0) {}

		virtual TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS;
		Mutex state_mutex_;
		const SharedWorkerPtr& backing_worker_;
		std::shared_ptr<Work> ongoing_work_;
		uint64_t task_num_;
	};

	//
	// ControlQueue
	//

	ControlQueue::ControlQueue() NOTHROWS
		: stats{ 0, 0 },
	    interrupted(false),
		capped(false)
	{}

	ControlQueue::~ControlQueue() NOTHROWS
	{ }

	TAKErr ControlQueue::queueWork(std::shared_ptr<Work> &&work, Stats *optStats) NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);

		if (this->capped)
			return TE_Done;

		TE_BEGIN_TRAP() {
			workQueue.push_back(std::move(work));
		} TE_END_TRAP(code);
		TE_CHECKRETURN_CODE(code);

		if (optStats)
			*optStats = stats;

		code = lockPtr->signal();
		return code;
	}

	TAKErr ControlQueue::awaitWork(std::shared_ptr<Work> &workPtr, int64_t milliLimit) NOTHROWS {

		if (milliLimit <= 0)
			return TE_TimedOut;

		{
			MonitorLockPtr lockPtr(nullptr, nullptr);
			TAKErr code(MonitorLock_create(lockPtr, this->monitor));
			TE_CHECKRETURN_CODE(code);

			if (this->interrupted) {
				return TE_Interrupted;
			}

			while (workQueue.size() == 0) {

				if (this->capped) {
					return TE_Done;
				}

				stats.waitingCount++;
				TAKErr waitCode = lockPtr->wait(milliLimit);
				stats.waitingCount--;
				if (waitCode == TE_TimedOut)
					return waitCode;
				TE_CHECKRETURN_CODE(waitCode);

				if (this->interrupted) {
					return TE_Interrupted;
				} else if (this->capped) {
					return TE_Done;
				}
			}

			workPtr = workQueue.front();
			workQueue.pop_front();
		}

		return TE_Ok;
	}

	TAKErr ControlQueue::takeWork(std::shared_ptr<Work> &workPtr) NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);

		if (this->interrupted)
			return TE_Interrupted;

		if (workQueue.size() == 0)
			return TE_Done;

		workPtr = workQueue.front();
		workQueue.pop_front();

		return TE_Ok;
	}

	TAKErr ControlQueue::interrupt() NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);

		this->interrupted = true;
		lockPtr->broadcast();
		return TE_Ok;
	}

	TAKErr ControlQueue::cap() NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);

		this->capped = true;
		lockPtr->broadcast();
		return TE_Ok;
	}

	TAKErr ControlQueue::capAndInterrupt() NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);

		this->capped = true;
		this->interrupted = true;
		lockPtr->broadcast();
		return TE_Ok;
	}

	TAKErr ControlQueue::attachThread() NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);
		stats.threadCount++;
		return TE_Ok;
	}

	TAKErr ControlQueue::detachThread() NOTHROWS {
		MonitorLockPtr lockPtr(nullptr, nullptr);
		TAKErr code(MonitorLock_create(lockPtr, this->monitor));
		TE_CHECKRETURN_CODE(code);
		stats.threadCount--;
		return TE_Ok;
	}

	//
	// ControlWorkerImpl
	//

	ControlWorkerImpl::ControlWorkerImpl(const std::shared_ptr<ControlQueue> &controlQueue) NOTHROWS
		: controlQueue(controlQueue)
	{}

	ControlWorkerImpl::~ControlWorkerImpl() NOTHROWS
	{ }
	
	TAKErr ControlWorkerImpl::scheduleWork(std::shared_ptr<Work> work) NOTHROWS {
		return controlQueue->queueWork(std::move(work));
	}

	TAKErr ControlWorkerImpl::doAnyWork(int64_t millisecondLimit) NOTHROWS {

		int64_t last = TAK::Engine::Port::Platform_systime_millis();
		int64_t countDown = millisecondLimit;

		while (countDown > 0) {
			std::shared_ptr<Work> work;
			TAKErr code = this->controlQueue->takeWork(work);
			if (code != TE_Ok)
				return code;

			work->signalWork();

			int64_t point = TAK::Engine::Port::Platform_systime_millis();
			countDown -= (point - last);
			last = point;
		}

		return TE_TimedOut;
	}

	TAKErr ControlWorkerImpl::doAllWork(int64_t millisecondLimit) NOTHROWS {

		int64_t last = TAK::Engine::Port::Platform_systime_millis();
		int64_t countDown = millisecondLimit;

		while (countDown > 0) {
			std::shared_ptr<Work> work;
			TAKErr code = this->controlQueue->awaitWork(work, countDown);
			if (code != TE_Ok)
				return code;

			work->signalWork();

			int64_t point = TAK::Engine::Port::Platform_systime_millis();
			countDown -= (point - last);
			last = point;
		}

		return TE_TimedOut;
	}

	TAKErr ControlWorkerImpl::interrupt() NOTHROWS {
		return controlQueue->interrupt();
	}

	//
	// ThreadWorker
	//

	TAKErr ThreadWorker::create(std::shared_ptr<ThreadWorker> &workerPtr, const std::shared_ptr<ControlQueue> &controlQueue, int64_t keepAliveMillis, bool shouldCap, bool shouldInterrupt) NOTHROWS {
		std::shared_ptr<ThreadWorker> threadWorker(new ThreadWorker(controlQueue, keepAliveMillis, shouldCap, shouldInterrupt));
		TAKErr code = ThreadWorker::spawnThread(threadWorker->threadPtr, controlQueue, keepAliveMillis);
		if (code == TE_Ok)
			workerPtr = threadWorker;
		return code;
	}

	ThreadWorker::ThreadWorker(const std::shared_ptr<ControlQueue> &queue, int64_t keepAliveMillis, bool shouldCap, bool shouldInterrupt) NOTHROWS
		: threadPtr(nullptr, nullptr),
		controlQueue(queue),
		keepAliveMillis(keepAliveMillis),
		shouldCap(shouldCap),
		shouldInterrupt(shouldInterrupt)
	{}

	ThreadWorker::~ThreadWorker() {
		if (shouldCap && shouldInterrupt)
			controlQueue->capAndInterrupt();
		else if (shouldCap)
			controlQueue->cap();
		else if (shouldInterrupt)
			controlQueue->interrupt();
	}

	TAKErr ThreadWorker::spawnThread(ThreadPtr &threadPtr, const std::shared_ptr<ControlQueue> &controlQueue, int64_t keepAliveMillis) NOTHROWS {
		std::unique_ptr<ThreadArgs> threadArgs(new ThreadArgs{ controlQueue, keepAliveMillis });
		TAKErr code = Thread_start(threadPtr, threadStart, threadArgs.get());
		if (code != TE_Ok)
			return code;

		threadPtr->detach();
		threadArgs.release();

		return TE_Ok;
	}

	TAKErr ThreadWorker::scheduleWork(std::shared_ptr<Work> work) NOTHROWS {
		TAKErr code = controlQueue->queueWork(std::move(work));
		if (code == TE_Interrupted) {
			return TE_Ok;
		}
		return code;
	}

	void *ThreadWorker::threadStart(void *opaque) {

		std::unique_ptr<ThreadArgs> threadArgs(static_cast<ThreadArgs *>(opaque));
		std::shared_ptr<ControlQueue> controlQueue = threadArgs->controlQueue;
		controlQueue->attachThread();
		std::size_t keepAliveMillis = (threadArgs->keepAliveMillis > 0LL) ? static_cast<std::size_t>(threadArgs->keepAliveMillis) : 0LL;
		threadArgs.reset();

		std::shared_ptr<Work> work;
		while (controlQueue->awaitWork(work, keepAliveMillis) == TE_Ok) {
			if (work) {
				work->signalWork();
				work.reset();
			}
		}
		controlQueue->detachThread();

		return nullptr;
	}

	//
	// ThreadPoolWorker
	//

	TAKErr ThreadPoolWorker::create(std::shared_ptr<ThreadPoolWorker> &worker, 
		size_t minThreadCount,
		size_t maxThreadCount,
		int64_t keepAliveMillis, 
		const std::shared_ptr<ControlQueue> &controlQueue) NOTHROWS {

		std::shared_ptr<ThreadPoolWorker> threadWorker(new ThreadPoolWorker(minThreadCount, maxThreadCount, keepAliveMillis, controlQueue));
		for (size_t i = 0; i < minThreadCount; ++i) {
			ThreadPtr threadPtr(nullptr, nullptr);
			TAKErr code = ThreadWorker::spawnThread(threadPtr, controlQueue, INT64_MAX);
			if (code != TE_Ok) {
				controlQueue->interrupt();
				return code;
			}
		}
		
		worker = threadWorker;
		return TE_Ok;
	}

	ThreadPoolWorker::ThreadPoolWorker(size_t minThreadCount,
		size_t maxThreadCount,
		int64_t keepAliveMillis, 
		const std::shared_ptr<ControlQueue> &controlQueue) NOTHROWS
		: minThreadCount(minThreadCount),
		maxThreadCount(maxThreadCount),
		keepAliveMillis(keepAliveMillis),
		controlQueue(controlQueue)
	{}

	ThreadPoolWorker::~ThreadPoolWorker() NOTHROWS {
		controlQueue->cap();
	}

	TAKErr ThreadPoolWorker::scheduleWork(std::shared_ptr<Work> work) NOTHROWS {
		ControlQueue::Stats stats;
		TAKErr code = controlQueue->queueWork(std::move(work), &stats);
		if (code == TE_Ok && 
			stats.waitingCount == 0 &&
			stats.threadCount < this->maxThreadCount) {
			ThreadPtr threadPtr(nullptr, nullptr);
			code = ThreadWorker::spawnThread(threadPtr, this->controlQueue, this->keepAliveMillis);
			TE_CHECKRETURN_CODE(code);
		}

		return code;
	}

	//
	// OverrideWorker
	//

	TAKErr OverrideWorker::scheduleWork(std::shared_ptr<Work> work) NOTHROWS {
		
		if (!work)
			return TE_InvalidArg;

		{
			Lock lock(state_mutex_);
			if (lock.status != TE_Ok)
				return lock.status;

			task_num_++;

			if (ongoing_work_) {
				ongoing_work_->preempt(TE_Canceled);
				ongoing_work_ = work;
			}
		}

		// backing_worker_ isn't transient-- don't need lock
		return this->backing_worker_->scheduleWork(work);
	}
}

TAKErr TAK::Engine::Util::Worker_createThread(SharedWorkerPtr &worker) NOTHROWS {
	std::shared_ptr<ControlQueue> controlQueue(new ControlQueue());
	std::shared_ptr<ThreadWorker> threadWorker;
	TAKErr code = ThreadWorker::create(threadWorker, controlQueue);
	if (code == TE_Ok)
		worker = threadWorker;
	return code;
}

TAKErr TAK::Engine::Util::Worker_createFixedThreadPool(SharedWorkerPtr &worker, size_t threadCount) NOTHROWS {
	std::shared_ptr<ControlQueue> controlQueue(new ControlQueue());
	std::shared_ptr<ThreadPoolWorker> threadWorker;
	TAKErr code = ThreadPoolWorker::create(threadWorker, threadCount, threadCount, INT64_MAX, controlQueue);
	if (code == TE_Ok)
		worker = threadWorker;
	return code;
}

TAKErr TAK::Engine::Util::Worker_createControlWorker(std::shared_ptr<ControlWorker> &controlWorker) NOTHROWS {
	std::shared_ptr<ControlQueue> controlQueue(new ControlQueue());
	controlWorker = std::make_shared<ControlWorkerImpl>(controlQueue);
	return TE_Ok;
}

TAKErr TAK::Engine::Util::Worker_createThreadPool(SharedWorkerPtr &worker, size_t minThreadCount, size_t maxThreadCount, int64_t keepAliveMillis) NOTHROWS {
	std::shared_ptr<ControlQueue> controlQueue(new ControlQueue());
	std::shared_ptr<ThreadPoolWorker> threadWorker;
	TAKErr code = ThreadPoolWorker::create(threadWorker, minThreadCount, maxThreadCount, INT64_MAX, controlQueue);
	if (code == TE_Ok)
		worker = threadWorker;
	return code;
}

TAKErr TAK::Engine::Util::Worker_createOverrideTasker(SharedWorkerPtr& result,
	const uint64_t** task_num_address, const SharedWorkerPtr& dest_worker) NOTHROWS {

	std::shared_ptr<OverrideWorker> overrideWorker = std::make_shared<OverrideWorker>(dest_worker);
	if (task_num_address)
		*task_num_address = &overrideWorker->task_num_;

	result = overrideWorker;
	return TE_Ok;
}

//
// GlobalWorkers
//

SharedWorkerPtr makeFixedWorker(size_t threadCount) {
	std::shared_ptr<Worker> result;
	Worker_createThreadPool(result, threadCount, threadCount, INT64_MAX);
	return result;
}

SharedWorkerPtr makeFlexWorker() {
	std::shared_ptr<Worker> result;
	Worker_createThreadPool(result, 0, 32, 60 * 1000);
	return result;
}



const SharedWorkerPtr& TAK::Engine::Util::GeneralWorkers_flex() NOTHROWS {
	static SharedWorkerPtr inst = makeFlexWorker();
	return inst;
}

const SharedWorkerPtr& TAK::Engine::Util::GeneralWorkers_single() NOTHROWS {
	static SharedWorkerPtr inst = makeFixedWorker(1);
	return inst;
}

const SharedWorkerPtr& TAK::Engine::Util::GeneralWorkers_cpu() NOTHROWS {
	static SharedWorkerPtr inst = makeFixedWorker(4);
	return inst;
}

SharedWorkerPtr TAK::Engine::Util::GeneralWorkers_newThread() NOTHROWS {
	SharedWorkerPtr inst;
	Worker_createThread(inst);
	return inst;
}

class ImmediateWorker : public Worker {
public:
	~ImmediateWorker() NOTHROWS override { }
	TAKErr scheduleWork(std::shared_ptr<Work> work) NOTHROWS override {
		return work->signalWork();
	}
};

const SharedWorkerPtr& TAK::Engine::Util::GeneralWorkers_immediate() NOTHROWS {
	static SharedWorkerPtr inst = std::make_shared<ImmediateWorker>();
	return inst;
}