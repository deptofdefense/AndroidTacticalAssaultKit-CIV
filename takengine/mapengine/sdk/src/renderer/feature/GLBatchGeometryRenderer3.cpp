#include "renderer/GL.h"

#include "renderer/feature/GLBatchGeometryRenderer3.h"

#include <cassert>
#include <math.h>

#include "feature/AltitudeMode.h"
#include "feature/Envelope.h"
#include "feature/Feature2.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Point2.h"
#include "math/Point2.h"

#include "core/AtakMapView.h"

#include "port/STLSetAdapter.h"
#include "renderer/GLSLUtil.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/core/GLLabelManager.h"
#include "renderer/feature/GLBatchGeometryRenderer3.h"
#include "renderer/feature/GLBatchGeometryCollection3.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/feature/GLGeometry.h"
#include "renderer/GLMatrix.h"
#include "renderer/map/GLMapView.h"
#include "util/ConfigOptions.h"
#include "util/Distance.h"
#include "util/Logging.h"

#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Math;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;
using namespace atakmap::util::distance;


#define POINT_VSH \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "uniform float uPointSize;\n" \
    "uniform float uTexSize;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "attribute vec2 aTextureCoords;\n" \
    "varying vec2 vTexPos;\n" \
    "varying float vPointTexSize;\n" \
    "void main() {\n" \
    "  gl_PointSize = uPointSize;\n" \
    "  vPointTexSize = uPointSize / uTexSize;\n" \
    "  vTexPos = aTextureCoords;\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" \
    "}"

#define POINT_FSH \
    "precision mediump float;\n" \
    "uniform sampler2D uTexture;\n" \
    "uniform vec4 uColor;\n" \
    "varying vec2 vTexPos;\n" \
    "varying float vPointTexSize;\n" \
    "void main(void) {\n" \
    "  vec2 atlasTexPos = vTexPos + (gl_PointCoord * vPointTexSize);\n" \
    "  gl_FragColor = uColor * texture2D(uTexture, atlasTexPos);\n" \
    "}"

#define LINE_VSH \
    "#version 300 es\n" \
    "precision highp float;\n" \
    "const float c_smoothBuffer = 2.0;\n" \
    "uniform mat4 u_mvp;\n" \
    "uniform mediump vec2 u_viewportSize;\n" \
    "in vec3 a_vertexCoord0;\n" \
    "in vec3 a_vertexCoord1;\n" \
    "in vec2 a_texCoord;\n" \
    "in vec4 a_color;\n" \
    "in float a_normal;\n" \
    "in float a_dir;\n" \
    "in int a_pattern;\n" \
    "in int a_factor;\n" \
    "in float a_halfStrokeWidth;\n" \
    "out vec4 v_color;\n" \
    "flat out float f_dist;\n" \
    "out float v_mix;\n" \
    "flat out int f_pattern;\n" \
    "out vec2 v_normal;\n" \
    "flat out float f_halfStrokeWidth;\n" \
    "flat out int f_factor;\n" \
    "void main(void) {\n" \
    "  gl_Position = u_mvp * vec4(a_vertexCoord0.xyz, 1.0);\n" \
    "  vec4 next_gl_Position = u_mvp * vec4(a_vertexCoord1.xyz, 1.0);\n" \
    "  vec4 p0 = (gl_Position / gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);\n" \
    "  vec4 p1 = (next_gl_Position / next_gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);\n" \
    "  v_mix = a_dir;\n" \
    "  float dist = distance(p0.xy, p1.xy);\n" \
    "  float dx = p1.x - p0.x;\n" \
    "  float dy = p1.y - p0.y;\n" \
    "  float normalDir = (2.0*a_normal) - 1.0;\n" \
    "  float adjX = normalDir*(dx/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.y);\n" \
    "  float adjY = normalDir*(dy/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.x);\n" \
    "  gl_Position.x = gl_Position.x - adjY;\n" \
    "  gl_Position.y = gl_Position.y + adjX;\n" \
    "  v_color = a_color;\n" \
    "  v_normal = vec2(-normalDir*(dy/dist)*(a_halfStrokeWidth+c_smoothBuffer), normalDir*(dx/dist)*(a_halfStrokeWidth+c_smoothBuffer));\n" \
    "  f_pattern = a_pattern;\n" \
    "  f_factor = a_factor;\n" \
    "  f_dist = dist;\n" \
    "  f_halfStrokeWidth = a_halfStrokeWidth;\n" \
    "}"

#define LINE_FSH \
    "#version 300 es\n" \
    "precision mediump float;\n" \
    "uniform mediump vec2 u_viewportSize;\n" \
    "in vec4 v_color;\n" \
    "in float v_mix;\n" \
    "flat in int f_pattern;\n" \
    "flat in int f_factor;\n" \
    "flat in float f_dist;\n" \
    "in vec2 v_normal;\n" \
    "flat in float f_halfStrokeWidth;\n" \
    "out vec4 v_FragColor;\n" \
    "void main(void) {\n" \
    "  float d = (f_dist*v_mix);\n" \
    "  int idist = int(d);\n" \
    "  float b0 = float((f_pattern>>((idist/f_factor)%16))&0x1);\n" \
    "  float b1 = float((f_pattern>>(((idist+1)/f_factor)%16))&0x1);\n" \
    "  float alpha = mix(b0, b1, fract(d));\n" \
    "  float antiAlias = smoothstep(-1.0, 0.25, f_halfStrokeWidth-length(v_normal));\n" \
    "  v_FragColor = vec4(v_color.rgb, antiAlias*alpha);\n" \
    "}"

#define PRE_FORWARD_LINES_POINT_RATIO_THRESHOLD 3

#define MAX_VERTS_PER_DRAW_ARRAYS 5000

#define POINT_BATCHING_THRESHOLD 500

#define POINT_VERTEX_SIZE 20u // { float x, float y, float z, float u, float v }
#define POINT_VERTICES_PER_SPRITE 6u

#ifdef MSVC
#define FORCE_LINEWIDTH_EMULATION 1
#else
#define FORCE_LINEWIDTH_EMULATION 0
#endif

#define POINT_SPRITE_VBOS_ENABLED 1

/*
 {
     float x0; // 12
     float y0;
     float z0;
     float x1; // 12
     float y1;
     float z1;
     uint8_t r; // 4
     uint8_t g;
     uint8_t b;
     uint8_t a;
     uint8_t normalDir; // 1
     uint8_t halfWidthPixels; // 1
     uint32_t pattern; // 4
     uint8_t patternLen; // 1
     uint8_t dir; // 1
 }
 */
#define LINES_VERTEX_SIZE (12u+12u+4u+1u+1u+4u+1u+1u)

namespace
{
    template<class Iter>
    TAKErr hitTestPoints(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double resolution, const int radius, int64_t noid) NOTHROWS;

