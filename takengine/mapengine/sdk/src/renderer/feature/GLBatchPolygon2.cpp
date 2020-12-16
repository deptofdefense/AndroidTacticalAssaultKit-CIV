
#include "renderer/GL.h"

#include "feature/Style.h"
#include "feature/Polygon.h"

#include "renderer/GLBackground.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLTriangulate.h"
#include "renderer/GLTriangulate2.h"
#include "renderer/feature/GLBatchPolygon2.h"
#include "renderer/feature/GLGeometry.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;

#define TRIANGULATION_ENABLED 1
#define BATCH_ENABLED 1

GLBatchPolygon2::GLBatchPolygon2(TAK::Engine::Core::RenderContext &surface) NOTHROWS
    : GLBatchLineString2(surface, 2),
    fillColorR(0),
    fillColorG(0),
    fillColorB(0),
    fillColorA(0),
    fillColor(0),
    polyRenderMode(GLTriangulate::STENCIL),
    indicesLength(0),
    numIndices(0)
{}

TAKErr GLBatchPolygon2::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchPolygon2::setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS
{
    
    const Style *style = value.get();
    if (const auto *compositeStyle = dynamic_cast<const atakmap::feature::CompositeStyle *>(style))
    {
        style = compositeStyle->findStyle<const atakmap::feature::BasicFillStyle>().get();
    }
    if (const auto *basicFill = dynamic_cast<const atakmap::feature::BasicFillStyle *>(style))
    {
        this->fillColor = basicFill->getColor();

        this->fillColorR = ((this->fillColor>>16)&0xFF) / (float)255;
        this->fillColorG = ((this->fillColor>>8)&0xFF) / (float)255;
        this->fillColorB = (this->fillColor&0xFF) / (float)255;
        this->fillColorA = ((this->fillColor>>24)&0xFF) / (float)255;
    }

    return GLBatchLineString2::setStyle(std::move(value));
}

TAKErr GLBatchPolygon2::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return GLBatchLineString2::setGeometryImpl(std::move(blob), type, lod_val);
}

TAKErr GLBatchPolygon2::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
{
    TAKErr code(TE_Ok);
    int numRings;
    code = blob->readInt(&numRings);
    TE_CHECKRETURN_CODE(code);

    if (numRings == 0)
    {
        this->numPoints = 0;
    }
    else
    {
        GLBatchLineString2::setGeometryImpl(std::move(blob), type);

        if (this->fillColorA > 0.0f && this->numPoints > 0)
        {
            size_t numVerts = this->numPoints - 1;
            if (!this->indices.get() || this->indicesLength < (numVerts - 2) * 3)
            {
                this->indices.reset(new uint16_t[(numVerts - 2) * 3]);
                this->indicesLength = ((numVerts - 2) * 3);
            }
#if TRIANGULATION_ENABLED
            code = GLTriangulate2_triangulate(this->indices.get(), &this->numIndices, this->points.get(), 2, numVerts);
            if (code == TE_Ok)
                this->polyRenderMode = GLTriangulate::INDEXED;
            else
                this->polyRenderMode = GLTriangulate::STENCIL;
#else
            this->polyRenderMode = GLTriangulate::STENCIL;
#endif
        }
    }

    return TE_Ok;
}

TAKErr GLBatchPolygon2::setGeometry(const atakmap::feature::Polygon &poly) NOTHROWS
{
    return GLBatchGeometry2::setGeometry(poly);
}

TAKErr GLBatchPolygon2::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);

    const auto &polygon = static_cast<const atakmap::feature::Polygon &>(geom);

    //const int numRings = ((polygon.getExteriorRing() != NULL) ? 1 : 0) + polygon->getInteriorRings()->Length;
    const auto numRings = 1 + (polygon.getInteriorRings().second - polygon.getInteriorRings().first);
    if (numRings == 0) {
        this->numPoints = 0;
    } else {
        GLBatchLineString2::setGeometryImpl(polygon.getExteriorRing());

        if (this->fillColorA > 0 && this->numPoints > 0) {
            size_t numVerts = this->numPoints - 1;
            if (this->indices.get() == nullptr || this->indicesLength < (numVerts - 2) * 3) {
                this->indices.reset(new uint16_t[(numVerts - 2) * 3]);
                this->indicesLength = ((numVerts - 2) * 3);
            }
#if TRIANGULATION_ENABLED
            code = GLTriangulate2_triangulate(this->indices.get(), &this->numIndices, this->points.get(), 2, numVerts);
            if (code == TE_Ok)
                this->polyRenderMode = GLTriangulate::INDEXED;
            else
                this->polyRenderMode = GLTriangulate::STENCIL;
#else
            this->polyRenderMode = GLTriangulate::STENCIL;
#endif
        }
    }

    return TE_Ok;
}

