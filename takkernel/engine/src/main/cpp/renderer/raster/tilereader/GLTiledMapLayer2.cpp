#include "renderer/raster/tilereader/GLTiledMapLayer2.h"

#include <algorithm>
#include "util/MathUtils.h"

using namespace TAK::Engine::Renderer::Raster::TileReader;

using namespace TAK::Engine;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Raster::TileReader;
using namespace TAK::Engine::Renderer::Core;

using atakmap::math::Rectangle;

namespace {

    /*
     * Java Double.compare():
     * 0 if equal, < 0 when d1 < d2, >0 when d1 > d2
     * NaN always equal to itself and greater than any other double
     * 0.0d  >  -0.0d
     */
    int Double_compare(double d1, double d2)
    {
        bool d1nan = isnan(d1);
        bool d2nan = isnan(d2);

        if (d1nan && d2nan) {
            return 0;
        } else if (d1nan) {
            return 1;
        } else if (d2nan) {
            return -1;
        } else if (d1 == 0.0 && d2 == -0.0) {
            return 1;
        } else if (d1 == -0.0 && d2 == 0.0) {
            return -1;
        } else if (d1 == d2) {
            return 0;
        } else if (d1 < d2) {
            return -1;
        } else {
            return 1;
        }
    }

    TAKErr getRasterROI2Impl(bool *ret, Rectangle<double> &roi, const Renderer::Core::GLGlobeBase &view, double viewNorth, double viewWest,
                             double viewSouth, double viewEast, int64_t rasterWidth, int64_t rasterHeight, const DatasetProjection2 &proj,
                             GeoPoint2 ulG_R, GeoPoint2 urG_R, GeoPoint2 lrG_R, GeoPoint2 llG_R, double unwrap)
    {
        TAKErr code(TE_Ok);
        double minLat = std::min({ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude});
        double minLng = std::min({ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude});
        double maxLat = std::max({ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude});
        double maxLng = std::max({ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude});

        double u2 = 0.0;
        if (unwrap != 0.0) {
            if (unwrap > 0.0 && (viewWest + viewEast) / 2 > 0.0) {
                u2 = -360.0;
                minLng -= u2;
                maxLng = 180.0;
            } else if (unwrap < 0.0 && (viewWest + viewEast) / 2 < 0.0) {
                u2 = 360.0;
                maxLng -= u2;
                minLng = -180.0;
            }
        }

        double roiMinLat = MathUtils_clamp(minLat, viewSouth, viewNorth);
        double roiMinLng = MathUtils_clamp(minLng, viewWest, viewEast);
        double roiMaxLat = MathUtils_clamp(maxLat, viewSouth, viewNorth);
        double roiMaxLng = MathUtils_clamp(maxLng, viewWest, viewEast);

        if (u2 != 0.0) {
            roiMinLng += u2;
            roiMaxLng += u2;
        }

        if (Double_compare(roiMinLat, roiMaxLat) != 0 && Double_compare(roiMinLng, roiMaxLng) != 0) {
            GeoPoint2 geo;
            geo = GeoPoint2(roiMaxLat, roiMinLng);
            Math::Point2<double> roiUL(0.0, 0.0);
            code = proj.groundToImage(&roiUL, geo);
            TE_CHECKRETURN_CODE(code);
            geo = GeoPoint2(roiMaxLat, roiMaxLng);
            Math::Point2<double> roiUR(0.0, 0.0);
            code = proj.groundToImage(&roiUR, geo);
            TE_CHECKRETURN_CODE(code);
            geo = GeoPoint2(roiMinLat, roiMaxLng);
            Math::Point2<double> roiLR(0.0, 0.0);
            code = proj.groundToImage(&roiLR, geo);
            TE_CHECKRETURN_CODE(code);
            geo = GeoPoint2(roiMinLat, roiMinLng);
            Math::Point2<double> roiLL(0.0, 0.0);
            code = proj.groundToImage(&roiLL, geo);
            TE_CHECKRETURN_CODE(code);

            // XXX - rounding issue ??? observe that blue marble needs one
            //       pixel of padding otherwise tiles disappear at higher zoom
            //       levels around zeams

            const int padding = 1;

            roi.x = MathUtils_clamp(std::min({roiUL.x, roiUR.x, roiLR.x, roiLL.x}) - padding, 0.0, static_cast<double>(rasterWidth));
            roi.y = MathUtils_clamp(std::min({roiUL.y, roiUR.y, roiLR.y, roiLL.y}) - padding, 0.0, static_cast<double>(rasterHeight));
            double right = MathUtils_clamp(std::max({roiUL.x, roiUR.x, roiLR.x, roiLL.x}) + padding, 0.0, static_cast<double>(rasterWidth));
            double bottom = 
                MathUtils_clamp(std::max({roiUL.y, roiUR.y, roiLR.y, roiLL.y}) + padding, 0.0, static_cast<double>(rasterHeight));
            roi.width = right - roi.x;
            roi.height = bottom - roi.y;

            *ret = !(roi.x == right || roi.y == bottom);
        } else {
            *ret = false;
        }
        return code;
    }



