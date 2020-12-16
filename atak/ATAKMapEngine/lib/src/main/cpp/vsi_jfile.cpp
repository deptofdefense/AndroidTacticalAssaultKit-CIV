#include "common.h"

#include "cpl_port.h"
#include "cpl_vsi.h"

#include <cstddef>
#include <cstdio>
#include <cstring>
#if HAVE_FCNTL_H
#  include <fcntl.h>
#endif
#if HAVE_SYS_STAT_H
#include <sys/stat.h>
#endif

#include <algorithm>

#include "cpl_conv.h"
#include "cpl_error.h"
#include "cpl_vsi_virtual.h"

#ifdef WIN32
#include <io.h>
#include <fcntl.h>
#endif

#include <jni.h>
#include <sys/stat.h>

#include "interop/JNIStringUTF.h"
#include "interop/JNIByteArray.h"
#include "interop/java/JNILocalRef.h"
#include "jvsijfilefilesystemhandler.h"

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct {
        jclass id;
        jmethodID openMethodId;
        jmethodID statMethodId;

        jclass statbufclazz;
        jmethodID statbufctor;
        jfieldID statbuf_st_devFieldId;
        jfieldID statbuf_st_inoFieldId;
        jfieldID statbuf_st_modeFieldId;
        jfieldID statbuf_st_nlinkFieldId;
        jfieldID statbuf_st_uidFieldId;
        jfieldID statbuf_st_gidFieldId;
        jfieldID statbuf_st_rdevFieldId;
        jfieldID statbuf_st_sizeFieldId;
        jfieldID statbuf_st_blksizeFieldId;
        jfieldID statbuf_st_blocksFieldId;
        jfieldID statbuf_st_atimeFieldId;
        jfieldID statbuf_st_mtimeFieldId;
        jfieldID statbuf_st_ctimeFieldId;
    } FilesystemHandler_class;

    struct {
        jclass id;
        jmethodID closeMethodId;
        jmethodID writeMethodId;
        jmethodID readMethodId;
        jmethodID tellMethodId;
        jmethodID seekMethodId;
        jmethodID sizeMethodId;
    } FileHandle_class;

    bool Handler_Managed_class_init(JNIEnv &env) NOTHROWS;

    bool Handle_Managed_class_init(JNIEnv &env) NOTHROWS;
}


class VSIJFileFilesystemHandler : public VSIFilesystemHandler
{
public:
    VSIJFileFilesystemHandler(JNIEnv &env, jobject instance);
    virtual                  ~VSIJFileFilesystemHandler();

    virtual VSIVirtualHandle *Open( const char *pszFilename,
                                    const char *pszAccess,
                                    bool bSetError) override;
    virtual int               Stat( const char *pszFilename,
                                    VSIStatBufL *pStatBuf,
                                    int nFlags) override;

    /**
     * The instance of the Java JniFileIOProvider JNI object.
     */
    jobject m_instance;
};

class VSIJFileHandle : public VSIVirtualHandle
{
public:
    VSIJFileHandle(JNIEnv &env, jobject instance);
    virtual ~VSIJFileHandle();

    virtual int       Seek( vsi_l_offset nOffset,
                            int nWhence) override;
    virtual vsi_l_offset Tell() override;
    virtual size_t    Read( void *pBuffer,
                            size_t nSize,
                            size_t nMemb) override;
    virtual size_t    Write(const void *pBuffer,
                            size_t nSize,
                            size_t nMemb) override;
    virtual int       Eof() override;
    virtual int       Close() override;

    /**
     * The instance of the Java JniFileIOProvider object.
     */
    jobject m_instance;

private:
    virtual size_t size();

};

VSIJFileHandle::VSIJFileHandle(JNIEnv &env, jobject instance)
{
    static bool clinit = Handle_Managed_class_init(env);
    m_instance = env.NewGlobalRef(instance);
}

VSIJFileHandle::~VSIJFileHandle()
{
    LocalJNIEnv env;

    env->DeleteGlobalRef(m_instance);
}

int VSIJFileHandle::Seek(vsi_l_offset nOffset, int nWhence)
{
    LocalJNIEnv env;

    jlong startPos(0);
    if(nWhence == SEEK_SET)
    {
        startPos = 0;
    }
    else if (nWhence == SEEK_CUR)
    {
        startPos = Tell();
    }
    else if (nWhence == SEEK_END)
    {
        startPos = size();
    }

    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return -1;
    }

    jlong newPosition = nOffset + startPos;
    env->CallObjectMethod(m_instance, FileHandle_class.seekMethodId, newPosition);

    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return -1;
    }

    return 0;
}

