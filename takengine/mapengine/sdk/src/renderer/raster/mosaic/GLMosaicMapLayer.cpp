#include "renderer/raster/mosaic/GLMosaicMapLayer.h"
#include "feature/LegacyAdapters.h"
#include "feature/SpatialCalculator2.h"
#include "port/Collections.h"
#include "port/STLSetAdapter.h"
#include "raster/PrecisionImageryFactory.h"
#include "raster/mosaic/MosaicDatabaseFactory2.h"
#include "raster/tilereader/TileReaderFactory2.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/raster/RasterDataAccessControl.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"
#include "util/ConfigOptions.h"
#include "util/Filter.h"

#include <sstream>

using namespace TAK::Engine;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::TileReader;
using namespace TAK::Engine::Raster::Mosaic;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Raster::Mosaic;
using namespace TAK::Engine::Renderer::Raster::TileReader;
using atakmap::raster::DatasetDescriptor;
using atakmap::raster::MosaicDatasetDescriptor;

/*****************************************************************************/
// Anonymous namespace

namespace {
    struct MosaicPendingData : public GLAsynchronousMapRenderable3::QueryContext
    {
        /**
         * holds the results of the query.
         * Each result may hold a renderable that is <I>instantiated</I> (not
         * <I>initialized</I>) during the query. The renderables should not be associated with
         * any of the currently rendered frames as per {@link #loaded}.
         */
        std::list<std::pair<MosaicDatabase2::Frame, GLQuadTileNode2Ptr>> frames;
        /** calculates occlusion */
        Feature::SpatialCalculator2 spatialCalc;
        /** list of currently rendered frame URIs */
        std::set<std::string> loaded;

        MosaicDatabase2Ptr database;

        MosaicPendingData();
        ~MosaicPendingData() override;
    };

    bool stringStartsWith(const std::string &s, const std::string &preface);
    bool intersects(const GLAsynchronousMapRenderable3::ViewState &state, const MosaicDatabase2::Frame &frame);
    Feature::Point2 toPoint(const GeoPoint2 geo);
    TAKErr getDatasetProjection(DatasetProjection2Ptr &projection, const Raster::Mosaic::MosaicDatabase2::Frame *frame);
    void renderThreadCleaner(void *opaque) NOTHROWS;
}

/*****************************************************************************/
// Private internal class definitions

class GLMosaicMapLayer::GLQuadTileNodeInitializer : public Renderer::Raster::TileReader::GLQuadTileNode2::Initializer
{
   public:
    GLQuadTileNodeInitializer(GLMosaicMapLayer *owner);
    ~GLQuadTileNodeInitializer() override;
    Util::TAKErr init(TAK::Engine::Raster::TileReader::TileReader2Ptr &reader,
                              TAK::Engine::Raster::DatasetProjection2Ptr &imprecise, TAK::Engine::Raster::DatasetProjection2Ptr &precise,
                              const TAK::Engine::Raster::ImageInfo *info,
                              TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts) const override;

   private:
    GLMosaicMapLayer *owner;
};

class GLMosaicMapLayer::QueryArgument : public GLAsynchronousMapRenderable3::ViewState
{
   public:
    double minGSD;
    double maxGSD;
    bool valid;

    QueryArgument(GLMosaicMapLayer *owner);
    ~QueryArgument() override;
    void set(const GLMapView2 &view) override;
    void copy(const ViewState &view) override;

   private:
    GLMosaicMapLayer *owner;
};

class GLMosaicMapLayer::RenderableListIter : public Port::Iterator2<GLMapRenderable2 *>
{
   public:
    RenderableListIter(GLMosaicMapLayer *owner) NOTHROWS;

    ~RenderableListIter() NOTHROWS override;

    TAKErr next() NOTHROWS override;
    TAKErr get(GLMapRenderable2 *&value) const NOTHROWS override;

   private:
    GLMosaicMapLayer *owner;
    SortedFrameMap *curMap;
    SortedFrameMap::iterator curIter;
};

class GLMosaicMapLayer::SelectControlImpl : public Renderer::Raster::ImagerySelectionControl
{
   public:
    SelectControlImpl(GLMosaicMapLayer *owner) NOTHROWS;
    ~SelectControlImpl() NOTHROWS override;
    void setResolutionSelectMode(Mode mode) NOTHROWS override;
    Mode getResolutionSelectMode() NOTHROWS override;
    void setFilter(Port::Set<Port::String> &filter) NOTHROWS override;

   private:
    GLMosaicMapLayer *owner;
};

class ENGINE_API GLMosaicMapLayer::RasterDataAccessControlImpl : public Renderer::Raster::RasterDataAccessControl
{
   public:
    RasterDataAccessControlImpl(GLMosaicMapLayer *owner) NOTHROWS;
    ~RasterDataAccessControlImpl() NOTHROWS override;
    Util::TAKErr accessRasterData(void (*accessVisitor)(void *opaque, RasterDataAccess2 *access),
                                    void *opaque, const GeoPoint2 &point) NOTHROWS override;

   private:
    GLMosaicMapLayer *owner;
};

/*****************************************************************************/
// GLMosaicMapLayer's own public API

GLMosaicMapLayer::GLMosaicMapLayer(TAK::Engine::Core::RenderContext *context, MosaicDatasetDescriptor *info) NOTHROWS
    : asyncio(nullptr),
      ownsIO(false),
      textureCacheEnabled(false),
      controls(),
      visibleFrames(frameSort),
      zombie_frames_(frameSort),
      resurrected_frames_(),
      info_(nullptr),
      selected_type_(),
      surface_(nullptr),
      raster_access_control_(nullptr),
      select_control_(nullptr),
      resolution_select_mode_(),
      imagery_type_filter_()
{
    commonInit(context, info);
}

