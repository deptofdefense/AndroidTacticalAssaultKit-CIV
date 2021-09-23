#include "interop/formats/quantizedmesh/ManagedQMESourceLayer.h"

#include "common.h"
#include "interop/java/JNILocalRef.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::QuantizedMesh;

using namespace TAKEngineJNI::Interop::Formats::QuantizedMesh;
using namespace TAKEngineJNI::Interop::Java;

namespace {
    struct {
        jclass id;
        jmethodID getMinZoom;
        jmethodID getMaxZoom;
        jmethodID isLocalDirectoryValid;
        jmethodID getClosestLevel;
        jmethodID getMaxLevel;
        jmethodID getDirectory;
        jmethodID getLevelDirectory;
        jmethodID getTileFile;
        jmethodID isValid;
        jmethodID isEnabled;
        jmethodID hasTile;
        jmethodID getAvailableExtents;
        jmethodID startDataRequest;
    } QMESourceLayer_class;

    struct {
        jclass id;
        jmethodID getStartX;
        jmethodID getStartY;
        jmethodID getEndX;
        jmethodID getEndY;
        jmethodID getLevel;
    } TileExtents_class;

    struct {
        jclass file_class;
        jmethodID file_getAbsolutePath;
        jclass list_class;
        jmethodID list_iterator;
        jclass iterator_class;
        jmethodID iterator_hasNext;
        jmethodID iterator_next;
    } MiscHelpers;

    bool QMESourceLayer_class_init(JNIEnv *env) NOTHROWS;
    bool TileExtents_class_init(JNIEnv *env) NOTHROWS;
    bool MiscHelpers_init(JNIEnv *env) NOTHROWS;
}


ManagedQMESourceLayer::ManagedQMESourceLayer(JNIEnv *env, jobject impl) NOTHROWS :
    impl(env->NewGlobalRef(impl))
{
    static bool clinit = QMESourceLayer_class_init(env);
    static bool teclinit = TileExtents_class_init(env);
    static bool fclinit = MiscHelpers_init(env);
}

ManagedQMESourceLayer::~ManagedQMESourceLayer() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
    }
}

TAKErr ManagedQMESourceLayer::getMinZoom(int *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallIntMethod(impl, QMESourceLayer_class.getMinZoom);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::getMaxZoom(int *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallIntMethod(impl, QMESourceLayer_class.getMaxZoom);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::isLocalDirectoryValid(bool *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallBooleanMethod(impl, QMESourceLayer_class.isLocalDirectoryValid) == JNI_TRUE;
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::getClosestLevel(int *value, double geodeticSpan) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallIntMethod(impl, QMESourceLayer_class.getClosestLevel, geodeticSpan);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::getMaxLevel(int *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallIntMethod(impl, QMESourceLayer_class.getMaxLevel);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::getDirectory(TAK::Engine::Port::String *dirname) const NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!dirname)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;

    JNILocalRef mdirfile(*env, env->CallObjectMethod(impl, QMESourceLayer_class.getDirectory));
    if (!mdirfile)
        return TE_Err;
    JNILocalRef mdirname(*env, env->CallObjectMethod(mdirfile, MiscHelpers.file_getAbsolutePath));
    code = JNIStringUTF_get(*dirname, *env, (jstring)mdirname);
    return code;
}

TAKErr ManagedQMESourceLayer::getLevelDirName(TAK::Engine::Port::String *dirname, int z) const NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!dirname)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;

    JNILocalRef mdirfile(*env, env->CallObjectMethod(impl, QMESourceLayer_class.getLevelDirectory, z));
    if (!mdirfile)
        return TE_Err;
    JNILocalRef mdirname(*env, env->CallObjectMethod(mdirfile, MiscHelpers.file_getAbsolutePath));
    code = JNIStringUTF_get(*dirname, *env, (jstring)mdirname);
    return code;
}

TAKErr ManagedQMESourceLayer::getTileFilename(TAK::Engine::Port::String *filename, int x, int y, int z) const NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!filename)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;

    JNILocalRef mfile(*env, env->CallObjectMethod(impl, QMESourceLayer_class.getTileFile, x, y, z));
    if (!mfile)
        return TE_Err;
    JNILocalRef mfilename(*env, env->CallObjectMethod(mfile, MiscHelpers.file_getAbsolutePath));
    code = JNIStringUTF_get(*filename, *env, (jstring)mfilename);
    return code;
}

