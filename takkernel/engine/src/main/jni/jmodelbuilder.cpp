#include "jmodelbuilder.h"

#include <map>

#include <feature/Envelope2.h>
#include <model/MeshBuilder.h>
#include <thread/Lock.h>
#include <thread/Mutex.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIIntArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct NioBufferReference
    {
        jobject ref;
        std::size_t count;
    };

    std::map<const void *, NioBufferReference> &bufferGlobalRefs() NOTHROWS;
    Mutex &mutex() NOTHROWS;
    void niobuffer_unref(const void *);
    void referenceNioBuffer(std::unique_ptr<const void, void(*)(const void *)> &value, JNIEnv &env, jobject buffer) NOTHROWS;

    typedef std::unique_ptr<MeshBuilder, void(*)(const MeshBuilder *)> MeshBuilderPtr;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_create__II
  (JNIEnv *env, jclass clazz, jint drawMode, jint attrs)
{
    MeshBuilderPtr retval(new MeshBuilder((DrawMode)drawMode, attrs), Memory_deleter_const<MeshBuilder>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_create__III
  (JNIEnv *env, jclass clazz, jint drawMode, jint attrs, jint indexType)
{
    MeshBuilderPtr retval(new MeshBuilder((DrawMode)drawMode, attrs, (DataType)indexType), Memory_deleter_const<MeshBuilder>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_create__IIIIIIIIIIIIIIZ
  (JNIEnv *env, jclass clazz,
   jint drawMode,
   jint attributes,
   jint posDataType, jint posOff, jint posStride,
   jint texCoordDataType, jint texCoordOff, jint texCoordStride,
   jint normalDataType, jint normalOff, jint normalStride,
   jint colorDataType, jint colorOff, jint colorStride,
   jboolean interleaved)
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

    MeshBuilderPtr retval(new MeshBuilder((DrawMode)drawMode, layout), Memory_deleter_const<MeshBuilder>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_create__IIIIIIIIIIIIIIZI
  (JNIEnv *env, jclass clazz,
     jint drawMode,
     jint attributes,
     jint posDataType, jint posOff, jint posStride,
     jint texCoordDataType, jint texCoordOff, jint texCoordStride,
     jint normalDataType, jint normalOff, jint normalStride,
     jint colorDataType, jint colorOff, jint colorStride,
     jboolean interleaved,
     jint indexType)
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

    MeshBuilderPtr retval(new MeshBuilder((DrawMode)drawMode, layout, (DataType)indexType), Memory_deleter_const<MeshBuilder>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_reserveVertices
  (JNIEnv *env, jclass clazz, jlong pointer, jint count)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    TAKErr code = builder.reserveVertices(count);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_reserveIndices
  (JNIEnv *env, jclass clazz, jlong pointer, jint count)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    TAKErr code = builder.reserveIndices(count);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_setWindingOrder
  (JNIEnv *env, jclass clazz, jlong pointer, jint windingOrder)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    TAKErr code = builder.setWindingOrder((WindingOrder)windingOrder);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_addMaterial
  (JNIEnv *env, jclass clazz, jlong pointer, jstring juri, jint color)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    JNIStringUTF uri(*env, juri);
    const char *curi = uri;
    Material material;
    material.color = color;
    material.propertyType = Material::TEPT_Diffuse;
    material.textureUri = curi;

    TAKErr code = builder.addMaterial(material);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_addVertex__JDDDFFFFFFFFF
  (JNIEnv *env, jclass clazz, jlong pointer,
   jdouble posx, jdouble posy, jdouble posz,
   jfloat texu, jfloat texv,
   jfloat nx, jfloat ny, jfloat nz,
   jfloat r, jfloat g, jfloat b, jfloat a)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    TAKErr code = builder.addVertex(posx, posy, posz, texu, texv, nx, ny, nz, r, g, b, a);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_addVertex__JDDD_3FFFFFFFF
  (JNIEnv *env, jclass clazz, jlong pointer,
   jdouble posx, jdouble posy, jdouble posz,
   jfloatArray jtexuv,
   jfloat nx, jfloat ny, jfloat nz,
   jfloat r, jfloat g, jfloat b, jfloat a)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    jfloat *texuv = env->GetFloatArrayElements(jtexuv, NULL);
    TAKErr code = builder.addVertex(posx, posy, posz, reinterpret_cast<const float *>(texuv), nx, ny, nz, r, g, b, a);
    env->ReleaseFloatArrayElements(jtexuv, texuv, JNI_ABORT);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_addIndex
  (JNIEnv *env, jclass clazz, jlong pointer, jint index)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    TAKErr code = builder.addIndex(index);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_build__J
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    MeshBuilder &builder = *JLONG_TO_INTPTR(MeshBuilder, pointer);
    MeshPtr retval(NULL, NULL);
    TAKErr code = builder.build(retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));

}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<MeshBuilder>(env, mpointer);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_build__IIIIII_3I_3I_3IIIIIIII_3I_3Ljava_lang_String_2_3IDDDDDDILjava_nio_Buffer_2
  (JNIEnv *env, jclass clazz,
   jint tedm,
   jint tewo,
   jint attrs,
   jint posType, jint posOff, jint posStride,
   jintArray jtexCoordTypes, jintArray jtexCoordOffs, jintArray jtexCoordStrides,
   jint normalType, jint normalOff, jint normalStride,
   jint colorType, jint colorOff, jint colorStride,
   jint numMaterials,
   jintArray jmatType, jobjectArray jmatTexUris, jintArray jmatColors,
   jdouble minX, jdouble minY, jdouble minZ,
   jdouble maxX, jdouble maxY, jdouble maxZ,
   jint numVertices, jobject jvertices)
{
    JNIIntArray texCoordTypes(*env, jtexCoordTypes, JNI_ABORT);
    JNIIntArray texCoordOffs(*env, jtexCoordOffs, JNI_ABORT);
    JNIIntArray texCoordStrides(*env, jtexCoordStrides, JNI_ABORT);

    jint *texCoordType = texCoordTypes;
    jint *texCoordOff = texCoordOffs;
    jint *texCoordStride = texCoordStrides;


    VertexDataLayout layout;
    layout.attributes = attrs;
    layout.position.type = (DataType)posType;
    layout.position.offset = posOff;
    layout.position.stride = posStride;

#define SET_TEXCOORD_ARRAY(teva, va) \
    if(attrs&teva) { \
        if(!texCoordType) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordOff) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordStride) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        layout.va.type = (DataType)*texCoordType++; \
        layout.va.offset = *texCoordOff++; \
        layout.va.stride = *texCoordStride++; \
    }

    SET_TEXCOORD_ARRAY(TEVA_TexCoord0, texCoord0);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord1, texCoord1);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord2, texCoord2);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord3, texCoord3);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord4, texCoord4);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord5, texCoord5);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord6, texCoord6);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord7, texCoord7);
