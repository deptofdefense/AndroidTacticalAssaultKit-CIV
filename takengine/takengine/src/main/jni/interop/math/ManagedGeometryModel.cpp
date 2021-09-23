#include "ManagedGeometryModel.h"

#include <util/Memory.h>

#include "../../common.h"

using namespace TAKEngineJNI::Interop::Math;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    struct {
        jclass id;
        jmethodID intersect;
    } GeometryModel_class;

    struct {
        jclass id;
        jmethodID ctor;
    } Ray_class;

    struct {
        jclass id;
        jmethodID ctor;
    } Vector3D_class;

    bool Managed_class_init(JNIEnv &env) NOTHROWS;
}

ManagedGeometryModel::ManagedGeometryModel(JNIEnv &env, jobject impl_) NOTHROWS :
        impl(env.NewGlobalRef(impl_))
{
    static bool clinit = Managed_class_init(env);
}
ManagedGeometryModel::~ManagedGeometryModel() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
bool ManagedGeometryModel::intersect(Point2<double> *value, const Ray2<double> &ray) const
{
    if(!value)
        return false;
    LocalJNIEnv env;
    if(env->ExceptionCheck())
        return false;

    jobject morg = env->NewObject(pointDClass, pointD_ctor__DDD, ray.origin.x, ray.origin.y, ray.origin.z);
    jobject mdir = env->NewObject(Vector3D_class.id, Vector3D_class.ctor, ray.direction.x, ray.direction.y, ray.direction.z);
    jobject mray = env->NewObject(Ray_class.id, Ray_class.ctor, morg, mdir);

    jobject mvalue = env->CallObjectMethod(impl, GeometryModel_class.intersect, mray);
    env->DeleteLocalRef(mray);
    env->DeleteLocalRef(morg);
    env->DeleteLocalRef(mdir);
    if(env->ExceptionCheck())
        return false;
    if(!mvalue)
        return false;
    value->x = env->GetDoubleField(mvalue, pointD_x);
    value->y = env->GetDoubleField(mvalue, pointD_y);
    value->z = env->GetDoubleField(mvalue, pointD_z);
    return true;
}
GeometryModel2::GeometryClass ManagedGeometryModel::getGeomClass() const
{
    return GeometryModel2::UNDEFINED;
}
void ManagedGeometryModel::clone(GeometryModel2Ptr &value) const
{
    LocalJNIEnv env;
    value = GeometryModel2Ptr(new ManagedGeometryModel(*env, impl), Memory_deleter_const<GeometryModel2, ManagedGeometryModel>);
}

namespace
{
    bool Managed_class_init(JNIEnv &env) NOTHROWS
    {
        GeometryModel_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/GeometryModel");
        GeometryModel_class.intersect = env.GetMethodID(GeometryModel_class.id, "intersect", "(Lcom/atakmap/math/Ray;)Lcom/atakmap/math/PointD;");

        Ray_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/Ray");
        Ray_class.ctor = env.GetMethodID(Ray_class.id, "<init>", "(Lcom/atakmap/math/PointD;Lcom/atakmap/math/Vector3D;)V");

        Vector3D_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/Vector3D");
        Vector3D_class.ctor = env.GetMethodID(Vector3D_class.id, "<init>", "(DDD)V");

        return true;
    }
}