
#ifndef TAK_ENGINE_UTIL_TASKING_H_INCLUDED
#define TAK_ENGINE_UTIL_TASKING_H_INCLUDED

#include "util/Work.h"

namespace TAK {
	namespace Engine {
		namespace Util {

			// Forward decls
			template <typename T> class FutureTask;
			template <typename T> class AsyncResult;
			template <typename T> class WeakFuture;
			
			template <typename Func>
			struct TaskResultOf;

			class VoidTaskResult {};

			template <typename R, typename ...Args>
			struct TaskResultOf<TAKErr(*)(R &, Args...)> {
				typedef R Type;
			};

			template <typename A0, typename ...Args>
			struct TaskResultOf<TAKErr(*)(const A0&, Args...)> {
				typedef VoidTaskResult Type;
			};

			template <typename Func>
			using TaskResultOfT = typename TaskResultOf<Func>::Type;

			/**
			 * A pending result
			 */
			template <typename T>
			class Future {
			public:
				Future() NOTHROWS;

				explicit Future(std::shared_ptr<AsyncResult<T>> asyncResult) NOTHROWS;

				/**
				 * Check if result is ready
				 */
				TAKErr isReady(bool &ready, TAKErr *resultCode = nullptr) const NOTHROWS;

				/**
				 * Await a result (blocking if not yet ready)
				 */
				TAKErr await(T &value, TAKErr &err) NOTHROWS;

				void detach() NOTHROWS { impl = nullptr; }

				operator bool() const NOTHROWS { return impl != nullptr; }

				/**
				 * Schedule a task on the a worker when this future is ready.
				 *
				 * @param worker the worker func will be called 
				 * @param func the function or functor to be called of signature TAKErr (Output, Input, Args)
				 * @param args arguments packaged up to be passed as tail arugments to func
				 *
				 * @return a FutureTask<T> that represents the newly scheduled task
				 *
				 * example:
				 *    TAKErr add(int &output, int input) { output += input; return TE_Ok; }
				 *
				 *    Future<int> f = ...
				 *
				 *    f.thenOn(GeneralWorkers_single(), add, 2)  // add(result, 2)
				 *     .thenOn(GeneralWorkers_single(), add, 5); // add(result, 5)
				 */
				template <typename Func, typename ...Args>
				FutureTask<TaskResultOfT<Func>>
					thenOn(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS;

				/**
				 * Schedule on another worker an error trap that is called if this task produces an error.
				 * The trap function (or functor) can return an error iself OR produce an output of type T
				 * in which case TE_Ok should be returned.
				 *
				 * @param worker the worker func will be called
				 * @param func the function or functor to be called of signature TAKErr (T, TAKErr, Args...)
				 * @param args arguments packaged up to be passed as tail arugments to func
				 *
				 * @return a FutureTask<T> that represents the newly scheduled trap
				 */
				template<typename Func, typename ...Args>
				FutureTask<TaskResultOfT<Func>>
					trapOn(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS;

			protected:
				std::shared_ptr<AsyncResult<T>> impl;
				friend class WeakFuture<T>;
			};

			/**
			 * A Future<T> that represents a pending task on a TaskQueue and may be canceled
			 */
			template <typename T>
			class FutureTask : public Future<T> {
			public:
				FutureTask() NOTHROWS;

				explicit FutureTask(std::shared_ptr<AsyncResult<T>> asyncResult, SharedWorkerPtr worker) NOTHROWS;

				/**
				 * Cancel the pending task. All subsequent tasks are canceled.
				 */
				TAKErr cancel() NOTHROWS;

				void detach() NOTHROWS { Future<T>::detach();  worker = nullptr; }

				/**
				 * Schedule a task on the same worker when this future is ready.
				 *
				 * @param func the function or functor to be called TAKErr (Output, Input, Args)
				 *
				 * @return a FutureTask<T> that represents the newly scheduled task
				 *
				 * example:
				 *    TAKErr add(int &output, int input) { output += input; return TE_Ok; }
				 *
				 *    Future<int> f = ...
				 *
				 *    f.then(add, 2)  // add(result, 2)
				 *     .then(add, 5); // add(result, 5)
				 */
				template <typename Func, typename ...Args>
				FutureTask<TaskResultOfT<Func>>
					then(Func func, Args &&...args) NOTHROWS;

				/**
				 * Schedule a trap onthe same worker that is called if this task produces an error
				 *
				 * @param worker the worker func will be called
				 * @param func the function or functor to be called of signature TAKErr (Output, TAKErr, Args)
				 *
				 * @return a FutureTask<T> that represents the newly scheduled trap
				 */
				template<typename Func, typename ...Args>
				FutureTask<TaskResultOfT<Func>>
					trap(Func func, Args&&...args) NOTHROWS;

			private:
				SharedWorkerPtr worker;
			};

			/**
			* Schedule a starting task on a worker.
			*
			* @param worker the worker func will be called on
			* @param func the function or functor to be called
			*
			* @return a FutureTask<T> that represents the newly scheduled task
			*/
			template <typename Func, typename ...Args>
			inline FutureTask<TaskResultOfT<Func>>
				Task_begin(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS;
		}
	}
}

#include "util/TaskingDetail.h"

#endif