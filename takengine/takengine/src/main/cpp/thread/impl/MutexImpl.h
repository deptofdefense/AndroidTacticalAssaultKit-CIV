#ifndef TAK_ENGINE_THREAD_MUTEXIMPL_H_INCLUDED
#define TAK_ENGINE_THREAD_MUTEXIMPL_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            namespace Impl {
                class ENGINE_API MutexImpl
                {
                protected:
                    MutexImpl() NOTHROWS;
                private:
                    MutexImpl(const MutexImpl &) NOTHROWS;
                protected:
                    virtual ~MutexImpl() NOTHROWS = 0;
                public:
                    /**
                     * Locks the mutex.
                     *
                     * @return  TE_Ok on success, other codes on failure.
                     */
                    virtual Util::TAKErr lock() NOTHROWS = 0;
                    /**
                     * Unlocks the mutex.
                     *
                     * @return  TE_Ok on success, other codes on failure.
                     */
                    virtual Util::TAKErr unlock() NOTHROWS = 0;
                };

                typedef std::unique_ptr<MutexImpl, void(*)(const MutexImpl *)> MutexImplPtr;

                /**
                 * Creates a new Mutex.
                 *
                 * @param value Returns the newly created Mutex
                 * @param type  The desired mutex type
                 *
                 * @return  TE_Ok on success, various codes on failure.
                 */
                Util::TAKErr MutexImpl_create(MutexImplPtr &value, const TAK::Engine::Thread::MutexType type = TAK::Engine::Thread::TEMT_Default) NOTHROWS;
            }
        }
    }
}

#endif
