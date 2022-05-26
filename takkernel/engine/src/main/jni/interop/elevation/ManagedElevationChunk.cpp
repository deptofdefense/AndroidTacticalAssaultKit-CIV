//
// Created by GeoDev on 12/13/2019.
//

#include "interop/elevation/ManagedElevationChunk.h"

#include <cmath>

#include "common.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/feature/Interop.h"
#include "interop/java/JNILocalRef.h"
#include "interop/math/Interop.h"
#include "interop/model/Interop.h"

using namespace TAKEngineJNI::Interop::Elevation;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jmethodID getUri;
        jmethodID getType;
        jmethodID getResolution;
        jmethodID getBounds;
        jmethodID createData;
        jmethodID sample_DD;
        jmethodID sample__1DII;
        jmethodID getCE;
        jmethodID getLE;
        jmethodID isAuthoritative;
        jmethodID getFlags;
        jmethodID dispose;
    } ElevationChunk_class;

    struct
    {
        jclass id;
        jfieldID value;
        jfieldID srid;
        jfieldID localFrame;
        jfieldID interpolated;
    } ElevationChunk_Data_class;

    bool ElevationChunk_class_init(JNIEnv *env) NOTHROWS;
}

ManagedElevationChunk::ManagedElevationChunk(JNIEnv *env_, jobject impl_) NOTHROWS :
    impl(env_->NewGlobalRef(impl_)),
    bounds(NULL, NULL)
{
    static bool clinit = ElevationChunk_class_init(env_);

    JNILocalRef mtype(*env_, env_->CallObjectMethod(impl, ElevationChunk_class.getType));
    JNIStringUTF_get(type, *env_, (jstring)mtype.get());
    JNILocalRef muri(*env_, env_->CallObjectMethod(impl, ElevationChunk_class.getUri));
    JNIStringUTF_get(uri, *env_, (jstring)muri.get());
    JNILocalRef mbounds(*env_, env_->CallObjectMethod(impl, ElevationChunk_class.getBounds));
    Feature::Interop_create(bounds, env_, mbounds);

}
ManagedElevationChunk::~ManagedElevationChunk() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->CallVoidMethod(impl, ElevationChunk_class.dispose);
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
const char *ManagedElevationChunk::getUri() const NOTHROWS
{
    return uri;
}
const char *ManagedElevationChunk::getType() const NOTHROWS
{
    return type;
}
double ManagedElevationChunk::getResolution() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, ElevationChunk_class.getResolution);
}
const Polygon2 *ManagedElevationChunk::getBounds() const NOTHROWS
{
    return static_cast<const Polygon2 *>(bounds.get());
}
TAKErr ManagedElevationChunk::createData(ElevationChunkDataPtr &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!impl)
        return TE_IllegalState;

    LocalJNIEnv env;
    JNILocalRef mdata(*env, env->CallObjectMethod(impl, ElevationChunk_class.createData));
    if(env->ExceptionCheck())
        return TE_Err;
    if(!mdata)
        return TE_IllegalState;

    std::unique_ptr<ElevationChunk::Data> cdata(new ElevationChunk::Data());
    cdata->interpolated = env->GetBooleanField(mdata, ElevationChunk_Data_class.interpolated);
    cdata->srid = env->GetBooleanField(mdata, ElevationChunk_Data_class.srid);
    JNILocalRef mdata_localFrame(*env, env->GetObjectField(mdata, ElevationChunk_Data_class.localFrame));
    if(mdata_localFrame) {
        code = Math::Interop_copy(&cdata->localFrame, env, mdata_localFrame);
        TE_CHECKRETURN_CODE(code);
    }
    JNILocalRef mdata_value(*env, env->GetObjectField(mdata, ElevationChunk_Data_class.value));
    if(mdata_value) {
        // XXX - if value is a wrapper, access as shared_ptr
        if(Model::Interop_isWrapped<Mesh>(env, mdata_value)) {
            code = Model::Interop_access(cdata->value, env, mdata_value);
            TE_CHECKRETURN_CODE(code);
        } else {
            // XXX - wrap
            return TE_Unsupported;
        }
    } else {
        return TE_IllegalState;
    }

    value = ElevationChunkDataPtr(cdata.release(), Memory_deleter_const<ElevationChunk::Data>);

    return code;
}
TAKErr ManagedElevationChunk::sample(double *value, const double latitude, const double longitude) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!impl)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    const double hae = env->CallDoubleMethod(impl, ElevationChunk_class.sample_DD, latitude, longitude);
    if(env->ExceptionCheck())
        return TE_Err;
    if(TE_ISNAN(hae))
        return TE_InvalidArg;
    *value = hae;
    return code;
}
TAKErr ManagedElevationChunk::sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(!impl)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    LocalJNIEnv env;
    jdoubleArray jarr = env->NewDoubleArray(count*3);
    {
        JNIDoubleArray jarr_w(*env, jarr, 0);
        for (std::size_t i = 0; i < count; i++) {
            jarr_w[i*3] = srcLng[i * srcLngStride];
            jarr_w[i*3+1] = srcLat[i * srcLatStride];
            jarr_w[i*3+2] = value[i*dstStride];
        }
    }
    const bool done = env->CallBooleanMethod(impl, ElevationChunk_class.sample__1DII, jarr, 0, count);
    if(env->ExceptionCheck())
        return TE_Err;

    {
        JNIDoubleArray jarr_w(*env, jarr, JNI_ABORT);
        for (std::size_t i = 0; i < count; i++) {
            value[i*dstStride] = jarr_w[i*3+2];
        }
    }

    return done ? TE_Ok : TE_Done;
}
double ManagedElevationChunk::getCE() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, ElevationChunk_class.getCE);
}
double ManagedElevationChunk::getLE() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, ElevationChunk_class.getLE);
}
bool ManagedElevationChunk::isAuthoritative() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, ElevationChunk_class.isAuthoritative);
}
unsigned int ManagedElevationChunk::getFlags() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(impl, ElevationChunk_class.getFlags);
}

