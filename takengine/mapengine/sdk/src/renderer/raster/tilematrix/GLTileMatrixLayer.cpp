#include "renderer/raster/tilematrix/GLTileMatrixLayer.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/RendererUtils.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/raster/TileClientControl.h"

#include "port/STLVectorAdapter.h"
#include "util/ConfigOptions.h"
#include "util/Error.h"
#include "util/MathUtils.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Raster::TileMatrix;

class GLTileMatrixLayer::TileClientControlImpl : public TAK::Engine::Renderer::Raster::TileClientControl {
   private:
    int64_t refreshInterval;
    bool manualRefreshRequested;
    bool offlineOnlyMode;
    bool dirty;
    GLTiledLayerCore *core;

   public:
    TileClientControlImpl(GLTiledLayerCore *core)
        : refreshInterval(0), manualRefreshRequested(false), offlineOnlyMode(false), dirty(false), core(core) {}

    void setOfflineOnlyMode(bool offlineOnly) override {}

    bool isOfflineOnlyMode() override { return false; }

    void refreshCache() override { core->requestRefresh(); }

    void setCacheAutoRefreshInterval(int64_t milliseconds) override { core->refreshInterval = static_cast<long>(milliseconds); }

    int64_t getCacheAutoRefreshInterval() override { return core->refreshInterval; }
};

#if 0
// XXX - this is ported from Java, but native adds a "mode".
// This implements "Replace" mode only.
// Disabled until all modes are implemented.

class ColorControlImpl : public TAK::Engine::Renderer::Core::ColorControl {
   private:
    TAK::Engine::Core::MapRenderer *renderer;
    GLTiledLayerCore *core;

   public:
    struct SetColorUpdate {
        ColorControlImpl *self;
        int color;

        SetColorUpdate(int color, ColorControlImpl *self) : self(self), color(color)
        {
        }

        static void run(void *selfPtr)
        { 
            SetColorUpdate *update = static_cast<SetColorUpdate *>(selfPtr);
            update->self->setColor(Mode::Replace, update->color);
        }

    };

    TAK::Engine::Util::TAKErr setColor(const Mode mode, const unsigned int color) {
        if (mode != Mode::Replace)
            return TAK::Engine::Util::TE_InvalidArg;
        if (renderer->isRenderThread()) {
            core->r = atakmap::renderer::Utils::colorExtract(color, atakmap::renderer::Utils::RED) / 255.0f;
            core->g = atakmap::renderer::Utils::colorExtract(color, atakmap::renderer::Utils::GREEN) / 255.0f;
            core->b = atakmap::renderer::Utils::colorExtract(color, atakmap::renderer::Utils::BLUE) / 255.0f;
            core->a = atakmap::renderer::Utils::colorExtract(color, atakmap::renderer::Utils::ALPHA) / 255.0f;
        } else {
            std::unique_ptr<void, void (*)(void *)> colorUpdater(new SetColorUpdate(color, this), TAK::Engine::Util::Memory_void_deleter<SetColorUpdate>);
            renderer->queueEvent(std::move(colorUpdater), SetColorUpdate::run);
        }
        return TAK::Engine::Util::TE_Ok;
    }

    int getColor() {
        return TAK::Engine::Util::MathUtils_clamp((int)(core->a * 255.0f), 0, 255) << 24 |
               TAK::Engine::Util::MathUtils_clamp((int)(core->r * 255.0f), 0, 255) << 16 |
               TAK::Engine::Util::MathUtils_clamp((int)(core->g * 255.0f), 0, 255) << 8 |
               TAK::Engine::Util::MathUtils_clamp((int)(core->b * 255.0f), 0, 255);
    }
    Mode getMode()
    { 
        return Mode::Replace;
    }
};
#endif

GLTileMatrixLayer::GLTileMatrixLayer(TAK::Engine::Core::RenderContext *context, atakmap::raster::DatasetDescriptor *info,
                                     const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix)
    : renderer(nullptr),
      info(info),
      core(matrix,
           info->getURI()),
      zoomLevels(nullptr),
      numZoomLevels(0),
      controls(),
      tileClientControl(nullptr)
{
    commonInit(context);
}

GLTileMatrixLayer::GLTileMatrixLayer(TAK::Engine::Core::MapRenderer *renderer, atakmap::raster::DatasetDescriptor *info,
                                     const std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix)
    : renderer(renderer),
      info(info),
      core(matrix, info->getURI()),
      zoomLevels(nullptr),
      numZoomLevels(0),
      controls(),
      tileClientControl(nullptr)
{

    commonInit(&static_cast<Core::GLMapView2 *>(renderer)->context);
}

GLTileMatrixLayer::~GLTileMatrixLayer() { release(); }

void GLTileMatrixLayer::commonInit(TAK::Engine::Core::RenderContext *context) 
{
    Port::String sval;
    Util::TAKErr err = Util::ConfigOptions_getOption(sval, "imagery.debug-draw-enabled");
    if (err != Util::TE_InvalidArg && sval == "true")
        core.debugDraw = true;
    else
        core.debugDraw = false;

    Core::GLMapRenderGlobals_getTextureCache2(&core.textureCache, *context);
    Core::GLMapRenderGlobals_getBitmapLoader(&core.bitmapLoader, *context);

    core.r = 1.0f;
    core.g = 1.0f;
    core.b = 1.0f;
    core.a = 1.0f;


    // controls
    //  - tile client control
    //  - color control
    //  - others ???

    tileClientControl = std::make_unique<TileClientControlImpl>(&core);
    this->controls["TAK.Engine.Renderer.Raster.TileClientControl"] = tileClientControl.get();
#if 0
    this.controls.add(new ColorControlImpl());
#endif
}


