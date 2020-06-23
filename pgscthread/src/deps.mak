PUBINCDIR = ../include

# Public header deps
deps_ThreadPlatform_h              = $(PUBINCDIR)/ThreadPlatform.h
deps_ThreadError_h                 = $(PUBINCDIR)/ThreadError.h


deps_Cond_h                        = $(deps_ThreadPlatform_h)                \
                                     $(deps_Mutex_h)                         \
                                     $(deps_Lock_h)                          \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/Cond.h
deps_Mutex_h                       = $(deps_ThreadPlatform_h)                \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/Mutex.h
deps_Lock_h                        = $(deps_ThreadPlatform_h)                \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/Lock.h
deps_Monitor_h                     = $(deps_ThreadPlatform_h)                \
                                     $(deps_Cond_h)                          \
                                     $(deps_Lock_h)                          \
                                     $(deps_Mutex_h)                         \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/Monitor.h
deps_RWMutex_h                     = $(deps_ThreadPlatform_h)                \
                                     $(deps_Monitor_h)                       \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/RWMutex.h
deps_ThreadPool_h                  = $(deps_ThreadPlatform_h)                \
                                     $(deps_Mutex_h)                         \
                                     $(deps_Thread_h)                        \
                                     $(deps_Lock_h)                          \
                                     $(deps_ThreadError_h)                   \
                                     $(PUBINCDIR)/RWMutex.h


# source deps
deps_Cond_cpp                      = $(deps_Cond_h)                          \
                                     $(deps_impl_CondImpl_h)                 \
                                     Cond.cpp
deps_Lock_cpp                      = $(deps_Lock_h)                          \
                                     $(deps_impl_LockImpl_h)                 \
                                     Lock.cpp
deps_Monitor_cpp                   = $(deps_Monitor_h)                       \
                                     Monitor.cpp
deps_Mutex_cpp                     = $(deps_Mutex_h)                         \
                                     $(deps_Lock_h)                          \
                                     $(deps_impl_MutexImpl_h)                \
                                     Mutex.cpp
deps_RWMutex_cpp                   = $(deps_RWMutex_h)                       \
                                     RWMutex.cpp
deps_ThreadPool_cpp                = $(deps_ThreadPool_h)                    \
                                     ThreadPool.cpp

# Impl source deps
deps_impl_ThreadImpl_h             = $(deps_ThreadPlatform_h)                \
                                     impl/ThreadImpl.h

deps_impl_MutexImpl_h              = $(deps_ThreadPlatform_h)                \
                                     $(deps_Mutex_h)                         \
                                     $(deps_ThreadError_h)                   \
                                     impl/MutexImpl.h

deps_impl_LockImpl_h               = $(deps_ThreadPlatform_h)                \
                                     $(deps_impl_MutexImpl_h)                \
                                     $(deps_ThreadError_h)                   \
                                     impl/LockImpl.h

deps_impl_CondImpl_h               = $(deps_ThreadPlatform_h)                \
                                     $(deps_impl_LockImpl_h)                 \
                                     $(deps_ThreadError_h)                   \
                                     impl/CondImpl.h

deps_impl_ThreadImpl_common_cpp    = $(deps_Thread_h)                        \
                                     $(deps_impl_CondImpl_h)                 \
                                     $(deps_impl_LockImpl_h)                 \
                                     $(deps_impl_MutexImpl_h)                \
                                     $(deps_impl_ThreadImpl_h)               \
                                     $(deps_Monitor_h)                       \
                                     impl/ThreadImpl_common.cpp

deps_impl_ThreadImpl_libpthread_cpp= $(deps_Thread_h)                        \
                                     $(deps_impl_CondImpl_h)                 \
                                     $(deps_impl_LockImpl_h)                 \
                                     $(deps_impl_MutexImpl_h)                \
                                     $(deps_impl_ThreadImpl_h)               \
                                     $(deps_Monitor_h)                       \
                                     impl/ThreadImpl_libpthread.cpp

deps_impl_ThreadImpl_WIN32_cpp =     $(deps_Thread_h)                        \
                                     $(deps_impl_CondImpl_h)                 \
                                     $(deps_impl_LockImpl_h)                 \
                                     $(deps_impl_MutexImpl_h)                \
                                     $(deps_impl_ThreadImpl_h)               \
                                     impl/ThreadImpl_WIN32.cpp


Cond$(OBJ): $(deps_Cond_cpp)
Lock$(OBJ): $(deps_Lock_cpp)
Monitor$(OBJ): $(deps_Monitor_cpp)
Mutex$(OBJ): $(deps_Mutex_cpp)
RWMutex$(OBJ): $(deps_RWMutex_cpp)
ThreadPool$(OBJ): $(deps_ThreadPool_cpp)
impl/ThreadImpl_common$(OBJ): $(deps_impl_ThreadImpl_common_cpp)
impl/ThreadImpl_libpthread$(OBJ): $(deps_impl_ThreadImpl_libpthread_cpp)
impl/ThreadImpl_WIN32$(OBJ): $(deps_impl_ThreadImpl_WIN32_cpp)

OBJS = Cond$(OBJ) Lock$(OBJ) Monitor$(OBJ) Mutex$(OBJ)                       \
       RWMutex$(OBJ) ThreadPool$(OBJ)                                        \
       impl/ThreadImpl_common$(OBJ) $(PLATFORM_IMPL_OBJ)

