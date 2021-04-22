#include "renderer/GLMegaTexture.h"

#include <cassert>

#define MT_POOL_LIMIT (2u*1024u*1024u)

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

GLMegaTexture::GLMegaTexture(const std::size_t width_, const std::size_t height_, const std::size_t tileSize_, const bool hasAlpha_) NOTHROWS :
    width(width_),
    height(height_),
    tileSize(tileSize_),
    hasAlpha(hasAlpha_),
    shareWith(nullptr)
{}
GLMegaTexture::~GLMegaTexture() NOTHROWS
{}
std::size_t GLMegaTexture::getWidth() const NOTHROWS
{
    return width;
}
std::size_t GLMegaTexture::getHeight() const NOTHROWS
{
    return height;
}
std::size_t GLMegaTexture::getTileSize() const NOTHROWS
{
    return tileSize;
}
std::size_t GLMegaTexture::getTileSize(const TileIndex &key) const NOTHROWS
{
    auto entry = tiles.find(key);
    if (entry == tiles.end())
        return tileSize;
    else
        return entry->second.textureWidth;
}
GLuint GLMegaTexture::getTile(const std::size_t level, const std::size_t column, const std::size_t row) const NOTHROWS
{
    TileIndex key;
    key.level = level;
    key.column = column;
    key.row = row;
    return getTile(key);
}
GLuint GLMegaTexture::getTile(const TileIndex &key) const NOTHROWS
{
    auto entry = tiles.find(key);
    if (entry == tiles.end())
        return GL_NONE;
    else
        return entry->second.colorTexture;
}
std::size_t GLMegaTexture::getNumAvailableTiles() const NOTHROWS
{
    return tiles.size();
}
TAKErr GLMegaTexture::getAvailableTiles(TileIndex* value, const std::size_t size) const NOTHROWS
{
    if (size < tiles.size())
        return TE_InvalidArg;
    std::size_t idx = 0;
    for (auto it = tiles.begin(); it != tiles.end(); it++)
        value[idx++] = it->first;
    return TE_Ok;
}
TAKErr GLMegaTexture::bindTile(const std::size_t level, const std::size_t column, const std::size_t row, const std::size_t mip) NOTHROWS
{
    TileIndex key;
    key.level = level;
    key.column = column;
    key.row = row;
    return bindTile(key, mip);
}
TAKErr GLMegaTexture::bindTile(const TileIndex &key, const std::size_t mip) NOTHROWS
{
    auto entry = tiles.find(key);
    if (entry != tiles.end()) {
        do {
            if (sharedTiles) {
                auto shared = sharedTiles->find(entry->second.handle);
                if (shared != sharedTiles->end()) {
                    assert(!!shared->second);
                    // stop using the shared instance
                    shared->second--;
                    // other references to the tile are still held
                    if (shared->second)
                        break;
                    // this megatexture holds the only reference
                    sharedTiles->erase(shared);
                }
            }
            if(entry->second.textureWidth == (tileSize>>mip)) {
                entry->second.bind();
                return TE_Ok;
            } else {
                GLOffscreenFramebuffer_release(entry->second);
            }
        } while(false);
    }

    GLOffscreenFramebuffer tile;
    if(!pool.empty() && !mip) {
        tile = pool[pool.size() - 1u];
        pool.pop_back();
    } else {
        GLOffscreenFramebuffer::Options opts;
        if(hasAlpha) {
            opts.colorFormat = GL_RGBA;
            opts.colorType = GL_UNSIGNED_BYTE;
        } else {
            opts.colorFormat = GL_RGB;
            opts.colorInternalFormat = GL_RGB565;
            opts.colorType = GL_UNSIGNED_SHORT_5_6_5;
        }
        opts.bufferMask = GL_COLOR_BUFFER_BIT;
            
        TAKErr code = GLOffscreenFramebuffer_create(&tile, (int)(tileSize>>mip), (int)(tileSize>>mip), opts);
        TE_CHECKRETURN_CODE(code);

        glBindTexture(GL_TEXTURE_2D, tile.colorTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    }
    tiles[key] = tile;
    tile.bind();

    return TE_Ok;
}
void GLMegaTexture::clear() NOTHROWS
{
    if(tiles.empty())
        return;
    if (pool.capacity() < tiles.size())
        pool.reserve(tiles.size());
    const std::size_t limit = tiles.size();
    for (auto it = tiles.begin(); it != tiles.end(); it++)
        releaseTile(it->first, (pool.size() < limit));
    tiles.clear();
}
void GLMegaTexture::release() NOTHROWS
{
    for (auto it = tiles.begin(); it != tiles.end(); it++)
        releaseTile(it->first, false);
    tiles.clear();
    for (auto it = pool.begin(); it != pool.end(); it++)
        GLOffscreenFramebuffer_release(*it);
    pool.clear();
}
TAKErr GLMegaTexture::shareTile(GLMegaTexture &other, const TileIndex& tile) NOTHROWS
{
    // get the tile
    const auto entry = tiles.find(tile);
    // if the tile isn't available, we can't share
    if (entry == tiles.end())
        return TE_InvalidArg;

    // check if sharing relationship already established
    if ((shareWith && shareWith != &other) || (other.shareWith && other.shareWith != this))
        return TE_IllegalState;
    if (!sharedTiles)
        sharedTiles.reset(new std::map<GLuint, std::size_t>());
    // establish relationship if necessary
    if (!other.shareWith) {
        other,shareWith = this;
        other.sharedTiles = sharedTiles;
    }

    // evict the old entry from other, if there is one
    {
        other.releaseTile(tile, true);
    }
    // share the entry
    {
        other.tiles[tile] = entry->second;
        // bump the shared count
        auto shares = sharedTiles->find(entry->second.handle);
        if (shares != sharedTiles->end()) {
            assert(!!shares->second);
            shares->second++;
        } else {
            // the tile is now shared with two references -- `this` and
            // `shareWith`
            (*sharedTiles)[entry->second.handle] = 2u;
        }
    }

    return TE_Ok;
}
TAKErr GLMegaTexture::transferTile(GLMegaTexture &other, const TileIndex& tile) NOTHROWS
{
    // get the tile
    const auto entry = tiles.find(tile);
    // if the tile isn't available, we can't transfer
    if (entry == tiles.end())
        return TE_InvalidArg;

    // evict the old entry from other, if there is one
    {
        other.releaseTile(tile, true);
        if(!other.pool.empty()) {
            // add to the pool
            pool.push_back(other.pool.back());
            other.pool.pop_back();
        }
    }
    // tranfer the entry
    {
        other.tiles[tile] = entry->second;
        tiles.erase(entry);
    }

    return TE_Ok;
}
TAKErr GLMegaTexture::compressTile(const TileIndex &tile, const std::size_t mip) NOTHROWS
{
    TAKErr code(TE_Ok);
    // get the tile
    const auto entry = tiles.find(tile);
    // if the tile isn't available, we can't compress
    if (entry == tiles.end())
        return TE_InvalidArg;
    const std::size_t compressedSize = tileSize>>mip;
    if(!compressedSize) // invalid mip
        return TE_InvalidArg;
    if(compressedSize >= entry->second.width)
        return TE_Ok; // meets or exceeds user request
    GLOffscreenFramebuffer compressed;
    GLOffscreenFramebuffer::Options opts;
    if(hasAlpha) {
        opts.colorFormat = GL_RGBA;
        opts.colorType = GL_UNSIGNED_BYTE;
    } else {
        opts.colorFormat = GL_RGB;
        opts.colorInternalFormat = GL_RGB565;
        opts.colorType = GL_UNSIGNED_SHORT_5_6_5;
    }
    opts.bufferMask = GL_COLOR_BUFFER_BIT;
    code = GLOffscreenFramebuffer_create(&compressed, (int)compressedSize, (int)compressedSize, opts);
    TE_CHECKRETURN_CODE(code);
#if 0
    // capture current FBO
    GLint currentFbo;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFbo);
    // bind compressed
    compressed.bind(true);
    // XXX - render old tile to compessed

    // restore FBO
    glBindFramebuffer(GL_FRAMEBUFFER, currentFbo;)

    // release old tile
    GLOffscreenFramebuffer_release(entry->second);
    // reset reference to the newly compressed tile
    entry->second = compressed;
#endif
    return code;
}
void GLMegaTexture::releaseTile(const TileIndex& tile, const bool sendToPool) NOTHROWS
{
    auto entry = tiles.find(tile);
    if (entry == tiles.end())
        return;
    if (sharedTiles) {
        auto shares = sharedTiles->find(entry->second.handle);
        const bool isShared = (shares != sharedTiles->end());
        if (isShared) {
            assert(!!shares->second);
            // decrement the share count
            shares->second--;
            // if this megatexture held the last reference, remove the entry
            // and fall through to disposal, else, ownership is relinquished
            // to the other reference holders
            if (!shares->second)
                sharedTiles->erase(shares);
            else
                return;
        }
    }

    const std::size_t textureStorage = (entry->second.textureWidth*entry->second.textureHeight)*(hasAlpha ? 4u : 2u);
    if (sendToPool && (entry->second.textureWidth == tileSize) && (pool.size()*textureStorage) < MT_POOL_LIMIT)
        pool.push_back(entry->second);
    else
        GLOffscreenFramebuffer_release(entry->second);
    tiles.erase(entry);
}

bool GLMegaTexture::TileIndexComp::operator()(const TileIndex& a, const TileIndex& b) const
{
    if (a.level < b.level)
        return true;
    else if (a.level > b.level)
        return false;
    else if (a.column < b.column)
        return true;
    else if (a.column > b.column)
        return false;
    else if (a.row < b.row)
        return true;
    else if (a.row > b.row)
        return false;
    else
        return false;
}
