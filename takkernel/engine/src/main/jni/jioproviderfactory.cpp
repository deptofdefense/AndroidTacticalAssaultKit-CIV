#include "jioproviderfactory.h"

#include <memory>
#include <sstream>
#include <vector>

#include <jni.h>

#include <db/DatabaseFactory.h>
#include <port/STLVectorAdapter.h>
#include <util/DataInput2.h>
#include <util/DataOutput2.h>
#include <util/Filesystem.h>
#include <util/IO2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"
#include "interop/db/ManagedDatabase2.h"
#include "interop/util/ManagedDataInput2.h"
#include "interop/util/ManagedDataOutput2.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;
using namespace TAKEngineJNI::Interop::Util;

namespace
{
    struct
    {
        jclass id;
        jmethodID getInputStream;
        jmethodID getOutputStream;
        jmethodID remove;
        jmethodID length;
        jmethodID lastModified;
        jmethodID exists;
        jmethodID isFile;
        jmethodID isDirectory;
        jmethodID list;
        jmethodID mkdirs;
        jmethodID getChannel;
        jmethodID createTempFile;
        jmethodID createDatabase;
    } IOProviderFactory_class;

    struct
    {
        jclass id;
        jmethodID ctor;
        jmethodID getAbsolutePath;
        jmethodID createTempFile;
    } File_class;

    class IOProviderFactoryProxy : public DatabaseProvider, public Filesystem
    {
    public :
        IOProviderFactoryProxy(JNIEnv &env) NOTHROWS;
    public : // DatabaseProvider
        virtual TAKErr create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS override;
        virtual TAKErr getType(const char** value) NOTHROWS override;
    public : // Filesystem
        TAKErr createTempFile(TAK::Engine::Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS override;
        TAKErr createTempDirectory(TAK::Engine::Port::String &value, const char* prefix, const char* suffix, const char* parentPath) NOTHROWS override;
        TAKErr getFileCount(std::size_t *value, const char *path, const std::size_t limit = 0u) NOTHROWS override;
        TAKErr listFiles(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, const char *path) NOTHROWS override;
        TAKErr length(int64_t *value, const char* path) NOTHROWS override;
        TAKErr getLastModified(int64_t *value, const char* path) NOTHROWS override;
        TAKErr isDirectory(bool *value, const char* path) NOTHROWS override;
        TAKErr isFile(bool *value, const char* path) NOTHROWS override;
        TAKErr exists(bool *value, const char *path) NOTHROWS override;
        TAKErr remove(const char *path) NOTHROWS override;
        TAKErr mkdirs(const char* dirPath) NOTHROWS override;
        TAKErr openFile(DataInput2Ptr &dataPtr, const char *path) NOTHROWS override;
        TAKErr openFile(DataOutput2Ptr &dataPtr, const char *path) NOTHROWS override;
    };

    bool IOProviderFactory_class_init(JNIEnv &env) NOTHROWS;
}

JNIEXPORT jlong JNICALL Java_com_atakmap_coremap_io_IOProviderFactory_installFactory
  (JNIEnv *env, jclass clazz)
{
    auto wrappedProvider = std::make_shared<IOProviderFactoryProxy>(*env);
    DatabaseFactory_registerProvider(wrappedProvider);
    IO_setFilesystem(wrappedProvider);

    return INTPTR_TO_JLONG(wrappedProvider.get());
}
JNIEXPORT void JNICALL Java_com_atakmap_coremap_io_IOProviderFactory_uninstallFactory
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    DatabaseFactory_unRegisterProvider(JLONG_TO_INTPTR(DatabaseProvider, ptr));
    IO_setFilesystem(std::shared_ptr<Filesystem>(nullptr));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_coremap_io_IOProviderFactory_DatabaseProvider_1create
  (JNIEnv *env, jclass clazz, jstring mpath)
{
    TAKErr code(TE_Ok);
    TAK::Engine::Port::String cpath;
    code = JNIStringUTF_get(cpath, *env, mpath);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    DatabasePtr retval(nullptr, nullptr);
    code = DatabaseFactory_create(retval, DatabaseInformation(cpath));
    if(code != TE_Ok)
        return NULL;
    return NewPointer(env, std::move(retval));
}


namespace
{
    IOProviderFactoryProxy::IOProviderFactoryProxy(JNIEnv &env) NOTHROWS
    {
        static bool clinit = IOProviderFactory_class_init(env);
    }

