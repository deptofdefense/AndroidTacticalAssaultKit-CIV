#include "renderer/raster/tilereader/GLQuadTileNode2.h"

#include <cstdint>
#include <iterator>
#include <sstream>
#include "core/GeoPoint.h"
#include "raster/DatasetDescriptor.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLText2.h"
#include "renderer/GLWireframe.h"
#include "renderer/map/layer/raster/gdal/GdalGraphicUtils.h"
#include "renderer/raster/tilereader/GLTiledMapLayer2.h"
#include "util/ConfigOptions.h"
#include "util/MathUtils.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::TileReader;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Raster::TileReader;

namespace {

    bool offscreenFboFailed = false;
    const bool MIPMAP_ENABLED = false;
    const bool DEBUG_QUADTILE = false;
    const int TEXTURE_CACHE_HINT_RESOLVED = 0x00000001;
    const int POI_ITERATION_BIAS[] = {
        1, 3, 0, 2,  // 0
        1, 0, 2, 3,  // 1
        0, 1, 2, 3,  // 2
        0, 2, 1, 3,  // 3
        2, 0, 3, 1,  // 4
        2, 3, 0, 1,  // 5
        3, 2, 1, 0,  // 6
        3, 1, 2, 0,  // 7
    };
    const int STATE_RESOLVED = 0x01;
    const int STATE_RESOLVING = 0x02;
    const int STATE_UNRESOLVED = 0x04;
    const int STATE_UNRESOLVABLE = 0x08;
    const int STATE_SUSPENDED = 0x10;
    const double POLE_LATITUDE_LIMIT_EPISLON = 0.00001;

    double DistanceCalculations_calculateRange(const GeoPoint2 &start, const GeoPoint2 &destination)
    {
        GeoPoint2 a(start.latitude, start.longitude, 0, AltitudeReference::HAE);
        GeoPoint2 b(destination.latitude, destination.longitude, 0, AltitudeReference::HAE);
        return GeoPoint2_distance(a, b, false);
    }

    // Porting notes: Java comparitor is <0 if n1 before n2, 0 if equal or >0 if n2 before n1
    // std sort comparitor wants true iff n1 before n2
    bool pointSort(const Math::Point2<int64_t> &p0, const Math::Point2<int64_t> &p1)
    {
        int64_t retval = p0.y - p1.y;
        if (retval == 0)
            retval = p0.x - p1.x;
        return retval < 0;
    }

    struct VertexCoordInfo
    {
        const int srid;
        const bool valid;
        const bool projected;
        const size_t texCoordsCapacity;
        const size_t vertexCoordsCapacity;
        const size_t texCoordsIndicesCapacity;

        VertexCoordInfo(int srid, bool valid, bool projected, size_t texCoordCap, size_t vertexCoordCap, size_t texIndexCap)
            : srid(srid),
              valid(valid),
              projected(projected),
              texCoordsCapacity(texCoordCap),
              vertexCoordsCapacity(vertexCoordCap),
              texCoordsIndicesCapacity(texIndexCap)
        {}
    };

}  // namespace

class GLQuadTileNode2::VertexResolver
{
   public:
    virtual ~VertexResolver() = 0;
    virtual void beginDraw(const Core::GLMapView2 &view) = 0;
    virtual void endDraw(const Core::GLMapView2 &view) = 0;
    virtual void beginNode(GLQuadTileNode2 *node) = 0;
    virtual void endNode(GLQuadTileNode2 *node) = 0;
    virtual Util::TAKErr project(GridVertex *vert, const Core::GLMapView2 &view, int64_t imgSrcX, int64_t imgSrcY) = 0;
    virtual void release() = 0;
    virtual void nodeDestroying(GLQuadTileNode2 *node) = 0;
};

class GLQuadTileNode2::DefaultVertexResolver : public GLQuadTileNode2::VertexResolver
{
   public:
    DefaultVertexResolver();
    ~DefaultVertexResolver() override;

    void beginDraw(const Renderer::Core::GLMapView2 &view) override;
    void endDraw(const Renderer::Core::GLMapView2 &view) override;
    void beginNode(GLQuadTileNode2 *node) override;
    void endNode(GLQuadTileNode2 *node) override;
    Util::TAKErr project(GridVertex *vert, const Renderer::Core::GLMapView2 &view, int64_t imgSrcX, int64_t imgSrcY) override;
    void release() override;
    void nodeDestroying(GLQuadTileNode2 *node) override;

    static constexpr double elScaleFactor = NAN;

   private:
    Math::Point2<double> scratch_img_;
    GLQuadTileNode2 *node_;
    bool populate_terrain_;
};

class GLQuadTileNode2::PreciseVertexResolver : public DefaultVertexResolver
{
   private:
    PreciseVertexResolver(GLQuadTileNode2 *owner);

   public:
    static TAKErr create(std::unique_ptr<VertexResolver> &value, GLQuadTileNode2 *owner);
    ~PreciseVertexResolver() override;

    /**********************************************************************/
    // Vertex Resolver
    void beginDraw(const Renderer::Core::GLMapView2 &view) override;
    void endDraw(const Renderer::Core::GLMapView2 &view) override;
    void beginNode(GLQuadTileNode2 *node) override;
    void endNode(GLQuadTileNode2 *node) override;
    Util::TAKErr project(GridVertex *vert, const Renderer::Core::GLMapView2 &view, int64_t imgSrcX, int64_t imgSrcY) override;
    void release() override;
    void nodeDestroying(GLQuadTileNode2 *node) override;

   private:
    typedef std::map<Math::Point2<int64_t>, GeoPoint2, decltype(pointSort) *> SortedPointMap;
    typedef std::set<Math::Point2<int64_t>, decltype(pointSort) *> SortedPointSet;

    struct RenderRunnableOpaque
    {
        PreciseVertexResolver *owner;
        enum
        {
            END_DRAW,
            RUN_RESULT
        } eventType;

        // END_DRAW
        GLQuadTileNode2 *node;
        int targetGridWidth;
        RenderRunnableOpaque(PreciseVertexResolver *owner, GLQuadTileNode2 *node, int targetGridWidth);
        RenderRunnableOpaque(PreciseVertexResolver *owner);
    };

    TAKErr preciseImageToGround(GeoPoint2 *ground, const Math::Point2<double> &image);
    TAKErr resolve();
    TAKErr getCacheKey(std::string *value);
    static TAKErr deserialize(MemBuffer2 &buf, SortedPointMap &precise, SortedPointSet &unresolvable);
    static TAKErr serialize(MemBuffer2Ptr &buf, const SortedPointMap &precise, const SortedPointSet &unresolvable);

    static void renderThreadRunnable(void *opaque) NOTHROWS;
    void renderThreadRunnableImpl(RenderRunnableOpaque *runnableInfo);
    void queueGLCallback(RenderRunnableOpaque *runnableInfo);

    static void *threadRun(void *opaque);
    void threadRunImpl();

    GLQuadTileNode2 *owner;
    Thread::Mutex syncOn;
    Thread::CondVar cv;
    Thread::ThreadPtr thread;
    Thread::ThreadID activeID;
    int threadCounter;
    std::list<Math::Point2<int64_t>> queue;
    Math::Point2<int64_t> query;
    SortedPointSet pending;
    SortedPointSet unresolvable;
    SortedPointMap precise;
    GLQuadTileNode2 *currentNode;

    SortedPointSet currentRequest;
    std::set<GLQuadTileNode2 *> requestNodes;

    GeoPoint2 scratchGeo;
    Math::Point2<double> scratchImg;

    int needsResolved;
    int requested;
    int numNodesPending;

    bool initialized;

    Thread::Mutex queuedRunnablesMutex;
    std::vector<RenderRunnableOpaque *> queuedRunnables;

    struct PointSetPredicate
    {
        const SortedPointSet &ref;
        PointSetPredicate(const SortedPointSet &ref) : ref(ref) {}
        bool operator()(const Math::Point2<int64_t> &v) { return ref.find(v) != ref.end(); }
    };

    struct PointMapPredicate
    {
        const SortedPointMap &ref;
        PointMapPredicate(const SortedPointMap &ref) : ref(ref) {}
        bool operator()(const Math::Point2<int64_t> &v) { return ref.find(v) != ref.end(); }
    };
};

struct GLQuadTileNode2::IOCallbackOpaque
{
    enum UpdateType
    {
        CB_Error,
        CB_Canceled,
        CB_Completed,
        CB_Update
    };

    const UpdateType type;
    GLQuadTileNode2 *owner;
    const int reqId;

    // Used by updates only
    BitmapPtr data;

    IOCallbackOpaque(UpdateType type, GLQuadTileNode2 &owner, int reqId) NOTHROWS
        : type(type), owner(&owner), reqId(reqId), data(nullptr, nullptr)
    {}

    IOCallbackOpaque(GLQuadTileNode2 &owner, int reqId, const Bitmap2 &data) NOTHROWS
        : type(CB_Update), owner(&owner), reqId(reqId), data(new(std::nothrow) Bitmap2(data), Memory_deleter_const<Bitmap2>)
    {}
};

TAKErr GLQuadTileNode2::create(GLQuadTileNode2Ptr &value, TAK::Engine::Core::RenderContext *context, const ImageInfo *info,
                               ::TileReader::TileReaderFactory2Options &readerOpts, const Options &opts, const Initializer &init)
{
    TAKErr code(TE_Ok);
    GLQuadTileNode2Ptr root(new GLQuadTileNode2(), Memory_deleter_const<GLQuadTileNode2>);
    NodeCore *core;
    code = NodeCore::create(&core, context, info, readerOpts, opts, true, init);
    TE_CHECKRETURN_CODE(code);
    code = root->init(nullptr, -1, core);
    TE_CHECKRETURN_CODE(code);
    value = std::move(root);
    return TE_Ok;
}

TAKErr GLQuadTileNode2::create(GLQuadTileNode2Ptr &value, GLQuadTileNode2 *parent, int idx)
{
    TAKErr code(TE_Ok);
    GLQuadTileNode2Ptr ret(new GLQuadTileNode2(), Memory_deleter_const<GLQuadTileNode2>);
    code = ret->init(parent, idx, parent->core_);
    TE_CHECKRETURN_CODE(code);
    value = std::move(ret);
    return code;
}

GLQuadTileNode2::GLQuadTileNode2()
    : core_(nullptr),
      current_request_id_(0),
      current_request_level_(0),
      current_request_row_(0),
      current_request_col_(0),
      current_request_id_valid_(false),
      texture_(nullptr, nullptr),
      tile_row_(-1),
      tile_column_(-1),
      level_(-1),
      state_(State::UNRESOLVED),
      tile_src_x_(0),
      tile_src_y_(0),
      tile_src_width_(0),
      tile_src_height_(0),
      tile_width_(0),
      tile_height_(0),
      texture_coordinates_(nullptr, Memory_array_deleter_const<float>),
      texture_coordinates_capacity_(0),
      vertex_coordinates_(nullptr, Memory_array_deleter_const<float>),
      vertex_coordinates_capacity_(0),
      gl_tex_coord_indices_(nullptr, Memory_array_deleter_const<uint16_t>),
      gl_tex_coord_indices_capacity_(0),
      loading_texture_coordinates_(nullptr, Memory_array_deleter_const<float>),
      loading_texture_coordinates_capacity_(0),
      texture_coords_valid_(false),
      received_update_(false),
      gl_tex_type_(0),
      gl_tex_format_(0),
      gl_tex_grid_width_(0),
      gl_tex_grid_height_(0),
      gl_tex_grid_vert_count_(0),
      gl_tex_grid_idx_count_(0),
      vertex_coord_srid_(-1),
      verts_draw_version_(0),
      vertex_coords_valid_(false),
      grid_vertices_(),
      queued_callbacks_mutex_(),
      queued_callbacks_(),
      centroid_(),
      centroid_proj_(),
      min_lat_(0),
      min_lng_(0),
      max_lat_(0),
      max_lng_(0),
      touched_(false),
      tile_version_(-1),
      children_{GLQuadTileNode2Ptr(nullptr, nullptr), GLQuadTileNode2Ptr(nullptr, nullptr), GLQuadTileNode2Ptr(nullptr, nullptr),
               GLQuadTileNode2Ptr(nullptr, nullptr)},
      half_tile_width_(0),
      half_tile_height_(0),
      parent_(nullptr),
      root_(nullptr),
      borrowing_from_(nullptr),
      borrowers_(),
      vertices_invalid_(false),
      loading_tex_coords_vert_count_(0),
      derived_unresolvable_data_(false),
      should_borrow_texture_(false),
      last_touch_(-1),
      fade_timer_(0),
      read_request_start_(0),
      read_request_complete_(0),
      read_request_elapsed_(0),
      debug_draw_indices_(nullptr, Memory_array_deleter_const<uint16_t>),
      debug_draw_indices_capacity_(0),
      debug_draw_indices_count_(0),
      is_overdraw_(false),
      texture_key_("")
{}

GLQuadTileNode2::~GLQuadTileNode2()
{
    release();
    if (this->root_ == this && this->core_) {
        delete this->core_;
        this->core_ = nullptr;
    } else if (this->core_) {
        this->core_->vertexResolver->nodeDestroying(this);
    }
}

