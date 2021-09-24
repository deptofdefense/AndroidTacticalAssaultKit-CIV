#include "model/MeshTransformer.h"

#include <algorithm>

#include "core/GeoPoint2.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "math/Point2.h"
#include "model/MeshBuilder.h"
#include "util/Memory.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    bool isScaleTranslateOnly(const Matrix2 &mx) NOTHROWS;
}

MeshTransformOptions::MeshTransformOptions() NOTHROWS :
    srid(-1),
    localFrame(nullptr, nullptr),
    layout(nullptr, nullptr)
{}
MeshTransformOptions::MeshTransformOptions(const int srid_) NOTHROWS :
    srid(srid_),
    localFrame(nullptr, nullptr),
    layout(nullptr, nullptr)
{}
MeshTransformOptions::MeshTransformOptions(const int srid_, const Matrix2 &localFrame_) NOTHROWS :
    srid(srid_),
    localFrame(new Matrix2(localFrame_), Memory_deleter_const<Matrix2>),
    layout(nullptr, nullptr)
{}
MeshTransformOptions::MeshTransformOptions(const int srid_, const Matrix2 &localFrame_, const VertexDataLayout &layout_) :
    srid(-1),
    localFrame(new Matrix2(localFrame_), Memory_deleter_const<Matrix2>),
    layout(new VertexDataLayout(layout_), Memory_deleter_const<VertexDataLayout>)
{}
MeshTransformOptions::MeshTransformOptions(const int srid_, const VertexDataLayout &layout_) NOTHROWS :
    srid(srid_),
    localFrame(nullptr, nullptr),
    layout(new VertexDataLayout(layout_), Memory_deleter_const<VertexDataLayout>)
{}
MeshTransformOptions::MeshTransformOptions(const VertexDataLayout &layout_) NOTHROWS :
    srid(-1),
    localFrame(nullptr, nullptr),
    layout(new VertexDataLayout(layout_), Memory_deleter_const<VertexDataLayout>)
{}
MeshTransformOptions::MeshTransformOptions(const Matrix2 &localFrame_) NOTHROWS :
    srid(-1),
    localFrame(new Matrix2(localFrame_), Memory_deleter_const<Matrix2>),
    layout(nullptr, nullptr)
{}
MeshTransformOptions::MeshTransformOptions(const Matrix2 &localFrame_, const VertexDataLayout &layout_) :
    srid(-1),
    localFrame(new Matrix2(localFrame_), Memory_deleter_const<Matrix2>),
    layout(new VertexDataLayout(layout_), Memory_deleter_const<VertexDataLayout>)
{}
MeshTransformOptions::MeshTransformOptions(const MeshTransformOptions &other) NOTHROWS :
    srid(other.srid),
    localFrame(other.localFrame.get() ? new Matrix2(*other.localFrame) : nullptr, Memory_deleter_const<Matrix2>),
    layout(other.layout.get() ? new VertexDataLayout(*other.layout) : nullptr, Memory_deleter_const<VertexDataLayout>)
{}
MeshTransformOptions::~MeshTransformOptions() NOTHROWS
{}

