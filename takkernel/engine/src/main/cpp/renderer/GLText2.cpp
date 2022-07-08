#include "renderer/GL.h"
#include "renderer/GLText2.h"
#include <cfloat>
#include <assert.h>
#include <limits>
#include "core/AtakMapView.h"
#include "math/Utils.h"
#include "renderer/GLES20FixedPipeline.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "thread/RWMutex.h"
#include "port/StringBuilder.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

/**********************************************************************/
// Private implementation statics

#ifdef MSVC
#define GLTEXT2_CHAR_START 32
#define GLTEXT2_CHAR_END 254
#else
#define GLTEXT2_CHAR_START 32
#define GLTEXT2_CHAR_END 126
#endif
#define GLTEXT2_CHAR_BATCH_SIZE 80
#define GLTEXT2_NUM_COMMON_CHARS ((GLTEXT2_CHAR_END) - (GLTEXT2_CHAR_START) + 1)

#define TE_RED(argb) (((argb)>>16)&0xFF)
#define TE_GREEN(argb) (((argb)>>8)&0xFF)
#define TE_BLUE(argb) ((argb)&0xFF)
#define TE_ALPHA(argb) (((argb)>>24)&0xFF)

namespace TAK {
    namespace Engine {
        namespace Renderer {
            // Exposed for testing
            ENGINE_API TAKErr GLText2_setBidirectionalTextMode(BidirectionalTextMode mode) NOTHROWS;
        }
    }
}

#ifdef _MSC_VER
extern bool TextFormatFactory_getDefault(std::shared_ptr<TextFormatFactory> &value) NOTHROWS;
#endif

namespace {
    std::unique_ptr<atakmap::renderer::GLRenderBatch> SPRITE_BATCH;

    Mutex cacheMutex(TEMT_Recursive);
    Mutex textFormatCacheMutex(TEMT_Recursive);

    struct TextFormatParamsLess {
        bool operator()(const TextFormatParams &a, const TextFormatParams &b) const {

            if(a.size < b.size)
                return true;
            if(a.bold < b.bold)
                return true;
            if(a.italic < b.italic)
                return true;
            if(a.underline < b.underline)
                return true;
            if(a.strikethrough < b.strikethrough)
                return true;
            if(!a.fontName || !b.fontName)
                return a.fontName < b.fontName;
            return strcmp(a.fontName, b.fontName) < 0;
        }
    };
    typedef std::map<std::shared_ptr<TextFormat2>, std::map<const TAK::Engine::Core::RenderContext *, std::unique_ptr<GLText2>>> GLTextCacheMap;
    GLTextCacheMap glTextCache;

    typedef std::map<TextFormatParams, std::shared_ptr<TextFormat2>, TextFormatParamsLess> TextFormatParamsCache;
    TextFormatParamsCache textFormatCache;


    RWMutex tf_factory_mutex_(TERW_Fair);
    std::shared_ptr<TextFormatFactory> tf_factory_;

    TAKErr bufferChar(const std::size_t size,
                      float *texVerts,
                      float *texCoords,
                      uint16_t *texIndices,
                      const float x0, const float y0,
                      const float x1, const float y1,
                      const float z,
                      const float u0, const float v0,
                      const float u1, const float v1) NOTHROWS;

    BidirectionalTextMode getRTLMode() NOTHROWS;
    TAKErr localizeImpl(std::string& dst, const char* text, size_t textLen, BidirectionalTextMode rightToLeftMode) NOTHROWS;
    unsigned int decode(const char* text, size_t len, char uri[5], const std::size_t i, std::size_t& size) NOTHROWS;
    void utf8_reverse(std::string& dst, const char* substr, size_t length) NOTHROWS;
}

TextFormat2::~TextFormat2() NOTHROWS
{}

TextFormatParams::TextFormatParams(const float size_) NOTHROWS :
    TextFormatParams(nullptr, size_)
{}
TextFormatParams::TextFormatParams(const char *name_, const float size_) NOTHROWS :
    fontName(name_),
    size(size_),
    bold(false),
    italic(false),
    underline(false),
    strikethrough(false)
{}

TAKErr TAK::Engine::Renderer::TextFormat2_createDefaultSystemTextFormat(TAK::Engine::Renderer::TextFormat2Ptr& value, float size) NOTHROWS
{
    return TextFormat2_createTextFormat(value, TextFormatParams(size));
}

TAKErr TAK::Engine::Renderer::TextFormat2_createTextFormat(TextFormat2Ptr &value, const TextFormatParams &params) NOTHROWS
{
    {
        ReadLock lock(tf_factory_mutex_);
#ifdef _MSC_VER
        // initialize the GDI+ impl
        if (!tf_factory_) {
            static bool clinit = TextFormatFactory_getDefault(tf_factory_);
        }
#endif
        if (tf_factory_)
            return tf_factory_->createTextFormat(value, params);
    }

    return TE_IllegalState;
}