TAKErr GLQuadTileNode2::init(GLQuadTileNode2 *parent, int idx, NodeCore *core)
{
    TAKErr code(TE_Ok);
    // *required* that this block happens before any possible error return
    // for consistency in dtor and to not leak core
    this->parent_ = parent;
    this->core_ = core;
    this->root_ = (parent == nullptr) ? this : parent->root_;

    // XXX - would really like to make this statically initializable
    if (parent == nullptr && core->vertexResolver == nullptr) {
        if (this->core_->precise.get() != nullptr) {
            code = PreciseVertexResolver::create(core->vertexResolver, this);
            TE_CHECKRETURN_CODE(code);
        } else
            core->vertexResolver.reset(new DefaultVertexResolver());
    }

    // super

    size_t tileWidth, tileHeight;
    code = this->core_->tileReader->getTileWidth(&tileWidth);
    TE_CHECKRETURN_CODE(code);
    code = this->core_->tileReader->getTileHeight(&tileHeight);
    TE_CHECKRETURN_CODE(code);

    this->half_tile_width_ = (tileWidth / 2);
    this->half_tile_height_ = (tileHeight / 2);

    if (this->parent_ != nullptr) {
        this->set((this->parent_->tile_column_ * 2) + (idx % 2), (this->parent_->tile_row_ * 2) + (idx / 2), this->parent_->level_ - 1);
    } else {
        // XXX - BUG: should be specifiying maxLevels-1 but then no data is
        // rendered????
        size_t maxNumResLevels;
        code = this->core_->tileReader->getMaximumNumResolutionLevels(&maxNumResLevels);
        TE_CHECKRETURN_CODE(code);
        code = this->set(0, 0, maxNumResLevels);
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}

void GLQuadTileNode2::setColor(int color)
{
    this->core_->color = color;
    this->core_->colorR = ((color >> 16) & 0xFF) / 255.0f;
    this->core_->colorG = ((color >> 8) & 0xFF) / 255.0f;
    this->core_->colorB = (color & 0xFF) / 255.0f;
    this->core_->colorA = ((color >> 24) & 0xFF) / 255.0f;
}

TAKErr GLQuadTileNode2::createDefaultVertexResolver(std::unique_ptr<VertexResolver> &resolver)
{
    resolver = std::make_unique<DefaultVertexResolver>();
    return TE_Ok;
}

DatasetProjection2 *GLQuadTileNode2::getDatasetProjection()
{
    return this->core_->imprecise.get();
}

TileReader::TileReader2 *GLQuadTileNode2::getReader()
{
    return this->core_->tileReader.get();
}

TAKErr GLQuadTileNode2::set(int64_t tileColumn, int64_t tileRow, size_t level)
{
    if (this->tile_column_ != tileColumn || this->tile_row_ != tileRow || this->level_ != level) {
        if (this->borrowing_from_ != nullptr) {
            this->borrowing_from_->unborrowTexture(this);
            this->borrowing_from_ = nullptr;
        }

        for (int i = 0; i < 4; i++)
            if (this->children_[i] != nullptr)
                this->children_[i]->set((tileColumn * 2) + (i % 2), (tileRow * 2) + (i / 2), level - 1);
    }

    return super_set(tileColumn, tileRow, level);
}

TAKErr GLQuadTileNode2::super_set(int64_t tileColumn, int64_t tileRow, size_t level)
{
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s set(tileColumn=%" PRId64 ",tileRow=%" PRId64 ",level=%d)", toString(false), tileColumn,
                         tileRow, level);
    }
    if (this->tile_column_ == tileColumn && this->tile_row_ == tileRow && this->level_ == level)
        return TE_Ok;

    if (this->current_request_id_valid_) {
        this->core_->tileReader->asyncCancel(this->current_request_id_);
        this->current_request_id_valid_ = false;
    }

    if (this->core_->textureCache != nullptr && ((this->state_ == State::RESOLVED) || this->received_update_))
        this->releaseTexture();

    this->state_ = State::UNRESOLVED;
    this->received_update_ = false;

    TAKErr code(TE_Ok);
    code = this->core_->tileReader->getTileSourceX(&tile_src_x_, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = this->core_->tileReader->getTileSourceY(&tile_src_y_, level, tileRow);
    TE_CHECKRETURN_CODE(code);
    code = this->core_->tileReader->getTileSourceWidth(&tile_src_width_, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = this->core_->tileReader->getTileSourceHeight(&tile_src_height_, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    code = this->core_->tileReader->getTileWidth(&tile_width_, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = this->core_->tileReader->getTileHeight(&tile_height_, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    this->texture_coords_valid_ = false;
    this->vertex_coords_valid_ = false;

    this->tile_column_ = tileColumn;
    this->tile_row_ = tileRow;
    this->level_ = level;
    this->texture_key_ = "";
    this->tile_version_ = -1;

    Bitmap2::Format tileFmt;
    code = this->core_->tileReader->getFormat(&tileFmt);
    TE_CHECKRETURN_CODE(code);
    code = GLTexture2_getFormatAndDataType(&gl_tex_format_, &gl_tex_type_, tileFmt);
    TE_CHECKRETURN_CODE(code);

    if (this->gl_tex_format_ != GL_RGBA && !(MathUtils_isPowerOf2(this->tile_width_) && MathUtils_isPowerOf2(this->tile_height_))) {
        this->gl_tex_format_ = GL_RGBA;
        this->gl_tex_type_ = GL_UNSIGNED_BYTE;
    }

    Math::Point2<double> scratchP(0, 0);
    GeoPoint2 scratchG;

    min_lat_ = 90;
    max_lat_ = -90;
    min_lng_ = 180;
    max_lng_ = -180;

    scratchP.x = static_cast<double>(this->tile_src_x_);
    scratchP.y = static_cast<double>(this->tile_src_y_);
    code = this->core_->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if (scratchG.latitude < min_lat_)
        min_lat_ = scratchG.latitude;
    if (scratchG.latitude > max_lat_)
        max_lat_ = scratchG.latitude;
    if (scratchG.longitude < min_lng_)
        min_lng_ = scratchG.longitude;
    if (scratchG.longitude > max_lng_)
        max_lng_ = scratchG.longitude;

    scratchP.x = static_cast<double>(this->tile_src_x_ + this->tile_src_width_);
    scratchP.y = static_cast<double>(this->tile_src_y_);
    code = this->core_->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if (scratchG.latitude < min_lat_)
        min_lat_ = scratchG.latitude;
    if (scratchG.latitude > max_lat_)
        max_lat_ = scratchG.latitude;
    if (scratchG.latitude < min_lng_)
        min_lng_ = scratchG.longitude;
    if (scratchG.longitude > max_lng_)
        max_lng_ = scratchG.longitude;

    scratchP.x = static_cast<double>(this->tile_src_x_ + this->tile_src_width_);
    scratchP.y = static_cast<double>(this->tile_src_y_ + this->tile_src_height_);
    code = this->core_->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if (scratchG.latitude < min_lat_)
        min_lat_ = scratchG.latitude;
    if (scratchG.latitude > max_lat_)
        max_lat_ = scratchG.latitude;
    if (scratchG.longitude < min_lng_)
        min_lng_ = scratchG.longitude;
    if (scratchG.longitude > max_lng_)
        max_lng_ = scratchG.longitude;

    scratchP.x = static_cast<double>(this->tile_src_x_);
    scratchP.y = static_cast<double>(this->tile_src_y_ + this->tile_src_height_);
    code = this->core_->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if (scratchG.latitude < min_lat_)
        min_lat_ = scratchG.latitude;
    if (scratchG.latitude > max_lat_)
        max_lat_ = scratchG.latitude;
    if (scratchG.longitude < min_lng_)
        min_lng_ = scratchG.longitude;
    if (scratchG.longitude > max_lng_)
        max_lng_ = scratchG.longitude;

    int minGridSize = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.minimum-grid-size", 1);
    int maxGridSize = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.maximum-grid-size", 32);

    // XXX - needs to be based off of the full image to prevent seams
    //       between adjacent tiles which may have different local spatial
    //       resolutions

    const int subsX =
        MathUtils_clamp(MathUtils_nextPowerOf2((int)ceil((max_lat_ - min_lat_) / Core::GLMapView2::getRecommendedGridSampleDistance())),
                        minGridSize, maxGridSize);
    const int subsY =
        MathUtils_clamp(MathUtils_nextPowerOf2((int)ceil((max_lng_ - min_lng_) / Core::GLMapView2::getRecommendedGridSampleDistance())),
                        minGridSize, maxGridSize);

    // XXX - rendering issues if grid is not square...
    this->gl_tex_grid_width_ = std::max(subsX, subsY);
    this->gl_tex_grid_height_ = std::max(subsX, subsY);

    size_t newSize = (this->gl_tex_grid_width_ + 1) * (this->gl_tex_grid_height_ + 1);
    if (newSize > this->grid_vertices_.size())
        std::fill(this->grid_vertices_.begin(), this->grid_vertices_.end(), GridVertex());
    this->grid_vertices_.resize(newSize);

    this->centroid_ = GeoPoint2((min_lat_ + max_lat_) / 2.0, (min_lng_ + max_lng_) / 2.0);
    this->centroid_proj_ = Math::Point2<double>(0, 0, 0);

    return code;
}

void GLQuadTileNode2::setLoadingTextureEnabled(bool enabled)
{
    this->core_->loadingTextureEnabled = enabled;
}

void GLQuadTileNode2::releaseTexture()
{
    const bool resolved = (this->state_ == State::RESOLVED);
    if (this->core_->textureCache != nullptr && (resolved || this->received_update_)) {
        GLTextureCache2::Entry::OpaquePtr opaque(
            new VertexCoordInfo(this->vertex_coord_srid_, this->vertex_coords_valid_, true, texture_coordinates_capacity_, vertex_coordinates_capacity_,
                                gl_tex_coord_indices_capacity_),
            Memory_void_deleter_const<VertexCoordInfo>);
        GLTextureCache2::EntryPtr ent(
            new GLTextureCache2::Entry(std::move(this->texture_), std::move(this->texture_coordinates_), std::move(this->vertex_coordinates_),
                                       std::move(this->gl_tex_coord_indices_), this->gl_tex_grid_vert_count_, this->gl_tex_grid_idx_count_,
                                       resolved ? TEXTURE_CACHE_HINT_RESOLVED : 0, std::move(opaque)),
            Memory_deleter_const<GLTextureCache2::Entry>);
        this->core_->textureCache->put(getTextureKey(), std::move(ent));
    } else {
        this->texture_->release();
        this->texture_.reset();
        this->texture_coordinates_.reset();
        this->vertex_coordinates_.reset();
        this->gl_tex_coord_indices_.reset();
    }
    texture_coordinates_capacity_ = 0;
    vertex_coordinates_capacity_ = 0;
    gl_tex_coord_indices_capacity_ = 0;

    this->texture_coords_valid_ = false;
    this->vertex_coords_valid_ = false;

    if (this->state_ != State::UNRESOLVABLE)
        this->state_ = State::UNRESOLVED;

    this->received_update_ = false;
}

void GLQuadTileNode2::release() NOTHROWS
{
    this->abandon();
    if (this->borrowing_from_ != nullptr) {
        this->borrowing_from_->unborrowTexture(this);
        this->borrowing_from_ = nullptr;
    }
    if (this == this->root_) {
        if (core_) {
            // vertex resolver is to be released prior to I2G functions
            core_->vertexResolver->release();
            this->core_->progressiveLoading = false;
        }
    } else {
        this->parent_ = nullptr;
    }
    this->loading_tex_coords_vert_count_ = 0;

    this->fade_timer_ = 0L;

    super_release();
}

void GLQuadTileNode2::super_release()
{
    this->current_request_id_valid_ = false;
    if (this->core_)
        this->core_->tileReader->asyncAbort(*this);
    {
        Thread::Lock lock(queued_callbacks_mutex_);
        for (IOCallbackOpaque *cbo : queued_callbacks_)
            cbo->owner = nullptr;
        queued_callbacks_.clear();
    }
    if (this->texture_.get() != nullptr)
        this->releaseTexture();

    this->loading_texture_coordinates_.reset();

    this->tile_column_ = -1;
    this->tile_row_ = -1;
    this->level_ = -1;
    this->texture_key_ = "";

    this->texture_coords_valid_ = false;

    this->state_ = State::UNRESOLVED;
    this->received_update_ = false;

    this->debug_draw_indices_.reset();
    this->debug_draw_indices_capacity_ = 0;
}

TAKErr GLQuadTileNode2::borrowTexture(Renderer::GLTexture2 **tex, GLQuadTileNode2 *ref, int64_t srcX, int64_t srcY, int64_t srcW,
                                      int64_t srcH, float *texCoords, size_t texGridWidth, size_t texGridHeight)
{
    const float extentX = ((float)this->tile_width_ / (float)this->texture_->getTexWidth());
    const float extentY = ((float)this->tile_height_ / (float)this->texture_->getTexHeight());

    const float minX = std::max(((float)(srcX - this->tile_src_x_ - 1) / (float)this->tile_src_width_) * extentX, 0.0f);
    const float minY = std::max(((float)(srcY - this->tile_src_y_ - 1) / (float)this->tile_src_height_) * extentY, 0.0f);
    const float maxX = std::min(((float)((srcX + srcW) - this->tile_src_x_ + 1) / (float)this->tile_src_width_) * extentX, 1.0f);
    const float maxY = std::min(((float)((srcY + srcH) - this->tile_src_y_ + 1) / (float)this->tile_src_height_) * extentY, 1.0f);

    atakmap::math::Point<float> ul(minX, minY);
    atakmap::math::Point<float> ur(maxX, minY);
    atakmap::math::Point<float> lr(maxX, maxY);
    atakmap::math::Point<float> ll(minX, maxY);

    TAKErr code = GLTexture2_createQuadMeshTexCoords(texCoords, ul, ur, lr, ll, texGridWidth, texGridHeight);
    TE_CHECKRETURN_CODE(code);

    if (ref != nullptr)
        this->borrowers_.insert(ref);

    *tex = this->texture_.get();
    return TE_Ok;
}

void GLQuadTileNode2::unborrowTexture(GLQuadTileNode2 *ref)
{
    this->borrowers_.erase(ref);
}

TAKErr GLQuadTileNode2::getLoadingTexture(Renderer::GLTexture2 **ret, float *texCoords, std::size_t texGridWidth, std::size_t texGridHeight)
{
    TAKErr code(TE_Ok);
    GLQuadTileNode2 *scratch = this->borrowing_from_;
    if (this->core_->options.textureBorrowEnabled && this->core_->textureBorrowEnabled && this->should_borrow_texture_) {
        if (scratch == nullptr) {
            GLQuadTileNode2 *updatedAncestor = nullptr;
            scratch = this->parent_;
            while (scratch != nullptr) {
                if (scratch->state_ == State::RESOLVED)
                    break;
                if (this->core_->textureCache != nullptr) {
                    const GLTextureCache2::Entry *scratchTexInfo;
                    code = this->core_->textureCache->get(&scratchTexInfo, scratch->getTextureKey());
                    if (code == TE_Ok && scratchTexInfo->hasHint(TEXTURE_CACHE_HINT_RESOLVED))
                        break;
                    else if (code == TE_Ok && updatedAncestor == nullptr)
                        updatedAncestor = scratch;
                }
                if (scratch->received_update_ && updatedAncestor == nullptr)
                    updatedAncestor = scratch;
                scratch = scratch->parent_;
            }
            if (scratch == nullptr)
                scratch = updatedAncestor;
            // if the ancestor is neither updated or resolved, we must have
            // found its texture in the cache
            if (scratch != nullptr && !(scratch->received_update_ || (scratch->state_ == State::RESOLVED))) {
                if (!scratch->useCachedTexture())
                    return TE_IllegalState;
            }
        }
    }
    if (scratch != nullptr && scratch != this->borrowing_from_) {
        if (this->borrowing_from_ != nullptr)
            this->borrowing_from_->unborrowTexture(this);
        this->borrowing_from_ = scratch;
        this->loading_tex_coords_vert_count_ = this->gl_tex_grid_vert_count_;
        return this->borrowing_from_->borrowTexture(ret, this, this->tile_src_x_, this->tile_src_y_, this->tile_src_width_, this->tile_src_height_,
                                                  texCoords, texGridWidth, texGridHeight);
    } else if (this->borrowing_from_ != nullptr) {
        if (this->loading_tex_coords_vert_count_ != this->gl_tex_grid_vert_count_) {
            GLTexture2 *junk;
            this->borrowing_from_->borrowTexture(&junk, nullptr, this->tile_src_x_, this->tile_src_y_, this->tile_src_width_, this->tile_src_height_,
                                               texCoords, texGridWidth, texGridHeight);
            this->loading_tex_coords_vert_count_ = this->gl_tex_grid_vert_count_;
        }
        *ret = this->borrowing_from_->texture_.get();
        return TE_Ok;
    } else if (this->core_->loadingTextureEnabled) {
        return super_getLoadingTexture(ret, texCoords, texGridWidth, texGridHeight);
    } else {
        *ret = nullptr;
        return TE_Ok;
    }
}

TAKErr GLQuadTileNode2::super_getLoadingTexture(Renderer::GLTexture2 **ret, float *texCoords, size_t texGridWidth, size_t texGridHeight) 
{
    // loading texture not supported
#if 0
    TAKErr code(TE_Ok);
    GLTexture2 *tex;
    code = GLTiledMapLayer2_getLoadingTexture(&tex);
    TE_CHECKRETURN_CODE(code);

    float x = ((float)this->tileWidth / (float)tex->getTexWidth());
    float y = ((float)this->tileHeight / (float)tex->getTexHeight());

    GLTexture2_createQuadMeshTexCoords(texCoords, x, y, texGridWidth, texGridHeight);

    *ret = tex;
    return TE_Ok;
#endif
    return TE_Unsupported;
}

TAKErr GLQuadTileNode2::validateTexture()
{
    if (this->texture_ == nullptr || this->texture_->getTexWidth() < this->tile_width_ || this->texture_->getTexHeight() < this->tile_height_ ||
        this->texture_->getFormat() != this->gl_tex_format_ || this->texture_->getType() != this->gl_tex_type_) {
        if (this->texture_.get() != nullptr)
            this->texture_->release();

        this->texture_ = GLTexture2Ptr(new GLTexture2(static_cast<int>(this->tile_width_), static_cast<int>(this->tile_height_), this->gl_tex_format_, this->gl_tex_type_),
                                      Memory_deleter_const<GLTexture2>);

        this->texture_coords_valid_ = false;
        this->texture_coordinates_.reset();
        this->texture_coordinates_capacity_ = 0;
        this->vertex_coordinates_.reset();
        this->vertex_coordinates_capacity_ = 0;
    }

    return this->validateTexVerts();
}

TAKErr GLQuadTileNode2::validateTexVerts()
{
    TAKErr code(TE_Ok);

    if (!this->texture_coords_valid_) {
        this->gl_tex_grid_idx_count_ = GLTexture2_getNumQuadMeshIndices(this->gl_tex_grid_width_, this->gl_tex_grid_height_);
        this->gl_tex_grid_vert_count_ = GLTexture2_getNumQuadMeshVertices(this->gl_tex_grid_width_, this->gl_tex_grid_height_);

        const size_t numVerts = this->gl_tex_grid_vert_count_;
        if (this->texture_coordinates_ == nullptr || this->texture_coordinates_capacity_ < (numVerts * 2)) {
            this->texture_coordinates_capacity_ = numVerts * 2;
            this->texture_coordinates_.reset(new float[this->texture_coordinates_capacity_]);
        }

        if (this->gl_tex_grid_vert_count_ > 4) {
            if (this->gl_tex_coord_indices_ == nullptr || this->gl_tex_coord_indices_capacity_ < this->gl_tex_grid_idx_count_) {
                this->gl_tex_coord_indices_capacity_ = this->gl_tex_grid_idx_count_;
                this->gl_tex_coord_indices_.reset(new uint16_t[this->gl_tex_coord_indices_capacity_]);
            }
        } else {
            this->gl_tex_coord_indices_.reset();
            this->gl_tex_coord_indices_capacity_ = 0;
        }

        if (this->vertex_coordinates_ == nullptr || this->vertex_coordinates_capacity_ < (numVerts * 3)) {
            this->vertex_coordinates_capacity_ = numVerts * 3;
            this->vertex_coordinates_.reset(new float[this->vertex_coordinates_capacity_]);
        }

        const float x = ((float)this->tile_width_ / (float)this->texture_->getTexWidth());
        const float y = ((float)this->tile_height_ / (float)this->texture_->getTexHeight());

        code = GLTexture2_createQuadMeshTexCoords(this->texture_coordinates_.get(), atakmap::math::Point<float>(0, 0),
                                                  atakmap::math::Point<float>(x, 0), atakmap::math::Point<float>(x, y),
                                                  atakmap::math::Point<float>(0, y), this->gl_tex_grid_width_, this->gl_tex_grid_height_);
        TE_CHECKRETURN_CODE(code);

        if (this->gl_tex_grid_vert_count_ > 4) {
            GLTexture2_createQuadMeshIndexBuffer(this->gl_tex_coord_indices_.get(), this->gl_tex_grid_width_, this->gl_tex_grid_height_);
        }

        if (gl_tex_coord_indices_ == nullptr) {
            size_t n;
            code = GLWireframe_getNumWireframeElements(&n, GL_TRIANGLE_STRIP, static_cast<GLuint>(this->gl_tex_grid_vert_count_));
            TE_CHECKRETURN_CODE(code);
            if (this->debug_draw_indices_capacity_ < n) {
                this->debug_draw_indices_capacity_ = n;
                debug_draw_indices_.reset(new uint16_t[debug_draw_indices_capacity_]);
            }
            code = GLWireframe_deriveIndices(this->debug_draw_indices_.get(), GL_TRIANGLE_STRIP, static_cast<GLuint>(this->gl_tex_grid_vert_count_));
            TE_CHECKRETURN_CODE(code);
        } else {
            size_t n;
            code = GLWireframe_getNumWireframeElements(&n, GL_TRIANGLE_STRIP, static_cast<GLuint>(this->gl_tex_grid_idx_count_));
            TE_CHECKRETURN_CODE(code);
            if (this->debug_draw_indices_capacity_ < n) {
                this->debug_draw_indices_capacity_ = n;
                debug_draw_indices_.reset(new uint16_t[debug_draw_indices_capacity_]);
            }
            code = GLWireframe_deriveIndices(debug_draw_indices_.get(), &debug_draw_indices_count_, gl_tex_coord_indices_.get(), GL_TRIANGLE_STRIP,
                                             static_cast<GLuint>(gl_tex_grid_idx_count_));
            TE_CHECKRETURN_CODE(code);
        }
    }
    this->texture_coords_valid_ = true;
    return code;
}

TAKErr GLQuadTileNode2::validateVertexCoords(const Renderer::Core::GLMapView2 &view)
{
    TAKErr code(TE_Ok);

    if (!this->vertex_coords_valid_ || (this->vertex_coord_srid_ != view.drawSrid)) {
        if (this->vertex_coord_srid_ != view.drawSrid) {
            code = view.scene.projection->forward(&this->centroid_proj_, this->centroid_);
            TE_CHECKRETURN_CODE(code);
        }

        // recompute vertex coordinates as necessary
        float *vptr = vertex_coordinates_.get();
        this->core_->vertexResolver->beginNode(this);
        int idx = 0;
        for (size_t i = 0; code == TE_Ok && i <= this->gl_tex_grid_height_; i++) {
            for (size_t j = 0; j <= this->gl_tex_grid_width_; j++) {
                const size_t gridVertsIdx = (i * (this->gl_tex_grid_width_ + 1)) + j;
                if (!this->grid_vertices_[gridVertsIdx].resolved) {
                    code = this->core_->vertexResolver->project(&(this->grid_vertices_[gridVertsIdx]), view,
                                                               this->tile_src_x_ + ((this->tile_src_width_ * j) / this->gl_tex_grid_width_),
                                                               this->tile_src_y_ + ((this->tile_src_height_ * i) / this->gl_tex_grid_height_));
                    TE_CHECKBREAK_CODE(code);
                    // force LLA reprojection
                    this->grid_vertices_[gridVertsIdx].projectedSrid = -1;
                }
                GeoPoint2 v(this->grid_vertices_[gridVertsIdx].value);

                // reproject LLA to the current map projection
                if (this->grid_vertices_[gridVertsIdx].projectedSrid != view.drawSrid) {
                    code = view.scene.projection->forward(&this->grid_vertices_[gridVertsIdx].projected, v);
                    TE_CHECKBREAK_CODE(code);
                    this->grid_vertices_[gridVertsIdx].projectedSrid = view.drawSrid;
                }

                Math::Point2<double> pointD;
                pointD.x = this->grid_vertices_[gridVertsIdx].projected.x - centroid_proj_.x;
                pointD.y = this->grid_vertices_[gridVertsIdx].projected.y - centroid_proj_.y;
                pointD.z = this->grid_vertices_[gridVertsIdx].projected.z - centroid_proj_.z;

                vptr[idx++] = static_cast<float>(pointD.x);
                vptr[idx++] = static_cast<float>(pointD.y);
                vptr[idx++] = static_cast<float>(pointD.z);
            }
        }
        this->core_->vertexResolver->endNode(this);
        TE_CHECKRETURN_CODE(code);

        this->vertex_coords_valid_ = true;

        this->vertex_coord_srid_ = view.drawSrid;
        this->verts_draw_version_ = view.drawVersion;
    }
    return code;
}

void GLQuadTileNode2::draw(const Renderer::Core::GLMapView2 &view, const int renderPass) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (this->parent_ != nullptr) {
        Util::Logger_log(LogLevel::TELL_Error, "External draw method should only be invoked on root node!");
        return;
    }

    core_->tilesThisFrame = 0;
    int64_t trw, trh;
    code = this->core_->tileReader->getWidth(&trw);
    TE_CHECKRETURN(code);
    code = this->core_->tileReader->getHeight(&trh);
    TE_CHECKRETURN(code);

    size_t numRois;
    code = GLTiledMapLayer2_getRasterROI2(this->core_->drawROI, &numRois, view, trw, trh, *(this->core_->imprecise), this->core_->upperLeft,
                                          this->core_->upperRight, this->core_->lowerRight, this->core_->lowerLeft, 0.0,
                                          360.0 / ((int64_t)1 << atakmap::raster::osm::OSMUtils::mapnikTileLevel(view.drawMapResolution)));

    if (numRois < 1)  // no intersection
        return;

    const double scale = (this->core_->gsd / view.drawMapResolution);

    // XXX - tune level calculation -- it may look better to swap to the
    // next level before we actually cross the threshold
    const size_t level = (size_t)ceil(std::max((log(1.0 / scale) / log(2.0)) + this->core_->options.levelTransitionAdjustment, 0.0));
    this->core_->drawPumpLevel = level;

    bool hasPrecise = false;
    code = this->hasPreciseCoordinates(&hasPrecise);
    if (view.targeting && hasPrecise)
        this->core_->magFilter = GL_NEAREST;
    else
        this->core_->magFilter = GL_LINEAR;

    int64_t poiX = 0;
    int64_t poiY = 0;
    Math::Point2<double> pointD;
    if (this->core_->imprecise->groundToImage(&pointD, GeoPoint2(view.drawLat, view.drawLng)) == TE_Ok) {
        poiX = (int64_t)pointD.x;
        poiY = (int64_t)pointD.y;
    }
    if (view.crossesIDL) {
        Math::Point2<double> poi2;
        if (this->core_->imprecise->groundToImage(&poi2, GeoPoint2(view.drawLat, view.drawLng+((view.drawLng>0.0)?-360.0:360.0))) == TE_Ok) {
            Math::Point2<double> cpoi1(MathUtils_clamp((double)poiX, (double)tile_src_x_, (double)(tile_src_x_+tile_src_width_)), MathUtils_clamp((double)poiY, (double)tile_src_y_, (double)(tile_src_y_+tile_src_height_)));
            Math::Point2<double> cpoi2(MathUtils_clamp(poi2.x, (double)tile_src_x_, (double)(tile_src_x_+tile_src_width_)), MathUtils_clamp(poi2.y, (double)tile_src_y_, (double)(tile_src_y_+tile_src_height_)));

#define __te_distance_squared(dx, dy) ((dx)*(dx)+(dy)*(dy))
            if (__te_distance_squared(poi2.x-cpoi2.x, poi2.y-cpoi1.y) < __te_distance_squared((double)poiX-cpoi1.x, (double)poiY-cpoi1.y)) {
                poiX = (int64_t)poi2.x;
                poiY = (int64_t)poi2.y;
            }
#undef __te_distance_squared
        }
    }

    code = TE_Ok;
    if (this->core_->tileReader.get() != nullptr) {
        code = this->core_->tileReader->start();
        TE_CHECKRETURN(code);
    }

    this->core_->vertexResolver->beginDraw(view);
    for (std::size_t i = 0u; i < numRois; i++) {
        // ROIs are always E hemi, W hemi

        if (i == 0)
            this->core_->drawPumpHemi = Core::GLAntiMeridianHelper::Hemisphere::East;
        else if (i == 1)
            this->core_->drawPumpHemi = Core::GLAntiMeridianHelper::Hemisphere::West;
        else
            // throw new IllegalStateException();
            break;

        atakmap::math::Rectangle<double> roi(this->core_->drawROI[i]);
        size_t maxResLevels;
        code = core_->tileReader->getMaximumNumResolutionLevels(&maxResLevels);
        TE_CHECKBREAK_CODE(code);
        this->draw(view, std::min(level, maxResLevels - 1), (int64_t)roi.x, (int64_t)roi.y,
                   (int64_t)(ceil(roi.x + roi.width) - (int64_t)roi.x), (int64_t)(ceil(roi.y + roi.height) - (int64_t)roi.y), poiX, poiY,
                   ((i + 1) == numRois));
    }
    this->core_->vertexResolver->endDraw(view);
    if (this->core_->tileReader.get() != nullptr)
        this->core_->tileReader->stop();

    // Log.v(TAG, "Tiles this frame: " + core->tilesThisFrame);
}

bool GLQuadTileNode2::shouldResolve()
{
    bool resolveUnresolvable = false;
    for (int i = 0; i < 4; i++)
        resolveUnresolvable |= (this->children_[i] != nullptr);

    return (this->state_ == State::UNRESOLVABLE && resolveUnresolvable) || super_shouldResolve();
}

int GLQuadTileNode2::getRenderPass() NOTHROWS
{
    return Renderer::Core::GLMapView2::Surface;
}

void GLQuadTileNode2::start() NOTHROWS {}

void GLQuadTileNode2::stop() NOTHROWS {}

bool GLQuadTileNode2::super_shouldResolve()
{
    return (this->state_ == State::UNRESOLVED) && (!this->current_request_id_valid_);
}

bool GLQuadTileNode2::useCachedTexture()
{
    if (this->core_->textureCache == nullptr)
        return false;

    GLTextureCache2::EntryPtr cachedTexture(nullptr, nullptr);
    if (this->core_->textureCache->remove(cachedTexture, this->getTextureKey()) != TE_Ok)
        return false;
    this->texture_ = std::move(cachedTexture->texture);
    this->gl_tex_format_ = this->texture_->getFormat();
    this->gl_tex_type_ = this->texture_->getType();
    this->received_update_ = true;
    if (cachedTexture->hasHint(TEXTURE_CACHE_HINT_RESOLVED)) {
        this->state_ = State::RESOLVED;
        this->resetFadeTimer();
    }

    this->texture_coords_valid_ = false;

    auto *vci = (VertexCoordInfo *)cachedTexture->opaque.get();
    this->texture_coordinates_ = std::move(cachedTexture->textureCoordinates);
    this->texture_coordinates_capacity_ = vci->texCoordsCapacity;
    this->vertex_coordinates_ = std::move(cachedTexture->vertexCoordinates);
    this->vertex_coordinates_capacity_ = vci->vertexCoordsCapacity;
    this->gl_tex_coord_indices_ = std::move(cachedTexture->indices);
    this->gl_tex_coord_indices_capacity_ = vci->texCoordsIndicesCapacity;
    this->gl_tex_grid_idx_count_ = cachedTexture->numIndices;
    this->gl_tex_grid_vert_count_ = cachedTexture->numVertices;
    // XXX -
    this->gl_tex_grid_width_ = (int)sqrt(this->gl_tex_grid_vert_count_) - 1;
    this->gl_tex_grid_height_ = (int)sqrt(this->gl_tex_grid_vert_count_) - 1;

    size_t newSize = (this->gl_tex_grid_width_ + 1) * (this->gl_tex_grid_height_ + 1);
    if (newSize > this->grid_vertices_.size())
        std::fill(this->grid_vertices_.begin(), this->grid_vertices_.end(), GridVertex());
    this->grid_vertices_.resize(newSize);

    this->vertex_coord_srid_ = -1;
    this->vertex_coords_valid_ = false;

    return true;
}

void GLQuadTileNode2::resolveTexture()
{
    // we only want to borrow if the texture is not yet resolved. if the
    // texture is already resolved, don't borrow -- just replace when the
    // latest version is loaded
    this->should_borrow_texture_ = (this->state_ != State::RESOLVED);

    // copy data from the children to our texture
    if (this->state_ != State::RESOLVED && this->core_->options.textureCopyEnabled && this->core_->enableTextureTargetFBO &&
        this->core_->textureCopyEnabled && !offscreenFboFailed) {
        bool hasChildData = false;
        bool willBeResolved = true;
        for (int i = 0; i < 4; i++) {
            willBeResolved &= (this->children_[i] != nullptr && (this->children_[i]->state_ == State::RESOLVED));
            if (this->children_[i] != nullptr && ((this->children_[i]->state_ == State::RESOLVED) || this->children_[i]->received_update_)) {
                hasChildData = true;
            }
        }

        if (hasChildData) {
            // XXX - luminance is not renderable for FBO
            if (!willBeResolved) {
                this->gl_tex_format_ = GL_RGBA;
                this->gl_tex_type_ = GL_UNSIGNED_BYTE;
            } else {
                if (this->gl_tex_format_ == GL_LUMINANCE)
                    this->gl_tex_format_ = GL_RGB;
                else if (this->gl_tex_format_ == GL_LUMINANCE_ALPHA)
                    this->gl_tex_format_ = GL_RGBA;
            }

            this->validateTexture();
            this->texture_->init();

            int parts = 0;
            GLint currentFrameBuffer;
            GLuint &frameBuffer = this->core_->frameBufferHandle;
            GLuint &depthBuffer = this->core_->depthBufferHandle;

            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFrameBuffer);

            bool fboCreated = false;
            do {
                if (frameBuffer == 0)
                    glGenFramebuffers(1, &frameBuffer);

                if (depthBuffer == 0)
                    glGenRenderbuffers(1, &depthBuffer);

                glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, static_cast<GLsizei>(this->texture_->getTexWidth()), static_cast<GLsizei>(this->texture_->getTexHeight()));
                glBindRenderbuffer(GL_RENDERBUFFER, 0);

                glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);

                // clear any pending errors
                while (glGetError() != GL_NO_ERROR)
                    ;
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this->texture_->getTexId(), 0);

                // XXX - observing hard crash following bind of "complete"
                //       FBO on SM-T230NU. reported error is 1280 (invalid
                //       enum) on glFramebufferTexture2D. I have tried using
                //       the color-renderable formats required by GLES 2.0
                //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
                //       the same outcome.
                if (glGetError() != GL_NO_ERROR)
                    break;

                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
                const GLenum fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                fboCreated = (fboStatus == GL_FRAMEBUFFER_COMPLETE);
            } while (false);

            if (fboCreated) {
                float *texCoordBuffer = this->texture_coordinates_.get();

                glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                glClear(GL_COLOR_BUFFER_BIT);

                int tx;
                int ty;
                std::size_t partX;
                std::size_t partY;
                std::size_t partWidth;
                std::size_t partHeight;

                float childTexWidth;
                float childTexHeight;
                for (int i = 0; i < 4; i++) {
                    if (this->children_[i] != nullptr &&
                        ((this->children_[i]->state_ == State::RESOLVED) || this->children_[i]->received_update_)) {
                        tx = i % 2;
                        ty = i / 2;
                        partX = tx * this->half_tile_width_;
                        partY = ty * this->half_tile_height_;
                        partWidth = (std::min((tx + 1) * (this->half_tile_width_), this->tile_width_) - partX);
                        partHeight = (std::min((ty + 1) * (this->half_tile_height_), this->tile_height_) - partY);
                        childTexWidth = static_cast<float>(this->children_[i]->texture_->getTexWidth());
                        childTexHeight = static_cast<float>(this->children_[i]->texture_->getTexHeight());
                        float *vertCoords = this->children_[i]->vertex_coordinates_.get();
                        // ll
                        texCoordBuffer[0] = 0.0f / childTexWidth;
                        texCoordBuffer[1] = 0.0f / childTexHeight;
                        vertCoords[0] = static_cast<float>(partX);
                        vertCoords[1] = static_cast<float>(partY);
                        // lr
                        texCoordBuffer[2] = static_cast<float>(this->children_[i]->tile_width_) / childTexWidth;
                        texCoordBuffer[3] = 0.0f / childTexHeight;
                        vertCoords[2] = static_cast<float>(partX + partWidth);
                        vertCoords[3] = static_cast<float>(partY);
                        // ur
                        texCoordBuffer[4] = static_cast<float>(this->children_[i]->tile_width_) / childTexWidth;
                        texCoordBuffer[5] = static_cast<float>(this->children_[i]->tile_height_) / childTexHeight;
                        vertCoords[4] = static_cast<float>(partX + partWidth);
                        vertCoords[5] = static_cast<float>(partY + partHeight);
                        // ul
                        texCoordBuffer[6] = 0.0f / childTexWidth;
                        texCoordBuffer[7] = static_cast<float>(this->children_[i]->tile_height_) / childTexHeight;
                        vertCoords[6] = static_cast<float>(partX);
                        vertCoords[7] = static_cast<float>(partY + partHeight);

                        this->children_[i]->texture_->draw(4, GL_FLOAT, this->texture_coordinates_.get(),
                                                         this->children_[i]->vertex_coordinates_.get());

                        // the child's vertex coordinates are now invalid
                        this->children_[i]->vertex_coords_valid_ = false;

                        // if the child is resolved, increment parts
                        if (this->children_[i]->state_ == State::RESOLVED)
                            parts++;
                    }
                }

                glBindFramebuffer(GL_FRAMEBUFFER, currentFrameBuffer);

                this->texture_coords_valid_ = false;
                this->received_update_ = true;

                this->validateTexVerts();

                if (!willBeResolved) {
                    this->texture_->setMagFilter(GL_NEAREST);
                    this->texture_->getTexId();
                }
            } else {
                Util::Logger_log(LogLevel::TELL_Warning, "Failed to create FBO for texture copy.");
                offscreenFboFailed = true;
            }

            const bool wasUnresolvable = (this->state_ == State::UNRESOLVABLE);

            // mark resolved if all 4 children were resolved
            if (parts == 4) {
                if (this->core_->options.childTextureCopyResolvesParent)
                    this->state_ = State::RESOLVED;
                else if (this->state_ != State::UNRESOLVABLE)
                    this->state_ = State::UNRESOLVED;
                // the tile is completely composited, but the client
                // indicated that the tile should be refreshed
                this->should_borrow_texture_ = false;
            } else if (this->state_ != State::SUSPENDED) {
                this->state_ = State::UNRESOLVED;
            }

            // if the tile was previously unresolvable, record whether or
            // not we were able to derive any data through compositing
            if (wasUnresolvable)
                this->derived_unresolvable_data_ |= (parts > 0);

            // if the tile will not be resolved via compositing, switch to
            // nearest neighbor interpolation to try to prevent edges
            if (!willBeResolved)
                this->texture_->setMinFilter(GL_NEAREST);
        }

        if ((this->state_ == State::RESOLVED) && this->borrowing_from_ != nullptr) {
            this->borrowing_from_->unborrowTexture(this);
            this->borrowing_from_ = nullptr;
        }
    } else if (this->state_ == State::RESOLVED) {
        // a refresh has been requested, move back into the unresolved state
        // to reload the tile
        this->state_ = State::UNRESOLVED;
    }

    // abandon the children before drawing ourself
    this->abandon();

    if (this->state_ == State::UNRESOLVED) {
        super_resolveTexture();
    }
}

