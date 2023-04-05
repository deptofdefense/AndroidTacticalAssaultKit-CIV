#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_VERTEXRESOLVER_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_VERTEXRESOLVER_H_INCLUDED

#include "port/Platform.h"
#include "raster/DatasetProjection2.h"
#include "renderer/core/GLGlobeBase.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    class GLQuadTileNode3;

                    class VertexResolver
                    {
                    private :
                        VertexResolver(TAK::Engine::Raster::DatasetProjection2 &i2g) NOTHROWS;
                    private :
                        virtual void beginDraw(const Core::GLGlobeBase &view) NOTHROWS;
                        virtual void endDraw(const Core::GLGlobeBase &view) NOTHROWS;

                        /**
                         * Signals that vertex resolution will commence for the specified node.
                         *
                         * <P>The invocation of this method must ALWAYS be followed by a
                         * subsequent invocation of {@link #endNode(GLQuadTileNode2)} with the same
                         * <code>GLTileNode2</code> instance.
                         *
                         * @param node  A node
                         */
                        virtual void beginNode(const GLQuadTileNode3 &node) NOTHROWS;

                        /**
                         * Signals that vertex resolution has completed for the specified node.
                         *
                         * <P>The invocation of this method should ALWAYS follow a previous
                         * invocation of {@link #beginNode(GLQuadTileNode2)}.
                         *
                         * @param node  A node
                         */
                        virtual void endNode(const GLQuadTileNode3 &node) NOTHROWS;

                        /**
                         * Projects the specified image space coordinate.
                         *
                         * @param view      The view
                         * @param imgSrcX   The image x-coordinate
                         * @param imgSrcY   The image x-coordinate
                         * @param vert       Returns the computed coordinate
                         */
                        virtual Util::TAKErr project(TAK::Engine::Core::GeoPoint2 *value, bool *resolved, const int64_t imgSrcX, const int64_t imgSrcY) NOTHROWS;
                    private :
                        TAK::Engine::Raster::DatasetProjection2 &i2g;

                        friend class GLQuadTileNode3;
                        friend class NodeCore;
                        friend class PreciseVertexResolver;
                    };

                    typedef std::unique_ptr<VertexResolver, void(*)(const VertexResolver *)> VertexResolverPtr;
                }
            }
        }
    }
}
#endif
