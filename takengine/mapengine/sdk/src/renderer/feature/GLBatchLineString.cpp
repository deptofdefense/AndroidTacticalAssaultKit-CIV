
#include "renderer/GL.h"

#include <algorithm>

#include "math/Ellipsoid.h"

#include "renderer/GLES20FixedPipeline.h"

#include "util/MemBuffer.h"
#include "util/DataInput2.h"

#include "feature/Style.h"
#include "feature/LineString.h"

#include "renderer/RendererUtils.h"
#include "renderer/feature/GLBatchGeometry.h"
#include "renderer/feature/GLBatchLineString.h"
#include "renderer/feature/GLGeometry.h"

using namespace atakmap::util;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

double GLBatchLineString::cachedNominalUnitSize;
int GLBatchLineString::cachedNominalUnitSizeSrid = -1;

GLBatchLineString::GLBatchLineString(GLRenderContext *surface)
: GLBatchLineString(surface, 1)
{ }

GLBatchLineString::GLBatchLineString(GLRenderContext *surface, int zOrder)
: GLBatchGeometry(surface, zOrder), vertices() {
    this->strokeWidth = 1.0f;
    this->strokeColorR = 1.0f;
    this->strokeColorG = 1.0f;
    this->strokeColorB = 1.0f;
    this->strokeColorA = 1.0f;
    this->strokeColor = 0xFFFFFFFF;
    this->projectedVerticesPtr = 0LL;
    this->projectedVerticesSrid = -1;
    this->numPoints = 0;
}

namespace {
    struct StrokeStylePredicate {
        inline bool operator()(const CompositeStyle::StylePtr &style) const {
            return dynamic_cast<atakmap::feature::StrokeStyle *>(style.get()) != nullptr;
        }
    };
}

void GLBatchLineString::setStyle(atakmap::feature::Style *value) {

    if (dynamic_cast<CompositeStyle *>(value) != nullptr) {
        CompositeStyle *composite = static_cast<CompositeStyle *>(value);
        
        CompositeStyle::StyleVector::const_iterator it = std::find_if(composite->components().begin(),
                                                             composite->components().end(),
                                                             StrokeStylePredicate());
        
        value = static_cast<atakmap::feature::StrokeStyle *>(it->get());
    }

    BasicStrokeStyle *basicStroke = dynamic_cast<atakmap::feature::BasicStrokeStyle *>(value);
    if (basicStroke != nullptr) {
        this->strokeWidth = basicStroke->getStrokeWidth();
        this->strokeColor = basicStroke->getColor();
        this->strokeColorR = Utils::colorExtract(this->strokeColor, Utils::RED) / 255.0f;
        this->strokeColorG = Utils::colorExtract(this->strokeColor, Utils::GREEN) / 255.0f;
        this->strokeColorB = Utils::colorExtract(this->strokeColor, Utils::BLUE) / 255.0f;
        this->strokeColorA = Utils::colorExtract(this->strokeColor, Utils::ALPHA) / 255.0f;
    }
}

void GLBatchLineString::setGeometryImpl(MemBufferT<uint8_t > *blob, int type, int lod) {
    GLBatchGeometry::setGeometry(blob, type, lod);
}

void GLBatchLineString::setGeometry(MemBufferT<uint8_t> *blob, int type, int lod) {
    
    TAK::Engine::Util::ByteBufferInput2 input;
    input.open(blob);
    input.readInt(&this->targetNumPoints);
    
    this->setGeometryImpl(blob, type, lod);
}