void GLQuadTileNode2::resetFadeTimer()
{
    float levelScale = 1.0f - (float)(this->level_ - this->core_->drawPumpLevel) / (float)(this->root_->level_ - this->core_->drawPumpLevel);
    if (this->borrowing_from_ != nullptr) {
        this->fade_timer_ = std::max((int64_t)(this->core_->fadeTimerLimit * levelScale) - this->read_request_elapsed_, (int64_t)0);
    } else {
        this->fade_timer_ = 0;
    }
}

TAKErr GLQuadTileNode2::super_resolveTexture()
{
    this->state_ = State::RESOLVING;
    this->read_request_start_ = Port::Platform_systime_millis();
    return this->core_->tileReader->asyncRead(static_cast<int>(this->level_), this->tile_column_, this->tile_row_, this);
}

void GLQuadTileNode2::draw(const Renderer::Core::GLMapView2 &view, size_t level, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH,
                           int64_t poiX, int64_t poiY, bool cull)
{
    // dynamically refine level based on expected nominal resolution for tile as rendered
    TAKErr code(TE_Ok);
    double tileGsd;
    code = GLMapView2_estimateResolution(&tileGsd, nullptr, view, max_lat_, min_lng_, min_lat_, max_lng_);
    TE_CHECKRETURN(code);
    double scale = (this->core_->gsd / tileGsd);
    const int computed = (int)ceil(std::max((log(1.0 / scale) / log(2.0)) + this->core_->options.levelTransitionAdjustment, 0.0));
    if (computed > static_cast<int>(level)) {
        level = (static_cast<std::size_t>(computed) > this->level_) ? this->level_ : level;
    }

    if (this->level_ == level) {
        const bool abandon = (this->texture_ == nullptr);
        this->drawImpl(view);
        if (abandon && (this->texture_.get() != nullptr))
            this->abandon();
    } else if (this->level_ > level) {
        bool unresolvedChildren = false;
        bool isMultiRes;
        code = this->core_->tileReader->isMultiResolution(&isMultiRes);

        if (code == TE_Ok && isMultiRes) {
            for (int i = 0; i < 4; i++)
                unresolvedChildren |= (this->children_[i] != nullptr && this->children_[i]->state_ == State::UNRESOLVABLE);

            if (unresolvedChildren &&
                (this->state_ == State::UNRESOLVED || (this->state_ == State::UNRESOLVABLE && this->derived_unresolvable_data_))) {
                this->useCachedTexture();
                if (this->state_ == State::UNRESOLVED) {
                    this->validateTexture();
                    super_resolveTexture();
                }
            }

            if (unresolvedChildren && ((this->state_ != State::UNRESOLVED) || this->received_update_)) {
                // force child to re-borrow
                for (int i = 0; i < 4; i++) {
                    if (this->children_[i] != nullptr && this->children_[i]->state_ == State::UNRESOLVABLE) {
                        if (this->children_[i]->borrowing_from_ != nullptr && this->children_[i]->borrowing_from_ != this) {
                            this->children_[i]->borrowing_from_->unborrowTexture(this->children_[i].get());
                            this->children_[i]->borrowing_from_ = nullptr;
                        }
                    }
                }
            }
        }

        // when progressive load is enabled, only allow children to draw
        // once parent data for every other level has been rendered
        if (this->core_->options.progressiveLoad && (this->level_ % 3) == 0 && (!this->touched_ || this->fade_timer_ > 0L)) {
            this->core_->progressiveLoading = true;
            this->drawImpl(view);
            return;
        } else {
            this->core_->progressiveLoading = false;
        }

        const int64_t maxSrcX = (srcX + srcW) - 1;
        const int64_t maxSrcY = (srcY + srcH) - 1;

        const int64_t tileMidSrcX = (this->tile_src_x_ + ((long)this->half_tile_width_ << this->level_));
        const int64_t tileMidSrcY = (this->tile_src_y_ + ((long)this->half_tile_height_ << this->level_));
        const int64_t tileMaxSrcX = (this->tile_src_x_ + this->tile_src_width_) - 1;
        const int64_t tileMaxSrcY = (this->tile_src_y_ + this->tile_src_height_) - 1;

        // XXX - quickfix to get some more tiles into view when zoomed out
        //       in globe mode. selection should be done via intersection
        //       with frustum or global camera distance dynamic level
        //       selection

        int64_t tileW, tileH;
        code = this->core_->tileReader->getWidth(&tileW);
        TE_CHECKRETURN(code);
        code = this->core_->tileReader->getHeight(&tileH);
        TE_CHECKRETURN(code);

        const int64_t limitX = tileW - 1;
        const int64_t limitY = tileH - 1;

        const bool left = (srcX < std::min(tileMidSrcX, limitX)) && (maxSrcX > std::max(this->tile_src_x_, (int64_t)0));
        const bool upper = (srcY < std::min(tileMidSrcY, limitY)) && (maxSrcY > std::max(this->tile_src_y_, (int64_t)0));
        const bool right = (srcX < std::min(tileMaxSrcX, limitX)) && (maxSrcX > std::max(tileMidSrcX, (int64_t)0));
        const bool lower = (srcY < std::min(tileMaxSrcY, limitY)) && (maxSrcY > std::max(tileMidSrcY, (int64_t)0));

        // orphan all children that are not visible
        std::vector<GLQuadTileNode2Ptr> orphanVec;
        if (cull) {
            code = this->orphan(&orphanVec, view.drawVersion, !(upper && left), !(upper & right), !(lower && left), !(lower && right));
            TE_CHECKRETURN(code);
        }

        bool visibleChildren[4] = {
            (upper && left),
            (upper && right),
            (lower && left),
            (lower && right),
        };

        int visCount = 0;
        auto orphanIter = orphanVec.begin();
        for (int i = 0; i < 4; i++) {
            if (visibleChildren[i]) {
                if (this->children_[i] == nullptr) {
                    if (orphanIter != orphanVec.end()) {
                        code = this->adopt(i, std::move(*orphanIter));
                        orphanIter++;
                        TE_CHECKBREAK_CODE(code);
                    } else {
                        GLQuadTileNode2Ptr newChild(nullptr, nullptr);
                        code = create(newChild, this, i);
                        this->children_[i] = std::move(newChild);
                        TE_CHECKBREAK_CODE(code);
                    }
                }
                // XXX - overdraw above is emitting 0 dimension children
                //       causing subsequent FBO failure. implementing
                //       quickfix to exclude empty children
                visibleChildren[i] &= (this->children_[i]->tile_width_ > 0 && this->children_[i]->tile_height_ > 0);
                if (!visibleChildren[i])
                    continue;

                this->children_[i]->last_touch_ = view.drawVersion;
                visCount++;
            }
        }
        TE_CHECKRETURN(code);

        // there are no visible children. this node must have been selected
        // for overdraw -- draw it and return
        if (visCount == 0) {
            this->is_overdraw_ = true;
            this->drawImpl(view);
            return;
        } else if (!cull) {
            // if this is not the 'cull' pass and we have not performed
            // overdraw
            this->is_overdraw_ = false;
        }

        // if there are no unresolved children and this node is requesting
        // tile data, cancel request
        if (!unresolvedChildren && this->current_request_id_valid_ && (cull && !this->is_overdraw_)) {
            this->core_->tileReader->asyncCancel(this->current_request_id_);
            this->current_request_id_valid_ = false;
        }

        int iterOffset = 2;
        if (poiX > 0L || poiY > 0L) {
            // XXX - I think this should happen implicitly based on the
            //       POI vector, but it doesn't appear to???
            if (atakmap::math::Rectangle<int64_t>::contains(this->tile_src_x_, this->tile_src_y_, this->tile_src_x_ + this->tile_src_width_,
                                                            this->tile_src_y_ + this->tile_src_height_, poiX, poiY)) {
                for (int i = 0; i < 4; i++) {
                    if (visibleChildren[i] && atakmap::math::Rectangle<int64_t>::contains(
                                                  this->children_[i]->tile_src_x_, this->children_[i]->tile_src_y_,
                                                  this->children_[i]->tile_src_x_ + this->children_[i]->tile_src_width_,
                                                  this->children_[i]->tile_src_y_ + this->children_[i]->tile_src_height_, poiX, poiY)) {
                        this->children_[i]->vertices_invalid_ = this->vertices_invalid_;
                        this->children_[i]->draw(view, level, srcX, srcY, srcW, srcH, poiX, poiY, cull);

                        // already drawn
                        visibleChildren[i] = false;

                        break;
                    }
                }
            }

            // obtain angle between tile center and POI
            const int64_t dx = poiX - tileMidSrcX;
            const int64_t dy = poiY - tileMidSrcY;
            double theta = atan2(dy, dx) * 180.0 / M_PI;
            if (theta < 0.0)
                theta += 360.0;

            // determine the half quadrant that the vector the POI extends
            // through
            int halfQuadrant = (int)(theta / 45.0) % 8;
            if (halfQuadrant >= 0 && halfQuadrant <= 7)
                iterOffset = halfQuadrant;
        }

        for (int j = 0; j < 4; j++) {
            const int i = POI_ITERATION_BIAS[(iterOffset * 4) + j];
            if (visibleChildren[i]) {
                this->children_[i]->vertices_invalid_ = this->vertices_invalid_;
                this->children_[i]->draw(view, level, srcX, srcY, srcW, srcH, poiX, poiY, cull);
                visibleChildren[i] = false;
            }
        }

        // if no one is borrowing from us, release our texture
        if (this->borrowers_.empty() && this->texture_ != nullptr && !unresolvedChildren && (cull && !this->is_overdraw_))
            this->releaseTexture();
    } else {
        // throw new IllegalStateException();
        return;
    }

    if (this->parent_ == nullptr) {
        if (this->core_->frameBufferHandle != 0) {
            glDeleteFramebuffers(1, &this->core_->frameBufferHandle);
            this->core_->frameBufferHandle = 0;
        }
        if (this->core_->depthBufferHandle != 0) {
            glDeleteRenderbuffers(1, &this->core_->depthBufferHandle);
            this->core_->depthBufferHandle = 0;
        }
    }

    this->vertices_invalid_ = false;
}

