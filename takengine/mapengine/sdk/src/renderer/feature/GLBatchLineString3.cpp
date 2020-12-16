
#include "renderer/GL.h"

#include "math/Ellipsoid.h"

#include "renderer/GLES20FixedPipeline.h"

#include "feature/Style.h"
#include "feature/LineString.h"

#include "renderer/feature/GLBatchGeometry3.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLGeometry.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
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

double GLBatchLineString3::cachedNominalUnitSize = 0.0;
double GLBatchLineString3::cachedNominalUnitSizeSrid = -1;

GLBatchLineString3::GLBatchLineString3(TAK::Engine::Core::RenderContext &surface) :
    GLBatchLineString3(surface, 1)
{}

GLBatchLineString3::GLBatchLineString3(TAK::Engine::Core::RenderContext &surface, int zOrder) :
    GLBatchGeometry3(surface, zOrder),
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
    stroke(1),
    drawVersion(-1),
    tessellationThreshold(0.0)
{}

TAKErr GLBatchLineString3::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchLineString3::setStyle(const std::shared_ptr<const atakmap::feature::Style>& value) NOTHROWS
{
    TAKErr code(TE_Ok);

    stroke.clear();
    if (value.get())
        code = pushStyle(*value);
    // if no style was derived, 
    if (stroke.empty())
        stroke.push_back(Stroke());
    return code;
}
TAKErr GLBatchLineString3::pushStyle(const atakmap::feature::Style& style) NOTHROWS
{
    switch (style.getClass()) {
        case atakmap::feature::TESC_BasicStrokeStyle :
        {
            const atakmap::feature::BasicStrokeStyle& basic = static_cast<const atakmap::feature::BasicStrokeStyle&>(style);
            stroke.push_back(Stroke(basic.getStrokeWidth(), basic.getColor()));
            break;
        }
        case atakmap::feature::TESC_PatternStrokeStyle :
        {
            const atakmap::feature::PatternStrokeStyle& pattern = static_cast<const atakmap::feature::PatternStrokeStyle&>(style);
            stroke.push_back(Stroke(pattern.getStrokeWidth(), pattern.getColor(), static_cast<GLsizei>(pattern.getFactor()), static_cast<GLuint>(pattern.getPattern())));
            break;
        }
        case atakmap::feature::TESC_CompositeStyle :
        {
            const atakmap::feature::CompositeStyle& composite = static_cast<const atakmap::feature::CompositeStyle&>(style);
            for (std::size_t i = 0u; i < composite.getStyleCount(); i++) {
                pushStyle(composite.getStyle(i));
            }
            break;
        }
    }

    return TE_Ok;
}

TAKErr GLBatchLineString3::setTessellationThreshold(double threshold) NOTHROWS
{
    this->tessellationThreshold = threshold;
    return TE_Ok;
}

double GLBatchLineString3::getTessellationThreshold() NOTHROWS
{
    return this->tessellationThreshold;
}

TAKErr GLBatchLineString3::setGeometryImpl(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return GLBatchGeometry3::setGeometry(std::move(blob), type, lod_val);
}

TAKErr GLBatchLineString3::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    return this->setGeometryImpl(std::move(blob), type, lod_val);
}

