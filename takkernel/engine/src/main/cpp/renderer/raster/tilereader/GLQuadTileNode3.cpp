#ifdef MSVC
#include "renderer/raster/tilereader/GLQuadTileNode3.h"

#include <sstream>

#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLMatrix.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "renderer/GLText2.h"
#include "renderer/GLWireframe.h"
#include "renderer/core/GLGlobe.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/raster/tilereader/NodeCore.h"
#include "renderer/raster/tilereader/PreciseVertexResolver.h"
#include "thread/RWMutex.h"
#include "util/ConfigOptions.h"
#include "util/IO2.h"
#include "util/MathUtils.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::TileReader;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Core::Controls;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define STATE_RESOLVING     0x01
#define STATE_UNRESOLVED    0x02
#define STATE_UNRESOLVABLE  0x04
#define STATE_RESOLVED      0x08
#define STATE_SUSPENDED     0x10

#define MIPMAP_ENABLED 0

namespace
{
    bool offscreenFboFailed = false;
    int transferBufferAllocs = 0;
    int buffersTransferred = 0;


    bool GLQuadTileNode3_init() NOTHROWS
    {
        // XXX - not currently implemented
        //atakmap::raster::gdal::GdalTileReader::setPaletteRgbaFormat(atakmap::raster::gdal::GdalTileReader::RGBA);
        return true;
    }

    std::shared_ptr<NodeCore> NodeCore_create(RenderContext &ctx, const ImageInfo &info, const TileReaderFactory2Options &readerOpts, const GLQuadTileNode2::Options &opts, const GLQuadTileNode2::Initializer &init) NOTHROWS
    {
        std::shared_ptr<NodeCore> core;
        do {
            
            NodeCorePtr corePtr(nullptr, nullptr);
            if (TAK::Engine::Renderer::Raster::TileReader::NodeCore_create(corePtr, ctx, info, readerOpts, opts, false, init) == TE_Ok)
                core = std::move(corePtr);
        } while (false);
        return core;
    }

    bool shouldDraw(const GLGlobeBase& view, const double minLng, const double minLat, const double maxLng, const double maxLat) NOTHROWS
    {

        return Rectangle2_intersects(minLng, minLat, maxLng, maxLat, view.renderPass->westBound, view.renderPass->southBound, view.renderPass->eastBound, view.renderPass->northBound, false) ||
            (minLng < -180.0 && Rectangle2_intersects(minLng + 360.0, minLat, std::min(maxLng + 360.0, 180.0), maxLat, view.renderPass->westBound, view.renderPass->southBound, view.renderPass->eastBound, view.renderPass->northBound, false)) ||
            (maxLng > 180.0 && Rectangle2_intersects(std::max(minLng - 360.0, -180.0), minLat, maxLng - 360.0, maxLat, view.renderPass->westBound, view.renderPass->southBound, view.renderPass->eastBound, view.renderPass->northBound, false));
    }
}

struct GLQuadTileNode3::IOCallbackOpaque
{
    enum UpdateType
    {
        CB_Error,
        CB_Canceled,
        CB_Completed,
        CB_Update
    };

    const UpdateType type;
    GLQuadTileNode3 *owner;
    const int reqId;

    // Used by updates only
    struct {
        std::unique_ptr<void, void(*)(const void*)> data = std::unique_ptr<void, void(*)(const void*)>(nullptr, nullptr);
        std::size_t width;
        std::size_t height;
        Bitmap2::Format format;
    } bitmap;

    IOCallbackOpaque(UpdateType type, GLQuadTileNode3 &owner, int reqId) NOTHROWS
        : type(type), owner(&owner), reqId(reqId)
    {}

    IOCallbackOpaque(GLQuadTileNode3 &owner, int reqId, const Bitmap2 &data) NOTHROWS
        : type(CB_Update), owner(&owner), reqId(reqId)
    {
        bitmap.width = data.getWidth();
        bitmap.height = data.getHeight();
        bitmap.format = data.getFormat();

        do {
            std::size_t pixelSize;
            if (Bitmap2_formatPixelSize(&pixelSize, bitmap.format) != TE_Ok)
                break;

            // try to obtain a block from the pool; if that fails or the block
            // is too large, heap allocate
#if 0
            if ((pixelSize*bitmap.width*bitmap.height) > BITMAP_POOL_BLOCK_SIZE ||
                textureDataPool().allocate(bitmap.data, false) != TE_Ok)
#endif
            {

                const std::size_t dataSize = (pixelSize * bitmap.width * bitmap.height);
                bitmap.data = std::unique_ptr<void, void(*)(const void*)>(new(std::nothrow) uint8_t[dataSize], Memory_void_array_deleter_const<uint8_t>);
                if (!bitmap.data)
                    break;
            }

            // copy the bitmap
            Bitmap2::DataPtr pixels(static_cast<uint8_t*>(bitmap.data.get()), Memory_leaker_const<uint8_t>);
            Bitmap2 bmp(std::move(pixels), bitmap.width, bitmap.height, bitmap.format);
            bmp.setRegion(data, 0, 0);
        } while (false);
    }
};

GLQuadTileNode3::GLQuadTileNode3(RenderContext &ctx, const ImageInfo &info, const TileReaderFactory2Options &readerOpts, const GLQuadTileNode2::Options &opts, const GLQuadTileNode2::Initializer &init) NOTHROWS :
    GLQuadTileNode3(nullptr, 0u, ::NodeCore_create(ctx, info, readerOpts, opts, init))
{}
GLQuadTileNode3::GLQuadTileNode3(GLQuadTileNode3 *parent, const std::size_t idx) NOTHROWS :
    GLQuadTileNode3(parent, idx, parent ? parent->core : std::shared_ptr<NodeCore>())
{}
GLQuadTileNode3::GLQuadTileNode3(GLQuadTileNode3 *parent_, const std::size_t idx, const std::shared_ptr<NodeCore> &core_) NOTHROWS :
    parent(parent_),
    core(core_),
    root(parent_ ? parent_->root : *this),
    textureCoordsValid(false),
    currentRequest(0),
    state(State::UNRESOLVED),
    receivedUpdate(false),
    tileIndex(SIZE_MAX, SIZE_MAX, SIZE_MAX),
    touched(false),
    tileVersion(-1LL),
    derivedUnresolvableData(false),
    lastTouch(-1LL),
    readRequestStart(0LL),
    readRequestElapsed(0LL),
    readRequestComplete(0LL),
    texture(nullptr, nullptr),
    debugDrawIndices(nullptr, nullptr),
    glTexGridWidth(1u),
    glTexGridHeight(1u)
{
    do {
        if (!core)
            break;

        TE_CHECKBREAK_CODE(core->initResult);

        // XXX - would really like to make this statically initializable
        if (!parent && !core->vertexResolver) {
            if (core->precise)
                core->vertexResolver = VertexResolverPtr(new PreciseVertexResolver(this->root), Memory_deleter_const<VertexResolver, PreciseVertexResolver>);
            else
                core->vertexResolver = VertexResolverPtr(new VertexResolver(*core->imprecise), Memory_deleter_const<VertexResolver>);
        }

        // super

        halfTileWidth = (core->nominalTileWidth / 2u);
        halfTileHeight = (core->nominalTileHeight / 2u);

        if (parent) {
            set((parent->tileIndex.x * 2) + (idx % 2), (parent->tileIndex.y * 2)
                + (idx / 2), parent->tileIndex.z - 1);
        } else {
            // XXX - BUG: should be specifiying maxLevels-1 but then no data is
            // rendered????

            set(0, 0, core->numResolutionLevels);
        }
    } while (false);
}
GLQuadTileNode3::~GLQuadTileNode3()
{
    if (&this->root == this && core) {
        // destruct the vertex resolver
        core->vertexResolver.reset();
    }
}

void GLQuadTileNode3::setColor(const int color) NOTHROWS
{
    core->color = color;
    core->colorR = ((color>>16)&0xFF)/255.f;
    core->colorG = ((color>>8)&0xFF)/255.f;
    core->colorB = (color&0xFF)/255.f;
    core->colorA = ((color>>24)&0xFF)/255.f;
}
DatasetProjection2 &GLQuadTileNode3::getDatasetProjection() const NOTHROWS
{
    return *core->imprecise;
}
TileReader2 &GLQuadTileNode3::getReader() const NOTHROWS
{
    return *core->tileReader;
}
TAKErr GLQuadTileNode3::computeBounds(TAK::Engine::Feature::Envelope2 &value, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH) const NOTHROWS
{
    TAKErr code(TE_Ok);
    if(srcX >= (int64_t)core->info.width || srcY >= (int64_t)core->info.height)
        return TE_InvalidArg;
    if(srcW < 1 || srcH < 1)
        return TE_InvalidArg;

    Point2<double> scratchP;
    GeoPoint2 scratchG;

    TAK::Engine::Feature::Envelope2 mbb(180.0, 90.0, 0.0, -180.0, -90.0, 0.0);

    scratchP.x = (double)srcX;
    scratchP.y = (double)srcY;
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < mbb.minY) mbb.minY = scratchG.latitude;
    if(scratchG.latitude > mbb.maxY) mbb.maxY = scratchG.latitude;
    if(scratchG.longitude < mbb.minX) mbb.minX = scratchG.longitude;
    if(scratchG.longitude > mbb.maxX) mbb.maxX = scratchG.longitude;

    scratchP.x = (double)(srcX+srcW);
    scratchP.y = (double)srcY;
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < mbb.minY) mbb.minY = scratchG.latitude;
    if(scratchG.latitude > mbb.maxY) mbb.maxY = scratchG.latitude;
    if(scratchG.longitude < mbb.minX) mbb.minX = scratchG.longitude;
    if(scratchG.longitude > mbb.maxX) mbb.maxX = scratchG.longitude;

    scratchP.x = (double)(srcX+srcW);
    scratchP.y = (double)(srcY+srcH);
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < mbb.minY) mbb.minY = scratchG.latitude;
    if(scratchG.latitude > mbb.maxY) mbb.maxY = scratchG.latitude;
    if(scratchG.longitude < mbb.minX) mbb.minX = scratchG.longitude;
    if(scratchG.longitude > mbb.maxX) mbb.maxX = scratchG.longitude;

    scratchP.x = (double)srcX;
    scratchP.y = (double)(srcY+srcH);
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < mbb.minY) mbb.minY = scratchG.latitude;
    if(scratchG.latitude > mbb.maxY) mbb.maxY = scratchG.latitude;
    if(scratchG.longitude < mbb.minX) mbb.minX = scratchG.longitude;
    if(scratchG.longitude > mbb.maxX) mbb.maxX = scratchG.longitude;

    value = mbb;
    return code;
}
TAKErr GLQuadTileNode3::set(const std::size_t tileColumn, const std::size_t tileRow, const std::size_t level) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (tileIndex.x == tileColumn && tileIndex.y == tileRow && tileIndex.z == level)
        return TE_Ok;
