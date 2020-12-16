
#include "renderer/GL.h"

#include "math/Ellipsoid.h"

#include "renderer/GLES20FixedPipeline.h"

#include "feature/Style.h"
#include "feature/LineString.h"

#include "renderer/feature/GLBatchGeometry2.h"
#include "renderer/feature/GLBatchLineString2.h"
#include "renderer/feature/GLGeometry.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;
using namespace atakmap::renderer::feature;

namespace
{
    template<class T>
    size_t tessellate(T *linestringDst, const T *linestringSrc, const size_t numPoints, const T threshold);
}

double GLBatchLineString2::cachedNominalUnitSize = 0.0;
double GLBatchLineString2::cachedNominalUnitSizeSrid = -1;

GLBatchLineString2::GLBatchLineString2(RenderContext &surface)
    : GLBatchGeometry2(surface, 1),
    points(nullptr),
    pointsLength(0),
    vertices(nullptr),
    verticesLength(0),
    projectedVertices(nullptr),
    projectedVerticesLength(0),
    projectedVerticesSrid(-1),
    projectedVerticesSize(0),
    projectedNominalUnitSize(0.0),
    numPoints(0),
    strokeWidth(1.0),
    strokeColorR(1.0),
    strokeColorG(1.0),
    strokeColorB(1.0),
    strokeColorA(1.0),
    strokeColor(0xFFFFFFFF),
    tessellationThreshold(0.0)
{}

GLBatchLineString2::GLBatchLineString2(RenderContext &surface, int zOrder) :
    GLBatchGeometry2(surface, zOrder),
    points(nullptr),
    pointsLength(0),
    vertices(nullptr),
    verticesLength(0),
    projectedVertices(nullptr),
    projectedVerticesLength(0),
    projectedVerticesSrid(-1),
    projectedVerticesSize(0),
    projectedNominalUnitSize(0.0),
    numPoints(0),
    strokeWidth(1.0),
    strokeColorR(1.0),
    strokeColorG(1.0),
    strokeColorB(1.0),
    strokeColorA(1.0),
    strokeColor(0xFFFFFFFF),
    tessellationThreshold(0.0)
{}

TAKErr GLBatchLineString2::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchLineString2::setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS
{
    using namespace atakmap::feature;

    const Style *style = value.get();
    if (const auto *compositeStyle = dynamic_cast<const CompositeStyle *>(style))
    {
        style = compositeStyle->findStyle<atakmap::feature::BasicStrokeStyle>().get();
    }

    if (const auto *basicStroke = dynamic_cast<const BasicStrokeStyle *>(style))
    {
        this->strokeWidth = basicStroke->getStrokeWidth();
        this->strokeColor = basicStroke->getColor();
        this->strokeColorR = ((this->strokeColor>>16)&0xFF) / (float)255;
        this->strokeColorG = ((this->strokeColor>>8)&0xFF) / (float)255;
        this->strokeColorB = (this->strokeColor&0xFF) / (float)255;
        this->strokeColorA = ((this->strokeColor>>24)&0xFF) / (float)255;
    }

    return TE_Ok;
}

TAKErr GLBatchLineString2::setTessellationThreshold(double threshold) NOTHROWS
{
    this->tessellationThreshold = threshold;
    return TE_Ok;
}

double GLBatchLineString2::getTessellationThreshold() NOTHROWS
{
    return this->tessellationThreshold;
}

TAKErr GLBatchLineString2::setGeometryImpl(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return GLBatchGeometry2::setGeometry(std::move(blob), type, lod_val);
}

TAKErr GLBatchLineString2::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return this->setGeometryImpl(std::move(blob), type, lod_val);
}

