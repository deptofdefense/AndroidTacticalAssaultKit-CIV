#include <jni.h>

#include "commojni_common.h"
#include "commojni_impl.h"
#include "fileioprovider.h"

using namespace atakmap::jni::commoncommo;
using namespace atakmap::commoncommo;

namespace {
    struct {
        jclass id;
        jmethodID open;
        jmethodID getSize;
    } FileIOProvider_class;

    struct {
        jclass id;
        jmethodID m_closeMethodId;
        jmethodID m_writeMethodId;
        jmethodID m_readMethodId;
        jmethodID m_sizeMethodId;
        jmethodID m_positionId;
        jmethodID m_seekMethodId;
    } FileChannel_class;

    class JNILocalRef
    {
    public :
        JNILocalRef(JNIEnv &env, jobject obj) : env(env), obj(obj) {}
        ~JNILocalRef()
        {
            if(obj)
                env.DeleteLocalRef(obj);
        }
    public :
        operator jobject () const
        {
            return obj;
        }
        operator jstring () const
        {
            return (jstring)obj;
        }
        operator bool () const
        {
            return !!obj;
        }
    private :
        JNIEnv &env;
        jobject obj;
    };
}

namespace atakmap {
namespace jni {
namespace commoncommo {

    JNIFileIOProvider::JNIFileIOProvider(JNIEnv &env, jobject instance)
    {
        m_instance = env.NewGlobalRef(instance);
        if(! m_instance)
        {
            throw 1;
        }
    }

    JNIFileIOProvider::~JNIFileIOProvider(){
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env){
            return;
        }
        if(m_instance) {
            env->DeleteGlobalRef(m_instance);
        }
        for(auto it: handleMap)
            env->DeleteGlobalRef(it.second);
    }

    FileHandle* JNIFileIOProvider::open(const char* path, const char * mode)
    {
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return NULL;
        }

        JNILocalRef jpath(*env, env->NewStringUTF(path));
        if (!jpath || env->ExceptionCheck()) {
            env->ExceptionClear();
            return NULL;
        }

        JNILocalRef jmode(*env, env->NewStringUTF(mode));
        if (!jmode || env->ExceptionCheck()) {
            env->ExceptionClear();
            return NULL;
        }

        // returned a FileChannel object
        JNILocalRef jfileIoClass(*env, env->CallObjectMethod(m_instance, FileIOProvider_class.open, (jstring)jpath, (jstring)jmode));
        if (!jfileIoClass || env->ExceptionCheck()) {
            env->ExceptionClear();
            return NULL;
        }


        jobject jhandle = env->NewGlobalRef(jfileIoClass);
        if (!jhandle || env->ExceptionCheck()) {
            env->ExceptionClear();
            return NULL;
        }

        handleMap[(FileHandle*)jhandle] = jhandle;

        return jhandle;
    }

    int JNIFileIOProvider::close(FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return -1;
        }

        if(handleMap.find(filePtr) != handleMap.end()){

            jobject mchannel = static_cast<jobject>(filePtr);
            // close the channel
            env->CallVoidMethod(mchannel, FileChannel_class.m_closeMethodId);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return -1;  // appropriate value on error
            }

