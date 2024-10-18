#ifdef MSVC
#include "renderer/raster/tilereader/NodeCore.h"

#include "port/Platform.h"
#include "raster/DatasetDescriptor.h"
#include "renderer/core/GLGlobeBase.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"
#include "util/Error.h"
#include "util/MathUtils.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::TileReader;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define POLE_LATITUDE_LIMIT_EPISLON 0.00001

NodeCore::NodeCore(RenderContext& ctx_,
        const ImageInfo &info_,
        const GLQuadTileNode2::Initializer& init_,
        const Util::TAKErr code_,
        const std::shared_ptr<TileReader2> &reader_,
        DatasetProjection2Ptr&& imprecise_,
        DatasetProjection2Ptr&& precise_,
        const std::shared_ptr<TileReader2::AsynchronousIO> &asyncio_,
        const GLQuadTileNode2::Options& opts_) NOTHROWS :
    context(ctx_),
    init(init_),
    tileReader(std::move(reader_)),
    imprecise(std::move(imprecise_)),
    precise(std::move(precise_)),
    info(info_),
    options(opts_),
    requestPrioritizer(opts_),
    vertexResolver(nullptr, nullptr),
    cacheControl(nullptr),
    surfaceControl(nullptr),
    textureCache(nullptr)
{
    do {
        if (!tileReader || !imprecise) {
            initResult = TE_InvalidArg;
            break;
        }


        debugDrawEnabled = !!ConfigOptions_getIntOptionOrDefault("imagery.debug-draw-enabled", 0);
        fadeTimerLimit = ConfigOptions_getIntOptionOrDefault("glquadtilenode2.fade-timer-limit", 0);

        color = 0xFFFFFFFF;
        colorR = 1.f;
        colorG = 1.f;
        colorB = 1.f;
        colorA = 1.f;

        //this.releasables = new LinkedList<Releasable>();

        loadingTextureEnabled = false;

        textureBorrowEnabled = !!ConfigOptions_getIntOptionOrDefault("imagery.texture-borrow", 1);
        textureCopyEnabled = !!ConfigOptions_getIntOptionOrDefault("imagery.texture-copy", 1);
        textureCache = options.textureCache;

        int64_t width, height;
        initResult = tileReader->getWidth(&width);
        TE_CHECKBREAK_CODE(initResult);
        initResult = tileReader->getHeight(&height);
        TE_CHECKBREAK_CODE(initResult);

        info.width = (std::size_t)width;
        info.height = (std::size_t)height;

        initResult = tileReader->getTileWidth(&nominalTileWidth);
        TE_CHECKBREAK_CODE(initResult);
        initResult = tileReader->getTileHeight(&nominalTileHeight);
        TE_CHECKBREAK_CODE(initResult);

        initResult = tileReader->getMaximumNumResolutionLevels(&numResolutionLevels);
        TE_CHECKBREAK_CODE(initResult);

        initResult = tileReader->isMultiResolution(&isMultiResolution);
        TE_CHECKBREAK_CODE(initResult);

        initResult = imprecise->imageToGround(&info.upperLeft, Point2<double>(0, 0));
        TE_CHECKBREAK_CODE(initResult);
        initResult = imprecise->imageToGround(&info.upperRight, Point2<double>((double)info.width - 1, 0));
        TE_CHECKBREAK_CODE(initResult);
        initResult = imprecise->imageToGround(&info.lowerRight, Point2<double>((double)info.width - 1, (double)info.height - 1));
        TE_CHECKBREAK_CODE(initResult);
        initResult = imprecise->imageToGround(&info.lowerLeft, Point2<double>(0, (double)info.height - 1));
        TE_CHECKBREAK_CODE(initResult);

        // if dataset bounds cross poles (e.g. Google "Flat Projection"
        // tile server), clip the bounds inset a very small amount from the
        // poles
        const double minLat = MathUtils_min(info.upperLeft.latitude, info.upperRight.latitude, info.lowerRight.latitude, info.lowerLeft.latitude);
        const double maxLat = MathUtils_max(info.upperLeft.latitude, info.upperRight.latitude, info.lowerRight.latitude, info.lowerLeft.latitude);

        if (minLat < -90.0 || maxLat > 90.0) {
            const double minLatLimit = -90.0 + POLE_LATITUDE_LIMIT_EPISLON;
            const double maxLatLimit = 90.0 - POLE_LATITUDE_LIMIT_EPISLON;
            info.upperLeft.latitude = MathUtils_clamp(info.upperLeft.latitude, minLatLimit, maxLatLimit);
            info.upperRight.latitude = MathUtils_clamp(info.upperRight.latitude, minLatLimit, maxLatLimit);
            info.lowerRight.latitude = MathUtils_clamp(info.lowerRight.latitude, minLatLimit, maxLatLimit);
            info.lowerLeft.latitude = MathUtils_clamp(info.lowerLeft.latitude, minLatLimit, maxLatLimit);
        }


//        if (minLng < -180 && maxLng > -180)
//            this.unwrap = 360;
//        else if (maxLng > 180 && minLng < 180)
//            this.unwrap = -360;
//        else
//            this.unwrap = 0;


        if(TE_ISNAN(info.maxGsd)) {
            info.maxGsd = atakmap::raster::DatasetDescriptor::computeGSD(
                (unsigned long)info.width,
                (unsigned long)info.height,
                info.upperLeft,
                info.upperRight,
                info.lowerRight,
                info.lowerLeft);
        }

        frameBufferHandle = GL_NONE;
        depthBufferHandle = GL_NONE;

        minFilter = GL_LINEAR;
        magFilter = GL_LINEAR;

        //this.releasables.add(this.imprecise);
        //if (precise)
        //    this.releasables.add(this.precise);

        //this.drawROI = new RectD[]{ new RectD(), new RectD() };
        drawPumpHemi = -1;

        progressiveLoading = false;

        disposed = false;

        asyncio = asyncio_;
        if (!asyncio)
            asyncio = std::move(std::unique_ptr<TileReader2::AsynchronousIO, void(*)(const TileReader2::AsynchronousIO*)>(TileReader2_getMasterIOThread(), Memory_leaker_const<TileReader2::AsynchronousIO>));

        {
            TAK::Engine::Core::Control *ctrl = nullptr;
            tileReader->getControl(&ctrl, TileCacheControl_getType());
            if (ctrl && ctrl->value) {
                cacheControl = static_cast<TileCacheControl*>(ctrl->value);
                cacheControl->setOnTileUpdateListener(this);
            }
        }

        //this.clientControl = this.tileReader.getControl(TileClientControl.class);

        initResult = NodeContextResources_get(resources, context);
        TE_CHECKBREAK_CODE(initResult);
    } while (false);
}
NodeCore::~NodeCore() NOTHROWS
{
    if(disposed)
        return;

    if (asyncio && tileReader) {
        asyncio->setReadRequestPrioritizer(*tileReader, ReadRequestPrioritizerPtr(nullptr, nullptr));
    }

    //this.init.dispose(this.initResult);
    
    if(this->cacheControl)
        this->cacheControl->setOnTileUpdateListener(nullptr);

    disposed = true;
}

