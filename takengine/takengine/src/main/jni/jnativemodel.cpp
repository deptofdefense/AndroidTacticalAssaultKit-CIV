#include "jnativemodel.h"

#include <math/Point2.h>
#include <model/Mesh.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    std::size_t getDataTypeSize(const DataType &type) NOTHROWS
    {
        switch(type) {
        case TEDT_Int8 :
        case TEDT_UInt8 :
            return 1u;
        case TEDT_Int16 :
        case TEDT_UInt16 :
            return 2u;
        case TEDT_Int32 :
        case TEDT_UInt32 :
        case TEDT_Float32 :
            return 4u;
        case TEDT_Float64 :
            return 8u;
        }

        return 0u;
    }

}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getNumMaterials
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    const Mesh *model = JLONG_TO_INTPTR(Mesh, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return model->getNumMaterials();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getMaterialTextureUri
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    const Mesh *model = JLONG_TO_INTPTR(Mesh, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    Material material;
    TAKErr code = model->getMaterial(&material, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    const char *ctextureUri = material.textureUri;
    if(!ctextureUri)
        return NULL;
    return env->NewStringUTF(ctextureUri);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getMaterialColor
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    const Mesh *model = JLONG_TO_INTPTR(Mesh, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    Material material;
    TAKErr code = model->getMaterial(&material, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return material.color;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getMaterialTextureIndex
  (JNIEnv *env, jclass clazz, jlong ptr, jint index)
{
    const Mesh *model = JLONG_TO_INTPTR(Mesh, ptr);
    if(!model) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    Material material;
    TAKErr code = model->getMaterial(&material, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getNumVertices
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.getNumVertices();
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getNumFaces
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.getNumFaces();
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_NativeMesh_isIndexed
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.isIndexed();
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getPosition
  (JNIEnv *env, jclass clazz, jobject jpointer, jint index, jobject jxyz)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    Point2<double> xyz;
    TAKErr code = model.getPosition(&xyz, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    env->SetDoubleField(jxyz, pointD_x, xyz.x);
    env->SetDoubleField(jxyz, pointD_y, xyz.y);
    env->SetDoubleField(jxyz, pointD_z, xyz.z);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTextureCoordinate
  (JNIEnv *env, jclass clazz, jobject jpointer, jint texCoordAttr, jint index, jobject juv)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    Point2<float> uv;
    TAKErr code = model.getTextureCoordinate(&uv, (VertexAttribute)texCoordAttr, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    env->SetDoubleField(juv, pointD_x, uv.x);
    env->SetDoubleField(juv, pointD_y, uv.y);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getNormal
  (JNIEnv *env, jclass clazz, jobject jpointer, jint index, jobject jxyz)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    Point2<float> xyz;
    TAKErr code = model.getNormal(&xyz, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    env->SetDoubleField(jxyz, pointD_x, xyz.x);
    env->SetDoubleField(jxyz, pointD_y, xyz.y);
    env->SetDoubleField(jxyz, pointD_z, xyz.z);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getColor
  (JNIEnv *env, jclass clazz, jobject jpointer, jint index)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    unsigned int argb;
    TAKErr code = model.getColor(&argb, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return argb;
}

JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getVertexDataLayout
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return INTPTR_TO_JLONG(&model.getVertexDataLayout());
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getIndexType
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    DataType indexType;
    TAKErr code = model.getIndexType(&indexType);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return indexType;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getIndex
  (JNIEnv *env, jclass clazz, jobject jpointer, jint index)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    std::size_t retval;
    TAKErr code = model.getIndex(&retval, index);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return -1;
    return retval;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getIndices
  (JNIEnv *env, jclass clazz, jobject jpointer, jobject ignored)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    const void *address = model.getIndices();
    if(!address)
        return NULL;
    TAKErr code;
    std::size_t indexCount = model.getNumIndices();
    DataType indexDataType;
    code = model.getIndexType(&indexDataType);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    indexCount *= getDataTypeSize(indexDataType);
    return env->NewDirectByteBuffer(const_cast<void *>(address), indexCount);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getIndexOffset
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.getIndexOffset();
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getVertices
  (JNIEnv *env, jclass clazz, jobject jpointer, jint cattr)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    std::size_t count = model.getNumVertices();
    const VertexDataLayout layout = model.getVertexDataLayout();
#define CASE_TEVA(teva, vao) \
    if(cattr == teva) { \
        count *= layout.vao.stride; \
        count += layout.vao.offset; \
    }

    CASE_TEVA(TEVA_Position, position)
    else CASE_TEVA(TEVA_TexCoord0, texCoord0)
    else CASE_TEVA(TEVA_TexCoord1, texCoord1)
    else CASE_TEVA(TEVA_TexCoord2, texCoord2)
    else CASE_TEVA(TEVA_TexCoord3, texCoord3)
    else CASE_TEVA(TEVA_TexCoord4, texCoord4)
    else CASE_TEVA(TEVA_TexCoord5, texCoord5)
    else CASE_TEVA(TEVA_TexCoord6, texCoord6)
    else CASE_TEVA(TEVA_TexCoord7, texCoord7)
    else CASE_TEVA(TEVA_Normal, normal)
    else CASE_TEVA(TEVA_Color, color)
#undef CASE_TEVA

    const void *address;
    TAKErr code = model.getVertices(&address, cattr);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return env->NewDirectByteBuffer(const_cast<void *>(address), count);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getFaceWindingOrder
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.getFaceWindingOrder();
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getDrawMode
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    return model.getDrawMode();
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getAABB
  (JNIEnv *env, jclass clazz, jobject jpointer, jdoubleArray jarr)
{
    const Mesh &model = *Pointer_get<Mesh>(env, jpointer);
    Envelope2 aabb = model.getAABB();

    jdouble *arr = env->GetDoubleArrayElements(jarr, NULL);
    arr[0] = aabb.minX;
    arr[1] = aabb.minY;
    arr[2] = aabb.minZ;
    arr[3] = aabb.maxX;
    arr[4] = aabb.maxY;
    arr[5] = aabb.maxZ;
    env->ReleaseDoubleArrayElements(jarr, arr, 0);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_NativeMesh_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<Mesh>(env, jpointer);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEWO_1Clockwise
  (JNIEnv *env, jclass clazz)
{
    return TEWO_Clockwise;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEWO_1CounterClockwise
  (JNIEnv *env, jclass clazz)
{
    return TEWO_CounterClockwise;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEWO_1Undefined
  (JNIEnv *env, jclass clazz)
{
    return TEWO_Undefined;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEDM_1Points
  (JNIEnv *env, jclass clazz)
{
    return TEDM_Points;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEDM_1Triangles
  (JNIEnv *env, jclass clazz)
{
    return TEDM_Triangles;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEDM_1TriangleStrip
  (JNIEnv *env, jclass clazz)
{
    return TEDM_TriangleStrip;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEDM_1Lines
  (JNIEnv *env, jclass clazz)
{
    return TEDM_Lines;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEDM_1LineStrip
  (JNIEnv *env, jclass clazz)
{
    return TEDM_LineStrip;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1Position
  (JNIEnv *env, jclass clazz)
{
    return TEVA_Position;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord0
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord0;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord1
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord1;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord2
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord2;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord3
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord3;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord4
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord4;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord5
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord5;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord6
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord6;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1TexCoord7
  (JNIEnv *env, jclass clazz)
{
    return TEVA_TexCoord7;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1Normal
  (JNIEnv *env, jclass clazz)
{
    return TEVA_Normal;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_NativeMesh_getTEVA_1Color
  (JNIEnv *env, jclass clazz)
{
    return TEVA_Color;
}