GLMosaicMapLayer::GLMosaicMapLayer(TAK::Engine::Core::MapRenderer *renderer, MosaicDatasetDescriptor *info) NOTHROWS
    : asyncio(nullptr),
      ownsIO(false),
      textureCacheEnabled(false),
      controls(),
      visibleFrames(frameSort),
      zombie_frames_(frameSort),
      resurrected_frames_(),
      info_(nullptr),
      selected_type_(),
      surface_(nullptr),
      raster_access_control_(nullptr),
      select_control_(nullptr),
      resolution_select_mode_(),
      imagery_type_filter_()
{
    commonInit(&static_cast<Core::GLMapView2 *>(renderer)->context, info);
}

GLMosaicMapLayer::~GLMosaicMapLayer() NOTHROWS
{
    release();
    this->asyncio.reset();
}

Util::TAKErr GLMosaicMapLayer::toString(TAK::Engine::Port::String &value) NOTHROWS
{
    std::stringstream sstr;
    sstr << "GLMosaicMapLayer@" << static_cast<void *>(this) << " " << this->info_->getName();
    value = sstr.str().c_str();
    return TE_Ok;
}

Util::TAKErr GLMosaicMapLayer::createRootNode(GLQuadTileNode2Ptr &value, const MosaicDatabase2::Frame &frame) NOTHROWS
{
    std::string tileCacheDatabase;
    if (this->info_->getExtraData("tilecacheDir") != nullptr) {
        const char *tilecachePath = this->info_->getExtraData("tilecacheDir");
        tileCacheDatabase = tilecachePath;
        tileCacheDatabase += Port::Platform_pathSep();
        tileCacheDatabase += std::to_string(frame.id);
    }

    ::TileReader::TileReaderFactory2Options opts;
    opts.asyncIO = this->asyncio;
    opts.cacheUri = tileCacheDatabase.c_str();
    opts.preferredTileWidth = 512;
    opts.preferredTileHeight = 512;

    Renderer::Raster::TileReader::GLQuadTileNode2::Options glopts;
    glopts.levelTransitionAdjustment = -1.0 * ConfigOptions_getDoubleOptionOrDefault("imagery.zoom-level-adjust", 0.0);
    //        glopts.levelTransitionAdjustment = 0.5d;
    if (this->textureCacheEnabled)
        Core::GLMapRenderGlobals_getTextureCache2(&glopts.textureCache, *this->surface_);

    TileReader::GLQuadTileNode2Ptr retval(nullptr, nullptr);
    GLQuadTileNodeInitializer initializer(this);
    TAKErr code = TileReader::GLQuadTileNode2::create(retval, this->surface_, &frame, opts, glopts, initializer);
    TE_CHECKRETURN_CODE(code);
    value = std::move(retval);
    return TE_Ok;
}

void GLMosaicMapLayer::release() NOTHROWS
{
    this->visibleFrames.clear();
    this->zombie_frames_.clear();
    this->resurrected_frames_.clear();
    GLAsynchronousMapRenderable3::release();
}

/*****************************************************************************/
// GLMapLayer2

const char *GLMosaicMapLayer::getLayerUri() const NOTHROWS
{
    return this->info_->getURI();
}

const DatasetDescriptor *GLMosaicMapLayer::getInfo() const NOTHROWS
{
    return this->info_;
}

Util::TAKErr GLMosaicMapLayer::getControl(void **ctrl, const char *type) const NOTHROWS
{
    if (!type)
        return TE_InvalidArg;

    auto iter = this->controls.find(type);
    if (iter == this->controls.end())
        return TE_InvalidArg;

    *ctrl = iter->second;
    return TE_Ok;
}

void GLMosaicMapLayer::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    Thread::Monitor::Lock lock(monitor_);

    SortedFrameMap::iterator iter;

    bool allResolved = true;
    iter = this->visibleFrames.begin();
    while (iter != this->visibleFrames.end()) {
        allResolved &= (iter->second->getState() == GLQuadTileNode2::State::RESOLVED);
        iter++;
    }

    // clean up the zombie list
    iter = this->zombie_frames_.begin();
    while (iter != this->zombie_frames_.end()) {
        auto zombieIter = iter;
        iter++;
        if (allResolved || zombieIter->second->getState() == GLQuadTileNode2::State::UNRESOLVED ||
            zombieIter->second->getState() == GLQuadTileNode2::State::UNRESOLVABLE ||
            !intersects(*this->prepared_state_, zombieIter->first)) {
            // all visible frames are resolved, the zombie frame has no
            // data or has fallen out of the ROI; release it
            zombieIter->second->release();
            this->zombie_frames_.erase(zombieIter);
        } else if (zombieIter->second->getState() == GLQuadTileNode2::State::RESOLVING) {
            zombieIter->second->suspend();
        }
    }

    // resurrected frames have already been moved from the zombie or
    // retention lists to the visible list; we just need to tell them to
    // resume
    auto resurrectedIter = this->resurrected_frames_.begin();
    while (resurrectedIter != this->resurrected_frames_.end()) {
        (*resurrectedIter)->resume();
        resurrectedIter++;
    }
    this->resurrected_frames_.clear();

    // apply any color mmodulation settings
#if 0
    this->colorControlImpl->apply();
#endif

    GLAsynchronousMapRenderable3::draw(view, renderPass);

    // System.out.println("$$ GLGdalMosaicMapLayer [" + this.info.getName() + "] draw " + this.renderable.size());
}

