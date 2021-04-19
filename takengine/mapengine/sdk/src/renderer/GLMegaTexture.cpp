#include "renderer/GLMegaTexture.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

GLMegaTexture::GLMegaTexture(const std::size_t width_, const std::size_t height_, const std::size_t tileSize_, const bool hasAlpha_) NOTHROWS :
    width(width_),
    height(height_),
    tileSize(tileSize_),
    hasAlpha(hasAlpha_)
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
GLuint GLMegaTexture::getTile(const std::size_t level, const std::size_t column, const std::size_t row) const NOTHROWS
{
    TileIndex key;
    key.level = level;
    key.column = column;
    key.row = row;
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
TAKErr GLMegaTexture::bindTile(const std::size_t level, const std::size_t column, const std::size_t row) NOTHROWS
{
    TileIndex key;
    key.level = level;
    key.column = column;
    key.row = row;
    auto entry = tiles.find(key);
    if (entry != tiles.end()) {
        entry->second.bind();
    } else {
        GLOffscreenFramebuffer tile;
        if(!pool.empty()) {
            tile = pool[pool.size() - 1u];
            pool.pop_back();
        } else {
            GLOffscreenFramebuffer::Options opts;
            opts.colorType = GL_UNSIGNED_BYTE;
            if(hasAlpha)
                opts.colorFormat = GL_RGBA;
            else
                opts.colorFormat = GL_RGB;
            
            TAKErr code = GLOffscreenFramebuffer_create(&tile, (int)tileSize, (int)tileSize, opts);
            TE_CHECKRETURN_CODE(code);
        }
        tiles[key] = tile;
        tile.bind();
    }

    return TE_Ok;
}
TAKErr GLMegaTexture::setTile(const GLuint texid, const std::size_t level, const std::size_t column, const std::size_t row) NOTHROWS
{
    struct GLFBOGuard {
        GLFBOGuard() { glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint*)&currentFbo); }
        ~GLFBOGuard() { glBindFramebuffer(GL_FRAMEBUFFER, currentFbo); }
        GLuint currentFbo = GL_NONE;
    };

    GLFBOGuard fboGuard;

    TileIndex key;
    key.level = level;
    key.column = column;
    key.row = row;

    GLOffscreenFramebuffer tile;
    tile.width = (int)width;
    tile.height = (int)height;
    tile.textureWidth = (int)width;
    tile.textureHeight = (int)height;
    tile.colorTexture = texid;

    bool fboCreated = false;
    do {
        // clear any pending errors
        while (glGetError() != GL_NO_ERROR)
            ;

        glGenFramebuffers(1, &tile.handle);

        GLuint depthBuffer;
        glGenRenderbuffers(1, &depthBuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
        glRenderbufferStorage(GL_RENDERBUFFER,
            GL_DEPTH_COMPONENT24,
            tile.textureWidth, tile.textureHeight);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        // bind the FBO and set all texture attachments
        glBindFramebuffer(GL_FRAMEBUFFER, tile.handle);

        // clear any pending errors
        while (glGetError() != GL_NO_ERROR)
            ;

        glFramebufferTexture2D(GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D, tile.colorTexture, 0);

        // XXX - observing hard crash following bind of "complete"
        //       FBO on SM-T230NU. reported error is 1280 (invalid
        //       enum) on glFramebufferTexture2D. I have tried using
        //       the color-renderable formats required by GLES 2.0
        //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
        //       the same outcome.
        if (glGetError() != GL_NO_ERROR)
            break;

        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, tile.handle);
        if (glGetError() != GL_NO_ERROR)
            break;

        int fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (fboStatus != GL_FRAMEBUFFER_COMPLETE)
            break;

        // if there was already a tile, send it to the pool
        auto entry = tiles.find(key);
        if (entry != tiles.end())
            pool.push_back(entry->second);

        // set the new tile
        tiles[key] = tile;

        return TE_Ok;
    } while (false);

    return TE_Err;
}
void GLMegaTexture::clear() NOTHROWS
{
    if(tiles.empty())
        return;
    if (pool.capacity() < tiles.size())
        pool.reserve(tiles.size());
    const std::size_t limit = tiles.size();
    for (auto it = tiles.begin(); it != tiles.end(); it++) {
        if(pool.size() < limit)
            pool.push_back(it->second);
        else
            GLOffscreenFramebuffer_release(it->second);
    }
    tiles.clear();
}
void GLMegaTexture::release() NOTHROWS
{
    for (auto it = tiles.begin(); it != tiles.end(); it++)
        GLOffscreenFramebuffer_release(it->second);
    tiles.clear();
    for (auto it = pool.begin(); it != pool.end(); it++)
        GLOffscreenFramebuffer_release(*it);
    pool.clear();
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
        return true;
}