TAKErr GLBatchPolygon2::draw(const atakmap::renderer::map::GLMapView *view, const int vertices_type) NOTHROWS
{
    TAKErr code;

    const float *v;
    code = this->projectVertices(&v, view, vertices_type);
    TE_CHECKRETURN_CODE(code);
    if (v == nullptr)
    {
        return TE_Ok;
    }

    int size = (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2;

    if (this->fillColorA > 0.0f)
    {
        switch (this->polyRenderMode)
        {
            case GLTriangulate::INDEXED:
                code = this->drawFillTriangulate(view, v, size);
                TE_CHECKRETURN_CODE(code);
                break;
            case GLTriangulate::TRIANGLE_FAN:
                code = this->drawFillConvex(view, v, size);
                TE_CHECKRETURN_CODE(code);
                break;
            case GLTriangulate::STENCIL:
            default:
                code = this->drawFillStencil(view, v, size);
                TE_CHECKRETURN_CODE(code);
                break;
        }
    }
    if (this->strokeColorA > 0.0f)
    {
        code = GLBatchLineString2::drawImpl(view, v, size);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

bool GLBatchPolygon2::isBatchable(const atakmap::renderer::map::GLMapView *view)
{
#if BATCH_ENABLED
    return (this->fillColorA == 0.0f || this->polyRenderMode != GLTriangulate::STENCIL) && GLBatchLineString2::isBatchable(view);
#else
    return false;
#endif
}

TAKErr GLBatchPolygon2::drawFillTriangulate(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS
{
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float *vp = v;
    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, vp);

    GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);

    const uint16_t *ip = this->indices.get();
    GLES20FixedPipeline::getInstance()->glDrawElements(GL_TRIANGLES, static_cast<int>(this->numIndices), GL_UNSIGNED_SHORT, ip);

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
}

TAKErr GLBatchPolygon2::drawFillConvex(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS
{
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float *vp = v;
    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, vp);

    GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);

    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, static_cast<int>(this->numPoints));

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    
    return TE_Ok;
}

TAKErr GLBatchPolygon2::drawFillStencil(const atakmap::renderer::map::GLMapView *view, const float *v, const int size) NOTHROWS
{
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    const float *vp = v;
    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, vp);

    glClear(GL_STENCIL_BUFFER_BIT);
    glStencilMask(0xFFFFFFFF);
    glStencilFunc(GL_ALWAYS, 0x1, 0xFFFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
    glEnable(GL_STENCIL_TEST);

    glColorMask(false, false, false, false);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, static_cast<int>(this->numPoints));
    glColorMask(true, true, true, true);

    glStencilMask(0xFFFFFFFF);
    glStencilFunc(GL_EQUAL, 0x1, 0x1);
    glStencilOp(GL_KEEP, GL_KEEP, GL_ZERO);

    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, static_cast<int>(this->numPoints));

    glDisable(GL_STENCIL_TEST);

    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
}

TAKErr GLBatchPolygon2::batchImpl(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, const int vertices_type, const float *v) NOTHROWS
{
    if (this->fillColorA > 0)
    {
        switch (this->polyRenderMode)
        {
            case GLTriangulate::INDEXED :
                batch->addTriangles(v, this->numPoints*2, reinterpret_cast<short *>(this->indices.get()), this->numIndices, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::TRIANGLE_FAN :
                batch->addTriangleFan(v, this->numPoints*2, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::STENCIL :
                batch->end();
                this->drawFillStencil(view, v, (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2);
                batch->begin();
                break;
            default :
                return TE_IllegalState;
        }
    }

    if (this->strokeColorA > 0)
    {
        GLBatchLineString2::batchImpl(view, batch, vertices_type, v);
    }

    return TE_Ok;
}

