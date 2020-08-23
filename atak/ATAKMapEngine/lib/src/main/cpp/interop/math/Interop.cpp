#include "interop/math/Interop.h"

#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
    } Matrix_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Math_interop_init(JNIEnv &env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Math::Interop_copy(Matrix2 *value, JNIEnv *env, jobject mmatrix) NOTHROWS
{
    if(!env)
        return TE_InvalidArg;
    if(!checkInit(*env))
        return TE_IllegalState;
    if(!mmatrix)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;

    JNILocalRef mpointer(*env, env->GetObjectField(mmatrix, Matrix_class.pointer));
    Matrix2 *cmatrix = Pointer_get<Matrix2>(env, mpointer);
    if(!cmatrix)
        return TE_IllegalState;

    value->set(*cmatrix);
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(JNILocalRef &value, JNIEnv &env, const Matrix2 &cmatrix) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    Matrix2Ptr retval(new Matrix2(cmatrix), Memory_deleter_const<Matrix2>);
    value = JNILocalRef(env, env.NewObject(Matrix_class.id, Matrix_class.ctor, NewPointer(&env, std::move(retval)), NULL));
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::Math::Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Math::Matrix2 &cmatrix) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;

    JNILocalRef mpointer(env, env.GetObjectField(value, Matrix_class.pointer));
    Matrix2 *impl = Pointer_get<Matrix2>(env, mpointer);
    if(!impl)
        return TE_IllegalState;
    impl->set(cmatrix);
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Math_interop_init(env);
        return clinit;
    }
    bool Math_interop_init(JNIEnv &env) NOTHROWS
    {
        Matrix_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/math/Matrix");
        Matrix_class.pointer = env.GetFieldID(Matrix_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
        Matrix_class.ctor = env.GetMethodID(Matrix_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;Ljava/lang/Object;)V");

        return true;
    }
}