void GLQuadTileNode2::drawTexture(const Renderer::Core::GLMapView2 &view, Renderer::GLTexture2 *tex, float *texCoords)
{
    if (tex == this->texture_.get()) {
        if (this->texture_->getMinFilter() != this->core_->minFilter)
            this->texture_->setMinFilter(this->core_->minFilter);
        if (this->texture_->getMagFilter() != this->core_->magFilter)
            this->texture_->setMagFilter(this->core_->magFilter);
    }
    super_drawTexture(view, tex, texCoords);
}

void GLQuadTileNode2::setLCS(const Renderer::Core::GLMapView2 &view)
{
    Math::Matrix2 matrix;
    matrix.concatenate(view.scene.forwardTransform);
    matrix.translate(centroid_proj_.x, centroid_proj_.y, centroid_proj_.z);
    if (view.drawSrid == 4326 && view.crossesIDL && centroid_.longitude*view.drawLng < 0)
        matrix.translate((view.drawLng < 0) ? -360.0 : 360.0, 0.0, 0.0);
    double matrixD[16];
    matrix.get(matrixD, Math::Matrix2::COLUMN_MAJOR);
    float matrixF[16];
    for (int i = 0; i < 16; i++)
        matrixF[i] = (float)matrixD[i];
    atakmap::renderer::GLES20FixedPipeline::getInstance()->glLoadMatrixf(matrixF);
}