TAKErr GLBatchLineString3::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
{
    TAKErr code(TE_Ok);

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
    std::size_t maxPoints;
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

    this->numPoints = static_cast<std::size_t>(num_points);

    if (!this->points.get() || this->pointsLength < (this->numPoints * 3))
    {
        this->points.reset(new double[this->numPoints * 3]);
        this->pointsLength = this->numPoints * 3;
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

        this->points[0] = x;
        this->points[1] = y;
        this->points[2] = 0.0f;

        for (std::size_t i = 1; i < this->numPoints; i++)
        {
            code = blob->readDouble(&x);
            TE_CHECKBREAK_CODE(code);
            code = blob->readDouble(&y);
            TE_CHECKBREAK_CODE(code);
            this->points[i * 3] = x;
            this->points[i * 3 + 1] = y;
            this->points[i * 3 + 2] = 0.0f;

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
    else if (dim == 3 && !compressed)
    {
        code = blob->readDouble(&x);
        TE_CHECKRETURN_CODE(code);
        code = blob->readDouble(&y);
        TE_CHECKRETURN_CODE(code);
        double z;
        code = blob->readDouble(&z);
        TE_CHECKRETURN_CODE(code);

        mbb.minX = x;
        mbb.maxX = x;
        mbb.minY = y;
        mbb.maxY = y;

        this->points[0] = x;
        this->points[1] = y;
        this->points[2] = z;

        for (std::size_t i = 1; i < this->numPoints; i++)
        {
            code = blob->readDouble(&x);
            TE_CHECKBREAK_CODE(code);
            code = blob->readDouble(&y);
            TE_CHECKBREAK_CODE(code);
            code = blob->readDouble(&z);
            TE_CHECKBREAK_CODE(code);
            this->points[i * 3] = x;
            this->points[i * 3 + 1] = y;
            this->points[i * 3 + 2] = z;

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

        this->points[0] = x;
        this->points[1] = y;
        this->points[2] = 0.0f;

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

                points[i * 3] = x;
                points[i * 3 + 1] = y;
                points[i * 3 + 2] = 0.0f;

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

                points[i * 3] = x;
                points[i * 3 + 1] = y;
                points[i * 3 + 2] = 0.0f;

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
        double *pointsPtr = this->points.get();

        const size_t tessellatedPoints = tessellate<double>(nullptr, pointsPtr, this->numPoints, this->tessellationThreshold);
        if (tessellatedPoints != this->numPoints) {
            array_ptr<double> npoints(new double[tessellatedPoints * 3]);

            this->numPoints = tessellate<double>(npoints.get(), pointsPtr, this->numPoints, this->tessellationThreshold);
            this->points.reset(npoints.release());
            this->pointsLength = (tessellatedPoints * 3);


        }
    }

    // force reprojection
    this->projectedVerticesSrid = -1;

    return code;
}

TAKErr GLBatchLineString3::setGeometry(const atakmap::feature::LineString &linestring) NOTHROWS
{
    return GLBatchGeometry3::setGeometry(linestring);
}

TAKErr GLBatchLineString3::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (geom.getType() != TEGC_LineString)
        return TE_InvalidArg;

    const auto &linestring = static_cast<const LineString &>(geom);

    this->numPoints = linestring.getPointCount();

    bool is3d = linestring.getDimension() == atakmap::feature::Geometry::Dimension::_3D;

    if (this->points.get() == nullptr || this->pointsLength < (this->numPoints * 3)) {
        this->points.reset(new double[this->numPoints * 3]);
        this->pointsLength = (this->numPoints * 3);
    }

    try {
        double x = linestring.getX(0);
        double y = linestring.getY(0);
        double z = 0.0;
        if (is3d) z = linestring.getZ(0);

        this->mbb.minX = x;
        this->mbb.maxX = x;
        this->mbb.minY = y;
        this->mbb.maxY = y;

        this->points[0] = x;
        this->points[1] = y;
        this->points[2] = z;

        for (std::size_t i = 1; i < this->numPoints; i++) {
            x = linestring.getX(i);
            y = linestring.getY(i);
            z = is3d ? linestring.getZ(i) : 0.0;

            this->points[i * 3] = x;
            this->points[i * 3 + 1] = y;
            this->points[i * 3 + 2] = z;

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
            double *pointsPtr = this->points.get();

            const size_t tessellatedPoints = tessellate<double>(nullptr, pointsPtr, this->numPoints, this->tessellationThreshold);
            if (tessellatedPoints != this->numPoints) {
                array_ptr<double> npoints(new double[tessellatedPoints * 3]);

                this->numPoints =
                    tessellate<double>(npoints.get(), pointsPtr, this->numPoints, this->tessellationThreshold);
                this->points.reset(npoints.release());
                this->pointsLength = (tessellatedPoints * 3);
            }
        }
    } catch (std::out_of_range &e) {
        Util::Logger_log(Util::LogLevel::TELL_Error, "Error setting line geometry: %s", e.what());
        code = TE_Err;
    }

    // force reprojection
    this->projectedVerticesSrid = -1;

    return code;
}

TAKErr GLBatchLineString3::projectVertices(const float **result, const GLMapView2 &view, const int vertices_type) NOTHROWS
{
    if (this->points.get() == nullptr || this->numPoints < 2)
    {
        return TE_Ok;
    }

    // allocate vertex buffers if necessary
    if (this->verticesLength < (this->numPoints * 2)) {
        this->vertices.reset(new float[this->numPoints * 2]);
        this->verticesLength = (this->numPoints * 2);
    }
    if (this->projectedVerticesLength < (this->numPoints * 3)) {
        this->projectedVertices.reset(new float[this->numPoints * 3]);
        this->projectedVerticesLength = (this->numPoints * 3);
    }

    // project the vertices
    switch (vertices_type)
    {
        case GLGeometry::VERTICES_PIXEL:
            view.forward(this->vertices.get(), 2u, this->points.get(), 3u, this->numPoints);
            *result = this->vertices.get();
            if (this->drawVersion != view.drawVersion) {
                this->screen_mbb.minX = this->vertices.get()[0];
                this->screen_mbb.minY = this->vertices.get()[1];
                this->screen_mbb.maxX = this->vertices.get()[0];
                this->screen_mbb.maxY = this->vertices.get()[1];
                for (std::size_t i = 1u; i < this->numPoints; i++) {
                    double x = this->vertices.get()[i * 2];
                    double y = this->vertices.get()[i * 2 + 1];
                    if (x < this->screen_mbb.minX)
                        this->screen_mbb.minX = x;
                    else if (x > this->screen_mbb.maxX)
                        this->screen_mbb.maxX = x;
                    if (y < this->screen_mbb.minY)
                        this->screen_mbb.minY = y;
                    else if (y > this->screen_mbb.maxY)
                        this->screen_mbb.maxY = y;
                }
                this->drawVersion = view.drawVersion;
            }
            return TE_Ok;
        case GLGeometry::VERTICES_PROJECTED:
            if (view.drawSrid != this->projectedVerticesSrid)
            {
                for (std::size_t i = 0u; i < this->numPoints; i++)
                {
                    GeoPoint2 scratchGeo;
                    scratchGeo.latitude = this->points.get()[i * 3 + 1];
                    scratchGeo.longitude = this->points.get()[i * 3];
                    double alt = this->points.get()[i * 3 + 2];
                    if (isnan(alt) || this->altitudeMode == TEAM_ClampToGround) {
                        alt = 0.0;
                    } else if (alt > 0) {
                        if (this->altitudeMode == TEAM_Relative) {
                            // RWI - Seems like we should be adding the terrain elevation here, but doing so causes the lines to be elevated
                            // far above the ground when they shouldn't be
                            double terrain;
                            view.getTerrainMeshElevation(&terrain, scratchGeo.latitude, scratchGeo.longitude);
                            alt += terrain;
                        }
                        scratchGeo.altitude = alt * view.elevationScaleFactor;
                    }
                    TAK::Engine::Math::Point2<double> scratchPointD;
                    view.scene.projection->forward(&scratchPointD, scratchGeo);
                    this->projectedVertices.get()[i * 3u] = (float)(scratchPointD.x-projectedCentroid.x);
                    this->projectedVertices.get()[i * 3u + 1] = (float)(scratchPointD.y-projectedCentroid.y);
                    this->projectedVertices.get()[i * 3u + 2] = (float)(scratchPointD.z-projectedCentroid.z);
                }
                this->projectedVerticesSrid = view.drawSrid;
                this->projectedVerticesSize = 3u;

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
                    GeoPoint2 scratchGeo;
                    scratchGeo.latitude = 0;
                    scratchGeo.longitude = view.scene.projection->getMaxLongitude();

                    TAK::Engine::Math::Point2<double> scratchPointD;
                    view.scene.projection->forward(&scratchPointD, scratchGeo);
                    double projMaxX = scratchPointD.x;

                    scratchGeo.latitude = 0;
                    scratchGeo.longitude = view.scene.projection->getMinLongitude();
                    view.scene.projection->forward(&scratchPointD, scratchGeo);
                    double projMinX = scratchPointD.x;

                    this->projectedNominalUnitSize = (6378137.0 * 2.0 * M_PI) / std::fabs(projMaxX - projMinX);
                    cachedNominalUnitSize = this->projectedVerticesSrid;
                }
            }
            *result = this->projectedVertices.get();
            if (this->drawVersion != view.drawVersion && this->numPoints) {
                TAK::Engine::Math::Point2<double> xyz;

                xyz.x = this->projectedVertices[0u]+this->projectedCentroid.x;
                xyz.y = this->projectedVertices[1u]+this->projectedCentroid.y;
                xyz.z = this->projectedVertices[2u]+this->projectedCentroid.z;
                view.scene.forwardTransform.transform(&xyz, xyz);
                this->vertices[0] = (float)xyz.x;
                this->vertices[1] = (float)xyz.y;
                this->screen_mbb.minX = this->vertices[0];
                this->screen_mbb.minY = this->vertices[1];
                this->screen_mbb.maxX = this->vertices[0];
                this->screen_mbb.maxY = this->vertices[1];
                for (std::size_t i = 1u; i < this->numPoints; i++) {
                    xyz.x = this->projectedVertices[i*3u]+this->projectedCentroid.x;
                    xyz.y = this->projectedVertices[i*3u+1u]+this->projectedCentroid.y;
                    xyz.z = this->projectedVertices[i*3u+2u]+this->projectedCentroid.z;
                    view.scene.forwardTransform.transform(&xyz, xyz);
                    this->vertices.get()[i * 2] = (float)xyz.x;
                    this->vertices.get()[i * 2 + 1] = (float)xyz.y;

                    double x = this->vertices[i * 2];
                    double y = this->vertices[i * 2 + 1];
                    if (x < this->screen_mbb.minX)
                        this->screen_mbb.minX = x;
                    else if (x > this->screen_mbb.maxX)
                        this->screen_mbb.maxX = x;
                    if (y < this->screen_mbb.minY)
                        this->screen_mbb.minY = y;
                    else if (y > this->screen_mbb.maxY)
                        this->screen_mbb.maxY = y;
                }
                this->drawVersion = view.drawVersion;
            }
            return TE_Ok;
        default :
            return TE_InvalidArg;
    }
}

void GLBatchLineString3::draw(const GLMapView2 &view, const int render_pass) NOTHROWS
{
    this->draw(view, render_pass, GLGeometry::VERTICES_PIXEL);
}

TAKErr GLBatchLineString3::draw(const GLMapView2 &view, const int render_pass, const int vertices_type) NOTHROWS
{
    TAKErr code;

    const float *v;
    code = this->projectVertices(&v, view, vertices_type);
    TE_CHECKRETURN_CODE(code);
    if (v == nullptr)
    {
        return TE_Ok;
    }

    return this->drawImpl(view, render_pass, v, (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2);
}

TAKErr GLBatchLineString3::drawImpl(const GLMapView2 &view, const int render_pass, const float *v, int size) NOTHROWS
{
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float *buf = v;
    GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, buf);
    for (std::size_t i = 0u; i < stroke.size(); i++) {
        GLES20FixedPipeline::getInstance()->glColor4f(stroke[i].color.r, stroke[i].color.g, stroke[i].color.b, stroke[i].color.a);

        glLineWidth(stroke[i].width);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, static_cast<int>(this->numPoints));
    }

    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
}

