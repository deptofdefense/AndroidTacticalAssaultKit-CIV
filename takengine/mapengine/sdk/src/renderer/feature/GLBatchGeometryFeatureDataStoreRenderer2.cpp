#include "renderer/GL.h"

#include "renderer/feature/GLBatchGeometryFeatureDataStoreRenderer2.h"

#include "feature/FeatureCursor2.h"
#include "port/STLListAdapter.h"
#include "port/STLVectorAdapter.h"
#include "port/StringBuilder.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/feature/GLBatchGeometryCollection2.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLBatchMultiLineString3.h"
#include "renderer/feature/GLBatchMultiPoint3.h"
#include "renderer/feature/GLBatchMultiPolygon3.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "util/Memory.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;
using namespace atakmap::util;

#define STALE_LIMIT 5

namespace
{
    double distance(double x1, double y1, double x2, double y2);
    TAKErr getStyle(std::shared_ptr<const Style> &value, FeatureCursor2 &cursor, std::map<std::string, std::shared_ptr<const Style>> &styleMap) NOTHROWS;
    void releaseGLBatchGeometryRunnable(void *) NOTHROWS;

    struct FeatureResult
    {
    public :
        FeatureResult() :
            geomUpdate(nullptr, nullptr),
            geomBlobUpdate(nullptr, nullptr),
            updateVersion(FeatureDataStore2::FEATURE_VERSION_NONE), 
            extrudeUpdate(NAN),
            altModeUpdate((AltitudeMode)-1)
        {}
    public :
        std::shared_ptr<GLBatchGeometry3> target;
        std::unique_ptr<TAK::Engine::Port::String> nameUpdate;
        std::shared_ptr<const Style> styleUpdate;
        AltitudeMode altModeUpdate;
        double extrudeUpdate;
        GeometryPtr_const geomUpdate;
        GLBatchGeometry3::BlobPtr geomBlobUpdate;
        int geomBlobUpdateType {-1};
        int geomBlobUpdateLod {-1};
        int64_t updateVersion;
    };

    class QueryContextImpl : public GLAsynchronousMapRenderable3::QueryContext
    {
    public :
        QueryContextImpl();
    public :
        std::list<FeatureResult> pendingData;
        int64_t queryCount;
    };
}

GLBatchGeometryFeatureDataStoreRendererOptions2::GLBatchGeometryFeatureDataStoreRendererOptions2() NOTHROWS
    : skipSameLodOptimization(false) { }

GLBatchGeometryFeatureDataStoreRenderer2::GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface_, FeatureDataStore2 &subject_) NOTHROWS :
    GLAsynchronousMapRenderable3(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer1(new GLBatchGeometryRenderer3()),
    batchRenderer2(new GLBatchGeometryRenderer3()),
    front(batchRenderer1.get()),
    back(batchRenderer2.get()),
    renderHeightPump(0.0f)
{
    hittest.reset(new HitTestImpl(*this));
}

GLBatchGeometryFeatureDataStoreRenderer2::GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface_, FeatureDataStore2 &subject_, const GLBatchGeometryRenderer3::CachePolicy &cachePolicy) NOTHROWS  :
    GLAsynchronousMapRenderable3(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer1(new GLBatchGeometryRenderer3(cachePolicy)),
    batchRenderer2(new GLBatchGeometryRenderer3(cachePolicy)),
    front(batchRenderer1.get()),
    back(batchRenderer2.get()),
    renderHeightPump(0.0f)
{
    hittest.reset(new HitTestImpl(*this));
}

GLBatchGeometryFeatureDataStoreRenderer2::GLBatchGeometryFeatureDataStoreRenderer2(TAK::Engine::Core::RenderContext &surface_, FeatureDataStore2 &subject_, const GLBatchGeometryRenderer3::CachePolicy &cachePolicy, const GLBatchGeometryFeatureDataStoreRendererOptions2 &opts) NOTHROWS :
    GLAsynchronousMapRenderable3(),
    surface(surface_),
    dataStore(subject_),
    batchRenderer1(new GLBatchGeometryRenderer3(cachePolicy)),
    batchRenderer2(new GLBatchGeometryRenderer3(cachePolicy)),
    front(batchRenderer1.get()),
    back(batchRenderer2.get()),
    options(opts),
    renderHeightPump(0.0f)
{
    hittest.reset(new HitTestImpl(*this));
}

/**************************************************************************/
// GL Asynchronous Map Renderable

void GLBatchGeometryFeatureDataStoreRenderer2::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    if (view.drawHorizon && view.drawMapResolution >= 2538.9415423720125)
        return;

    // record the height during the render pump
    if(renderPass&GLMapView2::Sprites)
        renderHeightPump = (float)(view.top - view.bottom);

    // XXX - layer visibility

    GLAsynchronousMapRenderable3::draw(view, renderPass);
}

