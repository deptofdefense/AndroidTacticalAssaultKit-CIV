#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_NODECONTEXTRESOURCES_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_NODECONTEXTRESOURCES_H_INCLUDED

#include "core/RenderContext.h"
#include "port/Platform.h"
#include "renderer/GL.h"
#include "renderer/core/GLGlobeBase.h"
#include "util/MemBuffer2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    class NodeContextResources
                    {
                    public :
                        NodeContextResources() NOTHROWS;
                    public :
                        bool isUniformGrid(const std::size_t gridWidth, const std::size_t gridHeight) const NOTHROWS;
                        GLuint getUniformGriddedTexCoords(const std::size_t gridSize) NOTHROWS;
                        GLuint getUniformGriddedIndices(const std::size_t gridSize) NOTHROWS;
                        GLuint discardBuffer(const GLuint id) NOTHROWS;
                        GLuint genBuffer() NOTHROWS;
                        void deleteBuffers() NOTHROWS;
                    private :
#define GLQuadTileNode3_MAX_GRID_SIZE 32
                        GLuint uniformGriddedTexCoords[GLQuadTileNode3_MAX_GRID_SIZE];
                        GLuint uniformGriddedIndices[GLQuadTileNode3_MAX_GRID_SIZE];
#undef GLQuadTileNode3_MAX_GRID_SIZE
                        Util::MemBuffer2 coordStreamBuffer;
                        Util::MemBuffer2 coordStreamBufferF;
                        Util::MemBuffer2 coordStreamBufferS;
                        GLuint discardBuffers[16];
                        GLsizei numNumDiscardBuffers{ 0u };

                        friend class GLQuadTileNode3;
                    };

                    Util::TAKErr NodeContextResources_get(std::shared_ptr<NodeContextResources>& value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
                }
            }
        }
    }
}
#endif