namespace
{
    bool ElevationChunk_class_init(JNIEnv *env) NOTHROWS
    {
        ElevationChunk_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationChunk");
        ElevationChunk_class.getUri = env->GetMethodID(ElevationChunk_class.id, "getUri", "()Ljava/lang/String;");
        ElevationChunk_class.getType = env->GetMethodID(ElevationChunk_class.id, "getType", "()Ljava/lang/String;");
        ElevationChunk_class.getResolution = env->GetMethodID(ElevationChunk_class.id, "getResolution", "()D");
        ElevationChunk_class.getBounds = env->GetMethodID(ElevationChunk_class.id, "getBounds", "()Lcom/atakmap/map/layer/feature/geometry/Polygon;");
        ElevationChunk_class.createData = env->GetMethodID(ElevationChunk_class.id, "createData", "()Lcom/atakmap/map/elevation/ElevationChunk$Data;");
        ElevationChunk_class.sample_DD = env->GetMethodID(ElevationChunk_class.id, "sample", "(DD)D");
        ElevationChunk_class.sample__1DII = env->GetMethodID(ElevationChunk_class.id, "sample", "([DII)Z");
        ElevationChunk_class.getCE = env->GetMethodID(ElevationChunk_class.id, "getCE", "()D");
        ElevationChunk_class.getLE = env->GetMethodID(ElevationChunk_class.id, "getLE", "()D");
        ElevationChunk_class.isAuthoritative = env->GetMethodID(ElevationChunk_class.id, "isAuthoritative", "()Z");
        ElevationChunk_class.getFlags = env->GetMethodID(ElevationChunk_class.id, "getFlags", "()I");
        ElevationChunk_class.dispose = env->GetMethodID(ElevationChunk_class.id, "dispose", "()V");

        ElevationChunk_Data_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/elevation/ElevationChunk$Data");
        ElevationChunk_Data_class.value = env->GetFieldID(ElevationChunk_Data_class.id, "value", "Lcom/atakmap/map/layer/model/Mesh;");
        ElevationChunk_Data_class.value = env->GetFieldID(ElevationChunk_Data_class.id, "srid", "I");
        ElevationChunk_Data_class.localFrame = env->GetFieldID(ElevationChunk_Data_class.id, "localFrame", "Lcom/atakmap/math/Matrix;");
        ElevationChunk_Data_class.value = env->GetFieldID(ElevationChunk_Data_class.id, "interpolated", "Z");

        return true;
    }
}