void GLQuadTileNode2::super_drawTexture(const Renderer::Core::GLMapView2 &view, Renderer::GLTexture2 *tex, float *texCoords)
{
    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glPushMatrix();
    setLCS(view);

    float fade = (tex == this->texture_.get() && this->core_->fadeTimerLimit > 0)
                     ? ((float)(this->core_->fadeTimerLimit - this->fade_timer_) / (float)this->core_->fadeTimerLimit)
                     : 1.0f;

    if (gl_tex_coord_indices_ == nullptr) {
        GLTexture2_draw(tex->getTexId(), GL_TRIANGLE_STRIP, this->gl_tex_grid_vert_count_, 2, GL_FLOAT, texCoords, 3, GL_FLOAT,
                        this->vertex_coordinates_.get(), this->core_->colorR, this->core_->colorG, this->core_->colorB,
                        fade * this->core_->colorA);
    } else {
        GLTexture2_draw(tex->getTexId(), GL_TRIANGLE_STRIP, this->gl_tex_grid_idx_count_, 2, GL_FLOAT, texCoords, 3, GL_FLOAT,
                        this->vertex_coordinates_.get(), GL_UNSIGNED_SHORT, this->gl_tex_coord_indices_.get(), this->core_->colorR,
                        this->core_->colorG, this->core_->colorB, fade * this->core_->colorA);
    }
    fixedPipe->glPopMatrix();

    core_->tilesThisFrame++;
}

const char *GLQuadTileNode2::getTextureKey()
{
    if (texture_key_.empty()) {
        std::stringstream sstr;
        sstr << core_->uri << "," << level_ << "," << tile_column_ << "," << tile_row_;
        texture_key_ = sstr.str();
    }
    return texture_key_.c_str();
}

void GLQuadTileNode2::invalidateVertexCoords()
{
    this->vertices_invalid_ = true;
}

void GLQuadTileNode2::expandTexGrid()
{
    this->gl_tex_grid_width_ *= 2;
    this->gl_tex_grid_height_ *= 2;

    size_t newSize = (this->gl_tex_grid_width_ + 1) * (this->gl_tex_grid_height_ + 1);
    if (newSize > this->grid_vertices_.size())
        std::fill(this->grid_vertices_.begin(), this->grid_vertices_.end(), GridVertex());
    this->grid_vertices_.resize(newSize);

    this->texture_coords_valid_ = false;
}

void GLQuadTileNode2::super_invalidateVertexCoords()
{
    this->vertex_coords_valid_ = false;
}

void GLQuadTileNode2::drawImpl(const Renderer::Core::GLMapView2 &view)
{
    this->vertex_coords_valid_ &= !this->vertices_invalid_;

    // clear cached mesh vertices
    if (this->vertices_invalid_)
        std::fill(this->grid_vertices_.begin(), this->grid_vertices_.end(), GridVertex());

    super_draw(view);
    if (this->state_ == State::RESOLVED && this->fade_timer_ == 0) {
        this->should_borrow_texture_ = false;
        if (this->borrowing_from_ != nullptr) {
            this->borrowing_from_->unborrowTexture(this);
            this->borrowing_from_ = nullptr;
        }
    } else if (this->state_ == State::RESOLVED) {
        this->fade_timer_ = std::max(this->fade_timer_ - view.animationDelta, (int64_t)0);
    }
}

void GLQuadTileNode2::super_draw(const Renderer::Core::GLMapView2 &view)
{
    if (this->core_->textureCache != nullptr && !((this->state_ == State::RESOLVED) || this->received_update_))
        this->useCachedTexture();

    this->validateTexture();

    // check the tiles version and move back into the UNRESOLVED state if
    // the tile version has changed
    bool checkShouldResolve = true;
    if ((this->state_ == State::UNRESOLVABLE || this->state_ == State::RESOLVED) && core_->versionCheckEnabled) {
        int64_t trVersion;
        TE_CHECKRETURN(this->core_->tileReader->getTileVersion(&trVersion, this->level_, this->tile_column_, this->tile_row_));
        if (this->tile_version_ != trVersion) {
            if (this->state_ == State::UNRESOLVABLE)
                this->state_ = State::UNRESOLVED;
            this->resolveTexture();
            checkShouldResolve = false;
        }
    }

    // read the data if we don't have it yet
    if (checkShouldResolve && this->shouldResolve())
        this->resolveTexture();

    if (!touched_ && (this->state_ == State::RESOLVED || this->state_ == State::UNRESOLVABLE)) {
        touched_ = true;
        if (this->core_->progressiveLoading)
            this->core_->context->requestRefresh();
    }

    if (this->state_ != State::RESOLVED || this->fade_timer_ > 0) {
        if (this->loading_texture_coordinates_ == nullptr || (this->loading_texture_coordinates_capacity_ < (this->gl_tex_grid_vert_count_ * 2))) {
            this->loading_texture_coordinates_capacity_ = this->gl_tex_grid_vert_count_ * 2;
            this->loading_texture_coordinates_.reset(new float[this->loading_texture_coordinates_capacity_]);
        }
        GLTexture2 *loadingTexture = nullptr;
        TAKErr code =
            this->getLoadingTexture(&loadingTexture, this->loading_texture_coordinates_.get(), this->gl_tex_grid_width_, this->gl_tex_grid_height_);
        TE_CHECKRETURN(code);

        if (loadingTexture != nullptr) {
            this->validateVertexCoords(view);

            this->drawTexture(view, loadingTexture, this->loading_texture_coordinates_.get());
        }
    } else {
        this->loading_texture_coordinates_.reset();
        this->loading_texture_coordinates_capacity_ = 0;
    }
    if (this->received_update_) {
        this->validateVertexCoords(view);

        this->drawTexture(view, this->texture_.get(), this->texture_coordinates_.get());
    }

    if (this->core_->debugDrawEnabled)
        this->debugDraw(view);
}

void GLQuadTileNode2::debugDraw(const Renderer::Core::GLMapView2 &view)
{
    this->validateVertexCoords(view);

    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    fixedPipe->glColor4f(1.0f, 1.0f, 0.0f, 1.0f);
    fixedPipe->glLineWidth(2.0f);
    fixedPipe->glVertexPointer(3, GL_FLOAT, 0, this->vertex_coordinates_.get());

    fixedPipe->glPushMatrix();
    this->setLCS(view);

    if (this->debug_draw_indices_.get() != nullptr) {
        fixedPipe->glDrawElements(GL_LINES, static_cast<int>(debug_draw_indices_count_), GL_UNSIGNED_SHORT, this->debug_draw_indices_.get());
    }

    fixedPipe->glColor4f(0.0f, 1.0f, 1.0f, 1.0f);

    int16_t dbg[4];
    dbg[0] = (short)0;
    dbg[1] = (short)this->gl_tex_grid_width_;
    dbg[2] = (short)(((this->gl_tex_grid_height_ + 1) * (this->gl_tex_grid_width_ + 1)) - 1);
    dbg[3] = (short)((this->gl_tex_grid_height_ * (this->gl_tex_grid_width_ + 1)));

    fixedPipe->glDrawElements(GL_LINE_LOOP, 4, GL_UNSIGNED_SHORT, dbg);

    fixedPipe->glPopMatrix();

    glDisable(GL_BLEND);
    fixedPipe->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

    TextFormat2Ptr txtfmt(nullptr, nullptr);
    TextFormat2_createDefaultSystemTextFormat(txtfmt, 14);
    std::shared_ptr<TextFormat2> sharedFmt = std::move(txtfmt);
    GLText2 *_titleText = GLText2_intern(sharedFmt);

    Math::Point2<double> pointD;
    Math::Point2<float> pointF;
    GeoPoint2 geo;
    pointD.x = static_cast<double>(this->tile_src_x_ + this->tile_src_width_ / 2);
    pointD.y = static_cast<double>(this->tile_src_y_ + this->tile_src_height_ / 2);
    this->core_->imprecise->imageToGround(&geo, pointD);
    view.forward(&pointF, geo);
    fixedPipe->glPushMatrix();
    fixedPipe->glTranslatef(pointF.x, pointF.y, 0);

    std::stringstream sstr;
    sstr << this->tile_column_ << "," << this->tile_row_ << "," << this->level_ << " " << getNameForState(this->state_);
    std::string s = sstr.str();
    _titleText->draw(s.c_str(), 0.0f, 0.0f, 1.0f, 1.0f);
    fixedPipe->glPopMatrix();
}

TAKErr GLQuadTileNode2::adopt(int idx, GLQuadTileNode2Ptr child)
{
    if (this->children_[idx] != nullptr)
        return TE_IllegalState;
    this->children_[idx] = std::move(child);
    TAKErr code = this->children_[idx]->set((this->tile_column_ * 2) + (idx % 2), (this->tile_row_ * 2) + (idx / 2), this->level_ - 1);
    this->children_[idx]->parent_ = this;
    return code;
}

TAKErr GLQuadTileNode2::orphan(std::vector<GLQuadTileNode2Ptr> *orphans, int64_t drawVersion, bool upperLeft, bool upperRight,
                               bool lowerLeft, bool lowerRight)
{
    const bool shouldOrphan[]{
        upperLeft,
        upperRight,
        lowerLeft,
        lowerRight,
    };

    for (int i = 0; i < 4; i++) {
        if (shouldOrphan[i] && this->children_[i] != nullptr && this->children_[i]->last_touch_ != drawVersion) {
            this->children_[i]->parent_ = nullptr;
            orphans->push_back(std::move(this->children_[i]));
        }
    }
    std::sort(orphans->begin(), orphans->end(), orphanSort);
    return TE_Ok;
}

