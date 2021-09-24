#ifndef TAK_ENGINE_THREAD_RWMUTEX_H_INCLUDED
#define TAK_ENGINE_THREAD_RWMUTEX_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "thread/Monitor.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Thread {

            class ENGINE_API ReadLock;
            class ENGINE_API WriteLock;

            enum RWMutexPolicy
            {
                /**
                 * Hint for fair scheduling. Pending write acquisition is given
                 * preference over new read acquisitions.
                 */
                TERW_Fair,
                /**
                 * Hint for unfair scheduling. Writers may starve.
                 */
                TERW_Unfair,
            };

            /**
            * Read-write type mutex, allowing for one writer and multiple
            * readers. Writing blocks reading, and reading blocks writing.
            */
            class ENGINE_API RWMutex
            {
            public:
                RWMutex(const RWMutexPolicy policy = TERW_Unfair) NOTHROWS;
            private:
                RWMutex(const RWMutex &) NOTHROWS;
            public:
                ~RWMutex() NOTHROWS;
            private:
                /**
                * Locks the mutex for reading. If already locked for writing,
                * this call will block until the writer has unlocked.
                *
                * @return  TE_Ok on success, various codes on failure.
                */
                Util::TAKErr lockRead() NOTHROWS;
                /**
                * Unlocks the mutex for reading.
                * @return  TE_Ok on success, various codes on failure.
                */
                Util::TAKErr unlockRead() NOTHROWS;
                /**
                * Locks the mutex for reading. If already locked for reading,
                * this call will block until all other readers have unlocked.
                *
                * @return  TE_Ok on success, various codes on failure.
                */
                Util::TAKErr lockWrite() NOTHROWS;
                /**
                * Unlocks the mutex for writing.
                * @return  TE_Ok on success, various codes on failure.
                */
                Util::TAKErr unlockWrite() NOTHROWS;
            private:
                Monitor monitor;
                RWMutexPolicy policy;
                /** the number of readers who have acquired */
                std::size_t readers;
                /** the number of writers who have acquired */
                std::size_t writers;
                /** the number of writers waiting to acquire */
                std::size_t waitingWriters;

                friend class ENGINE_API ReadLock;
                friend class ENGINE_API WriteLock;
            };

            /**
            * RAII read lock construct for RWMutex.
            */
            class ENGINE_API ReadLock
            {
            public:
                ReadLock(RWMutex &mutex) NOTHROWS;
            private :
                ReadLock(const ReadLock &) NOTHROWS;
            public:
                ~ReadLock() NOTHROWS;
            public :
                Util::TAKErr status;
            private:
                RWMutex &mutex;
            };

            typedef std::unique_ptr<ReadLock, void(*)(const ReadLock *)> ReadLockPtr;

            /**
            * RAII write lock construct for RWMutex.
            */
            class ENGINE_API WriteLock
            {
            public:
                WriteLock(RWMutex &mutex) NOTHROWS;
            private :
                WriteLock(const WriteLock &) NOTHROWS;
            public:
                ~WriteLock() NOTHROWS;
            public :
                Util::TAKErr status;
            private:
                RWMutex &mutex;
            };

            typedef std::unique_ptr<WriteLock, void(*)(const WriteLock *)> WriteLockPtr;

            /**
            * Acquires a read lock on the specified read-write mutex. If
            * already locked for writing, this call will block until the writer
            * has unlocked.
            *
            * @param value Returns the newly acquired read lock
            * @param mutex The read-write mutex to acquire a read lock on
            *
            * @return  TE_Ok on success, various codes on failure
            */
            ENGINE_API Util::TAKErr ReadLock_create(ReadLockPtr &value, RWMutex &mutex) NOTHROWS;

            /**
            * Acquires a write lock on the specified read-write mutex. If
            * already locked for reading or writing, this call will block until
            * all other readers and writers have unlocked.
            *
            * @param value Returns the newly acquired write lock
            * @param mutex The mutex to acquire a write lock on
            *
            * @return  TE_Ok on success, various codes on failure
            */
            ENGINE_API Util::TAKErr WriteLock_create(WriteLockPtr &value, RWMutex &mutex) NOTHROWS;
        }
    }
}

#endif
