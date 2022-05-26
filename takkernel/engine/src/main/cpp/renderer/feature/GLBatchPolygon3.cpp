
#include "renderer/GL.h"

#include "feature/Style.h"
#include "feature/Polygon.h"

#include "renderer/GLBackground.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLTriangulate.h"
#include "renderer/GLTriangulate2.h"
#include "renderer/Tessellate.h"
#include "renderer/core/GLGlobeBase.h"
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
    fillColor(0)
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
            VertexData srcgeom;
            srcgeom.data = this->points.get();
            srcgeom.size = 3u;
            srcgeom.stride = 24u;

            Tessellate_polygon<double>(triangles.data, &triangles.count, srcgeom, numPoints, 0.0, Tessellate_CartesianAlgorithm());
        } else {
            triangles.count = 0u;
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
            VertexData srcgeom;
            srcgeom.data = this->points.get();
            srcgeom.size = 3u;
            srcgeom.stride = 24u;
            VertexDataPtr tessellated(nullptr, nullptr);
            Tessellate_polygon<double>(triangles.data, &triangles.count, srcgeom, numPoints, 0.0, Tessellate_CartesianAlgorithm());
        } else {
            triangles.count = 0u;
        }
    }

    return TE_Ok;
}

TAKErr GLBatchPolygon3::draw(const GLGlobeBase &view, const int render_pass, const int vertices_type) NOTHROWS
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

    if (this->fillColorA > 0.0f && triangles.count)
    {
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        const float *vp = v;
        GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, triangles.vertices.get());

        GLES20FixedPipeline::getInstance()->glColor4f(this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);

        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLES, 0, (int)triangles.count);

        glDisable(GL_BLEND);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    }
    if (!this->stroke.empty())
    {
        code = GLBatchLineString3::drawImpl(view, render_pass, v, size);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLBatchPolygon3::batchImpl(const GLGlobeBase &view, const int render_pass, GLRenderBatch2 &batch, const int vertices_type, const float *v) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (this->fillColorA > 0 && triangles.count)
    {
        code = batch.batch(-1, GL_TRIANGLES, triangles.count, 3u, 0, triangles.vertices.get(), 0, nullptr, this->fillColorR, this->fillColorG, this->fillColorB, this->fillColorA);
        TE_CHECKRETURN_CODE(code);
    }

    if (!this->stroke.empty())
    {
        GLBatchLineString3::batchImpl(view, render_pass, batch, vertices_type, v);
    }

    return TE_Ok;
}
TAKErr GLBatchPolygon3::projectVertices(const float** result, const GLGlobeBase& view, const int vertices_type) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = GLBatchLineString3::projectVertices(result, view, vertices_type);
    TE_CHECKRETURN_CODE(code);

    if (triangles.count) {
        triangles.vertices.reset(new float[triangles.count * 3u]);
        const double* trixyz = static_cast<const double*>(triangles.data->data);
        switch (vertices_type) {
        case GLGeometry::VERTICES_PIXEL :
            for (std::size_t i = 0u; i < triangles.count; i++) {
                TAK::Engine::Core::GeoPoint2 lla(trixyz[1u], trixyz[0u], trixyz[2u], TAK::Engine::Core::HAE);
                TAK::Engine::Math::Point2<double> xyz;
                view.renderPass->scene.forward(&xyz, lla);
                triangles.vertices[(i*3u)] = (float)xyz.x;
                triangles.vertices[(i*3u)+1u] = (float)xyz.y;
                triangles.vertices[(i*3u)+2u] = (float)xyz.z;
                trixyz += 3u;
            }
            break;
        case GLGeometry::VERTICES_PROJECTED :
            for (std::size_t i = 0u; i < triangles.count; i++) {
                TAK::Engine::Core::GeoPoint2 lla(trixyz[1u], trixyz[0u], trixyz[2u], TAK::Engine::Core::HAE);
                if (isnan(lla.altitude) || this->altitudeMode == TEAM_ClampToGround) {
                    lla.altitude = 0.0;
                } else if (lla.altitude > 0) {
                    if (this->altitudeMode == TEAM_Relative) {
                        // RWI - Seems like we should be adding the terrain elevation here, but doing so causes the lines to be elevated
                        // far above the ground when they shouldn't be
                        double terrain;
                        view.getTerrainMeshElevation(&terrain, lla.latitude, lla.longitude);
                        lla.altitude += terrain;
                    }
                    lla.altitude *= view.elevationScaleFactor;
                }
                TAK::Engine::Math::Point2<double> xyz;
                view.renderPass->scene.projection->forward(&xyz, lla);
                triangles.vertices[(i*3u)] = (float)xyz.x;
                triangles.vertices[(i*3u)+1u] = (float)xyz.y;
                triangles.vertices[(i*3u)+2u] = (float)xyz.z;
                trixyz += 3u;
            }
            break;
        default :
            return TE_InvalidArg;
        }
    } else {
        triangles.vertices.reset();
    }

    return code;
}
