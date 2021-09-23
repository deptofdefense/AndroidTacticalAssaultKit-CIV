#ifndef TAK_ENGINE_RENDERER_GLMEGATEXTURE_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLMEGATEXTURE_H_INCLUDED

#include <map>
#include <vector>

#include "renderer/GL.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            /**
             * A sparse data structure for multi-LOD tiled texture.
             * 
             * <P>Characteristics
             * <UL>
             *   <LI>Width and height must be a power-of-two multiple of the
             *   tile size.
             *   <LI>Level 0 corresponds to the lowest resolution level. The lowest resolution level is the level past which further subsampling would result in a tile that is less than the defined file size.
             * </UL>
             */
            class ENGINE_API GLMegaTexture
            {
            public :
                struct TileIndex
                {
                    std::size_t level;
                    std::size_t column;
                    std::size_t row;
                };
            private :
                struct TileIndexComp
                {
                    bool operator()(const TileIndex& a, const TileIndex& b) const;
                };
            public :
                GLMegaTexture(const std::size_t width, const std::size_t height, const std::size_t tileSize, const bool hasAlpha) NOTHROWS;
                ~GLMegaTexture() NOTHROWS;
            public :
                /**
                 * Returns the width of the mega texture.
                 * 
                 * @return   The width of the mega texture.
                 */
                std::size_t getWidth() const NOTHROWS;
                /**
                 * Returns the height of the mega texture.
                 * 
                 * @return   The height of the mega texture.
                 */
                std::size_t getHeight() const NOTHROWS;
                /**
                 * Returns the tile size
                 * @return  The tile size
                 */
                std::size_t getTileSize() const NOTHROWS;
                std::size_t getTileSize(const TileIndex &idx) const NOTHROWS;
                /**
                 * Returns the texture ID for the specified tile.
                 * 
                 * @param level     The tile level
                 * @param column    The tile column
                 * @param row       The tile row
                 * 
                 * @return  The texture ID for the tile or `GL_NONE` if no such
                 *          tile exists.
                 */
                GLuint getTile(const std::size_t level, const std::size_t column, const std::size_t row) const NOTHROWS;
                GLuint getTile(const TileIndex &tile) const NOTHROWS;
                /**
                 * Returns the number of populated tiles in the mega texture.
                 * @return  The number of populated tiles
                 */
                std::size_t getNumAvailableTiles() const NOTHROWS;
                /**
                 * @param value Returns the tile available tile indices
                 * @param size  The number of elements in the `value` array
                 */
                Util::TAKErr getAvailableTiles(TileIndex *value, const std::size_t size) const NOTHROWS;
                /**
                 * Binds the associated tile for subsequent draw-to operations.
                 * If the tile at the specified index is not available, a new
                 * one will be created. Following this call, subsequent draw
                 * calls will write to the tile texture.
                 * 
                 * <P>Active frame buffer and viewport may be modified as a
                 * result to this call.
                 * 
                 * @param level     The tile level
                 * @param column    The tile column
                 * @param row       The tile row
                 * 
                 * @return  TE_Ok on success, TE_InvalidArg on bad tile index;
                 *          various codes on other causes of failure
                 */
                Util::TAKErr bindTile(const std::size_t level, const std::size_t column, const std::size_t row, const std::size_t mip = 0u) NOTHROWS;
                Util::TAKErr bindTile(const TileIndex &tile, const std::size_t mip = 0u) NOTHROWS;
                /**
                 * Restores the GL state modified during binding.
                 */
                void unbind() const NOTHROWS;
                /**
                 * Clears all current tile data, but GL resources may not be
                 * released so that they may be re-used in future operations on
                 * this megatexture.
                 */
                void clear() NOTHROWS;
                /**
                 * Clears all current tile data and releases GL resources.
                 */
                void release() NOTHROWS;
                /**
                 * Releases the specified texture
                 * @param tile          The texture index
                 * @param sendToPool    If `true` the texture may be released
                 *                      to a pool for future re-use; if `false`
                 *                      the resources are released immediately.
                 */
                void releaseTile(const TileIndex& tile, const bool sendToPool) NOTHROWS;
                Util::TAKErr shareTile(GLMegaTexture &shareWith, const TileIndex &tile) NOTHROWS;
                Util::TAKErr transferTile(GLMegaTexture &transferTo, const TileIndex &tile) NOTHROWS;
                Util::TAKErr compressTile(const TileIndex &tile, const std::size_t mip) NOTHROWS;

                void getStats(std::size_t *tileStorage, std::size_t *sharedStorage, std::size_t *poolStorage, std::size_t *totalSharedStorage) NOTHROWS
                {
                    const std::size_t pixelSize = (hasAlpha ? 4u : 2u);
                    if(tiles.empty()) {
                        *tileStorage = 0;
                        *sharedStorage = 0;
                    } else {
                        *tileStorage = 0u;
                        *sharedStorage = 0u;
                        for (auto it = tiles.begin(); it != tiles.end(); it++) {
                            const std::size_t sz = (it->second.textureWidth*it->second.textureHeight)*pixelSize;
                            if(sharedTiles) {
                                auto e = sharedTiles->find(it->second.handle);
                                if (e != sharedTiles->end())
                                    (*sharedStorage) += sz;
                                else
                                    (*tileStorage) += sz;
                            } else {
                                (*tileStorage) += sz;
                            }
                        }
                    }
                    *poolStorage = 0u;
                    for(auto p : pool) {
                        *poolStorage += (p.textureWidth*p.textureHeight)*pixelSize;
                    }
                    *totalSharedStorage = 0;
                    if(sharedTiles) {
                        for(auto it = sharedTiles->begin(); it != sharedTiles->end(); it++) {
                            std::size_t sz = 0u;
                            do {
                                for (auto t = tiles.begin(); t != tiles.end(); t++) {
                                    if (t->second.handle == it->first) {
                                        sz += (t->second.textureWidth*t->second.textureHeight)*pixelSize;
                                        break;
                                    }
                                }
                                if(sz) break;
                                if(shareWith) {
                                    for (auto t = shareWith->tiles.begin(); t != shareWith->tiles.end(); t++) {
                                        if (t->second.handle == it->first) {
                                            sz += (t->second.textureWidth *
                                                   t->second.textureHeight) * pixelSize;
                                            break;
                                        }
                                    }
                                }
                                if(sz) break;
                            } while(false);
                            if(!sz)
                                sz = (tileSize*tileSize)*pixelSize;
                            *totalSharedStorage += sz;
                        }
                    }
                }

            private :
                std::size_t tileSize;
                std::size_t width;
                std::size_t height;
                bool hasAlpha;
                std::map<TileIndex, GLOffscreenFramebuffer, TileIndexComp> tiles;
                std::vector<GLOffscreenFramebuffer> pool;
                std::shared_ptr<std::map<GLuint, std::size_t>> sharedTiles;
                const GLMegaTexture *shareWith;
            };
        }
    }
}

#endif
