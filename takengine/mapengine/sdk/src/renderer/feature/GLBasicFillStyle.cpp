
#include "util/MemBuffer.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/feature/GLBasicFillStyle.h"
#include "feature/GeometryCollection.h"
#include "feature/Polygon.h"
#include "renderer/RendererUtils.h"
#include "renderer/feature/GLPolygon.h"
#include "renderer/feature/GLGeometryCollectionStyle.h"
#include "renderer/GLTriangulate.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/map/GLMapView.h"
#include "GLES2/gl2.h"


using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;


GLBasicFillStyle::GLBasicFillStyle(const BasicFillStyle *style) :
    GLFillStyle(style),
    fillColorR(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::RED) / 255.0f),
    fillColorG(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::GREEN) / 255.0f),
    fillColorB(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::BLUE) / 255.0f),
    fillColorA(atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::ALPHA) / 255.0f)
{
}

GLBasicFillStyle::~GLBasicFillStyle()
{

}

StyleRenderContext *GLBasicFillStyle::createRenderContext(const GLMapView *view, GLGeometry *geom)
{
    return nullptr;
}

void GLBasicFillStyle::releaseRenderContext(StyleRenderContext *ctx)
{

}


namespace {
    class GLBackground {

    private:
        float data[8];

    public:
        GLBackground(float x0, float y0, float x1, float y1) {
            float extentY = fabs(y1 - y0);
            float extentX = fabs(x1 - x0);

            float maxExtent = fmax(extentY, extentX);

            float expandedExtent = maxExtent * 2.3f;

            data[0] = (-maxExtent);
            data[1] = (-maxExtent);
            data[2] = (-maxExtent);
            data[3] = (expandedExtent);
            data[4] = (expandedExtent);
            data[5] = (expandedExtent);
            data[6] = (expandedExtent);
            data[7] = (-maxExtent);
        }

        void draw(float red, float green, float blue, float alpha,
                         bool blend) {
            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadIdentity();

            GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, data);

            if (alpha < 1.0f && !blend) {
                glEnable(GL_BLEND);
                glBlendFunc(
                    GL_SRC_ALPHA,
                    GL_ONE_MINUS_SRC_ALPHA);
            }
            GLES20FixedPipeline::getInstance()->glColor4f(red, green, blue, alpha);
            GLES20FixedPipeline::getInstance()->glDrawArrays(
                GL_TRIANGLE_FAN, 0, 4);

