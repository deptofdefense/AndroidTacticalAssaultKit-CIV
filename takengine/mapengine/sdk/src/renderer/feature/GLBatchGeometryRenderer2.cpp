#include "renderer/GL.h"

#include "renderer/feature/GLBatchGeometryRenderer2.h"

#include <cassert>

#include "feature/Envelope.h"
#include "feature/Point.h"

#include "core/AtakMapView.h"

#include "port/STLSetAdapter.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/feature/GLBatchGeometry2.h"
#include "renderer/feature/GLBatchGeometryCollection2.h"
#include "renderer/feature/GLBatchPoint2.h"
#include "renderer/feature/GLGeometry.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/map/GLMapView.h"
#include "util/ConfigOptions.h"
#include "util/Distance.h"
#include "util/Logging.h"

#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;
using namespace atakmap::util::distance;

#define VECTOR_2D_VERT_SHADER_SRC \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "attribute vec2 aVertexCoords;\n" \
    "void main() {\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
    "}"

#define VECTOR_3D_VERT_SHADER_SRC \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "void main() {\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" \
    "}"

#define TEXTURE_2D_VERT_SHADER_SRC \
    "uniform mat4 uProjection;\n" \
    "uniform mat4 uModelView;\n" \
    "attribute vec2 aVertexCoords;\n" \
    "attribute vec2 aTextureCoords;\n" \
    "varying vec2 vTexPos;\n" \
    "void main() {\n" \
    "  vTexPos = aTextureCoords;\n" \
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
    "}"

#define MODULATED_TEXTURE_FRAG_SHADER_SRC \
    "precision mediump float;\n" \
    "uniform sampler2D uTexture;\n" \
    "uniform vec4 uColor;\n" \
    "varying vec2 vTexPos;\n" \
    "void main(void) {\n" \
    "  gl_FragColor = uColor * texture2D(uTexture, vTexPos);\n" \
    "}"

#define GENERIC_VECTOR_FRAG_SHADER_SRC \
    "precision mediump float;\n" \
    "uniform vec4 uColor;\n" \
    "void main(void) {\n" \
    "  gl_FragColor = uColor;\n" \
    "}"

#define PRE_FORWARD_LINES_POINT_RATIO_THRESHOLD 3

#define MAX_BUFFERED_2D_POINTS 20000
#define MAX_BUFFERED_3D_POINTS ((MAX_BUFFERED_2D_POINTS * 2) / 3)
#define MAX_VERTS_PER_DRAW_ARRAYS 5000

#define POINT_BATCHING_THRESHOLD 500

#ifdef MSVC
#define FORCE_LINEWIDTH_EMULATION 1
#else
#define FORCE_LINEWIDTH_EMULATION 0
#endif

namespace
{
    TAKErr loadShader(int *result, const int type, const char *source) NOTHROWS
    {
        int retval = glCreateShader(type);
        if (retval == GL_FALSE)
            return TE_Err;

        auto *c = (GLchar *)source;
        glShaderSource(retval, 1, &c, nullptr);
        glCompileShader(retval);

        int success;
        glGetShaderiv(retval, GL_COMPILE_STATUS, &success);
        if (success == 0) {
            //Log.d(TAG, "FAILED TO LOAD SHADER: " + source);
            //String ^msg = glGetShaderInfoLog(retval);
            glDeleteShader(retval);
            //throw gcnew Exception(msg);
        }
        *result = retval;
        return TE_Ok;
    }

    TAKErr createProgram(int *result, const int vertShader, const int fragShader) NOTHROWS
    {
        int retval = glCreateProgram();
        if (retval == GL_FALSE)
            return TE_Err;
        glAttachShader(retval, vertShader);
        glAttachShader(retval, fragShader);
        glLinkProgram(retval);

        int success;
        glGetProgramiv(retval, GL_LINK_STATUS, &success);
        if (success == 0) {
            //String ^msg = glGetProgramInfoLog(retval);
            glDeleteProgram(retval);
            //throw new RuntimeException(msg);
        }
        *result = retval;
        return TE_Ok;
    }

    template<class Iter>
    TAKErr hitTestPoints(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double resolution, const int radius, int64_t noid) NOTHROWS;

