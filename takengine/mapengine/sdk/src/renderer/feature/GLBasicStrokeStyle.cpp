#include "renderer/feature/GLBasicStrokeStyle.h"
#include "renderer/GLRenderBatch.h"
#include "feature/GeometryCollection.h"
#include "feature/Style.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/feature/GLLineString.h"
#include "renderer/feature/GLPolygon.h"
#include "renderer/feature/GLGeometryCollectionStyle.h"
#include "renderer/map/GLMapView.h"
#include "feature/LineString.h"
#include "feature/Polygon.h"
#include "renderer/GL.h"

#include "renderer/RendererUtils.h"

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;
using namespace atakmap::renderer::map;

GLBasicStrokeStyle::GLBasicStrokeStyle(const BasicStrokeStyle *style)
: GLStrokeStyle(style),
  strokeWidth(style->getStrokeWidth()),
  strokeColorR(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::RED) / 255.0f),
  strokeColorG(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::GREEN) / 255.0f),
  strokeColorB(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::BLUE) / 255.0f),
  strokeColorA(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::ALPHA) / 255.0f) { }

GLBasicStrokeStyle::~GLBasicStrokeStyle() { }

void GLBasicStrokeStyle::drawImpl(const GLMapView *view, const float *vertices, int size, int count) {
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glColor4f(strokeColorR,
                                  strokeColorG,
                                  strokeColorB,
                                  strokeColorA);
    glLineWidth(strokeWidth);

    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, vertices);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, count);

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
}

void GLBasicStrokeStyle::batchImpl(const GLMapView *view, GLRenderBatch *batch, const float *vertices, size_t count) {
    batch->addLineStrip(vertices,
                        count,
                        strokeWidth,
                        strokeColorR,
                        strokeColorG,
                        strokeColorB,
                        strokeColorA);
}

StyleRenderContext *GLBasicStrokeStyle::createRenderContext(const GLMapView *view, GLGeometry *geom) {
    return nullptr;
}

void GLBasicStrokeStyle::releaseRenderContext(StyleRenderContext *ctx) { }



namespace {


    class LineStringStyle : public GLBasicStrokeStyle {
    public:
        LineStringStyle(const BasicStrokeStyle *style) : GLBasicStrokeStyle(style) { }

        
        virtual void draw(const GLMapView *view, GLGeometry *g, StyleRenderContext *ctx) override {
            GLLineString *geometry = (GLLineString *)g;

            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);
            const float *verts = geometry->getVertices(view, GLGeometry::VERTICES_PROJECTED);
            drawImpl(view, verts, 3, geometry->getNumVertices());
            GLES20FixedPipeline::getInstance()->glPopMatrix();
        }

        virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *geometry, StyleRenderContext *ctx) override {
            // XXX - really need to be able to specify software transform to
            //       batch
            GLLineString *glLS = (GLLineString *)geometry;
            batchImpl(view, batch, glLS->getVertices(view, GLGeometry::VERTICES_PIXEL), glLS->getNumVertices());
        }

        virtual bool isBatchable(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override {
            return true;
        }
    };

    class PolygonStyle : public GLBasicStrokeStyle {
    public:
        PolygonStyle(const BasicStrokeStyle *style) : GLBasicStrokeStyle(style) { }

        virtual void draw(const GLMapView *view, GLGeometry *g, StyleRenderContext *ctx) override {
            GLPolygon *geometry = (GLPolygon *)g;

            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);
            int numRings = geometry->getNumInteriorRings() + 1;
            for (int i = 0; i < numRings; i++)
                drawImpl(view, geometry->getVertices(view, GLGeometry::VERTICES_PROJECTED, i).first, 3, geometry->getNumVertices(i));
            GLES20FixedPipeline::getInstance()->glPopMatrix();
        }

        virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *g, StyleRenderContext *ctx) override {
            GLPolygon *geometry = (GLPolygon *)g;

            int numRings = geometry->getNumInteriorRings() + 1;
            int numInt = geometry->getNumInteriorRings();
            for (int i = 0; i < numRings; i++) {
                std::pair<float *, size_t> verts = geometry->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
                batchImpl(view, batch, verts.first, verts.second);
            }
        }

        virtual bool isBatchable(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override {
            return true;
        }
    };

}

GLStyleSpi *GLBasicStrokeStyle::getSpi() {
    static Spi spi;
    return &spi;
}

GLBasicStrokeStyle::Spi::~Spi() { }

GLStyle *GLBasicStrokeStyle::Spi::create(const atakmap::renderer::feature::GLStyleSpiArg &object) {

    const Style * const s = object.style;
    const Geometry * const g = object.geometry;
    if (s == nullptr || g == nullptr)
        return nullptr;
    const BasicStrokeStyle * const styleImpl = dynamic_cast<const BasicStrokeStyle *>(s);
    if (styleImpl == nullptr)
        return nullptr;

    if (dynamic_cast<const atakmap::feature::LineString *>(g))
        return new LineStringStyle(styleImpl);
    else if (dynamic_cast<const atakmap::feature::Polygon *>(g))
        return new PolygonStyle(styleImpl);
    else if (dynamic_cast<const atakmap::feature::GeometryCollection *>(g))
        return new GLGeometryCollectionStyle(s, getSpi());
    return nullptr;
}
