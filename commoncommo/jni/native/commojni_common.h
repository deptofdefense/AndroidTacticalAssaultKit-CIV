#ifndef COMMOJNI_COMMON_H
#define COMMOJNI_COMMON_H

#include <jni.h>
#include <stddef.h>
#include <stdint.h>

// Common macros
#define PTR_TO_JLONG(a)            (jlong)(intptr_t)(a)
#define JLONG_TO_PTR(type, var)    (type *)(intptr_t)(var)

// Base package for use in FindClass()
#define COMMO_PACKAGE "com/atakmap/commoncommo/"

// Quirks in API types between android and sun
#ifdef __ANDROID__
    #define ATTACH_ARG_CAST(a) a
#else
    #define ATTACH_ARG_CAST(a) (void **)(a)
#endif


namespace atakmap {
namespace jni {
namespace commoncommo {
    // Used for global references
    typedef jobject jglobalobjectref;

    struct JVMManagement
    {
        static JavaVM *runningVM;
        static const JavaVMAttachArgs JVM_ATTACH_ARGS;
    };
    
    // scope-based JNIEnv which attaches current thread
    // to running VM managed by JVMManagement (if needed)
    class LocalJNIEnv
    {
    public:
        LocalJNIEnv(JNIEnv **env);
        ~LocalJNIEnv();
    private:
        bool needsDetach;
    };
    
    
        
}
}
}



#endif