void GLBatchLineString::setGeometryImpl(MemBufferT<uint8_t > *blob, int type) {
    bool compressed = ((type / 1000000) == 1);
    int hi = ((type / 1000) % 1000);
    int dim;
    if (hi == 0)
    {
        dim = 2;
    }
    else if (hi == 1 || hi == 2)
    {
        dim = 3;
    }
    else if (hi == 3)
    {
        dim = 4;
    }
    else
    {
        throw std::invalid_argument("data");
    }

    TAK::Engine::Util::ByteBufferInput2 input;
    input.open(blob);
    int numPoints = 0;
    input.readInt(&numPoints);
    size_t maxPoints;
    if (!compressed)
    {
        maxPoints = blob->remaining() / (dim * 8);
    }
    else
    {
        maxPoints = 1 + ((blob->remaining() - (dim * 8)) / (dim * 4));
    }

    // XXX - really not sure why this is happening, but sometimes the
    //       simplified geometry appears to come back with a bad number of
    //       points and point data. if we detect this situation, report it
    //       and quietly retain the previous (valid) geometry
    if (numPoints > maxPoints)
    {
//        Log->w(TAG, "Invalid simplified geometry for " + name + "; field=" + numPoints + " available=" + maxPoints);
        return;
    }

    this->numPoints = numPoints;

    if (this->points.capacity() < (this->numPoints * 2 * 4)) {
        this->points.resize(this->numPoints * 2);
        this->vertices.resize(this->numPoints * 2);
        this->projectedVertices.resize(this->numPoints * 3);
    }

    int pointsPos = 0;

    double x;
    double y;
    int skip = (dim - 2) * (compressed ? 4 : 8);
    if (dim == 2 && !compressed) {
        for (int i = 0; i < this->numPoints; i++) {
            input.readDouble(&x);
            input.readDouble(&y);
            this->points[i * 2] = x;
            this->points[i * 2 + 1] = y;
        }
    }
    else
    {
        x = 0.0;
        y = 0.0;
        for (int i = 0; i < this->numPoints; i++)
        {
            if (!compressed || i == 0) {
                input.readDouble(&x);
                input.readDouble(&y);
            }
            else {
                float xv = 0.f, yv = 0.f;
                input.readFloat(&xv);
                input.readFloat(&yv);
                x += xv;
                y += yv;
            }

            blob->position(blob->position() + skip);
            points[i * 2] = x;
            points[i * 2 + 1] = y;
        }
    }

    /*this->points->limit(pointsPos / 4);

    this->vertices->position(this->points->position());
    this->vertices->limit(this->points->limit());*/

    // force reprojection
    this->projectedVerticesSrid = -1;
}

void GLBatchLineString::setGeometry(LineString *linestring) {
    
/*TODO--    System::Tuple<GLBatchLineString ^, LineString ^> ^arg = gcnew System::Tuple<GLBatchLineString ^, LineString ^>(this, linestring);
    if (this->surface->isGLThread())
        this->setGeometryImpl(arg);
    else
        this->surface->runOnGLThread(gcnew DelegateGLRunnable(gcnew DelegateGLRunnable::Run(setGeometryImpl)), arg);*/
}

/*TODO--void GLBatchLineString::setGeometryImpl(System::Object ^opaque) {
    
    System::Tuple<GLBatchLineString ^, LineString ^> ^arg = static_cast<System::Tuple<GLBatchLineString ^, LineString ^> ^>(opaque);
    LineString ^linestring = arg->Item2;
    arg->Item1->numPoints = linestring->getPointCount();

    if (arg->Item1->points == nullptr || arg->Item1->points->Length < (arg->Item1->numPoints * 2 * 4)) {
        arg->Item1->points = gcnew array<float>(arg->Item1->numPoints * 2);
        arg->Item1->vertices = gcnew array<float>(arg->Item1->numPoints*2);
        arg->Item1->projectedVertices = gcnew array<float>(arg->Item1->numPoints * 3);
    }

    double x;
    double y;

    for (int i = 0; i < arg->Item1->numPoints; i++) {
        x = linestring->getX(i);
        y = linestring->getY(i);

        arg->Item1->points[i * 2] = x;
        arg->Item1->points[i * 2 + 1] = y;
    }

    // force reprojection
    arg->Item1->projectedVerticesSrid = -1;
}*/

