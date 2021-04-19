#ifndef TAK_ENGINE_UTIL_TASKINGDETAIL_H_INCLUDED
#define TAK_ENGINE_UTIL_TASKINGDETAIL_H_INCLUDED

namespace TAK {
	namespace Engine {
		namespace Util {

			namespace Tasking {
				/**
				 * Produces a default value of a type
				 */
				template <typename T>
				struct Defaulter {
					static inline T value() { return T(); }
				};

				/**
				 * Produces a default value of a unique_ptr<T, void (*)(T *)>
				 */
				template <typename T>
				struct Defaulter<std::unique_ptr<T, void(*)(T *)>> {
					static inline std::unique_ptr<T, void(*)(T *)> value() {
						return std::unique_ptr<T, void(*)(T *)>(nullptr, nullptr);
					}
				};

				/**
				 * Produces a default value of a unique_ptr<T, void (*)(const T *)>
				 */
				template <typename T>
				struct Defaulter<std::unique_ptr<T, void(*)(const T *)>> {
					static inline std::unique_ptr<T, void(*)(const T *)> value() {
						return std::unique_ptr<T, void(*)(const T *)>(nullptr, nullptr);
					}
				};

				/**
				 * Transfers value from source to destination by copying
				 */
				template <typename T>
				struct Transfer {
					static inline void invoke(T &dst, const T &src) {
						dst = src;
					}
				};

				/**
				 * Transfers unique_ptr value from source to destination by moving
				 */
				template <typename T, typename Del>
				struct Transfer<std::unique_ptr<T, Del>> {
					static inline void invoke(std::unique_ptr<T, Del> &dst, std::unique_ptr<T, Del> &src) {
						dst = std::move(src);
					}
				};


				template <typename T>
				struct Resolver {
					static TAKErr resolve(T &r, const T &v) NOTHROWS {
						r = v;
						return TE_Ok;
					}
				};

				template <typename T>
				struct Resolver<std::unique_ptr<T>> {
					static TAKErr resolve(std::unique_ptr<T>& r, std::unique_ptr<T>& v) NOTHROWS {
						r = std::move(v);
						return TE_Ok;
					}
				};

				template <typename T>
				struct Resolver<Future<T>> {
					static TAKErr resolve(T &result, Future<T> &future) NOTHROWS {
						TAKErr callCode = TE_Err;
						TAKErr code = future.await(result, callCode);
						if (code == TE_Ok)
							code = callCode;
						return code;
					}
				};

				template <typename T>
				struct Resolver<FutureTask<T>> {
					static TAKErr resolve(T& result, FutureTask<T>& future) NOTHROWS {
						TAKErr callCode = TE_Err;
						TAKErr code = future.await(result, callCode);
						if (code == TE_Ok)
							code = callCode;
						return code;
					}
				};

				template <typename T>
				struct CaptureOf {
					typedef  std::remove_const_t<std::remove_reference_t<T>> Type;
				};

				template <typename T>
				using CaptureOfT = typename CaptureOf<T>::Type;

				template <typename Func>
				struct TaskArgs { };

				template <typename R>
				struct TaskArgs<TAKErr(*)(R &)> {
					typedef R ResultType;
				};

				template <typename R, typename A0>
				struct TaskArgs<TAKErr(*)(R&, A0)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
				};

				template <typename R, typename A0, typename A1>
				struct TaskArgs<TAKErr(*)(R&, A0, A1)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
				};

				template <typename R, typename A0, typename A1, typename A2>
				struct TaskArgs<TAKErr(*)(R&, A0, A1, A2)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
					typedef CaptureOfT<A2> A2Type;
				};

