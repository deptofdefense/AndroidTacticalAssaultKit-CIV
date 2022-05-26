#include "jmesh.h"

#include <math/Mesh.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_math_Mesh_create
  (JNIEnv *env, jclass clazz, jdoubleArray jvertices, jint numCols, jint numRows)
{
    jdouble *vertices = env->GetDoubleArrayElements(jvertices, NULL);
    GeometryModel2Ptr retval(new Mesh(reinterpret_cast<double *>(vertices), numCols, numRows), Memory_deleter_const<GeometryModel2, Mesh>);
    env->ReleaseDoubleArrayElements(jvertices, vertices, JNI_ABORT);

    return NewPointer(env, std::move(retval));
}


