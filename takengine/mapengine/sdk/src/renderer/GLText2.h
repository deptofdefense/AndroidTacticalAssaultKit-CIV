#ifndef TAK_ENGINE_RENDERER_GLTEXT2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTEXT2_H_INCLUDED

#include <map>

#include "math/Rectangle.h"
#include "port/Platform.h"
#include "renderer/Bitmap2.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/GLRenderBatch.h"
#include "util/Error.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            class ENGINE_API TextFormat2
            {
            protected :
                virtual ~TextFormat2() = 0;
            public:
                /** returns the width of the specified string */
                virtual float getStringWidth(const char *text) NOTHROWS = 0;
                /** returns the width of the specified character at the specified position */
                virtual float getCharPositionWidth(const char *text, int position) NOTHROWS = 0;
                /** returns the width of the specified character */
                virtual float getCharWidth(const unsigned int chr) NOTHROWS = 0;
                /** returns nominal character height */
                virtual float getCharHeight() NOTHROWS = 0;
                virtual float getDescent() NOTHROWS = 0;
                /** returns expected rendered height of the specified string */
                virtual float getStringHeight(const char *text) NOTHROWS = 0;
                /** returns spacing, in pixels, between consecutive lines */
                virtual float getBaselineSpacing() NOTHROWS = 0;
                /** returns the point size of the font */
                virtual int getFontSize() NOTHROWS = 0;

                virtual Util::TAKErr loadGlyph(BitmapPtr &value, const unsigned int c) NOTHROWS = 0;
            };

            typedef std::unique_ptr<TextFormat2, void(*)(const TextFormat2 *)> TextFormat2Ptr;

            struct ENGINE_API TextFormatParams
            {
            public :
                TextFormatParams(const float size) NOTHROWS;
                TextFormatParams(const char* fontName, const float size) NOTHROWS;
            public :
                const char *fontName;
                float size;
                bool bold;
                bool italic;
                bool underline;
                bool strikethrough;
            };

            ENGINE_API Util::TAKErr TextFormat2_createDefaultSystemTextFormat(TextFormat2Ptr &value, const float textSize) NOTHROWS;
            ENGINE_API Util::TAKErr TextFormat2_createTextFormat(TextFormat2Ptr &value, const TextFormatParams &params) NOTHROWS;

            class ENGINE_API GLText2
            {
            public:
                GLText2(TextFormat2Ptr &&textFormat) NOTHROWS;
                ~GLText2() NOTHROWS;
            public:
                // draw
                Util::TAKErr draw(const char *text,
                                  const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr draw(const char *text,
                                  const int *colors, const std::size_t numColors) NOTHROWS;
                Util::TAKErr draw(const char *text,
                                  const float r, const float g, const float b, const float a,
                                  const float scissorX0, const float scissorX1) NOTHROWS;
                Util::TAKErr draw(const char *text,
                                  const int *colors, const std::size_t numColors,
                                  const float scissorX0, const float scissorX1) NOTHROWS;

                // add to existing batch
                Util::TAKErr batch(atakmap::renderer::GLRenderBatch &batch,
                                  const char *text,
                                  const float x, const float y,
                                  const int *colors, const std::size_t numColors) NOTHROWS;
                Util::TAKErr batch(atakmap::renderer::GLRenderBatch &batch,
                                   const char *text,
                                   const float x, const float y,
                                   const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr batch(GLRenderBatch2 &batch,
                    const char *text,
                    const float x, const float y, const float z,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                Util::TAKErr batch(GLRenderBatch2 &batch,
                    const char *text,
                    const float x, const float y, const float z,
                    const int *colors, const std::size_t numColors) NOTHROWS;
                Util::TAKErr batch(atakmap::renderer::GLRenderBatch &batch,
                                   const char *text,
                                   const float x, const float y,
                                   const int *colors, const std::size_t numColors,
                                   const float scissorX0, const float scissorX1) NOTHROWS;
                Util::TAKErr batch(atakmap::renderer::GLRenderBatch &batch,
                                   const char *text,
                                   const float x, const float y,
                                   const float r, const float g, const float b, const float a,
                                   const float scissorX0, const float scissorX1) NOTHROWS;
                Util::TAKErr batch(GLRenderBatch2 &batch,
                    const char *text,
                    const float x, const float y, const float z,
                    const int *colors, const std::size_t numColors,
                    const float scissorX0, const float scissorX1) NOTHROWS;
                Util::TAKErr batch(GLRenderBatch2 &batch,
                    const char *text,
                    const float x, const float y, const float z,
                    const float r, const float g, const float b, const float a,
                    const float scissorX0, const float scissorX1) NOTHROWS;
                TextFormat2 &getTextFormat() const NOTHROWS;
            public :
                static std::size_t getLineCount(const char *text) NOTHROWS;
            private:
                /** batches single line of text */
                Util::TAKErr batchImpl(atakmap::renderer::GLRenderBatch &batch,
                                       const float tx, const float ty,
                                       const char *text,
                                       const std::size_t off, const std::size_t len,
                                       const float r, const float g, const float b, const float a) NOTHROWS;

                Util::TAKErr batchImpl(GLRenderBatch2 &batch,
                    const float tx, const float ty, const float tz,
                    const char *text,
                    const std::size_t off, const std::size_t len,
                    const float r, const float g, const float b, const float a) NOTHROWS;
                /** batches single line of text */
                Util::TAKErr batchImpl(atakmap::renderer::GLRenderBatch &batch,
                                       const float tx, const float ty,
                                       const char *text,
                                       const std::size_t off, const std::size_t len,
                                       const float r, const float g, const float b, const float a,
                                       const float scissorX0, const float scissorX1) NOTHROWS;

                Util::TAKErr batchImpl(GLRenderBatch2 &batch,
                    const float tx, const float ty, const float tz,
                    const char *text,
                    const std::size_t off, const std::size_t len,
                    const float r, const float g, const float b, const float a,
                    const float scissorX0, const float scissorX1) NOTHROWS;

                Util::TAKErr loadGlyph(int64_t *atlasKey, const unsigned int c) NOTHROWS;
            private :
                TextFormat2Ptr textFormat;

                GLTextureAtlas2 glyphAtlas;

                float charMaxHeight;
#define GLTEXT2_NUM_COMMON_CHARS (((254u) - (32u) + 1u))
                float commonCharUV[GLTEXT2_NUM_COMMON_CHARS * 4u];
                int commonCharTexId[GLTEXT2_NUM_COMMON_CHARS];
                float commonCharWidth[GLTEXT2_NUM_COMMON_CHARS];
#undef GLTEXT2_NUM_COMMON_CHARS
            };

            ENGINE_API GLText2 *GLText2_intern(std::shared_ptr<TextFormat2> textFormat) NOTHROWS;
            ENGINE_API GLText2 *GLText2_intern(const TextFormatParams &fmt) NOTHROWS;
        }
    }
}

#endif