std::pair<float *, size_t> GLBatchLineString::projectVertices(const GLMapView *view, int vertices)
{
    if (this->points.capacity() == 0 || this->numPoints < 2) {
        return std::pair<float *, size_t>(NULL, 0);
    }

    // project the vertices
    switch (vertices)
    {
        case GLGeometry::VERTICES_PIXEL:
            view->forward(this->points.access(), this->points.limit(), this->vertices.access());
            //view->forward(this->points, this->vertices);
            return std::pair<float *, size_t>(this->vertices.access(), this->vertices.limit());
        case GLGeometry::VERTICES_PROJECTED:
            if (view->drawSrid != this->projectedVerticesSrid)
            {
                bool proj3d = view->scene.projection->is3D();
                int stride = 2;
                if (proj3d) stride = 3;

                for (int i = 0; i < this->numPoints; i++) {
                    atakmap::core::GeoPoint scratchGeo(this->points[i * 2 + 1], this->points[i * 2]);
                    atakmap::math::Point<double> scratchPoint;
                    view->scene.projection->forward(&scratchGeo, &scratchPoint);
                    this->projectedVertices[i * stride] = scratchPoint.x;
                    this->projectedVertices[i * stride + 1] = scratchPoint.y;
                    // if the projection is 3D, include Z
                    if (proj3d) {
                        this->projectedVertices[i * stride + 2] = scratchPoint.z;
                    }
                }
                this->projectedVerticesSrid = view->drawSrid;
                this->projectedVerticesSize = proj3d ? 3 : 2;

                // XXX - !!! this will be broken for 3D !!!

                // XXX - attempts to batch on the 'strokeWidth' will produce
                //       undesirable effects for the projected case as the
                //       the input vertices and associated width are defined
                //       in the projected coordinate space NOT in pixels. we
                //       will attempt to compute a nominal projected stroke
                //       width in meters to produce a reasonable width for
                //       batching purposes based on the map's resolution at
                //       render time
                if (cachedNominalUnitSizeSrid == this->projectedVerticesSrid)
                {
                    this->projectedNominalUnitSize = cachedNominalUnitSize;
                }
                else
                {
                    atakmap::core::GeoPoint scratchGeo(0, view->scene.projection->getMaxLongitude());
                    atakmap::math::PointD scratchPoint;
                    view->scene.projection->forward(&scratchGeo, &scratchPoint);
                    double projMaxX = scratchPoint.x;
                    scratchGeo.set(0, view->scene.projection->getMinLongitude());
                    view->scene.projection->forward(&scratchGeo, &scratchPoint);
                    double projMinX = scratchPoint.x;

                    this->projectedNominalUnitSize = (6378137.0 * 2.0 * M_PI) / std::fabs(projMaxX - projMinX);
                    cachedNominalUnitSize = this->projectedVerticesSrid;
                }
            }
            return std::pair<float *, size_t>(this->projectedVertices.access(), this->projectedVertices.limit());
        default :
            throw std::invalid_argument("verticies");
    }
}

void GLBatchLineString::draw(const GLMapView *view) {
    this->draw(view, GLGeometry::VERTICES_PIXEL);
}

void GLBatchLineString::draw(const GLMapView *view, int vertices) {
    std::pair<float *, size_t> v = this->projectVertices(view, vertices);
    if (v.first == NULL) {
        return;
    }

    this->drawImpl(view, v.first, (vertices == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2);
}

void GLBatchLineString::drawImpl(const GLMapView *view, const float *v, int size) {
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, v);
    GLES20FixedPipeline::getInstance()->glColor4f(this->strokeColorR, this->strokeColorG, this->strokeColorB, this->strokeColorA);

    glLineWidth(this->strokeWidth);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, this->numPoints);

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
}

void GLBatchLineString::release() {
    this->points.resize(0);
    this->vertices.resize(0);
    this->projectedVertices.resize(0);
    this->projectedVerticesPtr = 0LL;
    this->projectedVerticesSrid = -1;
    this->numPoints = 0;
}

bool GLBatchLineString::isBatchable(const GLMapView *view) {
    return (this->numPoints > 1);
}

void GLBatchLineString::batch(const GLMapView *view, GLRenderBatch *batch) {
    this->batch(view, batch, GLGeometry::VERTICES_PIXEL);
}

void GLBatchLineString::batch(const GLMapView *view, GLRenderBatch *batch, int vertices) {
    std::pair<float *, size_t> v = this->projectVertices(view, vertices);
    if (v.first == NULL) {
        return;
    }

    this->batchImpl(view, batch, vertices, v.first, v.second);
}

void GLBatchLineString::batchImpl(const GLMapView *view, GLRenderBatch *batch, int vertices, const float *v, size_t count) {
    float strokeFactor;
    switch (vertices) {
        case GLGeometry::VERTICES_PIXEL:
            strokeFactor = 1.0f;
            break;
        case GLGeometry::VERTICES_PROJECTED:
            strokeFactor = static_cast<float>(view->getView()->getMapResolution() / this->projectedNominalUnitSize);
            break;
        default :
            throw std::invalid_argument("vertices");
    }

    batch->addLineStrip(v, this->numPoints, this->strokeWidth * strokeFactor, this->strokeColorR, this->strokeColorG, this->strokeColorB, this->strokeColorA);
}