int GLBatchGeometryFeatureDataStoreRenderer2::getRenderPass() NOTHROWS
{
    return GLMapView2::Surface|GLMapView2::Sprites;
}

void GLBatchGeometryFeatureDataStoreRenderer2::start() NOTHROWS
{
}

void GLBatchGeometryFeatureDataStoreRenderer2::stop() NOTHROWS
{
}

void GLBatchGeometryFeatureDataStoreRenderer2::initImpl(const GLMapView2 &view) NOTHROWS
{
    this->dataStore.addOnDataStoreContentChangedListener(this);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::releaseImpl() NOTHROWS
{
    this->dataStore.removeOnDataStoreContentChangedListener(this);
    this->batchRenderer1->release();
    this->batchRenderer2->release();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::getRenderables(Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS
{
    if (this->renderList.empty())
        return TE_Done;
    STLListAdapter<GLMapRenderable2 *> adapter(this->renderList);
    return adapter.iterator(iter);
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::resetQueryContext(QueryContext &ctx) NOTHROWS
{
    static_cast<QueryContextImpl &>(ctx).pendingData.clear();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::createQueryContext(QueryContextPtr &result) NOTHROWS
{
    result = QueryContextPtr(new QueryContextImpl(), Memory_deleter_const<QueryContext, QueryContextImpl>);
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::updateRenderableLists(QueryContext &ctx) NOTHROWS
{
    // XXX - check for cancellation
    GLBatchGeometryRenderer3 *swap = back;
    back = front;
    front = swap;
    this->renderList.clear();
    this->renderList.push_back(front);
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS
{
    StringBuilder strm;
    strm << "GLBatchGeometryRendererThread-";
    strm << (uintptr_t)this;

    value = strm.c_str();
    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::query(QueryContext &ctx, const ViewState &state) NOTHROWS
{
    TAKErr code(TE_Ok);

    const bool crossIdl = (state.westBound > state.eastBound);
    if (crossIdl) {
        // XXX - SpatiaLite will not correctly perform intersection (at least
        //       when the using spatial index) if the geometry provided is a
        //       GeometryCollection that is divided across the IDL. Two queries
        //       must be performed, one for each hemisphere.

        ViewState stateE;
        stateE.copy(state);
        stateE.eastBound = 180;
        ViewState stateW;
        stateW.copy(state);
        stateW.westBound = -180;

        code = this->queryImpl(ctx, stateE);
        TE_CHECKRETURN_CODE(code);

        code = this->queryImpl(ctx, stateW);
        TE_CHECKRETURN_CODE(code);
    } else {
        code = this->queryImpl(ctx, state);
        TE_CHECKRETURN_CODE(code);
    }

    std::vector<std::shared_ptr<GLBatchGeometry3>> update;
    {
        WriteLock wlock(renderables_mutex_);
        code = wlock.status;
        TE_CHECKRETURN_CODE(code);

        auto &result = static_cast<QueryContextImpl &>(ctx);
        
        update.reserve(result.pendingData.size());

        std::list<FeatureResult>::iterator it;
        for (it = result.pendingData.begin(); it != result.pendingData.end(); it++) {
            FeatureResult &r = *it;
            if (r.geomBlobUpdate.get())
                r.target->setGeometry(std::move(r.geomBlobUpdate), r.geomBlobUpdateType, r.geomBlobUpdateLod);
            else if (r.geomUpdate.get())
                r.target->setGeometry(*r.geomUpdate);
            if (r.styleUpdate)
                r.target->setStyle(std::move(r.styleUpdate));
            if (r.nameUpdate.get())
                r.target->setName(*r.nameUpdate);
            if (r.altModeUpdate != -1)
                r.target->setAltitudeMode(r.altModeUpdate);
            if (!std::isnan(r.extrudeUpdate))
                r.target->extrude = r.extrudeUpdate;
            r.target->version = r.updateVersion;
            update.push_back(r.target);
        }
    }

    STLVectorAdapter<std::shared_ptr<GLBatchGeometry3>> adapter(update);
    code = back->setBatch(adapter);

    return TE_Ok;
}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::queryImpl(QueryContext &ctx, const ViewState &state) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::list<FeatureResult> &result = static_cast<QueryContextImpl &>(ctx).pendingData;
    static_cast<QueryContextImpl &>(ctx).queryCount++;

    const int lod = OSMUtils::mapnikTileLevel(state.drawMapResolution);

    const bool crossIdl = (state.westBound > state.eastBound);

    double simplifyFactor = distance(state.upperLeft.longitude,
        state.upperLeft.latitude, state.lowerRight.longitude,
        state.lowerRight.latitude) /
        distance(state._left, state._top, state._right, state._bottom) * 2;

    FeatureDataStore2::FeatureQueryParameters params;
    params.visibleOnly = true;

    LineString mbb(atakmap::feature::Geometry::_2D);
    mbb.addPoint(state.westBound, state.northBound);
    mbb.addPoint(state.eastBound, state.northBound);
    mbb.addPoint(state.eastBound, state.southBound);
    mbb.addPoint(state.westBound, state.southBound);
    mbb.addPoint(state.westBound, state.northBound);

    params.spatialFilter = GeometryPtr_const(new atakmap::feature::Polygon(mbb), Memory_deleter_const<atakmap::feature::Geometry>);
    params.maxResolution = state.drawMapResolution;
	params.minResolution = state.drawMapResolution * 0.5;

    FeatureDataStore2::FeatureQueryParameters::SpatialOp simplify;
    simplify.type = FeatureDataStore2::FeatureQueryParameters::SpatialOp::Simplify;
    simplify.args.simplify.distance = simplifyFactor;
    params.ops->add(simplify);

    params.ignoredFields = FeatureDataStore2::FeatureQueryParameters::AttributesField;

    try {
        std::map<std::string, std::shared_ptr<const atakmap::feature::Style>> styleMap;

        int64_t featureId;
        int64_t featureVersion;

        //System::Int64 s = SystemClock::currentTimeMillis();

        FeatureCursorPtr cursor(nullptr, nullptr);
        
        // release entries
        std::unique_ptr<std::vector< std::shared_ptr<GLBatchGeometry3>>> releaseGeometry(new std::vector< std::shared_ptr<GLBatchGeometry3>>());

        code = this->dataStore.queryFeatures(cursor, params);
        TE_CHECKRETURN_CODE(code);
        do {
            code = cursor->moveToNext();
            TE_CHECKBREAK_CODE(code);

            code = cursor->getId(&featureId);
            TE_CHECKBREAK_CODE(code);

            code = cursor->getVersion(&featureVersion);
            TE_CHECKBREAK_CODE(code);

            std::shared_ptr<GLBatchGeometry3> glitem;
            std::map<int64_t, GLGeometryRecord>::iterator glitemEntry;
            glitemEntry = this->glSpatialItems.find(featureId);
            if (glitemEntry != this->glSpatialItems.end()) {
                glitem = glitemEntry->second.geometry;

                // the feature is being used on this pump, mark it as touched
                glitemEntry->second.touched = static_cast<QueryContextImpl &>(ctx).queryCount;

                // XXX - this isn't perfect as it doesn't take into
                // account the point scale factor, however
                // performance is probably more important than
                // the small amount of detail we may end up
                // losing
                
                // if we've already simplified the geometry at the
                // current level of detail, just go with what we have.
                if (!this->options.skipSameLodOptimization &&
                    glitem->version == featureVersion &&
                    glitem->lod == lod)
                {
                    FeatureResult r;
                    r.target = glitem;
                    r.updateVersion = featureVersion;
                    result.push_back(std::move(r));
                    continue;
                }
            }

            int type;
            GLBatchGeometry3::BlobPtr blob(nullptr, nullptr);
            GeometryPtr_const geomPtr(nullptr, nullptr);

            if (cursor->getGeomCoding() == FeatureDefinition2::GeomBlob) {
                FeatureDefinition2::RawData  rawGeometry;
                code = cursor->getRawGeometry(&rawGeometry);
                TE_CHECKBREAK_CODE(code);
                const uint8_t *geom = rawGeometry.binary.value;
                if (!geom || !rawGeometry.binary.len)
                    continue;

                std::unique_ptr<uint8_t, void(*)(const uint8_t *)> blobData(new uint8_t[rawGeometry.binary.len], Memory_array_deleter_const<uint8_t>);
                memcpy(blobData.get(), geom, rawGeometry.binary.len);
                blob = GLBatchGeometry3::BlobPtr(new MemoryInput2(), Memory_deleter_const<MemoryInput2>);
                blob->open(std::move(blobData), rawGeometry.binary.len);

                uint8_t b;
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                if (b != 0x00) // marker byte
                    continue;
                // endian
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                switch (b) {
                case 0x00:
                    blob->setSourceEndian2(TE_BigEndian);
                    break;
                case 0x01:
                    blob->setSourceEndian2(TE_LittleEndian);
                    break;
                default:
                    continue;
                }
                // skip the SRID and MBR
                code = blob->skip(36);
                TE_CHECKBREAK_CODE(code);

                // marker byte
                code = blob->readByte(&b);
                TE_CHECKBREAK_CODE(code);
                if (b != 0x7C)
                    // XXX - drop through and process as Geometry class
                    continue;

                code = blob->readInt(&type);
                TE_CHECKBREAK_CODE(code);
            } else {
                if (cursor->getGeomCoding() == FeatureDefinition2::GeomGeometry) {
                    FeatureDefinition2::RawData rawGeom;
                    code = cursor->getRawGeometry(&rawGeom);
                    TE_CHECKBREAK_CODE(code);

                    geomPtr = GeometryPtr_const(static_cast<const atakmap::feature::Geometry *>(rawGeom.object)->clone(), atakmap::feature::destructGeometry);
                } else {
                    const Feature2 *feature;
                    code = cursor->get(&feature);
                    TE_CHECKBREAK_CODE(code);

                    geomPtr = GeometryPtr_const(static_cast<const atakmap::feature::Geometry *>(feature->getGeometry())->clone(), atakmap::feature::destructGeometry);
                }

                atakmap::feature::Geometry::Type gtype = geomPtr->getType();
                if (gtype == atakmap::feature::Geometry::POINT) {
                    type = 1;
                } else if (gtype == atakmap::feature::Geometry::LINESTRING) {
                    type = 2;
                } else if (gtype == atakmap::feature::Geometry::POLYGON) {
                    type = 3;
                } else if (gtype == atakmap::feature::Geometry::COLLECTION) {
                    type = 7;
                } else if (geomPtr == nullptr) {
                    continue;
                } else {
                    return TE_IllegalState;
                }
            }

            if (glitem.get()) {
                switch (type % 1000) {
                    case 1: {
                        const GLBatchPoint3 *gl_batch_point(nullptr);
                        if ((gl_batch_point = dynamic_cast<const GLBatchPoint3 *>(glitem.get())), gl_batch_point == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 2: {
                        const GLBatchLineString3 *gl_line_string(nullptr);
                        if ((gl_line_string = dynamic_cast<const GLBatchLineString3 *>(glitem.get())), gl_line_string == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 3: {
                        const GLBatchPolygon3 *gl_polygon(nullptr);
                        if ((gl_polygon = dynamic_cast<const GLBatchPolygon3 *>(glitem.get())), gl_polygon == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 4: {
                        const GLBatchMultiPoint3 *gl_multi_point(nullptr);
                        if ((gl_multi_point = dynamic_cast<const GLBatchMultiPoint3 *>(glitem.get())), gl_multi_point == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 5: {
                        const GLBatchMultiLineString3 *gl_multi_line_string(nullptr);
                        if ((gl_multi_line_string = dynamic_cast<const GLBatchMultiLineString3 *>(glitem.get())),
                            gl_multi_line_string == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 6: {
                        const GLBatchMultiPolygon3 *gl_multi_polygon(nullptr);
                        if ((gl_multi_polygon = dynamic_cast<const GLBatchMultiPolygon3 *>(glitem.get())), gl_multi_polygon == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                    case 7: {
                        const GLBatchGeometryCollection3 *gl_collection(nullptr);
                        if ((gl_collection = dynamic_cast<const GLBatchGeometryCollection3 *>(glitem.get())), gl_collection == nullptr) {
                            releaseGeometry->push_back(glitem);
                            glitem.reset();
                        }
                    }
                        break;
                }
            }

            FeatureResult r;
            if (!glitem.get()) {
                switch (type % 1000) {
                case 1:
                    glitem.reset(new GLBatchPoint3(this->surface));
                    break;
                case 2:
                    glitem.reset(new GLBatchLineString3(this->surface));
                    break;
                case 3:
                    glitem.reset(new GLBatchPolygon3(this->surface));
                    break;
                case 4:
                    glitem.reset(new GLBatchMultiPoint3(this->surface));
                    break;
                case 5:
                    glitem.reset(new GLBatchMultiLineString3(this->surface));
                    break;
                case 6:
                    glitem.reset(new GLBatchMultiPolygon3(this->surface));
                    break;
                case 7:
                    glitem.reset(new GLBatchGeometryCollection3(this->surface));
                    break;
                default:
                    Logger::log(Logger::Warning, "Geometry type not supported, skipping feature %" PRId64, featureId);
                    continue;
                }

                const char *name;
                code = cursor->getName(&name);
                TE_CHECKBREAK_CODE(code);

                std::shared_ptr<const Style> style;
                code = getStyle(style, *cursor, styleMap);
                TE_CHECKBREAK_CODE(code);

                TAK::Engine::Feature::AltitudeMode altitudeMode = cursor->getAltitudeMode();
                double extrude = cursor->getExtrude();

                if (blob.get())
                    glitem->init(featureId, name, std::move(blob), altitudeMode, extrude, type, lod, style);
                else if (geomPtr.get())
                    glitem->init(featureId, name, std::move(geomPtr), altitudeMode, extrude, style);
                else
                    glitem->init(featureId, name, GeometryPtr_const(nullptr, Memory_leaker_const<Geometry>), altitudeMode, extrude, style);

                this->glSpatialItems[featureId] = { glitem, static_cast<QueryContextImpl &>(ctx).queryCount };
            } else if (glitem->version != featureVersion || lod != glitem->lod) {
                r.altModeUpdate = cursor->getAltitudeMode();
                r.extrudeUpdate = cursor->getExtrude();

                // XXX - geometry only needs to get set on GLBatchPoint once
                if (blob.get()) {
                    r.geomBlobUpdate = std::move(blob);
                    r.geomBlobUpdateType = type;
                    r.geomBlobUpdateLod = lod;
                } else if (geomPtr.get()) {
                    r.geomUpdate = std::move(geomPtr);
                } else {
                    continue; // XXX - 
                }

                // if feature version has changed, refresh name/style
                if (glitem->version != featureVersion)
                {
                    const char *name;
                    code = cursor->getName(&name);
                    TE_CHECKBREAK_CODE(code);

                    r.nameUpdate.reset(new TAK::Engine::Port::String(name));

                    std::shared_ptr<const Style> style;
                    code = getStyle(style, *cursor, styleMap);
                    TE_CHECKBREAK_CODE(code);
                    if (style.get())
                        r.styleUpdate = style;
                }
            }

            r.target = glitem;

            // update the version to reflect the current style and geometry
            r.updateVersion = featureVersion;

                        // only add the result if it's visible
            result.push_back(std::move(r));
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        cursor.reset();

        /*
        long e = System.currentTimeMillis();
        Statistics stats = queryStats.get(lod);
        if (stats == nullptr)
            queryStats.put(lod, stats = new Statistics());
        stats.observe(e - s);

        System.out.println("SPATIAL DB QUERY in " + (e-s) + "ms (avg=" + stats.average +
        ") " + result.size() + " RESULTS lod=" +
        OSMUtils.mapnikTileLevel(state.drawMapResolution) + " simplify factor=" +
        simplifyFactor);
        */
        
        // XXX - do stale check only every N seconds???

        // clean out stale entries
        const int64_t queryCount = static_cast<QueryContextImpl &>(ctx).queryCount;
        auto stale = glSpatialItems.begin();
        
        while (stale != glSpatialItems.end()) {
            bool visible = stale->second.touched == queryCount;
            if (visible != stale->second.visible) {
                stale->second.visible = visible;
                stale->second.geometry->setVisible(visible);
            }
            if ((queryCount - stale->second.touched) > STALE_LIMIT) {
                releaseGeometry->push_back(stale->second.geometry);
                stale = glSpatialItems.erase(stale);
            } else {
                stale++;
            }
        }

        if (releaseGeometry->size() > 0) {
            surface.queueEvent(releaseGLBatchGeometryRunnable, std::unique_ptr<void, void(*)(const void *)>(releaseGeometry.release(), Memory_leaker_const<void>));
        }

        return code;
    } catch (...) {
        // XXX - seeing this get raised pretty consistently on shutdown as
        //       result of race between Database.close() and the worker
        //       thread exiting. Worker thread needs to be join()'d as part
        //       of release(), but this will require some significant effort
        //       to ensure query is aborted in non-blocking way across all
        //       asynchronous renderables. Log exception for 2.2 release.

        //throw new RuntimeException(e);
        Logger::log(Logger::Level::Error, "GLBatchGeometryFeatureDataStoreRenderer2: Unexpected exception");
        return TE_Err;
    }
}

void GLBatchGeometryFeatureDataStoreRenderer2::onDataStoreContentChanged(FeatureDataStore2 &data_store) NOTHROWS
{
    this->invalidate();
}

/**************************************************************************/
// Hit Test Service

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::hitTest2(Collection<int64_t> &fids, const float screenX, const float screenY, const GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS
{
    TAKErr code(TE_Ok);
    Point touchPt(touch.longitude, touch.latitude);

    // lock the features for read
    ReadLock rlock(renderables_mutex_);
    code = rlock.status;
    TE_CHECKRETURN_CODE(code);

    // do hit test
    this->front->hitTest2(
        fids,
        touchPt,
        screenX,
        renderHeightPump-screenY, // vertical flip for GL origin
        resolution,
        static_cast<int>(radius),
        static_cast<int>(limit),
        FeatureDataStore2::FEATURE_ID_NONE);

    return TE_Ok;
}

GLBatchGeometryFeatureDataStoreRenderer2::HitTestImpl::HitTestImpl(GLBatchGeometryFeatureDataStoreRenderer2 &owner_) :
    owner(owner_)
{}

TAKErr GLBatchGeometryFeatureDataStoreRenderer2::HitTestImpl::hitTest(Collection<int64_t> &features, const float screenX, const float screenY, const GeoPoint &touch, const double resolution, const float radius, const std::size_t limit) NOTHROWS
{
    return owner.hitTest2(features, screenX, screenY, touch, resolution, radius, limit);
}

namespace
{
    double distance(double x1, double y1, double x2, double y2)
    {
        double dx = (x1 - x2);
        double dy = (y1 - y2);

        return sqrt(dx * dx + dy * dy);
    }

    TAKErr getStyle(std::shared_ptr<const Style> &value, FeatureCursor2 &cursor, std::map<std::string, std::shared_ptr<const atakmap::feature::Style>> &styleMap) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (cursor.getStyleCoding() == FeatureDefinition2::StyleOgr) {
            FeatureDefinition2::RawData rawStyle;
            cursor.getRawStyle(&rawStyle);
            const char *styleString = rawStyle.text;
            if (styleString) {
                value.reset();
                std::map<std::string, std::shared_ptr<const atakmap::feature::Style>>::const_iterator entry;
                entry = styleMap.find(styleString);
                if (entry == styleMap.end()) {
                    StylePtr_const parsed(nullptr, nullptr);
                    try {
                        parsed = StylePtr_const(atakmap::feature::Style::parseStyle(styleString), atakmap::feature::Style::destructStyle);
                    } catch (...) {}
                    if (parsed.get()) {
                        value = std::move(parsed);
                        styleMap.insert(std::pair<std::string, std::shared_ptr<const atakmap::feature::Style>>(styleString, value));
                    }
                } else {
                    value = entry->second;
                }
            }
        } else if (cursor.getStyleCoding() == FeatureDefinition2::StyleStyle) {
            FeatureDefinition2::RawData rawStyle;
            code = cursor.getRawStyle(&rawStyle);
            TE_CHECKRETURN_CODE(code);

            if (rawStyle.object) {
                value = StylePtr_const(static_cast<const Style *>(rawStyle.object)->clone(), Style::destructStyle);
            } else {
                value.reset();
            }
        } else {
            const Feature2 *f;
            code = cursor.get(&f);
            TE_CHECKRETURN_CODE(code);

            if(f->getStyle())
                value = StylePtr_const(f->getStyle()->clone(), Style::destructStyle);
            else
                value = StylePtr_const(nullptr, Memory_leaker_const<Style>);
        }

        return code;
    }

    void releaseGLBatchGeometryRunnable(void *opaque) NOTHROWS
    {
        std::unique_ptr<std::vector<std::shared_ptr<GLBatchGeometry3>>> releaseGeometry(static_cast<std::vector< std::shared_ptr<GLBatchGeometry3>> *>(opaque));
        auto it = releaseGeometry->begin();
        auto end = releaseGeometry->end();
        while (it != end) {
            (*it)->release();
            ++it;
        }
    }

    QueryContextImpl::QueryContextImpl() :
        queryCount(0LL)
    {}
}
