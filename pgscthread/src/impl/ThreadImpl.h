#ifndef PGSCTHREAD_THREADIMPL_H_INCLUDED
#define PGSCTHREAD_THREADIMPL_H_INCLUDED

#include <memory>

#include "ThreadPlatform.h"

namespace PGSC {
    namespace Thread {
        namespace Impl {
            class ThreadIDImpl
            {
            protected :
                ThreadIDImpl() PGSCT_NOTHROWS;
            private :
                ThreadIDImpl(const ThreadIDImpl &) PGSCT_NOTHROWS;
            protected :
                virtual ~ThreadIDImpl() PGSCT_NOTHROWS = 0;
            public :
                virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) PGSCT_NOTHROWS = 0;
            public:
                virtual bool operator==(const ThreadIDImpl &other) const PGSCT_NOTHROWS = 0;
            };

            typedef std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> ThreadIDImplPtr;
        }
    }
}

#endif