void GLBatchLineString3::release() NOTHROWS
{
    this->vertices.reset();
    this->verticesLength = 0;
    this->projectedVertices.reset();
    this->projectedVerticesLength = 0;
    this->projectedVerticesSrid = -1;
}

TAKErr GLBatchLineString3::batch(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch) NOTHROWS
{
    TAKErr code(TE_Ok);
    if(this->numPoints > 1)
        code = this->batch(view, render_pass, batch, GLGeometry::VERTICES_PIXEL);
    return code;
}

TAKErr GLBatchLineString3::batch(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch, const int vertices_type) NOTHROWS
{
    TAKErr code;

    const float *v;
    code = this->projectVertices(&v, view, vertices_type);
    TE_CHECKRETURN_CODE(code);
    if (v == nullptr)
    {
        return TE_Ok;
    }

    return this->batchImpl(view, render_pass, batch, vertices_type, v);
}

TAKErr GLBatchLineString3::batchImpl(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch, const int vertices_type, const float *v) NOTHROWS
{
    float strokeFactor;
    switch (vertices_type)
    {
        case GLGeometry::VERTICES_PIXEL :
            strokeFactor = 1.0f;
            break;
        case GLGeometry::VERTICES_PROJECTED :
            strokeFactor = (float)(view.drawMapResolution / this->projectedNominalUnitSize);
            break;
        default :
            return TE_InvalidArg;
    }

    const std::size_t size = (vertices_type == GLGeometry::VERTICES_PROJECTED) ? this->projectedVerticesSize : 2u;

    // XXX - texturing
    for (std::size_t i = 0u; i < stroke.size(); i++) {
        batch.setLineWidth(stroke[i].width);
        batch.batch(-1, GL_LINE_STRIP, this->numPoints, size, 0, v, 0, nullptr, stroke[i].color.r, stroke[i].color.g, stroke[i].color.b, stroke[i].color.a);
    }
    return TE_Ok;
}

