#include "ManagedProjection.h"

#include "../../common.h"
#include "Interop.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    struct
    {
        jclass id;
        jmethodID forward;
        jmethodID inverse;
        jmethodID getSpatialReferenceID;
        jmethodID getMinLatitude;
        jmethodID getMinLongitude;
        jmethodID getMaxLatitude;
        jmethodID getMaxLongitude;
        jmethodID is3D;
    } Projection_class;

    bool Projection_class_init(JNIEnv &env) NOTHROWS;
}

ManagedProjection::ManagedProjection(JNIEnv &env, jobject impl_) NOTHROWS :
    impl(env.NewGlobalRef(impl_))
{
    static bool clinit = Projection_class_init(env);
}
ManagedProjection::~ManagedProjection() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
int ManagedProjection::getSpatialReferenceID() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(impl, Projection_class.getSpatialReferenceID);
}
TAKErr ManagedProjection::forward(Point2<double> *proj, const GeoPoint2 &geo) const NOTHROWS
{
    if(!proj)
        return TE_InvalidArg;

    LocalJNIEnv env;

    jobject mgeo = Interop_create(env, geo);
    if(!mgeo)
        return TE_Err;
    jobject mproj = env->CallObjectMethod(impl, Projection_class.forward, mgeo, NULL);
    env->DeleteLocalRef(mgeo);
    if(!mproj)
        return TE_Err;

    proj->x = env->GetDoubleField(mproj, pointD_x);
    proj->y = env->GetDoubleField(mproj, pointD_y);
    proj->z = env->GetDoubleField(mproj, pointD_z);
    env->DeleteLocalRef(mproj);

    return TE_Ok;
}
TAKErr ManagedProjection::inverse(GeoPoint2 *geo, const Point2<double> &proj) const NOTHROWS
{
    TAKErr code(TE_Ok);
    LocalJNIEnv env;

    jobject mproj = env->NewObject(pointDClass, pointD_ctor__DDD, proj.x, proj.y, proj.z);
    if(!mproj)
        return TE_Err;
    jobject mgeo = env->CallObjectMethod(impl, Projection_class.inverse, mproj, NULL);
    env->DeleteLocalRef(mproj);
    if(!mgeo)
        return TE_Err;
    code = Interop_copy(geo, env, mgeo);
    env->DeleteLocalRef(mgeo);
    TE_CHECKRETURN_CODE(code);

    return code;
}
double ManagedProjection::getMinLatitude() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, Projection_class.getMinLatitude);
}
double ManagedProjection::getMaxLatitude() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, Projection_class.getMaxLatitude);
}
double ManagedProjection::getMinLongitude() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, Projection_class.getMinLongitude);
}
double ManagedProjection::getMaxLongitude() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, Projection_class.getMaxLongitude);
}
bool ManagedProjection::is3D() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, Projection_class.is3D);
}

namespace
{
    bool Projection_class_init(JNIEnv &env) NOTHROWS
    {
        Projection_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/projection/Projection");
        Projection_class.forward = env.GetMethodID(Projection_class.id, "forward", "(Lcom/atakmap/coremap/maps/coords/GeoPoint;Lcom/atakmap/math/PointD;)Lcom/atakmap/math/PointD;");
        Projection_class.inverse = env.GetMethodID(Projection_class.id, "inverse", "(Lcom/atakmap/math/PointD;Lcom/atakmap/coremap/maps/coords/GeoPoint;)Lcom/atakmap/coremap/maps/coords/GeoPoint;");
        Projection_class.getSpatialReferenceID = env.GetMethodID(Projection_class.id, "getSpatialReferenceID", "()I");
        Projection_class.getMinLatitude = env.GetMethodID(Projection_class.id, "getMinLatitude", "()D");
        Projection_class.getMinLongitude = env.GetMethodID(Projection_class.id, "getMinLongitude", "()D");
        Projection_class.getMaxLatitude = env.GetMethodID(Projection_class.id, "getMaxLatitude", "()D");
        Projection_class.getMaxLongitude = env.GetMethodID(Projection_class.id, "getMaxLongitude", "()D");
        Projection_class.is3D = env.GetMethodID(Projection_class.id, "is3D", "()Z");

        return true;
    }
}
