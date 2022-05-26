#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_TILEREADREQUESTPRIORITIZER_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_TILEREADREQUESTPRIORITIZER_H_INCLUDED

#include <vector>

#include "math/Rectangle.h"
#include "raster/tilereader/TileReader2.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    class TileReadRequestPrioritizer : public TAK::Engine::Raster::TileReader::TileReader2::ReadRequestPrioritizer
                    {
                    private:
                        TileReadRequestPrioritizer(const GLQuadTileNode2::Options& opts) NOTHROWS;
                    private:
                        void update(const int64_t focusX, const int64_t focusY, const atakmap::math::Rectangle<double>* rois, const std::size_t numRois) NOTHROWS;
                    public:
                        virtual bool compare(const TAK::Engine::Raster::TileReader::TileReader2::ReadRequest& a, const TAK::Engine::Raster::TileReader::TileReader2::ReadRequest& b) NOTHROWS;
                    private:
                        GLQuadTileNode2::Options opts_;
                        int64_t focus_x_;
                        int64_t focus_y_;
                        std::vector<atakmap::math::Rectangle<double>> rois_;
                        friend class GLQuadTileNode2;
                    };
                }
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_RASTER_TILEREADER_TILEREADREQUESTPRIORITIZER_H_INCLUDED
