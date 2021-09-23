#include "jmodels.h"

#include <memory>

#include "renderer/GL.h"

#include <math/Matrix2.h>
#include <math/Mesh.h>
#include <math/Point2.h>
#include <math/Triangle.h>
#include <math/Ray2.h>
#include <model/Mesh.h>
#include <model/MeshTransformer.h>
#include <util/Memory.h>
#include <util/ProcessingCallback.h>

#include "common.h"
#include "ManagedModel.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

using namespace TAKEngine::Model::Impl;
using namespace TAKEngineJNI::Interop;

namespace
{
    double MathUtils_distance(const Point2<double> &a, const Point2<double> &b)
    {
        Point2<double> diff;
        if(Vector2_subtract(&diff, a, b) != TE_Ok)
            return NAN;
        double retval = NAN;
        Vector2_length(&retval, diff);
        return retval;
    }

    template<class V, class I>
    bool intersect(Point2<double> *result,
                   const int mode,
                   const std::size_t faceCount,
                   const uint8_t *modelBlob,
                   const std::size_t posStride,
                   const I *indices,
                   const double rox, const double roy, const double roz,
                   const double rdx, const double rdy, const double rdz,
                   const Matrix2 *localFrame) NOTHROWS;

    TAKErr MeshTransform_progress(void *opaque, const int current, const int max) NOTHROWS;
    TAKErr MeshTrasnform_error(void *opaque, const char *msg) NOTHROWS;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_Models_intersect__IIIJIIJDDDDDD_3DLcom_atakmap_math_PointD_2
  (JNIEnv *env,
   jclass clazz,
   jint mode,
   jint faceCount,
   jint verticesType,
   jlong jmodelBlob,
   jint posStride,
   jint indicesType,
   jlong jindicesPtr,
   jdouble rox, jdouble roy, jdouble roz,
   jdouble rdx, jdouble rdy, jdouble rdz,
   jdoubleArray jlocalFrameMx,
   jobject jresult)
{
    const uint8_t *modelBlob = JLONG_TO_INTPTR(const uint8_t, jmodelBlob);

    Matrix2 localFrame;
    Matrix2 *pLocalFrame = nullptr;
    if(jlocalFrameMx) {
        jdouble *mx = env->GetDoubleArrayElements(jlocalFrameMx, NULL);
        localFrame = Matrix2(mx[0], mx[1], mx[2], mx[3],
                             mx[4], mx[5], mx[6], mx[7],
                             mx[8], mx[9], mx[10], mx[11],
                             mx[12], mx[13], mx[14], mx[15]);
        pLocalFrame = &localFrame;
        env->ReleaseDoubleArrayElements(jlocalFrameMx, mx, JNI_ABORT);
    }

    Point2<double> result(100, 100, 100);
 
    switch(verticesType) {
#define IDX_CASE(itl, vt, it) \
    case itl : \
    if(!intersect<vt, it>(&result, mode, faceCount, modelBlob, posStride, JLONG_TO_INTPTR(it, jindicesPtr), rox, roy, roz, rdx, rdy, rdz, pLocalFrame)) return false; \
    break;

#define IDX_SWITCH(vt) \
    switch(indicesType) { \
        IDX_CASE(TEDT_UInt8, vt, const uint8_t) \
        IDX_CASE(TEDT_UInt16, vt, const uint16_t) \
        IDX_CASE(TEDT_UInt32, vt, const uint32_t) \
        default : return false; \
    }

        case TEDT_Int8 :    IDX_SWITCH(const int8_t); break;
        case TEDT_UInt8 :   IDX_SWITCH(const uint8_t); break;
        case TEDT_Int16 :   IDX_SWITCH(const int16_t); break;
        case TEDT_UInt16 :  IDX_SWITCH(const uint16_t); break;
        case TEDT_Int32 :   IDX_SWITCH(const int32_t); break;
        case TEDT_UInt32 :  IDX_SWITCH(const uint32_t); break;
        case TEDT_Float32 : IDX_SWITCH(const float); break;
        case TEDT_Float64 : IDX_SWITCH(const double); break;
        default : return false;
    }

    env->SetDoubleField(jresult, pointD_x, result.x);
    env->SetDoubleField(jresult, pointD_y, result.y);
    env->SetDoubleField(jresult, pointD_z, result.z);
    
    return true;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_Models_intersect__JDDDDDD_3DLcom_atakmap_math_PointD_2
  (JNIEnv *env, jclass clazz,
   jlong meshPtr,
   jdouble rox, jdouble roy, jdouble roz,
   jdouble rdx, jdouble rdy, jdouble rdz,
   jdoubleArray jlocalFrameMx,
   jobject jresult)
{
    const TAK::Engine::Model::Mesh *mesh = JLONG_TO_INTPTR(TAK::Engine::Model::Mesh, meshPtr);
    if(!mesh)
        return false;
    Matrix2 localFrame;
    Matrix2 *pLocalFrame = nullptr;
    if(jlocalFrameMx) {
        jdouble *mx = env->GetDoubleArrayElements(jlocalFrameMx, NULL);
        localFrame = Matrix2(mx[0], mx[1], mx[2], mx[3],
                             mx[4], mx[5], mx[6], mx[7],
                             mx[8], mx[9], mx[10], mx[11],
                             mx[12], mx[13], mx[14], mx[15]);
        pLocalFrame = &localFrame;
        env->ReleaseDoubleArrayElements(jlocalFrameMx, mx, JNI_ABORT);
    }
    TAK::Engine::Math::Mesh gmesh(std::move(TAK::Engine::Model::MeshPtr_const(mesh, Memory_leaker_const<TAK::Engine::Model::Mesh>)), pLocalFrame);
    Point2<double> result;
    if(!gmesh.intersect(&result, Ray2<double>(Point2<double>(rox, roy, roz), Vector4<double>(rdx, rdy, rdz))))
        return false;

    env->SetDoubleField(jresult, pointD_x, result.x);
    env->SetDoubleField(jresult, pointD_y, result.y);
    env->SetDoubleField(jresult, pointD_z, result.z);

    return true;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_Models_transform__Lcom_atakmap_interop_Pointer_2IIIIIIIIIIIIIZ
  (JNIEnv *env, jclass clazz, jobject jpointer,
   jint attributes,
   jint posDataType, jint posOff, jint posStride,
   jint texCoordDataType, jint texCoordOff, jint texCoordStride,
   jint normalDataType, jint normalOff, jint normalStride,
   jint colorDataType, jint colorOff, jint colorStride,
   jboolean interleaved)
{
    const auto &src = *Pointer_get<TAK::Engine::Model::Mesh>(env, jpointer);
    VertexDataLayout dstLayout;
    dstLayout.attributes = attributes;
    dstLayout.position.type = (DataType)posDataType;
    dstLayout.position.offset = posOff;
    dstLayout.position.stride = posStride;
    dstLayout.texCoord0.type = (DataType)texCoordDataType;
    dstLayout.texCoord0.offset = texCoordOff;
    dstLayout.texCoord0.stride = texCoordStride;
    dstLayout.normal.type = (DataType)normalDataType;
    dstLayout.normal.offset = normalOff;
    dstLayout.normal.stride = normalStride;
    dstLayout.color.type = (DataType)colorDataType;
    dstLayout.color.offset = colorOff;
    dstLayout.color.stride = colorStride;
    dstLayout.interleaved = interleaved;

    MeshPtr dst(NULL, NULL);
    MeshTransformOptions dstOut;
    TAKErr code = Mesh_transform(dst, &dstOut, src, MeshTransformOptions(src.getVertexDataLayout()), MeshTransformOptions(dstLayout), nullptr);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(dst));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_Models_transform__Lcom_atakmap_interop_Pointer_2I_3DIZ_3DLcom_atakmap_interop_ProgressCallback_2
  (JNIEnv *env, jclass clazz, jobject jpointer, jint srcSrid, jdoubleArray jsrcMx, jint dstSrid, jboolean dstMxDefined, jdoubleArray jdstMx, jobject jcallback)
{
    const auto &src = *Pointer_get<TAK::Engine::Model::Mesh>(env, jpointer);
    std::unique_ptr<MeshTransformOptions> srcOpts;
    if(jsrcMx) {
        jdouble *mx = env->GetDoubleArrayElements(jsrcMx, NULL);
        srcOpts.reset(new MeshTransformOptions(srcSrid,
                                               Matrix2(mx[0], mx[1], mx[2], mx[3],
                                                       mx[4], mx[5], mx[6], mx[7],
                                                       mx[8], mx[9], mx[10], mx[11],
                                                       mx[12], mx[13], mx[14], mx[15])));
        env->ReleaseDoubleArrayElements(jsrcMx, mx, JNI_ABORT);
    } else {
        srcOpts.reset(new MeshTransformOptions(srcSrid));
    }
    std::unique_ptr<MeshTransformOptions> dstInOpts;
    if(dstMxDefined) {
        jdouble *mx = env->GetDoubleArrayElements(jdstMx, NULL);
        dstInOpts.reset(new MeshTransformOptions(dstSrid,
                                                 Matrix2(mx[0], mx[1], mx[2], mx[3],
                                                         mx[4], mx[5], mx[6], mx[7],
                                                         mx[8], mx[9], mx[10], mx[11],
                                                         mx[12], mx[13], mx[14], mx[15])));
        env->ReleaseDoubleArrayElements(jdstMx, mx, JNI_ABORT);
    } else {
        dstInOpts.reset(new MeshTransformOptions(dstSrid));
    }
    std::unique_ptr<ProcessingCallback> callback;
    if(jcallback) {
        callback.reset(new ProcessingCallback());
        callback->opaque = jcallback;
        callback->cancelToken = nullptr;
        callback->progress = MeshTransform_progress;
        callback->error = MeshTrasnform_error;
    }
    MeshPtr dst(NULL, NULL);
    MeshTransformOptions dstOpts;
    TAKErr code = Mesh_transform(dst, &dstOpts, src, *srcOpts, *dstInOpts, callback.get());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(jdstMx && dstOpts.localFrame.get()) {
        jdouble *dstMx = env->GetDoubleArrayElements(jdstMx, NULL);
        dstOpts.localFrame->get(dstMx, Matrix2::ROW_MAJOR);
        env->ReleaseDoubleArrayElements(jdstMx, dstMx, 0);
    }

    return NewPointer(env, std::move(dst));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_model_Models_adapt
  (JNIEnv *env, jclass clazz,
   jobject jmodel,
   jstring jtextureUri,
   jint numVertices,
   jint numFaces,
   jboolean isIndexed,
   jint indexType,
   jint numIndices,
   jlong indicesPtr,
   jint indexOffset,
   jlong positionsPtr,
   jlong texCoordsPtr,
   jlong normalsPtr,
   jlong colorsPtr,
   jint windingOrder,
   jint drawMode,
   jdouble aabbMinX, jdouble aabbMinY, jdouble aabbMinZ, jdouble aabbMaxX, jdouble aabbMaxY, jdouble aabbMaxZ,
   jint attributes,
   jint posDataType, jint posOff, jint posStride,
   jint texCoordDataType, jint texCoordOff, jint texCoordStride,
   jint normalDataType, jint normalOff, jint normalStride,
   jint colorDataType, jint colorOff, jint colorStride,
   jboolean interleaved)
{
    TAKErr code(TE_Ok);

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

    Envelope2 aabb(aabbMinX, aabbMinY, aabbMinZ, aabbMaxX, aabbMaxY, aabbMaxZ);

    TAK::Engine::Port::String textureUri;
    code = JNIStringUTF_get(textureUri, *env, jtextureUri);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    MeshPtr retval(NULL, NULL);
    if(isIndexed) {
        retval = MeshPtr(new ManagedModel(env,
                                           jmodel,
                                           textureUri,
                                           numVertices,
                                           numFaces,
                                           JLONG_TO_INTPTR(const void, positionsPtr),
                                           JLONG_TO_INTPTR(const void, texCoordsPtr),
                                           JLONG_TO_INTPTR(const void, normalsPtr),
                                           JLONG_TO_INTPTR(const void, colorsPtr),
                                           (WindingOrder)windingOrder,
                                           (DrawMode)drawMode,
                                           aabb,
                                           layout,
                                           (DataType)indexType,
                                           numIndices,
                                           JLONG_TO_INTPTR(const void, indicesPtr),
                                           indexOffset),
                          Memory_deleter_const<TAK::Engine::Model::Mesh, ManagedModel>);
    } else {
        retval = MeshPtr(new ManagedModel(env,
                                           jmodel,
                                           textureUri,
                                           numVertices,
                                           numFaces,
                                           JLONG_TO_INTPTR(const void, positionsPtr),
                                           JLONG_TO_INTPTR(const void, texCoordsPtr),
                                           JLONG_TO_INTPTR(const void, normalsPtr),
                                           JLONG_TO_INTPTR(const void, colorsPtr),
                                           (WindingOrder)windingOrder,
                                           (DrawMode)drawMode,
                                           aabb,
                                           layout),
                          Memory_deleter_const<TAK::Engine::Model::Mesh, ManagedModel>);
    }

    return NewPointer(env, std::move(retval));
}

namespace
{
    template<class V, class I>
    bool intersect(Point2<double> *result,
                   const int mode,
                   const std::size_t faceCount,
                   const uint8_t *modelBlob,
                   const std::size_t posStride,
                   const I *indices,
                   const double rox, const double roy, const double roz,
                   const double rdx, const double rdy, const double rdz,
                   const Matrix2 *localFrame) NOTHROWS {

        Ray2<double> ray(Point2<double>(rox, roy, roz), Vector4<double>(rdx, rdy, rdz));
        Point2<double> *isect = NULL;
        double isectDist;

        std::size_t s;
        switch(mode) {
            case TEDM_Triangles :
                s = 3u;
                break;
            case TEDM_TriangleStrip :
                s = 1u;
                break;
            default :
                return false;
        }

        for(std::size_t face = 0; face < faceCount; face++) {
            std::size_t aidx = face*s;
            std::size_t bidx = face*s + 1;
            std::size_t cidx = face*s + 2;

            if(indices) {
                aidx = indices[aidx];
                bidx = indices[bidx];
                cidx = indices[cidx];

                // skip degenerates
                if((aidx == bidx) || (aidx == cidx) || (bidx == cidx))
                    continue;
            }

            V *position;

#define VERTEX(voff) \
    reinterpret_cast<const V *>(modelBlob + (voff))

            position = VERTEX(aidx*posStride);
            Point2<double> a(position[0], position[1], position[2]);
        
            position = VERTEX(bidx*posStride);
            Point2<double> b(position[0], position[1], position[2]);

            position = VERTEX(cidx*posStride);
            Point2<double> c(position[0], position[1], position[2]);

            if(localFrame) {
                localFrame->transform(&a, a);
                localFrame->transform(&b, b);
                localFrame->transform(&c, c);
            }
    
            Point2<double> p;
            int code = Triangle_intersect(&p,
                                a.x, a.y, a.z,
                                b.x, b.y, b.z,
                                c.x, c.y, c.z,
                                ray);
            if(code != TE_Ok)
                continue;

            if(!isect || MathUtils_distance(ray.origin, p) < isectDist) {
                isect = result;
                isect->x = p.x;
                isect->y = p.y;
                isect->z = p.z;
                isectDist = MathUtils_distance(ray.origin, *result);
            }
        }

        return !!isect;
    }

    TAKErr MeshTransform_progress(void *opaque, const int current, const int max) NOTHROWS
    {
        TAKErr code = ProgressCallback_dispatchProgress(static_cast<jobject>(opaque), (current < 0 || max<= 0) ? -1 : (int)(((double)current/(double)max)*100));
        if(code != TE_Ok)
            code = TE_Done;
        return code;
    }
    TAKErr MeshTrasnform_error(void *opaque, const char *msg) NOTHROWS
    {
        TAKErr code = ProgressCallback_dispatchError(static_cast<jobject>(opaque), msg);
        if(code != TE_Ok)
            code = TE_Done;
        return code;
    }
}