				template <typename R, typename A0, typename A1, typename A2, typename A3>
				struct TaskArgs<TAKErr(*)(R&, A0, A1, A2, A3)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
					typedef CaptureOfT<A2> A2Type;
					typedef CaptureOfT<A3> A3Type;
				};

				template <typename R, typename A0, typename A1, typename A2, typename A3,
					typename A4>
				struct TaskArgs<TAKErr(*)(R&, A0, A1, A2, A3, A4)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
					typedef CaptureOfT<A2> A2Type;
					typedef CaptureOfT<A3> A3Type;
					typedef CaptureOfT<A4> A4Type;
				};

				template <typename R, typename A0, typename A1, typename A2, typename A3,
					typename A4, typename A5>
					struct TaskArgs<TAKErr(*)(R&, A0, A1, A2, A3, A4, A5)> {
					typedef R ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
					typedef CaptureOfT<A2> A2Type;
					typedef CaptureOfT<A3> A3Type;
					typedef CaptureOfT<A4> A4Type;
					typedef CaptureOfT<A5> A5Type;
				};

				template <typename Func>
				struct VoidTaskArgs { 
					typedef VoidTaskResult ResultType;
				};

				template <typename A0>
				struct VoidTaskArgs<TAKErr(*)(A0)> {
					typedef VoidTaskResult ResultType;
					typedef CaptureOfT<A0> A0Type;
				};

				template <typename A0, typename A1>
				struct VoidTaskArgs<TAKErr(*)(A0, A1)> {
					typedef VoidTaskResult ResultType;
					typedef CaptureOfT<A0> A0Type;
					typedef CaptureOfT<A1> A1Type;
				};

				template <typename Func, typename ResultType = TaskResultOfT<Func>>
				struct TaskArgsTypeOf {
					typedef TaskArgs<Func> TaskArgsType;
				};

				template <typename Func>
				struct TaskArgsTypeOf<Func, VoidTaskResult> {
					typedef VoidTaskArgs<Func> TaskArgsType;
				};

				template <typename Func>
				using TaskArgsTypeOfT = typename TaskArgsTypeOf<Func>::TaskArgsType;

				template <typename Func, typename ResultType = TaskResultOfT<Func>>
				struct Invoker {
					template <typename ...Args>
					static TAKErr invoke(Func func, Args&&...args) {
						return func(std::forward<Args>(args)...);
					}
				};

				template <typename Func>
				struct Invoker<Func, VoidTaskResult> {
					template <typename ...Args>
					static TAKErr invoke(Func func, VoidTaskResult v, Args&&...args) {
						return func(std::forward<Args>(args)...);
					}
				};

				template <typename Func, typename ...Caps>
				struct Capture;

