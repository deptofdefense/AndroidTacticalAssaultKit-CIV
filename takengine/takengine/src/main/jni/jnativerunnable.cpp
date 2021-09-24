#include "jnativerunnable.h"

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNIRunnable.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_interop_NativeRunnable_run
  (JNIEnv *env, jclass clazz, jlong runfn_ptr, jlong ptr)
{
    typedef void(*runfn)(void *);
    runfn run = JLONG_TO_FNPTR(runfn, runfn_ptr);
    if(!run) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    void *opaque = JLONG_TO_INTPTR(void, ptr);
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    run(opaque);
}

JNIEXPORT void JNICALL Java_com_atakmap_interop_NativeRunnable_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<void>(env, mpointer);
}
