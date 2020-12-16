#include "jtessellate.h"

#include <renderer/Tessellate.h>

#include "common.h"
#include "junsafe.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIFloatArray.h"

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jdoubleArray JNICALL Java_com_atakmap_opengl_Tessellate_linestring___3DIIDZ
  (JNIEnv *env, jclass clazz, jdoubleArray jarr, jint size, jint count, jdouble threshold, jboolean wgs84)
{
    if(!jarr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIDoubleArray arr(*env, jarr, JNI_ABORT);

    VertexData src;
    src.data = arr.get<jdouble>();
    src.stride = sizeof(jdouble) * size;
    src.size = size;

    VertexDataPtr result(NULL, NULL);
    std::size_t outputCount;
    TAKErr code = Tessellate_linestring<jdouble>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
    if(code == TE_Done)
        return jarr;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return JNIDoubleArray_newDoubleArray(env, static_cast<jdouble *>(result->data), count*size);
}
JNIEXPORT jfloatArray JNICALL Java_com_atakmap_opengl_Tessellate_linestring___3FIIDZ
  (JNIEnv *env, jclass clazz, jfloatArray jarr, jint size, jint count, jdouble threshold, jboolean wgs84)
{
    if(!jarr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIFloatArray arr(*env, jarr, JNI_ABORT);

    VertexData src;
    src.data = arr.get<jfloat>();
    src.stride = sizeof(jfloat) * size;
    src.size = size;

    VertexDataPtr result(NULL, NULL);
    std::size_t outputCount;
    TAKErr code = Tessellate_linestring<jfloat>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
    if(code == TE_Done)
        return jarr;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return JNIFloatArray_newFloatArray(env, static_cast<jfloat *>(result->data), count*size);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_opengl_Tessellate_linestring__ILjava_nio_Buffer_2IIIDZ
  (JNIEnv *env, jclass clazz, jint tedt, jobject jsrc, jint stride, jint size, jint count, jdouble threshold, jboolean wgs84)
{
    if(!jsrc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);

    VertexData src;
    src.data = GET_BUFFER_POINTER(void, jsrc);
    src.stride = stride;
    src.size = size;

    VertexDataPtr result(NULL, NULL);
    std::size_t outputCount;

    switch((DataType)tedt) {
    case TEDT_UInt8 :
        code = Tessellate_linestring<uint8_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Int8 :
        code = Tessellate_linestring<int8_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_UInt16 :
        code = Tessellate_linestring<uint16_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Int16 :
        code = Tessellate_linestring<int16_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_UInt32 :
        code = Tessellate_linestring<uint32_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Int32 :
        code = Tessellate_linestring<int32_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_UInt64 :
        code = Tessellate_linestring<uint64_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Int64 :
        code = Tessellate_linestring<int64_t>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Float32 :
        code = Tessellate_linestring<float>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    case TEDT_Float64 :
        code = Tessellate_linestring<double>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
        break;
    default :
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0L;
    }
    if(code == TE_Done)
        return jsrc;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    // XXX - requires copy currently due to buffer free mechanism
    jlong buf = Java_com_atakmap_lang_Unsafe_allocate(env, NULL, outputCount*stride);
    memcpy(JLONG_TO_INTPTR(void, buf), result->data, outputCount*stride);
    return Java_com_atakmap_lang_Unsafe_newDirectBuffer(env, NULL, buf, outputCount*stride);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_opengl_Tessellate_polygonImpl
  (JNIEnv *env, jclass clazz, jobject jsrc, jint stride, jint size, jint count, jdouble threshold, jboolean wgs84)
{
    if(!jsrc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);

    // check for default stride requested
    if(!stride)
        stride = size*8;

    VertexData src;
    src.data = GET_BUFFER_POINTER(void, jsrc);
    src.stride = stride;
    src.size = size;

    VertexDataPtr result(NULL, NULL);
    std::size_t outputCount;

    code = Tessellate_polygon<double>(result, &outputCount, src, count, threshold, wgs84 ? Tessellate_WGS84Algorithm() : Tessellate_CartesianAlgorithm());
    if(code == TE_Done)
        return jsrc;
    else if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    // XXX - requires copy currently due to buffer free mechanism
    jlong buf = Java_com_atakmap_lang_Unsafe_allocate(env, NULL, outputCount*stride);
    memcpy(JLONG_TO_INTPTR(void, buf), result->data, outputCount*stride);
    return Java_com_atakmap_lang_Unsafe_newDirectBuffer(env, NULL, buf, outputCount*stride);
}
