
#include "math/Point.h"
#include "renderer/feature/GLBasicPointStyle.h"
#include "feature/GeometryCollection.h"
#include "feature/Style.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/RendererUtils.h"
#include "renderer/feature/GLLineString.h"
#include "renderer/feature/GLPoint.h"
#include "renderer/feature/GLPolygon.h"
#include "renderer/feature/GLGeometryCollectionStyle.h"
#include "renderer/GLRenderBatch.h"

#include "GLES2/gl2.h"

using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;

#define RELATIVE_SCALING 1.0f

namespace
{
    static float ZERO_COORDS[2] = { 0, 0 };

    class BasicPointRenderContext : public StyleRenderContext {
    public:
        BasicPointRenderContext(atakmap::renderer::feature::GLBasicPointStyle *owner, const atakmap::feature::BasicPointStyle *style);
        virtual ~BasicPointRenderContext();
        void release();
    
        GLBasicPointStyle *owner;
    };

    /**************************************************************************/

    class Point : public GLBasicPointStyle {
    public:
        Point(const atakmap::feature::BasicPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };

    class LineString : public GLBasicPointStyle
    {
    public:
        LineString(const atakmap::feature::BasicPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };

    class PolygonS : public GLBasicPointStyle
    {
    public:
        PolygonS(const atakmap::feature::BasicPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };
}

GLStyleSpi *GLBasicPointStyle::getSpi() {
    static Spi spi;
    return &spi;
}

GLBasicPointStyle::Spi::~Spi() { }

GLStyle *GLBasicPointStyle::Spi::create(const GLStyleSpiArg &object) {
    const atakmap::feature::Style *s = object.style;
    const atakmap::feature::Geometry *g = object.geometry;
    if (s == nullptr || g == nullptr)
        return nullptr;
#if 0
    // XXX - capture basic point style here as well to avoid having to
    //       implement a new GLStyle using GL_POINTs rendering right now
    if (s instanceof BasicPointStyle) {
        final BasicPointStyle basic = (BasicPointStyle)s;
        s = new IconPointStyle(basic.getColor(),
                               defaultIconUri,
                               basic.getSize(),
                               basic.getSize(),
                               0,
                               0,
                               0,
                               false);
    }
#endif

    const atakmap::feature::BasicPointStyle *bs = dynamic_cast<const atakmap::feature::BasicPointStyle *>(s);
    if (bs == nullptr)
        return nullptr;
    if (dynamic_cast<const atakmap::feature::Point *>(g))
        return new ::Point(bs);
    else if (dynamic_cast<const atakmap::feature::LineString *>(g))
        return new ::LineString(bs);
    else if (dynamic_cast<const atakmap::feature::Polygon *>(g))
        return new ::PolygonS(bs);
    else if (dynamic_cast<const atakmap::feature::GeometryCollection *>(g)) {
        return new GLGeometryCollectionStyle(s, GLBasicPointStyle::getSpi());
    }
    return nullptr;
}

GLBasicPointStyle::GLBasicPointStyle(const atakmap::feature::BasicPointStyle *style)
: GLPointStyle(style),
#define GET_COLOR(c) atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::c) / 255.0f
colorR(GET_COLOR(RED)),
colorG(GET_COLOR(GREEN)),
colorB(GET_COLOR(BLUE)),
colorA(GET_COLOR(ALPHA)),
#undef GET_COLOR
size(style->getSize())
{
}

void GLBasicPointStyle::drawAt(const atakmap::renderer::map::GLMapView *view, float xpos, float ypos, StyleRenderContext *ctx)
{
    BasicPointRenderContext *context = static_cast<BasicPointRenderContext *>(ctx);

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, 0.0f);
    GLES20FixedPipeline::getInstance()->glPointSize(this->size);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, ZERO_COORDS);

    GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);

    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_POINTS, 0, 4);

    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    glDisable(GL_BLEND);

    GLES20FixedPipeline::getInstance()->glPopMatrix();
}

void GLBasicPointStyle::batchAt(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, float xpos, float ypos, StyleRenderContext *ctx)
{
    // XXX - batching of GL_POINTS not supported, cycle the batch and draw the
    //       points
    batch->end();
    drawAt(view, xpos, ypos, ctx);
    batch->begin();
}

bool GLBasicPointStyle::isBatchable(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
{
    return false;
}

StyleRenderContext *GLBasicPointStyle::createRenderContext(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry)
{
    return new BasicPointRenderContext(this, static_cast<const atakmap::feature::BasicPointStyle *>(this->style));
}

void GLBasicPointStyle::releaseRenderContext(StyleRenderContext *ctx)
{
    if (ctx != nullptr) {
        BasicPointRenderContext *context = static_cast<BasicPointRenderContext *>(ctx);
        context->release();
    }
}

/**************************************************************************/

namespace
{
    BasicPointRenderContext::BasicPointRenderContext(GLBasicPointStyle * o, const atakmap::feature::BasicPointStyle *style) :
        owner(o)
    {}

    BasicPointRenderContext::~BasicPointRenderContext()
    {}

    void BasicPointRenderContext::release()
    {
    }

    /**************************************************************************/

    Point::Point(const atakmap::feature::BasicPointStyle *style)
    : GLBasicPointStyle(style) { }

    void Point::draw(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) {
        
        GLPoint *p = static_cast<GLPoint *>(geometry);
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        this->drawAt(view,
                     (float)scratchPoint.x,
                     (float)scratchPoint.y,
                     static_cast<BasicPointRenderContext *>(ctx));
    }

    void Point::batch(const atakmap::renderer::map::GLMapView *view,
                      GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {
        GLPoint *p = static_cast<GLPoint *>(geometry);
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        this->batchAt(view,
                      batch,
                      (float)scratchPoint.x,
                      (float)scratchPoint.y,
                      static_cast<BasicPointRenderContext *>(ctx));
    }

    LineString::LineString(const atakmap::feature::BasicPointStyle *style) :
        GLBasicPointStyle(style)
    {}

    void LineString::draw(const atakmap::renderer::map::GLMapView *view,
                          GLGeometry *geometry, StyleRenderContext *ctx) {
        
        GLLineString *p = static_cast<GLLineString *>(geometry);
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            this->drawAt(view,
                         buffer[i * 2],
                         buffer[i * 2 + 1],
                         static_cast<BasicPointRenderContext *>(ctx));
        }
    }

    void LineString::batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) {

        GLLineString *p = static_cast<GLLineString *>(geometry);
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            this->batchAt(view,
                          batch,
                          buffer[i * 2],
                          buffer[i * 2 + 1],
                          static_cast<BasicPointRenderContext *>(ctx));
        }
    }

    PolygonS::PolygonS(const atakmap::feature::BasicPointStyle *style) :
        GLBasicPointStyle(style)
    {}

    void PolygonS::draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {

        GLPolygon *p = static_cast<GLPolygon *>(geometry);
        const float *buffer;
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                this->drawAt(view,
                             buffer.first[j * 2],
                             buffer.first[j * 2 + 1],
                             static_cast<BasicPointRenderContext *>(ctx));
            }
        }
    }

    void PolygonS::batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {
        GLPolygon *p = static_cast<GLPolygon *>(geometry);
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            //for(int i = 0; i < 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                this->batchAt(view,
                              batch,
                              buffer.first[j * 2],
                              buffer.first[j * 2 + 1],
                              static_cast<BasicPointRenderContext *>(ctx));
            }
        }
    }
}
