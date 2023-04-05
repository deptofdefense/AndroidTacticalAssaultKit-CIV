#include "interop/core/Interop.h"

#include "common.h"
#include "interop/Pointer.h"
#include "interop/InterfaceMarshalContext.h"
#include "interop/core/ManagedLayer.h"
#include "interop/core/ManagedVisibilityListener.h"

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

    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
        jmethodID init;
    } MapSceneModel_class;
    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeLayer_class;

    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } NativeLayer_NativeOnLayerVisibilityChangedListener_class;

    struct
    {
        struct {
            jobject Flat;
            jobject Globe;
        } DisplayMode_enum;
        struct {
            jobject LowerLeft;
            jobject UpperLeft;
        } DisplayOrigin_enum;
        struct {
            jobject RayCast;
            jobject Transform;
        } InverseMode_enum;
        struct {
            jobject None;
            jobject Transformed;
            jobject GeometryModel;
            jobject SurfaceMesh;
            jobject TerrainMesh;
        } InverseResult_enum;
        struct {
            jobject Ignore;
            jobject AdjustCamera;
            jobject AdjustFocus;
            jobject Abort;
        } CameraCollision_enum;
    } IMapRendererEnums_class;

    InterfaceMarshalContext<atakmap::core::Layer> Layer_interop;
    InterfaceMarshalContext<atakmap::core::Layer::VisibilityListener> Layer_VisibilityListener_interop;

    void GeoPoint_interop_entry(JNIEnv *env) NOTHROWS;
    bool GeoPoint_class_init(JNIEnv *env) NOTHROWS;
    void NativeProjection_interop_entry(JNIEnv *env) NOTHROWS;
    bool NativeProjection_class_init(JNIEnv *env) NOTHROWS;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Interop_class_init(JNIEnv &env) NOTHROWS;
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

// MapSceneModel interop
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapSceneModel2 &cmodel) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    MapSceneModel2Ptr retval(new MapSceneModel2(cmodel), Memory_deleter_const<MapSceneModel2>);
    Java::JNILocalRef mpointer(env, NewPointer(&env, std::move(retval)));
    value = Java::JNILocalRef(env, env.NewObject(MapSceneModel_class.id, MapSceneModel_class.ctor, mpointer.release(), NULL));
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(jobject value, JNIEnv &env, const MapSceneModel2 &cmodel) NOTHROWS
{
    if(!value)
        return TE_InvalidArg;
    if(!checkInit(env))
        return TE_IllegalState;
    Java::JNILocalRef mpointer(env, env.GetObjectField(value, MapSceneModel_class.pointer));
    if(!mpointer)
        return TE_IllegalState;
    MapSceneModel2 *cref = Pointer_get<MapSceneModel2>(env, mpointer);
    if(!cref)
        return TE_IllegalState;
    *cref = cmodel;
    env.CallVoidMethod(value, MapSceneModel_class.init);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(std::shared_ptr<TAK::Engine::Core::MapSceneModel2> &value, JNIEnv &env, jobject mmodel) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!mmodel)
        return TE_InvalidArg;

    return Pointer_get<MapSceneModel2>(value, env, env.GetObjectField(mmodel, MapSceneModel_class.pointer));
}

