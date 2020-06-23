#ifndef TAK_ENGINE_THREAD_LOCK_H_INCLUDED
#define TAK_ENGINE_THREAD_LOCK_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            
            class CondVar;

            namespace Impl {
                class LockImpl;
            }

            /**
             * RAII Mutex lock construct.
             */
            class ENGINE_API Lock
            {
            private :
                Lock(std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> &&impl) NOTHROWS;
                Lock(const Lock &) NOTHROWS;
            public :
                ~Lock() NOTHROWS;
            private :
                std::unique_ptr<Impl::LockImpl, void(*)(const Impl::LockImpl *)> impl;

                friend class CondVar;
                friend Util::TAKErr Lock_create(std::unique_ptr<Lock, void(*)(const Lock *)> &value, Mutex &mutex) NOTHROWS;
            };

            typedef std::unique_ptr<Lock, void(*)(const Lock *)> LockPtr;

            /**
             * Acquires a lock on the specified mutex.
             *
             * @param value Returns the newly acquired lock
             * @param mutex The mutex to acquire a lock on
             *
             * @return  TE_Ok on success, various codes on failure
             */
            Util::TAKErr Lock_create(LockPtr &value, Mutex &mutex) NOTHROWS;
        }
    }
}

#endif