int GLMosaicMapLayer::getRenderPass() NOTHROWS
{
    return GLMapView2::Surface;
}

void GLMosaicMapLayer::start() NOTHROWS {}

void GLMosaicMapLayer::stop() NOTHROWS {}

/*****************************************************************************/
// GLAsynchronousMapRenderable3

Util::TAKErr GLMosaicMapLayer::createQueryContext(QueryContextPtr &value) NOTHROWS
{
    MosaicDatasetDescriptor *mosaic = this->info_;
    std::unique_ptr<MosaicPendingData, void (*)(const QueryContext *)> retval(new MosaicPendingData(),
                                                                              Memory_deleter_const<QueryContext, MosaicPendingData>);
    MosaicPendingData *m = retval.get();
    value = std::move(retval);

    TAKErr code = MosaicDatabaseFactory2_create(m->database, mosaic->getMosaicProvider());
    TE_CHECKLOGRETURN_CODE2(code, LogLevel::TELL_Error, "Failed to open mosaic %s", mosaic->getMosaicPath());
    code = m->database->open(mosaic->getMosaicPath());
    TE_CHECKLOGRETURN_CODE2(code, LogLevel::TELL_Error, "Failed to open mosaic %s", mosaic->getMosaicPath());
    return TE_Ok;
}

Util::TAKErr GLMosaicMapLayer::resetQueryContext(QueryContext &pendingDataOpaque) NOTHROWS
{
    auto &pendingData = static_cast<MosaicPendingData &>(pendingDataOpaque);
    pendingData.frames.clear();
    pendingData.spatialCalc.clear();

    pendingData.loaded.clear();
    for (auto iter = this->visibleFrames.begin(); iter != visibleFrames.end(); iter++) {
        Port::String resolved;
        TAKErr code = this->resolvePath(resolved, iter->first.path);
        TE_CHECKRETURN_CODE(code);
        pendingData.loaded.insert(resolved.get());
    }
    for (auto iter = this->zombie_frames_.begin(); iter != zombie_frames_.end(); iter++) {
        Port::String resolved;
        TAKErr code = this->resolvePath(resolved, iter->first.path);
        TE_CHECKRETURN_CODE(code);
        pendingData.loaded.insert(resolved.get());
    }
    return TE_Ok;
}

// Java - updateRenderableReleaseLists()
Util::TAKErr GLMosaicMapLayer::updateRenderableLists(QueryContext &pendingDataOpaque) NOTHROWS
{
    auto &pendingData = static_cast<MosaicPendingData &>(pendingDataOpaque);

    // holds renderables that will be the new 'visibleFrames'
    SortedFrameMap swap(frameSort);

    // move any previously visible nodes into our nowVisible map
    for (std::pair<MosaicDatabase2::Frame, GLQuadTileNode2Ptr> &framePair : pendingData.frames) {
        std::shared_ptr<GLQuadTileNode2> mapRenderable;
        SortedFrameMap::iterator iter;
        if ((iter = this->visibleFrames.find(framePair.first)) != this->visibleFrames.end()) {
            mapRenderable = std::move(iter->second);
            this->visibleFrames.erase(iter);
        } else if ((iter = this->zombie_frames_.find(framePair.first)) != this->zombie_frames_.end()) {
            mapRenderable = std::move(iter->second);
            this->zombie_frames_.erase(iter);

            // the frame was a zombie, it needs to be marked as resurrected
            this->resurrected_frames_.push_back(mapRenderable);
        } else if (framePair.second != nullptr) {
            mapRenderable = std::move(framePair.second);

            // apply color modulation to the newly created renderable
#if 0
            ((ColorControlImpl)this.colorControl).apply(frame, mapRenderable);
#endif
        } else {
            // the frame is not in any of the lists and intersects the view,
            // create a node for it
            // not checking error intentionally; error will be handled with null check below
            GLQuadTileNode2Ptr root(nullptr, nullptr);
            this->createRootNode(root, framePair.first);
            if (root.get())
                mapRenderable = std::move(root);

            // apply color modulation to the newly created renderable
#if 0
            ((ColorControlImpl)this.colorControl).apply(frame, mapRenderable);
#endif
        }

        // add it to the list of frames that are now visible
        if (mapRenderable != nullptr)
            swap.insert(SortedFrameMap::value_type(framePair.first, mapRenderable));
    }
    // NOTE: the renderables contained in the frame list that we didn't use have been instantiated but
    //       not yet initialized. This is an important distinction as we
    //       must invoke release (or, destruct as here) to free any non-GL resources owned by the
    //       renderable but we may do so OFF of the GL thread
    pendingData.frames.clear();

    for (auto releaseIter = this->visibleFrames.begin(); releaseIter != this->visibleFrames.end();) {
        auto curIter = releaseIter;
        releaseIter++;
        // if the frame marked for release intersects the current ROI, make
        // it a zombie
        if (curIter->second->getState() != GLQuadTileNode2::State::UNRESOLVED &&
            curIter->second->getState() != GLQuadTileNode2::State::UNRESOLVABLE && intersects(*this->target_state_, curIter->first)) {
            this->zombie_frames_.insert(SortedFrameMap::value_type(curIter->first, std::move(curIter->second)));
            this->visibleFrames.erase(curIter);
        }
    }

    // retain a reference to the renderables that need to be released
    std::unique_ptr<std::list<std::shared_ptr<GLQuadTileNode2>>> releaseList(new std::list<std::shared_ptr<GLQuadTileNode2>>());
    for (auto iter = this->visibleFrames.begin(); iter != this->visibleFrames.end(); iter++) {
        releaseList->push_back(std::move(iter->second));
    }

    // set visible to the swap map
    this->visibleFrames.swap(swap);

    if (releaseList->size() > 0) {
        this->surface_->queueEvent(renderThreadCleaner,
                                   std::unique_ptr<void, void (*)(const void *)>(releaseList.release(), Memory_leaker_const<void>));
    }

    // long e = android.os.SystemClock.elapsedRealtime();
    // System.out.println("GLGdalMosaicMapLayer@" + Integer.toString(this.hashCode(), 16) + " updateRenderableList in " + (e-s) + "ms");
    return TE_Ok;
}

