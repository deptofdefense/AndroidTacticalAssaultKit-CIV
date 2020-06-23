#ifndef PGSCTHREAD_RWMUTEX_H_INCLUDED
#define PGSCTHREAD_RWMUTEX_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"
#include "Monitor.h"
#include "ThreadError.h"

namespace PGSC{
    namespace Thread {

        class ReadLock;
        class WriteLock;

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
        class PGSCTHREAD_API RWMutex
        {
        public:
            RWMutex(const RWMutexPolicy policy = TERW_Unfair) PGSCT_NOTHROWS;
        private:
            RWMutex(const RWMutex &) PGSCT_NOTHROWS;
        public:
            ~RWMutex() PGSCT_NOTHROWS;
        private:
            /**
            * Locks the mutex for reading. If already locked for writing,
            * this call will block until the writer has unlocked.
            *
            * @return  Thread_Ok on success, various codes on failure.
            */
            Util::ThreadErr lockRead() PGSCT_NOTHROWS;
            /**
            * Unlocks the mutex for reading.
            * @return  Thread_Ok on success, various codes on failure.
            */
            Util::ThreadErr unlockRead() PGSCT_NOTHROWS;
            /**
            * Locks the mutex for reading. If already locked for reading,
            * this call will block until all other readers have unlocked.
            *
            * @return  Thread_Ok on success, various codes on failure.
            */
            Util::ThreadErr lockWrite() PGSCT_NOTHROWS;
            /**
            * Unlocks the mutex for writing.
            * @return  Thread_Ok on success, various codes on failure.
            */
            Util::ThreadErr unlockWrite() PGSCT_NOTHROWS;
        private:
            Monitor monitor;
            RWMutexPolicy policy;
            /** the number of readers who have acquired */
            std::size_t readers;
            /** the number of writers who have acquired */
            std::size_t writers;
            /** the number of writers waiting to acquire */
            std::size_t waitingWriters;

            friend class ReadLock;
            friend class WriteLock;
            friend PGSCTHREAD_API Util::ThreadErr ReadLock_create(std::unique_ptr<ReadLock, void(*)(const ReadLock *)> &value, RWMutex &mutex) PGSCT_NOTHROWS;
            friend PGSCTHREAD_API Util::ThreadErr WriteLock_create(std::unique_ptr<WriteLock, void(*)(const WriteLock *)> &value, RWMutex &mutex) PGSCT_NOTHROWS;
        };

        /**
        * RAII read lock construct for RWMutex.
        */
        class PGSCTHREAD_API ReadLock
        {
        private:
            ReadLock(RWMutex &mutex) PGSCT_NOTHROWS;
            ReadLock(const ReadLock &) PGSCT_NOTHROWS;
        public:
            ~ReadLock() PGSCT_NOTHROWS;
        private:
            RWMutex &mutex;

            friend PGSCTHREAD_API Util::ThreadErr ReadLock_create(std::unique_ptr<ReadLock, void(*)(const ReadLock *)> &value, RWMutex &mutex) PGSCT_NOTHROWS;
        };

        typedef std::unique_ptr<ReadLock, void(*)(const ReadLock *)> ReadLockPtr;

        /**
        * RAII write lock construct for RWMutex.
        */
        class PGSCTHREAD_API WriteLock
        {
        private:
            WriteLock(RWMutex &mutex) PGSCT_NOTHROWS;
            WriteLock(const WriteLock &) PGSCT_NOTHROWS;
        public:
            ~WriteLock() PGSCT_NOTHROWS;
        private:
            RWMutex &mutex;

            friend PGSCTHREAD_API Util::ThreadErr WriteLock_create(std::unique_ptr<WriteLock, void(*)(const WriteLock *)> &value, RWMutex &mutex) PGSCT_NOTHROWS;
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
        * @return  Thread_Ok on success, various codes on failure
        */
        PGSCTHREAD_API Util::ThreadErr ReadLock_create(ReadLockPtr &value, RWMutex &mutex) PGSCT_NOTHROWS;

        /**
        * Acquires a write lock on the specified read-write mutex. If
        * already locked for reading or writing, this call will block until
        * all other readers and writers have unlocked.
        *
        * @param value Returns the newly acquired write lock
        * @param mutex The mutex to acquire a write lock on
        *
        * @return  Thread_Ok on success, various codes on failure
        */
        PGSCTHREAD_API Util::ThreadErr WriteLock_create(WriteLockPtr &value, RWMutex &mutex) PGSCT_NOTHROWS;
    }
}

#endif