#define CAP_ST() typedef typename TaskArgsTypeOfT<Func>::ResultType ResultType; Func func
#define CAP_IT(n) typedef typename TaskArgsTypeOfT<Func>::A##n##Type A##n##Type; typedef typename std::decay<C##n>::type C##n##Type; C##n##Type c##n
#define CAP_ID(n) A##n##Type a##n(Defaulter<A##n##Type>::value()); { TAKErr code = Resolver<C##n##Type>::resolve(a##n, c##n); if (code != TE_Ok) return code; }

				// 0 Args
				template <typename Func>
				struct Capture<Func> {
					CAP_ST();
					inline Capture(Func func) : func(func) { }
					inline TAKErr invoke(ResultType& r) NOTHROWS { return func(r); }
				};

				// 1 Arg
				template <typename Func, typename C0>
				struct Capture<Func, C0> {
					CAP_ST();
					CAP_IT(0);
					Capture(Func func, C0 c0) : func(func), c0(c0) { }
					TAKErr invoke(ResultType &r) NOTHROWS {
						CAP_ID(0);
						return Invoker<Func>::invoke(func, r, a0);
					}
				};

				// 2 Arg
				template <typename Func, typename C0, typename C1>
				struct Capture<Func, C0, C1> {
					CAP_ST();
					CAP_IT(0); CAP_IT(1);
					Capture(Func func, C0 c0, C1 c1) : func(func), c0(c0), c1(c1) { }
					TAKErr invoke(ResultType& r) NOTHROWS {
						CAP_ID(0); CAP_ID(1);
						return Invoker<Func>::invoke(func, r, a0, a1);
					}
				};

				// 3 Arg
				template <typename Func, typename C0, typename C1, typename C2>
				struct Capture<Func, C0, C1, C2> {
					CAP_ST();
					CAP_IT(0); CAP_IT(1); CAP_IT(2);

					template <typename C0F, typename C1F, typename C2F>
					Capture(Func func, C0F&& c0, C1F&& c1, C2F&& c2) 
						: func(func), c0(std::forward<C0F>(c0)), c1(std::forward<C1F>(c1)), c2(std::forward<C2F>(c2)) { }
					TAKErr invoke(ResultType& r) NOTHROWS {
						CAP_ID(0); CAP_ID(1); CAP_ID(2);
						return func(r, a0, a1, a2);
					}
				};
				
				// 4 Arg
				template <typename Func, typename C0, typename C1, typename C2, typename C3>
				struct Capture<Func, C0, C1, C2, C3> {
					CAP_ST();
					CAP_IT(0); CAP_IT(1); CAP_IT(2); CAP_IT(3);
					template <typename C0F, typename C1F, typename C2F, typename C3F>
					Capture(Func func, C0F&& c0, C1F&& c1, C2F&& c2, C3F&& c3) : func(func), c0(std::forward<C0F>(c0)), c1(std::forward<C1F>(c1)), c2(std::forward<C2F>(c2)),
					c3(std::forward<C3F>(c3)) { }
					TAKErr invoke(ResultType& r) NOTHROWS {
						CAP_ID(0); CAP_ID(1); CAP_ID(2); CAP_ID(3);
						return func(r, a0, a1, a2, a3);
					}
				};
				
				// 5 Arg
				template <typename Func, typename C0, typename C1, typename C2, typename C3, typename C4>
				struct Capture<Func, C0, C1, C2, C3, C4> {
					CAP_ST();
					CAP_IT(0); CAP_IT(1); CAP_IT(2); CAP_IT(3); CAP_IT(4);
					Capture(Func func, C0 c0, C1 c1, C2 c2, C3 c3, C4 c4) : func(func), c0(c0), c1(c1), c2(c2), c3(c3), c4(c4) { }
					TAKErr invoke(ResultType& r) NOTHROWS {
						CAP_ID(0); CAP_ID(1); CAP_ID(2); CAP_ID(3); CAP_ID(4);
						return func(r, a0, a1, a2, a3, a4);
					}
				};
				
				// 6 Arg
				template <typename Func, typename C0, typename C1, typename C2, typename C3, typename C4, typename C5>
				struct Capture<Func, C0, C1, C2, C3, C4, C5> {
					CAP_ST();
					CAP_IT(0); CAP_IT(1); CAP_IT(2); CAP_IT(3); CAP_IT(4); CAP_IT(5);
					Capture(Func func, C0 c0, C1 c1, C2 c2, C3 c3, C4 c4, C5 c5) : func(func), c0(c0), c1(c1), c2(c2), c3(c3), c4(c4), c5(c5) { }
					TAKErr invoke(ResultType& r) NOTHROWS {
						CAP_ID(0); CAP_ID(1); CAP_ID(2); CAP_ID(3); CAP_ID(4); CAP_ID(5);
						return func(r, a0, a1, a2, a3, a4, a5);
					}
				};

				template <typename Func, typename C0>
				struct Capture<Func, VoidTaskResult, C0> : public Capture<Func, C0> { };

