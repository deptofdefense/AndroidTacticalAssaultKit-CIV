
#include "renderer/GL.h"

#include "feature/Style.h"
#include "feature/Polygon.h"

#include "renderer/GLES20FixedPipeline.h"
//TODO--#include "renderer/GLBackground.h"
#include "renderer/GLTriangulate.h"
#include "renderer/feature/GLGeometry.h"
#include "renderer/feature/GLBatchPolygon.h"
#include "renderer/RendererUtils.h"
#include "util/DataInput2.h"

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;

GLBatchPolygon::GLBatchPolygon(GLRenderContext *surface)
: GLBatchLineString(surface, 2),
fillColorR(0.0f),
fillColorG(0.0f),
fillColorB(0.0f),
fillColorA(0.0f),
polyRenderMode(GLTriangulate::STENCIL) { }

namespace {
    struct FillStylePredicate {
        inline bool operator()(CompositeStyle::StylePtr value) const {
            return dynamic_cast<atakmap::feature::FillStyle *>(value.get()) != NULL;
        }
    };
}

void GLBatchPolygon::setStyle(atakmap::feature::Style *value) {

    GLBatchLineString::setStyle(value);

    CompositeStyle *compositeStyle = dynamic_cast<atakmap::feature::CompositeStyle *>(value);
    if (compositeStyle != nullptr) {
        CompositeStyle::StyleVector::const_iterator it = std::find_if(compositeStyle->components().begin(),
                                                                      compositeStyle->components().end(),
                                                                      FillStylePredicate());
        if (it != compositeStyle->components().end()) {
            value = it->get();
        }
    }
    
    atakmap::feature::BasicFillStyle *basicFill = dynamic_cast<atakmap::feature::BasicFillStyle *>(value);
    if (basicFill != nullptr) {
        this->fillColor = basicFill->getColor();
        this->fillColorR = Utils::colorExtract(this->fillColor, Utils::RED) / 255.0f;
        this->fillColorG = Utils::colorExtract(this->fillColor, Utils::GREEN) / 255.0f;
        this->fillColorB = Utils::colorExtract(this->fillColor, Utils::BLUE) / 255.0f;
        this->fillColorA = Utils::colorExtract(this->fillColor, Utils::ALPHA) / 255.0f;
    }
}

void GLBatchPolygon::setGeometry(atakmap::util::MemBufferT<uint8_t> *blob, int type, int lod) {
    if (blob->remaining() >= 8) // XXX - may be 4 for 0 rings?
    {
        size_t oldPos = blob->position();
        blob->position(blob->position() + 4);
        
        TAK::Engine::Util::ByteBufferInput2 input;
        input.open(blob);
        input.readInt(&this->targetNumPoints);
        blob->position(oldPos);
    }
    else
    {
        this->targetNumPoints = 0;
    }

    GLBatchLineString::setGeometryImpl(blob, type, lod);
}

void GLBatchPolygon::setGeometryImpl(atakmap::util::MemBufferT<uint8_t> *blob, int type) {
    
    TAK::Engine::Util::ByteBufferInput2 input;
    input.open(blob);
    
    int numRings = 0;
    input.readInt(&numRings);
    if (numRings == 0)
    {
        this->numPoints = 0;
    }
    else
    {
        GLBatchLineString::setGeometryImpl(blob, type);

        if (this->fillColorA > 0.0f && this->numPoints > 0)
        {
            int numVerts = this->numPoints - 1;
            if (this->indices.capacity() < (numVerts - 2) * 3) {
                this->indices.resize((numVerts - 2) * 3);
            }
#if 0
            this->polyRenderMode = GLTriangulate::triangulate(this->points, this->indices);
#else
            this->polyRenderMode = GLTriangulate::STENCIL;
#endif
        }
    }
}

void GLBatchPolygon::setGeometry(Polygon *geom) {
    
    /*TODO--System::Tuple<GLBatchPolygon ^, Polygon ^> ^arg = gcnew System::Tuple<GLBatchPolygon ^, Polygon ^>(this, geom);
    if (this->surface->isGLThread())
        this->setGeometryImpl(arg);
    else
        this->surface->runOnGLThread(gcnew DelegateGLRunnable(gcnew DelegateGLRunnable::Run(setGeometryImpl)), arg);*/
}

