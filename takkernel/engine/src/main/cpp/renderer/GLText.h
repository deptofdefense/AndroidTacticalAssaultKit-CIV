#ifndef ATAKMAP_RENDERER_GLTEXT_H_INCLUDED
#define ATAKMAP_RENDERER_GLTEXT_H_INCLUDED

#include "renderer/GLTextureAtlas.h"
#include "math/Rectangle.h"
#include "renderer/GLRenderBatch.h"
#include "port/Platform.h"

#include <map>

namespace atakmap
{
    namespace renderer
    {

        class ENGINE_API TextFormat {
        public:
            virtual ~TextFormat() {};
            virtual float getStringWidth(const char *text) = 0;
            virtual float getStringHeight(const char *text) = 0;
            virtual float getCharPositionWidth(const char *text, int position) = 0;
            virtual float getCharWidth(char chr) = 0;
            virtual float getCharHeight() = 0;
            virtual float getDescent() = 0;
            virtual float getStringHeight() = 0;
            virtual float getBaselineSpacing() = 0;
            virtual int getFontSize() = 0;

            virtual Bitmap loadGlyph(char c) = 0;
            
            static TextFormat *createDefaultSystemTextFormat(float textSize);
        };

        class ENGINE_API GLText {

        public:
            static GLText *getInstance(TextFormat *textFormat);

            void drawSplitString(const char *text, int *colors, size_t colorsCount);
            void drawSplitString(const char *text, float r, float g, float b, float a);

            void draw(const char *text, float r, float g, float b, float a);
            void draw(const char *text, float r, float g, float b, float a, float scissorX0,
                      float scissorX1);


            void batchSplitString(GLRenderBatch *batch, const char *text, float x, float y, int *colors, size_t colorsCount);
            void batchSplitString(GLRenderBatch *batch, const char *text, float x, float y, float r,
                                  float g, float b, float a);
            void batch(GLRenderBatch *batch, const char *text, float x, float y, float r, float g,
                       float b, float a);
            
            void batch(GLRenderBatch *batch, const char *text, float x, float y, float r, float g,
                       float b, float a, float scissorX0, float scissorX1);


            TextFormat *getTextFormat();
            static int getLineCount(const char *text);

        private:
            GLText(TextFormat *textFormat, float densityAdjustedTextSize);
            ~GLText();
            void batchImpl(GLRenderBatch *batch, float tx, float ty, const char *text, int off, int len,
                           float r, float g, float b, float a);

            void batchImpl(GLRenderBatch *batch, float tx, float ty, const char *text, size_t off, size_t len,
                           float r, float g, float b, float a, float scissorX0, float scissorX1);
            
            int64_t loadGlyph(const char c);
            void bufferChar(float *verts, float *tex, short *indices, int n, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1);
            
            
            /**************************************************************************/

            static std::map<intptr_t, GLText *> glTextCache;

            // NOTE: marked as static since 'batchImpl' is non-reentrant and
            //       non-recursive; there is no need to make these instance
            static float *trianglesVerts;
            static float *trianglesTexCoords;
            static short *trianglesIndices;

            /**************************************************************************/

            TextFormat *textFormat;

            GLTextureAtlas *glyphAtlas;

            float charMaxHeight;

            float *commonCharUV;
            int *commonCharTexId;
            float *commonCharWidth;

            math::Rectangle<float> scratchCharBounds;



        };
    }
}

#endif
