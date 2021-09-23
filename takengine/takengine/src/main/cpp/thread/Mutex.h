#ifndef TAK_ENGINE_THREAD_MUTEX_H_INCLUDED
#define TAK_ENGINE_THREAD_MUTEX_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {

            class CondVar;
            class Lock;

            namespace Impl {
                class MutexImpl;
            }

            enum MutexType
            {
                /** Default, non-recursive behavior */
                TEMT_Default,
                /** Allows recursive locks by owning thread */
                TEMT_Recursive,
            };

            class ENGINE_API Mutex
            {
            public :
                Mutex(const MutexType type = TEMT_Default) NOTHROWS;
            private :
                Mutex(const Mutex &) NOTHROWS;
            public :
                ~Mutex() NOTHROWS;
            public :
                /**
                 * Locks the mutex.
                 *
                 * @return  TE_Ok on success, other codes on failure.
                 */
                Util::TAKErr lock() NOTHROWS;
                /**
                 * Unlocks the mutex.
                 * 
                 * @return  TE_Ok on success, other codes on failure.
                 */
                Util::TAKErr unlock() NOTHROWS;
            private :
                std::unique_ptr<Impl::MutexImpl, void(*)(const Impl::MutexImpl *)> impl;

                friend class ENGINE_API CondVar;
            };
        }
    }
}

#endif
