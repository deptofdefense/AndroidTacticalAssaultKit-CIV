#include "commojni_common.h"
#include "commojni_impl.h"

using namespace atakmap::jni::commoncommo;


JavaVM *JVMManagement::runningVM = NULL;
const JavaVMAttachArgs JVMManagement::JVM_ATTACH_ARGS = {
    JNI_VERSION_1_6,
    NULL,
    NULL
};

LocalJNIEnv::LocalJNIEnv(JNIEnv **env)
{
    if(JVMManagement::runningVM->GetEnv((void **)env, JNI_VERSION_1_6) != JNI_OK) {
        JavaVMAttachArgs aargs = JVMManagement::JVM_ATTACH_ARGS;
        JVMManagement::runningVM->AttachCurrentThread(ATTACH_ARG_CAST(env), &aargs);
        needsDetach = !!(*env);
    } else
        needsDetach = false;
}

LocalJNIEnv::~LocalJNIEnv()
{
    if (needsDetach)
        JVMManagement::runningVM->DetachCurrentThread();
}


extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JVMManagement::runningVM = vm;
    void *env_vp;
    if (vm->GetEnv(&env_vp, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
        
    if (!CommoJNI::reflectionInit((JNIEnv *)env_vp))
        return JNI_ERR;

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
#if 0
    void *env_vp;
    if (vm->GetEnv(&env_vp, JNI_VERSION_1_6) != JNI_OK)
        // Very odd for this to happen, but... what can we do?
        return;

    JNIEnv *env = (JNIEnv *)env_vp;
#endif

}

} // end extern "C"