TAKErr TAK::Engine::Renderer::TextFormat2_setTextFormatFactory(const std::shared_ptr<TAK::Engine::Renderer::TextFormatFactory>& factory) NOTHROWS
{
    {
        WriteLock wlock(tf_factory_mutex_);
        tf_factory_ = factory;

        {
            // the text format factory is changed, all previously cached
            // instances are no longer valid
            Lock lock(cacheMutex);
            glTextCache.clear();
        }
    }
    return TE_Ok;
}

TextFormatFactory::~TextFormatFactory() NOTHROWS
{}

/**********************************************************************/
// Factory method

GLText2 *TAK::Engine::Renderer::GLText2_intern(std::shared_ptr<TextFormat2> textFormat) NOTHROWS
{
    const TAK::Engine::Core::RenderContext *ctx = nullptr;
    return GLText2_intern(*ctx, textFormat);
}
GLText2 *TAK::Engine::Renderer::GLText2_intern(const TAK::Engine::Core::RenderContext &ctx, std::shared_ptr<TextFormat2> textFormat) NOTHROWS
{
    Lock lock(cacheMutex);

    if (!SPRITE_BATCH.get())
        SPRITE_BATCH.reset(new atakmap::renderer::GLRenderBatch(GLTEXT2_CHAR_BATCH_SIZE));

    do {
        auto entry = glTextCache.find(textFormat);
        if(entry == glTextCache.end())
            break;
        auto ctxEntry = entry->second.find(&ctx);
        if(ctxEntry == entry->second.end())
            break;
        return ctxEntry->second.get();
    } while(false);

    GLText2 *customGLText = nullptr;
    std::unique_ptr<GLText2> retval(new GLText2(std::move(TextFormat2Ptr(textFormat.get(), Memory_leaker_const<TextFormat2>))));
    customGLText = retval.get();
    glTextCache[textFormat].insert(std::pair<const TAK::Engine::Core::RenderContext *, std::unique_ptr<GLText2>>(&ctx, std::move(retval)));

    return customGLText;

}
GLText2 *TAK::Engine::Renderer::GLText2_intern(const TextFormatParams &params) NOTHROWS
{
    const TAK::Engine::Core::RenderContext *ctx = nullptr;
    return GLText2_intern(*ctx, params);
}
GLText2 *TAK::Engine::Renderer::GLText2_intern(const TAK::Engine::Core::RenderContext &ctx, const TextFormatParams &params) NOTHROWS
{
#ifndef _MSC_VER
    Lock lock(cacheMutex);
    if(glTextCache.empty())
        return nullptr;

    for(auto &entry : glTextCache) {
        // XXX - other properties
        if(entry.first->getFontSize() == params.size) {
            auto ctxEntry = entry.second.find(&ctx);
            if(ctxEntry != entry.second.end())
                return ctxEntry->second.get();
        }
    }
#endif
    std::shared_ptr<TextFormat2> fmt;
    {
        Lock lock(textFormatCacheMutex);
        auto it = textFormatCache.find(params);
        if(it != textFormatCache.end())
        {
            fmt = it->second;
        }
        if(!fmt)
        {
            TextFormat2Ptr fmt2ptr(nullptr, nullptr);
            if (TextFormat2_createTextFormat(fmt2ptr, params) == TE_Ok)
            {
                fmt = std::move(fmt2ptr);
                textFormatCache[params] = fmt;
            }
        }
    }
    if(fmt)
        return GLText2_intern(ctx, fmt);

    return nullptr;
}