    template<class Iter>
    TAKErr hitTestSurfaceLineStrings(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS;

    template<class Iter>
    TAKErr hitTestSpriteLineStrings(int64_t *result, Iter &iter, const Iter &end, const double screen_x, const double screen_y, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS;

    /**
     * Tests for intersection against a linestring geometry
     *
     * @param value         Returns 'true' if intersection detected
     * @param linestring    The linestring geometry, in x,y (longitude,latitude) ordered pairs
     * @param numPoints     The number of points in the linestring
     * @param mbr           The minimum bounding box of the linestring
     * @param loc           The hit-test point
     * @param radius        The hit-test radius
     * @param test          A bounding box approximating the hit-test radius
     */
    template<class T>
    TAKErr testOrthoHit(bool *value, const T *linestring, const int numPoints, const Envelope &mbr, const Point &loc, const double radius, const Envelope &test) NOTHROWS;

    TAKErr testScreenHit(bool* value, const float *linestring, const int numPoints, const Envelope &mbr, const double screen_x, const double screen_y, const double radius, const Envelope &test) NOTHROWS;

    /**
     * Tests the intersection of the specified line against the specified point.
     *
     * @param value     Returns 'true' if intersection detected
     * @param x1        The x-coordinte of the segment start point
     * @param y1        The y-coordinte of the segment start point
     * @param x2        The x-coordinte of the segment end point
     * @param y2        The y-coordinte of the segment end point
     * @param x3        The x-coordinte of the test point
     * @param y3        The y-coordinte of the test point
     * @param radius    The hit-test radius, in meters
     * @param test      A bounding box approximating the hit-test radius
     */
    TAKErr isectOrthoTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS;

    /**
     * Tests the intersection of the specified line against the specified point.
     *
     * @param value     Returns 'true' if intersection detected
     * @param x1        The x-coordinte of the segment start point
     * @param y1        The y-coordinte of the segment start point
     * @param x2        The x-coordinte of the segment end point
     * @param y2        The y-coordinte of the segment end point
     * @param x3        The x-coordinte of the test point
     * @param y3        The y-coordinte of the test point
     * @param radius    The hit-test radius, in meters
     * @param test      A bounding box approximating the hit-test radius
     */
    TAKErr isectScreenTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS;
    /**
     * Expands a buffer containing a line strip into a buffer containing lines.
     * None of the properties of the specified buffers (e.g. position, limit)
     * are modified as a result of this method.
     *
     * @param size              The vertex size, in number of elements
     * @param linestrip         The pointer to the base of the line strip buffer
     * @param linestripPosition The position of the linestrip buffer (should
     *                          always be <code>linestrip.position()</code>). </param>
     * @param lines             The pointer to the base of the destination
     *                          buffer for the lines </param>
     * @param linesPosition     The position of the lines buffer (should always
     *                          be <code>lines.position()</code>). </param>
     * @param count             The number of points in the line string to be
     *                          consumed. </param>
     */
    void expandLineStringToLines(const std::size_t size, const float *linestrip , const std::size_t linestripPosition, float *lines, const std::size_t linesPosition, const std::size_t count, Matrix2 *xform) NOTHROWS;
}

GLBatchGeometryRenderer3::GLBatchGeometryRenderer3(const CachePolicy &cachePolicy_) NOTHROWS :
    cachePolicy(cachePolicy_),
    labelBackgrounds(true),
    fadingLabelsCount(0),
    drawResolution(50.0),
    batchTerrainVersion(-1),
    rebuildBatchBuffers(0)
{
    Port::String opt;
    TAKErr code;

    code = ConfigOptions_getOption(opt, "default-label-background");
    if (code == TE_Ok)
        labelBackgrounds = !!atoi(opt);

    code = ConfigOptions_getOption(opt, "default-labels-fading-count");
    if (code == TE_Ok)
        fadingLabelsCount = atoi(opt);
    
    code = ConfigOptions_getOption(opt, "default-label-draw-resolution");
    if (code == TE_Ok)
        drawResolution = atof(opt);

    pointShader.base.handle = 0u;
    lineShader.base.handle = 0u;
}

GLBatchGeometryRenderer3::GLBatchGeometryRenderer3() NOTHROWS :
    labelBackgrounds(true),
    fadingLabelsCount(0),
    drawResolution(50.0),
    batchTerrainVersion(-1),
    rebuildBatchBuffers(0)
{
    Port::String opt;
    TAKErr code;
    
    code = ConfigOptions_getOption(opt, "default-label-background");
    if (code == TE_Ok)
        labelBackgrounds = !!atoi(opt);

    code = ConfigOptions_getOption(opt, "default-labels-fading-count");
    if (code == TE_Ok)
        fadingLabelsCount = atoi(opt);

    pointShader.base.handle = 0u;
    lineShader.base.handle = 0u;
}

TAKErr GLBatchGeometryRenderer3::hitTest(int64_t *result, const Point &loc, const double screen_x, const double screen_y, const double thresholdMeters, const int64_t noid) const NOTHROWS
{
    TAKErr code;
    std::vector<int64_t> fid;
    STLVectorAdapter<int64_t> fidAdapter(fid);
    code = this->hitTest2(fidAdapter, loc, screen_x, screen_y, thresholdMeters, 1, 1, noid);
    TE_CHECKRETURN_CODE(code);
    if (fid.empty()) {
        *result = noid;
        return code;
    }
    *result = fid[0];
    return code;
}

TAKErr GLBatchGeometryRenderer3::hitTest2(Collection<int64_t> &fids, const Point &loc,  const double screen_x, const double screen_y,
    const double resolution, const int radius, const int limit, const int64_t noid) const NOTHROWS
{
    TAKErr code(TE_Ok);

    const double thresholdMeters = radius*resolution;

    const double rlat = (loc.y * M_PI / 180.0);
    const double metersDegLat = 111132.92 - 559.82 * cos(2 * rlat) + 1.175*cos(4 * rlat);
    const double metersDegLng = 111412.84 * cos(rlat) - 93.5 * cos(3 * rlat);

    const double ra = thresholdMeters / metersDegLat;
    const double ro = thresholdMeters / metersDegLng;

    const Envelope screen_hit_box(screen_x - radius, screen_y - radius, NAN, screen_x + radius, screen_y + radius, NAN);
    const Envelope hitBox(loc.x - ro, loc.y - ra, NAN, loc.x + ro, loc.y + ra, NAN);

    {
#ifdef __APPLE__
        auto pointIter = this->labels.rbegin();
        do {
            int64_t fid;
            code = hitTestPoints<std::list<GLBatchPoint3 *>::const_reverse_iterator>(&fid, pointIter, this->labels.rend(), loc, resolution, radius, noid);
            TE_CHECKBREAK_CODE(code);
            if (fid != noid) {
                fids.add(fid);
                if (fids.size() == limit)
                    return code;
            }
            else {
                // no hit was detected, break
                break;
            }
        } while (true);
#else
        GLLabelManager *labelManager = nullptr;
        for (auto pointIter = this->labels.rbegin(); pointIter != this->labels.rend(); pointIter++) {
            GLBatchPoint3 *item = *pointIter;
            Envelope labelHitBox = hitBox;
            if (item->labelId != GLLabelManager::NO_ID) {
                if (labelManager == nullptr) {
                    code = GLMapRenderGlobals_getLabelManager(&labelManager, item->surface);
                    TE_CHECKBREAK_CODE(code);
                }
                if (labelManager != nullptr) {
                    atakmap::math::Rectangle<double> labelRect;
                    labelManager->getSize(item->labelId, labelRect);

                    if (atakmap::math::Rectangle<double>::contains(labelRect.x, labelRect.y, labelRect.x + labelRect.width, labelRect.y + labelRect.height, screen_x, screen_y)) {
                        code = fids.add(item->featureId);
                        TE_CHECKBREAK_CODE(code);
                        if (fids.size() == limit) 
                            return code;
                        continue;
                    }
                }
            }
        }
#endif
        TE_CHECKRETURN_CODE(code);
    }

    {
#ifdef __APPLE__
        auto pointIter = this->batchPoints.rbegin();
        do {
            int64_t fid;
            code = hitTestPoints(&fid, pointIter, this->batchPoints.rend(), loc, resolution, radius, noid);
            TE_CHECKBREAK_CODE(code);
            if (fid != noid) {
                fids.add(fid);
                if (fids.size() == limit)
                    return code;
            }
            else {
                // not hit was detected, break
                break;
            }
        } while (true);
#else
        for (auto pointIter = this->batchPoints.rbegin(); pointIter != this->batchPoints.rend(); pointIter++) {
            GLBatchPoint3 *item = *pointIter;
            Envelope iconHitBox = screen_hit_box;
            double iconSize = 0;
            if (item->textureId) {
                //XXX-- density issue and make solution for all plats
                size_t iconW, iconH;
                item->getIconSize(&iconW, &iconH);
                iconSize = (double)(std::max(iconW, iconH) / 2.0);

                iconHitBox.minX = screen_x - iconSize;
                iconHitBox.minY = screen_y - iconSize;
                iconHitBox.maxX = screen_x + iconSize;
                iconHitBox.maxY = screen_y + iconSize;
            }
            if (atakmap::math::Rectangle<double>::contains(iconHitBox.minX, iconHitBox.minY,
                                                           iconHitBox.maxX, iconHitBox.maxY,
                                                           item->screen_x, item->screen_y)) {

                code = fids.add(item->featureId);
                TE_CHECKBREAK_CODE(code);
                if (fids.size() == limit)
                    return code;
            }
        }
#endif
        TE_CHECKRETURN_CODE(code);

        for (auto drawPointIter = this->draw_points_.rbegin(); drawPointIter != this->draw_points_.rend(); drawPointIter++) {
            GLBatchPoint3 *item = *drawPointIter;
            Envelope iconHitBox = screen_hit_box;
            double iconSize = 0;
            if (item->textureId) {
                // XXX-- density issue and make solution for all plats
                size_t iconW, iconH;
                item->getIconSize(&iconW, &iconH);
                iconSize = (double)(std::max(iconW, iconH) / 2.0);

                iconHitBox.minX = screen_x - iconSize;
                iconHitBox.minY = screen_y - iconSize;
                iconHitBox.maxX = screen_x + iconSize;
                iconHitBox.maxY = screen_y + iconSize;
            }
            if (atakmap::math::Rectangle<double>::contains(iconHitBox.minX, iconHitBox.minY, iconHitBox.maxX, iconHitBox.maxY,
                                                           item->screen_x, item->screen_y)) {
                code = fids.add(item->featureId);
                TE_CHECKBREAK_CODE(code);
                if (fids.size() == limit) return code;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    
    auto surLineIter = this->surfaceLines.rbegin();
    do {
        int64_t fid;
        code = hitTestSurfaceLineStrings<std::list<GLBatchLineString3 *>::const_reverse_iterator>(&fid, surLineIter, this->surfaceLines.rend(),
                                                                                           loc,
                                                                                           thresholdMeters, hitBox, noid);
        TE_CHECKBREAK_CODE(code);
        if (fid != noid) {
            fids.add(fid);
            if (fids.size() == limit)
                return code;
        } else {
            // no hit was detected
            break;
        }
    } while (true);
    TE_CHECKRETURN_CODE(code);

    auto sprLineIter = this->spriteLines.rbegin();
    do {
        int64_t fid;
        code = hitTestSpriteLineStrings<std::list<GLBatchLineString3 *>::const_reverse_iterator>(&fid, sprLineIter, this->spriteLines.rend(),
                                                                                           screen_x, screen_y, radius, screen_hit_box, noid);
        TE_CHECKBREAK_CODE(code);
        if (fid != noid) {
            fids.add(fid);
            if (fids.size() == limit) return code;
        } else {
            // no hit was detected
            break;
        }
    } while (true);
    TE_CHECKRETURN_CODE(code);

    auto surPolyIter = this->surfacePolys.rbegin();
    do {
        int64_t fid;
        code = hitTestSurfaceLineStrings<std::list<GLBatchPolygon3 *>::const_reverse_iterator>(&fid, surPolyIter, this->surfacePolys.rend(), loc,
                                                                                        thresholdMeters, hitBox, noid);
        TE_CHECKBREAK_CODE(code);
        if (fid != noid) {
            fids.add(fid);
            if (fids.size() == limit)
                return code;
        } else {
            // no hit was detected, break
            break;
        }
    } while (true);
    TE_CHECKRETURN_CODE(code);

    auto sprPolyIter = this->spritePolys.rbegin();
    do {
        int64_t fid;
        code = hitTestSpriteLineStrings<std::list<GLBatchPolygon3 *>::const_reverse_iterator>(&fid, sprPolyIter, this->spritePolys.rend(), screen_x, screen_y, radius, screen_hit_box, noid);
        TE_CHECKBREAK_CODE(code);
        if (fid != noid) {
            fids.add(fid);
            if (fids.size() == limit) return code;
        } else {
            // no hit was detected, break
            break;
        }
    } while (true);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLBatchGeometryRenderer3::setBatch(Collection<GLBatchGeometry3 *> &value) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    geoms.clear();

    sortedPolys.clear();
    sortedLines.clear();

    surfacePolys.clear();
    spritePolys.clear();
    surfaceLines.clear();
    spriteLines.clear();
    pointLollipops.clear();

    loadingPoints.clear();
    batchPoints.clear();
    draw_points_.clear();
    labels.clear();

    if (!value.empty()) {
        Collection<GLBatchGeometry3 *>::IteratorPtr valueIter(nullptr, nullptr);
        code = value.iterator(valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->fillBatchLists(*valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->createLabels();
        TE_CHECKRETURN_CODE(code);
        code = this->extrudePoints();
        TE_CHECKRETURN_CODE(code);
    }

    // sort points
    {
        BatchPointComparator cmp;
        std::sort(this->batchPoints.begin(), this->batchPoints.end(), cmp);
    }

    std::set<GLBatchGeometry3 *, FidComparator>::iterator iter;

    for (iter = this->sortedLines.begin(); iter != this->sortedLines.end(); iter++) {
        auto *line = static_cast<GLBatchLineString3 *>(*iter);
        if (line->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
            surfaceLines.push_back(line);
        else
            spriteLines.push_back(line);
    }
    this->sortedLines.clear();

    for (iter = this->sortedPolys.begin(); iter != this->sortedPolys.end(); iter++) {
        auto *poly = static_cast<GLBatchPolygon3 *>(*iter);
        if (poly->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
            surfacePolys.push_back(poly);
        else
            spritePolys.push_back(poly);
    }
    this->sortedPolys.clear();

    batchState.sprites.srid = -1;
    batchState.surface.srid = -1;

    return code;
}

TAKErr GLBatchGeometryRenderer3::setBatch(Collection<SharedGLBatchGeometryPtr> &value) NOTHROWS
{
    TAKErr code(TE_Ok);

    geoms.clear();

    sortedPolys.clear();
    sortedLines.clear();

    surfacePolys.clear();
    spritePolys.clear();
    surfaceLines.clear();
    spriteLines.clear();
    pointLollipops.clear();

    loadingPoints.clear();
    batchPoints.clear();
    draw_points_.clear();
    labels.clear();

    if (!value.empty()) {
        Collection<SharedGLBatchGeometryPtr>::IteratorPtr valueIter(nullptr, nullptr);
        code = value.iterator(valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->fillBatchLists(*valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->createLabels();
        TE_CHECKRETURN_CODE(code);
        code = this->extrudePoints();
        TE_CHECKRETURN_CODE(code);
    }

    // sort points
    {
        BatchPointComparator cmp;
        std::sort(this->batchPoints.begin(), this->batchPoints.end(), cmp);
    }

    std::set<GLBatchGeometry3 *, FidComparator>::iterator iter;

    for (iter = this->sortedLines.begin(); iter != this->sortedLines.end(); iter++) {
        auto *line = static_cast<GLBatchLineString3 *>(*iter);
        if (line->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
            surfaceLines.push_back(line);
        else
            spriteLines.push_back(line);
    }
    this->sortedLines.clear();

    for (iter = this->sortedPolys.begin(); iter != this->sortedPolys.end(); iter++) {
        auto *poly = static_cast<GLBatchPolygon3 *>(*iter);
        if (poly->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
            surfacePolys.push_back(poly);
        else
            spritePolys.push_back(poly);
    }
    this->sortedPolys.clear();

    batchState.surface.srid = -1;
    batchState.sprites.srid = -1;

    return code;
}

TAKErr GLBatchGeometryRenderer3::fillBatchLists(Iterator2<SharedGLBatchGeometryPtr> &iter) NOTHROWS
{
    TAKErr code(TE_Ok);

    do
    {
        SharedGLBatchGeometryPtr g;
        code = iter.get(g);
        // track the pointer internally
        geoms.push_back(g);
        TE_CHECKBREAK_CODE(code);
        switch (g->zOrder)
        {
            case 0:
            {
                auto *point = static_cast<GLBatchPoint3 *>(g.get());
                if (point->textureKey != 0LL) {
                    if (!point->hasBatchProhibitiveAttributes()) {
                        batchPoints.push_back(point);
                    } else {
                        draw_points_.push_back(point);
                    }
                    if (point->iconDirty)
                        loadingPoints.push_back(point);
                } else if (point->iconUri) {
                    loadingPoints.push_back(point);
                } else if (point->name) {
                    labels.push_back(point);
                }
                break;
            }
            case 2:
            {
                if ((static_cast<GLBatchPolygon3 *>(g.get()))->fillColorA > 0.0f) {
                    sortedPolys.insert(static_cast<GLBatchPolygon3 *>(g.get()));
                    break;
                }

                // if the polygon isn't filled, treat it just like a line
            }
            case 1:
            {
                GLBatchLineString3& bls3 = static_cast<GLBatchLineString3&>(*g);
                for (std::size_t i = 0u; i < bls3.stroke.size(); i++) {
                    if (bls3.stroke[i].color.a > 0.0f) {
                        sortedLines.insert(&bls3);
                        break;
                    }
                }
                break;
            }
            case 10:
            case 11:
            case 12:
            case 13:
            {
                STLVectorAdapter<std::shared_ptr<GLBatchGeometry3>> children;
                code = static_cast<GLBatchGeometryCollection3 *>(g.get())->getChildren(children);
                TE_CHECKBREAK_CODE(code);

                // check for empty set
                if (children.empty()) {
                    code = TE_Ok;
                    break;
                }

                Collection<std::shared_ptr<GLBatchGeometry3>>::IteratorPtr collectionIter(nullptr, nullptr);
                code = children.iterator(collectionIter);
                TE_CHECKBREAK_CODE(code);

                code = this->fillBatchLists(*collectionIter);
                break;
            }
            default :
                return TE_InvalidArg;
        }
        TE_CHECKBREAK_CODE(code);

        code = iter.next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLBatchGeometryRenderer3::fillBatchLists(Iterator2<GLBatchGeometry3 *> &iter) NOTHROWS
{
    TAKErr code(TE_Ok);

    do
    {
        GLBatchGeometry3 *g;
        code = iter.get(g);
        TE_CHECKBREAK_CODE(code);
        switch (g->zOrder)
        {
        case 0:
        {
            auto *point = static_cast<GLBatchPoint3 *>(g);
            if (point->textureKey != 0LL) {
                if (!point->hasBatchProhibitiveAttributes()) {
                    batchPoints.push_back(point);
                } else {
                    draw_points_.push_back(point);
                }
                if (point->iconDirty)
                    loadingPoints.push_back(point);
            } else if (point->iconUri) {
                loadingPoints.push_back(point);
            } else if (point->name) {
                labels.push_back(point);
            }
            break;
        }
        case 2:
        {
            if ((static_cast<GLBatchPolygon3 *>(g))->fillColorA > 0.0f) {
                sortedPolys.insert(static_cast<GLBatchPolygon3 *>(g));
                break;
            }

            // if the polygon isn't filled, treat it just like a line
        }
        case 1:
        {
            GLBatchLineString3& bls3 = static_cast<GLBatchLineString3&>(*g);
            for (std::size_t i = 0u; i < bls3.stroke.size(); i++) {
                if (bls3.stroke[i].color.a > 0.0f) {
                    sortedLines.insert(&bls3);
                    break;
                }
            }
            break;
        }
        case 10:
        case 11:
        case 12:
        case 13:
        {
            STLVectorAdapter<std::shared_ptr<GLBatchGeometry3>> children;
            code = static_cast<GLBatchGeometryCollection3 *>(g)->getChildren(children);
            TE_CHECKBREAK_CODE(code);

            // check for empty set
            if (children.empty()) {
                code = TE_Ok;
                break;
            }

            Collection<std::shared_ptr<GLBatchGeometry3>>::IteratorPtr collectionIter(nullptr, nullptr);
            code = children.iterator(collectionIter);
            TE_CHECKBREAK_CODE(code);

            code = this->fillBatchLists(*collectionIter);
            break;
        }
        default:
            return TE_InvalidArg;
        }
        TE_CHECKBREAK_CODE(code);

        code = iter.next();
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}

void addLabel(GLLabelManager& labelManager, const double drawResolution, GLBatchPoint3& point, TAK::Engine::Renderer::GLTextureAtlas2 *atlas, const bool backgrounds) NOTHROWS
{
    if (point.labelId != GLLabelManager::NO_ID)
        return;
    double draw_resolution = 50.0;
    if (point.textureKey != 0LL)
        draw_resolution = drawResolution;
    else
        draw_resolution = 20000;
    if(point.labels.empty()) {
        double altitude(0.0);
        if (!isnan(point.altitude)) {
            altitude = point.altitude;
        }
        std::size_t offy = 0u;
        if(atlas)
            atlas->getImageHeight(&offy, point.textureKey);

        TAK::Engine::Feature::Geometry2Ptr_const geometry(new TAK::Engine::Feature::Point2(point.longitude, point.latitude, altitude),
                                                            Memory_deleter_const<TAK::Engine::Feature::Geometry2>);
        GLLabel label(std::move(geometry),
                      point.name,
                      TAK::Engine::Math::Point2<double>(0, static_cast<double>(offy)),
                      draw_resolution,
                      TETA_Center,
                      offy > 0 ? TEVA_Top : TEVA_Middle,
                      0xFFFFFFFF,
                      0x80000000,
                      backgrounds,
                      point.altitudeMode);
        point.labels.push_back(label);
    } else if (atlas) {
        std::size_t offy = 0u;
        atlas->getImageHeight(&offy, point.textureKey);
        point.labels[0].setVerticalAlignment(offy > 0 ? TEVA_Top : TEVA_Middle);
        point.labels[0].setDesiredOffset(TAK::Engine::Math::Point2<double>(0, static_cast<double>(offy)));
    }
                
    point.labels[0].setMaxDrawResolution(draw_resolution);
    point.labelId = labelManager.addLabel(point.labels[0]);
}

TAKErr GLBatchGeometryRenderer3::createLabels() NOTHROWS 
{
    TAKErr code(TE_Ok);
    GLLabelManager *labelManager = nullptr;
    TAK::Engine::Core::RenderContext* ctx = nullptr;
    if (!this->draw_points_.empty())
        ctx = &(*this->draw_points_.begin())->surface;
    if (!this->labels.empty())
        ctx = &(*this->labels.begin())->surface;
    if (!this->batchPoints.empty())
        ctx = &(*this->batchPoints.begin())->surface;
    // no labels
    if (!ctx)
        return code;

    code = GLMapRenderGlobals_getLabelManager(&labelManager, *ctx);
    TE_CHECKRETURN_CODE(code);
    if (!labelManager)
        return code;

    for (auto labelIter = this->labels.begin(); labelIter != this->labels.end(); labelIter++) {
        GLBatchPoint3 *lbl = *labelIter;
        addLabel(*labelManager, this->drawResolution, *lbl, nullptr, this->labelBackgrounds);
    }
    
    for (auto iter = this->batchPoints.begin(); iter != this->batchPoints.end(); iter++) {
        GLBatchPoint3 *point = *iter;
        addLabel(*labelManager, this->drawResolution, *point, point->iconAtlas, this->labelBackgrounds);
    }
    for (auto iter = this->draw_points_.begin(); iter != this->draw_points_.end(); iter++) {
        GLBatchPoint3 *point = *iter;
        addLabel(*labelManager, this->drawResolution, *point, point->iconAtlas, this->labelBackgrounds);
    }

    return code;
}


TAKErr GLBatchGeometryRenderer3::extrudePoints() NOTHROWS {
    TAKErr code(TE_Ok);
    if (!this->labels.empty()) {
        for (auto labelIter = this->labels.begin(); labelIter != this->labels.end(); labelIter++) {
            GLBatchPoint3 *lbl = *labelIter;
            if (lbl->extrude >= 0.0 || lbl->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround) continue;

            std::unique_ptr<GLBatchLineString3> glLollipop;
            glLollipop.reset(new GLBatchLineString3(lbl->surface));
            auto *lineString = new atakmap::feature::LineString(atakmap::feature::Geometry::_3D);
            lineString->addPoint(lbl->longitude, lbl->latitude, 0.0);
            lineString->addPoint(lbl->longitude, lbl->latitude, lbl->altitude);
            TAK::Engine::Feature::GeometryPtr_const geometry(lineString, atakmap::feature::destructGeometry);
            glLollipop->init(lbl->featureId, lbl->name, std::move(geometry), lbl->altitudeMode, 0.0, nullptr);

            pointLollipops.emplace_back(std::move(glLollipop));
            spriteLines.push_back(pointLollipops.back().get());
        }
    }

    if (!this->batchPoints.empty()) {
        for (auto iter = this->batchPoints.begin(); iter != this->batchPoints.end(); iter++) {
            GLBatchPoint3 *point = *iter;
            if (point->extrude >= 0.0 || point->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround) continue;

            std::unique_ptr<GLBatchLineString3> glLollipop;
            glLollipop.reset(new GLBatchLineString3(point->surface));
            auto *lineString = new atakmap::feature::LineString(atakmap::feature::Geometry::_3D);
            lineString->addPoint(point->longitude, point->latitude, 0.0);
            lineString->addPoint(point->longitude, point->latitude, point->altitude);
            TAK::Engine::Feature::GeometryPtr_const geometry(lineString, atakmap::feature::destructGeometry);
            glLollipop->init(point->featureId, point->name, std::move(geometry), point->altitudeMode, 0.0, nullptr);

            pointLollipops.emplace_back(std::move(glLollipop));
            spriteLines.push_back(pointLollipops.back().get());
        }
    }

    if (!this->draw_points_.empty()) {
        for (auto iter = this->draw_points_.begin(); iter != this->draw_points_.end(); iter++) {
            GLBatchPoint3 *point = *iter;
            if (point->extrude >= 0.0 || point->altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround) continue;

            std::unique_ptr<GLBatchLineString3> glLollipop;
            glLollipop.reset(new GLBatchLineString3(point->surface));
            auto *lineString = new atakmap::feature::LineString(atakmap::feature::Geometry::_3D);
            lineString->addPoint(point->longitude, point->latitude, 0.0);
            lineString->addPoint(point->longitude, point->latitude, point->altitude);
            TAK::Engine::Feature::GeometryPtr_const geometry(lineString, atakmap::feature::destructGeometry);
            glLollipop->init(point->featureId, point->name, std::move(geometry), point->altitudeMode, 0.0, nullptr);

            pointLollipops.emplace_back(std::move(glLollipop));
            spriteLines.push_back(pointLollipops.back().get());
        }
    }
    return code;
}

void GLBatchGeometryRenderer3::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    const bool depthEnabled = glIsEnabled(GL_DEPTH_TEST) != 0;
    const bool surface = !!(renderPass & GLMapView2::Surface);
    const bool sprites = !!(renderPass & GLMapView2::Sprites);
    if (surface || (view.drawTilt == 0.0))
        glDisable(GL_DEPTH_TEST);
    
    const int terrainVersion = view.getTerrainVersion();

    // match batches as dirty
    if (surface && batchState.surface.srid != view.drawSrid) {
        // reset relative to center
        batchState.surface.centroid.latitude = view.drawLat;
        batchState.surface.centroid.longitude = view.drawLng;
        view.scene.projection->forward(&batchState.surface.centroidProj, batchState.surface.centroid);
        batchState.surface.srid = view.drawSrid;

        batchState.surface.localFrame.setToTranslate(batchState.surface.centroidProj.x, batchState.surface.centroidProj.y, batchState.surface.centroidProj.z);

        // mark batches dirty
        rebuildBatchBuffers = 0xFFFFFFFF;
        batchTerrainVersion = view.getTerrainVersion();
    }
    if (sprites && batchState.sprites.srid != view.drawSrid) {
        // reset relative to center
        batchState.sprites.centroid.latitude = view.drawLat;
        batchState.sprites.centroid.longitude = view.drawLng;
        view.scene.projection->forward(&batchState.sprites.centroidProj, batchState.sprites.centroid);
        batchState.sprites.srid = view.drawSrid;

        batchState.sprites.localFrame.setToTranslate(batchState.sprites.centroidProj.x, batchState.sprites.centroidProj.y, batchState.sprites.centroidProj.z);

        // mark batches dirty
        rebuildBatchBuffers = 0xFFFFFFFF;
        batchTerrainVersion = view.getTerrainVersion();
    }
    
    if (batchTerrainVersion != terrainVersion) {
        // mark batches dirty
        rebuildBatchBuffers = 0xFFFFFFFF;
        batchTerrainVersion = terrainVersion;
    }

    if (surface)
        drawSurface(view);
    if (sprites)
        drawSprites(view);

    rebuildBatchBuffers &= ~renderPass;

    if (depthEnabled)
        glEnable(GL_DEPTH_TEST);
}

void GLBatchGeometryRenderer3::drawSurface(const GLMapView2 &view) NOTHROWS
{
    const bool is3D = view.scene.projection->is3D();

    // reset the state to the defaults
    //C# TO C++ CONVERTER TODO TASK: There is no C++ equivalent to 'unchecked' in this context:
    //ORIGINAL LINE: this.state.color = unchecked((int)0xFFFFFFFF);
    this->state.color = 0xFFFFFFFF;
    this->state.lineWidth = 1.0f;
    this->state.texId = 0;

    int i = 0;
    glGetIntegerv(GL_ACTIVE_TEXTURE, &i);
    this->state.textureUnit = i;

    // polygons
    if (!this->surfacePolys.empty())
    {
        if (this->batch.get() == nullptr)
        {
            this->batch.reset(new GLRenderBatch2(MAX_VERTS_PER_DRAW_ARRAYS));
        }

        try {
            float modelView[16u];
            int vertType;
            if (view.drawMapResolution < view.hardwareTransformResolutionThreshold) {
                // XXX - force all polygons projected as pixels as stroking does
                //       not work properly. since vertices are in projected
                //       coordinate space units, width also needs to be
                //       specified as such. attempts to compute some nominal
                //       scale factor produces reasonable results at lower map
                //       resolutions but cause width to converge to zero (32-bit
                //       precision?) at higher resolutions
                vertType = GLGeometry::VERTICES_PIXEL;

                atakmap::renderer::GLMatrix::identity(modelView);
            } else {
                vertType = GLGeometry::VERTICES_PROJECTED;
                memcpy(modelView, view.sceneModelForwardMatrix, sizeof(float) * 16u);
            }

            int hints = GLRenderBatch2::Untextured;
            if (!(vertType == GLGeometry::VERTICES_PROJECTED && view.scene.projection->is3D())) hints |= GLRenderBatch2::TwoDimension;

            this->batch->begin(hints);
            {
                float proj[16];
                atakmap::renderer::GLMatrix::orthoM(proj, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
                this->batch->setMatrix(GL_PROJECTION, proj);
                this->batch->setMatrix(GL_MODELVIEW, modelView);
            }
            for (auto poly = this->surfacePolys.begin(); poly != this->surfacePolys.end(); poly++)
                (*poly)->batch(view, GLMapView2::Surface, *this->batch, vertType);
            this->batch->end();
        }
        catch (std::out_of_range &e) {
            Util::Logger_log(Util::LogLevel::TELL_Error, "Error drawing surface: %s", e.what());
        }
    }

    // lines
    if (!this->surfaceLines.empty())
    {
        if (rebuildBatchBuffers&GLMapView2::Surface) {
            for (auto it = surfaceLineBuffers.begin(); it != surfaceLineBuffers.end(); it++)
                glDeleteBuffers(1u, &(*it).vbo);
            surfaceLineBuffers.clear();
            this->buildLineBuffers(surfaceLineBuffers, view, batchState.surface, surfaceLines);
        }

        this->drawLineBuffers(view, batchState.surface, surfaceLineBuffers);
    }
}

void GLBatchGeometryRenderer3::drawSprites(const GLMapView2 &view) NOTHROWS
{
    // points

    // XXX ----
/******************************************************/
/******************************************************/
/******************************************************/
#if 0
    // if the relative scaling has changed we need to reset the default text
    // and clear the texture atlas
    if (GLBatchPoint3::iconAtlasDensity != atakmap::core::AtakMapView::DENSITY)
    {
        GLBatchPoint3::ICON_ATLAS->release();
        GLBatchPoint3::ICON_ATLAS = new GLTextureAtlas(1024, safe_cast<int>(ceil(32 * atakmap::core::AtakMapView::DENSITY)));
        GLBatchPoint3::iconLoaders.clear();
        GLBatchPoint3::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
    }
#endif
/******************************************************/
/******************************************************/
/******************************************************/

    //IEnumerator<GLBatchPoint^> ^iter;

    // check all points with loading icons and move those whose icon has
    // loaded into the batchable list
    //iter = this->loadingPoints->GetEnumerator();
    bool resortBatchPoints = false;
    auto node = this->loadingPoints.begin();
    while (node != this->loadingPoints.end()) {
        GLBatchPoint3 &point = **node;
        GLBatchPoint3::getOrFetchIcon(view.context, point);
        if (point.textureKey != 0LL && !point.iconDirty) {
            if (!point.hasBatchProhibitiveAttributes()) {
                batchPoints.push_back(*node);
                resortBatchPoints = true;
            } else {
                draw_points_.push_back(*node);
            }
            node = this->loadingPoints.erase(node);
            rebuildBatchBuffers |= GLMapView2::Sprites;
        } else {
            node++;
        }
    }
    if(resortBatchPoints) {
        BatchPointComparator cmp;
        std::sort(this->batchPoints.begin(), this->batchPoints.end(), cmp);
    }

    // polygons
    if (!this->spritePolys.empty()) {
        if (this->batch.get() == nullptr) {
            this->batch.reset(new GLRenderBatch2(MAX_VERTS_PER_DRAW_ARRAYS));
        }

        try {
            float modelView[16u];
            int vertType;
            if (view.drawMapResolution < view.hardwareTransformResolutionThreshold) {
                // XXX - force all polygons projected as pixels as stroking does
                //       not work properly. since vertices are in projected
                //       coordinate space units, width also needs to be
                //       specified as such. attempts to compute some nominal
                //       scale factor produces reasonable results at lower map
                //       resolutions but cause width to converge to zero (32-bit
                //       precision?) at higher resolutions
                vertType = GLGeometry::VERTICES_PIXEL;

                atakmap::renderer::GLMatrix::identity(modelView);
            } else {
                vertType = GLGeometry::VERTICES_PROJECTED;
                memcpy(modelView, view.sceneModelForwardMatrix, sizeof(float) * 16u);;
            }

            int hints = GLRenderBatch2::Untextured;
            if (!(vertType == GLGeometry::VERTICES_PROJECTED && view.scene.projection->is3D())) hints |= GLRenderBatch2::TwoDimension;

            this->batch->begin(hints);
            {
                float proj[16];
                atakmap::renderer::GLMatrix::orthoM(proj, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
                this->batch->setMatrix(GL_PROJECTION, proj);
                this->batch->setMatrix(GL_MODELVIEW, modelView);
            }
            for (auto poly = this->spritePolys.begin(); poly != this->spritePolys.end(); poly++)
                (*poly)->batch(view, GLMapView2::Surface, *this->batch, vertType);
            this->batch->end();
        } catch (std::out_of_range &e) {
            Util::Logger_log(Util::LogLevel::TELL_Error, "Error drawing sprites: %s", e.what());
        }
    }

    // lines
    if (!this->spriteLines.empty()) {
        if (rebuildBatchBuffers&GLMapView2::Sprites) {
            for (auto it = spriteLineBuffers.begin(); it != spriteLineBuffers.end(); it++)
                glDeleteBuffers(1u, &(*it).vbo);
            spriteLineBuffers.clear();
            this->buildLineBuffers(spriteLineBuffers, view, batchState.sprites, spriteLines);
        }

        this->drawLineBuffers(view, batchState.sprites, spriteLineBuffers);
    }

    if (!this->batchPoints.empty() || !this->pointsBuffers.empty()) {
        if (rebuildBatchBuffers&GLMapView2::Sprites) {
            for (auto it = pointsBuffers.begin(); it != pointsBuffers.end(); it++)
                glDeleteBuffers(1u, &(*it).vbo);
            pointsBuffers.clear();
            this->buildPointsBuffers(pointsBuffers, view, batchState.sprites, batchPoints);
        }
        // render points with icons// render points with icons
        this->batchDrawPoints(view, batchState.sprites);
    }
    if (!this->draw_points_.empty()) {
        this->drawPoints(view);
    }
}

int GLBatchGeometryRenderer3::getRenderPass() NOTHROWS
{
    return GLMapView2::Surface | GLMapView2::Sprites;
}

TAKErr GLBatchGeometryRenderer3::buildLineBuffers(std::vector<LinesBuffer> &linesBuf, const GLMapView2 &view, const BatchState &ctx, const std::list<GLBatchLineString3 *> &lines) NOTHROWS {
    TAKErr code(TE_Ok);

    try {
#define VERTEX_BUF_SIZE 0xFFFFu
        uint8_t buf[VERTEX_BUF_SIZE];

        MemBuffer2 vbuf(buf, VERTEX_BUF_SIZE);
        for (auto g = lines.begin(); g != lines.end(); g++) {
            GLBatchLineString3 &line = **g;
            if (line.numPoints < 2u)
                continue;

            // project the line vertices, applying the batch centroid
            line.projectedVerticesSrid = -1;
            line.projectedCentroid = ctx.centroidProj;
            const float *ignored;
            code = line.projectVertices(&ignored, view, GLGeometry::VERTICES_PROJECTED);
            TE_CHECKBREAK_CODE(code);

            for (std::size_t i = 0u; i < line.stroke.size(); i++) {
                for (std::size_t j = 0u; j < (line.numPoints-1u); j++) {
                    if (vbuf.remaining() < (6u * LINES_VERTEX_SIZE)) {
                        LinesBuffer b;
                        glGenBuffers(1u, &b.vbo);
                        if (!b.vbo)
                            return TE_OutOfMemory;
                        glBindBuffer(GL_ARRAY_BUFFER, b.vbo);
                        glBufferData(GL_ARRAY_BUFFER, vbuf.position(), vbuf.get(), GL_STATIC_DRAW);
                        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
                        b.count = static_cast<GLsizei>(vbuf.position()) / LINES_VERTEX_SIZE;
                        linesBuf.push_back(b);
                        vbuf.reset();
                    }

                    Point2<float> a(line.projectedVertices[j * 3u], line.projectedVertices[j * 3u+1u], line.projectedVertices[j * 3u+2]);
                    Point2<float> b(line.projectedVertices[(j+1) * 3u], line.projectedVertices[(j+1) * 3u+1u], line.projectedVertices[(j+1) * 3u+2]);
#define bls3_vertex(v1, v2, n, dir) \
    vbuf.put<float>(v1.x); \
    vbuf.put<float>(v1.y); \
    vbuf.put<float>(v1.z); \
    vbuf.put<float>(v2.x); \
    vbuf.put<float>(v2.y); \
    vbuf.put<float>(v2.z); \
    vbuf.put<uint8_t>((line.stroke[i].color.argb>>16u)&0xFF); \
    vbuf.put<uint8_t>((line.stroke[i].color.argb>>8u)&0xFF); \
    vbuf.put<uint8_t>(line.stroke[i].color.argb&0xFF); \
    vbuf.put<uint8_t>((line.stroke[i].color.argb>>24u)&0xFF); \
    vbuf.put<uint8_t>(n); \
    vbuf.put<uint8_t>(static_cast<uint8_t>(std::min(line.stroke[i].width/4.0f, 255.0f))); \
    vbuf.put<uint8_t>(dir); \
    vbuf.put<uint32_t>(line.stroke[i].factor ? static_cast<uint16_t>(line.stroke[i].pattern) : 0xFFFFu); \
    vbuf.put<uint8_t>(line.stroke[i].factor ? static_cast<uint8_t>(line.stroke[i].factor) : 0x1u);

                    bls3_vertex(a, b, 0xFFu, 0xFFu);
                    bls3_vertex(b, a, 0xFFu, 0x00u);
                    bls3_vertex(a, b, 0x00u, 0xFFu);

                    bls3_vertex(a, b, 0xFFu, 0xFFu);
                    bls3_vertex(b, a, 0xFFu, 0x00u);
                    bls3_vertex(b, a, 0x00u, 0x00u);
#undef bls3_vertex
                }
            }
        }
        TE_CHECKRETURN_CODE(code);

        // flush the remaining record
        if (vbuf.position()) {
            LinesBuffer b;
            glGenBuffers(1u, &b.vbo);
            if (!b.vbo)
                return TE_OutOfMemory;
            glBindBuffer(GL_ARRAY_BUFFER, b.vbo);
            glBufferData(GL_ARRAY_BUFFER, vbuf.position(), vbuf.get(), GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
            b.count = static_cast<GLsizei>(vbuf.position()) / LINES_VERTEX_SIZE;
            linesBuf.push_back(b);
            vbuf.reset();
        }
    } catch (std::out_of_range &e) {
        code = TE_Err;
        Util::Logger_log(Util::LogLevel::TELL_Error, "Error drawing lines: %s", e.what());
    }

    return code;
}
TAKErr GLBatchGeometryRenderer3::drawLineBuffers(const GLMapView2 &view, const BatchState &ctx, const std::vector<LinesBuffer> &buf) NOTHROWS {
    TAKErr code(TE_Ok);

    if (!this->lineShader.base.handle) {
        int vertShader = GL_NONE;
        code = GLSLUtil_loadShader(&vertShader, LINE_VSH, GL_VERTEX_SHADER);
        assert(code == TE_Ok);

        int fragShader = GL_NONE;
        code = GLSLUtil_loadShader(&fragShader, LINE_FSH, GL_FRAGMENT_SHADER);
        assert(code == TE_Ok);

        ShaderProgram prog;
        code = GLSLUtil_createProgram(&prog, vertShader, fragShader);
        glDeleteShader(vertShader);
        glDeleteShader(fragShader);
        assert(code == TE_Ok);
        lineShader.base.handle = prog.program;

        glUseProgram(lineShader.base.handle);
        lineShader.u_mvp = glGetUniformLocation(lineShader.base.handle, "u_mvp");
        lineShader.u_viewportSize = glGetUniformLocation(lineShader.base.handle, "u_viewportSize");
        lineShader.a_vertexCoord0 = glGetAttribLocation(lineShader.base.handle, "a_vertexCoord0");
        lineShader.a_vertexCoord1 = glGetAttribLocation(lineShader.base.handle, "a_vertexCoord1");
        lineShader.a_color = glGetAttribLocation(lineShader.base.handle, "a_color");
        lineShader.a_normal = glGetAttribLocation(lineShader.base.handle, "a_normal");
        lineShader.a_halfStrokeWidth = glGetAttribLocation(lineShader.base.handle, "a_halfStrokeWidth");
        lineShader.a_dir = glGetAttribLocation(lineShader.base.handle, "a_dir");
        lineShader.a_pattern = glGetAttribLocation(lineShader.base.handle, "a_pattern");
        lineShader.a_factor = glGetAttribLocation(lineShader.base.handle, "a_factor");
    }

    glUseProgram(lineShader.base.handle);

    // MVP
    {
        Matrix2 mvp;
        // projection
        float matrixF[16u];
        atakmap::renderer::GLMatrix::orthoM(matrixF, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
        for(std::size_t i = 0u; i < 16u; i++)
            mvp.set(i%4, i/4, matrixF[i]);
        // model-view
        mvp.concatenate(view.scene.forwardTransform);
        mvp.translate(ctx.centroidProj.x, ctx.centroidProj.y, ctx.centroidProj.z);
        for (std::size_t i = 0u; i < 16u; i++) {
            double v;
            mvp.get(&v, i % 4, i / 4);
            matrixF[i] = (float)v;
        }
        glUniformMatrix4fv(lineShader.u_mvp, 1u, false, matrixF);
    }
    // viewport size
    {
        GLint viewport[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glUniform2f(lineShader.u_viewportSize, (float)viewport[2] / 2.0f, (float)viewport[3] / 2.0f);
    }

    glEnableVertexAttribArray(lineShader.a_vertexCoord0);
    glEnableVertexAttribArray(lineShader.a_vertexCoord1);
    glEnableVertexAttribArray(lineShader.a_color);
    glEnableVertexAttribArray(lineShader.a_normal);
    glEnableVertexAttribArray(lineShader.a_halfStrokeWidth);
    glEnableVertexAttribArray(lineShader.a_dir);
    glEnableVertexAttribArray(lineShader.a_pattern);
    glEnableVertexAttribArray(lineShader.a_factor);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    for (auto it = buf.begin(); it != buf.end(); it++) {
        glBindBuffer(GL_ARRAY_BUFFER, (*it).vbo);
        glVertexAttribPointer(lineShader.a_vertexCoord0, 3u, GL_FLOAT, false, LINES_VERTEX_SIZE, (const void *)0);
        glVertexAttribPointer(lineShader.a_vertexCoord1, 3u, GL_FLOAT, false, LINES_VERTEX_SIZE, (const void *)12u);
        glVertexAttribPointer(lineShader.a_color, 4u, GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, (const void *)24u);
        glVertexAttribPointer(lineShader.a_normal, 1u, GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, (const void *)28u);
        glVertexAttribPointer(lineShader.a_halfStrokeWidth, 1u, GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, (const void *)29u);
        glVertexAttribPointer(lineShader.a_dir, 1u, GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, (const void *)30u);
        glVertexAttribPointer(lineShader.a_pattern, 1u, GL_UNSIGNED_INT, false, LINES_VERTEX_SIZE, (const void *)31u);
        glVertexAttribPointer(lineShader.a_factor, 1u, GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, (const void *)35u);
        glDrawArrays(GL_TRIANGLES, 0u, (*it).count);
    }
    glDisable(GL_BLEND);

    glDisableVertexAttribArray(lineShader.a_vertexCoord0);
    glDisableVertexAttribArray(lineShader.a_vertexCoord1);
    glDisableVertexAttribArray(lineShader.a_color);
    glDisableVertexAttribArray(lineShader.a_normal);
    glDisableVertexAttribArray(lineShader.a_halfStrokeWidth);
    glDisableVertexAttribArray(lineShader.a_dir);
    glDisableVertexAttribArray(lineShader.a_pattern);
    glDisableVertexAttribArray(lineShader.a_factor);

    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    glUseProgram(GL_NONE);

    return code;
}

TAKErr GLBatchGeometryRenderer3::buildPointsBuffers(std::vector<PointsBuffer> &linesBuf, const GLMapView2 &view, const BatchState &ctx, const std::vector<GLBatchPoint3 *> &points) NOTHROWS {
    TAKErr code(TE_Ok);

    try {
#define VERTEX_BUF_SIZE 0xFFFFu
        uint8_t buf[VERTEX_BUF_SIZE];

        MemBuffer2 vbuf(buf, VERTEX_BUF_SIZE);
        GLuint lastTexId = (points.empty()) ? GL_NONE : points[0]->textureId;
        for (auto g = points.begin(); g != points.end(); g++) {
            GLBatchPoint3 &point = **g;

            if (vbuf.remaining() < POINT_VERTEX_SIZE || point.textureId != lastTexId) {
                PointsBuffer b;
                glGenBuffers(1u, &b.vbo);
                if (!b.vbo)
                    return TE_OutOfMemory;
                glBindBuffer(GL_ARRAY_BUFFER, b.vbo);
                glBufferData(GL_ARRAY_BUFFER, vbuf.position(), vbuf.get(), GL_STATIC_DRAW);
                glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
                b.count = static_cast<GLsizei>(vbuf.position()) / POINT_VERTEX_SIZE;
                b.texid = point.textureId;
                linesBuf.push_back(b);
                vbuf.reset();
            }

            lastTexId = point.textureId;
            point.validateProjectedLocation(view);

            auto relativeScaling = static_cast<float>(1.0f / view.pixelDensity);

            int textureSize = static_cast<int>(std::ceil(point.iconAtlas->getTextureSize() * relativeScaling));
            std::size_t iconSize;
            point.iconAtlas->getImageWidth(&iconSize, point.textureKey);
            int iconIndex = point.textureIndex;

            auto fTextureSize = static_cast<float>(textureSize);

            int numIconsX = (int)(textureSize / iconSize);

            auto iconX = static_cast<float>((iconIndex % numIconsX) * iconSize);
            auto iconY = static_cast<float>((iconIndex / numIconsX) * iconSize);

            vbuf.put<float>(static_cast<float>(point.posProjected.x-ctx.centroidProj.x));
            vbuf.put<float>(static_cast<float>(point.posProjected.y-ctx.centroidProj.y));
            vbuf.put<float>(static_cast<float>(point.posProjected.z-ctx.centroidProj.z));
            vbuf.put<float>(iconX / fTextureSize);
            vbuf.put<float>(iconY / fTextureSize);
        }
        TE_CHECKRETURN_CODE(code);

        // flush the remaining record
        if (vbuf.position()) {
            PointsBuffer b;
            glGenBuffers(1u, &b.vbo);
            if (!b.vbo)
                return TE_OutOfMemory;
            glBindBuffer(GL_ARRAY_BUFFER, b.vbo);
            glBufferData(GL_ARRAY_BUFFER, vbuf.position(), vbuf.get(), GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
            b.count = static_cast<GLsizei>(vbuf.position()) / POINT_VERTEX_SIZE;
            b.texid = lastTexId;
            linesBuf.push_back(b);
            vbuf.reset();
        }
    } catch (std::out_of_range &e) {
        code = TE_Err;
        Util::Logger_log(Util::LogLevel::TELL_Error, "Error building points buffers: %s", e.what());
    }

    return code;
}
TAKErr GLBatchGeometryRenderer3::batchDrawPoints(const GLMapView2 &view, const BatchState &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);

    this->state.color = static_cast<int>(0xFFFFFFFF);
    this->state.texId = 0;

    try {
        if (pointShader.base.handle == 0) {
            int vertShader = GL_NONE;
            code = GLSLUtil_loadShader(&vertShader, POINT_VSH, GL_VERTEX_SHADER);
            TE_CHECKRETURN_CODE(code);

            int fragShader = GL_NONE;
            code = GLSLUtil_loadShader(&fragShader, POINT_FSH, GL_FRAGMENT_SHADER);
            TE_CHECKRETURN_CODE(code);

            ShaderProgram prog{ 0u, 0u, 0u };
            code = GLSLUtil_createProgram(&prog, vertShader, fragShader);
            glDeleteShader(prog.fragShader);
            glDeleteShader(prog.vertShader);
            TE_CHECKRETURN_CODE(code);

            pointShader.base.handle = prog.program;
            glUseProgram(pointShader.base.handle);

            pointShader.uProjectionHandle = glGetUniformLocation(pointShader.base.handle, "uProjection");
            pointShader.uModelViewHandle = glGetUniformLocation(pointShader.base.handle, "uModelView");
            pointShader.uTextureHandle = glGetUniformLocation(pointShader.base.handle, "uTexture");
            pointShader.uColorHandle = glGetUniformLocation(pointShader.base.handle, "uColor");
            pointShader.uTexSizeHandle = glGetUniformLocation(pointShader.base.handle, "uTexSize");
            pointShader.uPointSizeHandle = glGetUniformLocation(pointShader.base.handle, "uPointSize");
            pointShader.aVertexCoordsHandle = glGetAttribLocation(pointShader.base.handle, "aVertexCoords");
            pointShader.aTextureCoordsHandle = glGetAttribLocation(pointShader.base.handle, "aTextureCoords");
        } else {
            glUseProgram(pointShader.base.handle);
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);


        float scratchMatrix[16];
        atakmap::renderer::GLMatrix::orthoM(scratchMatrix, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
        glUniformMatrix4fv(pointShader.uProjectionHandle, 1, false, scratchMatrix);

        // we will concatenate the local frame to the MapSceneModel's Model-View matrix to transform
        // from the Local Coordinate System into world coordinates before applying the model view.
        // If we do this all in double precision, then cast to single-precision, we'll avoid the
        // precision issues with trying to cast the world coordinates to float
        Matrix2 modelView(view.scene.forwardTransform);
        modelView.concatenate(ctx.localFrame);
        double modelViewMxD[16];
        modelView.get(modelViewMxD, Matrix2::COLUMN_MAJOR);
        float modelViewMxF[16];
        for (std::size_t i = 0u; i < 16u; i++) modelViewMxF[i] = (float)modelViewMxD[i];

        glUniformMatrix4fv(pointShader.uModelViewHandle, 1, false, modelViewMxF);

        // work with texture0
        glActiveTexture(this->state.textureUnit);
        glUniform1i(pointShader.uTextureHandle, this->state.textureUnit - GL_TEXTURE0);

        // sync the current color with the shader
        glUniform4f(pointShader.uColorHandle, ((this->state.color >> 16) & 0xFF) / (float)255, ((this->state.color >> 8) & 0xFF) / (float)255,
                    (this->state.color & 0xFF) / (float)255, ((this->state.color >> 24) & 0xFF) / (float)255);

        GLTextureAtlas *iconAtlas;
        GLMapRenderGlobals_getIconAtlas(&iconAtlas, view.context);

        auto texSize = static_cast<float>(iconAtlas->getTextureSize());
        auto pointSize = static_cast<float>(iconAtlas->getImageWidth(0));

        glUniform1f(pointShader.uTexSizeHandle, texSize);
        glUniform1f(pointShader.uPointSizeHandle, pointSize);

        glEnableVertexAttribArray(pointShader.aVertexCoordsHandle);
        glEnableVertexAttribArray(pointShader.aTextureCoordsHandle);

        for (auto &buf : pointsBuffers) {
            // set the texture for this batch
            this->state.texId = buf.texid;
            if (!this->state.texId)
                continue;

            glBindTexture(GL_TEXTURE_2D, this->state.texId);

            // set the color for this batch
            // XXX - 
            this->state.color = 0xFFFFFFFF;

            glUniform4f(pointShader.uColorHandle, ((this->state.color >> 16) & 0xFF) / (float)255,
                        ((this->state.color >> 8) & 0xFF) / (float)255, (this->state.color & 0xFF) / (float)255,
                        ((this->state.color >> 24) & 0xFF) / (float)255);


            glBindBuffer(GL_ARRAY_BUFFER, buf.vbo);

            glVertexAttribPointer(pointShader.aVertexCoordsHandle, 3u, GL_FLOAT, false, POINT_VERTEX_SIZE, (const void *)nullptr);
            glVertexAttribPointer(pointShader.aTextureCoordsHandle, 2u, GL_FLOAT, false, POINT_VERTEX_SIZE, (const void *)(0 + 12u));

            glDrawArrays(GL_POINTS, 0, buf.count);
        }

        glDisableVertexAttribArray(pointShader.aVertexCoordsHandle);
        glDisableVertexAttribArray(pointShader.aTextureCoordsHandle);

        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glDisable(GL_BLEND);

        if (this->state.texId != 0) {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    } catch (std::out_of_range &e) {
        code = TE_Err;
        Util::Logger_log(Util::LogLevel::TELL_Error, "Error drawing points: %s", e.what());
    }

    return code;
}


TAKErr GLBatchGeometryRenderer3::drawPoints(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS
{
    TAKErr code(TE_Ok);

    for (auto iter = this->draw_points_.begin(); iter != this->draw_points_.end(); iter++) {
        GLBatchPoint3 *point = *iter;

        point->draw(view, GLMapView2::Sprites);
    }

    return code;
}

TAKErr GLBatchGeometryRenderer3::renderPointsBuffers(const GLMapView2 &view, GLBatchPointBuffer & batch_point_buffer) NOTHROWS
{
    return TE_Ok;
}

void GLBatchGeometryRenderer3::start() NOTHROWS
{}

void GLBatchGeometryRenderer3::stop() NOTHROWS
{}

void GLBatchGeometryRenderer3::release() NOTHROWS
{
    this->surfaceLines.clear();
    this->spriteLines.clear();
    this->surfacePolys.clear();
    this->spritePolys.clear();
    this->pointLollipops.clear();

    this->batchPoints.clear();
    this->loadingPoints.clear();
    this->draw_points_.clear();
    this->labels.clear();

    // clear the tracking pointers
    this->geoms.clear();

    this->batch.reset();

    for (auto it = surfaceLineBuffers.begin(); it != surfaceLineBuffers.end(); it++)
        glDeleteBuffers(1u, &(*it).vbo);
    surfaceLineBuffers.clear();
    for (auto it = spriteLineBuffers.begin(); it != spriteLineBuffers.end(); it++)
        glDeleteBuffers(1u, &(*it).vbo);
    spriteLineBuffers.clear();
    for (auto it = pointsBuffers.begin(); it != pointsBuffers.end(); it++)
        glDeleteBuffers(1u, &(*it).vbo);
    pointsBuffers.clear();
}

GLBatchGeometryRenderer3::CachePolicy::CachePolicy() NOTHROWS :
    enabledCaches(0u)
{}

namespace
{
    template<class Iter>
    TAKErr hitTestPoints(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double resolution, const int radius, int64_t noid) NOTHROWS
    {
        GeoPoint touch(loc.y, loc.x);
        GeoPoint point;
        GLBatchPoint3 *item;
        int correctedRadius = radius;
        while (iter != end) {
            item = *iter;
            // increment at the top so returning early leaves iterator in correct spot. Don't reference iter after this line
            ++iter;
            point.latitude = item->latitude;
            point.longitude = item->longitude;
            double range = atakmap::util::distance::calculateRange(point, touch);

            int iconSize = 0;
            if (item->textureId) {
#ifdef __APPLE__
                //XXX-- density issue and make solution for all plats
                size_t iconW, iconH;
                item->getIconSize(&iconW, &iconH);
                iconSize = (int)(iconW / 2);
                
                // icon and radius together is much too large
                correctedRadius = std::max(0, radius - iconSize / 2);
#else
                // XXX - would be nice to derive from the point itself!!!
                iconSize = (int)ceil(atakmap::core::AtakMapView::DENSITY * 64);
#endif
            }
            double thresholdMeters = resolution*(correctedRadius + iconSize/2);
            if (range < thresholdMeters) {
                *result = item->featureId;
                return TE_Ok;
            }
        }

        *result = noid;
        return TE_Ok;
    }

    template<class Iter>
    TAKErr hitTestSurfaceLineStrings(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS
    {
        TAKErr code(TE_Ok);

        GLBatchLineString3 *item;
        while(iter != end) {
            item = *iter;
            // increment at the top so returning early leaves iterator in correct spot. Don't reference iter after this line
            ++iter;
            if (!item->points.get())
                continue;
            bool orthoHit;
            code = testOrthoHit(&orthoHit, item->points.get(), static_cast<int>(item->numPoints), item->mbb, loc, radius, hitBox);
            TE_CHECKBREAK_CODE(code);
            if(orthoHit) {
                *result = item->featureId;
                return code;
            }
        }
        TE_CHECKRETURN_CODE(code);

        *result = noid;
        return code;
    }

    template<class Iter>
    TAKErr hitTestSpriteLineStrings(int64_t *result, Iter &iter, const Iter &end, const double screen_x, const double screen_y, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS
    {
        TAKErr code(TE_Ok);

        GLBatchLineString3 *item;
        while(iter != end) {
            item = *iter;
            // increment at the top so returning early leaves iterator in correct spot. Don't reference iter after this line
            ++iter;
            if (!item->points.get())
                continue;
            bool orthoHit;
            code = testScreenHit(&orthoHit, item->vertices.get(), static_cast<int>(item->numPoints), item->screen_mbb, screen_x, screen_y,
                                 radius, hitBox);
            TE_CHECKBREAK_CODE(code);
            if(orthoHit) {
                *result = item->featureId;
                return code;
            }
        }
        TE_CHECKRETURN_CODE(code);

        *result = noid;
        return code;
    }

    template<class T>
    TAKErr testOrthoHit(bool *value, const T *linestring, const int numPoints, const Envelope &mbr, const Point &loc, const double radius, const Envelope &test) NOTHROWS
    {
        TAKErr code(TE_Ok);
        double x0;
        double y0;
        double x1;
        double y1;
        std::size_t idx;

        if (!atakmap::math::Rectangle<double>::intersects(mbr.minX, mbr.minY, mbr.maxX, mbr.maxY,
            test.minX, test.minY, test.maxX, test.maxY)) {

            //Log.d(TAG, "hit not contained in any geobounds");
            *value = false;
            return TE_Ok;
        }

        for (int i = 0; i < numPoints - 1; ++i) {
            idx = static_cast<std::size_t>(i * 3);
            x0 = linestring[idx];
            y0 = linestring[idx+1];
            x1 = linestring[idx+3];
            y1 = linestring[idx+4];

            code = isectOrthoTest(value, x0, y0, x1, y1, loc.x, loc.y, radius, test);
            TE_CHECKBREAK_CODE(code);
            if (*value)
                return code;
        }

        //Log.d(TAG, "hit not contained in any sub geobounds");
        *value = false;
        return code;
    }

    TAKErr testScreenHit(bool *value, const float *linestring, const int numPoints, const Envelope &mbr, const double screen_x, const double screen_y, const double radius, const Envelope &test) NOTHROWS {
        TAKErr code(TE_Ok);
        double x0;
        double y0;
        double x1;
        double y1;
        std::size_t idx;

        if (!atakmap::math::Rectangle<double>::intersects(mbr.minX, mbr.minY, mbr.maxX, mbr.maxY, test.minX, test.minY, test.maxX,
                                                          test.maxY)) {
            // Log.d(TAG, "hit not contained in any geobounds");
            *value = false;
            return TE_Ok;
        }

        for (int i = 0; i < numPoints - 1; ++i) {
            idx = static_cast<std::size_t>(i * 2);
            x0 = linestring[idx];
            y0 = linestring[idx + 1];
            x1 = linestring[idx + 2];
            y1 = linestring[idx + 3];

            code = isectScreenTest(value, x0, y0, x1, y1, screen_x, screen_y, radius, test);
            TE_CHECKBREAK_CODE(code);
            if (*value) return code;
        }

        // Log.d(TAG, "hit not contained in any sub geobounds");
        *value = false;
        return code;
    }

    TAKErr isectOrthoTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!atakmap::math::Rectangle<double>::intersects(std::min(x1, x2), std::min(y1, y2),
                                                          std::max(x1, x2), std::max(y1, y2),
                                                          test.minX, test.minY,
                                                          test.maxX, test.maxY)) {

            *value = false;
            return TE_Ok;
        }

        double px = x2 - x1;
        double py = y2 - y1;

        double something = px*px + py*py;

        double u = ((x3 - x1) * px + (y3 - y1) * py) / something;

        if (u > 1)
            u = 1;
        else if (u < 0)
            u = 0;

        double x = x1 + u * px;
        double y = y1 + u * py;

        *value = atakmap::util::distance::calculateRange(GeoPoint(y, x), GeoPoint(y3, x3)) < (radius * radius);
        return code;
    }

    TAKErr isectScreenTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!atakmap::math::Rectangle<double>::intersects(std::min(x1, x2), std::min(y1, y2),
                                                          std::max(x1, x2), std::max(y1, y2),
                                                          test.minX, test.minY,
                                                          test.maxX, test.maxY)) {

            *value = false;
            return TE_Ok;
        }

        double px = x2 - x1;
        double py = y2 - y1;

        double something = px*px + py*py;

        double u = ((x3 - x1) * px + (y3 - y1) * py) / something;

        if (u > 1)
            u = 1;
        else if (u < 0)
            u = 0;

        double x = x1 + u * px;
        double y = y1 + u * py;

        double dst;
        TAK::Engine::Math::Point2<double> pt(x3 - x, y3 - y);
        TAK::Engine::Math::Vector2_length(&dst, pt);
        *value = dst < ((2*radius) * (2*radius));
        return code;
    }

    void expandLineStringToLines(const std::size_t size, const float *verts, const std::size_t vertsOff, float *lines, const std::size_t linesOff, const std::size_t count, Matrix2 *xform) NOTHROWS
    {
        const float *pVerts = verts + vertsOff;
        float *pLines = lines + linesOff;
        const std::size_t segElems = (2 * size);
        const std::size_t cpySize = sizeof(float)*segElems;
        if (xform) {
            for (std::size_t i = 0u; i < count - 1; i++) {
                Point2<double> a;
                xform->transform(&a, Point2<double>(pVerts[0], pVerts[1], pVerts[2]));
                Point2<double> b;
                xform->transform(&b, Point2<double>(pVerts[3], pVerts[4], pVerts[5]));
                
                *pLines++ = (float)a.x;
                *pLines++ = (float)a.y;
                *pLines++ = (float)a.z;
                *pLines++ = (float)b.x;
                *pLines++ = (float)b.y;
                *pLines++ = (float)b.z;

                pVerts += size;
            }
        } else {
            for (std::size_t i = 0u; i < count - 1; i++) {
                memcpy(pLines, pVerts, cpySize);
                pLines += segElems;
                pVerts += size;
            }
        }
    }
}