#if 0
    if (GLQuadTileNode3.DEBUG)
        Log.d(TAG, toString(false) + " set(tileColumn=" + tileColumn + ",tileRow=" + tileRow
                + ",level=" + level + ")");
#endif
    if (this->tileIndex.x != tileColumn || this->tileIndex.y != tileRow || this->tileIndex.z != level) {
        for(std::size_t i = 0; i < 4; i++)
            if (this->children[i])
                this->children[i]->set((tileColumn * 2) + (i % 2), (tileRow * 2) + (i / 2),
                        level - 1);
    }

    if (this->currentRequest) {
        this->currentRequest->cancel();
#if 0
        if(core->cacheControl != null)
            core->cacheControl.abort(this->tileIndex.z, (int)this->tileIndex.x, (int)this->tileIndex.y);
#endif
        this->readRequests.remove(this->currentRequest);
        this->currentRequest.reset();
    }

    this->releaseTexture();

    this->state = State::UNRESOLVED;
    this->receivedUpdate = false;

    code = core->tileReader->getTileSourceX(&this->tileSrcX, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = core->tileReader->getTileSourceY(&this->tileSrcY, level, tileRow);
    TE_CHECKRETURN_CODE(code);
    code = core->tileReader->getTileSourceWidth(&this->tileSrcWidth, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = core->tileReader->getTileSourceHeight(&this->tileSrcHeight, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    code = core->tileReader->getTileWidth(&this->tileWidth, level, tileColumn);
    TE_CHECKRETURN_CODE(code);
    code = core->tileReader->getTileHeight(&this->tileHeight, level, tileRow);
    TE_CHECKRETURN_CODE(code);

    this->textureCoordsValid = false;
    this->gridVertices.valid = false;

    this->tileIndex.x = (int)tileColumn;
    this->tileIndex.y = (int)tileRow;
    this->tileIndex.z = level;
    this->tileVersion = -1L;

    Bitmap2::Format tileFmt;
    code = this->core->tileReader->getFormat(&tileFmt);
    TE_CHECKRETURN_CODE(code);
    code = GLTexture2_getFormatAndDataType(&this->glTexFormat, &this->glTexType, tileFmt);
    TE_CHECKRETURN_CODE(code);


    if (this->glTexFormat != GL_RGBA
            && !(MathUtils_isPowerOf2(this->tileWidth) && MathUtils_isPowerOf2(this->tileHeight))) {
        this->glTexFormat = GL_RGBA;
        this->glTexType = GL_UNSIGNED_BYTE;
#if 0
    } else if (this->tileIndex.z >= core->tileReader->getMinCacheLevel()) {
        // if we are pulling from the cache, we want alpha since the tile
        // will be delivered as parts rather than as a whole
        if (this->glTexFormat == GL_LUMINANCE) {
            this->glTexFormat = GL_LUMINANCE_ALPHA;
            this->glTexType = GL_UNSIGNED_BYTE;
        } else if (this->glTexFormat == GL_RGB) {
            this->glTexFormat = GL_RGBA;
            // if(this->glTexType == GL_UNSIGNED_SHORT_5_6_5)
            // this->glTexType = GL_UNSIGNED_SHORT_5_5_5_1;
            this->glTexType = GL_UNSIGNED_BYTE;
        }
#endif
    }

    Point2<double> scratchP;
    GeoPoint2 scratchG;

    minLat = 90;
    maxLat = -90;
    minLng = 180;
    maxLng = -180;

    scratchP.x = (double)this->tileSrcX;
    scratchP.y = (double)this->tileSrcY;
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;

    scratchP.x = (double)(this->tileSrcX+this->tileSrcWidth);
    scratchP.y = (double)this->tileSrcY;
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;

    scratchP.x = (double)(this->tileSrcX+this->tileSrcWidth);
    scratchP.y = (double)(this->tileSrcY+this->tileSrcHeight);
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;

    scratchP.x = (double)this->tileSrcX;
    scratchP.y = (double)(this->tileSrcY+this->tileSrcHeight);
    code = core->imprecise->imageToGround(&scratchG, scratchP);
    TE_CHECKRETURN_CODE(code);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;

    const int minGridSize = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.minimum-grid-size", 1);
    const int maxGridSize = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.maximum-grid-size", 32);

    // XXX - needs to be based off of the full image to prevent seams
    //       between adjacent tiles which may have different local spatial
    //       resolutions
    const int subsX = MathUtils_clamp(MathUtils_nextPowerOf2((int)std::ceil((maxLat-minLat) / GLGlobe::getRecommendedGridSampleDistance())), minGridSize, maxGridSize);
    const int subsY = MathUtils_clamp(MathUtils_nextPowerOf2((int)std::ceil((maxLng-minLng) / GLGlobe::getRecommendedGridSampleDistance())), minGridSize, maxGridSize);

    // XXX - rendering issues if grid is not square...
    this->glTexGridWidth = std::max(subsX, subsY);
    this->glTexGridHeight = std::max(subsX, subsY);

    this->gridVertices.value.reserve((this->glTexGridWidth+1)*(this->glTexGridHeight+1));
    this->gridVertices.value.clear();
    for (std::size_t i = 0u; i < ((this->glTexGridWidth + 1) * (this->glTexGridHeight + 1)); i++)
        this->gridVertices.value.push_back(GridVertex());

    this->centroid = GeoPoint2();

    if(this->tileWidth <= halfTileWidth)
        this->tileSrcMidX = this->tileSrcX+(this->tileSrcWidth/2);
    else
        this->tileSrcMidX = (int64_t)(tileSrcX+(core->nominalTileWidth*std::pow(2, ((int)this->tileIndex.z-1))));
    if(this->tileHeight <= halfTileHeight)
        this->tileSrcMidY = this->tileSrcY+(this->tileSrcHeight/2);
    else
        this->tileSrcMidY = (int64_t)(tileSrcY+(core->nominalTileHeight*std::pow(2, ((int)this->tileIndex.z-1))));
    this->centroidProj = Point2<double>((double)tileSrcMidX, (double)tileSrcMidY, 0.0);
    if(!core->imprecise->imageToGround(&this->centroid, this->centroidProj))
        this->centroid = GeoPoint2((minLat+maxLat)/2.0, (minLng+maxLng)/2.0);
    this->centroidProj.x = 0.0;
    this->centroidProj.y = 0.0;

    this->centroidHemi2 = this->centroid;
    if(this->centroid.longitude < 0.0)
        this->centroidHemi2.longitude += 360.0;
    else
        this->centroidHemi2.longitude -= 360.0; // XXX - error in Java??? was +360
    this->centroidProjHemi2 = Point2<double>(0.0, 0.0, 0.0);

    // ul-ur-ll-lr
    childMbb[0].valid = computeBounds(childMbb[0].value, tileSrcX, tileSrcY, (tileWidth > halfTileWidth) ? tileSrcMidX-tileSrcX : tileSrcWidth, (tileHeight > halfTileHeight) ? tileSrcMidY-tileSrcY : tileSrcHeight) == TE_Ok;
    if(tileWidth > halfTileWidth)
        childMbb[1].valid = computeBounds(childMbb[1].value, tileSrcMidX, tileSrcY, tileSrcWidth-(tileSrcMidX-tileSrcX), (tileHeight > halfTileHeight) ? tileSrcMidY-tileSrcY : tileSrcHeight) == TE_Ok;
    else
        childMbb[1].valid = false;
    if(tileHeight > halfTileHeight)
        childMbb[2].valid = computeBounds(childMbb[2].value, tileSrcX, tileSrcMidY, (tileWidth > halfTileWidth) ? tileSrcMidX-tileSrcX : tileSrcWidth, tileSrcHeight-(tileSrcMidY-tileSrcY)) == TE_Ok;
    else
        childMbb[2].valid = false;
    if(tileWidth > halfTileWidth && tileHeight > halfTileHeight)
        childMbb[3].valid = computeBounds(childMbb[3].value, tileSrcMidX, tileSrcMidY, tileSrcWidth-(tileSrcMidX-tileSrcX), tileSrcHeight-(tileSrcMidY-tileSrcY)) == TE_Ok;
    else
        childMbb[3].valid = false;

    return code;
}
void GLQuadTileNode3::releaseTexture() NOTHROWS
{
    if(this->texture) {
        if(!core->isMultiResolution && core->textureCache) {
            std::ostringstream key;
            key << core->info.path.get() << "&x=" << tileIndex.x << "&y=" << tileIndex.y << "&z=" << tileIndex.z;
            GLTextureCache2::EntryPtr entry(
                new GLTextureCache2::Entry(std::move(this->texture)),
                Memory_deleter_const<GLTextureCache2::Entry>);
            //this->texture), (this->state == State::RESOLVED) ? GLQuadTileNode3.TEXTURE_CACHE_HINT_RESOLVED : 0, Long.valueOf(tileVersion)
            core->textureCache->put(key.str().c_str(), std::move(entry));
        } else {
            this->texture->release();
            this->texture.reset();
        }
        this->touched = false;
    }

    if(!texCoordsShared)
        core->resources->discardBuffer(textureCoordinates);
    this->textureCoordinates = GL_NONE;
    this->texCoordsShared = false;
    vertexCoordinates2 = core->resources->discardBuffer(vertexCoordinates2);
    if(!this->indicesShared)
        core->resources->discardBuffer(glTexCoordIndices2);
    this->glTexCoordIndices2 = GL_NONE;
    this->indicesShared = false;

    this->textureCoordsValid = false;
    this->gridVertices.valid = false;

    if(this->state != State::UNRESOLVABLE)
        this->state = State::UNRESOLVED;

    this->receivedUpdate = false;
    this->derivedUnresolvableData = false;
}
void GLQuadTileNode3::release() NOTHROWS
{
    this->abandon();
    if(this == &this->root) {
        core->suspended = false;
    } else {
        this->parent = nullptr;
    }

    this->touched = false;

    this->fadeTimer = 0L;

    if (this->currentRequest) {
        this->currentRequest->cancel();
        if(core->cacheControl)
            core->cacheControl->abort(this->tileIndex.z, this->tileIndex.x, this->tileIndex.y);
        this->currentRequest.reset();
    }
    for (auto rr : this->readRequests)
        rr->cancel();
    this->readRequests.clear();

    {
        Thread::Lock lock(queuedCallbacksMutex);
        for (IOCallbackOpaque *cbo : queuedCallbacks)
            cbo->owner = nullptr;
        queuedCallbacks.clear();
    }


    if (this->texture)
        this->releaseTexture();

    vertexCoordinates2 = core->resources->discardBuffer(this->vertexCoordinates2);
    if(!this->indicesShared)
        core->resources->discardBuffer(glTexCoordIndices2);
    this->glTexCoordIndices2 = GL_NONE;
    this->indicesShared = false;
    if(!this->texCoordsShared)
        core->resources->discardBuffer(textureCoordinates);
    this->textureCoordinates = GL_NONE;
    this->texCoordsShared = false;
    borrowTextureCoordinates = core->resources->discardBuffer(borrowTextureCoordinates);
    this->borrowingFrom = nullptr;

    this->tileIndex.x = -1;
    this->tileIndex.y = -1;
    this->tileIndex.z = -1;

    this->textureCoordsValid = false;
    this->gridVertices.valid = false;

    this->state = State::UNRESOLVED;
    this->receivedUpdate = false;

    if(this->debugDrawIndices) {
        this->debugDrawIndices.reset();
    }
}
void GLQuadTileNode3::start() NOTHROWS
{}
void GLQuadTileNode3::stop() NOTHROWS
{}
void GLQuadTileNode3::validateTexture() NOTHROWS
{
    if (!this->texture ||
            this->texture->getTexWidth() < this->tileWidth ||
            this->texture->getTexHeight() < this->tileHeight ||
            this->texture->getFormat() != this->glTexFormat ||
            this->texture->getType() != this->glTexType) {

        if (this->texture)
            this->texture->release();

        this->texture = GLTexture2Ptr(new GLTexture2(this->tileWidth, this->tileHeight, this->glTexFormat,
                this->glTexType), Memory_deleter_const<GLTexture2>);

        // mark all coords as invalid
        this->textureCoordsValid = false;
        this->gridVertices.valid = false;

        if(!texCoordsShared)
            core->resources->discardBuffer(this->textureCoordinates);
        this->textureCoordinates = GL_NONE;
        this->texCoordsShared = false;
        this->vertexCoordinates2 = core->resources->discardBuffer(this->vertexCoordinates2);
    }

    this->validateTexVerts();
}
void GLQuadTileNode3::validateTexCoordIndices() NOTHROWS
{
    const std::size_t vertCount = GLTexture2_getNumQuadMeshVertices(this->glTexGridWidth,
            this->glTexGridHeight);
    const std::size_t idxCount = GLTexture2_getNumQuadMeshIndices(this->glTexGridWidth,
            this->glTexGridHeight);
    if (vertCount != this->glTexGridVertCount ||
            idxCount != glTexGridIdxCount ||
            (this->glTexGridVertCount <= 4 && this->glTexCoordIndices2 != GL_NONE) ||
            (this->glTexGridVertCount > 4 && this->glTexCoordIndices2 == GL_NONE)) {

        this->glTexGridIdxCount = idxCount;
        this->glTexGridVertCount = vertCount;
        if (this->glTexGridVertCount > 4) {
            if(core->resources->isUniformGrid(this->glTexGridWidth, this->glTexGridHeight)) {
                this->glTexCoordIndices2 = core->resources->getUniformGriddedIndices(this->glTexGridWidth);
                indicesShared = true;
            } else {
                if (this->indicesShared || this->glTexCoordIndices2 == GL_NONE)
                    this->glTexCoordIndices2 = core->resources->genBuffer();
                core->resources->coordStreamBufferS.reset();
                GLTexture2_createQuadMeshIndexBuffer(
                        core->resources->coordStreamBufferS,
                        GL_UNSIGNED_SHORT,
                        this->glTexGridWidth,
                        this->glTexGridHeight);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this->glTexCoordIndices2);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, this->glTexGridIdxCount * 2, core->resources->coordStreamBuffer.get(), GL_STATIC_DRAW);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
                indicesShared = false;
            }
            //this->debugDrawIndices = GLWireFrame.deriveIndices(glTexCoordIndices.asShortBuffer(), GL_TRIANGLE_STRIP, glTexGridIdxCount, GL_UNSIGNED_SHORT);
        } else {
            if(!indicesShared)
                core->resources->discardBuffer(this->glTexCoordIndices2);
            this->glTexCoordIndices2 = GL_NONE;
            indicesShared = false;
#if 0
            this->debugDrawIndices = GLWireFrame.deriveIndices(GL_TRIANGLE_STRIP,
                    this->glTexGridVertCount, GL_UNSIGNED_SHORT);
#endif
        }
        this->gridVertices.valid = false;
    }
}
void GLQuadTileNode3::validateTexVerts() NOTHROWS
{
    if (!this->textureCoordsValid) {
        validateTexCoordIndices();

        const float x = ((float) this->tileWidth / (float) this->texture->getTexWidth());
        const float y = ((float) this->tileHeight / (float) this->texture->getTexHeight());

        // if the texture mesh is uniform, the cell count is a power of two
        // and the texture data fills the full texture, utilize one of the
        // shared texture coordinate meshes, otherwise allocate a per-node
        // texture coordinate buffer
        if(core->resources->isUniformGrid(glTexGridWidth, glTexGridHeight) &&
                (x == 1.f && y == 1.f)) {

            this->textureCoordinates = core->resources->getUniformGriddedTexCoords(glTexGridWidth);
            texCoordsShared = true;
        } else {
            if (texCoordsShared ||
                    this->textureCoordinates == GL_NONE) {

                this->textureCoordinates = core->resources->genBuffer();
            }

            core->resources->coordStreamBufferF.reset();
            GLTexture2_createQuadMeshTexCoords(
                    core->resources->coordStreamBufferF,
                    Point2<float>(0.0f, 0.0f),
                    Point2<float>(x, 0.0f),
                    Point2<float>(x, y),
                    Point2<float>(0.0f, y),
                    this->glTexGridWidth,
                    this->glTexGridHeight);
            glBindBuffer(GL_ARRAY_BUFFER, this->textureCoordinates);
            glBufferData(GL_ARRAY_BUFFER, this->glTexGridVertCount*2*4, core->resources->coordStreamBuffer.get(), GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
            texCoordsShared = false;
        }
    }
    this->textureCoordsValid = true;
}
void GLQuadTileNode3::validateVertexCoords(const GLGlobeBase &view) NOTHROWS
{
    if(!this->gridVertices.valid || this->gridVertices.srid != view.renderPass->drawSrid) {
        view.renderPass->scene.projection->forward(&this->centroidProj, this->centroid);
        view.renderPass->scene.projection->forward(&this->centroidProjHemi2, this->centroidHemi2);

        if(this->vertexCoordinates2 == GL_NONE)
            this->vertexCoordinates2 = core->resources->genBuffer();

        core->resources->coordStreamBufferF.reset();

        // recompute vertex coordinates as necessary
        core->vertexResolver->beginNode(*this);
        for (std::size_t i = 0; i <= this->glTexGridHeight; i++) {
            for (std::size_t j = 0; j <= this->glTexGridWidth; j++) {
                auto& gridVertex = this->gridVertices.value[(i*(this->glTexGridWidth+1))+j];
                if (!gridVertex.resolved) {
                    core->vertexResolver->project(
                        &gridVertex.lla,
                        &gridVertex.resolved,
                        (this->tileSrcX + ((this->tileSrcWidth * j) / this->glTexGridWidth)),
                        (this->tileSrcY + ((this->tileSrcHeight * i) / this->glTexGridHeight)));
                }
                view.renderPass->scene.projection->forward(&gridVertex.xyz, gridVertex.lla);

                // obtain position in LCS
                float xyz[3u] =
                {
                    (float)(gridVertex.xyz.x-centroidProj.x),
                    (float)(gridVertex.xyz.y-centroidProj.y),
                    (float)(gridVertex.xyz.z-centroidProj.z),
                };
                core->resources->coordStreamBufferF.put(xyz, 3u);
            }
        }
        core->vertexResolver->endNode(*this);

        glBindBuffer(GL_ARRAY_BUFFER, this->vertexCoordinates2);
        glBufferData(GL_ARRAY_BUFFER, this->glTexGridVertCount*3*4, core->resources->coordStreamBuffer.get(), GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

        this->gridVertices.valid = true;
        this->gridVertices.srid = view.renderPass->drawSrid;

        // force secondary hemi centroid reprojection
        this->primaryHemi = -1;
    }
}
void GLQuadTileNode3::cull(const int pump) NOTHROWS
{
    this->verticesInvalid = false;
    if(this->lastTouch != pump) {
        if(this != &this->root)
            this->release();
        else
            this->abandon();
    } else {
        for(std::size_t i = 0u; i < 4u; i++) {
            if(!this->children[i])
                continue;
            if(this->children[i]->lastTouch != pump) {
                this->children[i]->release();
                this->children[i].reset();
            } else {
                this->children[i]->cull(pump);
            }
        }
    }
}
int GLQuadTileNode3::getRenderPass() NOTHROWS
{
    return GLGlobeBase::Surface;
}
void GLQuadTileNode3::draw(const GLGlobeBase &view, const int renderPass) NOTHROWS
{
    if(!MathUtils_hasBits(renderPass, GLGlobeBase::Surface))
        return;

    if (this->parent) {
        Logger_log(TELL_Error, "GLQuadTileNode3: External draw method should only be invoked on root node!");
        return;
    }

    if(!core->shader) {
        RenderAttributes attrs;
        attrs.textureIds[0] = 1;
        Shader_get(core->shader, core->context, attrs);
        void* ctrl = nullptr;
        view.getControl(&ctrl, "TAK.Engine.Renderer.Core.Controls.SurfaceRendererControl");
        if(ctrl)
            core->surfaceControl = static_cast<SurfaceRendererControl *>(ctrl);
    }

    if(this->lastTouch != view.renderPass->renderPump) {
        // prioritize read requests based on camera location
        GeoPoint2 camlocLLA;
        view.renderPasses[0u].scene.projection->inverse(&camlocLLA, view.renderPasses[0u].scene.camera.location);
        camlocLLA.altitude = 0.0;

        core->prioritize(camlocLLA);
        core->refreshUpdateList();

        core->vertexResolver->beginDraw(view);
    }

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(core->shader->handle);
    if (core->shader->aNormals >= 0)
        glVertexAttrib3f(core->shader->aNormals, 0.f, 0.f, 0.f);
    //glVertexAttrib4f(core->shader->aColors, 1.f, 1.f, 1.f, 1.f);

    glUniform1ui(core->shader->uTexture, 0);
    glUniform4f(core->shader->uColor, core->colorR, core->colorG, core->colorB, core->colorA);
    glEnableVertexAttribArray(core->shader->aTexCoords);
    glEnableVertexAttribArray(core->shader->aVertexCoords);

    glUniform4f(core->shader->uColor, core->colorR, core->colorG, core->colorB, core->colorA);
    core->renderState.r = core->colorR;
    core->renderState.g = core->colorG;
    core->renderState.b = core->colorB;
    core->renderState.a = core->colorA;

    // construct the ortho transform
    {
        float mx[16u];
        atakmap::renderer::GLMatrix::orthoM(mx, (float)view.renderPass->left, (float)view.renderPass->right, (float)view.renderPass->bottom, (float)view.renderPass->top, (float)view.renderPass->scene.camera.near, (float)view.renderPass->scene.camera.far);
        for(std::size_t i = 0; i < 16; i++)
            core->xproj.set(i % 4, i / 4, mx[i]);
    }
    core->mvp.set(core->xproj);
    core->mvp.concatenate(view.renderPass->scene.forwardTransform);

    core->tilesThisFrame = 0;
    if (shouldDraw(view, minLng, minLat, maxLng, maxLat)) {
        const int lastStateMask = core->stateMask;
        core->stateMask = 0;
        const double scale = (core->info.maxGsd / view.renderPass->drawMapResolution);

        // XXX - tune level calculation -- it may look better to swap to the
        // next level before we actually cross the threshold
        const int level = (int)std::ceil(std::max((std::log(1.0 / scale) / std::log(2.0)) + core->options.levelTransitionAdjustment, 0.0));
        core->drawPumpLevel = level;

        bool hasPrecisionCoords = false;
        this->hasPreciseCoordinates(&hasPrecisionCoords);
        if (view.targeting && hasPrecisionCoords)
            core->magFilter = GL_NEAREST;
        else
            core->magFilter = GL_LINEAR;

        if (core->tileReader)
            core->tileReader->start();

        this->drawImpl(view, std::min(level, (int)root.tileIndex.z));

        if (core->tileReader)
            core->tileReader->stop();

        glDisableVertexAttribArray(core->shader->aTexCoords);
        glDisableVertexAttribArray(core->shader->aVertexCoords);

        glUseProgram(GL_NONE);
        glDisable(GL_BLEND);
        //Log.v(TAG, "Tiles this frame: " + core->tilesThisFrame);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);

        core->renderState.texCoords = GL_NONE;
        core->renderState.vertexCoords = GL_NONE;
        core->renderState.indices = GL_NONE;
        core->renderState.texId = 0;

        core->resources->deleteBuffers();

        if (core->frameBufferHandle) {
            glDeleteFramebuffers(1, &core->frameBufferHandle);
            core->frameBufferHandle = GL_NONE;
        }
        if (core->depthBufferHandle) {
            glDeleteRenderbuffers(1, &core->depthBufferHandle);
            core->depthBufferHandle = GL_NONE;
        }

        if (core->stateMask != lastStateMask)
            core->context.requestRefresh();
    }
    // cull any undrawn
    if(!view.multiPartPass) {
        cull(view.renderPass->renderPump);

        core->vertexResolver->endDraw(view);
    }
}
bool GLQuadTileNode3::shouldResolve() const NOTHROWS
{
    bool resolveUnresolvable = false;
    for (std::size_t i = 0; i < 4u; i++)
        resolveUnresolvable |= this->children[i] && (this->children[i]->receivedUpdate);

    return (this->state == State::UNRESOLVABLE && resolveUnresolvable) ||
            ((this->state == State::UNRESOLVED) && (!this->currentRequest));
}
void GLQuadTileNode3::resolveTexture(bool fetch) NOTHROWS
{
    if(!core->isMultiResolution && core->textureCache) {
        std::ostringstream key;
        key << core->info.path.get() << "&x=" << tileIndex.x << "&y=" << tileIndex.y << "&z=" << tileIndex.z;
        GLTextureCache2::EntryPtr entry(nullptr, nullptr);
        core->textureCache->remove(entry, key.str().c_str());
        if(entry && entry->texture) {
            if(this->texture)
                this->texture->release();
            this->texture = std::move(entry->texture);
            //this->state = MathUtils_hasBits(entry.hints, GLQuadTileNode3.TEXTURE_CACHE_HINT_RESOLVED) ? State::RESOLVED : State::UNRESOLVED;
            this->state = State::UNRESOLVED;
            if(entry->opaque)
                this->tileVersion = (int64_t)(intptr_t)entry->opaque.get();
            this->receivedUpdate = true;
            if(this->state == State::RESOLVED)
                return;
        }
    }

    // copy data from the children to our texture
    if(this->state != State::RESOLVED &&
            core->options.textureCopyEnabled &&
#if 0
            GLMapSurface.SETTING_enableTextureTargetFBO &&
#endif
            core->textureCopyEnabled &&
            !offscreenFboFailed) {

        int64_t ntxChild, ntyChild;
        core->tileReader->getNumTilesX(&ntxChild, this->tileIndex.z+1);
        core->tileReader->getNumTilesY(&ntyChild, this->tileIndex.z+1);

        bool hasChildData = false;
        bool willBeResolved = true;
        int numChildren = 0;
        for(std::size_t i = 0; i < 4; i++) {
            const std::size_t ctx = this->tileIndex.x+(i%2);
            const std::size_t cty = this->tileIndex.y+(i%2);
            if(ctx > (std::size_t)ntxChild)
                continue;
            if(cty > (std::size_t)ntyChild)
                continue;
            numChildren++;
            willBeResolved &= (this->children[i] && (this->children[i]->state == State::RESOLVED));
            if (this->children[i]
                    && ((this->children[i]->state == State::RESOLVED) || this->children[i]->receivedUpdate)) {
                hasChildData = true;
            }
        }

        if (hasChildData) {
            // XXX - luminance is not renderable for FBO
            this->glTexFormat = GL_RGBA;
            this->glTexType = GL_UNSIGNED_BYTE;

            this->validateTexture();
            this->texture->init();

            int parts = 0;
            GLint currentFrameBuffer;

            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFrameBuffer);

            GLOffscreenFramebuffer fbo;
            GLOffscreenFramebuffer::Options opts;

            // pre-allocated texture
            fbo.colorTexture = this->texture->getTexId();
            opts.colorFormat = GL_NONE;
            // no depth/stencil
            opts.bufferMask = GL_COLOR_BUFFER_BIT;
            
            if (GLOffscreenFramebuffer_create(&fbo, (int)this->tileWidth, (int)this->tileHeight, opts) == TE_Ok) {
                fbo.bind(false);

                // x,y,width,height
                GLint viewport[4];
                glGetIntegerv(GL_VIEWPORT, viewport);

                // reset the viewport to the tile dimensions
                glViewport(0, 0, (GLsizei)this->tileWidth, (GLsizei)this->tileHeight);

                // construct an ortho projection to render the tile data
                float mx[16];
                atakmap::renderer::GLMatrix::orthoM(mx, 0, (float)this->tileWidth, 0, (float)this->tileHeight, 1, -1);
                glUniformMatrix4fv(core->shader->uMVP, 1, false, mx);

                core->resources->coordStreamBuffer.reset();
                MemBuffer2 &xyuv = core->resources->coordStreamBufferF;

                glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT|GL_STENCIL_BUFFER_BIT);

                std::size_t tx;
                std::size_t ty;
                std::size_t partX;
                std::size_t partY;
                std::size_t partWidth;
                std::size_t partHeight;

                float childTexWidth;
                float childTexHeight;
                for(std::size_t i = 0; i < 4; i++) {
                    if (this->children[i]
                            && this->children[i]->texture
                            && ((this->children[i]->state == State::RESOLVED) || this->children[i]->receivedUpdate)) {
                        tx = i % 2;
                        ty = i / 2;
                        partX = tx * this->halfTileWidth;
                        partY = ty * this->halfTileHeight;
                        partWidth = (std::min((tx + 1) * (this->halfTileWidth), this->tileWidth) - partX);
                        partHeight = (std::min((ty + 1) * (this->halfTileHeight), this->tileHeight) - partY);
                        childTexWidth = (float)this->children[i]->texture->getTexWidth();
                        childTexHeight = (float)this->children[i]->texture->getTexHeight();

                        xyuv.reset();
                        // ll
                        xyuv.put<float>((float)partX);
                        xyuv.put<float>((float)partY);
                        xyuv.put<float>((float)(0.f/childTexWidth));
                        xyuv.put<float>((float)(0.f/childTexHeight));
                        // lr
                        xyuv.put<float>((float)(partX + partWidth));
                        xyuv.put<float>((float)partY);
                        xyuv.put<float>((float)(this->children[i]->tileWidth/childTexWidth));
                        xyuv.put<float>((float)(0.f/childTexHeight));
                        // ur
                        xyuv.put<float>((float)(partX + partWidth));
                        xyuv.put<float>((float)(partY + partHeight));
                        xyuv.put<float>((float)((float) this->children[i]->tileWidth/childTexWidth));
                        xyuv.put<float>((float)((float) this->children[i]->tileHeight/childTexHeight));
                        // ul
                        xyuv.put<float>((float)partX);
                        xyuv.put<float>((float)(partY + partHeight));
                        xyuv.put<float>((float)(0.f/childTexWidth));
                        xyuv.put<float>((float)((float) this->children[i]->tileHeight/childTexHeight));

                        xyuv.flip();

                        glUniform4f(core->shader->uColor, 1.f, 1.f, 1.f, 1.f);
                        core->renderState.r = 1.f;
                        core->renderState.g = 1.f;
                        core->renderState.b = 1.f;
                        core->renderState.a = 1.f;
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
                        core->renderState.indices = GL_NONE;
                        core->renderState.texCoords = GL_NONE;
                        core->renderState.vertexCoords = GL_NONE;
                        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
                        glVertexAttribPointer(core->shader->aVertexCoords, 2, GL_FLOAT, false, 16, xyuv.get());
                        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
                        glVertexAttribPointer(core->shader->aTexCoords, 2, GL_FLOAT, false, 16, xyuv.get()+(2u*sizeof(float)));
                        glBindTexture(GL_TEXTURE_2D, this->children[i]->texture->getTexId());
                        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

                        // if the child is resolved, increment parts
                        if (this->children[i]->state == State::RESOLVED)
                            parts++;
                    }
                }

                // relinquish reference to color attachment before release
                fbo.colorTexture = GL_NONE;

                // release FBO
                GLOffscreenFramebuffer_release(fbo);

                glBindFramebuffer(GL_FRAMEBUFFER, currentFrameBuffer);
                // restore the viewport
                glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

                this->textureCoordsValid = false;
                this->receivedUpdate = true;

                this->validateTexVerts();

                if(!willBeResolved) {
                    this->texture->setMagFilter(GL_NEAREST);
                    this->texture->getTexId();
                }
            } else {
                Logger_log(TELL_Warning, "GLQuadTileNode3: Failed to create FBO for texture copy.");
                offscreenFboFailed = true;
            }

            const bool wasUnresolvable = (this->state == State::UNRESOLVABLE);

            // mark resolved if all 4 children were resolved
            if (parts == numChildren) {
                if(core->options.childTextureCopyResolvesParent)
                    this->state = State::RESOLVED;
                else if(this->state != State::UNRESOLVABLE)
                    this->state = State::UNRESOLVED;
            } else if (this->state != State::SUSPENDED) {
                this->state = State::UNRESOLVED;
            }

            // if the tile was previously unresolvable, record whether or
            // not we were able to derive any data through compositing
            if(wasUnresolvable)
                this->derivedUnresolvableData |= (parts > 0);

            // if the tile will not be resolved via compositing, switch to
            // nearest neighbor interpolation to try to prevent edges
            if(!willBeResolved)
                this->texture->setMinFilter(GL_NEAREST);
        }
    } else if(this->state == State::RESOLVED) {
        // a refresh has been requested, move back into the unresolved state
        // to reload the tile
        this->state = State::UNRESOLVED;
    }
    if (this->state == State::UNRESOLVED && fetch) {
        this->state = State::RESOLVING;
        this->readRequestStart = Platform_systime_millis();

        this->currentRequest.reset(
            new TileReader2::ReadRequest(
                this->core->tileReader,
                static_cast<int>(tileIndex.z),
                this->tileIndex.x,
                this->tileIndex.y, this));
        this->readRequests.push_back(this->currentRequest);
        this->core->asyncio->asyncRead(this->currentRequest);
    }
}
void GLQuadTileNode3::resetFadeTimer() NOTHROWS
{
    float levelScale = 1.f - (float)(this->tileIndex.z-core->drawPumpLevel) / (float)(this->root.tileIndex.z-core->drawPumpLevel);
    //if(this->borrowingFrom != null) {
    if(false) {
        this->fadeTimer = std::max((int64_t)(core->fadeTimerLimit * levelScale) - this->readRequestElapsed, 0LL);
    } else {
        this->fadeTimer = 0L;
    }
}
bool GLQuadTileNode3::needsRefresh() const NOTHROWS
{
    int64_t version = this->tileVersion;
    core->tileReader->getTileVersion(&version, this->tileIndex.z, this->tileIndex.x, this->tileIndex.y);
    return this->tileVersion != version
        || (core->updatedTiles.value.find(this->tileIndex) != core->updatedTiles.value.end());
}
bool GLQuadTileNode3::drawImpl(const GLGlobeBase &view, const int level) NOTHROWS
{
    // dynamically refine level based on expected nominal resolution for tile as rendered
    const bool recurse = (level < this->tileIndex.z);
    this->lastTouch = view.renderPass->renderPump;

    if (!recurse) {
        // draw self
        this->drawImpl(view, true);
        this->descendantsRequestDraw = false;
        switch (this->state) {
            case RESOLVED:
                core->stateMask |= STATE_RESOLVED;
                break;
            case RESOLVING:
                core->stateMask |= STATE_RESOLVING;
                break;
            case UNRESOLVED:
                core->stateMask |= STATE_UNRESOLVED;
                break;
            case UNRESOLVABLE:
                core->stateMask |= STATE_UNRESOLVABLE;
                break;
            case SUSPENDED:
                core->stateMask |= STATE_SUSPENDED;
                break;
        }
        return (this->state != State::RESOLVED);
    } else {
        // determine children to draw
        bool drawChild[4u]; // UL, UR, LL, LR
        for (int i = 0; i < 4; i++)
            drawChild[i] = (childMbb[i].valid) && shouldDraw(view, childMbb[i].value.minX, childMbb[i].value.minY, childMbb[i].value.maxX, childMbb[i].value.maxY);

        // ensure we have child to be drawn
        for (std::size_t i = 0u; i < 4u; i++) {
            if (drawChild[i] && !this->children[i]) {
                this->children[i].reset(new GLQuadTileNode3(this, i));
                this->children[i]->selfRef = this->children[i];
            }
        }

        // should resolve self if:
        // - multires and any child is unresolvable
        // should draw self if:
        // - texture data available and any visible descendant not resolved

        const bool multiRes = core->isMultiResolution;

        // load texture to support child
        if(core->isMultiResolution &&
            (this->tileIndex.z == this->root.tileIndex.z-1 || this->tileIndex.z == (level + 3)) &&
            ((this->state == State::UNRESOLVED) || (this->state == State::UNRESOLVABLE && needsRefresh()))) {

            if(state == State::UNRESOLVABLE) state = State::UNRESOLVED;
            this->resolveTexture(core->isMultiResolution);
            this->touched = true;
            view.context.requestRefresh();
        }

        // draw children
        descendantsRequestDraw = false;
        for(std::size_t i = 0; i < 4; i++) {
            if(!drawChild[i])
                continue;
            this->children[i]->verticesInvalid = this->verticesInvalid;
            descendantsRequestDraw |= this->children[i]->drawImpl(view, level);
        }

        return this->descendantsRequestDraw && (this->state != State::RESOLVED);
    }
}
void GLQuadTileNode3::drawTexture(const GLGlobeBase &view, GLTexture2 &tex, const int texCoords) NOTHROWS
{
    if (tex.getMinFilter() != core->minFilter)
        tex.setMinFilter(core->minFilter);
    if (tex.getMagFilter() != core->magFilter)
        tex.setMagFilter(core->magFilter);

    // draw tile
    drawTextureImpl(view, tex, texCoords);
}
void GLQuadTileNode3::setLCS(const GLGlobeBase &view) NOTHROWS
{
    const double epsilon = 1e-5;
    const bool hemi =
            (view.renderPass->drawLng < 0.0 && (maxLng-epsilon) <= 180.0) ||
            (view.renderPass->drawLng >= 0.0 && (minLng+epsilon) >= -180.0);

    const double tx = hemi ? centroidProj.x : centroidProjHemi2.x;
    const double ty = hemi ? centroidProj.y : centroidProjHemi2.y;
    const double tz = hemi ? centroidProj.z : centroidProjHemi2.z;

    Matrix2 mvp(core->mvp);
    mvp.translate(tx, ty, tz);
    float mx[16];
    for(std::size_t i = 0u; i < 16u; i++) {
        double v;
        mvp.get(&v, i%4u, i/4u);
        mx[i] = static_cast<float>(v);
    }
    glUniformMatrix4fv(core->shader->uMVP, 1u, GL_FALSE, mx);
}
void GLQuadTileNode3::drawTextureImpl(const GLGlobeBase &view, GLTexture2 &tex, const int texCoords) NOTHROWS
{
    setLCS(view);

    float fade = (&tex == this->texture.get() && core->fadeTimerLimit > 0L) ?
            ((float)(core->fadeTimerLimit
                    -this->fadeTimer) /
                    (float)core->fadeTimerLimit) :
            1.f;

    const float r = core->colorR;
    const float g = core->colorG;
    const float b = core->colorB;
    const float a = fade * core->colorA;
    if(r != core->renderState.r || g != core->renderState.g || b != core->renderState.b || a != core->renderState.a) {
        glUniform4f(core->shader->uColor, r, g, b, a);
        core->renderState.r = r;
        core->renderState.g = g;
        core->renderState.b = b;
        core->renderState.a = a;
    }

    // bind texture coordinates
    if(core->renderState.texCoords != texCoords)
    {
        glBindBuffer(GL_ARRAY_BUFFER, texCoords);
        glVertexAttribPointer(core->shader->aTexCoords, 2, GL_FLOAT, false, 0, 0);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        core->renderState.texCoords = texCoords;
    }
    // bind vertex coordinates
    glBindBuffer(GL_ARRAY_BUFFER, this->vertexCoordinates2);
    glVertexAttribPointer(core->shader->aVertexCoords, 3, GL_FLOAT, false, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    core->renderState.vertexCoords = this->vertexCoordinates2;

    // bind texture
    glBindTexture(GL_TEXTURE_2D, tex.getTexId());

    // bind indices, if unused, this _unbinds_ any existing index binding
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this->glTexCoordIndices2);
    if (this->glTexCoordIndices2 == GL_NONE)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, (GLsizei)this->glTexGridVertCount);
    else
        glDrawElements(GL_TRIANGLE_STRIP, (GLsizei)this->glTexGridIdxCount, GL_UNSIGNED_SHORT, 0);

    core->tilesThisFrame++;
}
void GLQuadTileNode3::expandTexGrid() NOTHROWS
{
    this->glTexGridWidth *= 2;
    this->glTexGridHeight *= 2;

    this->gridVertices.value.reserve((this->glTexGridWidth+1)*(this->glTexGridHeight+1));
    this->gridVertices.value.clear();
    for (std::size_t i = 0u; i < ((this->glTexGridWidth + 1) * (this->glTexGridHeight + 1)); i++)
        this->gridVertices.value.push_back(GridVertex());

    this->textureCoordsValid = false;
}
void GLQuadTileNode3::drawImpl(const GLGlobeBase &view, const bool resolve) NOTHROWS
{
    // clear cached mesh vertices
    this->gridVertices.valid &= !this->verticesInvalid;

    super_draw(view, resolve);
    if(this->state == State::RESOLVED && this->fadeTimer != 0L){
        this->fadeTimer = std::max(
                this->fadeTimer-view.animationDelta, 0LL);
    }
}
void GLQuadTileNode3::super_draw(const GLGlobeBase &view, const bool resolve) NOTHROWS
{
    // check the tiles version and move back into the UNRESOLVED state if
    // the tile version has changed
    if(resolve) {
        if ((this->state == State::UNRESOLVABLE || this->state == State::RESOLVED) &&
                needsRefresh()) {

            if (this->state == State::UNRESOLVABLE)
                this->state = State::UNRESOLVED;
            this->resolveTexture(resolve);
        }
        // read the data if we don't have it yet
        else if (this->shouldResolve())
            this->resolveTexture(resolve);
    }
    if(!touched && (this->state == State::RESOLVED || this->state == State::UNRESOLVABLE)) {
        touched = true;
    }
    // XXX - always false in java reference impl
    //if(!core->options.adaptiveTileLod)
    {
        if (this->state != State::RESOLVED) {
            // borrow
            this->validateTexCoordIndices();
            auto borrowedTexture = this->tryBorrow();
            if (borrowedTexture) {
                this->validateVertexCoords(view);
                this->drawTexture(view, *borrowedTexture, this->borrowTextureCoordinates);
            }
        } else {
            this->borrowingFrom = nullptr;
            this->borrowTextureCoordinates = core->resources->discardBuffer(this->borrowTextureCoordinates);
        }
    }

    if (this->receivedUpdate && this->texture) {
        this->validateTexCoordIndices();
        this->validateTexVerts();
        this->validateVertexCoords(view);

        this->drawTexture(view, *this->texture, this->textureCoordinates);
    }

    if(core->debugDrawEnabled)
        this->debugDraw(view);
}
TAK::Engine::Renderer::GLTexture2 *GLQuadTileNode3::tryBorrow() NOTHROWS
{
    GLQuadTileNode3 *borrowFrom = this->parent;
    GLQuadTileNode3 *updatedAncestor = nullptr;
    while(borrowFrom && borrowFrom != this->borrowingFrom) {
        if(borrowFrom->state == State::RESOLVED)
            break;
        else if(borrowFrom->receivedUpdate && updatedAncestor == nullptr)
            updatedAncestor = borrowFrom;
        borrowFrom = borrowFrom->parent;
    }
    // if no ancestor is resolved, but there is an ancestor with an update, use the update
    if(borrowFrom == nullptr && updatedAncestor != nullptr)
        borrowFrom = updatedAncestor;
    // no texture to borrow
    if(!borrowFrom)
        return nullptr;
    else if(!borrowFrom->texture)
        // XXX -
        return nullptr;
    if(borrowFrom != this->borrowingFrom) {
        this->gridVertices.valid = false;
        if(this->borrowTextureCoordinates == GL_NONE)
            this->borrowTextureCoordinates = core->resources->genBuffer();

        // map the region of the ancestor tile into this tile
        const float extentX = ((float) borrowFrom->tileWidth / (float) borrowFrom->texture->getTexWidth());
        const float extentY = ((float) borrowFrom->tileHeight / (float) borrowFrom->texture->getTexHeight());

        const float minX = std::max(
                ((float) (this->tileSrcX - borrowFrom->tileSrcX - 1) / (float) borrowFrom->tileSrcWidth) * extentX, 0.0f);
        const float minY = std::max(
                ((float) (this->tileSrcY - borrowFrom->tileSrcY - 1) / (float) borrowFrom->tileSrcHeight) * extentY, 0.0f);
        const float maxX = std::min(
                ((float) ((this->tileSrcX + this->tileSrcWidth) - borrowFrom->tileSrcX + 1) / (float) borrowFrom->tileSrcWidth)
                        * extentX, 1.0f);
        const float maxY = std::min(
                ((float) ((this->tileSrcY + this->tileSrcHeight) - borrowFrom->tileSrcY + 1) / (float) borrowFrom->tileSrcHeight)
                        * extentY, 1.0f);

        core->resources->coordStreamBufferF.reset();
        GLTexture2_createQuadMeshTexCoords(
                core->resources->coordStreamBufferF,
                Point2<float>(minX, minY),
                Point2<float>(maxX, minY),
                Point2<float>(maxX, maxY),
                Point2<float>(minX, maxY),
                this->glTexGridWidth,
                this->glTexGridHeight);
        glBindBuffer(GL_ARRAY_BUFFER, this->borrowTextureCoordinates);
        glBufferData(GL_ARRAY_BUFFER, this->glTexGridVertCount*2*4, core->resources->coordStreamBuffer.get(), GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

        this->borrowingFrom = borrowFrom;
    }

    return this->borrowingFrom->texture.get();
}
void GLQuadTileNode3::debugDraw(const GLGlobeBase &view) NOTHROWS
{
    this->validateVertexCoords(view);

    glLineWidth(2.f);
    {
        GLuint whitePixel;
        if(GLMapRenderGlobals_getWhitePixel(&whitePixel, core->context) == TE_Ok)
            glBindTexture(GL_TEXTURE_2D, whitePixel);
    }

    core->renderState.texId = 0;

    // bind texture coordinates
    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    glDisableVertexAttribArray(core->shader->aTexCoords);
    glVertexAttrib2f(core->shader->aTexCoords, 0.f, 0.f);
    core->renderState.texCoords = GL_NONE;

    // bind vertex coordinates
    if(core->renderState.vertexCoords != this->vertexCoordinates2) {
        glBindBuffer(GL_ARRAY_BUFFER, this->vertexCoordinates2);
        glVertexAttribPointer(core->shader->aVertexCoords, 3, GL_FLOAT, false, 0, 0);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        core->renderState.vertexCoords = this->vertexCoordinates2;
    }

    this->setLCS(view);

    float r, g, b, a;

    // debug draw indices
    {
        core->resources->coordStreamBuffer.reset();
        GLTexture2_createQuadMeshIndexBuffer(core->resources->coordStreamBuffer, GL_UNSIGNED_SHORT, glTexGridWidth, glTexGridHeight);
        core->resources->coordStreamBuffer.flip();
        std::size_t numWireframeIndices;
        GLWireframe_getNumWireframeElements(&numWireframeIndices, GL_TRIANGLE_STRIP, (GLuint)GLTexture2_getNumQuadMeshIndices(glTexGridWidth, glTexGridHeight));
        array_ptr<uint16_t> dbgIndices(new uint16_t[numWireframeIndices]);
        GLWireframe_deriveIndices(dbgIndices.get(), &numWireframeIndices, (const uint16_t*)core->resources->coordStreamBuffer.get(), GL_TRIANGLE_STRIP, (GLuint)GLTexture2_getNumQuadMeshIndices(glTexGridWidth, glTexGridHeight));
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
        core->renderState.indices = GL_NONE;
        glDrawElements(GL_LINES, (GLsizei)numWireframeIndices, GL_UNSIGNED_SHORT, dbgIndices.get());        
    }

    r = 0.f;
    g = 1.f;
    b = 1.f;
    a = 1.f;
    if(r != core->renderState.r || g != core->renderState.g || b != core->renderState.b || a != core->renderState.a) {
        glUniform4f(core->shader->uColor, r, g, b, a);
        core->renderState.r = r;
        core->renderState.g = g;
        core->renderState.b = b;
        core->renderState.a = a;
    }

    uint16_t dbg[4u];

    dbg[0] = (short)0;
    dbg[1] = (short)this->glTexGridWidth;
    dbg[2] = (short)(((this->glTexGridHeight + 1) * (this->glTexGridWidth + 1)) - 1);
    dbg[3] = (short)((this->glTexGridHeight * (this->glTexGridWidth + 1)));

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);

    // XXX - tints tile based on state
    if(false) {
        unsigned color = 0;
        if (state == State::UNRESOLVABLE)
            color = 0x3FFF0000u;
        else if (state == State::UNRESOLVED)
            color = 0x3F0000FFu;
        else if (state == State::RESOLVED)
            color = 0x3F00FF00u;
        else if (state == State::RESOLVING)
            color = 0x3F00FFFFu;
        else if (state == State::SUSPENDED)
            color = 0x3FFFFF00u;
        r = ((color&0x00FF0000u)>>16u) / 255.f;
        g = ((color&0x0000FF00u)>>8u) / 255.f;
        b = (color&0xFFu) / 255.f;
        a = ((color&0xFF000000u)>>24u) / 255.f;
        if (r != core->renderState.r || g != core->renderState.g || b != core->renderState.b || a != core->renderState.a) {
            glUniform4f(core->shader->uColor, r, g, b, a);
            core->renderState.r = r;
            core->renderState.g = g;
            core->renderState.b = b;
            core->renderState.a = a;
        }
        glDrawElements(GL_TRIANGLE_FAN, 4, GL_UNSIGNED_SHORT, dbg);
    }

    r = 0.f;
    g = 1.f;
    b = 1.f;
    a = 1.f;
    if(r != core->renderState.r || g != core->renderState.g || b != core->renderState.b || a != core->renderState.a) {
        glUniform4f(core->shader->uColor, r, g, b, a);
        core->renderState.r = r;
        core->renderState.g = g;
        core->renderState.b = b;
        core->renderState.a = a;
    }
    glDrawElements(GL_LINE_LOOP, 4, GL_UNSIGNED_SHORT, dbg);
    core->renderState.indices = GL_NONE;

    glUseProgram(GL_NONE);

    GLText2 *_titleText = GLText2_intern(TextFormatParams(16.f));
    Point2<float> textxy;
    view.renderPass->scene.forward(&textxy, centroid);
    auto &gles10 = *atakmap::renderer::GLES20FixedPipeline::getInstance();
    gles10.glPushMatrix();
    gles10.glTranslatef(textxy.x, textxy.y, 0);
    {
        std::ostringstream strm;
        strm << this->tileIndex.x << "," << this->tileIndex.y << "," << this->tileIndex.z << " " << GLResolvable::getNameForState(this->state);
        _titleText->draw(strm.str().c_str(), 0.0f, 0.0f, 1.0f, 1.0f);
    }
    gles10.glTranslatef(0.f, -_titleText->getTextFormat().getBaselineSpacing(), 0.f);
    {
        String filename;
        IO_getName(filename, core->info.path);
        _titleText->draw(filename, 0.0f, 0.0f, 1.0f, 1.0f);
    }
    gles10.glPopMatrix();

    // restore the program, re-enable attribute arrays
    glUseProgram(core->shader->handle);
    glEnableVertexAttribArray(core->shader->aVertexCoords);
    glEnableVertexAttribArray(core->shader->aTexCoords);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}