/**
 * Abandons all of the children.
 */
void GLQuadTileNode2::abandon()
{
    for (int i = 0; i < 4; i++) {
        this->children_[i].reset();
    }
}

/**************************************************************************/

// XXX - implementation for getState

GLQuadTileNode2::State GLQuadTileNode2::getState()
{
    const int mask = this->recurseState();

    // if anything in the hierarchy is resolving, return resolving
    if (MathUtils_hasBits(mask, STATE_RESOLVING) || this->core_->progressiveLoading)
        return State::RESOLVING;
    return this->getStateImpl();
}

GLQuadTileNode2::State GLQuadTileNode2::getStateImpl()
{
    State retval(UNRESOLVED);
    bool haveRetval = false;
    State childState;
    for (int i = 0; i < 4; i++) {
        if (this->children_[i] == nullptr)
            continue;
        childState = this->children_[i]->getState();
        if (childState == State::UNRESOLVABLE || childState == State::SUSPENDED)
            return childState;
        else if (!haveRetval || childState != State::RESOLVED) {
            retval = childState;
            haveRetval = true;
        }
    }
    if (!haveRetval)
        retval = this->state_;
    return retval;
}

int GLQuadTileNode2::recurseState()
{
    int retval = 0;
    switch (this->state_) {
        case UNRESOLVED:
            retval |= STATE_UNRESOLVED;
            break;
        case RESOLVING:
            retval |= STATE_RESOLVING;
            break;
        case RESOLVED:
            if (this->fade_timer_ > 0)
                retval |= STATE_RESOLVING;
            else
                retval |= STATE_RESOLVED;
            break;
        case UNRESOLVABLE:
            retval |= STATE_UNRESOLVABLE;
            break;
        case SUSPENDED:
            retval |= STATE_SUSPENDED;
            break;
    }

    for (int i = 0; i < 4; i++) {
        if (this->children_[i] != nullptr)
            retval |= this->children_[i]->recurseState();
    }
    return retval;
}

GLQuadTileNode2::State GLQuadTileNode2::super_getState()
{
    return this->state_;
}

void GLQuadTileNode2::suspend()
{
    for (int i = 0; i < 4; i++)
        if (this->children_[i] != nullptr)
            this->children_[i]->suspend();
    super_suspend();
}

void GLQuadTileNode2::super_suspend()
{
    if (this->state_ == State::RESOLVING && this->current_request_id_valid_) {
        this->core_->tileReader->asyncCancel(this->current_request_id_);
        this->state_ = State::SUSPENDED;
    }
}

void GLQuadTileNode2::resume()
{
    for (int i = 0; i < 4; i++)
        if (this->children_[i] != nullptr)
            this->children_[i]->resume();
    super_resume();
}

void GLQuadTileNode2::super_resume()
{
    // move us back into the unresolved from suspended to re-enable texture
    // loading
    if (this->state_ == State::SUSPENDED)
        this->state_ = State::UNRESOLVED;
}

/**************************************************************************/
// RasterDataAccess2

TAKErr GLQuadTileNode2::getType(Port::String &value) NOTHROWS
{
    value = this->core_->type.c_str();
    return TE_Ok;
}

TAKErr GLQuadTileNode2::getUri(Port::String &value) NOTHROWS
{
    value = this->core_->uri.c_str();
    return TE_Ok;
}

TAKErr GLQuadTileNode2::imageToGround(GeoPoint2 *ground, bool *isPrecise, const Math::Point2<double> &image) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (this->core_->precise.get() != nullptr && isPrecise != nullptr) {
        do {
            code = this->core_->precise->imageToGround(ground, image);
            TE_CHECKBREAK_CODE(code);
            // we were able to compute the precise I2G value -- make sure it
            // falls within the maximum allowed error
            GeoPoint2 imprecise;
            code = this->core_->imprecise->imageToGround(&imprecise, image);
            TE_CHECKBREAK_CODE(code)

            const double err = DistanceCalculations_calculateRange(*ground, imprecise);
            const int errPixels = (int)(err / this->core_->gsd);
            size_t tileWidth, tileHeight;
            code = this->core_->tileReader->getTileWidth(&tileWidth);
            TE_CHECKBREAK_CODE(code);
            code = this->core_->tileReader->getTileHeight(&tileHeight);
            TE_CHECKBREAK_CODE(code);

            if (errPixels > (sqrt((tileWidth * tileWidth) + (tileHeight * tileHeight)) / 8.0)) {
                Port::String s;
                this->getType(s);
                Util::Logger_log(LogLevel::TELL_Warning,
                                 "Large discrepency observed for %s imageToGround, discarding point (error=%f m, %d px)", s.get(), err,
                                 errPixels);

                // fall through to imprecise computation
            } else {
                *isPrecise = true;
                return TE_Ok;
            }
        } while (false);
    }
    if (isPrecise != nullptr)
        *isPrecise = false;
    return this->core_->imprecise->imageToGround(ground, image);
}

TAKErr GLQuadTileNode2::groundToImage(Math::Point2<double> *image, bool *isPrecise, const GeoPoint2 &ground) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (this->core_->precise.get() != nullptr && isPrecise != nullptr) {
        code = this->core_->precise->groundToImage(image, ground);
        if (code == TE_Ok) {
            do {
                // we were able to compute the precise G2I value -- make sure it
                // falls within the maximum allowed error
                Math::Point2<double> imprecise(0.0, 0.0);
                code = this->core_->imprecise->groundToImage(&imprecise, ground);
                TE_CHECKBREAK_CODE(code);
                const double dx = (image->x - imprecise.x);
                const double dy = (image->y - imprecise.y);
                const double errPixels = sqrt(dx * dx + dy * dy);
                const double errMeters = (int)(errPixels * this->core_->gsd);
                size_t tileWidth, tileHeight;
                code = this->core_->tileReader->getTileWidth(&tileWidth);
                TE_CHECKBREAK_CODE(code);
                code = this->core_->tileReader->getTileHeight(&tileHeight);
                TE_CHECKBREAK_CODE(code);
                if (errPixels > (sqrt((tileWidth * tileWidth) + (tileHeight * tileHeight)) / 8.0)) {
                    Port::String s;
                    this->getType(s);
                    Util::Logger_log(LogLevel::TELL_Warning,
                                     "Large discrepency observed for %s groundToImage, discarding point (error=%f m, %f px)", s.get(),
                                     errMeters, errPixels);
                    // Fall through to error return

                } else {
                    *isPrecise = true;
                    return TE_Ok;
                }
            } while (false);

            return TE_Err;
        }
    }
    if (isPrecise != nullptr)
        *isPrecise = false;
    return this->core_->imprecise->groundToImage(image, ground);
}

TAKErr GLQuadTileNode2::getSpatialReferenceId(int *value) NOTHROWS
{
    *value = this->core_->srid;
    return TE_Ok;
}

TAKErr GLQuadTileNode2::hasPreciseCoordinates(bool *value) NOTHROWS
{
    *value = (this->core_->precise.get() != nullptr);
    return TE_Ok;
}

TAKErr GLQuadTileNode2::getWidth(int *value) NOTHROWS
{
    int64_t trw;
    TAKErr code = this->core_->tileReader->getWidth(&trw);
    // To match Java reference impl, not checking range
    *value = (int)trw;
    return TE_Ok;
}

TAKErr GLQuadTileNode2::getHeight(int *value) NOTHROWS
{
    int64_t trh;
    TAKErr code = this->core_->tileReader->getHeight(&trh);
    // To match Java reference impl, not checking range
    *value = (int)trh;
    return TE_Ok;
}

TAKErr GLQuadTileNode2::getImageInfo(ImageInfoPtr_const &info) NOTHROWS
{
    TAKErr code(TE_Ok);
    Thread::Lock lock(this->core_->infoLock);
    if (!this->core_->imageInfo) {
        GeoPoint2 ul, lr, ll, ur;
        int tw, th;
        bool isPrecise;
        code = getHeight(&th);
        TE_CHECKRETURN_CODE(code);
        code = getWidth(&tw);
        TE_CHECKRETURN_CODE(code);
        code = this->imageToGround(&ul, &isPrecise, Math::Point2<double>(0, 0, 0));
        TE_CHECKRETURN_CODE(code);
        code = this->imageToGround(&ur, &isPrecise, Math::Point2<double>(tw, 0, 0));
        TE_CHECKRETURN_CODE(code);
        code = this->imageToGround(&lr, &isPrecise, Math::Point2<double>(tw, th, 0));
        TE_CHECKRETURN_CODE(code);
        code = this->imageToGround(&ll, &isPrecise, Math::Point2<double>(0, th, 0));
        TE_CHECKRETURN_CODE(code);

        double gsd = atakmap::raster::DatasetDescriptor::computeGSD(tw, th, atakmap::core::GeoPoint(ul), atakmap::core::GeoPoint(ur), atakmap::core::GeoPoint(lr), atakmap::core::GeoPoint(ll));

        this->core_->imageInfo = ImageInfoPtr(
            new ImageInfo(core_->uri.c_str(), core_->type.c_str(), core_->precise != nullptr, ul, ur, lr, ll, gsd, tw, th, core_->srid),
            Memory_deleter_const<ImageInfo>);
    }
    info = ImageInfoPtr_const(this->core_->imageInfo.get(), Memory_leaker_const<ImageInfo>);
    return TE_Ok;
}

/**************************************************************************/
// Asynchronous Read Request Listener and private support functions

void GLQuadTileNode2::rendererIORunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<IOCallbackOpaque> iocb(static_cast<IOCallbackOpaque *>(opaque));
    if (iocb->owner)
        iocb->owner->rendererIORunnableImpl(iocb.get());
}

void GLQuadTileNode2::rendererIORunnableImpl(IOCallbackOpaque *iocb)
{
    if (this->checkRequest(iocb->reqId)) {
        switch (iocb->type) {
            case IOCallbackOpaque::UpdateType::CB_Canceled:
                this->current_request_id_valid_ = false;
                break;

            case IOCallbackOpaque::UpdateType::CB_Completed:
                this->read_request_complete_ = Port::Platform_systime_millis();
                this->read_request_elapsed_ = (read_request_complete_ - read_request_start_);

                this->state_ = State::RESOLVED;
                this->resetFadeTimer();

                if (MIPMAP_ENABLED) {
                    glBindTexture(GL_TEXTURE_2D, this->texture_->getTexId());
                    this->texture_->setMinFilter(GL_LINEAR_MIPMAP_NEAREST);
                    glGenerateMipmap(GL_TEXTURE_2D);
                    glBindTexture(GL_TEXTURE_2D, 0);
                }

                // XXX - should be packaged in read request
                this->core_->tileReader->getTileVersion(&this->tile_version_, this->current_request_level_, this->current_request_col_,
                                                       this->current_request_row_);

                this->current_request_id_valid_ = false;

                break;

            case IOCallbackOpaque::UpdateType::CB_Error:
                this->current_request_id_valid_ = false;

                // XXX - should be packaged in read request
                this->core_->tileReader->getTileVersion(&this->tile_version_, this->current_request_level_, this->current_request_col_,
                                                       this->current_request_row_);

                this->state_ = State::UNRESOLVABLE;
                break;

            case IOCallbackOpaque::UpdateType::CB_Update:
                if (iocb->data.get()) {
                    this->texture_->load(*iocb->data);
                    this->received_update_ = true;
                } else {
                    // OOM occured
                    // XXX - should be packaged in read request
                    this->core_->tileReader->getTileVersion(&this->tile_version_, this->current_request_level_, this->current_request_col_,
                                                           this->current_request_row_);

                    this->state_ = State::UNRESOLVABLE;
                }
                break;
        }
    }
    Thread::Lock lock(this->queued_callbacks_mutex_);

    for (auto iter = this->queued_callbacks_.begin(); iter != this->queued_callbacks_.end(); ++iter) {
        if (iocb == *iter) {
            queued_callbacks_.erase(iter);
            break;
        }
    }
}

void GLQuadTileNode2::queueGLCallback(IOCallbackOpaque *iocb)
{
    Thread::Lock lock(this->queued_callbacks_mutex_);
    this->queued_callbacks_.push_back(iocb);
    this->core_->context->queueEvent(rendererIORunnable, std::unique_ptr<void, void(*)(const void *)>(iocb, Memory_leaker_const<void>));
}

bool GLQuadTileNode2::checkRequest(int id)
{
    return (this->current_request_id_valid_ && this->current_request_id_ == id);
}

void GLQuadTileNode2::requestCreated(const int id) NOTHROWS
{
    this->current_request_id_ = id;
    this->current_request_id_valid_ = true;
    this->current_request_level_ = this->level_;
    this->current_request_row_ = this->tile_row_;
    this->current_request_col_ = this->tile_column_;
}

void GLQuadTileNode2::requestStarted(const int id) NOTHROWS
{
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestStarted(id=%d), currentRequest=%d)", toString(false), id, this->current_request_id_);
    }
}

void GLQuadTileNode2::requestUpdate(const int id, const Bitmap2 &data) NOTHROWS
{
    if (DEBUG_QUADTILE) {
        Logger_log(TELL_Debug, "%s requestUpdate(id=%d), currentRequest=%d)", toString(false), id, this->current_request_id_);
    }

    const bool curidvalid = this->current_request_id_valid_;
    const int curid = this->current_request_id_;
    if (!curidvalid || curid != id)
        return;

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP()
    {
        queueGLCallback(new IOCallbackOpaque(*this, curid, data));
    }
    TE_END_TRAP(code);
}

void GLQuadTileNode2::requestCompleted(const int id) NOTHROWS
{
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestCompleted(id=%d), currentRequest=%d)", toString(false), id,
                         this->current_request_id_);
    }

    const int curId = current_request_id_;
    if (!current_request_id_valid_ || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Completed, *this, curId));
}

void GLQuadTileNode2::requestCanceled(const int id) NOTHROWS
{
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestCanceled(id=%d), currentRequest=%d)", toString(false), id,
                         this->current_request_id_);
    }
    const int curId = current_request_id_;
    if (!current_request_id_valid_ || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Canceled, *this, curId));
}

void GLQuadTileNode2::requestError(const int id, const Util::TAKErr code, const char *msg) NOTHROWS
{
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestError(id=%d), currentRequest=%d)", toString(false), id, this->current_request_id_);
    }

    Util::Logger_log(LogLevel::TELL_Error, "asynchronous read error id = %d  code = %d (%s)", id, code, msg ? msg : "no message");

    const int curId = current_request_id_;
    if (!current_request_id_valid_ || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Error, *this, curId));
}

std::string GLQuadTileNode2::toString(bool l)
{
    std::stringstream sstr;
    sstr << "GLQuadTileNode2@" << (intptr_t)(this);
    if (l)
        sstr << " {level=" << this->level_ << ",tileColumn=" << this->tile_column_ << ",tileRow=" << this->tile_row_
             << ",tileWidth=" << this->tile_width_ << ",tileHeight=" << this->tile_height_ << "}";
    return sstr.str();
}

