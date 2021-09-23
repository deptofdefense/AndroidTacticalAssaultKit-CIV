#include "commoutils.h"

#include <memory>
#include <string>

#ifndef IMPL_THREAD_H_
#define IMPL_THREAD_H_

namespace atakmap {
namespace commoncommo {
namespace impl {
namespace thread
{
    // Forward declarations
    class CondVar;
    class Lock;
    class ReadLock;
    class WriteLock;
    class Monitor;
    class MonitorLock;
    class Thread;
    class PlatformMutex;
    class PlatformCondVar;
    class PlatformThreadID;

    typedef std::unique_ptr<ReadLock> ReadLockPtr;
    typedef std::unique_ptr<WriteLock> WriteLockPtr;
    typedef std::unique_ptr<Lock> LockPtr;
    typedef std::unique_ptr<MonitorLock> MonitorLockPtr;
    typedef std::unique_ptr<Thread> ThreadPtr;

    typedef std::unique_ptr<PlatformMutex> PlatformMutexPtr;
    typedef std::unique_ptr<PlatformCondVar> PlatformCondVarPtr;
    typedef std::unique_ptr<PlatformThreadID> PlatformThreadIDPtr;



#define THREAD_CHECKBREAK_CODE(c) \
    if((c) != Thread_Ok)  {\
        break; \
    }

#define THREAD_CHECKRETURN_CODE(c) \
    if((c) != Thread_Ok) {\
        return (c); \
    }




    enum ThreadErr
    {
        Thread_Ok,
        Thread_Err,
        Thread_IllegalState,
        Thread_Interrupted,
        Thread_TimedOut,
        Thread_Done
    };

    class Mutex
    {
    public:
        enum Type
        {
            /** Default, non-recursive behavior */
            Type_Default,
            /** Allows recursive locks by owning thread */
            Type_Recursive,
        };

    public :
        Mutex(const Type type = Type_Default);
        ~Mutex();

        /**
         * Locks the mutex.
         *
         * @return  Thread_Ok on success, other codes on failure.
         */
        ThreadErr lock();
        /**
         * Unlocks the mutex.
         * 
         * @return  Thread_Ok on success, other codes on failure.
         */
        ThreadErr unlock();

    private :
        COMMO_DISALLOW_COPY(Mutex);

        PlatformMutexPtr impl;

        friend class CondVar;
    };
    
    
    /**
     * Defines a condition variable for use with the Mutex class.
     */
    class CondVar
    {
    public:
        CondVar();
        ~CondVar();

        /**
        * Broadcasts a signal to all threads that are currently
        * waiting on this condition.
        *
        * @param lock  A lock that has been acquired on the Mutex
        *              specified during construction.
        *
        * @return  Thread_Ok on success, various codes on failure
        */
        ThreadErr broadcast(Lock &lock);

        /**
        * Signals a single threads that is currently waiting on this
        * condition.
        *
        * @param lock  A lock that has been acquired on the Mutex
        *              specified during construction.
        *
        * @return  Thread_Ok on success, various codes on failure
        */
        ThreadErr signal(Lock &lock);

        /**
        * Releases the specified lock and causes the currently
        * executing thread to block until this condition is signaled
        * from another thread or the specified period in milliseconds
        * has elapsed. The thread will have reaquired the lock before
        * this function returns.
        *
        * @param lock          A lock that has been acquired on the
        *                      Mutex specified during construction.
        * @param milliseconds  The maximum number of milliseconds to
        *                      wait for a signal before resuming, or
        *                      zero to wait indefinitely
        *
        * @return  Thread_Ok on success, Thread_TimedOut if the timeout
        *          elapsed or various codes on failure.
        */
        ThreadErr wait(Lock &lock, const int64_t milliseconds = 0LL);

    private:
        COMMO_DISALLOW_COPY(CondVar);

        PlatformCondVarPtr impl;
    };


    /**
     * RAII Mutex lock construct.
     */
    class Lock
    {
    public :
        Lock(Mutex &mutex);
        ~Lock();
        
        const ThreadErr status;

        /**
         * Acquires a lock on the specified mutex.
         *
         * @param value Returns the newly acquired lock
         * @param mutex The mutex to acquire a lock on
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        static ThreadErr create(LockPtr &value, Mutex &mutex);

    private :
        Lock(LockPtr &&impl);
        COMMO_DISALLOW_COPY(Lock);

        Mutex &mutex;

        friend class CondVar;
    };


    class Monitor
    {
    public:
        /**
        * Creates a new Monitor instance.
        */
        Monitor(const Mutex::Type type = Mutex::Type_Default);
        ~Monitor();

    private:
        COMMO_DISALLOW_COPY(Monitor);

        friend class MonitorLock;

        Mutex mutex;
        CondVar cond;
    };

    class MonitorLock
    {
    public:
        ~MonitorLock();
        
        /**
         * Acquires a new lock on the specified Monitor.
         *
         * @param value     Returns the newly acquired lock
         * @param monitor   The monitor to acquire a lock on
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        static ThreadErr create(MonitorLockPtr &value, Monitor &monitor);

        /**
         * Releases the lock on the associated Monitor and causes the
         * currently executing thread to block until the Monitor is
         * signaled from another thread or the specified period in
         * milliseconds has elapsed. The thread will have reaquired
         * the lock before this function returns.
         *
         * @param milliseconds  The maximum number of milliseconds to
         *                      wait for a signal before resuming, or
         *                      zero to wait indefinitely
         *
         * @return  Thread_Ok on success, Thread_TimedOut if the timeout
         *          elapsed or various codes on failure.
         */
        ThreadErr wait(const int64_t millis = 0LL);