TAKErr TAK::Engine::Model::Mesh_transform(MeshPtr &value, MeshTransformOptions *valueOpts, const Mesh &src, const MeshTransformOptions &srcOpts, const MeshTransformOptions &dstOpts, ProcessingCallback *callback) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!valueOpts)
        return TE_InvalidArg;

    if (ProcessingCallback_isCanceled(callback))
        return TE_Canceled;

    if (dstOpts.srid == -1)
        valueOpts->srid = srcOpts.srid;
    else
        valueOpts->srid = dstOpts.srid;

    Projection2Ptr srcProj(nullptr, nullptr);
    Projection2Ptr dstProj(nullptr, nullptr);
    if (srcOpts.srid != valueOpts->srid) {
        code = ProjectionFactory3_create(srcProj, srcOpts.srid);
        TE_CHECKRETURN_CODE(code);
        code = ProjectionFactory3_create(dstProj, valueOpts->srid);
        TE_CHECKRETURN_CODE(code);
    }

    // compute the local frame for the destination model
    Matrix2 invDstLocalFrame;
    if (!dstOpts.localFrame.get() && srcOpts.localFrame.get()) {
        // check to see if the
        if (isScaleTranslateOnly(*srcOpts.localFrame)) {
            // XXX - not sure if this will really work
            double scaleX, scaleY, scaleZ;
            code = srcOpts.localFrame->get(&scaleX, 0, 0);
            TE_CHECKRETURN_CODE(code);
            code = srcOpts.localFrame->get(&scaleY, 1, 1);
            TE_CHECKRETURN_CODE(code);
            code = srcOpts.localFrame->get(&scaleZ, 2, 2);
            TE_CHECKRETURN_CODE(code);

            double translateX, translateY, translateZ;
            code = srcOpts.localFrame->get(&translateX, 0, 3);
            TE_CHECKRETURN_CODE(code);
            code = srcOpts.localFrame->get(&translateY, 1, 3);
            TE_CHECKRETURN_CODE(code);
            code = srcOpts.localFrame->get(&translateZ, 2, 3);
            TE_CHECKRETURN_CODE(code);

            // transform translation into destination spatial reference
            if (srcOpts.srid != valueOpts->srid) {
                GeoPoint2 geo;
                Point2<double> translation(translateX, translateY, translateZ);
                code = srcProj->inverse(&geo, translation);
                TE_CHECKRETURN_CODE(code);
                code = dstProj->forward(&translation, geo);
                TE_CHECKRETURN_CODE(code);
                translateX = translation.x;
                translateY = translation.y;
                translateZ = translation.z;
            }

            valueOpts->localFrame = Matrix2Ptr(new Matrix2(), Memory_deleter_const<Matrix2>);
            valueOpts->localFrame->setToTranslate(translateX, translateY, translateZ);
            valueOpts->localFrame->scale(scaleX, scaleY, scaleZ);
        } else {
            // XXX - this will likely produce a bad local frame if we just adopt the source local frame. instead, try to compute
            valueOpts->localFrame = Matrix2Ptr(new Matrix2(), Memory_deleter_const<Matrix2>);
            valueOpts->localFrame->concatenate(*srcOpts.localFrame);

            const Envelope2 &srcAabb = src.getAABB();

            // compute the local center
            Point2<double> srcLocalCenter(
                (srcAabb.minX + srcAabb.maxX) / 2.0,
                (srcAabb.minY + srcAabb.maxY) / 2.0,
                (srcAabb.minZ + srcAabb.maxZ) / 2.0);

            Point2<double> scratch;
            // transform local center into destination SR
            code = srcOpts.localFrame->transform(&scratch, srcLocalCenter);
            TE_CHECKRETURN_CODE(code);
            if (srcOpts.srid != valueOpts->srid) {
                GeoPoint2 geo;
                code = srcProj->inverse(&geo, scratch);
                TE_CHECKRETURN_CODE(code);
                code = dstProj->forward(&scratch, geo);
                TE_CHECKRETURN_CODE(code);
            }

            // translate dst SR center back to src SR local center, then
            // run through original local frame transform
            valueOpts->localFrame->translate(srcLocalCenter.x - scratch.x,
                srcLocalCenter.y - scratch.y,
                srcLocalCenter.z - scratch.z);
        }
    } else if(dstOpts.localFrame.get()) {
        valueOpts->localFrame = Matrix2Ptr(new Matrix2(*dstOpts.localFrame), Memory_deleter_const<Matrix2>);
    }

    if (valueOpts->localFrame.get()) {
        code = valueOpts->localFrame->createInverse(&invDstLocalFrame);
        TE_CHECKRETURN_CODE(code);
    }

    const unsigned int srcAttrs = src.getVertexDataLayout().attributes;

    if (dstOpts.layout.get())
        valueOpts->layout = VertexDataLayoutPtr(new VertexDataLayout(*dstOpts.layout), Memory_deleter_const<VertexDataLayout>);
    else
        valueOpts->layout = VertexDataLayoutPtr(new VertexDataLayout(src.getVertexDataLayout()), Memory_deleter_const<VertexDataLayout>);

    if (ProcessingCallback_isCanceled(callback))
        return TE_Canceled;

    std::unique_ptr<MeshBuilder> dstData;
    if (src.isIndexed()) {
        DataType indexType;
        code = src.getIndexType(&indexType);
        TE_CHECKRETURN_CODE(code);
        dstData.reset(new MeshBuilder(src.getDrawMode(), *valueOpts->layout, indexType));
    } else {
        dstData.reset(new MeshBuilder(src.getDrawMode(), *valueOpts->layout));
    }
    for (std::size_t i = 0u; i < src.getNumMaterials(); i++) {
        Material mat;
        code = src.getMaterial(&mat, i);
        TE_CHECKBREAK_CODE(code);
        code = dstData->addMaterial(mat);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    code =dstData->setWindingOrder(src.getFaceWindingOrder());
    TE_CHECKRETURN_CODE(code);
    code = dstData->reserveVertices(src.getNumVertices());
    TE_CHECKRETURN_CODE(code);
    if (src.isIndexed()) {
        code = dstData->reserveIndices(src.getNumIndices());
        TE_CHECKRETURN_CODE(code);
    }

    size_t updateInterval = std::max(src.getNumVertices() / 200u, (size_t)1u);

    Point2<double> pos;
    array_ptr<float> uv(new float[16]);
    
    Point2<float> dir;
    unsigned int color = -1;
    for (std::size_t i = 0; i < src.getNumVertices(); i++) {
        if (ProcessingCallback_isCanceled(callback))
            return TE_Canceled;

        float *texuv = uv.get();

#define ATTRS_HAS_BITS(a, b) \
    (((a)&(b)) == (b))
        if (ATTRS_HAS_BITS(srcAttrs, TEVA_Position)) {
            code = src.getPosition(&pos, i);
            TE_CHECKBREAK_CODE(code);
        }
#define FETCH_UV(teva) \
    if (ATTRS_HAS_BITS(srcAttrs, teva)) { \
        Point2<float> uv##teva; \
        code = src.getTextureCoordinate(&uv##teva, teva, i); \
        TE_CHECKBREAK_CODE(code); \
        *texuv++ = uv##teva.x; \
        *texuv++ = uv##teva.y; \
    }
        FETCH_UV(TEVA_TexCoord0);
        FETCH_UV(TEVA_TexCoord1);
        FETCH_UV(TEVA_TexCoord2);
        FETCH_UV(TEVA_TexCoord3);
        FETCH_UV(TEVA_TexCoord4);
        FETCH_UV(TEVA_TexCoord5);
        FETCH_UV(TEVA_TexCoord6);
        FETCH_UV(TEVA_TexCoord7);
#undef FETCH_UV

        if (ATTRS_HAS_BITS(srcAttrs, TEVA_Normal)) {
            code = src.getNormal(&dir, i);
            TE_CHECKBREAK_CODE(code);
        }
        if (ATTRS_HAS_BITS(srcAttrs, TEVA_Color)) {
            code = src.getColor(&color, i);
            TE_CHECKBREAK_CODE(code);
        }
#undef ATTRS_HAS_BITS
        // transform from source local frame
        if (srcOpts.localFrame.get()) {
            code = srcOpts.localFrame->transform(&pos, pos);
            TE_CHECKBREAK_CODE(code);
        }
        // reproject if necessary
        if (srcOpts.srid != valueOpts->srid) {
            GeoPoint2 geo;
            code = srcProj->inverse(&geo, pos);
            TE_CHECKBREAK_CODE(code);
            code =dstProj->forward(&pos, geo);
            TE_CHECKBREAK_CODE(code);
        }
        // transform to destination local frame
        if (valueOpts->localFrame.get()) {
            invDstLocalFrame.transform(&pos, pos);
            TE_CHECKBREAK_CODE(code);
        }

        // add the vertex
        code = dstData->addVertex(pos.x, pos.y, pos.z,
            uv.get(),
            (float)dir.x, (float)dir.y, (float)dir.z,
            (float)((color>>16)&0xFF) / (float)255.0,
            (float)((color>>8)&0xFF) / (float)255.0,
            (float)(color&0xFF) / (float)255.0,
            (float)((color>>24)&0xFF) / (float)255.0);
        TE_CHECKBREAK_CODE(code);

        if ((i % updateInterval) == 0 && callback && callback->progress) {
            code = callback->progress(callback->opaque, (unsigned int)(((double)i / (double)src.getNumVertices()) * 100.0), 100);
            if (code == TE_Done)
                callback = nullptr;
            code = TE_Ok;
        }
    }
    TE_CHECKRETURN_CODE(code);
    if (src.isIndexed()) {
        const size_t numIndices = src.getNumIndices();
        for (std::size_t i = 0; i < numIndices; i++) {
            std::size_t idx;
            code = src.getIndex(&idx, i);
            TE_CHECKBREAK_CODE(code);
            code = dstData->addIndex(idx);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (ProcessingCallback_isCanceled(callback))
        return TE_Canceled;

    code = dstData->build(value);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Model::Mesh_transform(Envelope2 *dstAABB, const Envelope2 &srcAABB, const MeshTransformOptions &srcInfo, const MeshTransformOptions &dstInfo) NOTHROWS
{
    TAKErr code(TE_Ok);

    Point2<double> pts[8];
    pts[0] = Point2<double>(srcAABB.minX, srcAABB.minY, srcAABB.minZ);
    pts[1] = Point2<double>(srcAABB.maxX, srcAABB.minY, srcAABB.minZ);
    pts[2] = Point2<double>(srcAABB.maxX, srcAABB.maxY, srcAABB.minZ);
    pts[3] = Point2<double>(srcAABB.minX, srcAABB.maxY, srcAABB.minZ);
    pts[4] = Point2<double>(srcAABB.minX, srcAABB.minY, srcAABB.maxZ);
    pts[5] = Point2<double>(srcAABB.maxX, srcAABB.minY, srcAABB.maxZ);
    pts[6] = Point2<double>(srcAABB.maxX, srcAABB.maxY, srcAABB.maxZ);
    pts[7] = Point2<double>(srcAABB.minX, srcAABB.maxY, srcAABB.maxZ);
    if(srcInfo.localFrame.get()) {
        for (std::size_t i = 0; i < 8u; i++) {
            code = srcInfo.localFrame->transform(&pts[i], pts[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }
    if(srcInfo.srid != dstInfo.srid) {
        Projection2Ptr srcProj(nullptr, nullptr);
        code = ProjectionFactory3_create(srcProj, srcInfo.srid);
        TE_CHECKRETURN_CODE(code);

        Projection2Ptr dstProj(nullptr, nullptr);
        code = ProjectionFactory3_create(dstProj, dstInfo.srid);
        TE_CHECKRETURN_CODE(code);

        for(std::size_t i = 0u; i < 8u; i++) {
            GeoPoint2 geo;
            code = srcProj->inverse(&geo, pts[i]);
            TE_CHECKBREAK_CODE(code);
            code = dstProj->forward(pts + i, geo);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }
    if(dstInfo.localFrame.get()) {
        Matrix2 dstLocalFrameInv;
        code = dstInfo.localFrame->createInverse(&dstLocalFrameInv);
        TE_CHECKRETURN_CODE(code);

        for (std::size_t i = 0u; i < 8u; i++) {
            code = dstLocalFrameInv.transform(pts + i, pts[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }

    dstAABB->minX = pts[0].x;
    dstAABB->minY = pts[0].y;
    dstAABB->minZ = pts[0].z;
    dstAABB->maxX = pts[0].x;
    dstAABB->maxY = pts[0].y;
    dstAABB->maxZ = pts[0].z;

    for(std::size_t i = 1u; i < 8u; i++) {
        if(pts[i].x < dstAABB->minX)
            dstAABB->minX = pts[i].x;
        else if(pts[i].x > dstAABB->maxX)
            dstAABB->maxX = pts[i].x;
        if(pts[i].y < dstAABB->minY)
            dstAABB->minY = pts[i].y;
        else if(pts[i].y > dstAABB->maxY)
            dstAABB->maxY = pts[i].y;
        if(pts[i].z < dstAABB->minZ)
            dstAABB->minZ = pts[i].z;
        else if(pts[i].z > dstAABB->maxZ)
            dstAABB->maxZ = pts[i].z;
    }

    return code;
}

namespace
{
    bool isScaleTranslateOnly(const Matrix2 &mx) NOTHROWS
    {
        double v01, v02, v10, v12, v20, v21, v30, v31, v32, v33;
        mx.get(&v01, 0, 1);
        mx.get(&v02, 0, 2);
        mx.get(&v10, 1, 0);
        mx.get(&v12, 1, 2);
        mx.get(&v20, 2, 0);
        mx.get(&v21, 2, 1);
        mx.get(&v30, 3, 0);
        mx.get(&v31, 3, 1);
        mx.get(&v32, 3, 2);
        mx.get(&v33, 3, 3);

        return v01 == 0.0 &&
               v02 == 0.0 &&
               v10 == 0.0 &&
               v12 == 0.0 &&
               v20 == 0.0 &&
               v21 == 0.0 &&
               v30 == 0.0 &&
               v31 == 0.0 &&
               v32 == 0.0 &&
               v33 == 1.0;
    }
}
