
#ifndef ATAKMAP_UTIL_FUTURETASK_H_INCLUDED
#define ATAKMAP_UTIL_FUTURETASK_H_INCLUDED

#include <sstream>

#include "port/Platform.h"
#include "port/String.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "thread/Cond.h"
#include "util/AtomicRefCountable.h"
#include "util/NonCopyable.h"

namespace atakmap {
    namespace util {

        /**
         * A sharable state flag. This state is atomically reference counted and
         * may be shared between threads threads.
         */
        class ENGINE_API SharedState : public AtomicRefCountable, private TAK::Engine::Util::NonCopyable {
        public:
            enum {
                /**
                 * The starting state (nothing has happened)
                 */
                Initial,

                /**
                 * Indicates processing has begun
                 */
                Processing,

                /**
                 * Indicates processing has finished successfully
                 */
                Complete,

                /**
                 * Indicates state was canceled before or during processing
                 */
                Canceled,

                /**
                 * Indicates processing has finished resulting in error
                 */
                Error
            };

            /**
             * Initialize to 'Initial' state value
             */
            SharedState();

            /**
             * Initialize to specified state value
             */
            explicit SharedState(int state);

            /**
             * Destroy
             */
            virtual ~SharedState() NOTHROWS;

            /**
             * Atomically get the state value
             */
            int getState() const;

            /**
             * Atomically set the state if the state change is supported as specified
             * by supportsStateNoSync. State changes will only happen if they are allowed
             * and only 1 thread wins the change to Processing.
             *
             * Acceptable state changes:
             *   Initial -> Processing
             *   Initial -> Canceled
             *   Processing -> Canceled
             *   Processing -> Complete
             *   Processing -> Error
             */
            bool setState(int state);

            /**
             * Block until one of any of the specified states are reached
             *
             * @param states - an array of state values
             * @params count - the number of state values
             *
             * @return the value of which of the states was reached
             */
            int awaitAny(const int *states, int count);

            /**
             * Atomically check if a state change is supported. This is most useful
             * for checking if a state can be canceled-- particularly that it may
             * never be canceled in the future.
             */
            bool supportsState(int state) const;

			/*
			* Noncopyable 
			*/
			SharedState(const SharedState&) = delete;
			SharedState &operator=(const SharedState&) = delete;

        protected:
            virtual bool supportsStateNoSync(int state) const;

        private:
            mutable TAK::Engine::Thread::Mutex mutex_;
            mutable TAK::Engine::Thread::CondVar change_cv_;
            int state_;
        };

        class ENGINE_API FutureError : public std::exception {
        public:
            FutureError();
            FutureError(const char *message);
            virtual ~FutureError() NOTHROWS;
            virtual const char *what() const NOTHROWS override;

        private:
            std::string message;
        };

        template <typename T> class FutureImpl;

        /**
         * A reference to a SharedState with an interface to check if an ongoing operation
         * has been canceled, is able to be canceled, or should be canceled. This should
         * generally be used for cooperative tasking.
         */
        class ENGINE_API CancelationToken {
        public:
            /**
             *
             */
            CancelationToken();

            /**
             * Initialize with the shared state this token monitors
             */
            explicit CancelationToken(const std::shared_ptr<SharedState> &sharedState);

            /**
             * Check if the operation is canceled
             */
            bool isCanceled() const;

            /**
             * Check if the operation is able to be canceled
             */
            bool isCancelSupported() const;

        private:
            std::shared_ptr<SharedState> sharedState;
        };

        /**
         * The base implementation for the shared state of a Future<T>, FutureTask<T>,
         * or Promise<T>.
         */
        template <typename T>
        class FutureImpl : public SharedState {
        public:
            FutureImpl() { }

            virtual ~FutureImpl() NOTHROWS { }

            const T &get() {
                const int doneStates[] = { Complete, Error, Canceled };
                int state = this->awaitAny(doneStates, 3);
                if (state == Canceled) {
                    throw FutureError("canceled");
                } else if (state == Error) {
                    if (this->message_) {
                        throw FutureError(this->message_.get());
                    } else {
                        throw FutureError();
                    }
                }
                return value_;
            }