#undef CAP_ST
#undef CAP_IT
#undef CAP_ID
			}

			/**
			 * Base class for Work that produces a result (value or error).
			 */
			template <typename T>
			class AsyncResult : public ExtendableWork {
			public:
				explicit AsyncResult(const T &initValue)
					: value_(initValue)
				{ }

				AsyncResult()
					: value_(Tasking::Defaulter<T>::value())
				{ }

				/**
				 *
				 */
				virtual ~AsyncResult() NOTHROWS;

				TAKErr await(T &value, TAKErr &err) NOTHROWS;

			protected:
				TAKErr setValue(Thread::MonitorLockPtr &lockPtr, T &&value) NOTHROWS;
				TAKErr setValue(Thread::MonitorLockPtr &lockPtr, const T &value) NOTHROWS;

			private:
				T value_;
			};

			template <typename Func, typename ...Caps>
			class AsyncTask : public AsyncResult<TaskResultOfT<Func>> {
			public:
				template <typename ...Args>
				AsyncTask(Func func, Args&&...args) NOTHROWS
					: capture(func, std::forward<Args>(args)...)
				{ }

				virtual ~AsyncTask() NOTHROWS;

			protected:
				virtual TAKErr onSignalWork(Thread::MonitorLockPtr &lockPtr) NOTHROWS;

			private:
				Tasking::Capture<Func, Caps...> capture;
			};

			template <typename Func, typename ...Caps>
			class AsyncTrap : public AsyncResult<TaskResultOfT<Func>> {
			public:
				template <typename ...Args>
				AsyncTrap(Func func, std::shared_ptr<AsyncResult<TaskResultOfT<Func>>> input, Args&&...args) NOTHROWS
					: input(input), capture(func, TE_Err, std::forward<Args>(args)...)
				{ }

				virtual ~AsyncTrap() NOTHROWS { }

			protected:
				virtual TAKErr onSignalWork(Thread::MonitorLockPtr &lockPtr) NOTHROWS {

					TaskResultOfT<Func> result(Tasking::Defaulter<TaskResultOfT<Func>>::value());
					TAKErr code = input->await(result, capture.c0);
					input.reset();

					if (code != TE_Ok)
						return code;

					if (capture.c0 != TE_Ok) {
						// release the lock before the function call
						lockPtr.reset();
						code = capture.invoke(result);
						if (code != TE_Ok)
							return code;
					}

					return this->setValue(lockPtr, std::move(result));
				}

			private:
				std::shared_ptr<AsyncResult<TaskResultOfT<Func>>> input;
				Tasking::Capture<Func, TAKErr, Caps...> capture;
			};

			template <typename T>
			class AsyncPromise : public AsyncResult<T> {
			public:
				AsyncPromise() NOTHROWS { }

				virtual ~AsyncPromise() NOTHROWS {}

				TAKErr setValue(const T& value) NOTHROWS {
					Thread::MonitorLockPtr lockPtr(nullptr, nullptr);
					TAKErr code = this->beginWorking(lockPtr);
					if (code != TE_Ok)
						return code;
					code = AsyncResult<T>::setValue(lockPtr, value);
					return AsyncResult<T>::finishWorking(lockPtr, code);
				}

				TAKErr setValue(T&& value) NOTHROWS {
					Thread::MonitorLockPtr lockPtr(nullptr, nullptr);
					TAKErr code = this->beginWorking(lockPtr);
					if (code != TE_Ok)
						return code;
					code = AsyncResult<T>::setValue(lockPtr, std::move(value));
					return AsyncResult<T>::finishWorking(lockPtr, code);
				}

			protected:
				virtual TAKErr onSignalWork(Thread::MonitorLockPtr& lockPtr) NOTHROWS {
					return TE_Ok;
				}
			};

			//
			// AsyncResult<T> definition
			//

			template <typename T>
			AsyncResult<T>::~AsyncResult() NOTHROWS
			{ }

			template <typename T>
			TAKErr AsyncResult<T>::await(T &value, TAKErr &err) NOTHROWS {

				TAKErr resCode = TE_Ok;
				Thread::MonitorLockPtr lockPtr(nullptr, nullptr);
				TAKErr code = this->awaitDone(lockPtr, resCode);
				if (code != TE_Ok)
					return code;

				err = resCode;
				if (resCode == TE_Ok)
					Tasking::Transfer<T>::invoke(value, this->value_);

				return code;
			}

			template <typename T>
			TAKErr AsyncResult<T>::setValue(Thread::MonitorLockPtr &lockPtr, T &&value) NOTHROWS {
				this->value_ = std::move(value);
				return TE_Ok;
			}

			template <typename T>
			TAKErr AsyncResult<T>::setValue(Thread::MonitorLockPtr &lockPtr, const T &value) NOTHROWS {
				this->value_ = value;
				return TE_Ok;
			}

			//
			// AsyncTask<T> definition
			//

			template <typename Func, typename ...Caps>
			AsyncTask<Func, Caps...>::~AsyncTask() NOTHROWS
			{}

			template <typename Func, typename ...Caps>
			TAKErr AsyncTask<Func, Caps...>::onSignalWork(Thread::MonitorLockPtr &lockPtr) NOTHROWS {

				// release the lock before the function call
				lockPtr.reset();

				TaskResultOfT<Func> result(Tasking::Defaulter<TaskResultOfT<Func>>::value());
				TAKErr code = capture.invoke(result);
				if (code != TE_Ok)
					return code;

				return this->setValue(lockPtr, std::move(result));
			}

			//
			// Future<T> definition
			//

			template <typename T>
			Future<T>::Future() NOTHROWS
			{ }

			template <typename T>
			Future<T>::Future(std::shared_ptr<AsyncResult<T>> asyncResult) NOTHROWS
				: impl(asyncResult)
			{ }

			template <typename T>
			TAKErr Future<T>::isReady(bool &ready, TAKErr *resultCode) const NOTHROWS {
				if (this->impl)
					return this->impl->isDone(ready, resultCode);
				return TE_IllegalState;
			}

			template <typename T>
			TAKErr Future<T>::await(T &value, TAKErr &err) NOTHROWS {
				if (this->impl)
					return this->impl->await(value, err);
				return TE_IllegalState;
			}

			template <typename T>
			TAKErr Future<T>::cancel() NOTHROWS {
				if (!this->impl)
					return TE_IllegalState;
				return this->impl->preempt(TE_Canceled);
			}

			template <typename T>
			template <typename Func, typename ...Args>
			FutureTask<TaskResultOfT<Func>>
			Future<T>::thenOn(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS {

				if (!this->impl) {
					auto error = std::make_shared<AsyncResult<TaskResultOfT<Func>>>();
					error->preempt(TE_IllegalState);
					return FutureTask<TaskResultOfT<Func>>(error, worker);
				}

				// make the transform task (first argument is future of self), and attach a transfer to worker
				auto transform = std::make_shared<AsyncTask<Func, Future<T>, Args...>>(func, Future<T>(impl), std::forward<Args>(args)...);
				auto transfer = std::make_shared<TransferWork>(transform, worker);
				impl->attachWork(transfer);
				return FutureTask<TaskResultOfT<Func>>(transform, worker);
			}

			template <typename T>
			template <typename Func, typename ...Args>
			FutureTask<TaskResultOfT<Func>>
			Future<T>::trapOn(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS {

				if (!this->impl) {
					auto error = std::make_shared<AsyncResult<TaskResultOfT<Func>>>();
					error->preempt(TE_IllegalState);
					return FutureTask<TaskResultOfT<Func>>(error, worker);
				}

				// make the transform task (first argument is future of self), and attach a transfer to worker
				auto transform = std::make_shared<AsyncTrap<Func, Args...>>(func, impl, std::forward<Args>(args)...);
				auto transfer = std::make_shared<TransferWork>(transform, worker);
				impl->attachWork(transfer);
				return FutureTask<TaskResultOfT<Func>>(transform, worker);
			}

			//
			// FutureTask<T> definition
			//

			template <typename T>
			FutureTask<T>::FutureTask() NOTHROWS
			{ }

			template <typename T>
			FutureTask<T>::FutureTask(std::shared_ptr<AsyncResult<T>> asyncResult, SharedWorkerPtr worker) NOTHROWS
				: Future<T>(asyncResult),
				worker(worker)
			{ }

			template <typename T>
			template <typename Func, typename ...Args>
			FutureTask<TaskResultOfT<Func>>
				FutureTask<T>::then(Func func, Args &&...args) NOTHROWS {
				return this->thenOn(this->worker, func, std::forward<Args>(args)...);
			}

			template <typename T>
			template <typename Func, typename ...Args>
			FutureTask<TaskResultOfT<Func>>
				FutureTask<T>::trap(Func func, Args&&...args) NOTHROWS {
				return this->trapOn(this->worker, func, std::forward<Args>(args)...);
			}

			//
			// Task_begin
			//

			template <typename Func, typename ...Args>
			inline FutureTask<TaskResultOfT<Func>>
				Task_begin(const SharedWorkerPtr &worker, Func func, Args &&...args) NOTHROWS {

				if (!worker) {
					auto error = std::make_shared<AsyncResult<TaskResultOfT<Func>>>();
					error->preempt(TE_InvalidArg);
					return FutureTask<TaskResultOfT<Func>>(error, worker);
				}

				std::shared_ptr<AsyncTask<Func, Tasking::CaptureOfT<Args>...>> work = std::make_shared<AsyncTask<Func, Tasking::CaptureOfT<Args>...>>(func, std::forward<Args>(args)...);
				worker->scheduleWork(work);
				return FutureTask<TaskResultOfT<Func>>(work, worker);
			}
		}
	}
}

#endif