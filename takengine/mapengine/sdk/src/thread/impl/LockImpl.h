#ifndef TAK_ENGINE_THREAD_LOCKIMPL_H_INCLUDED
#define TAK_ENGINE_THREAD_LOCKIMPL_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/impl/MutexImpl.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            namespace Impl {
                /**
                 * RAII Mutex lock construct.
                 *
                 * <P>Corresponds to a read lock for RWMutex instances.
                 */
                class LockImpl
                {
                protected:
                    LockImpl() NOTHROWS;
                private:
                    LockImpl(const LockImpl &) NOTHROWS;
                protected:
                    virtual ~LockImpl() NOTHROWS;
                };

                typedef std::unique_ptr<LockImpl, void(*)(const LockImpl *)> LockImplPtr;

                /**
                 * Acquires a lock on the specified mutex.
                 *
                 * @param value Returns the newly acquired lock
                 * @param mutex The mutex to acquire a lock on
                 *
                 * @return  TE_Ok on success, various codes on failure
                 */
                Util::TAKErr LockImpl_create(LockImplPtr &value, MutexImpl &mutex) NOTHROWS;
            }
        }
    }
}

#endif
