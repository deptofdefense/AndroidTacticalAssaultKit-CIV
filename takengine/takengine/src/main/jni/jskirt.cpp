#include "jskirt.h"

#include "common.h"

#include <renderer/Skirt.h>

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

JNIEXPORT void JNICALL Java_com_atakmap_opengl_Skirt_create
  (JNIEnv *env, jclass clazz, jint mode, jint stride, jint verticesType, jlong verticesPtr, jint verticesLim, jint verticesSize, jint indicesType, jlong edgeIndicesPtr, jint edgeIndicesSize, jint count, jlong skirtIndicesPtr, jint skirtIndicesSize, jfloat height)
{
    TAKErr code(TE_Ok);
    MemBuffer2 vertices(JLONG_TO_INTPTR(uint8_t, verticesPtr), verticesSize);
    code = vertices.limit(verticesLim);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    MemBuffer2 edgeIndices(JLONG_TO_INTPTR(const uint8_t, edgeIndicesPtr), edgeIndicesSize);
    MemBuffer2 skirtIndices(JLONG_TO_INTPTR(uint8_t, skirtIndicesPtr), skirtIndicesSize);
    switch(verticesType) {
        case GL_FLOAT :
            switch(indicesType) {
                case GL_UNSIGNED_SHORT :
                    code = Skirt_create<float, uint16_t>(vertices, skirtIndices, mode, stride, edgeIndicesPtr ? &edgeIndices : NULL, count, height);
                    break;
                case GL_UNSIGNED_INT :
                    code = Skirt_create<float, uint32_t>(vertices, skirtIndices, mode, stride, edgeIndicesPtr ? &edgeIndices : NULL, count, height);
                    break;
                default :
                    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                    return;
            }
            break;
        default :
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
    }
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jint JNICALL Java_com_atakmap_opengl_Skirt_getNumOutputVertices
  (JNIEnv *env, jclass clazz, jint count)
{
    return Skirt_getNumOutputVertices(count);
}
JNIEXPORT jint JNICALL Java_com_atakmap_opengl_Skirt_getNumOutputIndices
  (JNIEnv *env, jclass clazz, jint mode, jint count)
{
    TAKErr code(TE_Ok);
    std::size_t retval;
    code = Skirt_getNumOutputIndices(&retval, mode, count);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return retval;
}