void GLQuadTileNode3::abandon() NOTHROWS
{
    for (std::size_t i = 0u; i < 4u; i++) {
        if (this->children[i]) {
            this->children[i]->release();
            this->children[i].reset();
        }
    }
}
GLResolvable::State GLQuadTileNode3::getState()
{
    // check for bits in order of precedence
    if(MathUtils_hasBits(core->stateMask, STATE_RESOLVING))
        // resolving if any resolving
        return State::RESOLVING;
    else if(MathUtils_hasBits(core->stateMask, STATE_UNRESOLVED))
        // unresolved if any unresolved
        return State::UNRESOLVED;
    else if(MathUtils_hasBits(core->stateMask, STATE_UNRESOLVABLE))
        // unresolvable if toggled and no resolving or unresolved
        return State::UNRESOLVABLE;
    else if(MathUtils_hasBits(core->stateMask, STATE_RESOLVED))
        // resolved if toggled and no resolving, unresolved or unresolvable
        return State::RESOLVED;
    else if(MathUtils_hasBits(core->stateMask, STATE_SUSPENDED))
        // suspended if toggled and no others
        return State::SUSPENDED;
    else
        // no bits, unresolved
        return State::UNRESOLVED;
}
void GLQuadTileNode3::suspend()
{
    core->suspended = true;
    for(std::size_t i = 0; i < 4u; i++)
        if (this->children[i])
            this->children[i]->suspend();
    if (this->state == State::RESOLVING && this->currentRequest) {
        this->currentRequest->cancel();
        this->state = State::SUSPENDED;
        if(core->cacheControl)
            core->cacheControl->abort(this->tileIndex.z, this->tileIndex.x, this->tileIndex.y);
    }
    this->readRequests.remove(this->currentRequest);
    this->currentRequest.reset();
}
void GLQuadTileNode3::resume()
{
    core->suspended = false;
    for(std::size_t i = 0; i < 4u; i++)
        if (this->children[i])
            this->children[i]->resume();
    // move us back into the unresolved from suspended to re-enable texture
    // loading
    if (this->state == State::SUSPENDED) {
        this->state = State::UNRESOLVED;
        this->currentRequest.reset();
    }
}
TAKErr GLQuadTileNode3::getType(String &value) NOTHROWS
{
    value = core->info.type;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::getUri(String &value) NOTHROWS
{
    value = core->info.path;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::imageToGround(GeoPoint2 *ground, bool *isPrecise, const Point2<double> &image) NOTHROWS
{
    if(core->precise && isPrecise) {
        if(core->precise->imageToGround(ground, image) == TE_Ok) {
            // we were able to compute the precise I2G value -- make sure it
            // falls within the maximum allowed error
            GeoPoint2 imprecise;
            core->imprecise->imageToGround(&imprecise, image);
            const double err = GeoPoint2_distance(*ground, imprecise, false);
            const int errPixels = (int) (err / core->info.maxGsd);
            if (errPixels > (std::sqrt((core->nominalTileWidth * core->nominalTileWidth) +
                    (core->nominalTileHeight * core->nominalTileHeight)) / 8.0)) {

                Logger_log(TELL_Warning, "GLQuadTileNode3: Large discrepency observed for %s imageToGround, discarding point (error=%.3fm, %dpx)", core->info.type.get(), err, errPixels);

                // fall through to imprecise computation
            } else {
                *isPrecise = true;
                return TE_Ok;
            }
        }
    }
    if(isPrecise) *isPrecise = false;
    return core->imprecise->imageToGround(ground, image);
}
TAKErr GLQuadTileNode3::groundToImage(Point2<double> *image, bool *isPrecise, const GeoPoint2 &ground) NOTHROWS
{
    if(core->precise && isPrecise) {
        if(core->precise->groundToImage(image, ground) == TE_Ok) {
            // we were able to compute the precise G2I value -- make sure it
            // falls within the maximum allowed error
            Point2<double> imprecise(0.0, 0.0);
            core->imprecise->groundToImage(&imprecise, ground);
            const double dx = (image->x-imprecise.x);
            const double dy = (image->y-imprecise.y);
            const double errPixels = std::sqrt(dx*dx + dy*dy);
            const double errMeters = (int) (errPixels * core->info.maxGsd);
            if (errPixels > (std::sqrt((core->nominalTileWidth * core->nominalTileWidth) +
                    (core->nominalTileHeight * core->nominalTileHeight)) / 8.0)) {

                Logger_log(TELL_Warning, "GLQuadTileNode3: Large discrepency observed for %s groundToImage, discarding point (error=%.3fm, %dpx)", core->info.type.get(), errMeters, errPixels);
                return TE_Err;
            } else {
                *isPrecise = true;
                return TE_Ok;
            }
        }
    }
    if(isPrecise) *isPrecise = false;
    return core->imprecise->groundToImage(image, ground);
}
TAKErr GLQuadTileNode3::getSpatialReferenceId(int* value) NOTHROWS
{
    *value = core->info.srid;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::hasPreciseCoordinates(bool *value) NOTHROWS
{
    *value = !!core->precise;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::getWidth(int *value) NOTHROWS
{
    *value = (int)core->info.width;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::getHeight(int *value) NOTHROWS
{
    *value = (int)core->info.height;
    return TE_Ok;
}
TAKErr GLQuadTileNode3::getImageInfo(ImageInfoPtr_const& info) NOTHROWS
{
    info = ImageInfoPtr_const(new ImageInfo(core->info), Memory_deleter_const<ImageInfo>);
    return TE_Ok;
}
TAKErr GLQuadTileNode3::getImageInfo(ImageInfo *info) NOTHROWS
{
    *info = core->info;
    return TE_Ok;
}
void GLQuadTileNode3::rendererIORunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<IOCallbackOpaque> iocb(static_cast<IOCallbackOpaque *>(opaque));
    if (iocb->owner)
        iocb->owner->rendererIORunnableImpl(iocb.get());
}
void GLQuadTileNode3::rendererIORunnableImpl(IOCallbackOpaque *iocb) NOTHROWS
{
    if (this->checkRequest(iocb->reqId)) {
        switch (iocb->type) {
            case IOCallbackOpaque::UpdateType::CB_Canceled:

                this->readRequests.remove(this->currentRequest);
                this->currentRequest.reset();
                break;

            case IOCallbackOpaque::UpdateType::CB_Completed:
                this->readRequestComplete = Platform_systime_millis();
                this->readRequestElapsed = (readRequestComplete - readRequestStart);

                this->state = State::RESOLVED;
                this->resetFadeTimer();
#if MIPMAP_ENABLED
                glBindTexture(GL_TEXTURE_2D, this->texture_->getTexId());
                this->texture_->setMinFilter(GL_LINEAR_MIPMAP_NEAREST);
                glGenerateMipmap(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, 0);
#endif
                // XXX - should be packaged in read request
                this->core->tileReader->getTileVersion(&this->tileVersion, this->currentRequest->level, this->currentRequest->tileColumn,
                                                       this->currentRequest->tileRow);

                this->readRequests.remove(this->currentRequest);
                this->currentRequest.reset();

                break;

            case IOCallbackOpaque::UpdateType::CB_Error:
                // XXX - should be packaged in read request
                this->core->tileReader->getTileVersion(&this->tileVersion, this->currentRequest->level, this->currentRequest->tileColumn,
                                                       this->currentRequest->tileRow);

                this->readRequests.remove(this->currentRequest);
                this->currentRequest.reset();

                this->state = State::UNRESOLVABLE;
                break;

            case IOCallbackOpaque::UpdateType::CB_Update:
                if (iocb->bitmap.data.get()) {
                    // ensure there is a texture to load to, with correct format/type/size
                    this->validateTexture();
                    // upload the data
                    this->texture->load(Bitmap2(Bitmap2::DataPtr(static_cast<uint8_t *>(iocb->bitmap.data.get()), Memory_leaker_const<uint8_t>), iocb->bitmap.width, iocb->bitmap.height, iocb->bitmap.format));
                    iocb->bitmap.data.reset();
                    // mark that data is received
                    this->receivedUpdate = true;
                } else {
                    // OOM occured
                    // XXX - should be packaged in read request
                    this->core->tileReader->getTileVersion(&this->tileVersion, this->currentRequest->level, this->currentRequest->tileColumn,
                                                           this->currentRequest->tileRow);

                    this->state = State::UNRESOLVABLE;
                }
                break;
        }

        // an update was received, mark the corresponding version as dirty
        if (this->core->surfaceControl)
            this->core->surfaceControl->markDirty(TAK::Engine::Feature::Envelope2(minLng, minLat, 0.0, maxLng, maxLat, 0.0), false);
    }
    Lock lock(this->queuedCallbacksMutex);

    for (auto iter = this->queuedCallbacks.begin(); iter != this->queuedCallbacks.end(); ++iter) {
        if (iocb == *iter) {
            queuedCallbacks.erase(iter);
            break;
        }
    }
}
void GLQuadTileNode3::queueGLCallback(IOCallbackOpaque *iocb)
{
    Lock lock(this->queuedCallbacksMutex);
    this->queuedCallbacks.push_back(iocb);
    this->core->context.queueEvent(rendererIORunnable, std::unique_ptr<void, void(*)(const void *)>(iocb, Memory_leaker_const<void>));
}
bool GLQuadTileNode3::checkRequest(const int id) const NOTHROWS
{
    return (this->currentRequest && this->currentRequest->id == id);
}
void GLQuadTileNode3::requestCreated(const int id) NOTHROWS
{}
void GLQuadTileNode3::requestStarted(const int id) NOTHROWS
{
    std::shared_ptr<TileReader2::ReadRequest> cur(this->currentRequest);
#if 0
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestStarted(id=%d), currentRequest=%d)", toString(false), id, (cur ? cur->id : 0));
    }
#endif
}
void GLQuadTileNode3::requestUpdate(const int id, const Bitmap2 &data) NOTHROWS
{
    std::shared_ptr<TileReader2::ReadRequest> cur(this->currentRequest);
#if 0
    if (DEBUG_QUADTILE) {
        Logger_log(TELL_Debug, "%s requestUpdate(id=%d), currentRequest=%d)", toString(false), id, (cur ? cur->id : 0));
    }
#endif
    const bool curidvalid = !!cur;
    const int curid = curidvalid ? cur->id : 0;
    if (!curidvalid || curid != id)
        return;

    TAKErr code(TE_Ok);
    TE_BEGIN_TRAP()
    {
        queueGLCallback(new IOCallbackOpaque(*this, curid, data));
    }
    TE_END_TRAP(code);
}
void GLQuadTileNode3::requestCompleted(const int id) NOTHROWS
{
    std::shared_ptr<TileReader2::ReadRequest> cur(this->currentRequest);
#if 0
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestCompleted(id=%d), currentRequest=%d)", toString(false), id,
                        (cur ? cur->id : 0));
    }
