#include "interop/model/Interop.h"

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jfieldID pointer;
    } NativeMesh_class;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Interop_class_init(JNIEnv *env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Model::Interop_access(std::shared_ptr<TAK::Engine::Model::Mesh> &value, JNIEnv *env, jobject jmesh)
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!jmesh)
        return TE_InvalidArg;
    if(!Interop_isWrapped<Mesh>(env, jmesh))
        return TE_InvalidArg;
    JNILocalRef jpointer(*env, env->GetObjectField(jmesh, NativeMesh_class.pointer));
    if(!jpointer)
        return TE_IllegalState;
    int pointerType = env->GetIntField(jpointer, Pointer_class.type);
    if(pointerType == com_atakmap_interop_Pointer_RAW)
        return TE_InvalidArg;

    jlong pointer = env->GetLongField(jpointer, Pointer_class.value);
    if(pointerType == com_atakmap_interop_Pointer_UNIQUE) {
        // obtain the unique_ptr
        MeshPtr *cpointer = JLONG_TO_INTPTR(MeshPtr, pointer);

        std::shared_ptr<Mesh> cpointer_shared(std::move(*cpointer));
        // delete the unique_ptr pointer memory
        delete cpointer;

        // reset local 'pointer' to a new shared_ptr instance promoted from unique_ptr
        pointer = INTPTR_TO_JLONG(new std::shared_ptr<Mesh>(cpointer_shared));
        // update the Java object
        env->SetLongField(jpointer, Pointer_class.value, pointer);
        env->SetIntField(jpointer, Pointer_class.type, com_atakmap_interop_Pointer_SHARED);

        value = cpointer_shared;
        return TE_Ok;
    } else if(pointerType == com_atakmap_interop_Pointer_SHARED) {
        // obtain the shared_ptr
        std::shared_ptr<Mesh> *cpointer = JLONG_TO_INTPTR(std::shared_ptr<Mesh>, pointer);

        value = *cpointer;
        return TE_Ok;
    } else {
        return TE_IllegalState;
    }
}

// template specializations
template<>
bool TAKEngineJNI::Interop::Model::Interop_isWrapped<TAK::Engine::Model::Mesh>(JNIEnv *env, jobject obj) NOTHROWS
{
    if(!checkInit(env))
        return false;
    return obj && ATAKMapEngineJNI_equals(env, env->GetObjectClass(obj), NativeMesh_class.id);
}

namespace
{
    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv *env) NOTHROWS
    {
        if(!env)
            return false;

        NativeMesh_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/model/NativeMesh");
        NativeMesh_class.pointer = env->GetFieldID(NativeMesh_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");

        return true;
    }
}
