
#include "renderer/GL.h"

#include "feature/Style.h"
#include "feature/Polygon.h"

#include "renderer/GLBackground.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLTriangulate.h"
#include "renderer/GLTriangulate2.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "renderer/feature/GLGeometry.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;

#define TRIANGULATION_ENABLED 1
#define BATCH_ENABLED 1

GLBatchPolygon3::GLBatchPolygon3(TAK::Engine::Core::RenderContext &surface) NOTHROWS
    : GLBatchLineString3(surface, 2),
    fillColorR(0),
    fillColorG(0),
    fillColorB(0),
    fillColorA(0),
    fillColor(0),
    polyRenderMode(GLTriangulate::STENCIL),
    indicesLength(0),
    numIndices(0)
{}

TAKErr GLBatchPolygon3::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchPolygon3::setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS
{    
    using namespace atakmap::feature;

    const Style *style = value.get();
    this->fillColor = 0;
    
    bool defaultStroking = true;
    stroke.clear();

    if (style) {
        if (style->getClass() == TESC_CompositeStyle)
        {
            style = static_cast<const CompositeStyle&>(*value).findStyle<const atakmap::feature::BasicFillStyle>().get();
        }
        if (style && style->getClass() == TESC_BasicFillStyle)
        {
            this->fillColor = static_cast<const BasicFillStyle &>(*style).getColor();
            defaultStroking = false;
        }

        this->fillColorR = ((this->fillColor>>16)&0xFF) / (float)255;
        this->fillColorG = ((this->fillColor>>8)&0xFF) / (float)255;
        this->fillColorB = (this->fillColor&0xFF) / (float)255;
        this->fillColorA = ((this->fillColor>>24)&0xFF) / (float)255;

        // if default stroking, let the public interface of `GLBatchLineString3` do its thing,
        // else call `pushStyle` which will not install a default if no usable stroking styles
        // are found
        if (defaultStroking)
            GLBatchLineString3::setStyle(value);
        else
            GLBatchLineString3::pushStyle(*value);
    } else {
        // default stroking
        stroke.push_back(Stroke());
    }

    return TE_Ok;
}

TAKErr GLBatchPolygon3::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return GLBatchLineString3::setGeometryImpl(std::move(blob), type, lod_val);
}

TAKErr GLBatchPolygon3::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
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
        GLBatchLineString3::setGeometryImpl(std::move(blob), type);

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

TAKErr GLBatchPolygon3::setGeometry(const atakmap::feature::Polygon &poly) NOTHROWS
{
    return GLBatchGeometry3::setGeometry(poly);
}

TAKErr GLBatchPolygon3::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);

    const auto &polygon = static_cast<const atakmap::feature::Polygon &>(geom);

    //const int numRings = ((polygon.getExteriorRing() != NULL) ? 1 : 0) + polygon->getInteriorRings()->Length;
    const auto numRings = 1 + (polygon.getInteriorRings().second - polygon.getInteriorRings().first);
    if (numRings == 0) {
        this->numPoints = 0;
    } else {
        GLBatchLineString3::setGeometryImpl(polygon.getExteriorRing());

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

TAKErr GLBatchPolygon3::draw(const GLMapView2 &view, const int render_pass, const int vertices_type) NOTHROWS
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
    if (!this->stroke.empty())
    {
        code = GLBatchLineString3::drawImpl(view, render_pass, v, size);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLBatchPolygon3::drawFillTriangulate(const GLMapView2 &view, const float *v, const int size) NOTHROWS
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

TAKErr GLBatchPolygon3::drawFillConvex(const GLMapView2 &view, const float *v, const int size) NOTHROWS
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

TAKErr GLBatchPolygon3::drawFillStencil(const GLMapView2 &view, const float *v, const int size) NOTHROWS
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

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, static_cast<int>(this->numPoints));

    glDisable(GL_STENCIL_TEST);
    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
}

TAKErr GLBatchPolygon3::batchImpl(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch, const int vertices_type, const float *v) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (this->fillColorA > 0)
    {
        const int size = (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2;
        switch (this->polyRenderMode)
        {
            case GLTriangulate::INDEXED :
                code = batch.batch(-1, GL_TRIANGLES, this->numPoints, size, 0, v, 0, nullptr, this->numIndices, this->indices.get(), this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::TRIANGLE_FAN :
                code = batch.batch(-1, GL_TRIANGLE_FAN, this->numPoints, size, 0, v, 0, nullptr, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::STENCIL :
                batch.end();
                this->drawFillStencil(view, v, size);
                batch.begin();
                break;
            default :
                return TE_IllegalState;
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (!this->stroke.empty())
    {
        GLBatchLineString3::batchImpl(view, render_pass, batch, vertices_type, v);
    }

    return TE_Ok;
}

