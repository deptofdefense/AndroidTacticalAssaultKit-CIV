#ifndef PGSCTHREAD_ERROR_H_INCLUDED
#define PGSCTHREAD_ERROR_H_INCLUDED



namespace PGSC {
    namespace Util {

        enum ThreadErr
        {
            Thread_Ok,
            Thread_Err,
            Thread_IllegalState,
            Thread_Interrupted,
            Thread_TimedOut,
            Thread_Done
        };
    
        template<class Iface, class Impl = Iface>
        inline void Memory_deleter_const(const Iface *obj)
        {
            const Impl *impl = static_cast<const Impl *>(obj);
            delete impl;
        }
        
        
        template<class Iface>
        inline void Memory_leaker(Iface *obj)
        {}
    
    }


}

#define THREAD_CHECKBREAK_CODE(c) \
    if((c) != PGSC::Util::Thread_Ok)  {\
        break; \
    }

#define THREAD_CHECKRETURN_CODE(c) \
    if((c) != PGSC::Util::Thread_Ok) {\
        return (c); \
    }


#endif