#undef SET_TEXCOORD_ARRAY

    layout.normal.type = (DataType)normalType;
    layout.normal.offset = normalOff;
    layout.normal.stride = normalStride;
    layout.color.type = (DataType)colorType;
    layout.color.offset = colorOff;
    layout.color.stride = colorStride;
    layout.interleaved = true;

    array_ptr<Material> materials;
    if(numMaterials > 0) {
        materials.reset(new Material[numMaterials]);
        jint *matColors = env->GetIntArrayElements(jmatColors, NULL);
        for(std::size_t i = 0; i < numMaterials; i++) {
            materials[i].propertyType = Material::TEPT_Diffuse;
            materials[i].color = matColors[i];
            JNIStringUTF_get(materials[i].textureUri, *env, (jstring)env->GetObjectArrayElement(jmatTexUris, i));
        }
        env->ReleaseIntArrayElements(jmatColors, matColors, JNI_ABORT);
    }

    std::unique_ptr<const void, void(*)(const void *)> vertices(NULL, NULL);
    referenceNioBuffer(vertices, *env, jvertices);

    MeshPtr retval(NULL, NULL);
    TAKErr code = MeshBuilder_buildInterleavedMesh(retval,
                                    (DrawMode)tedm,
                                    (WindingOrder)tewo,
                                    layout,
                                    numMaterials,
                                    materials.get(),
                                    Envelope2(minX, minY, minZ, maxX, maxY, maxZ),
                                    numVertices,
                                    std::move(vertices));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_build__IIIIII_3I_3I_3IIIIIIII_3I_3Ljava_lang_String_2_3IDDDDDDILjava_nio_Buffer_2IILjava_nio_Buffer_2
  (JNIEnv *env, jclass clazz,
   jint tedm,
   jint tewo,
   jint attrs,
   jint posType, jint posOff, jint posStride,
   jintArray jtexCoordTypes, jintArray jtexCoordOffs, jintArray jtexCoordStrides,
   jint normalType, jint normalOff, jint normalStride,
   jint colorType, jint colorOff, jint colorStride,
   jint numMaterials,
   jintArray jmatType, jobjectArray jmatTexUris, jintArray jmatColors,
   jdouble minX, jdouble minY, jdouble minZ,
   jdouble maxX, jdouble maxY, jdouble maxZ,
   jint numVertices, jobject jvertices,
   jint numIndices, jint indexType, jobject jindices)
{
    JNIIntArray texCoordTypes(*env, jtexCoordTypes, JNI_ABORT);
    JNIIntArray texCoordOffs(*env, jtexCoordOffs, JNI_ABORT);
    JNIIntArray texCoordStrides(*env, jtexCoordStrides, JNI_ABORT);

    jint *texCoordType = texCoordTypes;
    jint *texCoordOff = texCoordOffs;
    jint *texCoordStride = texCoordStrides;

    VertexDataLayout layout;
    layout.attributes = attrs;
    layout.position.type = (DataType)posType;
    layout.position.offset = posOff;
    layout.position.stride = posStride;

#define SET_TEXCOORD_ARRAY(teva, va) \
    if(attrs&teva) { \
        if(!texCoordType) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordOff) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordStride) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        layout.va.type = (DataType)*texCoordType++; \
        layout.va.offset = *texCoordOff++; \
        layout.va.stride = *texCoordStride++; \
    }

    SET_TEXCOORD_ARRAY(TEVA_TexCoord0, texCoord0);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord1, texCoord1);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord2, texCoord2);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord3, texCoord3);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord4, texCoord4);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord5, texCoord5);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord6, texCoord6);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord7, texCoord7);