            // Only to be called by the thread that initiated the Processing state
            bool completeProcessing(const T &value) {
                bool result = false;
                try {
                    this->value_ = value;
                    result = setState(Complete);
                } catch (const std::exception &e) {
                    this->message_ = e.what();
                    this->setState(Error);
                } catch (...) {
                    this->message_ = "unknown exception";
                    this->setState(Error);
                }
                return result;
            }

            // Only to be called by the thread that intiated the Processing state
            void completeProcessingWithError(const char *message) {
                this->message_ = message;
                this->setState(Error);
            }

        private:
            T value_;
            TAK::Engine::Port::String message_;
        };

        //
        // Future
        //

        template <typename T>
        class Future {
        public:

            Future() { }

            explicit Future(const std::shared_ptr<FutureImpl<T>> &impl)
            : impl(impl) { }

        public:
            T get() { return impl->get(); }

            int getState() const { return impl->getState(); }

            bool cancel() { return impl->setState(SharedState::Canceled); }

            bool isDone() const {
                int state = impl->getState();
                return state != SharedState::Initial && state != SharedState::Processing;
            }

            bool valid() const { return impl.get() != nullptr; }

            CancelationToken getCancelationToken() { return CancelationToken(impl); }

            void clear() { impl.reset(); }

        private:
            std::shared_ptr<FutureImpl<T>> impl;
        };

        //
        // FutureTaskImpl
        //

        template <typename T>
        class FutureTaskImpl : public FutureImpl<T> {
        public:
            FutureTaskImpl() { }

            virtual ~FutureTaskImpl() { }

            virtual bool runImpl() = 0;
        };

        //
        // FutureTask
        //

        template <typename T>
        class FutureTask {
        private:
            class FuncPtrImpl : public FutureTaskImpl<T> {
            public:
                FuncPtrImpl(T (*func)(void *), void *opaque)
                : func(func), opaque(opaque) { }

                virtual ~FuncPtrImpl() { }

                virtual bool runImpl() {
                    return this->completeProcessing(func(opaque));
                }

            private:
                T (*func)(void *);
                void *opaque;
            };

            template <typename F>
            class FunctorImpl : public FutureTaskImpl<T> {
            public:
                FunctorImpl(const F &f) : ftor(f) { }

                virtual ~FunctorImpl() { }

                virtual bool runImpl() {
                    return this->completeProcessing(ftor());
                }

            private:
                F ftor;
            };

        public:
            FutureTask() { }

            explicit FutureTask(const std::shared_ptr<FutureTaskImpl<T>> &futureTaskImpl)
            : impl(futureTaskImpl) { }

            explicit FutureTask(T (*func)(void *), void *opaque)
            : impl(std::shared_ptr<FutureTaskImpl<T>>(new FuncPtrImpl(func, opaque))) { }

            FutureTask(const FutureTask<T> &other)
            : impl(other.impl) { }

            template<typename F>
            explicit FutureTask(const F &ftor)
            : impl(std::shared_ptr<FutureTaskImpl<T>>(new FunctorImpl<F>(ftor))) { }

            inline Future<T> getFuture() const { return Future<T>(impl); }

            bool run() {
                bool result = false;
                if (impl->setState(SharedState::Processing)) {
                    try {
                        result = impl->runImpl();
                    } catch (const std::exception &e) {
                        impl->completeProcessingWithError(e.what());
                    } catch (...) {
                        impl->completeProcessingWithError("unknown exception");
                    }
                }
                return result;
            }

            inline bool valid() const { return impl.get() != nullptr; }

            CancelationToken getCancelationToken() { return CancelationToken(impl); }

            inline void clear() { impl.reset(); }

        private:
            std::shared_ptr<FutureTaskImpl<T>> impl;
        };

        //
        // Promise
        //

        template <typename T>
        class Promise {
        public:
            Promise()
            : impl(std::shared_ptr<FutureImpl<T>>(new FutureImpl<T>())) { }

            void setValue(const T &value) {
                if (impl->setState(SharedState::Processing)) {
                    impl->completeProcessing(value);
                }
            }
            void setError() { impl->completeProcessingWithError(""); }
            void cancel() { impl->cancel(); }
            Future<T> getFuture() { return Future<T>(impl); }

        private:
            std::shared_ptr<FutureImpl<T>> impl;
        };
    }
}

#endif