// Porting notes: Java comparitor is <0 if n1 before n2, 0 if equal or >0 if n2 before n1
// std sort comparitor wants true iff n1 before n2
bool GLQuadTileNode2::orphanSort(const GLQuadTileNode2Ptr &n1, const GLQuadTileNode2Ptr &n2)
{
    if (n1->texture_ != nullptr && n2->texture_ != nullptr) {
        std::size_t dx = (n1->texture_->getTexWidth() - n2->texture_->getTexWidth());
        std::size_t dy = (n1->texture_->getTexHeight() - n2->texture_->getTexHeight());
        if ((dx * dy) > 0)
            return dx < 0;

    } else if (n1->texture_ != nullptr && n2->texture_ == nullptr) {
        // n2 has tex, so orphan it before n1
        return false;
    } else if (n1->texture_ == nullptr && n2->texture_ != nullptr) {
        // n1 has tex, so orphan it before n2
        return true;
    }

    return (n2.get() - n1.get()) > 0;
}

/**************************************************************************/
// Initializer

GLQuadTileNode2::Initializer::~Initializer() {}

/**************************************************************************/
// Options

GLQuadTileNode2::Options::Options()
    : textureCopyEnabled(true),
      childTextureCopyResolvesParent(true),
      textureCache(nullptr),
      progressiveLoad(true),
      levelTransitionAdjustment(0.0),
      textureBorrowEnabled(true)
{}

/**************************************************************************/
// GridVertex

GLQuadTileNode2::GridVertex::GridVertex() : value(), resolved(false), projected(0.0, 0.0), projectedSrid(-1) {}

/**************************************************************************/
// NodeCore

GLQuadTileNode2::NodeCore::NodeCore(TAK::Engine::Core::RenderContext *context, const char *type, const Initializer &init,
                                    TileReader2Ptr &reader, DatasetProjection2Ptr &imprecise,
                                    DatasetProjection2Ptr &precise, int srid, const Options &opts)
    : tileReader(std::move(reader)),
      imprecise(std::move(imprecise)),
      precise(std::move(precise)),
      srid(srid),
      type(type),
      options(opts),
      context(context),
      uri(),
      textureBorrowEnabled(false),
      textureCopyEnabled(false),
      gsd(0.0),
      vertexResolver(nullptr),
      textureCache(opts.textureCache),
      upperLeft(),
      upperRight(),
      lowerRight(),
      lowerLeft(),
      frameBufferHandle(0),
      depthBufferHandle(0),
      minFilter(GL_NEAREST),
      magFilter(GL_LINEAR),
      color(0xFFFFFFFF),
      colorR(1.0f),
      colorG(1.0f),
      colorB(1.0f),
      colorA(1.0f),
      debugDrawEnabled(false),
      enableTextureTargetFBO(false),
      loadingTextureEnabled(false),
      drawROI{},
      drawPumpHemi(-1),
      drawPumpLevel(0),
      progressiveLoading(false),
      fadeTimerLimit(0),
      versionCheckEnabled(true),
      tilesThisFrame(0),
      infoLock(),
      imageInfo(nullptr, nullptr)
{}

TAKErr GLQuadTileNode2::NodeCore::initCore(double gsdHint)
{
    TAKErr code;
    Port::String pstr;
    bool b;

    code = this->tileReader->getUri(pstr);
    TE_CHECKRETURN_CODE(code);
    this->uri = pstr;

    code = this->tileReader->isMultiResolution(&b);
    TE_CHECKRETURN_CODE(code);
    this->options.progressiveLoad &= b;

    this->enableTextureTargetFBO = (ConfigOptions_getIntOptionOrDefault("glquadtilenode2.enableTextureTargetFBO", 1) != 0);
    this->debugDrawEnabled = (ConfigOptions_getIntOptionOrDefault("imagery.debug-draw-enabled", 0) != 0);
    this->fadeTimerLimit = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.fade-timer-limit", 0);

    this->textureBorrowEnabled = (ConfigOptions_getIntOptionOrDefault("imagery.texture-borrow", 1) != 0);
    this->textureCopyEnabled = (ConfigOptions_getIntOptionOrDefault("imagery.texture-copy", 1) != 0);

    int64_t trw, trh;
    code = this->tileReader->getWidth(&trw);
    TE_CHECKRETURN_CODE(code);
    code = this->tileReader->getHeight(&trh);
    TE_CHECKRETURN_CODE(code);

    code = this->imprecise->imageToGround(&(this->upperLeft), Math::Point2<double>(0, 0));
    TE_CHECKRETURN_CODE(code);
    code = this->imprecise->imageToGround(&(this->upperRight), Math::Point2<double>(static_cast<double>(trw - 1), 0));
    TE_CHECKRETURN_CODE(code);
    code = this->imprecise->imageToGround(&(this->lowerRight), Math::Point2<double>(static_cast<double>(trw - 1), static_cast<double>(trh - 1)));
    TE_CHECKRETURN_CODE(code);
    code = this->imprecise->imageToGround(&(this->lowerLeft), Math::Point2<double>(0, static_cast<double>(trh - 1)));
    TE_CHECKRETURN_CODE(code);

    // if dataset bounds cross poles (e.g. Google "Flat Projection"
    // tile server), clip the bounds inset a very small amount from the
    // poles
    const double minLat = std::min({this->upperLeft.latitude, this->upperRight.latitude, this->lowerRight.latitude});
    const double maxLat = std::max({this->upperLeft.latitude, this->upperRight.latitude, this->lowerRight.latitude});

    if (minLat < -90.0 || maxLat > 90.0) {
        const double minLatLimit = -90.0 + POLE_LATITUDE_LIMIT_EPISLON;
        const double maxLatLimit = 90.0 - POLE_LATITUDE_LIMIT_EPISLON;
        this->upperLeft.latitude = MathUtils_clamp(this->upperLeft.latitude, minLatLimit, maxLatLimit);
        this->upperRight.latitude = MathUtils_clamp(this->upperRight.latitude, minLatLimit, maxLatLimit);
        this->lowerRight.latitude = MathUtils_clamp(this->lowerRight.latitude, minLatLimit, maxLatLimit);
        this->lowerLeft.latitude = MathUtils_clamp(this->lowerLeft.latitude, minLatLimit, maxLatLimit);
    }
    if (!isnan(gsdHint)) {
        this->gsd = gsdHint;
    } else {
        atakmap::core::GeoPoint ul(this->upperLeft);
        atakmap::core::GeoPoint ur(this->upperRight);
        atakmap::core::GeoPoint lr(this->lowerRight);
        atakmap::core::GeoPoint ll(this->lowerLeft);
        this->gsd = atakmap::raster::DatasetDescriptor::computeGSD(static_cast<unsigned long>(trw), static_cast<unsigned long>(trh), ul, ur, lr, ll);
    }
    return code;
}

TAKErr GLQuadTileNode2::NodeCore::create(NodeCore **value, TAK::Engine::Core::RenderContext *context, const ImageInfo *info,
                                         TileReaderFactory2Options &readerOpts, const Options &opts,
                                         bool failOnReaderFailedInit, const Initializer &init)
{
    TAKErr code(TE_Ok);

    ::TileReader::TileReader2Ptr reader(nullptr, nullptr);
    DatasetProjection2Ptr imprecise(nullptr, nullptr);
    DatasetProjection2Ptr precise(nullptr, nullptr);

    code = init.init(reader, imprecise, precise, info, readerOpts);
    if (code != TE_Ok && failOnReaderFailedInit)
        return code;

    if (reader == nullptr || imprecise == nullptr)
        // These are required so error out
        return TE_Err;

    auto *core = new NodeCore(context, info->type, init, reader, imprecise, precise, info->srid, opts);
    code = core->initCore(info->maxGsd);
    if (code != TE_Ok)
        delete core;
    else
        *value = core;

    return code;
}

/**************************************************************************/
// VertexResolver and DefaultVertexResolver

GLQuadTileNode2::VertexResolver::~VertexResolver() {}

GLQuadTileNode2::DefaultVertexResolver::DefaultVertexResolver() : scratch_img_(0.0, 0.0), node_(nullptr), populate_terrain_(false)
{
    this->populate_terrain_ = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.defaultvertexresolver.populate-terrain", 0) != 0;
}

GLQuadTileNode2::DefaultVertexResolver::~DefaultVertexResolver() {}

void GLQuadTileNode2::DefaultVertexResolver::beginDraw(const Renderer::Core::GLMapView2 &view) {}

void GLQuadTileNode2::DefaultVertexResolver::endDraw(const Renderer::Core::GLMapView2 &view) {}

void GLQuadTileNode2::DefaultVertexResolver::beginNode(GLQuadTileNode2 *node)
{
    this->node_ = node;
}

void GLQuadTileNode2::DefaultVertexResolver::endNode(GLQuadTileNode2 *node)
{
    if (this->node_ != node)
        // throw new IllegalStateException();
        Logger_log(LogLevel::TELL_Warning, "Illegal state - vertexresolver::endNode not consistent with beginNode");
    this->node_ = nullptr;
}

Util::TAKErr GLQuadTileNode2::DefaultVertexResolver::project(GridVertex *vert, const Renderer::Core::GLMapView2 &view, int64_t imgSrcX,
                                                             int64_t imgSrcY)
{
    this->scratch_img_.x = static_cast<double>(imgSrcX);
    this->scratch_img_.y = static_cast<double>(imgSrcY);
    return this->node_->imageToGround(&vert->value, nullptr, this->scratch_img_);
}

void GLQuadTileNode2::DefaultVertexResolver::release() {}
void GLQuadTileNode2::DefaultVertexResolver::nodeDestroying(GLQuadTileNode2 *node) {}

/**********************************************************************/
// Precise Vertex Resolver

GLQuadTileNode2::PreciseVertexResolver::PreciseVertexResolver(GLQuadTileNode2 *owner)
    : owner(owner),
      syncOn(Thread::TEMT_Recursive),
      cv(),
      thread(nullptr, nullptr),
      activeID(),
      threadCounter(0),
      queue(),
      query(),
      pending(pointSort),
      unresolvable(pointSort),
      precise(pointSort),
      currentNode(nullptr),
      currentRequest(pointSort),
      requestNodes(),
      scratchGeo(),
      scratchImg(0.0, 0.0),
      needsResolved(0),
      requested(0),
      numNodesPending(0),
      initialized(false),
      queuedRunnablesMutex(),
      queuedRunnables()
{}

TAKErr GLQuadTileNode2::PreciseVertexResolver::create(std::unique_ptr<VertexResolver> &value, GLQuadTileNode2 *owner)
{
    TAKErr code(TE_Ok);

    std::unique_ptr<PreciseVertexResolver> pvr(new PreciseVertexResolver(owner));

    // fill the four corners -- we can assume that these are precisely
    // defined for the dataset projection
    const int64_t minx = 0;
    const int64_t miny = 0;
    int64_t maxx, maxy;
    code = owner->core_->tileReader->getWidth(&maxx);
    TE_CHECKRETURN_CODE(code);
    code = owner->core_->tileReader->getHeight(&maxy);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 ul, ur, lr, ll;
    code = owner->core_->imprecise->imageToGround(&ul, Math::Point2<double>(static_cast<double>(minx), static_cast<double>(miny)));
    TE_CHECKRETURN_CODE(code);
    pvr->precise[Math::Point2<int64_t>(minx, miny)] = ul;

    code = owner->core_->imprecise->imageToGround(&ur, Math::Point2<double>(static_cast<double>(maxx), static_cast<double>(miny)));
    TE_CHECKRETURN_CODE(code);
    pvr->precise[Math::Point2<int64_t>(maxx, miny)] = ur;

    code = owner->core_->imprecise->imageToGround(&lr, Math::Point2<double>(static_cast<double>(maxx), static_cast<double>(maxy)));
    TE_CHECKRETURN_CODE(code);
    pvr->precise[Math::Point2<int64_t>(maxx, maxy)] = lr;

    code = owner->core_->imprecise->imageToGround(&ll, Math::Point2<double>(static_cast<double>(minx), static_cast<double>(maxy)));
    TE_CHECKRETURN_CODE(code);
    pvr->precise[Math::Point2<int64_t>(minx, maxy)] = ll;

    value = std::move(pvr);
    return TE_Ok;
}

GLQuadTileNode2::PreciseVertexResolver::~PreciseVertexResolver()
{
    release();
}

void GLQuadTileNode2::PreciseVertexResolver::beginDraw(const Renderer::Core::GLMapView2 &view)
{
    this->currentRequest.clear();
    this->numNodesPending = 0;
}

void GLQuadTileNode2::PreciseVertexResolver::endDraw(const Renderer::Core::GLMapView2 &view)
{
    if (!view.targeting && this->numNodesPending == 0) {
        // all node grids are full resolved, go ahead and expand the
        // grids
        std::size_t minGridWidth = 16;
        std::set<GLQuadTileNode2 *>::iterator iter;
        for (iter = this->requestNodes.begin(); iter != this->requestNodes.end(); ++iter) {
            GLQuadTileNode2 *node = *iter;
            if (node->gl_tex_grid_width_ < minGridWidth)
                minGridWidth = node->gl_tex_grid_width_;
        }

        for (iter = this->requestNodes.begin(); iter != this->requestNodes.end(); ++iter) {
            GLQuadTileNode2 *node = *iter;
            if (node->gl_tex_grid_width_ > minGridWidth)
                continue;

            const std::size_t targetGridWidth = static_cast<std::size_t>((uint64_t)1u << (4u - std::min(node->level_ << 1u, (size_t)4)));
            if (node->gl_tex_grid_width_ < targetGridWidth) {
                queueGLCallback(new RenderRunnableOpaque(this, node, static_cast<int>(targetGridWidth)));
            }
        }
    }
    this->requestNodes.clear();

    {
        Thread::Lock lock(this->syncOn);

        for (auto iter = this->queue.begin(); iter != this->queue.end();) {
            if (this->currentRequest.find(*iter) == this->currentRequest.end()) {
                iter = this->queue.erase(iter);
            } else {
                iter++;
            }
        }
        SortedPointSet tmp(pointSort);
        std::set_intersection(this->pending.begin(), this->pending.end(), this->currentRequest.begin(), this->currentRequest.end(),
                              std::inserter(tmp, tmp.begin()), pointSort);
        this->pending = tmp;
        this->currentRequest.clear();
    }
}

void GLQuadTileNode2::PreciseVertexResolver::beginNode(GLQuadTileNode2 *node)
{
    DefaultVertexResolver::beginNode(node);

    this->currentNode = node;
    this->needsResolved = 0;
    this->requested = 0;
    this->requestNodes.insert(node);
}

void GLQuadTileNode2::PreciseVertexResolver::endNode(GLQuadTileNode2 *node)
{
    this->currentNode = nullptr;
    // update our pending count if the node needs one or more vertices
    // resolved
    if (this->requested > 0 && this->needsResolved > 0)
        this->numNodesPending++;

    DefaultVertexResolver::endNode(node);
}