#undef SET_TEXCOORD_ARRAY

    layout.normal.type = (DataType)normalType;
    layout.normal.offset = normalOff;
    layout.normal.stride = normalStride;
    layout.color.type = (DataType)colorType;
    layout.color.offset = colorOff;
    layout.color.stride = colorStride;
    layout.interleaved = true;

    array_ptr<Material> materials;
    if(numMaterials > 0) {
        materials.reset(new Material[numMaterials]);
        jint *matColors = env->GetIntArrayElements(jmatColors, NULL);
        for(std::size_t i = 0; i < numMaterials; i++) {
            materials[i].propertyType = Material::TEPT_Diffuse;
            materials[i].color = matColors[i];
            JNIStringUTF_get(materials[i].textureUri, *env, (jstring)env->GetObjectArrayElement(jmatTexUris, i));
        }
        env->ReleaseIntArrayElements(jmatColors, matColors, JNI_ABORT);
    }

    std::unique_ptr<const void, void(*)(const void *)> vertices(NULL, NULL);
    referenceNioBuffer(vertices, *env, jvertices);

    std::unique_ptr<const void, void(*)(const void *)> indices(NULL, NULL);
    referenceNioBuffer(indices, *env, jindices);

    MeshPtr retval(NULL, NULL);
    TAKErr code = MeshBuilder_buildInterleavedMesh(retval,
                                    (DrawMode)tedm,
                                    (WindingOrder)tewo,
                                    layout,
                                    numMaterials,
                                    materials.get(),
                                    Envelope2(minX, minY, minZ, maxX, maxY, maxZ),
                                    numVertices,
                                    std::move(vertices),
                                    (DataType)indexType,
                                    numIndices,
                                    std::move(indices));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_build__IIIIII_3I_3I_3IIIIIIII_3I_3Ljava_lang_String_2_3IDDDDDDILjava_nio_Buffer_2_3Ljava_nio_Buffer_2Ljava_nio_Buffer_2Ljava_nio_Buffer_2
  (JNIEnv *env, jclass clazz,
   jint tedm,
   jint tewo,
   jint attrs,
   jint posType, jint posOff, jint posStride,
   jintArray jtexCoordTypes, jintArray jtexCoordOffs, jintArray jtexCoordStrides,
   jint normalType, jint normalOff, jint normalStride,
   jint colorType, jint colorOff, jint colorStride,
   jint numMaterials,
   jintArray jmatTypes, jobjectArray jmatTexUris, jintArray jmatColors,
   jdouble minX, jdouble minY, jdouble minZ,
   jdouble maxX, jdouble maxY, jdouble maxZ,
   jint numVertices,
   jobject jpositions,
   jobjectArray jtexCoordsArray,
   jobject jnormals,
   jobject jcolors)
{
    JNIIntArray texCoordTypes(*env, jtexCoordTypes, JNI_ABORT);
    JNIIntArray texCoordOffs(*env, jtexCoordOffs, JNI_ABORT);
    JNIIntArray texCoordStrides(*env, jtexCoordStrides, JNI_ABORT);

    jint *texCoordType = texCoordTypes;
    jint *texCoordOff = texCoordOffs;
    jint *texCoordStride = texCoordStrides;


    VertexDataLayout layout;
    layout.attributes = attrs;
    layout.position.type = (DataType)posType;
    layout.position.offset = posOff;
    layout.position.stride = posStride;

#define SET_TEXCOORD_ARRAY(teva, va) \
    if(attrs&teva) { \
        if(!texCoordType) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordOff) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordStride) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        layout.va.type = (DataType)*texCoordType++; \
        layout.va.offset = *texCoordOff++; \
        layout.va.stride = *texCoordStride++; \
    }

    SET_TEXCOORD_ARRAY(TEVA_TexCoord0, texCoord0);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord1, texCoord1);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord2, texCoord2);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord3, texCoord3);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord4, texCoord4);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord5, texCoord5);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord6, texCoord6);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord7, texCoord7);