            env->DeleteGlobalRef(mchannel);
            handleMap.erase(filePtr);
        }

        return 0;
    }

    size_t JNIFileIOProvider::read(void* buf, size_t size, size_t nmemb, FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return 0;
        }

        jobject mchannel = static_cast<jobject>(filePtr);
        JNILocalRef mbuf(*env, env->NewDirectByteBuffer(buf, size*nmemb));
        if (!mbuf || env->ExceptionCheck()) {
            env->ExceptionClear();
            return 0;
        }

        jint retval = env->CallIntMethod(mchannel, FileChannel_class.m_readMethodId, (jobject)mbuf);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 0;  // appropriate value on error
        }
        return retval > 0 ? retval : 0;
    }

    size_t JNIFileIOProvider::write(void* buf, size_t size, size_t nmemb, FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return 0;
        }

        jobject mchannel = static_cast<jobject>(filePtr);
        JNILocalRef mbuf(*env, env->NewDirectByteBuffer(buf, size*nmemb));
        if (!mbuf || env->ExceptionCheck()) {
            env->ExceptionClear();
            return 0;
        }

        jlong retval = env->CallIntMethod(mchannel, FileChannel_class.m_writeMethodId, (jobject)mbuf);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 0;  // appropriate value on error
        }

        return retval;
    }

    int JNIFileIOProvider::eof(FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return -1;
        }

        jobject mchannel = static_cast<jobject>(filePtr);
        jlong currentPos = env->CallLongMethod(mchannel, FileChannel_class.m_positionId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return -1;  // appropriate value on error
        }

        jlong channelSize = env->CallLongMethod(mchannel, FileChannel_class.m_sizeMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return -1;  // appropriate value on error
        }

        if (currentPos == (channelSize - 1)) {
            return 1;
        }

        return 0;
    }

    long JNIFileIOProvider::tell(FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return -1;
        }

        jobject mchannel = static_cast<jobject>(filePtr);
        jint retval = env->CallLongMethod(mchannel, FileChannel_class.m_positionId);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;  // appropriate value on error
        }
        return retval;
    }

    int JNIFileIOProvider::seek(long offset, int origin, FileHandle* filePtr)
    {
        if (!filePtr)
            return 0; // no-op
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return -1;
        }

        jobject mchannel = static_cast<jobject>(filePtr);
        jlong off = (jlong)offset;
        int retVal;
        //We are at the current position in the file
        if (origin == SEEK_CUR) {
            jlong currentPos = env->CallLongMethod(mchannel, FileChannel_class.m_positionId);

            off = off + currentPos;
        }
        //we are at the end of the file
        else if (origin == SEEK_END) {
            jlong currentPos = env->CallLongMethod(mchannel, FileChannel_class.m_sizeMethodId);

            off = off + currentPos - 1;
        }

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;  // appropriate value on error
        }

        JNILocalRef newChannel(*env, env->CallObjectMethod(mchannel, FileChannel_class.m_seekMethodId, off));

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;  // appropriate value on error
        }

        return 0;
    }

    int JNIFileIOProvider::error(FileHandle* filePtr)
    {
        return 0;
    }

    size_t JNIFileIOProvider::getSize(const char* path)
    {
        JNIEnv *env = NULL;
        LocalJNIEnv localEnv(&env);
        if (!env)
        {
            return 0;
        }

        JNILocalRef jpath(*env, env->NewStringUTF(path));
        if (!jpath || env->ExceptionCheck()) {
            env->ExceptionClear();
            return 0;
        }

        jlong size = env->CallLongMethod(m_instance, FileIOProvider_class.getSize, (jstring)jpath);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return 0;  // appropriate value on error
        }

        return static_cast<size_t>(size);
    }

    bool JNIFileIOProvider::reflectionInit(JNIEnv *env)
    {
        LOOKUP_CLASS(FileIOProvider_class.id, COMMO_PACKAGE "FileIOProvider", false);
        LOOKUP_METHOD(
            FileIOProvider_class.open,
            FileIOProvider_class.id,
            "open",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/nio/channels/FileChannel;");
        LOOKUP_METHOD(
            FileIOProvider_class.getSize,
            FileIOProvider_class.id,
            "getSize",
            "(Ljava/lang/String;)J");

        LOOKUP_CLASS(FileChannel_class.id, "java/nio/channels/FileChannel", false);
        LOOKUP_METHOD(
            FileChannel_class.m_closeMethodId,
            FileChannel_class.id,
            "close",
            "()V");
        LOOKUP_METHOD(
            FileChannel_class.m_writeMethodId,
            FileChannel_class.id,
            "write",
            "(Ljava/nio/ByteBuffer;)I");
        LOOKUP_METHOD(
            FileChannel_class.m_readMethodId,
            FileChannel_class.id,
            "read",
            "(Ljava/nio/ByteBuffer;)I");
        LOOKUP_METHOD(
            FileChannel_class.m_positionId,
            FileChannel_class.id,
            "position",
            "()J");
        LOOKUP_METHOD(
            FileChannel_class.m_sizeMethodId,
            FileChannel_class.id,
            "size",
            "()J");
        LOOKUP_METHOD(
            FileChannel_class.m_seekMethodId,
            FileChannel_class.id,
            "position",
            "(J)Ljava/nio/channels/FileChannel;");

        cleanup:
            return true;
    }


    void JNIFileIOProvider::reflectionRelease(JNIEnv *env)
    {
        env->DeleteGlobalRef(FileIOProvider_class.id);
        env->DeleteGlobalRef(FileChannel_class.id);
    }
}
}
}