void NodeCore::onTileUpdated(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS
{
    {
        Lock lock(updatedTiles.mutex);
        updatedTiles.write.insert(Point2<std::size_t>(x, y, level));
    }
    if (surfaceControl)
        surfaceControl->markDirty();
}

void NodeCore::refreshUpdateList() NOTHROWS
{
    Lock lock(updatedTiles.mutex);
    updatedTiles.value = updatedTiles.write;
    updatedTiles.write.clear();
}
void NodeCore::prioritize(const GeoPoint2 &camlocLLA) NOTHROWS
{
    Point2<double> camlocIMG;
    imprecise->groundToImage(&camlocIMG, camlocLLA);
    if (requestPrioritizer.focus_x_ != (int64_t)camlocIMG.x || requestPrioritizer.focus_y_ != (int64_t)camlocIMG.y) {
        requestPrioritizer.update((int64_t)camlocIMG.x, (int64_t)camlocIMG.y, nullptr, 0u);

        // dispatch the new prioritizer
        asyncio->setReadRequestPrioritizer(*tileReader, ReadRequestPrioritizerPtr(new TileReadRequestPrioritizer(requestPrioritizer), Memory_deleter_const<TileReader2::ReadRequestPrioritizer, TileReadRequestPrioritizer>));
    }
    if(cacheControl)
        cacheControl->prioritize(camlocLLA);
}


TAKErr TAK::Engine::Renderer::Raster::TileReader::NodeCore_create(NodeCorePtr &value, RenderContext &ctx, const ImageInfo &info, const TileReaderFactory2Options &readerOpts_, const GLQuadTileNode2::Options &opts_, bool throwOnReaderFailedInit, const GLQuadTileNode2::Initializer &init) NOTHROWS
{
    TAKErr code(TE_Ok);
    GLQuadTileNode2::Options opts(opts_);
    std::shared_ptr<TileReader2> reader;
    DatasetProjection2Ptr imprecise(nullptr, nullptr);
    DatasetProjection2Ptr precise(nullptr, nullptr);
    TileReaderFactory2Options readerOpts(readerOpts_);
    code = init.init(reader, imprecise, precise, &info, readerOpts);
    if(!throwOnReaderFailedInit) {
        TE_CHECKRETURN_CODE(code);
    }
    
    if (reader)
        reader->isMultiResolution(&opts.progressiveLoad);

    value = NodeCorePtr(
        new NodeCore(ctx, info, init, code, reader, std::move(imprecise), std::move(precise), readerOpts.asyncIO, opts),
        Memory_deleter_const<NodeCore>
    );

    return code;
}
#endif