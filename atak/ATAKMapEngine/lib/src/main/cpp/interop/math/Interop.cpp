#include "interop/math/Interop.h"

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
    } Matrix_class;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Math_class_init(JNIEnv *env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Math::Interop_copy(Matrix2 *value, JNIEnv *env, jobject mmatrix) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!mmatrix)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;

    JNILocalRef mpointer(*env, env->GetObjectField(mmatrix, Matrix_class.pointer));
    Matrix2 *cmatrix = Pointer_get<Matrix2>(*env, mpointer);
    if(!cmatrix)
        return TE_IllegalState;

    value->set(*cmatrix);
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Math_class_init(env);
        return clinit;
    }
    bool Math_class_init(JNIEnv *env) NOTHROWS
    {
        if(!env)
            return false;

        Matrix_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/math/Matrix");
        Matrix_class.pointer = env->GetFieldID(Matrix_class.id, "pointer", "Lcom/atakmap/interop/Pointer;");
    }
}