GLBatchLineString3::Stroke::Stroke() NOTHROWS :
    Stroke(1.0f, 0xFFFFFFFFu, 0u, 0u)
{}
GLBatchLineString3::Stroke::Stroke(const float width, const unsigned int color) NOTHROWS :
    Stroke(width, color, 0u, 0u)
{}
GLBatchLineString3::Stroke::Stroke(const float width_, const unsigned int color_, const GLsizei factor_, const GLushort pattern_) NOTHROWS :
    width(width_),
    pattern(pattern_),
    factor(factor_)
{
    color.r = (float)((color_ & 0x00FF0000) >> 16u) / 255.0f;
    color.g = (float)((color_ & 0x0000FF00) >> 8u) / 255.0f;
    color.b = (float) (color_ & 0x000000FF) / 255.0f;
    color.a = (float)((color_ & 0xFF000000) >> 24u) / 255.0f;
    color.argb = color_;
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
        T lastZ = linestringSrc[2];

        T dist;
        T dx;
        T dy;
        T dz;
        for (size_t i = 1; i < numPoints; i++) {
            dx = linestringSrc[i * 3] - lastX;
            dy = linestringSrc[i * 3 + 1] - lastY;
            dz = linestringSrc[i * 3 + 2] - lastZ;
            dist = sqrt(dx*dx + dy*dy + dz*dz);
            if (dist > 0)
                tessellatedPoints += (int)ceil(dist / threshold) - 1;
            lastX = linestringSrc[i * 3];
            lastY = linestringSrc[i * 3 + 1];
            lastZ = linestringSrc[i * 3 + 2];
        }

        if (numPoints != tessellatedPoints && linestringDst) {
            size_t idx = 0;

            linestringDst[idx++] = linestringSrc[0];
            linestringDst[idx++] = linestringSrc[1];
            linestringDst[idx++] = linestringSrc[2];

            int subdivisions;
            for (std::size_t i = 1; i < numPoints; i++) {
                dx = linestringSrc[i * 3] - linestringSrc[(i - 1) * 3];
                dy = linestringSrc[i * 3 + 1] - linestringSrc[(i - 1) * 3 + 1];
                dz = linestringSrc[i * 3 + 2] - linestringSrc[(i - 1) * 3 + 2];
                dist = sqrt(dx*dx + dy*dy + dz*dz);
                if (dist > 0) {
                    subdivisions = (int)ceil(dist / threshold) - 1;
                    dx /= subdivisions + 1;
                    dy /= subdivisions + 1;
                    dz /= subdivisions + 1;
                    for (int j = 0; j < subdivisions; j++) {
                        linestringDst[idx] = linestringDst[idx - 3] + dx;
                        linestringDst[idx + 1] = linestringDst[idx - 2] + dy;
                        linestringDst[idx + 2] = linestringDst[idx - 1] + dz;
                        idx += 2;
                    }
                }

                linestringDst[idx++] = linestringSrc[i * 3];
                linestringDst[idx++] = linestringSrc[i * 3 + 1];
                linestringDst[idx++] = linestringSrc[i * 3 + 2];
            }
            
            tessellatedPoints = idx / 3;
        }

        return tessellatedPoints;
    }
}