    struct PrefetchedInitializer : public GLQuadTileNode2::Initializer
    {
    public :
        PrefetchedInitializer(const std::shared_ptr<TileReader2> &reader_) NOTHROWS :
            reader(reader_)
        {}

        TAKErr init(std::shared_ptr<TileReader2> &value,
                            DatasetProjection2Ptr &imprecise,
                            DatasetProjection2Ptr &precise, const ImageInfo *info,
                            TileReaderFactory2Options &readerOpts) const override
        {
            TAKErr code(TE_Ok);
            if (!reader)
                return TE_Err;
            value = reader;
            code = DatasetProjection2_create(imprecise, info->srid, info->width, info->height, info->upperLeft, info->upperRight, info->lowerRight, info->lowerLeft);
            TE_CHECKRETURN_CODE(code);
            return code;
        }
    private :
        mutable std::shared_ptr<TileReader2> reader;
    };
}

class GLTiledMapLayer2::ColorControlImpl : TAK::Engine::Renderer::Core::ColorControl {
   private:
    GLQuadTileNode2 *impl_;
    TAK::Engine::Core::RenderContext *renderer_;

   public:
    struct SetColorUpdate {
        ColorControlImpl *self;
        int color;

        SetColorUpdate(int color, ColorControlImpl *self) : self(self), color(color) {}

        static void run(void *selfPtr) NOTHROWS {
            SetColorUpdate *update = static_cast<SetColorUpdate *>(selfPtr);
            update->self->setColor(Mode::Replace, update->color);
        }
    };

    ColorControlImpl(GLQuadTileNode2 *impl, TAK::Engine::Core::RenderContext *renderer) NOTHROWS : impl_(impl), renderer_(renderer) {}
    ~ColorControlImpl() NOTHROWS override = default;

    TAK::Engine::Util::TAKErr setColor(const Mode mode, const unsigned int color) NOTHROWS override {
        if (mode != Mode::Replace) return TAK::Engine::Util::TE_InvalidArg;
        if (renderer_->isRenderThread()) {
            impl_->setColor(color);
        } else {
            renderer_->queueEvent(SetColorUpdate::run, std::unique_ptr<void, void (*)(const void *)>(
                                                           new SetColorUpdate(color, this), TAK::Engine::Util::Memory_leaker_const<void>));
        }
        return TAK::Engine::Util::TE_Ok;
    }

    unsigned int getColor() const NOTHROWS override { return 0xFFFFFFFF; }
    Mode getMode() const NOTHROWS override { return Mode::Replace; }
};