#undef SET_TEXCOORD_ARRAY

    layout.normal.type = (DataType)normalType;
    layout.normal.offset = normalOff;
    layout.normal.stride = normalStride;
    layout.color.type = (DataType)colorType;
    layout.color.offset = colorOff;
    layout.color.stride = colorStride;
    layout.interleaved = true;

    array_ptr<Material> materials;
    if(numMaterials > 0) {
        materials.reset(new Material[numMaterials]);
        jint *matColors = env->GetIntArrayElements(jmatColors, NULL);
        for(std::size_t i = 0; i < numMaterials; i++) {
            materials[i].propertyType = Material::TEPT_Diffuse;
            materials[i].color = matColors[i];
            JNIStringUTF_get(materials[i].textureUri, *env, (jstring)env->GetObjectArrayElement(jmatTexUris, i));
        }
        env->ReleaseIntArrayElements(jmatColors, matColors, JNI_ABORT);
    }

    std::unique_ptr<const void, void(*)(const void *)> positions(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords0(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords1(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords2(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords3(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords4(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords5(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords6(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords7(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> normals(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> colors(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> indices(NULL, NULL);

    referenceNioBuffer(positions, *env, jpositions);

    std::size_t texCoordIdx = 0u;
    if(attrs&TEVA_TexCoord0)
        referenceNioBuffer(texCoords0, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord1)
        referenceNioBuffer(texCoords1, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord2)
        referenceNioBuffer(texCoords2, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord3)
        referenceNioBuffer(texCoords3, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord4)
        referenceNioBuffer(texCoords4, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord5)
        referenceNioBuffer(texCoords5, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord6)
        referenceNioBuffer(texCoords6, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord7)
        referenceNioBuffer(texCoords7, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));

    referenceNioBuffer(normals, *env, jnormals);
    referenceNioBuffer(colors, *env, jcolors);

    MeshPtr retval(NULL, NULL);
    TAKErr code = MeshBuilder_buildNonInterleavedMesh(
                                    retval,
                                    (DrawMode)tedm,
                                    (WindingOrder)tewo,
                                    layout,
                                    numMaterials,
                                    materials.get(),
                                    Envelope2(minX, minY, minZ, maxX, maxY, maxZ),
                                    numVertices,
                                    std::move(positions),
                                    std::move(texCoords0),
                                    std::move(texCoords1),
                                    std::move(texCoords2),
                                    std::move(texCoords3),
                                    std::move(texCoords4),
                                    std::move(texCoords5),
                                    std::move(texCoords6),
                                    std::move(texCoords7),
                                    std::move(normals),
                                    std::move(colors));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}


/*
 * Class:     com_atakmap_map_layer_model_ModelBuilder
 * Method:    build
 * Signature: (IIIIII[I[I[IIIIIIII[I[Ljava/lang/String;[IDDDDDDIJ[JJJIIJ)Lcom/atakmap/interop/Pointer;
 */
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_ModelBuilder_build__IIIIII_3I_3I_3IIIIIIII_3I_3Ljava_lang_String_2_3IDDDDDDILjava_nio_Buffer_2_3Ljava_nio_Buffer_2Ljava_nio_Buffer_2Ljava_nio_Buffer_2IILjava_nio_Buffer_2
  (JNIEnv *env, jclass clazz,
   jint tedm,
   jint tewo,
   jint attrs,
   jint posType, jint posOff, jint posStride,
   jintArray jtexCoordTypes, jintArray jtexCoordOffs, jintArray jtexCoordStrides,
   jint normalType, jint normalOff, jint normalStride,
   jint colorType, jint colorOff, jint colorStride,
   jint numMaterials,
   jintArray jmatTypes, jobjectArray jmatTexUris, jintArray jmatColors,
   jdouble minX, jdouble minY, jdouble minZ,
   jdouble maxX, jdouble maxY, jdouble maxZ,
   jint numVertices,
   jobject jpositions,
   jobjectArray jtexCoordsArray,
   jobject jnormals,
   jobject jcolors,
   jint indexType, jint numIndices, jobject jindices)
{
    JNIIntArray texCoordTypes(*env, jtexCoordTypes, JNI_ABORT);
    JNIIntArray texCoordOffs(*env, jtexCoordOffs, JNI_ABORT);
    JNIIntArray texCoordStrides(*env, jtexCoordStrides, JNI_ABORT);

    jint *texCoordType = texCoordTypes;
    jint *texCoordOff = texCoordOffs;
    jint *texCoordStride = texCoordStrides;


    VertexDataLayout layout;
    layout.attributes = attrs;
    layout.position.type = (DataType)posType;
    layout.position.offset = posOff;
    layout.position.stride = posStride;

#define SET_TEXCOORD_ARRAY(teva, va) \
    if(attrs&teva) { \
        if(!texCoordType) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordOff) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        if(!texCoordStride) { \
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
            return NULL; \
        } \
        layout.va.type = (DataType)*texCoordType++; \
        layout.va.offset = *texCoordOff++; \
        layout.va.stride = *texCoordStride++; \
    }

    SET_TEXCOORD_ARRAY(TEVA_TexCoord0, texCoord0);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord1, texCoord1);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord2, texCoord2);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord3, texCoord3);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord4, texCoord4);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord5, texCoord5);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord6, texCoord6);
    SET_TEXCOORD_ARRAY(TEVA_TexCoord7, texCoord7);
#undef SET_TEXCOORD_ARRAY

    layout.normal.type = (DataType)normalType;
    layout.normal.offset = normalOff;
    layout.normal.stride = normalStride;
    layout.color.type = (DataType)colorType;
    layout.color.offset = colorOff;
    layout.color.stride = colorStride;
    layout.interleaved = true;

    array_ptr<Material> materials;
    if(numMaterials > 0) {
        materials.reset(new Material[numMaterials]);
        jint *matColors = env->GetIntArrayElements(jmatColors, NULL);
        for(std::size_t i = 0; i < numMaterials; i++) {
            materials[i].propertyType = Material::TEPT_Diffuse;
            materials[i].color = matColors[i];
            JNIStringUTF_get(materials[i].textureUri, *env, (jstring)env->GetObjectArrayElement(jmatTexUris, i));
        }
        env->ReleaseIntArrayElements(jmatColors, matColors, JNI_ABORT);
    }

    std::unique_ptr<const void, void(*)(const void *)> positions(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords0(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords1(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords2(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords3(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords4(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords5(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords6(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> texCoords7(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> normals(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> colors(NULL, NULL);
    std::unique_ptr<const void, void(*)(const void *)> indices(NULL, NULL);

    referenceNioBuffer(positions, *env, jpositions);

    std::size_t texCoordIdx = 0u;
    if(attrs&TEVA_TexCoord0)
        referenceNioBuffer(texCoords0, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord1)
        referenceNioBuffer(texCoords1, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord2)
        referenceNioBuffer(texCoords2, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord3)
        referenceNioBuffer(texCoords3, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord4)
        referenceNioBuffer(texCoords4, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord5)
        referenceNioBuffer(texCoords5, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord6)
        referenceNioBuffer(texCoords6, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));
    if(attrs&TEVA_TexCoord7)
        referenceNioBuffer(texCoords7, *env, env->GetObjectArrayElement(jtexCoordsArray, texCoordIdx++));

    referenceNioBuffer(normals, *env, jnormals);
    referenceNioBuffer(colors, *env, jcolors);

    referenceNioBuffer(indices, *env, jindices);

    MeshPtr retval(NULL, NULL);
    TAKErr code = MeshBuilder_buildNonInterleavedMesh(
                                    retval,
                                    (DrawMode)tedm,
                                    (WindingOrder)tewo,
                                    layout,
                                    numMaterials,
                                    materials.get(),
                                    Envelope2(minX, minY, minZ, maxX, maxY, maxZ),
                                    numVertices,
                                    std::move(positions),
                                    std::move(texCoords0),
                                    std::move(texCoords1),
                                    std::move(texCoords2),
                                    std::move(texCoords3),
                                    std::move(texCoords4),
                                    std::move(texCoords5),
                                    std::move(texCoords6),
                                    std::move(texCoords7),
                                    std::move(normals),
                                    std::move(colors),
                                    (DataType)indexType,
                                    numIndices,
                                    std::move(indices));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}

namespace
{
    std::map<const void *, NioBufferReference> &bufferGlobalRefs() NOTHROWS
    {
        static std::map<const void *, NioBufferReference> refs;
        return refs;
    }
    Mutex &mutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }
    void niobuffer_unref(const void *opaque)
    {
        LockPtr lock(NULL, NULL);
        Lock_create(lock, mutex());

        std::map<const void *, NioBufferReference> &refs = bufferGlobalRefs();
        std::map<const void *, NioBufferReference>::iterator entry;
        entry = refs.find(opaque);
        if(entry == refs.end()) {
            Logger_log(TELL_Warning, "Failed to find java.nio.Buffer global ref when destructing Mesh vertex pointer 0x%x", opaque);
            return;
        }

        entry->second.count--;
        Logger_log(TELL_Debug, "Dereference java.nio.Buffer held by Mesh vertex pointer 0x%x, count=%u", opaque, entry->second.count);
        if(!entry->second.count) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(entry->second.ref);
            refs.erase(entry);
        }
    }
    void referenceNioBuffer(std::unique_ptr<const void, void(*)(const void *)> &value, JNIEnv &env, jobject buffer) NOTHROWS
    {
        const void *ptr = buffer ? env.GetDirectBufferAddress(buffer) : NULL;
        if(!ptr) {
            value.reset();
            return;
        }

        LockPtr lock(NULL, NULL);
        Lock_create(lock, mutex());

        std::map<const void *, NioBufferReference> &refs = bufferGlobalRefs();
        std::map<const void *, NioBufferReference>::iterator entry;
        entry = refs.find(ptr);
        if(entry == refs.end()) {
            NioBufferReference ref;
            ref.ref = env.NewGlobalRef(buffer);
            ref.count = 1u;
            refs[ptr] = ref;
        } else {
            entry->second.count++;
        }

        value = std::unique_ptr<const void, void(*)(const void *)>(ptr, niobuffer_unref);
    }
}