TAKErr GLBatchLineString2::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
{
    TAKErr code;

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
        return TE_IllegalState;
    }

    int num_points;
    code = blob->readInt(&num_points);
    TE_CHECKRETURN_CODE(code);
    size_t maxPoints;
    if (!compressed)
    {
        std::size_t remaining;
        code = blob->remaining(&remaining);
        TE_CHECKRETURN_CODE(code);
        maxPoints = remaining / (dim * 8);
    }
    else
    {
        std::size_t remaining;
        code = blob->remaining(&remaining);
        TE_CHECKRETURN_CODE(code);
        maxPoints = 1 + ((remaining - (dim * 8)) / (dim * 4));
    }

    // XXX - really not sure why this is happening, but sometimes the
    //       simplified geometry appears to come back with a bad number of
    //       points and point data. if we detect this situation, report it
    //       and quietly retain the previous (valid) geometry
    if (static_cast<std::size_t>(num_points) > maxPoints)
    {
//        Log->w(TAG, "Invalid simplified geometry for " + name + "; field=" + numPoints + " available=" + maxPoints);
        return TE_Ok;
    }

    this->numPoints = static_cast<size_t>(num_points);

    if (!this->points.get() || this->pointsLength < (this->numPoints * 2))
    {
        this->points.reset(new float[this->numPoints * 2]);
        this->pointsLength = this->numPoints * 2;
        this->vertices.reset(new float[this->numPoints * 2]);
        this->verticesLength = (this->numPoints * 2);
        this->projectedVertices.reset(new float[this->numPoints * 3]);
        this->projectedVerticesLength = (this->numPoints * 3);
    }

    double x;
    double y;
    int skip = (dim - 2) * (compressed ? 4 : 8);
    if (dim == 2 && !compressed)
    {
        code = blob->readDouble(&x);
        TE_CHECKRETURN_CODE(code);
        code = blob->readDouble(&y);
        TE_CHECKRETURN_CODE(code);

        mbb.minX = x;
        mbb.maxX = x;
        mbb.minY = y;
        mbb.maxY = y;

        this->points[0] = static_cast<float>(x);
        this->points[1] = static_cast<float>(y);

        for (std::size_t i = 1; i < this->numPoints; i++)
        {
            code = blob->readDouble(&x);
            TE_CHECKBREAK_CODE(code);
            code = blob->readDouble(&y);
            TE_CHECKBREAK_CODE(code);
            this->points[i * 2] = static_cast<float>(x);
            this->points[i * 2 + 1] = static_cast<float>(y);

            if (x < this->mbb.minX)
                this->mbb.minX = x;
            else if (x > this->mbb.maxX)
                this->mbb.maxX = x;
            if (y < this->mbb.minY)
                this->mbb.minY = y;
            else if (y > this->mbb.maxY)
                this->mbb.maxY = y;
        }
        TE_CHECKRETURN_CODE(code);
    }
    else
    {
        code = blob->readDouble(&x);
        TE_CHECKRETURN_CODE(code);
        code = blob->readDouble(&y);
        TE_CHECKRETURN_CODE(code);

        mbb.minX = x;
        mbb.maxX = x;
        mbb.minY = y;
        mbb.maxY = y;

        this->points[0] = static_cast<float>(x);
        this->points[1] = static_cast<float>(y);

        if (!compressed)
        {
            for (std::size_t i = 1; i < this->numPoints; i++)
            {
                code = blob->readDouble(&x);
                TE_CHECKBREAK_CODE(code);
                code = blob->readDouble(&y);
                TE_CHECKBREAK_CODE(code);

                code = blob->skip(skip);
                TE_CHECKBREAK_CODE(code);

                points[i * 2] = static_cast<float>(x);
                points[i * 2 + 1] = static_cast<float>(y);

                if (x < this->mbb.minX)
                    this->mbb.minX = x;
                else if (x > this->mbb.maxX)
                    this->mbb.maxX = x;
                if (y < this->mbb.minY)
                    this->mbb.minY = y;
                else if (y > this->mbb.maxY)
                    this->mbb.maxY = y;
            }
        } else {
            for (std::size_t i = 1; i < this->numPoints; i++)
            {
                float fx;
                code = blob->readFloat(&fx);
                TE_CHECKBREAK_CODE(code);
                x += fx;

                float fy;
                code = blob->readFloat(&fy);
                TE_CHECKBREAK_CODE(code);
                y += fy;

                code = blob->skip(skip);
                TE_CHECKBREAK_CODE(code);

                points[i * 2] = static_cast<float>(x);
                points[i * 2 + 1] = static_cast<float>(y);

                if (x < this->mbb.minX)
                    this->mbb.minX = x;
                else if (x > this->mbb.maxX)
                    this->mbb.maxX = x;
                if (y < this->mbb.minY)
                    this->mbb.minY = y;
                else if (y > this->mbb.maxY)
                    this->mbb.maxY = y;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    /*this->points->limit(pointsPos / 4);

    this->vertices->position(this->points->position());
    this->vertices->limit(this->points->limit());*/

    // tessellation
    if (this->tessellationThreshold) {
        float *pointsPtr = this->points.get();

        const size_t tessellatedPoints = tessellate<float>(nullptr, pointsPtr, this->numPoints, static_cast<float>(this->tessellationThreshold));
        if (tessellatedPoints != this->numPoints) {
            array_ptr<float> npoints(new float[tessellatedPoints * 2]);

            this->numPoints = tessellate<float>(npoints.get(), pointsPtr, this->numPoints, static_cast<float>(this->tessellationThreshold));
            this->points.reset(npoints.release());
            this->pointsLength = (tessellatedPoints * 2);

            if (this->verticesLength < (this->numPoints * 2)) {
                this->vertices.reset(new float[this->numPoints * 2]);
                this->verticesLength = (this->numPoints * 2);
            }
            if (this->projectedVerticesLength < (this->numPoints * 3)) {
                this->projectedVertices.reset(new float[this->numPoints * 3]);
                this->projectedVerticesLength = (this->numPoints * 3);
            }
        }
    }

    // force reprojection
    this->projectedVerticesSrid = -1;

    return code;
}

TAKErr GLBatchLineString2::setGeometry(const atakmap::feature::LineString &linestring) NOTHROWS
{
    return GLBatchGeometry2::setGeometry(linestring);
}

TAKErr GLBatchLineString2::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    const auto &linestring = static_cast<const LineString &>(geom);

    this->numPoints = linestring.getPointCount();

    if (this->points.get() == nullptr || this->pointsLength < (this->numPoints * 2)) {
        this->points.reset(new float[this->numPoints * 2]);
        this->pointsLength = (this->numPoints * 2);
        this->vertices.reset(new float[this->numPoints * 2]);
        this->verticesLength = (this->numPoints * 2);
        this->projectedVertices.reset(new float[this->numPoints * 3]);
        this->projectedVerticesLength = (this->numPoints * 3);
    }

    double x = linestring.getX(0);
    double y = linestring.getY(0);

    this->mbb.minX = x;
    this->mbb.maxX = x;
    this->mbb.minY = y;
    this->mbb.maxY = y;

    this->points[0] = static_cast<float>(x);
    this->points[1] = static_cast<float>(y);

    for (std::size_t i = 1; i < this->numPoints; i++) {
        x = linestring.getX(i);
        y = linestring.getY(i);

        this->points[i * 2] = static_cast<float>(x);
        this->points[i * 2 + 1] = static_cast<float>(y);

        if (x < this->mbb.minX)
            this->mbb.minX = x;
        else if (x > this->mbb.maxX)
            this->mbb.maxX = x;
        if (y < this->mbb.minY)
            this->mbb.minY = y;
        else if (y > this->mbb.maxY)
            this->mbb.maxY = y;
    }

    if (this->tessellationThreshold) {
        float *pointsPtr = this->points.get();

        const size_t tessellatedPoints = tessellate<float>(nullptr, pointsPtr, this->numPoints, static_cast<float>(this->tessellationThreshold));
        if (tessellatedPoints != this->numPoints) {
            array_ptr<float> npoints(new float[tessellatedPoints * 2]);

            this->numPoints = tessellate<float>(npoints.get(), pointsPtr, this->numPoints, static_cast<float>(this->tessellationThreshold));
            this->points.reset(npoints.release());
            this->pointsLength = (tessellatedPoints * 2);

            if (this->verticesLength < (this->numPoints * 2)) {
                this->vertices.reset(new float[this->numPoints * 2]);
                this->verticesLength = (this->numPoints * 2);
            }
            if (this->projectedVerticesLength < (this->numPoints * 3)) {
                this->projectedVertices.reset(new float[this->numPoints * 3]);
                this->projectedVerticesLength = (this->numPoints * 3);
            }
        }
    }

    // force reprojection
    this->projectedVerticesSrid = -1;

    return TE_Ok;
}

TAKErr GLBatchLineString2::projectVertices(const float **result, const GLMapView *view, const int vertices_type) NOTHROWS
{
    if (this->points.get() == nullptr || this->numPoints < 2)
    {
        return TE_Ok;
    }

    // project the vertices
    switch (vertices_type)
    {
        case GLGeometry::VERTICES_PIXEL:
            view->forward(this->points.get(), this->numPoints, this->vertices.get());
            *result = this->vertices.get();
            return TE_Ok;
        case GLGeometry::VERTICES_PROJECTED:
            if (view->drawSrid != this->projectedVerticesSrid)
            {
                bool proj3d = view->scene.projection->is3D();
                int stride = 2;
                if (proj3d) stride = 3;

                for (std::size_t i = 0; i < this->numPoints; i++)
                {
                    atakmap::core::GeoPoint scratchGeo;
                    scratchGeo.latitude = this->points.get()[i * 2 + 1];
                    scratchGeo.longitude = this->points.get()[i * 2];
                    atakmap::math::Point<double> scratchPointD;
                    view->scene.projection->forward(&scratchGeo, &scratchPointD);
                    this->projectedVertices.get()[i * stride] = (float)scratchPointD.x;
                    this->projectedVertices.get()[i * stride + 1] = (float)scratchPointD.y;
                    // if the projection is 3D, include Z
                    if (proj3d)
                    {
                        this->projectedVertices.get()[i * stride + 2] = (float)scratchPointD.z;
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
                    atakmap::core::GeoPoint scratchGeo;
                    scratchGeo.latitude = 0;
                    scratchGeo.longitude = view->scene.projection->getMaxLongitude();

                    atakmap::math::Point<double> scratchPointD;
                    view->scene.projection->forward(&scratchGeo, &scratchPointD);
                    double projMaxX = scratchPointD.x;

                    scratchGeo.latitude = 0;
                    scratchGeo.longitude = view->scene.projection->getMinLongitude();
                    view->scene.projection->forward(&scratchGeo, &scratchPointD);
                    double projMinX = scratchPointD.x;

                    this->projectedNominalUnitSize = (6378137.0 * 2.0 * M_PI) / std::fabs(projMaxX - projMinX);
                    cachedNominalUnitSize = this->projectedVerticesSrid;
                }
            }
            *result = this->projectedVertices.get();
            return TE_Ok;
        default :
            return TE_InvalidArg;
    }
}

void GLBatchLineString2::draw(const GLMapView *view)
{
    this->draw(view, GLGeometry::VERTICES_PIXEL);
}

TAKErr GLBatchLineString2::draw(const GLMapView *view, const int vertices_type) NOTHROWS
{
    TAKErr code;

    const float *v;
    code = this->projectVertices(&v, view, vertices_type);
    TE_CHECKRETURN_CODE(code);
    if (v == nullptr)
    {
        return TE_Ok;
    }

    return this->drawImpl(view, v, (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2);
}

TAKErr GLBatchLineString2::drawImpl(const GLMapView *view, const float *v, int size) NOTHROWS
{
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float *buf = v;
    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, buf);
    GLES20FixedPipeline::getInstance()->glColor4f(this->strokeColorR, this->strokeColorG, this->strokeColorB, this->strokeColorA);

    glLineWidth(this->strokeWidth);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, static_cast<int>(this->numPoints));

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
}

void GLBatchLineString2::release()
{
    this->points.reset();
    this->pointsLength = 0;
    this->vertices.reset();
    this->verticesLength = 0;
    this->projectedVertices.reset();
    this->projectedVerticesLength = 0;
    this->projectedVerticesSrid = -1;
    this->numPoints = 0;
}

bool GLBatchLineString2::isBatchable(const GLMapView *view)
{
    return (this->numPoints > 1);
}

void GLBatchLineString2::batch(const GLMapView *view, GLRenderBatch *batch)
{
    this->batch(view, batch, GLGeometry::VERTICES_PIXEL);
}

TAKErr GLBatchLineString2::batch(const GLMapView *view, GLRenderBatch *batch, const int vertices_type) NOTHROWS
{
    TAKErr code;

    const float *v;
    code = this->projectVertices(&v, view, vertices_type);
    TE_CHECKRETURN_CODE(code);
    if (v == nullptr)
    {
        return TE_Ok;
    }

    return this->batchImpl(view, batch, vertices_type, v);
}

TAKErr GLBatchLineString2::batchImpl(const GLMapView *view, GLRenderBatch *batch, const int vertices_type, const float *v) NOTHROWS
{
    float strokeFactor;
    switch (vertices_type)
    {
        case GLGeometry::VERTICES_PIXEL :
            strokeFactor = 1.0f;
            break;
        case GLGeometry::VERTICES_PROJECTED :
            strokeFactor = (float)(view->drawMapResolution / this->projectedNominalUnitSize);
            break;
        default :
            return TE_InvalidArg;
    }

    batch->addLineStrip(v, this->numPoints*2, this->strokeWidth * strokeFactor, this->strokeColorR, this->strokeColorG, this->strokeColorB, this->strokeColorA);
    return TE_Ok;
}

namespace
{

    template<class T>
    size_t tessellate(T *linestringDst, const T *linestringSrc, const size_t numPoints, const T threshold)
    {
        // tessellation
        size_t tessellatedPoints = numPoints;

        T lastX = linestringSrc[0];
        T lastY = linestringSrc[1];

        T dist;
        T dx;
        T dy;
        for (size_t i = 1; i < numPoints; i++) {
            dx = linestringSrc[i * 2] - lastX;
            dy = linestringSrc[i * 2 + 1] - lastY;
            dist = sqrt(dx*dx + dy*dy);
            if (dist > 0)
                tessellatedPoints += (int)ceil(dist / threshold) - 1;
            lastX = linestringSrc[i * 2];
            lastY = linestringSrc[i * 2 + 1];
        }

        if (numPoints != tessellatedPoints && linestringDst) {
            size_t idx = 0;

            linestringDst[idx++] = linestringSrc[0];
            linestringDst[idx++] = linestringSrc[1];

            int subdivisions;
            for (std::size_t i = 1; i < numPoints; i++) {
                dx = linestringSrc[i * 2] - linestringSrc[(i - 1) * 2];
                dy = linestringSrc[i * 2 + 1] - linestringSrc[(i - 1) * 2 + 1];
                dist = sqrt(dx*dx + dy*dy);
                if (dist > 0) {
                    subdivisions = (int)ceil(dist / threshold) - 1;
                    dx /= subdivisions + 1;
                    dy /= subdivisions + 1;
                    for (int j = 0; j < subdivisions; j++) {
                        linestringDst[idx] = linestringDst[idx - 2] + dx;
                        linestringDst[idx + 1] = linestringDst[idx - 1] + dy;
                        idx += 2;
                    }
                }

                linestringDst[idx++] = linestringSrc[i * 2];
                linestringDst[idx++] = linestringSrc[i * 2 + 1];
            }
            
            tessellatedPoints = idx / 2;
        }

        return tessellatedPoints;
    }
}
