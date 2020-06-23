#include "interop/core/Interop.h"

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID ctor__DDDLAltitudeReference__DD;
        jmethodID set__DDDLAltitudeReference__DD;
        jfieldID latitude;
        jfieldID longitude;
        jfieldID altitude;
        jfieldID altitudeReference;
        jfieldID ce90;
        jfieldID le90;

        struct
        {
            jobject HAE;
            jobject AGL;
        } AltitudeReference_enum;

    } GeoPoint_class;

    struct {
        jclass id;
        jmethodID ctor;
    } NativeProjection_class;

    void GeoPoint_interop_entry(JNIEnv *env) NOTHROWS;
    bool GeoPoint_class_init(JNIEnv *env) NOTHROWS;
    void NativeProjection_interop_entry(JNIEnv *env) NOTHROWS;
    bool NativeProjection_class_init(JNIEnv *env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Core::Interop_copy(jobject value, JNIEnv *env, const GeoPoint2 &p) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    GeoPoint_interop_entry(env);

    jobject maltRef;
    switch(p.altitudeRef) {
        case AltitudeReference::HAE :
            maltRef = GeoPoint_class.AltitudeReference_enum.HAE;
            break;
        case AltitudeReference::AGL :
            maltRef = GeoPoint_class.AltitudeReference_enum.AGL;
            break;
        default :
            return TE_Unsupported;
    }

    env->CallBooleanMethod(value, GeoPoint_class.set__DDDLAltitudeReference__DD, p.latitude, p.longitude, p.altitude, maltRef, p.ce90, p.le90);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_copy(GeoPoint2 *value, JNIEnv *env, jobject p) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    if(env->ExceptionCheck())
        return TE_Err;
    GeoPoint_interop_entry(env);

    AltitudeReference caltRef;
    jobject maltRef = env->GetObjectField(p, GeoPoint_class.altitudeReference);
    if(maltRef == NULL)
        return TE_InvalidArg;
    else if(maltRef == GeoPoint_class.AltitudeReference_enum.HAE)
        caltRef = AltitudeReference::HAE;
    else if(maltRef == GeoPoint_class.AltitudeReference_enum.AGL)
        caltRef = AltitudeReference::AGL;
    else
        return TE_InvalidArg;

    value->latitude = env->GetDoubleField(p, GeoPoint_class.latitude);
    value->longitude = env->GetDoubleField(p, GeoPoint_class.longitude);
    value->altitude = env->GetDoubleField(p, GeoPoint_class.altitude);
    value->altitudeRef = caltRef;
    value->ce90 = env->GetDoubleField(p, GeoPoint_class.ce90);
    value->le90 = env->GetDoubleField(p, GeoPoint_class.le90);
    return TE_Ok;
}
jobject TAKEngineJNI::Interop::Core::Interop_create(JNIEnv *env, const GeoPoint2 &p) NOTHROWS
{
    if(!env)
        return NULL;
    if(env->ExceptionCheck())
        return NULL;
    GeoPoint_interop_entry(env);

    jobject maltRef;
    switch(p.altitudeRef) {
        case AltitudeReference::HAE :
            maltRef = GeoPoint_class.AltitudeReference_enum.HAE;
            break;
        case AltitudeReference::AGL :
            maltRef = GeoPoint_class.AltitudeReference_enum.AGL;
            break;
        default :
            return NULL;
    }

    return env->NewObject(GeoPoint_class.id, GeoPoint_class.ctor__DDDLAltitudeReference__DD, p.latitude, p.longitude, p.altitude, maltRef, p.ce90, p.le90);
}

jobject TAKEngineJNI::Interop::Core::Interop_wrap(JNIEnv *env, TAK::Engine::Core::Projection2Ptr &&cproj) NOTHROWS
{
    if(!env)
        return NULL;
    if(!cproj.get())
        return NULL;
    jobject mpointer = NewPointer(env, std::move(cproj));
    NativeProjection_interop_entry(env);
    return env->NewObject(NativeProjection_class.id, NativeProjection_class.ctor, mpointer);
}
jobject TAKEngineJNI::Interop::Core::Interop_wrap(JNIEnv *env, const std::shared_ptr<TAK::Engine::Core::Projection2> &cproj) NOTHROWS
{
    if(!env)
        return NULL;
    if(!cproj.get())
        return NULL;
    jobject mpointer = NewPointer(env, cproj);
    NativeProjection_interop_entry(env);
    return env->NewObject(NativeProjection_class.id, NativeProjection_class.ctor, mpointer);
}
            
namespace
{
    void GeoPoint_interop_entry(JNIEnv *env) NOTHROWS
    {
        static bool geopoint_clinit = GeoPoint_class_init(env);
    }
    bool GeoPoint_class_init(JNIEnv *env) NOTHROWS
    {
        GeoPoint_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/coremap/maps/coords/GeoPoint");
        GeoPoint_class.ctor__DDDLAltitudeReference__DD = env->GetMethodID(GeoPoint_class.id, "<init>", "(DDDLcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;DD)V");
        GeoPoint_class.set__DDDLAltitudeReference__DD = env->GetMethodID(GeoPoint_class.id, "set", "(DDDLcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;DD)Z");
        GeoPoint_class.latitude = env->GetFieldID(GeoPoint_class.id, "latitude", "D");
        GeoPoint_class.longitude = env->GetFieldID(GeoPoint_class.id, "longitude", "D");
        GeoPoint_class.altitude = env->GetFieldID(GeoPoint_class.id, "altitude", "D");
        GeoPoint_class.altitudeReference = env->GetFieldID(GeoPoint_class.id, "altitudeReference", "Lcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;");
        GeoPoint_class.ce90 = env->GetFieldID(GeoPoint_class.id, "ce90", "D");
        GeoPoint_class.le90 = env->GetFieldID(GeoPoint_class.id, "le90", "D");

        jclass AltitudeReference_enum = ATAKMapEngineJNI_findClass(env, "com/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference");
        GeoPoint_class.AltitudeReference_enum.HAE = env->NewWeakGlobalRef(env->GetStaticObjectField(AltitudeReference_enum, env->GetStaticFieldID(AltitudeReference_enum, "HAE", "Lcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;")));
        GeoPoint_class.AltitudeReference_enum.AGL = env->NewWeakGlobalRef(env->GetStaticObjectField(AltitudeReference_enum, env->GetStaticFieldID(AltitudeReference_enum, "AGL", "Lcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;")));

        return true;
    }

    void NativeProjection_interop_entry(JNIEnv *env) NOTHROWS
    {
        static bool nativeprojection_clinit = NativeProjection_class_init(env);
    }
    bool NativeProjection_class_init(JNIEnv *env) NOTHROWS
    {
        NativeProjection_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/projection/NativeProjection");
        NativeProjection_class.ctor = env->GetMethodID(NativeProjection_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;)V");

        return true;
    }
}