TAKErr TAK::Engine::Renderer::GLText2_invalidateCache() NOTHROWS {

    Lock lock(cacheMutex);

    for (auto& entry : glTextCache) {
        for(auto &ctxEntry : entry.second) {
            GLText2_invalidate(ctxEntry.second.get());
        }
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::GLText2_invalidate(GLText2* glText) NOTHROWS {

    if (!glText)
        return TE_InvalidArg;

    // force rebuild of everything
    glText->glyphAtlas.release();
    for (size_t i = 0; i < GLTEXT2_NUM_COMMON_CHARS; ++i) {
        glText->commonCharTexId[i] = 0;
    }

    return TE_Ok;
}

/**********************************************************************/
// Constructor/destructor (private)


GLText2::GLText2(TextFormat2Ptr &&textFormat_) NOTHROWS :
    textFormat(std::move(textFormat_)),
    glyphAtlas(std::max(256u, (unsigned)(textFormat->getCharHeight()*2u)), true),
    charMaxHeight(textFormat->getCharHeight())
{
    memset(commonCharTexId, 0u, sizeof(commonCharTexId));
}

GLText2::~GLText2()
{}

/**********************************************************************/
// Public Draw methods

TAKErr GLText2::draw(const char *text, const int *colors, const std::size_t colorsCount) NOTHROWS
{
    TAKErr code;

    SPRITE_BATCH->begin();
    code = batch(*SPRITE_BATCH, text, 0.0f, 0.0f, colors, colorsCount);
    SPRITE_BATCH->end();

    return code;
}

TAKErr GLText2::draw(const char *text,
    const int *colors, const std::size_t colorsCount,
    const float scissorX0, const float scissorX1) NOTHROWS
{
    TAKErr code;

    SPRITE_BATCH->begin();
    code = batch(*SPRITE_BATCH, text, 0.0f, 0.0f, colors, colorsCount, scissorX0, scissorX1);
    SPRITE_BATCH->end();

    return code;
}


TAKErr GLText2::draw(const char *text, const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code;
    SPRITE_BATCH->begin();
    code = batch(*SPRITE_BATCH, text, 0.0f, 0.0f, r, g, b, a);
    SPRITE_BATCH->end();

    return code;
}

TAKErr GLText2::draw(const char *text, const float r, const float g, const float b, const float a, const float scissorX0, const float scissorX1) NOTHROWS
{
    TAKErr code;

    SPRITE_BATCH->begin();
    code = batch(*SPRITE_BATCH, text, 0.0f, 0.0f, r, g, b, a, scissorX0,
        scissorX1);
    SPRITE_BATCH->end();

    return code;
}


/**********************************************************************/
// Public Batch methods


TAKErr GLText2::batch(atakmap::renderer::GLRenderBatch &batch,
                      const char *text,
                      const float x, const float y,
                      const int *colors, const std::size_t colorsCount) NOTHROWS
{
    return this->batch(batch.impl, text, x, y, 0.0, colors, colorsCount);
}

TAKErr GLText2::batch(GLRenderBatch2 &batch,
    const char *text,
    const float x, const float y, const float z,
    const int *colors, const std::size_t colorsCount) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::size_t lineNum = 0;
    std::size_t length = strlen(text);
    std::size_t lineStart = 0;
    std::size_t lineChars = 0;
    int c;
    for (std::size_t i = 0; i < length; i++) {
        if (text[i] == '\n') {
            if (lineChars > 0) {
                if (colors != nullptr && colorsCount)
                    c = colors[atakmap::math::clamp<std::size_t>(lineNum, 0, colorsCount - 1)];
                else
                    // White
                    c = -1;

                code = batchImpl(batch,
                    x + 0,
                    y + -textFormat->getBaselineSpacing() * (lineNum),
                    z, 
                    text,
                    lineStart,
                    lineChars,
                    TE_RED(c) / 255.0f,
                    TE_GREEN(c) / 255.0f,
                    TE_BLUE(c) / 255.0f,
                    TE_ALPHA(c) / 255.0f);
                TE_CHECKBREAK_CODE(code);
            }


            lineStart = i + 1;
            lineChars = 0;
            lineNum++;
        }
        else {
            lineChars++;
        }
    }
    TE_CHECKRETURN_CODE(code);

    // flush the line
    if (lineChars > 0) {
        if (colors != nullptr && colorsCount)
            c = colors[atakmap::math::clamp<std::size_t>(lineNum, 0u, colorsCount - 1)];
        else
            c = -1;

        code = batchImpl(batch,
            x + 0,
            y + -textFormat->getBaselineSpacing() * (lineNum),
            z,
            text,
            lineStart,
            lineChars,
            TE_RED(c) / 255.0f,
            TE_GREEN(c) / 255.0f,
            TE_BLUE(c) / 255.0f,
            TE_ALPHA(c) / 255.0f);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLText2::batch(atakmap::renderer::GLRenderBatch &batch,
                      const char *text,
                      const float x, const float y,
                      const float r, const float g, const float b, const float a) NOTHROWS
{
    return this->batch(batch.impl, text, x, y, 0.0, r, g, b, a);
}

TAKErr GLText2::batch(GLRenderBatch2 &batch,
    const char *text,
    const float x, const float y, const float z,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::size_t lineNum = 0;
    std::size_t length = strlen(text);
    std::size_t lineStart = 0;
    std::size_t lineChars = 0;
    for (std::size_t i = 0; i < length; i++) {
        if (text[i] == '\n') {
            if (lineChars > 0) {
                code = batchImpl(batch,
                    x + 0.0f,
                    y + -textFormat->getBaselineSpacing() * (lineNum),
                    z,
                    text,
                    lineStart,
                    lineChars,
                    r, g, b, a);
                TE_CHECKBREAK_CODE(code);
            }

            lineStart = i + 1;
            lineChars = 0;
            lineNum++;
        }
        else {
            lineChars++;
        }
    }
    TE_CHECKRETURN_CODE(code);

    if (lineChars > 0) {
        code = batchImpl(batch,
            x + 0.0f,
            y + -textFormat->getBaselineSpacing() * (lineNum),
            z,
            text,
            lineStart,
            lineChars,
            r, g, b, a);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLText2::batch(atakmap::renderer::GLRenderBatch &batch,
                      const char *text,
                      const float x, const float y,
                      const int *colors, const std::size_t colorsCount,
                      const float scissorX0, const float scissorX1) NOTHROWS
{
    return this->batch(batch.impl, text, x, y, 0.0, colors, colorsCount, scissorX0, scissorX1);
}

TAKErr GLText2::batch(GLRenderBatch2 &batch,
    const char *text,
    const float x, const float y, const float z,
    const int *colors, const std::size_t colorsCount,
    const float scissorX0, const float scissorX1) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::size_t lineNum = 0;
    std::size_t length = strlen(text);
    std::size_t lineStart = 0;
    std::size_t lineChars = 0;
    int c;
    for (std::size_t i = 0; i < length; i++) {
        if (text[i] == '\n') {
            if (lineChars > 0) {
                if (colors != nullptr && colorsCount)
                    c = colors[atakmap::math::clamp<std::size_t>(lineNum, 0, colorsCount - 1)];
                else
                    // White
                    c = -1;

                code = batchImpl(batch,
                    x + 0,
                    y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                    z,
                    text,
                    lineStart,
                    lineChars,
                    TE_RED(c) / 255.0f,
                    TE_GREEN(c) / 255.0f,
                    TE_BLUE(c) / 255.0f,
                    TE_ALPHA(c) / 255.0f,
                    scissorX0, scissorX1);
                TE_CHECKBREAK_CODE(code);
            }


            lineStart = i + 1;
            lineChars = 0;
            lineNum++;
        }
        else {
            lineChars++;
        }
    }
    TE_CHECKRETURN_CODE(code);

    // flush the line
    if (lineChars > 0) {
        if (colors != nullptr && colorsCount)
            c = colors[atakmap::math::clamp<std::size_t>(lineNum, 0u, colorsCount - 1)];
        else
            c = -1;

        code = batchImpl(batch,
            x + 0,
            y + -textFormat->getBaselineSpacing() * (lineNum + 1),
            z,
            text,
            lineStart,
            lineChars,
            TE_RED(c) / 255.0f,
            TE_GREEN(c) / 255.0f,
            TE_BLUE(c) / 255.0f,
            TE_ALPHA(c) / 255.0f,
            scissorX0, scissorX1);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLText2::batch(atakmap::renderer::GLRenderBatch &batch,
                      const char *text,
                      const float x, const float y,
                      const float r, const float g, const float b, const float a,
                      const float scissorX0, const float scissorX1) NOTHROWS
{
    return this->batch(batch.impl, text, x, y, 0.0, r, g, b, a, scissorX0, scissorX1);
}

TAKErr GLText2::batch(GLRenderBatch2 &batch,
    const char *text,
    const float x, const float y, const float z,
    const float r, const float g, const float b, const float a,
    const float scissorX0, const float scissorX1) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::size_t lineNum = 0;
    std::size_t length = strlen(text);
    std::size_t lineStart = 0;
    std::size_t lineChars = 0;
    for (std::size_t i = 0; i < length; i++) {
        if (text[i] == '\n') {
            if (lineChars > 0) {
                code = batchImpl(batch,
                    x + 0.0f,
                    y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                    z,
                    text,
                    lineStart,
                    lineChars,
                    r, g, b, a,
                    scissorX0, scissorX1);
                TE_CHECKBREAK_CODE(code);
            }

            lineStart = i + 1;
            lineChars = 0;
            lineNum++;
        }
        else {
            lineChars++;
        }
    }
    TE_CHECKRETURN_CODE(code);

    if (lineChars > 0) {
        code = batchImpl(batch,
            x + 0.0f,
            y + -textFormat->getBaselineSpacing() * (lineNum + 1),
            z,
            text,
            lineStart,
            lineChars,
            r, g, b, a,
            scissorX0, scissorX1);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}


/**********************************************************************/
// Other Public methods

TextFormat2 &GLText2::getTextFormat() const NOTHROWS
{
    return *textFormat;
}

std::size_t GLText2::getLineCount(const char *text) NOTHROWS
{
    std::size_t numLines = 1;
    size_t len = strlen(text);
    for (size_t i = 0; i < len; i++)
        if (text[i] == '\n')
            numLines++;
    return numLines;
}

/**********************************************************************/
// Private implementation methods

TAKErr GLText2::batchImpl(atakmap::renderer::GLRenderBatch &batch,
                          const float tx, const float ty,
                          const char *text,
                          const std::size_t off, const std::size_t len,
                          const float r, const float g, const float b, const float a) NOTHROWS
{
    return batchImpl(batch, tx, ty, text, off, len, r, g, b, a, 0.0f, FLT_MAX);
}

TAKErr GLText2::batchImpl(GLRenderBatch2 &batch,
    const float tx, const float ty, const float tz,
    const char *text,
    const std::size_t off, const std::size_t len,
    const float r, const float g, const float b, const float a) NOTHROWS
{
    return batchImpl(batch, tx, ty, tz, text, off, len, r, g, b, a, 0.0f, FLT_MAX);
}

TAKErr GLText2::batchImpl(atakmap::renderer::GLRenderBatch &batch,
                          const float tx, const float ty,
                          const char *text,
                          const std::size_t off, const std::size_t len,
                          const float r, const float g, const float b, const float a,
                          const float scissorX0, const float scissorX1) NOTHROWS
{
    return this->batchImpl(batch.impl, tx, ty, 0.0, text, off, len, r, g, b, a, scissorX0, scissorX1);
}

TAKErr GLText2::batchImpl(GLRenderBatch2 &batch,
    const float tx, const float ty, const float tz,
    const char *text,
    const std::size_t off, const std::size_t len,
    const float r, const float g, const float b, const float a,
    const float scissorX0, const float scissorX1) NOTHROWS
{
    TAKErr code(TE_Ok);

    const std::size_t size = (tz == 0.0f) ? 2u : 3u;
#ifdef __ANDROID__
    const float fontPadX = 2;
    const float fontPadY = 2;
#else
    const float fontPadX = 0;
    const float fontPadY = 0;
#endif

    float cellWidth;

    float cellHeight = textFormat->getCharHeight();// + (2*fontPadY);

    float charAdjX; // adjust start X
    float charAdjY = (cellHeight / 2.0f) - fontPadY; // adjust start Y
    charAdjY -= textFormat->getDescent() - fontPadY;

    float letterX, letterY;
    letterX = letterY = 0;

    int64_t key;
    int texId;
    float charWidth;
    auto texSize = (float)glyphAtlas.getTextureSize();

    float u0;
    float v0;
    float u1;
    float v1;

    for (std::size_t i = 0; i < len; i++) { // for each character in string
        char uri[5];
        std::size_t uri_size = 0;
        unsigned int c = decode(text, len, uri, i+off, uri_size);
        assert(uri_size != 0);
        i += uri_size - 1;

        // skip non-printable
        if (c < 32u)
            continue;

        // if common character, use LUT
        if (c >= GLTEXT2_CHAR_START && c <= GLTEXT2_CHAR_END) {
            const std::size_t ccidx = c - GLTEXT2_CHAR_START;
            texId = commonCharTexId[ccidx];
            if (texId == 0) {
                code = loadGlyph(&key, c);
                TE_CHECKBREAK_CODE(code);

                code = glyphAtlas.getTexId(&texId, key);
                TE_CHECKBREAK_CODE(code);

                commonCharWidth[ccidx] = (float)textFormat->getCharWidth(c);
                commonCharTexId[ccidx] = texId;

                atakmap::math::Rectangle<float> uvBounds;
                code = glyphAtlas.getImageRect(&uvBounds, key, true);
                TE_CHECKBREAK_CODE(code);

                commonCharUV[ccidx * 4] = uvBounds.x;
                commonCharUV[ccidx * 4 + 1] = uvBounds.y + uvBounds.height;
                commonCharUV[ccidx * 4 + 2] = uvBounds.x + uvBounds.width;
                commonCharUV[ccidx * 4 + 3] = uvBounds.y;
            }

            charWidth = commonCharWidth[ccidx];

            u0 = commonCharUV[ccidx * 4];
            v0 = commonCharUV[ccidx * 4 + 1];
            u1 = commonCharUV[ccidx * 4 + 2];
            v1 = commonCharUV[ccidx * 4 + 3];
        } else {
            code = glyphAtlas.getTextureKey(&key, uri);
            if (code == TE_InvalidArg) {
                key = 0LL;
                code = TE_Ok;
            }
            TE_CHECKBREAK_CODE(code);
            if (key == 0LL) { // load the glyph
                code = loadGlyph(&key, c);
                TE_CHECKBREAK_CODE(code);
            }

            atakmap::math::Rectangle<float> uvBounds;
            code = glyphAtlas.getImageRect(&uvBounds, key, true);
            TE_CHECKBREAK_CODE(code);

            charWidth = textFormat->getCharWidth(c);
            code = glyphAtlas.getTexId(&texId, key);
            TE_CHECKBREAK_CODE(code);

            u0 = uvBounds.x;
            v0 = (uvBounds.y + uvBounds.height);
            u1 = (uvBounds.x + uvBounds.width);
            v1 = uvBounds.y;
        }

        cellWidth = charWidth;
        charAdjX = (cellWidth / 2.0f) - fontPadX;
        if ((letterX + charWidth) >= scissorX0 && letterX < scissorX1) {
            float x0 = charAdjX + letterX - cellWidth / 2.0f;
            float y0 = charAdjY + letterY - cellHeight / 2.0f;
            float x1 = charAdjX + letterX + cellWidth / 2.0f;
            float y1 = charAdjY + letterY + cellHeight / 2.0f;

            // adjust the vertex and text coordinates to account for
            // scissoring
            if (letterX < scissorX0) {
                u0 += (scissorX0 - x0) / texSize;
                x0 = scissorX0;
            }
            if ((letterX + charWidth) > scissorX1) {
                u1 -= (x1 - scissorX1) / texSize;
                x1 = scissorX1;
            }

            // batch the character
            float trianglesVerts[12u];
            float trianglesTexCoords[8u];
            uint16_t trianglesIndices[6u];
            code = bufferChar(size,
                              trianglesVerts,
                              trianglesTexCoords,
                              trianglesIndices,
                              tx + x0, ty + y0,
                              tx + x1, ty + y1,
                              tz,
                              u0, v0,
                              u1, v1);
            TE_CHECKBREAK_CODE(code);
            batch.batch(texId,
                        GL_TRIANGLES,
                        4u,
                        size,
                        0u, trianglesVerts,
                        0u, trianglesTexCoords,
                        6u,
                        trianglesIndices,
                        r, g, b, a);
        }
        TE_CHECKRETURN_CODE(code);

        // advance X position by scaled character width
        letterX += charWidth;
        if (letterX > scissorX1)
            break;
    }

    return code;
}

TAKErr GLText2::loadGlyph(int64_t *key, const unsigned int c) NOTHROWS
{
    TAKErr code;
    BitmapPtr b(nullptr, nullptr);

    code = textFormat->loadGlyph(b, c);
    // if the glyph fails to load, show unknown character to allow the entire string to render
    if(code != TE_Ok)
        code = textFormat->loadGlyph(b, '?');
    TE_CHECKRETURN_CODE(code);

    char buf[5u];
    if(c < 128u) {
        // 7 bits, emit
        buf[0] = c;
        buf[1] = '\0';
    } else if(c < 0x800) {
        // 11 bits
        buf[0] = 0xC0 | ((c >> 6)&0x1F);
        buf[1] = 0x80 | (c&0x3F);
        buf[2] = '\0';
    } else if(c < 0x10000) {
        // 16 bits
        buf[0] = 0xE0 | ((c >> 12)&0x0F);
        buf[1] = 0x80 | ((c>>6)&0x3F);
        buf[2] = 0x80 | (c&0x3F);
        buf[3] = '\0';
    } else {
        // truncated to 21 bits
        buf[0] = 0xF0 | ((c >> 18)&0x07);
        buf[1] = 0x80 | ((c>>12)&0x3F);
        buf[2] = 0x80 | ((c>>6)&0x3F);
        buf[3] = 0x80 | (c&0x3F);
        buf[4] = '\0';
    }
    code = glyphAtlas.addImage(key, buf, *b);
    TE_CHECKRETURN_CODE(code);
    
    return code;
}

TAKErr TAK::Engine::Renderer::GLText2_localize(Port::String* result, const char* mtext) NOTHROWS
{
    if (mtext == nullptr || result == nullptr)
        return TE_InvalidArg;

    std::string retval;
    const BidirectionalTextMode mode = getRTLMode();

    size_t textLen = strlen(mtext);
    retval.reserve(textLen);
    size_t lineNum = 0;
    size_t lineStart = 0;
    size_t lineChars = 0;
    for (size_t i = 0; i < textLen; i++) {
        if (mtext[i] == '\n') {
            if (lineChars > 0)
                localizeImpl(retval, mtext + lineStart, lineChars, mode);
            retval += '\n';
            lineStart = i + 1;
            lineChars = 0;
            lineNum++;
        } else {
            lineChars++;
        }
    }
    if (lineChars > 0)
        localizeImpl(retval, mtext + lineStart, lineChars, mode);

    *result = retval.c_str();

    return TE_Ok;
}

namespace {
    static BidirectionalTextMode bidirectionalTextMode = TEBTM_Default;

    BidirectionalTextMode getRTLMode() NOTHROWS {
        return bidirectionalTextMode;
    }
}

TAKErr TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(BidirectionalTextMode mode) NOTHROWS {
    // XXX-- Make this thread safe if it becomes public API. This just exists to expose to
    // unit tests for now.
    bidirectionalTextMode = mode;
    return TE_Ok;
}

namespace
{
    TAKErr bufferChar(const std::size_t size,
                      float *texVerts,
                      float *texCoords,
                      uint16_t *texIndices,
                      const float x0, const float y0,
                      const float x1, const float y1,
                      const float z,
                      const float u0, const float v0,
                      const float u1, const float v1) NOTHROWS
    {
        if (size == 2) {
            texVerts[0] = x0; // UL
            texVerts[1] = y0;
            texVerts[2] = x1; // UR
            texVerts[3] = y0;
            texVerts[4] = x1; // LR
            texVerts[5] = y1;
            texVerts[6] = x0; // LL
            texVerts[7] = y1;
        } else if (size == 3) {
            texVerts[0] = x0; // UL
            texVerts[1] = y0;
            texVerts[2] = z;
            texVerts[3] = x1; // UR
            texVerts[4] = y0;
            texVerts[5] = z;
            texVerts[6] = x1; // LR
            texVerts[7] = y1;
            texVerts[8] = z;
            texVerts[9] = x0; // LL
            texVerts[10] = y1;
            texVerts[11] = z;
        } else {
            return TE_InvalidArg;
        }

        texCoords[0] = u0; // UL
        texCoords[1] = v0;
        texCoords[2] = u1; // UR
        texCoords[3] = v0;
        texCoords[4] = u1; // LR
        texCoords[5] = v1;
        texCoords[6] = u0; // LL
        texCoords[7] = v1;

        texIndices[0] = 3u; // LL
        texIndices[1] = 0u;   // UL
        texIndices[2] = 2u; // LR

        texIndices[3] = 0u;   // UL
        texIndices[4] = 2u; // LR
        texIndices[5] = 1u; // UR

        return TE_Ok;
    }

    void utf8_reverse(std::string& dst, const char* substr, size_t length) NOTHROWS
    {
        //std::string rev(substr);
        size_t off = dst.length();
        dst.append(substr, length);

        for (std::size_t i = 0; i < length; ++i) {
            char uri[5];
            std::size_t uri_size;
            int c = decode(substr, length, uri, i, uri_size);
            size_t dst_i = off + length - i - uri_size;
            std::copy_n(substr + i, uri_size, dst.begin() + dst_i);
            i += uri_size - 1;
        }
    }

    bool isWhitespace(unsigned int ch) {
        switch (ch) {
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 32:
            case 133:
            case 160:
            case 5760:
            case 8192:
            case 8193:
            case 8194:
            case 8195:
            case 8196:
            case 8197:
            case 8198:
            case 8199:
            case 8200:
            case 8201:
            case 8202:
            case 8232:
            case 8233:
            case 8239:
            case 8287:
            case 12288:
                return true;
        }
        return false;
    }

    inline bool isRTLCodepoint(unsigned int ch) {
        return ch >= 1425 && ch < 1632 || ch > 1641 && ch <= 1901
            || ch >= 64285 && ch <= 65276;
    }

    //
    // host is left-to-right and input text is left-to-right
    //
    TAKErr localizeImplLeftToRightHost(std::string& dst, const char* text, size_t textLen) NOTHROWS {
        size_t start = std::string::npos;
        size_t end = std::string::npos;
        size_t endNonWhite = std::string::npos;
        bool skipspace = false;

        for (std::size_t idx = 0; idx < textLen; ++idx) {
            char uri[5];
            memset(uri, 0, sizeof(uri));
            std::size_t uri_size = idx;
            unsigned int ch = decode(text, textLen, uri, idx, uri_size);
            bool white = isWhitespace(ch);

            // Check if character is used in a right-to-left locale
            // Currently covers Arabic and Hebrew
            if (isRTLCodepoint(ch) || skipspace && white) {

                if (start == std::string::npos)
                    start = idx;
                end = idx + uri_size;

                // always the case on first char
                if (!white)
                    endNonWhite = end;

                skipspace = true;
            }
            else {
                const size_t sublen = endNonWhite - start;
                if (sublen > 1) {
                    utf8_reverse(dst, text + start, sublen);
                    if (end > endNonWhite)
                        dst.append(text + endNonWhite, end - endNonWhite);
                }
                dst.append(uri, uri_size);
                start = std::string::npos;
                endNonWhite = std::string::npos;
                end = std::string::npos;
                skipspace = false;
            }
            idx += uri_size - 1;
        }

        // one final run
        const size_t sublen = endNonWhite - start;
        if (sublen > 1) {
            utf8_reverse(dst, text + start, sublen);
            if (end > endNonWhite)
                dst.append(text + endNonWhite, end - endNonWhite);
        }

        return TE_Ok;
    }

    //
    // host is right-to-left and input text is left-to-right
    //
    TAKErr localizeImplRightToLeftHost(std::string& dst2, const char* text, size_t textLen) NOTHROWS {
        
        size_t start = std::string::npos;
        size_t end = std::string::npos;
        size_t endNonWhite = std::string::npos;
        bool skipspace = true;

        std::string seg;
        seg.reserve(textLen);

        for (std::size_t idx = 0; idx < textLen; ++idx) {
            char uri[5];
            memset(uri, 0, sizeof(uri));
            std::size_t uri_size = idx;
            unsigned int ch = decode(text, textLen, uri, idx, uri_size);
            bool white = isWhitespace(ch);

            // Check if character is used in a right-to-left locale
            // Currently covers Arabic and Hebrew
            if (isRTLCodepoint(ch) || skipspace && white) {

                const size_t sublen = endNonWhite - start;
                if (sublen > 1) {
                    utf8_reverse(seg, text + start, sublen);
                    if (end > endNonWhite)
                        seg.append(text + endNonWhite, end - endNonWhite);
                }
                seg.append(uri, uri_size);
                start = std::string::npos;
                endNonWhite = std::string::npos;
                end = std::string::npos;
                skipspace = true;
            } else {
                if (start == std::string::npos)
                    start = idx;
                end = idx + uri_size;

                // always the case on first char
                if (!white)
                    endNonWhite = end;

                skipspace = false;
            }
            idx += uri_size - 1;
        }

        // one final run
        const size_t sublen = endNonWhite - start;
        if(sublen) {
            utf8_reverse(seg, text + start, sublen);
            if (end > endNonWhite)
                seg.append(text + endNonWhite, end - endNonWhite);
        }

        // finally, reverse the whole thing
        utf8_reverse(dst2, seg.c_str(), seg.length());

        return TE_Ok;
    }

    TAKErr localizeImpl(std::string& dst, const char* text, size_t textLen, BidirectionalTextMode rightToLeftMode) NOTHROWS {
        switch (rightToLeftMode) {
        case TEBTM_Default:
            /* XXX-- normally should let locale decide this, and left-to-right host should remain left to right, and
                     simply reverse right-to-left substrings so they are RTL, but to avoid ATAK regression, default is 
                     RTL host mode (i.e. all input text is LTR, embedded RTL substrings are detected and reversed and the
                     string is reordered to read RTL) */
        
            // fall through
        case TEBTM_HostRightToLeft:
            return localizeImplRightToLeftHost(dst, text, textLen);
        case TEBTM_HostLeftToRight:
            return localizeImplLeftToRightHost(dst, text, textLen);
        
        default: break;
        }
        return TE_InvalidArg;
    }


    unsigned int decode(const char* text, size_t len, char uri[5], const std::size_t i, std::size_t& size) NOTHROWS
    {
        size = 1;
        int off = 0;
        unsigned int c = (unsigned int)text[i + off] & 0xFFu;
        // skip non-printable
        if (c < 32u)
            return 0;
        uri[0] = c;
        // decode UTF-8
        do {
            if (!(c & 0x80))
                break;
            const uint8_t mask = (c & 0xF8);
            if (mask == 0xF0) { // 21-bit code
                if ((i + 3) >= len)
                    break;
                uri[1] = text[(i + 1) + off];
                uri[2] = text[(i + 2) + off];
                uri[3] = text[(i + 3) + off];
                uri[4] = '\0';
                const uint16_t bs0 = text[i + off] & 0x07;
                const uint16_t bs1 = text[(i + 1) + off] & 0x3F;
                const uint16_t bs2 = text[(i + 2) + off] & 0x3F;
                const uint16_t bs3 = text[(i + 3) + off] & 0x3F;
                c = (bs0 << 18) | (bs1 << 12) | (bs2 << 6) | bs3;
                size = 4;
            }
            else if ((mask & 0xE0) == 0xE0) {
                // incomplete coding, push the bits through
                if ((i + 2) >= len)
                    break;
                // 16-bit code
                uri[1] = text[(i + 1) + off];
                uri[2] = text[(i + 2) + off];
                uri[3] = '\0';

                const uint16_t bs0 = text[i + off] & 0x0F;
                const uint16_t bs1 = text[(i + 1) + off] & 0x3F;
                const uint16_t bs2 = text[(i + 2) + off] & 0x3F;
                c = (bs0 << 12) | (bs1 << 6) | bs2;
                size = 3;
            }
            else if ((mask & 0xE0) == 0xC0) { // 11-bit code
             // incomplete coding, push the bits through
                if ((i + 1) >= len)
                    break;
                uri[1] = text[(i + 1) + off];
                uri[2] = '\0';
                const uint16_t bs0 = text[i + off] & 0x1F;
                const uint16_t bs1 = text[(i + 1) + off] & 0x3F;
                c = (bs0 << 6) | bs1;
                size = 2;
            }
            else {
                // XXX - invalid char...

            }
        } while (false);
        return c;
    }
}

