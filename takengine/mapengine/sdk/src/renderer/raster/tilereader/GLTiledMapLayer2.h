#pragma once

#include "util/Error.h"
#include "port/Platform.h"
#include "core/GeoPoint2.h"
#include "math/Rectangle.h"
#include "renderer/core/GLMapView2.h"
#include "raster/DatasetProjection2.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {

                    Util::TAKErr GLTiledMapLayer2_getRasterROI2(atakmap::math::Rectangle<double> (&rois)[2], size_t *numROIs,
                                                                const Renderer::Core::GLMapView2 &view, int64_t rasterWidth,
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
