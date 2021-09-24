#ifndef TAKENGINEJNI_INTEROP_CORE_MANAGEDVISIBILITYLISTENER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_CORE_MANAGEDVISIBILITYLISTENER_H_INCLUDED

#include <jni.h>

#include <core/Layer.h>
#include <port/Platform.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            class ManagedVisibilityListener : public atakmap::core::Layer::VisibilityListener
            {
            public :
                ManagedVisibilityListener(JNIEnv &env, jobject impl) NOTHROWS;
                ~ManagedVisibilityListener() NOTHROWS;
            public :
                virtual void visibilityChanged(atakmap::core::Layer &subject);
            public :
                jobject impl;
            private :
            };
        }
    }
}
#endif