TAKErr ManagedQMESourceLayer::isValid(bool *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallBooleanMethod(impl, QMESourceLayer_class.isValid) == JNI_TRUE;
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::isEnabled(bool *value) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallBooleanMethod(impl, QMESourceLayer_class.isEnabled) == JNI_TRUE;
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::hasTile(bool *value, int x, int y, int level) const NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallBooleanMethod(impl, QMESourceLayer_class.hasTile, x, y, level) == JNI_TRUE;
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::getAvailableExtents(TAK::Engine::Port::Vector<TAK::Engine::Formats::QuantizedMesh::TileExtents> *extents, int level) const NOTHROWS
{
    if(!extents)
        return TE_InvalidArg;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;

    JNILocalRef mextents(*env, env->CallObjectMethod(impl, QMESourceLayer_class.getAvailableExtents, level));
    if (!mextents)
        return TE_Err;

    JNILocalRef extentsIter(*env, env->CallObjectMethod(mextents, MiscHelpers.list_iterator));
    if (!extentsIter)
        return TE_Err;

    extents->clear();

    while (env->CallBooleanMethod(extentsIter, MiscHelpers.iterator_hasNext) == JNI_TRUE) {
        JNILocalRef mextent(*env, env->CallObjectMethod(extentsIter, MiscHelpers.iterator_next));
        if (!mextent)
            return TE_Err;

        TileExtents cextent;
        cextent.endX = env->CallIntMethod(mextent, TileExtents_class.getEndX);
        cextent.endY = env->CallIntMethod(mextent, TileExtents_class.getEndY);
        cextent.startX = env->CallIntMethod(mextent, TileExtents_class.getStartX);
        cextent.startY = env->CallIntMethod(mextent, TileExtents_class.getStartY);
        cextent.level = env->CallIntMethod(mextent, TileExtents_class.getLevel);
        extents->add(cextent);
    }

    return TE_Ok;
}

TAKErr ManagedQMESourceLayer::startDataRequest(int x, int y, int z) NOTHROWS
{
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return TE_Err;
    env->CallVoidMethod(impl, QMESourceLayer_class.startDataRequest, x, y, z);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}



namespace {

    bool QMESourceLayer_class_init(JNIEnv *env) NOTHROWS
    {
        QMESourceLayer_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/formats/quantizedmesh/QMESourceLayer");
        QMESourceLayer_class.getMinZoom = env->GetMethodID(QMESourceLayer_class.id, "getMinZoom", "()I");
        QMESourceLayer_class.getMaxZoom = env->GetMethodID(QMESourceLayer_class.id, "getMaxZoom", "()I");
        QMESourceLayer_class.isLocalDirectoryValid = env->GetMethodID(QMESourceLayer_class.id, "isLocalDirectoryValid", "()Z");
        QMESourceLayer_class.getClosestLevel = env->GetMethodID(QMESourceLayer_class.id, "getClosestLevel", "(D)I");
        QMESourceLayer_class.getMaxLevel = env->GetMethodID(QMESourceLayer_class.id, "getMaxLevel", "()I");
        QMESourceLayer_class.getDirectory = env->GetMethodID(QMESourceLayer_class.id, "getDirectory", "()Ljava/io/File;");
        QMESourceLayer_class.getLevelDirectory = env->GetMethodID(QMESourceLayer_class.id, "getLevelDirectory", "(I)Ljava/io/File;");
        QMESourceLayer_class.getTileFile = env->GetMethodID(QMESourceLayer_class.id, "getTileFile", "(III)Ljava/io/File;");
        QMESourceLayer_class.isValid = env->GetMethodID(QMESourceLayer_class.id, "isValid", "()Z");
        QMESourceLayer_class.isEnabled = env->GetMethodID(QMESourceLayer_class.id, "isEnabled", "()Z");
        QMESourceLayer_class.hasTile = env->GetMethodID(QMESourceLayer_class.id, "hasTile", "(III)Z");
        QMESourceLayer_class.getAvailableExtents = env->GetMethodID(QMESourceLayer_class.id, "getAvailableExtents", "(I)Ljava/util/List;");
        QMESourceLayer_class.startDataRequest = env->GetMethodID(QMESourceLayer_class.id, "startDataRequest", "(III)V");

        return true;
    }

    bool TileExtents_class_init(JNIEnv *env) NOTHROWS
    {
        TileExtents_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/formats/quantizedmesh/TileExtents");
        TileExtents_class.getStartX = env->GetMethodID(TileExtents_class.id, "getStartX", "()I");
        TileExtents_class.getStartY = env->GetMethodID(TileExtents_class.id, "getStartY", "()I");
        TileExtents_class.getEndX = env->GetMethodID(TileExtents_class.id, "getEndX", "()I");
        TileExtents_class.getEndY = env->GetMethodID(TileExtents_class.id, "getEndY", "()I");
        TileExtents_class.getLevel = env->GetMethodID(TileExtents_class.id, "getLevel", "()I");

        return true;
    }

    bool MiscHelpers_init(JNIEnv *env) NOTHROWS
    {
        MiscHelpers.file_class = ATAKMapEngineJNI_findClass(env, "java/io/File");
        MiscHelpers.list_class = ATAKMapEngineJNI_findClass(env, "java/util/List");
        MiscHelpers.iterator_class = ATAKMapEngineJNI_findClass(env, "java/util/Iterator");

        MiscHelpers.file_getAbsolutePath = env->GetMethodID(MiscHelpers.file_class, "getAbsolutePath", "()Ljava/lang/String;");
        MiscHelpers.list_iterator = env->GetMethodID(MiscHelpers.list_class, "iterator", "()Ljava/util/Iterator;");
        MiscHelpers.iterator_hasNext = env->GetMethodID(MiscHelpers.iterator_class, "hasNext", "()Z");
        MiscHelpers.iterator_next = env->GetMethodID(MiscHelpers.iterator_class, "next", "()Ljava/lang/Object;");

        return true;
    }

}