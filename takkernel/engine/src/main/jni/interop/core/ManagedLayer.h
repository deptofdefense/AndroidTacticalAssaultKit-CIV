#ifndef TAKENGINEJNI_INTEROP_CORE_MANAGEDLAYER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_CORE_MANAGEDLAYER_H_INCLUDED

#include <map>

#include <jni.h>

#include <core/Layer.h>
#include <port/String.h>
#include <thread/Mutex.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            class ManagedLayer : public atakmap::core::Layer
            {
            public :
                ManagedLayer(JNIEnv &env, jobject impl) NOTHROWS;
                ~ManagedLayer () NOTHROWS;
            public :
                virtual void addVisibilityListener (atakmap::core::Layer::VisibilityListener*);
                virtual const char* getName () const throw ();
                virtual bool isVisible () const;
                virtual void removeVisibilityListener (atakmap::core::Layer::VisibilityListener*);
                virtual void setVisible (bool visibility) ;
            public :
                jobject impl;
            private :
                TAK::Engine::Port::String name;
                std::map<atakmap::core::Layer::VisibilityListener *, jobject> visibilityListeners;
                TAK::Engine::Thread::Mutex mutex;
            };
        }
    }
}
#endif
