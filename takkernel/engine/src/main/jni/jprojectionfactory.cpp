#include "jprojectionfactory.h"

#include <core/ProjectionFactory3.h>
#include <core/ProjectionSpi3.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/core/ManagedProjection.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Core;

namespace
{
    struct
    {
        jclass id;
        jmethodID create;
    } ProjectionSpi_class;

    class ManagedProjectionSpi : public ProjectionSpi3
    {
    public :
        ManagedProjectionSpi(JNIEnv &env, jobject impl) NOTHROWS;
        ~ManagedProjectionSpi() NOTHROWS;
    public :
        TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS;
    private :
        jobject impl;
    };

    bool ProjectionSpi_class_init(JNIEnv &env) NOTHROWS;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_projection_ProjectionFactory_setPreferLibrarySpi
  (JNIEnv *env, jclass clazz, jboolean prefer)
{
    TAKErr code(TE_Ok);
    code = ProjectionFactory3_setPreferSdkProjections(prefer);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_ProjectionFactory_getProjectionImpl
  (JNIEnv *env, jclass clazz, jint srid)
{
    TAKErr code(TE_Ok);
    Projection2Ptr retval(NULL, NULL);
    code = ProjectionFactory3_create(retval, srid);
    if(code == TE_InvalidArg)
        return NULL;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    if(ManagedProjection *managed = dynamic_cast<ManagedProjection *>(retval.get())) {
        return env->NewLocalRef(managed->impl);
    } else {
        return Interop_wrap(env, std::move(retval));
    }
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_ProjectionFactory_registerSpiImpl
  (JNIEnv *env, jclass clazz, jobject mspi, jint priority)
{
    TAKErr code(TE_Ok);
    if(!mspi) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    std::shared_ptr<ProjectionSpi3> cspi = ProjectionSpi3Ptr(new ManagedProjectionSpi(*env, mspi), Memory_deleter_const<ProjectionSpi3, ManagedProjectionSpi>);
    code = ProjectionFactory3_registerSpi(cspi, priority);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, cspi);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_ProjectionFactory_unregisterSpiImpl
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    if(!mpointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    ProjectionFactory3_unregisterSpi(*Pointer_get<ProjectionSpi3>(env, mpointer));

    Pointer_destruct_iface<ProjectionSpi3>(env, mpointer);
}

namespace
{
    ManagedProjectionSpi::ManagedProjectionSpi(JNIEnv &env, jobject impl_) NOTHROWS :
        impl(env.NewGlobalRef(impl_))
    {
        static bool clinit = ProjectionSpi_class_init(env);
    }
    ManagedProjectionSpi::~ManagedProjectionSpi() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    TAKErr ManagedProjectionSpi::create(Projection2Ptr &value, const int srid) NOTHROWS
    {
        LocalJNIEnv env;
        if(env->ExceptionCheck())
            return TE_Err;
        jobject result = env->CallObjectMethod(impl, ProjectionSpi_class.create, srid);
        if(!result)
            return TE_InvalidArg;
        value = Projection2Ptr(new ManagedProjection(*env, result), Memory_deleter_const<Projection2, ManagedProjection>);
        return TE_Ok;
    }

    bool ProjectionSpi_class_init(JNIEnv &env) NOTHROWS
    {
        ProjectionSpi_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/projection/ProjectionSpi");
        ProjectionSpi_class.create = env.GetMethodID(ProjectionSpi_class.id, "create", "(I)Lcom/atakmap/map/projection/Projection;");

        return true;
    }
}