            GLES20FixedPipeline::getInstance()->glPopMatrix();
        }

    };



    class PolygonFillContext : public StyleRenderContext {

    public:
        int fillMode;
        atakmap::util::MemBufferT<short> indices;

    public:
        PolygonFillContext(GLPolygon *poly) {
            // XXX - holes
            indices.resize((poly->getNumVertices(0) - 2) * 3);
            auto verts = poly->getPoints(0);
            fillMode = GLTriangulate::triangulate(verts.first, verts.second, indices.access());
            if (fillMode != GLTriangulate::INDEXED)
                indices.resize(0);
        }
    };

    class PolygonStyle : public GLBasicFillStyle
    {
    public:
        PolygonStyle(const BasicFillStyle *style) : GLBasicFillStyle(style), bkgrnd(nullptr)
        {
        };

        virtual ~PolygonStyle() {};

        virtual StyleRenderContext *createRenderContext(const GLMapView *view, GLGeometry *geom) override {
            return new PolygonFillContext((GLPolygon *)geom);
        };
        
        virtual void draw(const GLMapView *view, GLGeometry *geom, StyleRenderContext *ctx) override {
            PolygonFillContext *context = (PolygonFillContext *)ctx;
            GLPolygon *geometry = (GLPolygon *)geom;

            switch (context->fillMode) {
            case GLTriangulate::TRIANGLE_FAN:
            case GLTriangulate::INDEXED:
                this->drawImpl(view, geometry, context);
                break;
            case GLTriangulate::STENCIL:
                this->drawStencil(view, geometry, context);
                break;
            default:
                throw std::logic_error("fillMode");
            }
        };

        virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *g, StyleRenderContext *ctx) override {
            PolygonFillContext *context = (PolygonFillContext *)ctx;
            GLPolygon *geometry = (GLPolygon *)g;

            std::pair<float *, size_t> vertices = geometry->getVertices(view, atakmap::renderer::feature::GLPolygon::VERTICES_PIXEL, 0);

            switch (context->fillMode) {
            case GLTriangulate::TRIANGLE_FAN:
                batch->addTriangleFan(vertices.first, vertices.second, fillColorR, fillColorG, fillColorB, fillColorA);
                break;
            case GLTriangulate::INDEXED:
                batch->addTriangles(vertices.first, vertices.second, context->indices.access(), context->indices.limit(), fillColorR, fillColorG, fillColorB, fillColorA);
                break;
            case GLTriangulate::STENCIL:
                batch->end();
                try {
                    drawStencil(view, geometry, context);
                } catch (...) {
                    batch->begin();
                    throw;
                }
                batch->begin();
                break;
            default:
                throw std::logic_error("fillMode");
            }
        };

        virtual bool isBatchable(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override
        {
            PolygonFillContext *context = (PolygonFillContext *)ctx;
            return context->fillMode != GLTriangulate::STENCIL;
        };

    private:
        void drawStencil(const GLMapView *view, GLPolygon *geometry, PolygonFillContext *context)
        {
            std::pair<float *, size_t> vertices = geometry->getVertices(view, atakmap::renderer::feature::GLPolygon::VERTICES_PROJECTED, 0);

            // XXX - holes

            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);

            GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            GLES20FixedPipeline::getInstance()->glVertexPointer(3, GL_FLOAT, 0, vertices.first);

            glClear(GL_STENCIL_BUFFER_BIT);
            glStencilMask(0xFFFFFFFF);
            glStencilFunc(GL_ALWAYS, 0x1, 0x1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_INVERT);
            glEnable(GL_STENCIL_TEST);

            glColorMask(false, false, false, false);
            GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0,
                                             geometry->getNumVertices(0));
            glColorMask(true, true, true, true);

            GLES20FixedPipeline::getInstance()->glPopMatrix();

            glStencilMask(0xFFFFFFFF);
            glStencilFunc(GL_EQUAL, 0x1, 0x1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

            // draw background if fill == true
            if (bkgrnd == nullptr)
                bkgrnd = new GLBackground(view->left, view->bottom, view->right, view->top);

            bkgrnd->draw(fillColorR,
                        fillColorG,
                        fillColorB,
                        fillColorA,
                        false); // blend set to false

            glDisable(GL_STENCIL_TEST);

            GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

        };

        void drawImpl(const GLMapView *view, GLPolygon *geometry, PolygonFillContext *context)
        {
            // XXX - holes
            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);

            GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            ::glEnable(GL_BLEND);
            ::glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline::getInstance()->glColor4f(fillColorR,
                                          fillColorG,
                                          fillColorB,
                                          fillColorA);
            std::pair<float *, size_t> vertices = geometry->getVertices(view, atakmap::renderer::feature::GLPolygon::VERTICES_PROJECTED, 0);

            GLES20FixedPipeline::getInstance()->glVertexPointer(3, GL_FLOAT, 0, vertices.first);

            switch (context->fillMode) {
            case GLTriangulate::TRIANGLE_FAN:
                GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, geometry->getNumVertices(0));
                break;
            case GLTriangulate::INDEXED:
            {
                short *ps = context->indices.access();
                GLES20FixedPipeline::getInstance()->glDrawElements(GL_TRIANGLES, context->indices.capacity(), GL_UNSIGNED_SHORT, ps);
                break;
            }
            case GLTriangulate::STENCIL:
            default:
                throw std::logic_error("fillMode");
            }

            ::glDisable(GL_BLEND);
            GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

            GLES20FixedPipeline::getInstance()->glPopMatrix();
        };


        GLBackground *bkgrnd;


    };
}
GLStyleSpi *GLBasicFillStyle::getSpi() {
    static Spi spi;
    return &spi;
}


GLBasicFillStyle::Spi::~Spi() { }

static GLBasicFillStyle::Spi basicFillStyleSpi;

GLStyle *GLBasicFillStyle::Spi::create(const atakmap::renderer::feature::GLStyleSpiArg &object) {
    
    const Style *s = object.style;
    const Geometry * const g = object.geometry;
    
    if (s == nullptr || g == nullptr)
        return nullptr;
    
    const BasicFillStyle * const styleImpl = dynamic_cast<const BasicFillStyle *>(s);
    if (styleImpl == nullptr)
        return nullptr;

    if (dynamic_cast<const atakmap::feature::Polygon *>(g))
        return new ::PolygonStyle(styleImpl);
    
    else if (dynamic_cast<const atakmap::feature::GeometryCollection *>(g))
        return new GLGeometryCollectionStyle(s, &basicFillStyleSpi);
    
    return nullptr;
}
