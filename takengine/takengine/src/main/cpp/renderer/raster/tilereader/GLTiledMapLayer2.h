#pragma once

#include "util/Error.h"
#include "port/Platform.h"
#include "core/GeoPoint2.h"
#include "math/Rectangle.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/DatasetProjection2.h"
#include "raster/tilereader/TileReader2.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/raster/GLMapLayer2.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {

                    class ENGINE_API GLTiledMapLayer2 : public TAK::Engine::Renderer::Raster::GLMapLayer2
                    {
                    public:
                        GLTiledMapLayer2(const atakmap::raster::ImageDatasetDescriptor & desc) NOTHROWS;
                        GLTiledMapLayer2(const atakmap::raster::ImageDatasetDescriptor &desc, TAK::Engine::Raster::TileReader::TileReader2Ptr &&prealloced) NOTHROWS;
                        ~GLTiledMapLayer2() NOTHROWS;
                    public : // GLMapLayer2
                        virtual const char *getLayerUri() const NOTHROWS;
                        virtual const atakmap::raster::DatasetDescriptor *getInfo() const NOTHROWS;
                        virtual Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS;
                    public : // GLMapRenderable2
                        void draw(const Core::GLGlobeBase &view, const int renderPass) NOTHROWS;
                        void release() NOTHROWS;
                        int getRenderPass() NOTHROWS;
                        void start() NOTHROWS;
                        void stop() NOTHROWS;
                    private :
                        class ColorControlImpl;
                    private :
                        atakmap::raster::DatasetDescriptorUniquePtr desc;
                        GLQuadTileNode2Ptr impl;
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2> prealloced;
                        bool initialized;
                        std::map<std::string, void *> controls_;
                        std::unique_ptr<ColorControlImpl> color_control_;
                    };

                    Util::TAKErr GLTiledMapLayer2_getRasterROI2(atakmap::math::Rectangle<double> (&rois)[2], size_t *numROIs,
                                                                const Renderer::Core::GLGlobeBase &view, int64_t rasterWidth,
                                                                int64_t rasterHeight, const TAK::Engine::Raster::DatasetProjection2 &proj,
                                                                const TAK::Engine::Core::GeoPoint2 &ulG_R,
                                                                const TAK::Engine::Core::GeoPoint2 &urG_R,
                                                                const TAK::Engine::Core::GeoPoint2 &lrG_R,
                                                                const TAK::Engine::Core::GeoPoint2 &llG_R,
                                                                double unwrap, double padding);

                }
            }
        }
    }
}