    // DatabaseProvider
    TAKErr IOProviderFactoryProxy::create(DatabasePtr& value, const DatabaseInformation& information) NOTHROWS
    {
        LocalJNIEnv env;
        TAKErr code(TE_Ok);
        const char *curi;
        code = information.getUri(&curi);
        TE_CHECKRETURN_CODE(code);
        JNILocalRef mpath(*env, env->NewStringUTF(curi));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        JNILocalRef mdatabase(*env, env->CallStaticObjectMethod(IOProviderFactory_class.id, IOProviderFactory_class.createDatabase, mfile.get(), 0));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        if(!mdatabase)
            return TE_Err;
        value = DatabasePtr(new ManagedDatabase2(*env, mdatabase), Memory_deleter_const<Database2, ManagedDatabase2>);
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::getType(const char** value) NOTHROWS
    {
        *value = "com.atakmap.coremap.io.IOProviderFactory";
        return TE_Ok;
    }
    // Filesystem
    TAKErr IOProviderFactoryProxy::createTempFile(TAK::Engine::Port::String &value, const char *prefix, const char *suffix, const char *dir) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mprefix(*env, env->NewStringUTF(prefix));
        JNILocalRef msuffix(*env, env->NewStringUTF(suffix));
        JNILocalRef mdir(*env, NULL);
        if(dir) {
            JNILocalRef mdirPath(*env, env->NewStringUTF(dir));
            mdir = JNILocalRef(*env, env->NewObject(File_class.id, File_class.ctor, mdirPath.get()));
        }
        JNILocalRef mtempFile(*env, env->CallStaticObjectMethod(IOProviderFactory_class.id, IOProviderFactory_class.createTempFile, mprefix.get(), msuffix.get(), mdir.get()));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        JNILocalRef mpath(*env, env->CallObjectMethod(mtempFile, File_class.getAbsolutePath));
        return JNIStringUTF_get(value, *env, (jstring)mpath);
    }
    TAKErr IOProviderFactoryProxy::createTempDirectory(TAK::Engine::Port::String &value, const char* prefix, const char* suffix, const char* dir) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = createTempFile(value, prefix, suffix, dir);
        TE_CHECKRETURN_CODE(code);
        code = remove(value);
        TE_CHECKRETURN_CODE(code);
        code = mkdirs(value);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr IOProviderFactoryProxy::getFileCount(std::size_t *value, const char *path, const std::size_t limit) NOTHROWS
    {
        TAKErr code(TE_Ok);
        bool b;
        code = this->exists(&b, path);
        TE_CHECKRETURN_CODE(code);

        if(!b)
            return TE_InvalidArg;

        code = this->isFile(&b, path);
        if(b) {
            *value = 1u;
            return TE_Ok;
        }
        code = this->isFile(&b, path);
        if(b) {
            std::vector<TAK::Engine::Port::String> contents;
            TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> contents_w;
            code = this->listFiles(contents_w, path);
            TE_CHECKRETURN_CODE(code);

            *value = 0u;
            for (auto it = contents.begin(); it != contents.end(); it++) {
                std::ostringstream ss;
                ss << path << '/' << (*it).get();
                std::size_t l;
                code = this->getFileCount(&l, ss.str().c_str());
                TE_CHECKBREAK_CODE(code);

                *value += l;
            }
            TE_CHECKRETURN_CODE(code);

            return code;
        }

        return TE_IllegalState;
    }
    TAKErr IOProviderFactoryProxy::listFiles(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, const char *path) NOTHROWS
    {
        TAKErr code(TE_Ok);
        bool b;
        code = this->exists(&b, path);
        TE_CHECKRETURN_CODE(code);

        if(!b)
            return TE_InvalidArg;

        code = this->isFile(&b, path);
        if(b)
            return TE_InvalidArg;

        code = this->isDirectory(&b, path);
        if(!b)
            return TE_IllegalState;
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        JNILocalRef mchildren(*env, env->CallStaticObjectMethod(IOProviderFactory_class.id, IOProviderFactory_class.list, mfile.get()));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        if(mchildren) {
            const jint numChildren = env->GetArrayLength((jarray)mchildren.get());
            for(jint i = 0; i < numChildren; i++) {
                JNILocalRef mchild(*env, env->GetObjectArrayElement((jobjectArray)mchildren.get(), i));
                if(mchild) {
                    TAK::Engine::Port::String cchild;
                    code = JNIStringUTF_get(cchild, *env, (jstring)mchild.get());
                    TE_CHECKBREAK_CODE(code);
                    code = value.add(cchild);
                    TE_CHECKBREAK_CODE(code);
                }
            }
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
    TAKErr IOProviderFactoryProxy::length(int64_t *value, const char* path) NOTHROWS
    {
        TAKErr code(TE_Ok);
        bool b;
        code = this->exists(&b, path);
        TE_CHECKRETURN_CODE(code);

        if(!b)
            return TE_InvalidArg;

        code = this->isFile(&b, path);
        if(b) {
            LocalJNIEnv env;
            JNILocalRef mpath(*env, env->NewStringUTF(path));
            JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
            *value = env->CallStaticLongMethod(IOProviderFactory_class.id, IOProviderFactory_class.length, mfile.get());
            if(env->ExceptionCheck()) {
                env->ExceptionClear();
                return TE_Err;
            }
            return TE_Ok;
        }
        code = this->isDirectory(&b, path);
        if(b) {
            std::vector<TAK::Engine::Port::String> contents;
            TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> contents_w;
            code = this->listFiles(contents_w, path);
            TE_CHECKRETURN_CODE(code);

            *value = 0LL;
            for(auto it = contents.begin(); it != contents.end(); it++) {
                std::ostringstream ss;
                ss << path << '/' << (*it).get();
                int64_t l;
                code = this->length(&l, ss.str().c_str());
                TE_CHECKBREAK_CODE(code);

                *value += l;
            }
            TE_CHECKRETURN_CODE(code);

            return code;
        }

        return TE_IllegalState;
    }
    TAKErr IOProviderFactoryProxy::getLastModified(int64_t *value, const char* path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jlong lastModified = env->CallStaticLongMethod(IOProviderFactory_class.id, IOProviderFactory_class.lastModified, mfile.get());
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        *value = lastModified;
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::isDirectory(bool *value, const char* path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jboolean isdir = env->CallStaticBooleanMethod(IOProviderFactory_class.id, IOProviderFactory_class.isDirectory, mfile.get());
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        *value = isdir;
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::isFile(bool *value, const char* path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jboolean isfile = env->CallStaticBooleanMethod(IOProviderFactory_class.id, IOProviderFactory_class.isFile, mfile.get());
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        *value = isfile;
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::exists(bool *value, const char *path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jboolean exists = env->CallStaticBooleanMethod(IOProviderFactory_class.id, IOProviderFactory_class.exists, mfile.get());
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        *value = exists;
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::remove(const char *path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jboolean success = env->CallStaticBooleanMethod(IOProviderFactory_class.id, IOProviderFactory_class.remove, mfile.get());
        return success ? TE_Ok : TE_Err;
    }
    TAKErr IOProviderFactoryProxy::mkdirs(const char* path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        jboolean success = env->CallStaticBooleanMethod(IOProviderFactory_class.id, IOProviderFactory_class.mkdirs, mfile.get());
        return success ? TE_Ok : TE_Err;
    }
    TAKErr IOProviderFactoryProxy::openFile(DataInput2Ptr &value, const char *path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        JNILocalRef mstream(*env, env->CallStaticObjectMethod(IOProviderFactory_class.id, IOProviderFactory_class.getInputStream, mfile.get()));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        std::unique_ptr<ManagedDataInput2> cstream(new ManagedDataInput2());
        TAKErr code = cstream->open(*env, mstream);
        TE_CHECKRETURN_CODE(code);
        value = DataInput2Ptr(cstream.release(), Memory_deleter_const<DataInput2, ManagedDataInput2>);
        return TE_Ok;
    }
    TAKErr IOProviderFactoryProxy::openFile(DataOutput2Ptr &value, const char *path) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef mpath(*env, env->NewStringUTF(path));
        JNILocalRef mfile(*env, env->NewObject(File_class.id, File_class.ctor, mpath.get()));
        JNILocalRef mstream(*env, env->CallStaticObjectMethod(IOProviderFactory_class.id, IOProviderFactory_class.getOutputStream, mfile.get()));
        if(env->ExceptionCheck()) {
            env->ExceptionClear();
            return TE_Err;
        }
        std::unique_ptr<ManagedDataOutput2> cstream(new ManagedDataOutput2());
        TAKErr code = cstream->open(*env, mstream);
        TE_CHECKRETURN_CODE(code);
        value = DataOutput2Ptr(cstream.release(), Memory_deleter_const<DataOutput2, ManagedDataOutput2>);
        return TE_Ok;
    }

    bool IOProviderFactory_class_init(JNIEnv &env) NOTHROWS
    {
        IOProviderFactory_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/coremap/io/IOProviderFactory");
        IOProviderFactory_class.getInputStream = env.GetStaticMethodID(IOProviderFactory_class.id, "getInputStream", "(Ljava/io/File;)Ljava/io/FileInputStream;");
        IOProviderFactory_class.getOutputStream = env.GetStaticMethodID(IOProviderFactory_class.id, "getOutputStream", "(Ljava/io/File;)Ljava/io/FileOutputStream;");
        IOProviderFactory_class.remove = env.GetStaticMethodID(IOProviderFactory_class.id, "delete", "(Ljava/io/File;)Z");
        IOProviderFactory_class.length = env.GetStaticMethodID(IOProviderFactory_class.id, "length", "(Ljava/io/File;)J");
        IOProviderFactory_class.lastModified = env.GetStaticMethodID(IOProviderFactory_class.id, "lastModified", "(Ljava/io/File;)J");
        IOProviderFactory_class.exists = env.GetStaticMethodID(IOProviderFactory_class.id, "exists", "(Ljava/io/File;)Z");
        IOProviderFactory_class.isFile = env.GetStaticMethodID(IOProviderFactory_class.id, "isFile", "(Ljava/io/File;)Z");
        IOProviderFactory_class.isDirectory = env.GetStaticMethodID(IOProviderFactory_class.id, "isDirectory", "(Ljava/io/File;)Z");
        IOProviderFactory_class.list = env.GetStaticMethodID(IOProviderFactory_class.id, "list", "(Ljava/io/File;)[Ljava/lang/String;");
        IOProviderFactory_class.mkdirs = env.GetStaticMethodID(IOProviderFactory_class.id, "mkdirs", "(Ljava/io/File;)Z");
        IOProviderFactory_class.getChannel = env.GetStaticMethodID(IOProviderFactory_class.id, "getChannel", "(Ljava/io/File;Ljava/lang/String;)Ljava/nio/channels/FileChannel;");
        IOProviderFactory_class.createTempFile = env.GetStaticMethodID(IOProviderFactory_class.id, "createTempFile", "(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;");
        IOProviderFactory_class.createDatabase = env.GetStaticMethodID(IOProviderFactory_class.id, "createDatabase", "(Ljava/io/File;I)Lcom/atakmap/database/DatabaseIface;");

        File_class.id = ATAKMapEngineJNI_findClass(&env, "java/io/File");
        File_class.ctor = env.GetMethodID(File_class.id, "<init>", "(Ljava/lang/String;)V");
        File_class.getAbsolutePath = env.GetMethodID(File_class.id, "getAbsolutePath", "()Ljava/lang/String;");
        File_class.createTempFile = env.GetStaticMethodID(File_class.id, "createTempFile", "(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;");

        return true;
    }
}