void GLTileMatrixLayer::init() {
    Port::STLVectorAdapter<TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel> zLevels;
    core.matrix->getZoomLevel(zLevels);
    numZoomLevels = zLevels.size();
    zoomLevels = new GLZoomLevel *[numZoomLevels];
    GLZoomLevel *prev = nullptr;
    for (std::size_t i = 0u; i < numZoomLevels; i++) {
        TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel zLevel;
        zLevels.get(zLevel, i);
        zoomLevels[i] = new GLZoomLevel(prev, &core, zLevel);
        prev = zoomLevels[i];
    }
}

void GLTileMatrixLayer::draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS {
    if (zoomLevels == nullptr) {
        init();
    }

    core.drawPump();

    // compute the selection scale

    // account for difference in the tiling DPI and device display
    /*
        final double dpiAdjust = core.service.tileInfo.dpi / (AtakMapView.DENSITY * 240d);
        final double selectScale = (1d / view.drawMapScale) * dpiAdjust * fudge;
    */
    // XXX - fudge select scale???
    const double fudge = 1.667;
    const double selectRes = view.renderPass->drawMapResolution * fudge;

    GLZoomLevel *toDraw = nullptr;
    for (std::size_t i = 0u; i < numZoomLevels; i++) {
        if (zoomLevels[i]->info.resolution >= selectRes)
            toDraw = zoomLevels[i];
        else if (zoomLevels[i]->info.resolution < selectRes)
            break;
    }
    if (toDraw != nullptr) toDraw->draw(view, renderPass);
    if (core.debugDraw) debugDraw(view);

    if (!view.multiPartPass) {
        for (int i = static_cast<int>(numZoomLevels - 1); i >= 0; i--) {
            if (zoomLevels[i] == toDraw) continue;
            zoomLevels[i]->release(true, view.renderPass->renderPump);
        }
    }
}

int GLTileMatrixLayer::getRenderPass() NOTHROWS { return Core::GLMapView2::Surface; }

void GLTileMatrixLayer::start() NOTHROWS {}
void GLTileMatrixLayer::stop() NOTHROWS {}

void GLTileMatrixLayer::debugDraw(const TAK::Engine::Renderer::Core::GLGlobeBase &view) {
    float dbg[8];
    Math::Point2<double> pointD;
    Math::Point2<float> pointF;
    TAK::Engine::Core::GeoPoint2 geo;

    pointD.x = core.fullExtent.minX;
    pointD.y = core.fullExtent.minY;
    pointD.z = 0;
    core.proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[0] = pointF.x;
    dbg[1] = pointF.y;

    pointD.x = core.fullExtent.minX;
    pointD.y = core.fullExtent.maxY;
    pointD.z = 0;
    core.proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[2] = pointF.x;
    dbg[3] = pointF.y;

    pointD.x = core.fullExtent.maxX;
    pointD.y = core.fullExtent.maxY;
    pointD.z = 0;
    core.proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[4] = pointF.x;
    dbg[5] = pointF.y;

    pointD.x = core.fullExtent.maxX;
    pointD.y = core.fullExtent.minY;
    pointD.z = 0;
    core.proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[6] = pointF.x;
    dbg[7] = pointF.y;

    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glColor4f(0.0f, 0.0f, 1.0f, 1.0f);

    fixedPipe->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    fixedPipe->glLineWidth(2.0f);
    fixedPipe->glVertexPointer(2, GL_FLOAT, 0, dbg);
    fixedPipe->glDrawArrays(GL_LINE_LOOP, 0, 4);
    fixedPipe->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
}

void GLTileMatrixLayer::release() NOTHROWS {
    if (zoomLevels != nullptr) {
        for (std::size_t i = 0u; i < numZoomLevels; i++) {
            zoomLevels[i]->release();
            delete zoomLevels[i];
        }
        delete[] zoomLevels;
        zoomLevels = nullptr;
        numZoomLevels = 0;
    }
}

const char *GLTileMatrixLayer::getLayerUri() const NOTHROWS
{
    return info->getURI();
}

const atakmap::raster::DatasetDescriptor *GLTileMatrixLayer::getInfo() const NOTHROWS
{
    return info;
}

TAK::Engine::Util::TAKErr GLTileMatrixLayer::getControl(void **ctrl, const char *type) const NOTHROWS
{
#if 0
    if (this.core.matrix instanceof Controls) {
        T ctrl = ((Controls)this.core.matrix).getControl(clazz);
        if (ctrl != null) return ctrl;
    }

    for (MapControl ctrl : this.controls) {
        if (clazz.isAssignableFrom(ctrl.getClass())) return clazz.cast(ctrl);
    }

    return null;
#endif
    return TAK::Engine::Util::TE_InvalidArg;
}