GLTiledMapLayer2::GLTiledMapLayer2(const atakmap::raster::ImageDatasetDescriptor& desc_) NOTHROWS :
    GLTiledMapLayer2(desc_, TileReader2Ptr(nullptr, nullptr))
{}
GLTiledMapLayer2::GLTiledMapLayer2(const atakmap::raster::ImageDatasetDescriptor& desc_, TAK::Engine::Raster::TileReader::TileReader2Ptr&& prealloced_) NOTHROWS :
    desc(nullptr, nullptr),
    prealloced(std::move(prealloced_)),
    impl(nullptr, nullptr),
    initialized(false),
    controls_(),
    color_control_(nullptr)
{
    desc_.clone(desc);
}
GLTiledMapLayer2::~GLTiledMapLayer2() NOTHROWS
{}
const char* GLTiledMapLayer2::getLayerUri() const NOTHROWS
{
    return desc->getURI();
}
const atakmap::raster::DatasetDescriptor* GLTiledMapLayer2::getInfo() const NOTHROWS
{
    return desc.get();
}
Util::TAKErr GLTiledMapLayer2::getControl(void** ctrl, const char* type) const NOTHROWS 
{
    if (!type) return TE_InvalidArg;

    auto iter = this->controls_.find(type);
    if (iter == this->controls_.end()) return TE_InvalidArg;

    *ctrl = iter->second;
    return TE_Ok;
}
void GLTiledMapLayer2::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS
{
    if (!initialized) {
        std::shared_ptr<TileReader2> reader(prealloced);
        if (!reader) {
            TileReader2Ptr ureader(nullptr, nullptr);
            if (TileReaderFactory2_create(ureader, getLayerUri()) == TE_Ok)
                reader = std::move(ureader);
        }
        if (reader) {
            const auto& image = static_cast<const atakmap::raster::ImageDatasetDescriptor&>(*desc);
            ImageInfo info;
            info.srid = image.getSpatialReferenceID();
            info.width = image.getWidth();
            info.height = image.getHeight();
            info.maxGsd = image.getMaxResolution();
            info.path = image.getURI();
            info.type = image.getImageryType();
            info.upperLeft.latitude = image.getUpperLeft().latitude;
            info.upperLeft.longitude = image.getUpperLeft().longitude;
            info.upperRight.latitude = image.getUpperRight().latitude;
            info.upperRight.longitude = image.getUpperRight().longitude;
            info.lowerRight.latitude = image.getLowerRight().latitude;
            info.lowerRight.longitude = image.getLowerRight().longitude;
            info.lowerLeft.latitude = image.getLowerLeft().latitude;
            info.lowerLeft.longitude = image.getLowerLeft().longitude;

            TileReaderFactory2Options readerOpts;
            readerOpts.cacheUri = desc->getExtraData("offlineCache");
            GLQuadTileNode2::Options nodeOpts;
            nodeOpts.levelTransitionAdjustment = 0.5;
            // if multi-res, child copy does not resolve; read actual tile
            {
                bool b;
                if (reader->isMultiResolution(&b) == TE_Ok)
                    nodeOpts.childTextureCopyResolvesParent = !b;
            }
            // progressive load if streaming
            nodeOpts.progressiveLoad = image.isRemote() && !view.isScreenshot;
            nodeOpts.textureCopyEnabled = true;
            nodeOpts.textureBorrowEnabled = true;

            PrefetchedInitializer init(reader);
            GLQuadTileNode2::create(impl, &view.context, &info, readerOpts, nodeOpts, init);
        }
        color_control_ = std::make_unique<ColorControlImpl>(impl.get(), &view.context);
        this->controls_["TAK.Engine.Renderer.Core.ColorControl"] = this->color_control_.get();
        initialized = true;
    }

    if (impl)
        impl->draw(view, renderPass);
}
void GLTiledMapLayer2::release() NOTHROWS
{
    if (impl) {
        impl->release();
        impl.reset();
        initialized = false;
    }
}
int GLTiledMapLayer2::getRenderPass() NOTHROWS
{
    return GLGlobeBase::Surface;
}
void GLTiledMapLayer2::start() NOTHROWS
{
    impl->start();
}
void GLTiledMapLayer2::stop() NOTHROWS
{
    impl->stop();
}

TAKErr TAK::Engine::Renderer::Raster::TileReader::GLTiledMapLayer2_getRasterROI2(Rectangle<double> (&rois)[2], size_t *numROIs,
    const Renderer::Core::GLGlobeBase &view, int64_t rasterWidth, int64_t rasterHeight,
    const DatasetProjection2 &proj, const GeoPoint2 &ulG_R,
    const GeoPoint2 &urG_R, const GeoPoint2 &lrG_R,
    const GeoPoint2 &llG_R, double unwrap, double padding)
{
    TAKErr code(TE_Ok);
    int retval = 0;
    bool implRetval;
    if (view.renderPass->crossesIDL) {
        // west of IDL
        code = getRasterROI2Impl(&implRetval, rois[retval], view, view.renderPass->northBound + padding, view.renderPass->westBound - padding, view.renderPass->southBound - padding, 180.0,
                                 rasterWidth, rasterHeight, proj, ulG_R, urG_R, lrG_R, llG_R, unwrap);
        TE_CHECKRETURN_CODE(code);
        if (implRetval) {
            retval++;
        }
        // east of IDL
        code = getRasterROI2Impl(&implRetval, rois[retval], view, view.renderPass->northBound + padding, -180.0, view.renderPass->southBound - padding,
                                 view.renderPass->eastBound + padding, rasterWidth, rasterHeight, proj, ulG_R, urG_R, lrG_R, llG_R, unwrap);
        TE_CHECKRETURN_CODE(code);
        if (implRetval) {
            retval++;
        }
    } else {
        code = getRasterROI2Impl(&implRetval, rois[retval], view, view.renderPass->northBound + padding, view.renderPass->westBound - padding,
                                 view.renderPass->southBound - padding, view.renderPass->eastBound + padding, rasterWidth, rasterHeight, proj, ulG_R, urG_R, lrG_R,
                                 llG_R, unwrap);
        TE_CHECKRETURN_CODE(code);
        if (implRetval) {
            retval++;
        }
    }

    *numROIs = retval;
    return TE_Ok;
}