#endif
    const int curId = (cur ? cur->id : 0);
    if (!cur || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Completed, *this, curId));
}
void GLQuadTileNode3::requestCanceled(const int id) NOTHROWS
{
    std::shared_ptr<TileReader2::ReadRequest> cur(this->currentRequest);
#if 0
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestCanceled(id=%d), currentRequest=%d)", toString(false), id,
                         (cur ? cur->id : 0));
    }
#endif
    const int curId = (cur ? cur->id : 0);
    if (!cur || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Canceled, *this, curId));
}
void GLQuadTileNode3::requestError(const int id, const TAKErr code, const char *msg) NOTHROWS
{
    std::shared_ptr<TileReader2::ReadRequest> cur(this->currentRequest);
#if 0
    if (DEBUG_QUADTILE) {
        Util::Logger_log(LogLevel::TELL_Debug, "%s requestError(id=%d), currentRequest=%d)", toString(false), id, (cur ? cur->id : 0));
        Util::Logger_log(LogLevel::TELL_Error, "asynchronous read error id = %d  code = %d (%s)", id, code, msg ? msg : "no message");
    }
#endif
    const int curId = (cur ? cur->id : 0);
    if (!cur || curId != id)
        return;

    queueGLCallback(new IOCallbackOpaque(IOCallbackOpaque::UpdateType::CB_Error, *this, curId));
}

TAKErr GLQuadTileNode3::create(GLMapRenderable2Ptr &value,
                               RenderContext& ctx,
                               const ImageInfo& info,
                               const TileReaderFactory2Options& readerOpts,
                               const GLQuadTileNode2::Options& opts,
                               const GLQuadTileNode2::Initializer& init) NOTHROWS
{
    std::unique_ptr<GLQuadTileNode3> root(new(std::nothrow) GLQuadTileNode3(ctx, info, readerOpts, opts, init));
    if (!root)
        return TE_OutOfMemory;
    if (!root->core)
        return TE_Err;
    TE_CHECKRETURN_CODE(root->core->initResult);

    value = GLMapRenderable2Ptr(root.release(), Memory_deleter_const<GLMapRenderable2, GLQuadTileNode3>);
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Raster::TileReader::GLQuadTileNode3_create(GLMapRenderable2Ptr &value,
                                                                         RenderContext& ctx,
                                                                         const ImageInfo& info,
                                                                         const TileReaderFactory2Options& readerOpts,
                                                                         const GLQuadTileNode2::Options& opts,
                                                                         const GLQuadTileNode2::Initializer& init) NOTHROWS
{
    return GLQuadTileNode3::create(value, ctx, info, readerOpts, opts, init);
}
#endif