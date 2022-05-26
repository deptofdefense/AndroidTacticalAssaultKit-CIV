#ifndef TAK_ENGINE_THREAD_MUTEXGUARD_H_INCLUDED
#define TAK_ENGINE_THREAD_MUTEXGUARD_H_INCLUDED

#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            struct MutexGuard {
                inline MutexGuard(TAK::Engine::Thread::Mutex &mutex) NOTHROWS
                    : mutex(mutex) {}

                inline ~MutexGuard() NOTHROWS {
                    mutex.unlock();
                }

            private:
                TAK::Engine::Thread::Mutex &mutex;
            };
        }
    }
}

#endif
