
#ifndef TAK_ENGINE_UTIL_COPYONWRITECONTAINERS_H_INCLUDED
#define TAK_ENGINE_UTIL_COPYONWRITECONTAINERS_H_INCLUDED

#include "util/Error.h"
#include "thread/Mutex.h"
#include "thread/MutexGuard.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            /**
             * Provides a basic copy-on-write interface for efficient implementations of rare
             * writes and many concurrent reads
             */
            template <typename T>
            class CopyOnWrite {
            public:
                CopyOnWrite() : item(std::make_shared<T>()) { }
                CopyOnWrite(const CopyOnWrite &) = delete;
                CopyOnWrite(CopyOnWrite &&) = delete;

                void operator=(const CopyOnWrite &) = delete;

                /**
                 * Call a TAKErr return based write method and maintain a NOTHROWS guarantee by trapping any
                 * exceptions
                 */
                template <typename ...Args>
                TAK::Engine::Util::TAKErr invokeWrite(TAK::Engine::Util::TAKErr(T::*method)(Args...), Args &&...args) NOTHROWS;

                template <typename ...Args>
                TAK::Engine::Util::TAKErr invokeWrite(TAK::Engine::Util::TAKErr(T::*method)(Args...), Args ...args) NOTHROWS;

                const std::shared_ptr<const T> &read() const NOTHROWS;

            private:
                mutable TAK::Engine::Thread::Mutex mutex;
                std::shared_ptr<const T> item;
                std::shared_ptr<const T> empty;
            };

            //
            // CopyOnWrite<T> impl
            //

            template <typename T>
            template <typename ...Args>
            TAK::Engine::Util::TAKErr CopyOnWrite<T>::invokeWrite(TAK::Engine::Util::TAKErr(T::*method)(Args...), Args &&...args) NOTHROWS {
                TAK::Engine::Util::TAKErr code(mutex.lock());
                if (code == TE_Ok) {
                    TAK::Engine::Thread::MutexGuard guard(mutex);
                    TE_BEGIN_TRAP() {
                        std::shared_ptr<T> newItem;
                        if (item) {
                            newItem = std::make_shared<T>(*item);
                        }
                        else {
                            newItem = std::make_shared<T>();
                        }
                        code = (newItem.get()->*method)(std::forward<Args>(args)...);
                        TE_CHECKRETURN_CODE(code);
                        item = std::move(newItem);
                    } TE_END_TRAP(code);
                }
                return code;
            }

            template <typename T>
            template <typename ...Args>
            TAK::Engine::Util::TAKErr CopyOnWrite<T>::invokeWrite(TAK::Engine::Util::TAKErr(T::*method)(Args...), Args ...args) NOTHROWS {
                TAK::Engine::Util::TAKErr code(mutex.lock());
                if (code == TE_Ok) {
                    TAK::Engine::Thread::MutexGuard guard(mutex);
                    TE_BEGIN_TRAP() {
                        std::shared_ptr<T> newItem;
                        if (item) {
                            newItem = std::make_shared<T>(*item);
                        }
                        else {
                            newItem = std::make_shared<T>();
                        }
                        code = (newItem.get()->*method)(std::forward<Args>(args)...);
                        TE_CHECKRETURN_CODE(code);
                        item = std::move(newItem);
                    } TE_END_TRAP(code);
                }
                return code;
            }

            template <typename T>
            const std::shared_ptr<const T> &CopyOnWrite<T>::read() const NOTHROWS {
                TAK::Engine::Util::TAKErr code(mutex.lock());
                if (code == TE_Ok) {
                    TAK::Engine::Thread::MutexGuard guard(mutex);
                    return item;
                }
                return empty;
            }
        }
    }
}

#endif
