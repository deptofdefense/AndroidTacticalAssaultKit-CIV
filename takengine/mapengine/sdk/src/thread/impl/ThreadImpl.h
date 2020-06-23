#ifndef TAK_ENGINE_THREAD_THREADIMPL_H_INCLUDED
#define TAK_ENGINE_THREAD_THREADIMPL_H_INCLUDED

#include <memory>

#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Thread {
            namespace Impl {
                class ThreadIDImpl
                {
                protected :
                    ThreadIDImpl() NOTHROWS;
                private :
                    ThreadIDImpl(const ThreadIDImpl &) NOTHROWS;
                protected :
                    virtual ~ThreadIDImpl() NOTHROWS = 0;
                public :
                    virtual void clone(std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> &value) NOTHROWS = 0;
                public:
                    virtual bool operator==(const ThreadIDImpl &other) const NOTHROWS = 0;
                };

                typedef std::unique_ptr<ThreadIDImpl, void(*)(const ThreadIDImpl *)> ThreadIDImplPtr;
            }
        }
    }
}

#endif