Util::TAKErr GLMosaicMapLayer::releaseImpl() NOTHROWS
{
    GLAsynchronousMapRenderable3::releaseImpl();
    if (this->ownsIO)
        this->asyncio->release();
    return TE_Ok;
}

Util::TAKErr GLMosaicMapLayer::query(QueryContext &result, const ViewState &state) NOTHROWS
{
    auto &retval = static_cast<MosaicPendingData &>(result);

    if (retval.database == nullptr)
        return TE_Ok;

    auto &localQuery = (QueryArgument &)state;
    if (!localQuery.valid) {
        Logger_log(LogLevel::TELL_Debug, "QUERY INVALID!!!");
        return TE_Ok;
    }

    if (state.crossesIDL) {
        std::map<MosaicDatabase2::Frame, GLQuadTileNode2Ptr, decltype(frameSort) *> frameMap(frameSort);
        TAKErr code;

        QueryArgument hemi(this);

        // west of IDL
        hemi.copy(state);
        hemi.eastBound = 180.0;
        hemi.upperLeft = GeoPoint2(hemi.northBound, hemi.westBound);
        hemi.upperRight = GeoPoint2(hemi.northBound, hemi.eastBound);
        hemi.lowerRight = GeoPoint2(hemi.southBound, hemi.eastBound);
        hemi.lowerLeft = GeoPoint2(hemi.southBound, hemi.westBound);
        code = this->queryImpl(retval, hemi);
        TE_CHECKRETURN_CODE(code);
        using mvIter = std::move_iterator<std::list<std::pair<MosaicDatabase2::Frame, GLQuadTileNode2Ptr>>::iterator>;
        frameMap.insert(mvIter(retval.frames.begin()), mvIter(retval.frames.end()));

        // reset
        retval.frames.clear();

        // east of IDL
        hemi.copy(localQuery);
        hemi.westBound = -180.0;
        hemi.upperLeft = GeoPoint2(hemi.northBound, hemi.westBound);
        hemi.upperRight = GeoPoint2(hemi.northBound, hemi.eastBound);
        hemi.lowerRight = GeoPoint2(hemi.southBound, hemi.eastBound);
        hemi.lowerLeft = GeoPoint2(hemi.southBound, hemi.westBound);
        code = this->queryImpl(retval, hemi);
        TE_CHECKRETURN_CODE(code);
        frameMap.insert(mvIter(retval.frames.begin()), mvIter(retval.frames.end()));

        retval.frames.clear();

        using mapMvIter = std::move_iterator<std::map<MosaicDatabase2::Frame, GLQuadTileNode2Ptr>::iterator>;
        retval.frames.insert(retval.frames.end(), mapMvIter(frameMap.begin()), mapMvIter(frameMap.end()));
        return TE_Ok;
    } else {
        return queryImpl(retval, localQuery);
    }
}

Util::TAKErr GLMosaicMapLayer::newViewStateInstance(ViewStatePtr &value) NOTHROWS
{
    value = ViewStatePtr(new QueryArgument(this), Memory_deleter_const<ViewState, QueryArgument>);
    return TE_Ok;
}

Util::TAKErr GLMosaicMapLayer::getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS
{
    std::stringstream sstr;
    sstr << "Mosaic [" << this->info_->getName() << "] GL worker@" << (void *)this;
    std::string s = sstr.str();
    value = s.c_str();
    return TE_Ok;
}

void GLMosaicMapLayer::initImpl(const GLMapView2 &view) NOTHROWS {}

// Java - checkState()
bool GLMosaicMapLayer::shouldQuery() NOTHROWS
{
    auto *prepared = static_cast<QueryArgument *>(this->prepared_state_.get());
    auto *target = static_cast<QueryArgument *>(this->target_state_.get());

    // System.out.println("GLGdalMosaicMapLayer checkState prepared.valid=" + (prepared.valid != target.valid) + " prepared.types=" +
    // !equals(prepared.types, target.types) + " super=" + super.checkState());

    return (prepared->valid != target->valid) || GLAsynchronousMapRenderable3::shouldQuery();
}

bool GLMosaicMapLayer::shouldCancel() NOTHROWS
{
    auto *prepared = static_cast<QueryArgument *>(this->prepared_state_.get());
    auto *target = static_cast<QueryArgument *>(this->target_state_.get());
    double delta = log(target->drawMapResolution / prepared->drawMapResolution) / log(2.0);
    if(abs(delta) > 1.0) {
        return true;
    }

    return false;
}