// Layer interop
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(std::shared_ptr<atakmap::core::Layer> &value, JNIEnv &env, jobject mlayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_interop.marshal<ManagedLayer>(value, env, mlayer);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(atakmap::core::LayerPtr &value, JNIEnv &env, jobject mlayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_interop.marshal<ManagedLayer>(value, env, mlayer);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<atakmap::core::Layer> &clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_interop.marshal<ManagedLayer>(value, env, clayer);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, atakmap::core::LayerPtr &&clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_interop.marshal<ManagedLayer>(value, env, std::move(clayer));
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const atakmap::core::Layer &clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_interop.marshal<ManagedLayer>(value, env, clayer);
}
template<>
TAKErr TAKEngineJNI::Interop::Core::Interop_isWrapper<atakmap::core::Layer>(bool *value, JNIEnv &env, jobject mlayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = Layer_interop.isWrapper(env, mlayer);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_isWrapper(bool *value, JNIEnv &env, const atakmap::core::Layer &clayer) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = Layer_interop.isWrapper<ManagedLayer>(clayer);
    return TE_Ok;
}

// Layer::VisibilityListener interop
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(std::shared_ptr<atakmap::core::Layer::VisibilityListener> &value, JNIEnv &env, jobject mlistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_VisibilityListener_interop.marshal<ManagedVisibilityListener>(value, env, mlistener);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(std::unique_ptr<atakmap::core::Layer::VisibilityListener, void(*)(const atakmap::core::Layer::VisibilityListener *)> &value, JNIEnv &env, jobject mlistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_VisibilityListener_interop.marshal<ManagedVisibilityListener>(value, env, mlistener);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<atakmap::core::Layer::VisibilityListener> &clistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_VisibilityListener_interop.marshal<ManagedVisibilityListener>(value, env, clistener);
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, std::unique_ptr<atakmap::core::Layer::VisibilityListener, void(*)(const atakmap::core::Layer::VisibilityListener *)> &&clistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_VisibilityListener_interop.marshal<ManagedVisibilityListener>(value, env, std::move(clistener));
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const atakmap::core::Layer::VisibilityListener &clistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    return Layer_VisibilityListener_interop.marshal<ManagedVisibilityListener>(value, env, clistener);
}
template<>
TAKErr TAKEngineJNI::Interop::Core::Interop_isWrapper<atakmap::core::Layer::VisibilityListener>(bool *value, JNIEnv &env, jobject mlistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = Layer_VisibilityListener_interop.isWrapper(env, mlistener);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_isWrapper(bool *value, JNIEnv &env, const atakmap::core::Layer::VisibilityListener &clistener) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    *value = Layer_VisibilityListener_interop.isWrapper<ManagedVisibilityListener>(clistener);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapRenderer::CameraCollision ccameraCollision) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    switch(ccameraCollision) {
        case MapRenderer::CameraCollision::Abort :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.CameraCollision_enum.Abort);
            break;
        case MapRenderer::CameraCollision::AdjustCamera :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.CameraCollision_enum.AdjustCamera);
            break;
        case MapRenderer::CameraCollision::AdjustFocus :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.CameraCollision_enum.AdjustFocus);
            break;
        case MapRenderer::CameraCollision::Ignore :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.CameraCollision_enum.Ignore);
            break;
        default :
            return TE_InvalidArg;
    }
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(MapRenderer::CameraCollision *value, JNIEnv &env, jobject mdisplayOrigin) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.CameraCollision_enum.Abort)) {
        *value = MapRenderer::CameraCollision::Abort;
    } else if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.CameraCollision_enum.AdjustCamera)) {
        *value = MapRenderer::CameraCollision::AdjustCamera;
    } else if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.CameraCollision_enum.AdjustFocus)) {
        *value = MapRenderer::CameraCollision::AdjustFocus;
    } else if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.CameraCollision_enum.Ignore)) {
        *value = MapRenderer::CameraCollision::Ignore;
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapRenderer::DisplayMode cdisplayMode) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    switch(cdisplayMode) {
        case MapRenderer::DisplayMode::Flat :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.DisplayMode_enum.Flat);
            break;
        case MapRenderer::DisplayMode::Globe :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.DisplayMode_enum.Globe);
            break;
        default :
            return TE_InvalidArg;
    }
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(MapRenderer::DisplayMode *value, JNIEnv &env, jobject mdisplayMode) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    if(ATAKMapEngineJNI_equals(&env, mdisplayMode, IMapRendererEnums_class.DisplayMode_enum.Flat)) {
        *value = MapRenderer::DisplayMode::Flat;
    } else if(ATAKMapEngineJNI_equals(&env, mdisplayMode, IMapRendererEnums_class.DisplayMode_enum.Globe)) {
        *value = MapRenderer::DisplayMode::Globe;
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapRenderer::DisplayOrigin cdisplayOrigin) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    switch(cdisplayOrigin) {
        case MapRenderer::DisplayOrigin::LowerLeft :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.DisplayOrigin_enum.LowerLeft);
            break;
        case MapRenderer::DisplayOrigin::UpperLeft :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.DisplayOrigin_enum.UpperLeft);
            break;
        default :
            return TE_InvalidArg;
    }
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(MapRenderer::DisplayOrigin *value, JNIEnv &env, jobject mdisplayOrigin) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.DisplayOrigin_enum.LowerLeft)) {
        *value = MapRenderer::DisplayOrigin::LowerLeft;
    } else if(ATAKMapEngineJNI_equals(&env, mdisplayOrigin, IMapRendererEnums_class.DisplayOrigin_enum.UpperLeft)) {
        *value = MapRenderer::DisplayOrigin::UpperLeft;
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapRenderer::InverseMode cinverseMode) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    switch(cinverseMode) {
        case MapRenderer::InverseMode::RayCast :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseMode_enum.RayCast);
            break;
        case MapRenderer::DisplayMode::Globe :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseMode_enum.Transform);
            break;
        default :
            return TE_InvalidArg;
    }
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(MapRenderer::InverseMode *value, JNIEnv &env, jobject minverseMode) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    if(ATAKMapEngineJNI_equals(&env, minverseMode, IMapRendererEnums_class.InverseMode_enum.RayCast)) {
        *value = MapRenderer::InverseMode::RayCast;
    } else if(ATAKMapEngineJNI_equals(&env, minverseMode, IMapRendererEnums_class.InverseMode_enum.Transform)) {
        *value = MapRenderer::InverseMode::Transform;
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const MapRenderer::InverseResult cinverseResult) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    switch(cinverseResult) {
        case MapRenderer::InverseResult::GeometryModel :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseResult_enum.GeometryModel);
            break;
        case MapRenderer::InverseResult::None :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseResult_enum.None);
            break;
        case MapRenderer::InverseResult::SurfaceMesh :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseResult_enum.SurfaceMesh);
            break;
        case MapRenderer::InverseResult::TerrainMesh :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseResult_enum.TerrainMesh);
            break;
        case MapRenderer::InverseResult::Transformed :
            value = Java::JNILocalRef(env, IMapRendererEnums_class.InverseResult_enum.Transformed);
            break;
        default :
            return TE_InvalidArg;
    }
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Core::Interop_marshal(MapRenderer::InverseResult *value, JNIEnv &env, jobject minverseResult) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    if(ATAKMapEngineJNI_equals(&env, minverseResult, IMapRendererEnums_class.InverseResult_enum.GeometryModel)) {
        *value = MapRenderer::InverseResult::GeometryModel;
    } else if(ATAKMapEngineJNI_equals(&env, minverseResult, IMapRendererEnums_class.InverseResult_enum.None)) {
        *value = MapRenderer::InverseResult::None;
    } else if(ATAKMapEngineJNI_equals(&env, minverseResult, IMapRendererEnums_class.InverseResult_enum.SurfaceMesh)) {
        *value = MapRenderer::InverseResult::SurfaceMesh;
    } else if(ATAKMapEngineJNI_equals(&env, minverseResult, IMapRendererEnums_class.InverseResult_enum.TerrainMesh)) {
        *value = MapRenderer::InverseResult::TerrainMesh;
    } else if(ATAKMapEngineJNI_equals(&env, minverseResult, IMapRendererEnums_class.InverseResult_enum.Transformed)) {
        *value = MapRenderer::InverseResult::Transformed;
    } else {
        return TE_InvalidArg;
    }

    return TE_Ok;
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
        GeoPoint_class.AltitudeReference_enum.HAE = env->NewGlobalRef(env->GetStaticObjectField(AltitudeReference_enum, env->GetStaticFieldID(AltitudeReference_enum, "HAE", "Lcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;")));
        GeoPoint_class.AltitudeReference_enum.AGL = env->NewGlobalRef(env->GetStaticObjectField(AltitudeReference_enum, env->GetStaticFieldID(AltitudeReference_enum, "AGL", "Lcom/atakmap/coremap/maps/coords/GeoPoint$AltitudeReference;")));

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

    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env->GetMethodID(c.id, #m, sig)

        MapSceneModel_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/MapSceneModel");
        MapSceneModel_class.pointer = env.GetFieldID(MapSceneModel_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        MapSceneModel_class.ctor = env.GetMethodID(MapSceneModel_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");
        MapSceneModel_class.init = env.GetMethodID(MapSceneModel_class.id, "init", "()V");

        NativeLayer_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/NativeLayer");
        NativeLayer_class.pointer = env.GetFieldID(NativeLayer_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeLayer_class.ctor = env.GetMethodID(NativeLayer_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        Layer_interop.init(env, NativeLayer_class.id, NativeLayer_class.pointer, NativeLayer_class.ctor);

        NativeLayer_NativeOnLayerVisibilityChangedListener_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/NativeLayer$NativeOnLayerVisibilityChangedListener");
        NativeLayer_NativeOnLayerVisibilityChangedListener_class.pointer = env.GetFieldID(NativeLayer_NativeOnLayerVisibilityChangedListener_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        NativeLayer_NativeOnLayerVisibilityChangedListener_class.ctor = env.GetMethodID(NativeLayer_NativeOnLayerVisibilityChangedListener_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        Layer_VisibilityListener_interop.init(env, NativeLayer_NativeOnLayerVisibilityChangedListener_class.id, NativeLayer_NativeOnLayerVisibilityChangedListener_class.pointer, NativeLayer_NativeOnLayerVisibilityChangedListener_class.ctor);

        // IMapRendererEnums
        jclass CameraCollision_enum = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/IMapRendererEnums$CameraCollision");
        IMapRendererEnums_class.CameraCollision_enum.Abort = env.NewGlobalRef(env.GetStaticObjectField(CameraCollision_enum, env.GetStaticFieldID(CameraCollision_enum, "Abort", "Lgov/tak/api/engine/map/IMapRendererEnums$CameraCollision;")));
        IMapRendererEnums_class.CameraCollision_enum.AdjustCamera = env.NewGlobalRef(env.GetStaticObjectField(CameraCollision_enum, env.GetStaticFieldID(CameraCollision_enum, "AdjustCamera", "Lgov/tak/api/engine/map/IMapRendererEnums$CameraCollision;")));
        IMapRendererEnums_class.CameraCollision_enum.AdjustFocus = env.NewGlobalRef(env.GetStaticObjectField(CameraCollision_enum, env.GetStaticFieldID(CameraCollision_enum, "AdjustFocus", "Lgov/tak/api/engine/map/IMapRendererEnums$CameraCollision;")));
        IMapRendererEnums_class.CameraCollision_enum.Ignore = env.NewGlobalRef(env.GetStaticObjectField(CameraCollision_enum, env.GetStaticFieldID(CameraCollision_enum, "Ignore", "Lgov/tak/api/engine/map/IMapRendererEnums$CameraCollision;")));

        jclass DisplayMode_enum = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/IMapRendererEnums$DisplayMode");
        IMapRendererEnums_class.DisplayMode_enum.Flat = env.NewGlobalRef(env.GetStaticObjectField(DisplayMode_enum, env.GetStaticFieldID(DisplayMode_enum, "Flat", "Lgov/tak/api/engine/map/IMapRendererEnums$DisplayMode;")));
        IMapRendererEnums_class.DisplayMode_enum.Globe = env.NewGlobalRef(env.GetStaticObjectField(DisplayMode_enum, env.GetStaticFieldID(DisplayMode_enum, "Globe", "Lgov/tak/api/engine/map/IMapRendererEnums$DisplayMode;")));

        jclass DisplayOrigin_enum = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/IMapRendererEnums$DisplayOrigin");
        IMapRendererEnums_class.DisplayOrigin_enum.LowerLeft = env.NewGlobalRef(env.GetStaticObjectField(DisplayOrigin_enum, env.GetStaticFieldID(DisplayOrigin_enum, "LowerLeft", "Lgov/tak/api/engine/map/IMapRendererEnums$DisplayOrigin;")));
        IMapRendererEnums_class.DisplayOrigin_enum.UpperLeft = env.NewGlobalRef(env.GetStaticObjectField(DisplayOrigin_enum, env.GetStaticFieldID(DisplayOrigin_enum, "UpperLeft", "Lgov/tak/api/engine/map/IMapRendererEnums$DisplayOrigin;")));

        jclass InverseMode_enum = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/IMapRendererEnums$InverseMode");
        IMapRendererEnums_class.InverseMode_enum.RayCast = env.NewGlobalRef(env.GetStaticObjectField(InverseMode_enum, env.GetStaticFieldID(InverseMode_enum, "RayCast", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseMode;")));
        IMapRendererEnums_class.InverseMode_enum.Transform = env.NewGlobalRef(env.GetStaticObjectField(InverseMode_enum, env.GetStaticFieldID(InverseMode_enum, "Transform", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseMode;")));

        jclass InverseResult_enum = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/IMapRendererEnums$InverseResult");
        IMapRendererEnums_class.InverseResult_enum.GeometryModel = env.NewGlobalRef(env.GetStaticObjectField(InverseResult_enum, env.GetStaticFieldID(InverseResult_enum, "GeometryModel", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseResult;")));
        IMapRendererEnums_class.InverseResult_enum.None = env.NewGlobalRef(env.GetStaticObjectField(InverseResult_enum, env.GetStaticFieldID(InverseResult_enum, "None", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseResult;")));
        IMapRendererEnums_class.InverseResult_enum.SurfaceMesh = env.NewGlobalRef(env.GetStaticObjectField(InverseResult_enum, env.GetStaticFieldID(InverseResult_enum, "SurfaceMesh", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseResult;")));
        IMapRendererEnums_class.InverseResult_enum.TerrainMesh = env.NewGlobalRef(env.GetStaticObjectField(InverseResult_enum, env.GetStaticFieldID(InverseResult_enum, "TerrainMesh", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseResult;")));
        IMapRendererEnums_class.InverseResult_enum.Transformed = env.NewGlobalRef(env.GetStaticObjectField(InverseResult_enum, env.GetStaticFieldID(InverseResult_enum, "Transformed", "Lgov/tak/api/engine/map/IMapRendererEnums$InverseResult;")));

        return true;
    }
}