        /**
         * Signals a single threads that is currently waiting to
         * acquire the associated Monitor.
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        ThreadErr signal();

        /**
         * Signals all waiting threads that are currently waiting to
         * acquire the associated Monitor.
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        ThreadErr broadcast();

    private:
        MonitorLock(Monitor &owner);
        COMMO_DISALLOW_COPY(MonitorLock);

        Monitor &owner;
        Lock impl;

    public:
        const ThreadErr status;
    };



    /**
     * Read-write type mutex, allowing for one writer and multiple
     * readers. Writing blocks reading, and reading blocks writing.
     */
    class RWMutex
    {
    public:
        enum Policy
        {
            /**
             * Hint for fair scheduling. Pending write acquisition is given
             * preference over new read acquisitions.
             */
            Policy_Fair,
            /**
             * Hint for unfair scheduling. Writers may starve.
             */
            Policy_Unfair,
        };

        RWMutex(const Policy policy = Policy_Unfair);
        ~RWMutex();

    private:
        COMMO_DISALLOW_COPY(RWMutex);

        /**
         * Locks the mutex for reading. If already locked for writing,
         * this call will block until the writer has unlocked.
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        ThreadErr lockRead();
        /**
         * Unlocks the mutex for reading.
         * @return  Thread_Ok on success, various codes on failure.
         */
        ThreadErr unlockRead();
        /**
         * Locks the mutex for reading. If already locked for reading,
         * this call will block until all other readers have unlocked.
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        ThreadErr lockWrite();
        /**
         * Unlocks the mutex for writing.
         * @return  Thread_Ok on success, various codes on failure.
         */
        ThreadErr unlockWrite();

    private:
        Monitor monitor;
        Policy policy;
        /** the number of readers who have acquired */
        std::size_t readers;
        /** the number of writers who have acquired */
        std::size_t writers;
        /** the number of writers waiting to acquire */
        std::size_t waitingWriters;

        friend class ReadLock;
        friend class WriteLock;
    };

    /**
     * RAII read lock construct for RWMutex.
     */
    class ReadLock
    {
    public:
        ReadLock(RWMutex &mutex);
        ~ReadLock();
        
        ThreadErr status;

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
        static ThreadErr create(ReadLockPtr &value, RWMutex &mutex);

    private:
        COMMO_DISALLOW_COPY(ReadLock);

        RWMutex &mutex;
    };


    /**
     * RAII write lock construct for RWMutex.
     */
    class WriteLock
    {
    public:
        WriteLock(RWMutex &mutex);
        ~WriteLock();
        
        ThreadErr status;

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
        static ThreadErr create(WriteLockPtr &value, RWMutex &mutex);


    private:
        COMMO_DISALLOW_COPY(WriteLock);

        RWMutex &mutex;
    };


    class ThreadID
    {
    private :
        ThreadID(PlatformThreadIDPtr &&impl);
    public :
        /**
         * Constructs a default ThreadID that will not match any other
         * ThreadIDs.
         */
        ThreadID();
        ThreadID(const ThreadID &other);
        ~ThreadID();

        /**
         * Returns the ID of the currently executing thread.
         *
         * @return  The ID of the currently executing thread.
         */
        static ThreadID currentThreadID();
    public :
        bool operator==(const ThreadID &other) const;
        bool operator!=(const ThreadID &other) const;
        ThreadID &operator=(const ThreadID &other);
    private :
        PlatformThreadIDPtr impl;

        friend class Thread;
    };

    class Thread
    {
    public:
        enum Priority
        {
            Priority_Lowest,
            Priority_Low,
            Priority_Normal,
            Priority_High,
            Priority_Highest,
        };

        struct CreateParams
        {
        public :
            /**
             * Creates a default parameter of NULL name and Priority_Normal priority
             */
            CreateParams();
        public :
            /** The desired thread name */
            std::string name;
            /** The desired thread priority */
            Priority priority;
        };

    public :
        virtual ~Thread() = 0;

        /**
         * Returns the ID of the thread.
         */
        ThreadID getID() const;

        /**
         * Joins the thread, waiting indefinitely if 'millis' is 0LL.
         *
         * @return  Thread_Ok on successful join, Thread_TimedOut if the
         *          timeout elapsed or other codes on failure.
         */
        virtual ThreadErr join(const int64_t millis = 0LL) = 0;

        /**
         * Detaches the thread.
         */
        virtual ThreadErr detach() = 0;
        
        /**
         * Creates and starts a new thread
         *
         * @param value     Returns the thread
         * @param entry     Defines the entry point for the thread
         * @param opaque    The parameter to be passed to the thread's
         *                  entry function
         * @param params    The (optional) thread create params
         *
         * @return  Thread_Ok on success, various codes on failure
         */
        static ThreadErr start(ThreadPtr &value, void *(*entry)(void *), void *opaque, const Thread::CreateParams params = Thread::CreateParams());

        /**
         * Causes the currently executing thread to sleep for the specified
         * number of milliseconds.
         *
         * @param milliseconds  The milliseconds to sleep
         *
         * @return  Thread_Ok on success, various codes on failure.
         */
        static ThreadErr sleep(const int64_t milliseconds);

    protected:
        Thread();

    private :
        COMMO_DISALLOW_COPY(Thread);
        virtual void getID(PlatformThreadIDPtr &value) const = 0;
    };

}
}
}
}    

#endif
