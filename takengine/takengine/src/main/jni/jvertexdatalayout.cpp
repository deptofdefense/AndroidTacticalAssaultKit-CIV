#include "jvertexdatalayout.h"

#include <model/VertexDataLayout.h>

#include "common.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getAttributes
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->attributes;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getPositionDataType
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->position.type;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getPositionOffset
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->position.offset;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getPositionStride
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->position.stride;
}

#define TEXCOORD_ATTR_ACCESS_FN_DEFN(i) \
    JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getTexCoord##i##DataType \
      (JNIEnv *env, jclass clazz, jlong pointer) \
    { \
        const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer); \
        return layout->texCoord##i.type; \
    } \
    JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getTexCoord##i##Offset \
      (JNIEnv *env, jclass clazz, jlong pointer) \
    { \
        const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer); \
        return layout->texCoord##i.offset; \
    } \
    JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getTexCoord##i##Stride \
      (JNIEnv *env, jclass clazz, jlong pointer) \
    { \
        const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer); \
        return layout->texCoord##i.stride; \
    }

TEXCOORD_ATTR_ACCESS_FN_DEFN(0)
TEXCOORD_ATTR_ACCESS_FN_DEFN(1)
TEXCOORD_ATTR_ACCESS_FN_DEFN(2)
TEXCOORD_ATTR_ACCESS_FN_DEFN(3)
TEXCOORD_ATTR_ACCESS_FN_DEFN(4)
TEXCOORD_ATTR_ACCESS_FN_DEFN(5)
TEXCOORD_ATTR_ACCESS_FN_DEFN(6)
TEXCOORD_ATTR_ACCESS_FN_DEFN(7)

#undef TEXCOORD_ATTR_ACCESS_FN_DEFN

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getNormalDataType
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->normal.type;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getNormalOffset
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->normal.offset;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getNormalStride
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->normal.stride;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getColorDataType
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->color.type;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getColorOffset
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->color.offset;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getColorStride
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->color.stride;
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_getInterleaved
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    const VertexDataLayout *layout = JLONG_TO_INTPTR(const VertexDataLayout, pointer);
    return layout->interleaved;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_model_VertexDataLayout_requiredInterleavedDataSize
  (JNIEnv *env, jclass clazz,
   jint attributes,
   jint posDataType, jint posOff, jint posStride,
   jint texCoordDataType, jint texCoordOff, jint texCoordStride,
   jint normalDataType, jint normalOff, jint normalStride,
   jint colorDataType, jint colorOff, jint colorStride,
   jboolean interleaved,
   jint numVertices)
{
    VertexDataLayout layout;
    layout.attributes = attributes;
    layout.position.type = (DataType)posDataType;
    layout.position.offset = posOff;
    layout.position.stride = posStride;
    layout.texCoord0.type = (DataType)texCoordDataType;
    layout.texCoord0.offset = texCoordOff;
    layout.texCoord0.stride = texCoordStride;
    layout.normal.type = (DataType)normalDataType;
    layout.normal.offset = normalOff;
    layout.normal.stride = normalStride;
    layout.color.type = (DataType)colorDataType;
    layout.color.offset = colorOff;
    layout.color.stride = colorStride;
    layout.interleaved = interleaved;

    std::size_t requiredSize;
    TAKErr code = VertexDataLayout_requiredInterleavedDataSize(&requiredSize, layout, numVertices);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return requiredSize;
}