Util::TAKErr GLQuadTileNode2::PreciseVertexResolver::project(GridVertex *vert, const Renderer::Core::GLMapView2 &view, int64_t imgSrcX,
                                                             int64_t imgSrcY)
{
    GeoPoint2 geo;
    bool geoValid = false;

    if (!view.targeting) {
        this->requested++;

        this->query.x = imgSrcX;
        this->query.y = imgSrcY;

        {
            Thread::Lock lock(this->syncOn);

            auto miter = this->precise.find(this->query);
            if (miter != this->precise.end()) {
                geo = miter->second;
                geoValid = true;
            }

            if (!geoValid && !this->initialized) {
                TAKErr code(TE_Ok);
                do {
                    if (owner->core_->textureCache != nullptr) {
                        GLTextureCache2::EntryPtr entry(nullptr, nullptr);
                        std::string key;
                        code = getCacheKey(&key);
                        TE_CHECKBREAK_CODE(code);
                        code = owner->core_->textureCache->remove(entry, key.c_str());
                        if (code == TE_Ok) {
                            void *data = entry->opaque.get();
                            MemBuffer2 buf(static_cast<const uint8_t *>(data), entry->opaqueSize);
                            code = deserialize(buf, this->precise, this->unresolvable);
                            TE_CHECKBREAK_CODE(code);

                            this->queue.remove_if(PointMapPredicate(this->precise));
                            this->queue.remove_if(PointSetPredicate(this->unresolvable));
                            for (auto kvp : this->precise)
                                this->pending.erase(kvp.first);
                            for (auto kvp : this->unresolvable)
                                this->pending.erase(kvp);
                        }
                        code = TE_Ok;
                        miter = this->precise.find(this->query);
                        if (miter != this->precise.end()) {
                            geo = miter->second;
                            geoValid = true;
                        }
                    }
                } while (false);
                this->initialized = true;
                TE_CHECKRETURN_CODE(code);
            }

            if (!geoValid) {
                TAKErr code = this->resolve();
                TE_CHECKRETURN_CODE(code);

                // try to obtain the next and previous points, if
                // present we can interpolate this point

                if (this->currentNode != nullptr) {
                    const int64_t texGridIncrementX = (this->currentNode->tile_src_width_ / this->currentNode->gl_tex_grid_width_);
                    const int64_t texGridIncrementY = (this->currentNode->tile_src_height_ / this->currentNode->gl_tex_grid_height_);

                    const int64_t prevImgSrcX = imgSrcX - texGridIncrementX;
                    const int64_t prevImgSrcY = imgSrcY - texGridIncrementY;
                    const int64_t nextImgSrcX = imgSrcX + texGridIncrementX;
                    const int64_t nextImgSrcY = imgSrcY + texGridIncrementY;

                    SortedPointMap::iterator interpolate0;
                    SortedPointMap::iterator interpolate1;

                    // check horizontal interpolation
                    this->query.y = imgSrcY;

                    this->query.x = prevImgSrcX;
                    interpolate0 = this->precise.find(this->query);
                    this->query.x = nextImgSrcX;
                    interpolate1 = this->precise.find(this->query);
                    if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                        geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                        (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                        geoValid = true;
                    }

                    // check vertical interpolation
                    if (!geoValid) {
                        this->query.x = imgSrcX;

                        this->query.y = prevImgSrcY;
                        interpolate0 = this->precise.find(this->query);
                        this->query.y = nextImgSrcY;
                        interpolate1 = this->precise.find(this->query);
                        if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                            geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                            (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                            geoValid = true;
                        }
                    }

                    // check cross interpolation
                    if (!geoValid) {
                        // XXX - just doing this quickly along one
                        // diagonal, but should really be doing a
                        // bilinear interpolation
                        this->query.x = prevImgSrcX;
                        this->query.y = prevImgSrcY;
                        interpolate0 = this->precise.find(this->query);
                        this->query.x = nextImgSrcX;
                        this->query.y = nextImgSrcY;
                        interpolate1 = this->precise.find(this->query);
                        if (interpolate0 != this->precise.end() && interpolate1 != this->precise.end()) {
                            geo = GeoPoint2((interpolate0->second.latitude + interpolate1->second.latitude) / 2.0,
                                            (interpolate0->second.longitude + interpolate1->second.longitude) / 2.0);
                            geoValid = true;
                        }
                    }
                }
            }
        }
    }
    if (!geoValid) {
        return DefaultVertexResolver::project(vert, view, imgSrcX, imgSrcY);
    } else {
        vert->value = geo;
        vert->resolved = true;
        return TE_Ok;
    }
}

void GLQuadTileNode2::PreciseVertexResolver::release()
{
    {
        Thread::Lock lock(this->syncOn);
        // Clear the thread id as a signal to our thread to exit
        this->activeID = Thread::ThreadID();
        this->queue.clear();
        this->pending.clear();
        cv.signal(lock);
    }
    // Join our thread
    thread.reset();

    // Veto queued events
    {
        Thread::Lock lock(this->queuedRunnablesMutex);
        for (auto iter = this->queuedRunnables.begin(); iter != this->queuedRunnables.end(); ++iter)
            (*iter)->owner = nullptr;
        this->queuedRunnables.clear();
    }

    if (owner->core_->textureCache != nullptr && (this->precise.size() > 4 || this->unresolvable.size() > 0)) {
        TAKErr code(TE_Ok);
        do {
            // XXX - need to restrict how many vertices we are storing
            // in the cache
            MemBuffer2Ptr buf(nullptr, nullptr);
            code = serialize(buf, this->precise, this->unresolvable);
            TE_CHECKBREAK_CODE(code);
            std::string key;
            code = this->getCacheKey(&key);
            TE_CHECKBREAK_CODE(code);
            GLTextureCache2::EntryPtr entry(
                new GLTextureCache2::Entry(
                    GLTexture2Ptr(new GLTexture2(1, buf->size(), GL_LUMINANCE, GL_UNSIGNED_BYTE), Memory_deleter_const<GLTexture2>),
                    std::unique_ptr<float, void (*)(const float *)>(nullptr, nullptr),
                    std::unique_ptr<float, void (*)(const float *)>(nullptr, nullptr), 0, 0,
                    GLTextureCache2::Entry::OpaquePtr(buf.get(), Memory_leaker_const<void>)),
                Memory_deleter_const<GLTextureCache2::Entry>);

            code = owner->core_->textureCache->put(key.c_str(), std::move(entry));
            TE_CHECKBREAK_CODE(code);
        } while (false);

        TE_CHECKRETURN(code);
    }

    this->precise.clear();
    this->unresolvable.clear();
    this->initialized = false;
}

void GLQuadTileNode2::PreciseVertexResolver::nodeDestroying(GLQuadTileNode2 *node)
{
    Thread::Lock lock(this->queuedRunnablesMutex);
    for (auto iter = this->queuedRunnables.begin(); iter != this->queuedRunnables.end();) {
        if ((*iter)->owner->owner == node) {
            (*iter)->owner = nullptr;
            iter = this->queuedRunnables.erase(iter);
        } else {
            ++iter;
        }
    }
}

TAKErr GLQuadTileNode2::PreciseVertexResolver::preciseImageToGround(GeoPoint2 *ground, const Math::Point2<double> &image)
{
    bool isPrecise = false;
    if (owner->imageToGround(ground, &isPrecise, image) != TE_Ok || !isPrecise)
        return TE_Err;

    return TE_Ok;
}

TAKErr GLQuadTileNode2::PreciseVertexResolver::resolve()
{
    Thread::Lock lock(this->syncOn);

    if (this->unresolvable.find(this->query) != this->unresolvable.end())
        return TE_Ok;
    this->needsResolved++;
    Math::Point2<int64_t> p(this->query);
    this->currentRequest.insert(p);
    if (this->pending.find(p) != this->pending.end()) {
        cv.signal(lock);
        return TE_Ok;
    }
    this->queue.push_back(p);
    this->pending.insert(p);

    if (!this->thread) {
        std::stringstream sstr("Precise Vertex Resolver-");
        sstr << threadCounter++;
        std::string s = sstr.str();

        Thread::ThreadCreateParams param;
        param.name = s.c_str();
        param.priority = Thread::ThreadPriority::TETP_Normal;

        TAKErr code = Thread_start(this->thread, threadRun, this, param);
        activeID = thread->getID();
        TE_CHECKRETURN_CODE(code)
    }
    cv.signal(lock);
    return TE_Ok;
}

void GLQuadTileNode2::PreciseVertexResolver::renderThreadRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<RenderRunnableOpaque> runnableInfo(static_cast<RenderRunnableOpaque *>(opaque));
    if (runnableInfo->owner)
        runnableInfo->owner->renderThreadRunnableImpl(runnableInfo.get());
}

void GLQuadTileNode2::PreciseVertexResolver::renderThreadRunnableImpl(RenderRunnableOpaque *runnableInfo)
{
    switch (runnableInfo->eventType) {
        case RenderRunnableOpaque::END_DRAW:
            if (runnableInfo->node->gl_tex_grid_width_ < static_cast<std::size_t>(runnableInfo->targetGridWidth)) {
                runnableInfo->node->expandTexGrid();
                runnableInfo->owner->owner->vertices_invalid_ = true;
            }
            break;
        case RenderRunnableOpaque::RUN_RESULT:
            runnableInfo->owner->owner->vertices_invalid_ = true;
            break;
    }
    {
        Thread::Lock lock(this->queuedRunnablesMutex);
        for (auto iter = this->queuedRunnables.begin(); iter != this->queuedRunnables.end(); ++iter) {
            if (*iter == runnableInfo) {
                this->queuedRunnables.erase(iter);
                break;
            }
        }
    }
}

void GLQuadTileNode2::PreciseVertexResolver::queueGLCallback(RenderRunnableOpaque *runnableInfo)
{
    Thread::Lock lock(this->queuedRunnablesMutex);
    this->queuedRunnables.push_back(runnableInfo);
    this->owner->core_->context->queueEvent(renderThreadRunnable, std::unique_ptr<void, void(*)(const void *)>(runnableInfo, Memory_leaker_const<void>));
}

void *GLQuadTileNode2::PreciseVertexResolver::threadRun(void *opaque)
{
    auto *p = (PreciseVertexResolver *)opaque;
    p->threadRunImpl();
    return nullptr;
}

void GLQuadTileNode2::PreciseVertexResolver::threadRunImpl()
{
    Math::Point2<int64_t> processL;
    bool havePoint = false;
    Math::Point2<double> processD(0, 0);
    GeoPoint2 result;
    bool haveResult = false;
    while (true) {
        {
            Thread::Lock lock(this->syncOn);

            if (haveResult)
                this->precise.insert(SortedPointMap::value_type(processL, result));
            else if (havePoint)
                this->unresolvable.insert(processL);

            if (havePoint) {
                this->pending.erase(processL);

                // Convert to renderthread
                queueGLCallback(new RenderRunnableOpaque(this));
            }

            havePoint = haveResult = false;
            if (this->activeID != Thread::Thread_currentThreadID())
                break;
            if (this->queue.size() < 1) {
                cv.wait(lock);
                continue;
            }
            processL = this->queue.front();
            this->queue.pop_front();
            havePoint = true;
        }

        processD.x = static_cast<double>(processL.x);
        processD.y = static_cast<double>(processL.y);
        TAKErr code = this->preciseImageToGround(&result, processD);
        if (code == TE_Ok && !std::isnan(result.latitude) && !std::isnan(result.longitude))
            haveResult = true;
    }

    {
        Thread::Lock lock(this->syncOn);

        if (this->activeID == Thread::Thread_currentThreadID()) {
            this->activeID = Thread::ThreadID();
        }
    }
}

TAKErr GLQuadTileNode2::PreciseVertexResolver::getCacheKey(std::string *value)
{
    TAKErr code(TE_Ok);
    Port::String s;

    code = owner->getUri(s);
    TE_CHECKRETURN_CODE(code);
    std::string ret = s.get();
    ret += ",coords";
    *value = ret;
    return code;
}

TAKErr GLQuadTileNode2::PreciseVertexResolver::serialize(MemBuffer2Ptr &buf, const SortedPointMap &precise,
                                                         const SortedPointSet &unresolvable)
{
    TAKErr code(TE_Ok);
    buf = MemBuffer2Ptr(new MemBuffer2((4 + (32 * precise.size())) + (4 + (16 * unresolvable.size()))), Memory_deleter_const<MemBuffer2>);

    code = buf->put(static_cast<uint32_t>(precise.size()));
    TE_CHECKRETURN_CODE(code);
    for (auto e : precise) {
        // 2 int64 + 2 double
        code = buf->put(e.first.x);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.first.y);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.second.latitude);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(e.second.longitude);
        TE_CHECKRETURN_CODE(code);
    }

    code = buf->put(static_cast<uint32_t>(unresolvable.size()));
    TE_CHECKRETURN_CODE(code);
    for (auto p : unresolvable) {
        // 2 int64
        code = buf->put(p.x);
        TE_CHECKRETURN_CODE(code);
        code = buf->put(p.y);
        TE_CHECKRETURN_CODE(code);
    }

    buf->flip();
    return code;
}

TAKErr GLQuadTileNode2::PreciseVertexResolver::deserialize(MemBuffer2 &buf, SortedPointMap &precise, SortedPointSet &unresolvable)
{
    TAKErr code(TE_Ok);
    uint32_t size;

    code = buf.get(&size);
    TE_CHECKRETURN_CODE(code);
    for (uint32_t i = 0; i < size; i++) {
        Math::Point2<int64_t> point;
        GeoPoint2 geo;
        code = buf.get(&point.x);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&point.y);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&geo.latitude);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&geo.longitude);
        TE_CHECKRETURN_CODE(code);
        precise.insert(SortedPointMap::value_type(point, geo));
    }

    code = buf.get(&size);
    TE_CHECKRETURN_CODE(code);
    for (uint32_t i = 0; i < size; i++) {
        Math::Point2<int64_t> point;
        code = buf.get(&point.x);
        TE_CHECKRETURN_CODE(code);
        code = buf.get(&point.y);
        TE_CHECKRETURN_CODE(code);
        unresolvable.insert(point);
    }
    return code;
}

GLQuadTileNode2::PreciseVertexResolver::RenderRunnableOpaque::RenderRunnableOpaque(GLQuadTileNode2::PreciseVertexResolver *owner)
    : owner(owner), eventType(RUN_RESULT), node(nullptr), targetGridWidth(0)
{}

GLQuadTileNode2::PreciseVertexResolver::RenderRunnableOpaque::RenderRunnableOpaque(GLQuadTileNode2::PreciseVertexResolver *owner,
                                                                                   GLQuadTileNode2 *node, int targetGridWidth)
    : owner(owner), eventType(END_DRAW), node(node), targetGridWidth(targetGridWidth)
{}