vsi_l_offset VSIJFileHandle::Tell()
{
    LocalJNIEnv env;

    jlong n = env->CallLongMethod(m_instance, FileHandle_class.tellMethodId);

    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return -1;
    }

    return n;
}

size_t VSIJFileHandle::Read(void * pBuffer, size_t nSize, size_t nCount)
{
    LocalJNIEnv env;

    jsize length = nSize * nCount;
    if(!length)
        return 0u;

    JNILocalRef buf(*env, env->NewDirectByteBuffer(pBuffer, length));

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 0;
    }

    jint n = env->CallIntMethod(m_instance, FileHandle_class.readMethodId, buf.get());
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return 0;
    }

    // return number of elements read
    return n > 0 ? static_cast<size_t>(n/nSize) : 0u;
}

size_t VSIJFileHandle::Write(
    const void * pBuffer,
    size_t nSize,
    size_t nCount)
{
    LocalJNIEnv env;

    size_t length = nSize*nCount;
    if(!length)
        return 0u;

    JNILocalRef buf(*env, env->NewDirectByteBuffer(const_cast<void*>(pBuffer), length));

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 0;
    }

    int n = env->CallIntMethod(m_instance, FileHandle_class.writeMethodId, buf.get());
    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return 0;
    }

    // return number of elements written
    return n > 0 ? static_cast<size_t>(n/nSize) : 0u;
}

int VSIJFileHandle::Eof()
{
    LocalJNIEnv env;

    // shenanigans
    jlong currentPos = Tell();
    jlong totalSize = size();

    if (env->ExceptionCheck() || currentPos >= totalSize)
    {
        env->ExceptionClear();
        return EOF;
    }

    return 0;
}

int VSIJFileHandle::Close()
{
    LocalJNIEnv env;

    env->CallVoidMethod(m_instance, FileHandle_class.closeMethodId);

    if(env->ExceptionCheck())
    {
        env->ExceptionClear();
        return EOF;
    }

    return 0;
}

size_t VSIJFileHandle::size()
{
    LocalJNIEnv env;

    return env->CallLongMethod(m_instance, FileHandle_class.sizeMethodId);
}

VSIJFileFilesystemHandler::VSIJFileFilesystemHandler(JNIEnv& env, jobject instance)
{
    static bool clinit = Handler_Managed_class_init(env);
    m_instance = env.NewGlobalRef(instance);
}

VSIJFileFilesystemHandler::~VSIJFileFilesystemHandler()
{
    LocalJNIEnv env;

    env->DeleteGlobalRef(m_instance);
}

VSIVirtualHandle *
VSIJFileFilesystemHandler::Open(
    const char *pszFilename,
    const char *pszAccess,
    bool bSetError)
{
    LocalJNIEnv env;

    JNILocalRef jPszFilename(*env, env->NewStringUTF(pszFilename));
    JNILocalRef jPszAccess(*env, env->NewStringUTF(pszAccess));
    JNILocalRef handle(*env, env->CallObjectMethod(
        m_instance,
        FilesystemHandler_class.openMethodId,
        (jstring)jPszFilename,
        (jstring)jPszAccess
    ));

    if(handle)
    {
        return new VSIJFileHandle(*env, handle);
    }

    env->ExceptionClear();

    return NULL;

}

int VSIJFileFilesystemHandler::Stat(
    const char * pszFilename,
    VSIStatBufL * pStatBuf,
    int nFlags)
{
    LocalJNIEnv jenv;

    JNILocalRef jPszFilename(*jenv, jenv->NewStringUTF(pszFilename));
    JNILocalRef jstatbuf(*jenv, jenv->NewObject(FilesystemHandler_class.statbufclazz, FilesystemHandler_class.statbufctor));
    jint retVal = jenv->CallIntMethod(m_instance, FilesystemHandler_class.statMethodId, (jstring)jPszFilename, jstatbuf.get(), (jint)nFlags);

    if(jenv->ExceptionCheck())
    {
        jenv->ExceptionClear();
        return -1;
    }

    if(retVal == 0)
    {
        pStatBuf->st_dev      = jenv->GetLongField  (jstatbuf, FilesystemHandler_class.statbuf_st_devFieldId);
        pStatBuf->st_ino      = jenv->GetLongField(jstatbuf, FilesystemHandler_class.statbuf_st_inoFieldId);
        pStatBuf->st_mode     = jenv->GetIntField(jstatbuf, FilesystemHandler_class.statbuf_st_modeFieldId);
        pStatBuf->st_nlink    = jenv->GetLongField(jstatbuf, FilesystemHandler_class.statbuf_st_nlinkFieldId);
        pStatBuf->st_uid      = jenv->GetIntField(jstatbuf, FilesystemHandler_class.statbuf_st_uidFieldId);
        pStatBuf->st_gid      = jenv->GetIntField(jstatbuf, FilesystemHandler_class.statbuf_st_gidFieldId);
        pStatBuf->st_rdev     = jenv->GetLongField  (jstatbuf, FilesystemHandler_class.statbuf_st_rdevFieldId);
        pStatBuf->st_size     = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_sizeFieldId);
        pStatBuf->st_blksize  = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_blksizeFieldId);
        pStatBuf->st_blocks   = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_blocksFieldId);
        pStatBuf->st_atime    = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_atimeFieldId);
        pStatBuf->st_mtime    = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_mtimeFieldId);
        pStatBuf->st_ctime    = jenv->GetLongField (jstatbuf, FilesystemHandler_class.statbuf_st_ctimeFieldId);
    }

    if(jenv->ExceptionCheck())
    {
        jenv->ExceptionClear();
        return -1;
    }

    return retVal;
}