Util::TAKErr GLMosaicMapLayer::getRenderables(Port::Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS
{
    iter = Port::Collection<GLMapRenderable2 *>::IteratorPtr(new RenderableListIter(this),
                                                             Memory_deleter_const<Port::Iterator2<GLMapRenderable2 *>, RenderableListIter>);

    return TE_Ok;
}

/*****************************************************************************/
// GLMosaicMapLayer's own private internal methods


void GLMosaicMapLayer::commonInit(TAK::Engine::Core::RenderContext *context, MosaicDatasetDescriptor *info) NOTHROWS
{
    this->surface_ = context;
    this->info_ = info;
    this->resolution_select_mode_ = ImagerySelectionControl::Mode::MinimumResolution;

#if 0
    // XXX getLocalData gives back const, so this is not possible without discarding const
    if (info_->getLocalData("asyncio") != nullptr) {
        this->asyncio = info->getLocalData<::Raster::TileReader::TileReader2::AsynchronousIO>("asyncio");
        this.ownsIO = false;
    } else
#endif
    if (ConfigOptions_getIntOptionOrDefault("imagery.single-async-io", 0) != 0) {
        this->asyncio = std::shared_ptr<TileReader2::AsynchronousIO>(TileReader2_getMasterIOThread(), TAK::Engine::Util::Memory_leaker<TileReader2::AsynchronousIO>);
        this->ownsIO = false;
    } else {
        this->asyncio = std::make_shared<TileReader2::AsynchronousIO>();
        this->ownsIO = true;
    }

    this->textureCacheEnabled = (ConfigOptions_getIntOptionOrDefault("imagery.texture-cache", 1) != 0);
    this->raster_access_control_ = std::make_unique<RasterDataAccessControlImpl>(this);
    this->select_control_ = std::make_unique<SelectControlImpl>(this);

    this->controls["TAK.Engine.Renderer.Raster.RasterDataAccessControl"] = this->raster_access_control_.get();
    this->controls["TAK.Engine.Renderer.Raster.ImagerySelectionControl"] = this->select_control_.get();
}

TAKErr GLMosaicMapLayer::constructQueryParams(std::list<MosaicDatabase2::QueryParameters> *retval, const QueryArgument &localQuery) NOTHROWS
{
    TAKErr code(TE_Ok);

    MosaicDatabase2::QueryParameters base;
    atakmap::feature::Geometry *geo =
        DatasetDescriptor::createSimpleCoverage(localQuery.upperLeft, localQuery.upperRight, localQuery.lowerRight, localQuery.lowerLeft);
    code = Feature::LegacyAdapters_adapt(base.spatialFilter, *geo);
    TE_CHECKRETURN_CODE(code);

    if (!this->imagery_type_filter_.empty())
        base.types = std::unique_ptr<Port::Set<Port::String>, void (*)(const Port::Set<Port::String> *)>(
            new Port::STLSetAdapter<Port::String>(this->imagery_type_filter_),
            Memory_deleter_const<Port::Set<Port::String>, Port::STLSetAdapter<Port::String>>);

    const ImagerySelectionControl::Mode resSelectMode = this->resolution_select_mode_;
    switch (resSelectMode) {
        case ImagerySelectionControl::Mode::IgnoreResolution:
            retval->push_back(base);
            break;
        case ImagerySelectionControl::Mode::MaximumResolution:
            base.maxGsd = localQuery.drawMapResolution / 2.0;
            base.maxGsdCompare = MosaicDatabase2::QueryParameters::GsdCompare::MaximumGsd;
            retval->push_back(base);
            break;
        case ImagerySelectionControl::Mode::MinimumResolution:
            MosaicDatabase2::QueryParameters asc(base);
            asc.maxGsd = localQuery.drawMapResolution / 2.0;
            asc.maxGsdCompare = MosaicDatabase2::QueryParameters::GsdCompare::MinimumGsd;
            asc.order = MosaicDatabase2::QueryParameters::Order::MaxGsdDesc;
            retval->push_back(asc);
            MosaicDatabase2::QueryParameters desc(base);
            desc.minGsd = localQuery.drawMapResolution / 2.0;
            desc.minGsdCompare = MosaicDatabase2::QueryParameters::GsdCompare::MaximumGsd;
            desc.maxGsd = localQuery.drawMapResolution / 16.0;
            asc.maxGsdCompare = MosaicDatabase2::QueryParameters::GsdCompare::MinimumGsd;
            desc.order = MosaicDatabase2::QueryParameters::Order::MaxGsdAsc;
            retval->push_back(desc);
            break;
    }

    return TE_Ok;
}

TAKErr GLMosaicMapLayer::queryImpl(QueryContext &retvalqc, const QueryArgument &localQuery) NOTHROWS
{
    TAKErr code(TE_Ok);
    auto &retval = static_cast<MosaicPendingData &>(retvalqc);

    // System.out.println("QUERY ON " + localQuery.upperLeft + " " + localQuery.lowerRight + " @ " + localQuery.drawMapResolution);

    // compute the amount of buffering to be added around the bounds of the
    // frame -- target 2 pixels
    const auto deltaPxX = (double)(localQuery._right - localQuery._left);
    const auto deltaPxY = (double)(localQuery._top - localQuery._bottom);
    const double distancePx = sqrt(deltaPxX * deltaPxX + deltaPxY * deltaPxY);
    const double deltaLat = localQuery.northBound - localQuery.southBound;
    const double deltaLng = localQuery.eastBound - localQuery.westBound;
    const double distanceDeg = sqrt(deltaLat * deltaLat + deltaLng * deltaLng);

    const double bufferArg = (distanceDeg / distancePx) * 2;

    MosaicDatabase2::CursorPtr result(nullptr, nullptr);

    // long s = android.os.SystemClock.elapsedRealtime();

    // int processed = 0;
    // wrap everything in a transaction
    retval.spatialCalc.beginBatch();
    do {
        int64_t viewHandle;
        code = retval.spatialCalc.createPolygon(&viewHandle, toPoint(localQuery.upperLeft), toPoint(localQuery.upperRight),
                                                toPoint(localQuery.lowerLeft), toPoint(localQuery.lowerRight));
        TE_CHECKBREAK_CODE(code);
        int64_t coverageHandle = 0;
        int64_t frameHandle;

        std::list<MosaicDatabase2::QueryParameters> params;
        code = this->constructQueryParams(&params, localQuery);
        TE_CHECKBREAK_CODE(code);

        bool isComplete = false;
        bool haveType = false;
        Port::String selectedTypeBuffer;

        for (MosaicDatabase2::QueryParameters &p : params) {
            // query the database over the spatial ROI
            code = retval.database->query(result, p);
            TE_CHECKBREAK_CODE(code);

            while ((code = result->moveToNext()) == TE_Ok) {
                if (this->cancelled_) {
                    break;
                }

                GeoPoint2 ul, ur, lr, ll;
                code = result->getUpperLeft(&ul);
                TE_CHECKBREAK_CODE(code);
                code = result->getUpperRight(&ur);
                TE_CHECKBREAK_CODE(code);
                code = result->getLowerLeft(&ll);
                TE_CHECKBREAK_CODE(code);
                code = result->getLowerRight(&lr);
                TE_CHECKBREAK_CODE(code);
                code = retval.spatialCalc.createPolygon(&frameHandle, toPoint(ul), toPoint(ur), toPoint(lr), toPoint(ll));
                TE_CHECKBREAK_CODE(code);

                if (coverageHandle != 0) {
                    // if the current coverage contains the frame, skip it
                    bool contained;
                    TAKErr spatialCode = retval.spatialCalc.contains(&contained, coverageHandle, frameHandle);
                    if (spatialCode != TE_Ok)
                        Logger_log(LogLevel::TELL_Error, "SpatialCalculation error: %d", spatialCode);
                    else if (contained)
                        continue;
                }

                MosaicDatabase2::FramePtr_const frame(nullptr, nullptr);
                code = MosaicDatabase2::Frame::createFrame(frame, *result);
                TE_CHECKBREAK_CODE(code);
                retval.frames.push_back(
                    std::pair<MosaicDatabase2::Frame, GLQuadTileNode2Ptr>(*frame, GLQuadTileNode2Ptr(nullptr, nullptr)));

                if (coverageHandle == 0) {
                    coverageHandle = frameHandle;
                } else {
                    // create the union of the current coverage plus the frame.
                    // specify a small buffer on the frame to try to prevent
                    // numerically insignificant seams between adjacent frames
                    // that would result in effectively occluded frames ending
                    // up in the render list
                    TAKErr spatialCode = retval.spatialCalc.updateBuffer(frameHandle, bufferArg, frameHandle);
                    if (spatialCode != TE_Ok)
                        Logger_log(LogLevel::TELL_Error, "SpatialCalculation error: %d", spatialCode);
                    else
                        spatialCode = retval.spatialCalc.updateUnion(coverageHandle, frameHandle, coverageHandle);
                    if (spatialCode != TE_Ok)
                        Logger_log(LogLevel::TELL_Error, "SpatialCalculation error: %d", spatialCode);
                }

                if (!haveType) {
                    const char *selectedType;
                    TAKErr typeCode = result->getType(&selectedType);
                    if (typeCode == TE_Ok) {
                        selectedTypeBuffer = selectedType;
                        haveType = true;
                    }
                }

                // if the current coverage contains the ROI, break
                {
                    bool contained;
                    TAKErr spatialCode = retval.spatialCalc.contains(&contained, coverageHandle, viewHandle);
                    if (spatialCode != TE_Ok) {
                        Logger_log(LogLevel::TELL_Error, "SpatialCalculation error: %d", spatialCode);
                    } else if (contained) {
                        isComplete = true;
                        break;
                    }
                }
            }
            // Done is a-ok
            if (code == TE_Done)
                code = TE_Ok;
            // If error condition - break outer loop
            TE_CHECKBREAK_CODE(code);
            if (isComplete || this->cancelled_)
                break;
        }

        if (haveType) {
            this->selected_type_ = selectedTypeBuffer;
            this->info_->setLocalData("selectedType", this->selected_type_.get());
        } else {
            this->info_->setLocalData("selectedType", nullptr);
        }
    } while (false);
    // dump everything -- we don't need it to persist
    retval.spatialCalc.endBatch(false);
    // checking if we broke out due to error or not
    TE_CHECKRETURN_CODE(code);

    // instantiate (but do not initialize) renderables for all frames that
    // are not currently loaded
    for (std::pair<MosaicDatabase2::Frame, GLQuadTileNode2Ptr> &framePair : retval.frames) {
        if (this->cancelled_) {
            break;
        }
        Port::String resolved;
        code = this->resolvePath(resolved, framePair.first.path);
        TE_CHECKRETURN_CODE(code);
        if (retval.loaded.find(resolved.get()) != retval.loaded.end())
            continue;

        GLQuadTileNode2Ptr renderable(nullptr, nullptr);
        this->createRootNode(renderable, framePair.first);
        if (renderable != nullptr)
            framePair.second = std::move(renderable);
    }
    // long e = android.os.SystemClock.elapsedRealtime();

    if (this->cancelled_) {
        retval.frames.erase(
            std::remove_if(retval.frames.begin(), retval.frames.end(), [](const auto &p) { return p.second.get() == nullptr; }),
            retval.frames.end());

        return TE_Canceled;
    }

    // System.out.println("PROCESSED " + processed + " in " + (e-s) + "ms");
    // System.out.println("got " + retval.frames.size() + " results");
    return TE_Ok;
}

TAKErr GLMosaicMapLayer::resolvePath(Port::String &value, const Port::String &path) NOTHROWS
{
#if 0
    std::string s = path.get();
    if (stringStartsWith(s, "file:///"))
        s = s.substr(7);
    else if (stringStartsWith(s, "file://"))
        s = s.substr(6);
    else if (stringStartsWith(s, "zip://"))
        s = s.replace(0, 6, "/vsizip/");
    value = s.c_str();
#else
    value = path.get();
#endif
    return TE_Ok;
}

// Porting notes: Java comparitor is <0 if f0 before f1, 0 if equal or >0 if f1 before f0
// std sort comparitor wants true iff f0 before f1
bool GLMosaicMapLayer::frameSort(const MosaicDatabase2::Frame &f0, const MosaicDatabase2::Frame &f1)
{
    if (f0.maxGsd < f1.maxGsd)
        return false;
    else if (f0.maxGsd > f1.maxGsd)
        return true;
    int retval = strcmp(f0.type.get(), f1.type.get());
    if (retval == 0)
        retval = strcmp(f0.path.get(), f1.path.get());
    return retval < 0;
}

/*****************************************************************************/
// Private internal class - GLQuadTileNodeInitializer

GLMosaicMapLayer::GLQuadTileNodeInitializer::GLQuadTileNodeInitializer(GLMosaicMapLayer *owner) : owner(owner) {}

GLMosaicMapLayer::GLQuadTileNodeInitializer::~GLQuadTileNodeInitializer() {}
TAKErr GLMosaicMapLayer::GLQuadTileNodeInitializer::init(TAK::Engine::Raster::TileReader::TileReader2Ptr &reader,
                                                         TAK::Engine::Raster::DatasetProjection2Ptr &imprecise,
                                                         TAK::Engine::Raster::DatasetProjection2Ptr &precise,
                                                         const TAK::Engine::Raster::ImageInfo *info,
                                                         TAK::Engine::Raster::TileReader::TileReaderFactory2Options &readerOpts) const
{
    TAKErr code(TE_Ok);
    const auto *frame =
        static_cast<const TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame *>(info);

    // try to create a TileReader for the frame. we will attempt using 
    // any preferred provider first then try a general open
    reader.reset();
    do {
        code = TAK::Engine::Raster::TileReader::TileReaderFactory2_create(reader, frame->path.get(), &readerOpts);
        if (code == TE_Ok || readerOpts.preferredSpi == nullptr)
            break;
        readerOpts.preferredSpi = nullptr;
    } while (true);

    if (reader == nullptr)
        return TE_Done;

    code = getDatasetProjection(imprecise, frame);
    if (code != TE_Ok) {
        reader.reset();
        return code;
    }

    // XXX - defer to frame property once insert supports specifying precise
#define MOSAICDB_HAS_PRECISIONIMAGERY_INFO 0

#if MOSAICDB_HAS_PRECISIONIMAGERY_INFO
    if (frame->precisionImagery) {
        do {
            Port::String resolved;
            code = owner->resolvePath(resolved, frame->path);
            if (code != TE_Ok)
                break;
#else
    {
        do {
            // not setting `code` to avoid excessive logging since each frame
            // must be checked when the info isn't recorded in the mosaic db
            Port::String resolved;
            if (owner->resolvePath(resolved, frame->path) != TE_Ok)
                break;
            if (PrecisionImageryFactory_isSupported(resolved) != TE_Ok)
                break;
#endif
            PrecisionImageryPtr p(nullptr, nullptr);
            code = PrecisionImageryFactory_create(p, resolved);
            TE_CHECKBREAK_CODE(code);
            code = p->getDatasetProjection(precise);
            TE_CHECKBREAK_CODE(code);
        } while (false);
        if (code != TE_Ok) {
            // Note: not returning error is intentional per Java reference
            Logger_log(LogLevel::TELL_Warning, "Failed to parse precision imagery for %s (%d)", frame->path.get(), code);
        }
    }

    return TE_Ok;
}

/*****************************************************************************/
// Private internal class - QueryArgument

GLMosaicMapLayer::QueryArgument::QueryArgument(GLMosaicMapLayer *owner)
    : GLAsynchronousMapRenderable3::ViewState(), minGSD(NAN), maxGSD(NAN), valid(false), owner(owner)
{}

GLMosaicMapLayer::QueryArgument::~QueryArgument() {}

void GLMosaicMapLayer::QueryArgument::set(const GLMapView2 &view)
{
    GLAsynchronousMapRenderable3::ViewState::set(view);

    const double mapResolution = view.view.getMapResolution(view.drawMapScale);

    // query the database over the spatial ROI
    this->minGSD = NAN;
    this->maxGSD = NAN;
    if (mapResolution < owner->info_->getMaxResolution()) {
        // the map resolution exceeds what is available; cap maximum
        // requested GSD at available
        this->maxGSD = owner->info_->getMaxResolution();
    } else {
        // don't give anything with a resolution higher than the twice
        // the current map resolution
        this->maxGSD = mapResolution;
    }

    this->valid = true;
}

void GLMosaicMapLayer::QueryArgument::copy(const ViewState &view)
{
    ViewState::copy(view);

    const auto &other = (const QueryArgument &)view;
    this->minGSD = other.minGSD;
    this->maxGSD = other.maxGSD;
    this->valid = other.valid;
}

/*****************************************************************************/
// Private internal class - RenderableListIter

GLMosaicMapLayer::RenderableListIter::RenderableListIter(GLMosaicMapLayer *owner) NOTHROWS : owner(owner),
                                                                                             curMap(&owner->zombie_frames_),
                                                                                             curIter(curMap->begin())
{
    if (curIter == curMap->end()) {
        curMap = &owner->visibleFrames;
        curIter = curMap->begin();
    }
}

GLMosaicMapLayer::RenderableListIter::~RenderableListIter() NOTHROWS {}

TAKErr GLMosaicMapLayer::RenderableListIter::next() NOTHROWS
{
    if (curIter == curMap->end() && curMap == &owner->visibleFrames)
        return TE_Done;

    curIter++;

    while (curIter == curMap->end()) {
        if (curMap == &owner->zombie_frames_) {
            curMap = &owner->visibleFrames;
            curIter = curMap->begin();
        } else
            return TE_Done;
    }
    return TE_Ok;
}

TAKErr GLMosaicMapLayer::RenderableListIter::get(GLMapRenderable2 *&value) const NOTHROWS
{
    if (curIter == curMap->end() && curMap == &owner->visibleFrames)
        return TE_Done;
    value = curIter->second.get();
    return TE_Ok;
}

/*****************************************************************************/
// Private internal class - SelectControlImpl

GLMosaicMapLayer::SelectControlImpl::SelectControlImpl(GLMosaicMapLayer *owner) NOTHROWS : owner(owner) {}

GLMosaicMapLayer::SelectControlImpl::~SelectControlImpl() NOTHROWS {}

void GLMosaicMapLayer::SelectControlImpl::setResolutionSelectMode(Mode mode) NOTHROWS
{
    owner->resolution_select_mode_ = mode;
    owner->invalidateNoSync();
}

GLMosaicMapLayer::SelectControlImpl::Mode GLMosaicMapLayer::SelectControlImpl::getResolutionSelectMode() NOTHROWS
{
    return owner->resolution_select_mode_;
}

void GLMosaicMapLayer::SelectControlImpl::setFilter(Port::Set<Port::String> &filter) NOTHROWS
{
    owner->imagery_type_filter_.clear();
    Port::STLSetAdapter<Port::String> ourSet(owner->imagery_type_filter_);
    Port::Collections_addAll(ourSet, filter);
    owner->invalidateNoSync();
}

/*****************************************************************************/
// Private internal class - RasterDataAccessControlImpl

GLMosaicMapLayer::RasterDataAccessControlImpl::RasterDataAccessControlImpl(GLMosaicMapLayer *owner) NOTHROWS : owner(owner) {}

GLMosaicMapLayer::RasterDataAccessControlImpl::~RasterDataAccessControlImpl() NOTHROWS {}

TAKErr GLMosaicMapLayer::RasterDataAccessControlImpl::accessRasterData(
            void (*accessVisitor)(void *opaque, RasterDataAccess2 *access),
            void *opaque, const GeoPoint2 &p) NOTHROWS
{
    Thread::Monitor::Lock lock(owner->monitor_);

    const double latitude = p.latitude;
    const double longitude = p.longitude;

    GLQuadTileNode2 *r;
    Math::Point2<double> img(0.0, 0.0);
    for (auto reverse = owner->visibleFrames.rbegin(); reverse != owner->visibleFrames.rend(); reverse++) {
        if (reverse->first.minLat > latitude || reverse->first.maxLat < latitude)
            continue;
        if (reverse->first.minLon > longitude || reverse->first.maxLon < longitude)
            continue;
        r = reverse->second.get();

        if (r == nullptr)
            continue;
        if (r->groundToImage(&img, nullptr, p) != TE_Ok)
            continue;
        int w, h;
        if (r->getWidth(&w) != TE_Ok || r->getHeight(&h) != TE_Ok)
            continue;
        if (atakmap::math::Rectangle<double>::contains(0.0, 0.0, w, h, img.x, img.y)) {
            accessVisitor(opaque, r);
            return TE_Ok;
        }
    }
    return TE_Done;
}

/*****************************************************************************/
// anonymous namespace implementations

namespace {

    MosaicPendingData::MosaicPendingData() : frames(), spatialCalc(), loaded(), database(nullptr, nullptr) {}
    MosaicPendingData::~MosaicPendingData()
    {
        if (database != nullptr) {
            database->close();
            database.reset();
        }
    }

    bool stringStartsWith(const std::string &s, const std::string &preface)
    {
        return preface.length() <= s.length() && 0 == s.compare(0, preface.length(), preface);
    }

    bool intersects(const GLAsynchronousMapRenderable3::ViewState &state, const MosaicDatabase2::Frame &frame)
    {
        return (state.southBound <= frame.maxLat && state.northBound >= frame.minLat && state.westBound <= frame.maxLon &&
                state.eastBound >= frame.minLon);
    }

    Feature::Point2 toPoint(const GeoPoint2 geo) { return Feature::Point2(geo.longitude, geo.latitude); }

    TAKErr getDatasetProjection(DatasetProjection2Ptr &projection, const Raster::Mosaic::MosaicDatabase2::Frame *frame)
    {
        return DatasetProjection2_create(projection, frame->srid, frame->width, frame->height, frame->upperLeft, frame->upperRight,
                                         frame->lowerRight, frame->lowerLeft);
    }

    void renderThreadCleaner(void *opaque) NOTHROWS
    {
        std::unique_ptr<std::list<std::shared_ptr<GLQuadTileNode2>>> releaseList(static_cast<std::list<std::shared_ptr<GLQuadTileNode2>> *>(opaque));
        for (auto iter = releaseList->begin(); iter != releaseList->end(); iter++)
            (*iter)->release();
    }

}  // namespace
