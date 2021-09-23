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

            /**
             * RAII Mutex lock construct.
             */
            class ENGINE_API Lock
            {
            public :
                Lock(Mutex &mutex) NOTHROWS;
            private :
                Lock(const Lock &) NOTHROWS;
            public :
                ~Lock() NOTHROWS;
            public :
                const Util::TAKErr status;
            private :
                Mutex &mutex;

                friend class CondVar;
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
            ENGINE_API Util::TAKErr Lock_create(LockPtr &value, Mutex &mutex) NOTHROWS;
        }
    }
}

#endif