    template<class Iter>
    TAKErr hitTestLineStrings(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS;

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
    TAKErr testOrthoHit(bool *value, const float *linestring, const int numPoints, const Envelope &mbr, const Point &loc, const double radius, const Envelope &test) NOTHROWS;

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
    TAKErr isectTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS;

    float SCRATCH_MATRIX[16];
}

GLBatchGeometryRenderer2::GLBatchGeometryRenderer2(const CachePolicy &cachePolicy_) NOTHROWS :
    pointsBuffer(new float[MAX_BUFFERED_2D_POINTS * 2]),
    pointsBufferLength(MAX_BUFFERED_2D_POINTS*2),
    pointsVertsTexCoordsBuffer(new float[(MAX_BUFFERED_2D_POINTS * 2) * 2 * 6 * 2]),
    textureAtlasIndicesBuffer(new int[(MAX_BUFFERED_2D_POINTS * 2)]),
    textureAtlasIndicesBufferLength(MAX_BUFFERED_2D_POINTS*2),
    pointsBufferPosition(0),
    pointsBufferLimit(0),
    textureAtlasIndicesBufferPosition(0),
    textureAtlasIndicesBufferLimit(0),
    textureProgram(0),
    tex_uProjectionHandle(0),
    tex_uModelViewHandle(0),
    tex_uTextureHandle(0),
    tex_aTextureCoordsHandle(0),
    tex_aVertexCoordsHandle(0),
    tex_uColorHandle(0),
    cachePolicy(cachePolicy_),
    labelBackgrounds(true),
    fadingLabelsCount(0)
{
    Port::String opt;
    TAKErr code;

    code = ConfigOptions_getOption(opt, "default-label-background");
    if (code == TE_Ok)
        labelBackgrounds = !!atoi(opt);

    code = ConfigOptions_getOption(opt, "default-labels-fading-count");
    if (code == TE_Ok)
        fadingLabelsCount = atoi(opt);
}

GLBatchGeometryRenderer2::GLBatchGeometryRenderer2() NOTHROWS :
    pointsBuffer(new float[MAX_BUFFERED_2D_POINTS * 2]),
    pointsBufferLength(MAX_BUFFERED_2D_POINTS * 2),
    pointsVertsTexCoordsBuffer(new float[(MAX_BUFFERED_2D_POINTS * 2) * 2 * 6 * 2]),
    textureAtlasIndicesBuffer(new int[(MAX_BUFFERED_2D_POINTS * 2)]),
    textureAtlasIndicesBufferLength(MAX_BUFFERED_2D_POINTS * 2),
    pointsBufferPosition(0),
    pointsBufferLimit(0),
    textureAtlasIndicesBufferPosition(0),
    textureAtlasIndicesBufferLimit(0),
    textureProgram(0),
    tex_uProjectionHandle(0),
    tex_uModelViewHandle(0),
    tex_uTextureHandle(0),
    tex_aTextureCoordsHandle(0),
    tex_aVertexCoordsHandle(0),
    tex_uColorHandle(0),
    labelBackgrounds(true),
    fadingLabelsCount(0)
{
    Port::String opt;
    TAKErr code;
    
    code = ConfigOptions_getOption(opt, "default-label-background");
    if (code == TE_Ok)
        labelBackgrounds = !!atoi(opt);

    code = ConfigOptions_getOption(opt, "default-labels-fading-count");
    if (code == TE_Ok)
        fadingLabelsCount = atoi(opt);
}

TAKErr GLBatchGeometryRenderer2::hitTest(int64_t *result, const Point &loc, const double thresholdMeters, const int64_t noid) const NOTHROWS
{
    TAKErr code;
    std::vector<int64_t> fid;
    STLVectorAdapter<int64_t> fidAdapter(fid);
    code = this->hitTest2(fidAdapter, loc, thresholdMeters, 1, 1, noid);
    TE_CHECKRETURN_CODE(code);
    if (fid.empty()) {
        *result = noid;
        return code;
    }
    *result = fid[0];
    return code;
}

TAKErr GLBatchGeometryRenderer2::hitTest2(Collection<int64_t> &fids, const Point &loc, const double resolution, const int radius, const int limit, const int64_t noid) const NOTHROWS
{
    TAKErr code(TE_Ok);

    const double thresholdMeters = radius*resolution;

    const double rlat = (loc.y * M_PI / 180.0);
    const double metersDegLat = 111132.92 - 559.82 * cos(2 * rlat) + 1.175*cos(4 * rlat);
    const double metersDegLng = 111412.84 * cos(rlat) - 93.5 * cos(3 * rlat);

    const double ra = thresholdMeters / metersDegLat;
    const double ro = thresholdMeters / metersDegLng;

    const Envelope hitBox(loc.x - ro, loc.y - ra, NAN, loc.x + ro, loc.y + ra, NAN);

    {
        std::list<GLBatchPoint2 *>::const_reverse_iterator pointIter;
#ifdef __APPLE__
        pointIter = this->labels.rbegin();
        do {
            int64_t fid;
            code = hitTestPoints<std::list<GLBatchPoint2 *>::const_reverse_iterator>(&fid, pointIter, this->labels.rend(), loc, resolution, radius, noid);
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
        for (pointIter = this->labels.rbegin(); pointIter != this->labels.rend(); pointIter++) {
            GLBatchPoint2 *item = *pointIter;
            if (atakmap::math::Rectangle<double>::contains(hitBox.minX, hitBox.minY,
                                                           hitBox.maxX, hitBox.maxY,
                                                           item->longitude, item->latitude)) {

                code = fids.add(item->featureId);
                TE_CHECKBREAK_CODE(code);
                if (fids.size() == limit)
                    return code;
            }
        }
#endif
        TE_CHECKRETURN_CODE(code);
    }

    {
        std::set<GLBatchPoint2 *>::const_reverse_iterator pointIter;
#ifdef __APPLE__
        pointIter = this->batchPoints.rbegin();
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
        for (pointIter = this->batchPoints.rbegin(); pointIter != this->batchPoints.rend(); pointIter++) {
            GLBatchPoint2 *item = *pointIter;
            Envelope iconHitBox = hitBox;
            double iconSize = 0;
            if (item->textureId) {
                //XXX-- density issue and make solution for all plats
                size_t iconW, iconH;
                item->getIconSize(&iconW, &iconH);
                iconSize = (double)(std::max(iconW, iconH) / 2.0);

                // icon and radius together is much too large
                const double correctedRadiusMeters = std::max(thresholdMeters, iconSize*resolution);

                const double ira = correctedRadiusMeters / metersDegLat;
                const double iro = correctedRadiusMeters / metersDegLng;

                iconHitBox.minX = loc.x - iro;
                iconHitBox.minY = loc.y - ira;
                iconHitBox.maxX = loc.x + iro;
                iconHitBox.maxY = loc.y + ira;
            }
            if (atakmap::math::Rectangle<double>::contains(iconHitBox.minX, iconHitBox.minY,
                                                           iconHitBox.maxX, iconHitBox.maxY,
                                                           item->longitude, item->latitude)) {

                code = fids.add(item->featureId);
                TE_CHECKBREAK_CODE(code);
                if (fids.size() == limit)
                    return code;
            }
        }
#endif
        TE_CHECKRETURN_CODE(code);
    }
    
    auto lineIter = this->lines.rbegin();
    do {
        int64_t fid;
        code = hitTestLineStrings<std::list<GLBatchLineString2 *>::const_reverse_iterator>(&fid, lineIter, this->lines.rend(), loc, thresholdMeters, hitBox, noid);
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

    auto polyIter = this->polys.rbegin();
    do {
        int64_t fid;
        code = hitTestLineStrings<std::list<GLBatchPolygon2 *>::const_reverse_iterator>(&fid, polyIter, this->polys.rend(), loc, thresholdMeters, hitBox, noid);
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

    return code;
}

TAKErr GLBatchGeometryRenderer2::setBatch(Collection<GLBatchGeometry2 *> &value) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    geoms.clear();

    sortedPolys.clear();
    sortedLines.clear();

    polys.clear();
    lines.clear();

    loadingPoints.clear();
    batchPoints.clear();
    labels.clear();

    if (!value.empty()) {
        Collection<GLBatchGeometry2 *>::IteratorPtr valueIter(nullptr, nullptr);
        code = value.iterator(valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->fillBatchLists(*valueIter);
        TE_CHECKRETURN_CODE(code);
    }

    std::set<GLBatchGeometry2 *, FidComparator>::iterator iter;

    for (iter = this->sortedLines.begin(); iter != this->sortedLines.end(); iter++) {
        this->lines.push_back(static_cast<GLBatchLineString2 *>(*iter));
    }
    this->sortedLines.clear();

    for (iter = this->sortedPolys.begin(); iter != this->sortedPolys.end(); iter++) {
        this->polys.push_back(static_cast<GLBatchPolygon2 *>(*iter));
    }
    this->sortedPolys.clear();

    // clear the line record cache
    cachedLines.clear();

    // XXX - for some reason page re-use isn't working, dump and reconstruct
    //       pages every time until resolved
#if 0
    // drop the line buffer pages if there aren't any lines
    if (lines.empty())
#endif
        cachedLinesPages.clear();


    return code;
}

TAKErr GLBatchGeometryRenderer2::setBatch(Collection<SharedGLBatchGeometryPtr> &value) NOTHROWS
{
    TAKErr code(TE_Ok);

    geoms.clear();

    sortedPolys.clear();
    sortedLines.clear();

    polys.clear();
    lines.clear();

    loadingPoints.clear();
    batchPoints.clear();
    labels.clear();

    if (!value.empty()) {
        Collection<SharedGLBatchGeometryPtr>::IteratorPtr valueIter(nullptr, nullptr);
        code = value.iterator(valueIter);
        TE_CHECKRETURN_CODE(code);
        code = this->fillBatchLists(*valueIter);
        TE_CHECKRETURN_CODE(code);
    }

    std::set<GLBatchGeometry2 *, FidComparator>::iterator iter;

    for(iter = this->sortedLines.begin(); iter != this->sortedLines.end(); iter++) {
        this->lines.push_back(static_cast<GLBatchLineString2 *>(*iter));
    }
    this->sortedLines.clear();

    for (iter = this->sortedPolys.begin(); iter != this->sortedPolys.end(); iter++) {
        this->polys.push_back(static_cast<GLBatchPolygon2 *>(*iter));
    }
    this->sortedPolys.clear();

    // clear the line record cache
    cachedLines.clear();

    // XXX - for some reason page re-use isn't working, dump and reconstruct
    //       pages every time until resolved
#if 0
    // drop the line buffer pages if there aren't any lines
    if (lines.empty())
#endif
        cachedLinesPages.clear();

    return code;
}

TAKErr GLBatchGeometryRenderer2::fillBatchLists(Iterator2<SharedGLBatchGeometryPtr> &iter) NOTHROWS
{
    TAKErr code;

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
                auto *point = static_cast<GLBatchPoint2 *>(g.get());
                if (point->textureKey != 0LL) {
                    if (!point->hasBatchProhibitiveAttributes()) {
                        batchPoints.insert(point);
                    } else {
                        labels.push_back(point);
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
                if ((static_cast<GLBatchPolygon2 *>(g.get()))->fillColorA > 0.0f) {
                    sortedPolys.insert(static_cast<GLBatchPolygon2 *>(g.get()));
                    break;
                }

                // if the polygon isn't filled, treat it just like a line
            }
            case 1:
            {
                if ((static_cast<GLBatchLineString2 *>(g.get()))->strokeColorA > 0.0f) {
                    sortedLines.insert(static_cast<GLBatchLineString2 *>(g.get()));
                }
                break;
            }
            case 10:
            case 11:
            case 12:
            case 13:
            {
                STLVectorAdapter<std::shared_ptr<GLBatchGeometry2>> children;
                code = static_cast<GLBatchGeometryCollection2 *>(g.get())->getChildren(children);
                TE_CHECKBREAK_CODE(code);

                // check for empty set
                if (children.empty()) {
                    code = TE_Ok;
                    break;
                }

                Collection<std::shared_ptr<GLBatchGeometry2>>::IteratorPtr collectionIter(nullptr, nullptr);
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

TAKErr GLBatchGeometryRenderer2::fillBatchLists(Iterator2<GLBatchGeometry2 *> &iter) NOTHROWS
{
    TAKErr code;

    do
    {
        GLBatchGeometry2 *g;
        code = iter.get(g);
        TE_CHECKBREAK_CODE(code);
        switch (g->zOrder)
        {
        case 0:
        {
            auto *point = static_cast<GLBatchPoint2 *>(g);
            if (point->textureKey != 0LL) {
                batchPoints.insert(point);
                if(point->iconDirty)
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
            if ((static_cast<GLBatchPolygon2 *>(g))->fillColorA > 0.0f) {
                sortedPolys.insert(static_cast<GLBatchPolygon2 *>(g));
                break;
            }

            // if the polygon isn't filled, treat it just like a line
        }
        case 1:
        {
            if ((static_cast<GLBatchLineString2 *>(g))->strokeColorA > 0.0f) {
                sortedLines.insert(static_cast<GLBatchLineString2 *>(g));
            }
            break;
        }
        case 10:
        case 11:
        case 12:
        case 13:
        {
            STLVectorAdapter<std::shared_ptr<GLBatchGeometry2>> children;
            code = static_cast<GLBatchGeometryCollection2 *>(g)->getChildren(children);
            TE_CHECKBREAK_CODE(code);

            // check for empty set
            if (children.empty()) {
                code = TE_Ok;
                break;
            }

            Collection<std::shared_ptr<GLBatchGeometry2>>::IteratorPtr collectionIter(nullptr, nullptr);
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

void GLBatchGeometryRenderer2::draw(const GLMapView *view)
{
    const bool is3D = view->scene.projection->is3D();

    // XXX - always disabling depth right now

    // if the projection isn't 3D, disable depth testing
//    if (!is3D)
        glDisable(GL_DEPTH_TEST);

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
    if (!this->polys.empty())
    {
        if (this->batch.get() == nullptr)
        {
            this->batch.reset(new GLRenderBatch(MAX_VERTS_PER_DRAW_ARRAYS));
        }

        GLES20FixedPipeline::getInstance()->glPushMatrix();

        // XXX - batch currently only supports 2D vertices

                        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
                        //ORIGINAL LINE: final int vertType;
        int vertType;
        if (true || is3D)
        {
            // XXX - force all polygons projected as pixels as stroking does
            //       not work properly. since vertices are in projected
            //       coordinate space units, width also needs to be
            //       specified as such. attempts to compute some nominal
            //       scale factor produces reasonable results at lower map
            //       resolutions but cause width to converge to zero (32-bit
            //       precision?) at higher resolutions
            vertType = GLGeometry::VERTICES_PIXEL;
        }
        else
        {
            vertType = GLGeometry::VERTICES_PROJECTED;

            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);
        }

        bool inBatch = false;
        std::list<GLBatchPolygon2 *>::iterator poly;
        for (poly = this->polys.begin(); poly != this->polys.end(); poly++)
        {
            if ((*poly)->isBatchable(view))
            {
                if (!inBatch)
                {
                    this->batch->begin();
                    inBatch = true;
                }
                (*poly)->batch(view, this->batch.get(), vertType);
            }
            else
            {
                // the geometry can't be batched right now -- kick out of
                // batch if necessary and draw
                if (inBatch)
                {
                    this->batch->end();
                    inBatch = false;
                }
                (*poly)->draw(view, vertType);
            }
        }
        if (inBatch)
        {
            this->batch->end();
        }
        GLES20FixedPipeline::getInstance()->glPopMatrix();
    }

    // lines
    if (!this->lines.empty())
    {
        if (this->cachedLines.empty())
            this->batchDrawLinesProjected(view);
        else
            this->batchDrawCachedLinesProjected(view);
    }

    // points

    // XXX ----
/******************************************************/
/******************************************************/
/******************************************************/
#if 0
    // if the relative scaling has changed we need to reset the default text
    // and clear the texture atlas
    if (GLBatchPoint2::iconAtlasDensity != atakmap::core::AtakMapView::DENSITY)
    {
        GLBatchPoint2::ICON_ATLAS->release();
        GLBatchPoint2::ICON_ATLAS = new GLTextureAtlas(1024, safe_cast<int>(ceil(32 * atakmap::core::AtakMapView::DENSITY)));
        GLBatchPoint2::iconLoaders.clear();
        GLBatchPoint2::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
    }
#endif
/******************************************************/
/******************************************************/
/******************************************************/

    //IEnumerator<GLBatchPoint^> ^iter;

    // check all points with loading icons and move those whose icon has
    // loaded into the batchable list
    //iter = this->loadingPoints->GetEnumerator();
    auto node = this->loadingPoints.begin();
    while (node != this->loadingPoints.end())
    {
        GLBatchPoint2::getOrFetchIcon(view->impl->context, *node);
        if ((*node)->textureKey != 0LL)
        {
            if ((*node)->isBatchable(view)) {
                // may be updating icon
                this->batchPoints.insert(std::move(*node));
            } else {
                // may be updating icon
                this->labels.push_back(std::move(*node));
            }
            node = this->loadingPoints.erase(node);
        } else {
            node++;
        }
    }

    // render all labels
    if (!this->labels.empty())
    {
        if (this->batch.get() == nullptr)
        {
            this->batch.reset(new GLRenderBatch(MAX_VERTS_PER_DRAW_ARRAYS));
        }

        bool inBatch = false;
        std::list<GLBatchPoint2 *>::iterator labelIter;
        for (labelIter = this->labels.begin(); labelIter != this->labels.end(); labelIter++)
        {
            GLBatchPoint2 *g = *labelIter;
            g->labelBackground = this->labelBackgrounds;
            if (g->isBatchable(view))
            {
                if (!inBatch)
                {
                    this->batch->begin();
                    inBatch = true;
                }
                g->batch(view, this->batch.get());
            }
            else
            {
                // the geometry can't be batched right now -- kick out of
                // batch if necessary and draw
                if (inBatch)
                {
                    this->batch->end();
                    inBatch = false;
                }
                g->draw(view);
            }
        }
        if (inBatch)
        {
            this->batch->end();
        }
    }

    // render points with icons
    if (this->batchPoints.size() > POINT_BATCHING_THRESHOLD || (this->batchPoints.size() > 1 &&
        (/*TODO: !atakmap::cpp_cli::renderer::GLRenderContext::SETTING_displayLabels*/false || view->drawMapScale < GLBatchPoint2::defaultLabelRenderScale)))
    {
        // batch if there are many points on the screen or if we have more
        // than one point and labels are not going to be drawn
        this->batchDrawPoints(view);
    }
    else if (!this->batchPoints.empty())
    {
        if (this->batch.get() == nullptr)
        {
            this->batch.reset(new GLRenderBatch(2048));
        }

        const bool resetLabelTimers = (this->fadingLabelsCount && this->batchPoints.size() > this->fadingLabelsCount);

        bool inBatch = false;
        std::set<GLBatchPoint2 *, BatchPointComparator>::iterator iter;
        for (iter = this->batchPoints.begin(); iter != this->batchPoints.end(); iter++)
        {
            GLBatchPoint2 *point = *iter;
            point->labelBackground = this->labelBackgrounds;
            if (resetLabelTimers)
                point->labelFadeTimer = view->animationLastUpdate;

            if (point->isBatchable(view))
            {
                if(!inBatch) {
                    this->batch->begin();
                    inBatch = true;
                }
                point->batch(view, this->batch.get());
            }
            else
            {
                // the point can't be batched right now -- render the point
                // surrounded by end/begin to keep the batch in a valid
                // state
                if (inBatch) {
                    this->batch->end();
                    inBatch = false;
                }
                point->draw(view);
            }

        }
        if (inBatch)
            this->batch->end();
    }

//    if (!is3D)
        glEnable(GL_DEPTH_TEST);
}

TAKErr GLBatchGeometryRenderer2::batchDrawLinesProjected(const GLMapView *view) NOTHROWS
{
    TAKErr code(TE_Ok);

    try {
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);

        VectorProgram *vectorProgram;
        int maxBufferedPoints;
        if (view->scene.projection->is3D()) {
            if (this->vectorProgram3d.get() == nullptr) {
                this->vectorProgram3d.reset(new VectorProgram(3));
            } else {
                glUseProgram(this->vectorProgram3d->programHandle);
            }
            vectorProgram = this->vectorProgram3d.get();
            maxBufferedPoints = MAX_BUFFERED_3D_POINTS;
        } else {
            if (this->vectorProgram2d.get() == nullptr) {
                this->vectorProgram2d.reset(new VectorProgram(2));
            } else {
                glUseProgram(this->vectorProgram2d->programHandle);
            }
            vectorProgram = this->vectorProgram2d.get();
            maxBufferedPoints = MAX_BUFFERED_2D_POINTS;
        }

        GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, SCRATCH_MATRIX);

        float *smPtr = SCRATCH_MATRIX;
        glUniformMatrix4fv(vectorProgram->uProjectionHandle, 1, false, smPtr);

        GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, SCRATCH_MATRIX);
        smPtr = SCRATCH_MATRIX;
        glUniformMatrix4fv(vectorProgram->uModelViewHandle, 1, false, smPtr);

        // sync the current color with the shader
        glUniform4f(vectorProgram->uColorHandle, ((this->state.color >> 16) & 0xFF) / 255.0f, ((this->state.color >> 8) & 0xFF) / 255.0f,
                    ((this->state.color) & 0xFF) / 255.0f, ((this->state.color >> 24) & 0xFF) / 255.0f);

        // sync the current line width
        glLineWidth(this->state.lineWidth);

        GLBatchLineString2 *line;

        LineBatchRecord record;
        record.numVerts = 0;
        record.buffer = nullptr;

        const bool cacheEnabled = !!(cachePolicy.enabledCaches & CachePolicy::Lines);

        // points the the page currently in use
        auto page = this->cachedLinesPages.begin();
        std::size_t position = 0;

        const std::size_t limit = cacheEnabled ? 0x10000 * vectorProgram->vertSize : pointsBufferLength;

        std::list<GLBatchLineString2 *>::iterator g;
        for (g = this->lines.begin(); g != this->lines.end(); g++) {
            line = *g;
            if (line->numPoints < 2) {
                continue;
            }

            if (line->strokeColor != this->state.color) {
                if (record.numVerts > 0) {
                    // store the record
                    this->cachedLines.push_back(LineBatchRecord(record));

                    // render the current record
                    code = renderLinesBuffers(*vectorProgram, record);
                    TE_CHECKBREAK_CODE(code);

                    // prepare a new record
                    record.numVerts = 0;
                    record.buffer = nullptr;
                }

                glUniform4f(vectorProgram->uColorHandle, line->strokeColorR, line->strokeColorG, line->strokeColorB, line->strokeColorA);

                this->state.color = line->strokeColor;
            }

            if (line->strokeWidth != this->state.lineWidth) {
                if (record.numVerts > 0) {
                    // store the record
                    this->cachedLines.push_back(LineBatchRecord(record));

                    // render the current record
                    code = renderLinesBuffers(*vectorProgram, record);
                    TE_CHECKBREAK_CODE(code);

                    // prepare a new record
                    record.numVerts = 0;
                    record.buffer = nullptr;
                }

                glLineWidth(line->strokeWidth);
                this->state.lineWidth = line->strokeWidth;
            }

            // sync the record with the line
            record.color = line->strokeColor;
            record.r = line->strokeColorR;
            record.g = line->strokeColorG;
            record.b = line->strokeColorB;
            record.a = line->strokeColorA;
            record.lineWidth = line->strokeWidth;

            // project the line vertices
            const float *ignored;
            code = line->projectVertices(&ignored, view, GLGeometry::VERTICES_PROJECTED);
            TE_CHECKBREAK_CODE(code);

            // append to the current record

            std::size_t remainingSegments = line->numPoints - 1;
            std::size_t numSegsToExpand;
            std::size_t off = 0;
            while (remainingSegments > 0) {
                // flush if we can't accommodate another segment
                if ((limit - position) < (2 * vectorProgram->vertSize)) {
                    if (record.numVerts > 0) {
                        this->cachedLines.push_back(LineBatchRecord(record));

                        // render the current record
                        code = renderLinesBuffers(*vectorProgram, record);
                        TE_CHECKBREAK_CODE(code);
                    }

                    // prepare a new record
                    record.numVerts = 0;
                    record.buffer = nullptr;

                    // advance the page
                    page++;
                }

                // create a new page if necessary
                if (page == this->cachedLinesPages.end()) {
                    FloatBufferPtr pageData(nullptr, nullptr);
                    if (cacheEnabled)
                        pageData = FloatBufferPtr(new float[limit], Memory_array_deleter_const<float>);
                    else
                        pageData = FloatBufferPtr(pointsBuffer.get(), Memory_leaker_const<float>);
                    page = this->cachedLinesPages.insert(page, std::move(pageData));
                    position = 0;
                }

                // if the record doesn't have a buffer assigned, assign at current position
                if (!record.buffer) record.buffer = (*page).get() + position;

                float *vertsPtr = line->projectedVertices.get();
                float *linesPtr = record.buffer;
                numSegsToExpand = std::min((limit - position) / (2 * vectorProgram->vertSize), remainingSegments);

                // expand linestrings into segments
                expandLineStringToLines(vectorProgram->vertSize, vertsPtr, off, linesPtr, record.numVerts * vectorProgram->vertSize,
                                        numSegsToExpand + 1);

                // update the number of vertices
                record.numVerts += numSegsToExpand * 2;
                // update src/dst offsets
                position += (numSegsToExpand * (2 * vectorProgram->vertSize));
                off += numSegsToExpand * vectorProgram->vertSize;
                remainingSegments -= numSegsToExpand;
            }
        }
        TE_CHECKRETURN_CODE(code);

        // flush the remaining record
        if (record.numVerts) {
            // store the record
            this->cachedLines.push_back(LineBatchRecord(record));

            // render the current record
            code = renderLinesBuffers(*vectorProgram, record);
            TE_CHECKRETURN_CODE(code);
        }

        if (!cacheEnabled) {
            // caching is not enabled, clear out the records
            this->cachedLines.clear();
            this->cachedLinesPages.clear();
        } else if (page != this->cachedLinesPages.end()) {
            // dump any unused pages
            this->cachedLinesPages.erase(++page, this->cachedLinesPages.end());
        }

        // sync the current color with the pipeline
        GLES20FixedPipeline::getInstance()->glColor4f(
            ((this->state.color >> 16) & 0xFF) / (float)255, ((this->state.color >> 8) & 0xFF) / (float)255,
            (this->state.color & 0xFF) / (float)255, ((this->state.color >> 24) & 0xFF) / (float)255);

        GLES20FixedPipeline::getInstance()->glPopMatrix();
    } catch (std::out_of_range& e) {
        code = TE_Err;
        Util::Logger_log(Util::LogLevel::TELL_Error, "Error drawing lines: %s", e.what());
    }

    return code;
}

TAKErr GLBatchGeometryRenderer2::batchDrawCachedLinesProjected(const GLMapView *view) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);

    VectorProgram *vectorProgram;
    int maxBufferedPoints;
    if (view->scene.projection->is3D())
    {
        if (this->vectorProgram3d.get() == nullptr)
        {
            this->vectorProgram3d.reset(new VectorProgram(3));
        }
        else
        {
            glUseProgram(this->vectorProgram3d->programHandle);
        }
        vectorProgram = this->vectorProgram3d.get();
        maxBufferedPoints = MAX_BUFFERED_3D_POINTS;
    }
    else
    {
        if (this->vectorProgram2d.get() == nullptr)
        {
            this->vectorProgram2d.reset(new VectorProgram(2));
        }
        else
        {
            glUseProgram(this->vectorProgram2d->programHandle);
        }
        vectorProgram = this->vectorProgram2d.get();
        maxBufferedPoints = MAX_BUFFERED_2D_POINTS;
    }

    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, SCRATCH_MATRIX);

    float *smPtr = SCRATCH_MATRIX;
    glUniformMatrix4fv(vectorProgram->uProjectionHandle, 1, false, smPtr);

    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, SCRATCH_MATRIX);
    smPtr = SCRATCH_MATRIX;
    glUniformMatrix4fv(vectorProgram->uModelViewHandle, 1, false, smPtr);

    // sync the current color with the shader
    glUniform4f(vectorProgram->uColorHandle,
        ((this->state.color >> 16) & 0xFF) / 255.0f,
        ((this->state.color >> 8) & 0xFF) / 255.0f,
        ((this->state.color) & 0xFF) / 255.0f,
        ((this->state.color >> 24) & 0xFF) / 255.0f);

    // sync the current line width
    glLineWidth(this->state.lineWidth);

    std::list<LineBatchRecord>::iterator record;
    for (record = this->cachedLines.begin(); record != this->cachedLines.end(); record++)
    {
        LineBatchRecord &line = *record;

        if (this->state.color != line.color) {
            this->state.color = line.color;
            glUniform4f(vectorProgram->uColorHandle,
                line.r,
                line.g,
                line.b,
                line.a);
        }
        if (line.lineWidth != this->state.lineWidth)
        {
            glLineWidth(line.lineWidth);
            this->state.lineWidth = line.lineWidth;
        }

        code = renderLinesBuffers(*vectorProgram, line);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    // sync the current color with the pipeline
    GLES20FixedPipeline::getInstance()->glColor4f(
        ((this->state.color >> 16) & 0xFF) / (float)255,
        ((this->state.color >> 8) & 0xFF) / (float)255,
        (this->state.color & 0xFF) / (float)255,
        ((this->state.color >> 24) & 0xFF) / (float)255);

    GLES20FixedPipeline::getInstance()->glPopMatrix();

    return code;
}

void GLBatchGeometryRenderer2::expandLineStringToLines(size_t size, float *verts, size_t vertsOff, float *lines, size_t linesOff, size_t count)
{
    float *pVerts = verts + vertsOff;
    float *pLines = lines + linesOff;
    const size_t segElems = (2 * size);
    const size_t cpySize = sizeof(float) * segElems;
    for (size_t i = 0; i < count - 1; i++) {
        memcpy(pLines, pVerts, cpySize);
        pLines += segElems;
        pVerts += size;
    }
}

void GLBatchGeometryRenderer2::fillVertexArrays(float *translations, int *texAtlasIndices, int iconSize, int textureSize, float *vertsTexCoords, int count, float relativeScaling)
{
    float *pVertsTexCoords = vertsTexCoords;
    float tx;
    float ty;

    iconSize = static_cast<int>(std::floor(iconSize * relativeScaling));
    textureSize = static_cast<int>(std::ceil(textureSize * relativeScaling));
    
    float vertices[12];
    vertices[0] = static_cast<float>(-iconSize / 2);  // upper-left
    vertices[1] = static_cast<float>(-iconSize / 2);
    vertices[2] = static_cast<float>(iconSize / 2);   // upper-right
    vertices[3] = static_cast<float>(-iconSize / 2);
    vertices[4] = static_cast<float>(-iconSize / 2);  // lower-left
    vertices[5] = static_cast<float>(iconSize / 2);
    vertices[6] = static_cast<float>(iconSize / 2);   // upper-right
    vertices[7] = static_cast<float>(-iconSize / 2);
    vertices[8] = static_cast<float>(-iconSize / 2);  // lower-left
    vertices[9] = static_cast<float>(iconSize / 2);
    vertices[10] = static_cast<float>(iconSize / 2);  // lower-right
    vertices[11] = static_cast<float>(iconSize / 2);

    float iconX;
    float iconY;

    auto fIconSize = static_cast<float>(iconSize /*- 1*/);
    auto fTextureSize = static_cast<float>(textureSize);

    int numIconsX = textureSize / iconSize;
    int iconIndex;

    for (int i = 0; i < count; i++) {
        tx = translations[i * 2];
        ty = translations[i * 2 + 1];

        iconIndex = texAtlasIndices[i];

        iconX = static_cast<float>((iconIndex % numIconsX) * iconSize);
        iconY = static_cast<float>((iconIndex / numIconsX) * iconSize);

        (*pVertsTexCoords++) = vertices[0] + tx;
        (*pVertsTexCoords++) = vertices[1] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // upper-left
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[2] + tx;
        (*pVertsTexCoords++) = vertices[3] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[4] + tx;
        (*pVertsTexCoords++) = vertices[5] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
        (*pVertsTexCoords++) = iconY / fTextureSize;
        (*pVertsTexCoords++) = vertices[6] + tx;
        (*pVertsTexCoords++) = vertices[7] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[8] + tx;
        (*pVertsTexCoords++) = vertices[9] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
        (*pVertsTexCoords++) = iconY / fTextureSize;
        (*pVertsTexCoords++) = vertices[10] + tx;
        (*pVertsTexCoords++) = vertices[11] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // lower-right
        (*pVertsTexCoords++) = iconY / fTextureSize;
    }
}

TAKErr GLBatchGeometryRenderer2::renderLinesBuffers(const VectorProgram &vectorProgram, const LineBatchRecord &record) NOTHROWS
{
#if FORCE_LINEWIDTH_EMULATION
    GLES20FixedPipeline::getInstance()->glColor4f(
                    ((this->state.color >> 16) & 0xFF) / (float)255,
                    ((this->state.color >> 8) & 0xFF) / (float)255,
                    (this->state.color & 0xFF) / (float)255,
                    ((this->state.color >> 24) & 0xFF) / (float)255);

    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    GLES20FixedPipeline::getInstance()->glVertexPointer(static_cast<int>(vectorProgram.vertSize), GL_FLOAT, 0, record.buffer);
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINES, 0, static_cast<int>(record.numVerts));
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    return TE_Ok;
#else
    glVertexAttribPointer(vectorProgram.aVertexCoordsHandle, vectorProgram.vertSize, GL_FLOAT, false, 0, record.buffer);

    glEnableVertexAttribArray(vectorProgram.aVertexCoordsHandle);
    glDrawArrays(GL_LINES, 0, record.numVerts);
    glDisableVertexAttribArray(vectorProgram.aVertexCoordsHandle);

    return TE_Ok;
#endif
}

TAKErr GLBatchGeometryRenderer2::batchDrawPoints(const GLMapView *view) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    this->state.color = static_cast<int>(0xFFFFFFFF);
    this->state.texId = 0;

    try {
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (this->textureProgram == 0) {
            int vertShader;
            code = loadShader(&vertShader, GL_VERTEX_SHADER, TEXTURE_2D_VERT_SHADER_SRC);
            TE_CHECKRETURN_CODE(code);

            int fragShader;
            code = loadShader(&fragShader, GL_FRAGMENT_SHADER, MODULATED_TEXTURE_FRAG_SHADER_SRC);
            TE_CHECKRETURN_CODE(code);

            code = createProgram(&this->textureProgram, vertShader, fragShader);
            TE_CHECKRETURN_CODE(code);

            glUseProgram(this->textureProgram);

            this->tex_uProjectionHandle = glGetUniformLocation(this->textureProgram, "uProjection");

            this->tex_uModelViewHandle = glGetUniformLocation(this->textureProgram, "uModelView");

            this->tex_uTextureHandle = glGetUniformLocation(this->textureProgram, "uTexture");

            this->tex_uColorHandle = glGetUniformLocation(this->textureProgram, "uColor");

            this->tex_aVertexCoordsHandle = glGetAttribLocation(this->textureProgram, "aVertexCoords");
            this->tex_aTextureCoordsHandle = glGetAttribLocation(this->textureProgram, "aTextureCoords");
        } else {
            glUseProgram(this->textureProgram);
        }

        GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, SCRATCH_MATRIX);
        const float *smPtr = SCRATCH_MATRIX;
        glUniformMatrix4fv(this->tex_uProjectionHandle, 1, false, smPtr);

        GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, SCRATCH_MATRIX);
        smPtr = SCRATCH_MATRIX;
        glUniformMatrix4fv(this->tex_uModelViewHandle, 1, false, smPtr);

        // work with texture0
        GLES20FixedPipeline::getInstance()->glActiveTexture(this->state.textureUnit);
        glUniform1i(this->tex_uTextureHandle, this->state.textureUnit - GL_TEXTURE0);

        // sync the current color with the shader
        glUniform4f(this->tex_uColorHandle, ((this->state.color >> 16) & 0xFF) / (float)255, ((this->state.color >> 8) & 0xFF) / (float)255,
                    (this->state.color & 0xFF) / (float)255, ((this->state.color >> 24) & 0xFF) / (float)255);

        pointsBufferPosition = 0;
        pointsBufferLimit = pointsBufferLength;

        textureAtlasIndicesBufferPosition = 0;
        textureAtlasIndicesBufferLimit = textureAtlasIndicesBufferLength;

        GLBatchPoint2 *point;
        std::set<GLBatchPoint2 *, BatchPointComparator>::iterator geom;
        for (geom = this->batchPoints.begin(); geom != this->batchPoints.end(); geom++) {
            point = *geom;

            if (!point->iconUri) {
                continue;
            }

            if (point->textureKey == 0LL || point->iconDirty) {
                code = GLBatchPoint2::getOrFetchIcon(view->impl->context, point);
                TE_CHECKBREAK_CODE(code);
                if (point->textureKey == 0LL) continue;
            }

            if (this->state.texId != point->textureId) {
                this->renderPointsBuffers(view);

                pointsBufferPosition = 0;
                textureAtlasIndicesBufferPosition = 0;

                this->state.texId = point->textureId;
                glBindTexture(GL_TEXTURE_2D, this->state.texId);
            }
            if (this->state.texId == 0) {
                continue;
            }

            if (point->color != this->state.color) {
                code = this->renderPointsBuffers(view);
                TE_CHECKBREAK_CODE(code);

                pointsBufferPosition = 0;
                textureAtlasIndicesBufferPosition = 0;

                glUniform4f(tex_uColorHandle, ((point->color >> 16) & 0xFF) / (float)255, ((point->color >> 8) & 0xFF) / (float)255,
                            (point->color & 0xFF) / (float)255, ((point->color >> 24) & 0xFF) / (float)255);

                this->state.color = point->color;
            }

            // Unsafe::setFloats(this->pointsBufferPtr + pointsBufferPos, safe_cast<float>(point->longitude),
            // safe_cast<float>(point->latitude));
            pointsBuffer.get()[pointsBufferPosition++] = static_cast<float>(point->longitude);
            pointsBuffer.get()[pointsBufferPosition++] = static_cast<float>(point->latitude);

            // this->textureAtlasIndicesBuffer->put(point->textureIndex);
            textureAtlasIndicesBuffer.get()[textureAtlasIndicesBufferPosition++] = point->textureIndex;

            if ((pointsBufferPosition == pointsBufferLimit) ||
                ((this->textureAtlasIndicesBufferLimit - this->textureAtlasIndicesBufferPosition) == 0)) {
                this->renderPointsBuffers(view);
                textureAtlasIndicesBufferPosition = 0;
                pointsBufferPosition = 0;
            }
        }

        if (textureAtlasIndicesBufferPosition > 0) {
            code = this->renderPointsBuffers(view);
            TE_CHECKRETURN_CODE(code);
            textureAtlasIndicesBufferPosition = 0;
            pointsBufferPosition = 0;
        }

        GLES20FixedPipeline::getInstance()->glPopMatrix();
    } catch (std::out_of_range &) {
        return TE_Err;
    }

    glDisable(GL_BLEND);

    if (this->state.texId != 0)
    {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    // sync the current color with the pipeline
    GLES20FixedPipeline::getInstance()->glColor4f(((this->state.color>>16)&0xFF) / (float)255,
                                                  ((this->state.color>>8)&0xFF) / (float)255,
                                                  (this->state.color&0xFF) / (float)255,
                                                  ((this->state.color>>24)&0xFF) / (float)255);

    return code;
}

TAKErr GLBatchGeometryRenderer2::renderPointsBuffers(const GLMapView *view) NOTHROWS
{
    textureAtlasIndicesBufferLimit = textureAtlasIndicesBufferPosition;
    textureAtlasIndicesBufferPosition = 0;

    if ((textureAtlasIndicesBufferLimit - textureAtlasIndicesBufferPosition) < 1)
    {
        return TE_Ok;
    }

    pointsBufferLimit = pointsBufferPosition;
    pointsBufferPosition = 0;
    view->forward(pointsBuffer.get(), pointsBufferLimit / 2, pointsBuffer.get());

    pointsBufferPosition = 0;

    float *pointsPtr = this->pointsBuffer.get();
    int *atlasPtr = this->textureAtlasIndicesBuffer.get();
    float *texPtr = this->pointsVertsTexCoordsBuffer.get();

    GLTextureAtlas *iconAtlas;
    GLMapRenderGlobals_getIconAtlas(&iconAtlas, view->impl->getRenderContext());
    auto densityMultiplier = static_cast<float>(1.0f / view->pixelDensity);
    
    fillVertexArrays(
        pointsPtr,
        atlasPtr,
        iconAtlas->getImageWidth(0) /** densityMultiplier*/,
        iconAtlas->getTextureSize() /** densityMultiplier*/,
        texPtr,
        static_cast<int>(textureAtlasIndicesBufferLimit),
        densityMultiplier); // fixed size

    float *tp = this->pointsVertsTexCoordsBuffer.get();
    glVertexAttribPointer(this->tex_aVertexCoordsHandle, 2, GL_FLOAT, false, 16, tp);
    glEnableVertexAttribArray(this->tex_aVertexCoordsHandle);

    glVertexAttribPointer(this->tex_aTextureCoordsHandle, 2, GL_FLOAT, false, 16, tp + 2);
    glEnableVertexAttribArray(this->tex_aTextureCoordsHandle);

    int remaining = static_cast<int>(textureAtlasIndicesBufferLimit - textureAtlasIndicesBufferPosition);//this->textureAtlasIndicesBuffer->Length;//this->textureAtlasIndicesBuffer->Length;
    int iconsPerPass = MAX_VERTS_PER_DRAW_ARRAYS / 6;
    int off = 0;
    do
    {
        // XXX - note that we could use triangle strips here, but we would
        //       need a degenerate triangle for every icon except the last
        //       one, meaning that all icons except the last would require
        //       6 vertices
        glDrawArrays(GL_TRIANGLES, off * 6, std::min(remaining, iconsPerPass) * 6);

        remaining -= iconsPerPass;
        off += iconsPerPass;
    } while (remaining > 0);

    glDisableVertexAttribArray(this->tex_aVertexCoordsHandle);
    glDisableVertexAttribArray(this->tex_aTextureCoordsHandle);

    //this->pointsBuffer->position(this->pointsBuffer->limit());
    //this->textureAtlasIndicesBuffer->position(this->textureAtlasIndicesBuffer->limit());

    return TE_Ok;
}

void GLBatchGeometryRenderer2::start()
{}

void GLBatchGeometryRenderer2::stop()
{}

void GLBatchGeometryRenderer2::release()
{
    this->lines.clear();
    this->polys.clear();

    this->batchPoints.clear();
    this->loadingPoints.clear();
    this->labels.clear();

    // clear the tracking pointers
    this->geoms.clear();

    this->cachedLines.clear();
    this->cachedLinesPages.clear();

    this->batch = nullptr;
}

GLBatchGeometryRenderer2::VectorProgram::VectorProgram(std::size_t vertSize)
{
    this->vertSize = vertSize;

    const char *vertShaderSrc;
    switch (this->vertSize)
    {
        case 2 :
            vertShaderSrc = VECTOR_2D_VERT_SHADER_SRC;
            break;
        case 3 :
            vertShaderSrc = VECTOR_3D_VERT_SHADER_SRC;
            break;
        default :
            // XXX - no failover
            assert(false);
            return;
    }
    TAKErr code;

    int vertShader;
    code = loadShader(&vertShader, GL_VERTEX_SHADER, vertShaderSrc);
    assert(code == TE_Ok);

    int fragShader;
    code = loadShader(&fragShader, GL_FRAGMENT_SHADER, GENERIC_VECTOR_FRAG_SHADER_SRC);
    assert(code == TE_Ok);

    code = createProgram(&this->programHandle, vertShader, fragShader);
    assert(code == TE_Ok);

    glUseProgram(this->programHandle);

    this->uProjectionHandle = glGetUniformLocation(this->programHandle, "uProjection");

    this->uModelViewHandle = glGetUniformLocation(this->programHandle, "uModelView");

    this->uColorHandle = glGetUniformLocation(this->programHandle, "uColor");

    this->aVertexCoordsHandle = glGetAttribLocation(this->programHandle, "aVertexCoords");
}

GLBatchGeometryRenderer2::CachePolicy::CachePolicy() NOTHROWS :
    enabledCaches(0u)
{}

namespace
{
    template<class Iter>
    TAKErr hitTestPoints(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double resolution, const int radius, int64_t noid) NOTHROWS
    {
        GeoPoint touch(loc.y, loc.x);
        GeoPoint point;
        GLBatchPoint2 *item;
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
    TAKErr hitTestLineStrings(int64_t *result, Iter &iter, const Iter &end, const Point &loc, const double radius, const Envelope &hitBox, const int64_t noid) NOTHROWS
    {
        TAKErr code(TE_Ok);

        GLBatchLineString2 *item;
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

    TAKErr testOrthoHit(bool *value, const float *linestring, const int numPoints, const Envelope &mbr, const Point &loc, const double radius, const Envelope &test) NOTHROWS
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
            idx = static_cast<std::size_t>(i * 2);
            x0 = linestring[idx];
            y0 = linestring[idx+1];
            x1 = linestring[idx+2];
            y1 = linestring[idx+3];

            code = isectTest(value, x0, y0, x1, y1, loc.x, loc.y, radius, test);
            TE_CHECKBREAK_CODE(code);
            if (*value)
                return code;
        }

        //Log.d(TAG, "hit not contained in any sub geobounds");
        *value = false;
        return code;
    }

    TAKErr isectTest(bool *value, const double x1, const double y1, const double x2, const double y2, const double x3, const double y3, const double radius, const Envelope &test) NOTHROWS
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

        *value = atakmap::util::distance::calculateRange(GeoPoint(y, x), GeoPoint(y3, x3))<radius;
        return code;
    }
}