/**
 * \brief Install /vsijfile/ file system handler
 *
 * A special file handler is installed that allows reading  encrypted files.
 *
 * The file operations available are of course limited to Read() and
 * forward Seek() (full seek in the first MB of a file).
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_atakmap_map_gdal_VSIJFileFilesystemHandler_installFilesystemHandler(JNIEnv *env,
                                                                                 jclass clazz,
                                                                                 jstring prefix,
                                                                                 jobject handler) {
    JNIStringUTF nativePrefix(*env, prefix);
    VSIFileManager::InstallHandler(nativePrefix.get(), new VSIJFileFilesystemHandler(*env, handler));
}


namespace
{
    bool Handler_Managed_class_init(JNIEnv &env) NOTHROWS
    {
        FilesystemHandler_class.id      = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/gdal/VSIJFileFilesystemHandler");
        FilesystemHandler_class.statbufclazz = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/gdal/VSIStatBuf");

        FilesystemHandler_class.statMethodId  = env.GetMethodID(FilesystemHandler_class.id, "stat",  "(Ljava/lang/String;Lcom/atakmap/map/gdal/VSIStatBuf;I)I");
        FilesystemHandler_class.openMethodId  = env.GetMethodID(FilesystemHandler_class.id, "open",  "(Ljava/lang/String;Ljava/lang/String;)Ljava/nio/channels/FileChannel;");

        FilesystemHandler_class.statbufctor               = env.GetMethodID(FilesystemHandler_class.statbufclazz, "<init>", "()V");
        FilesystemHandler_class.statbuf_st_devFieldId     = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_dev", "J");
        FilesystemHandler_class.statbuf_st_inoFieldId     = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_ino", "J");
        FilesystemHandler_class.statbuf_st_modeFieldId    = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_mode", "I");
        FilesystemHandler_class.statbuf_st_nlinkFieldId   = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_nlink", "J");
        FilesystemHandler_class.statbuf_st_uidFieldId     = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_uid", "I");
        FilesystemHandler_class.statbuf_st_gidFieldId     = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_gid", "I");
        FilesystemHandler_class.statbuf_st_rdevFieldId    = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_rdev", "J");
        FilesystemHandler_class.statbuf_st_sizeFieldId    = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_size", "J");
        FilesystemHandler_class.statbuf_st_blksizeFieldId = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_blksize", "J");
        FilesystemHandler_class.statbuf_st_blocksFieldId  = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_blocks", "J");
        FilesystemHandler_class.statbuf_st_atimeFieldId   = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_atime", "J");
        FilesystemHandler_class.statbuf_st_mtimeFieldId   = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_mtime", "J");
        FilesystemHandler_class.statbuf_st_ctimeFieldId   = env.GetFieldID (FilesystemHandler_class.statbufclazz, "st_ctime", "J");

        return true;
    }

    bool Handle_Managed_class_init(JNIEnv &env) NOTHROWS
    {
        FileHandle_class.id = ATAKMapEngineJNI_findClass(&env, "java/nio/channels/FileChannel");

        FileHandle_class.closeMethodId = env.GetMethodID(FileHandle_class.id, "close", "()V");
        FileHandle_class.writeMethodId = env.GetMethodID(FileHandle_class.id, "write", "(Ljava/nio/ByteBuffer;)I");
        FileHandle_class.readMethodId  = env.GetMethodID(FileHandle_class.id, "read",  "(Ljava/nio/ByteBuffer;)I");
        FileHandle_class.tellMethodId  = env.GetMethodID(FileHandle_class.id, "position",  "()J");
        FileHandle_class.seekMethodId  = env.GetMethodID(FileHandle_class.id, "position",  "(J)Ljava/nio/channels/FileChannel;");
        FileHandle_class.sizeMethodId  = env.GetMethodID(FileHandle_class.id, "size",  "()J");
        return true;
    }
}