/*TODO--void GLBatchPolygon::setGeometryImpl(System::Object ^opaque)
{
    System::Tuple<GLBatchPolygon ^, Polygon ^> ^arg = static_cast<System::Tuple<GLBatchPolygon ^, Polygon ^> ^>(opaque);
    Polygon ^polygon = arg->Item2;

    const int numRings = ((polygon->getExteriorRing() != nullptr) ? 1 : 0) + polygon->getInteriorRings()->Length;
    if (numRings == 0) {
        arg->Item1->numPoints = 0;
    }
    else {
        GLBatchLineString::setGeometryImpl(polygon->getExteriorRing());

        if (arg->Item1->fillColorA > 0.0f && arg->Item1->numPoints > 0) {
            int numVerts = arg->Item1->numPoints - 1;
            if (arg->Item1->indices == nullptr || arg->Item1->indices->Length < (numVerts - 2) * 3) {
                arg->Item1->indices = gcnew array<short>((numVerts - 2) * 3);
            }
#if 0
            arg->Item1->polyRenderMode = GLTriangulate::triangulate(arg->Item1->points, arg->Item1->indices);
#else
            arg->Item1->polyRenderMode = GLTriangulate::STENCIL;
#endif
        }
    }
}*/

void GLBatchPolygon::draw(const atakmap::renderer::map::GLMapView *view, int vertices) {
    std::pair<float *, size_t> v = this->projectVertices(view, vertices);
    if (v.first == NULL) {
        return;
    }

    int size = (vertices == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2;

    if (this->fillColorA > 0.0f) {
        switch (this->polyRenderMode)
        {
            case GLTriangulate::INDEXED:
                this->drawFillTriangulate(view, v.first, size);
                break;
            case GLTriangulate::TRIANGLE_FAN:
                this->drawFillConvex(view, v.first, size);
                break;
            case GLTriangulate::STENCIL:
            default:
                this->drawFillStencil(view, v.first, size);
                break;
        }
    }
    
    if (this->strokeColorA > 0.0f) {
        GLBatchLineString::drawImpl(view, v.first, size);
    }
}

bool GLBatchPolygon::isBatchable(const atakmap::renderer::map::GLMapView *view) {
    return (this->fillColorA == 0.0f || this->polyRenderMode != GLTriangulate::STENCIL) && GLBatchLineString::isBatchable(view);
}

void GLBatchPolygon::drawFillTriangulate(const atakmap::renderer::map::GLMapView *view, const float *v, int size) {
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, v);

    GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);

    GLES20FixedPipeline::getInstance()->glDrawElements(GL_TRIANGLES, this->indices.limit(), GL_UNSIGNED_SHORT, this->indices.access());

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
}

void GLBatchPolygon::drawFillConvex(const atakmap::renderer::map::GLMapView *view, const float *v, int size) {
    
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, v);

    GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);

    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, this->numPoints);

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
}

void GLBatchPolygon::drawFillStencil(const atakmap::renderer::map::GLMapView *view, const float *v, int size) {
    
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, v);

    glClear(GL_STENCIL_BUFFER_BIT);
    glStencilMask(0xFFFFFFFF);
    glStencilFunc(GL_ALWAYS, 0x1, 0x1);
    glStencilOp(GL_KEEP, GL_KEEP, GL_INVERT);
    glEnable(GL_STENCIL_TEST);

    glColorMask(false, false, false, false);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, this->numPoints);
    glColorMask(true, true, true, true);

    glStencilMask(0xFFFFFFFF);
    glStencilFunc(GL_EQUAL, 0x1, 0x1);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

    // draw background if fill == true
    /*TODO--if (bkgrnd == nullptr) {
        bkgrnd = gcnew GLBackground(view->_left, view->_bottom, view->_right, view->_top);
        //bkgrnd = gcnew GLBackground(-2000, -2000, 2000, 2000);
    }

    bkgrnd->draw(GLBackground::BKGRND_TYPE_SOLID, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA, false); // blend set to false
*/
    glDisable(GL_STENCIL_TEST);

    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
}

void GLBatchPolygon::batchImpl(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch, int vertices, const float *v, size_t count) {
    if (this->fillColorA > 0) {
        switch (this->polyRenderMode) {
            case GLTriangulate::INDEXED:
                batch->addTriangles(v, count, /*TODO-- this->indices,*/ this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::TRIANGLE_FAN:
                batch->addTriangleFan(v, count, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
                break;
            case GLTriangulate::STENCIL:
                batch->end();
                this->drawFillStencil(view, v, (vertices == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2);
                batch->begin();
                break;
            default:
                throw std::logic_error("polyRenderMode");
        }
    }

    if (this->strokeColorA > 0)
    {
        GLBatchLineString::batchImpl(view, batch, vertices, v, count);
    }
}

