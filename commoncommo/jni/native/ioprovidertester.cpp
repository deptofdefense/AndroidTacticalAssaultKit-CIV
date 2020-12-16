#include <jni.h>

#include "commojni_common.h"
#include "commojni_impl.h"
#include "../../core/impl/fileioprovidertracker.h"

using namespace atakmap::jni::commoncommo;
using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

namespace{
    FileIOProviderTracker* ptrToTracker(jlong nativePtr){
        return (JLONG_TO_PTR(FileIOProviderTracker, nativePtr));
    }
}

extern "C"{

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_ioProviderTesterCreateNative
    (JNIEnv *env, jclass selfCls)
{
    jlong ret = 0;
    try {
        FileIOProviderTracker *tester = new FileIOProviderTracker();
        ret = PTR_TO_JLONG(tester);
    } catch (int &) {
    }
    return ret;
}

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_registerFileIOProviderNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jprovider){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    if(!tracker)
        return 0LL;
    auto provider(std::static_pointer_cast<FileIOProvider>(
        std::make_shared<JNIFileIOProvider>(*env, jprovider)));
    tracker->registerProvider(provider);
    return (jlong)(intptr_t)provider.get();
}

JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_deregisterFileIOProviderNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jlong jprovider){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    if(!tracker)
        return;
    FileIOProvider *provider = (FileIOProvider *)(intptr_t)jprovider;
    if(!provider)
        return;
    tracker->deregisterProvider(*provider);
}

JNIEXPORT jobject JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_openNative
    (JNIEnv *env, jobject self, jlong nativePtr,
    jstring jpath, jstring jmode){
    FileHandle* handle = NULL;

    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());
    const char *path = env->GetStringUTFChars(jpath, NULL);
    const char *mode = env->GetStringUTFChars(jmode, NULL);

    if(path && mode){
        handle = provider->open(path, mode);
    }

    jclass myclass = env->GetObjectClass(self);
    jmethodID setMapId;
    LOOKUP_METHOD(
        setMapId,
        myclass,
        "setMap",
        "(Ljava/nio/channels/FileChannel;J)V");

    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jmode, mode);
    if(handle && !!dynamic_cast<JNIFileIOProvider *>(provider.get())){
        env->CallVoidMethod(self, setMapId, static_cast<jobject>(handle), (jlong)(intptr_t)handle);
        return static_cast<jobject>(handle);
    }
    cleanup:
    return NULL; // handle is NULL or the provider is pure C++
}

JNIEXPORT void JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_closeNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jlong jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());
    provider->close((JLONG_TO_PTR(FileHandle*, jchannel)));
}

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_readNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jbuf, jlong jsize, jlong jnmemb, jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    void* buf = env->GetDirectBufferAddress(jbuf);
    return jlong(provider->read(buf, size_t(jsize), size_t(jnmemb), static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_writeNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jbuf, jlong jsize, jlong jnmemb, jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    void* buf = env->GetDirectBufferAddress(jbuf);
    return jlong(provider->write(buf, size_t(jsize), size_t(jnmemb), static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jint JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_eofNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    return jint(provider->eof(static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jint JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_seekNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jlong joffset, jint jorigin, jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    int forigin = -1;
    int origin (jorigin);
    /*
     * defining the whence consts here to make it easier to use
     * in tests in the java side
     */
    switch (origin){
        case 0:
            forigin = SEEK_SET;
            break;
        case 1:
            forigin = SEEK_CUR;
            break;
        case 2:
            forigin = SEEK_END;
            break;
    }

    return jint(provider->seek(size_t(joffset), forigin ,static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jint JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_errorNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    return jint(provider->error(static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_tellNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jobject jchannel){
    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider (tracker->getCurrentProvider());

    return jint(provider->tell(static_cast<FileHandle*>(jchannel)));
}

JNIEXPORT jlong JNICALL
Java_com_atakmap_commoncommo_IOProviderTester_getSizeNative
    (JNIEnv *env, jclass selfCls, jlong nativePtr,
    jstring jpath){

    jlong size = 0;

    FileIOProviderTracker* tracker = ptrToTracker(nativePtr);
    auto provider(tracker->getCurrentProvider());
    const char *path = env->GetStringUTFChars(jpath, NULL);

    if (path)
    {
        size = (jlong)provider->getSize(path);
    }

    env->ReleaseStringUTFChars(jpath, path);
    return size;
}

} //extern "C"
