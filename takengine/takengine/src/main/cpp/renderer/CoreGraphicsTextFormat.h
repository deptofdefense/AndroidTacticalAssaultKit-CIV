#ifndef ATAKMAP_RENDERER_CORE_GRAPHICS_TEXT_FORMAT_H_INCLUDED
#define ATAKMAP_RENDERER_CORE_GRAPHICS_TEXT_FORMAT_H_INCLUDED

#include "GLText.h"

namespace atakmap
{
    namespace renderer
    {
        class CoreGraphicsTextFormat : public TextFormat {
        public:
            CoreGraphicsTextFormat(const char *fontName, float fontSize);
            virtual ~CoreGraphicsTextFormat();
            virtual float getStringWidth(const char *text);
            virtual float getStringHeight(const char *text);
            virtual float getCharPositionWidth(const char *text, int position);
            virtual float getCharWidth(char chr);
            virtual float getCharHeight();
            virtual float getDescent();
            virtual float getStringHeight();
            virtual float getBaselineSpacing();
            virtual int getFontSize();
            
            virtual Bitmap loadGlyph(char c);
            
        private:
            void *fontRef;
            float commonDescent;
            float commonStringHeight;
            float baselineSpacing;
            float fontSize;
            float asciiWidths[126 - 32 - 1];
        };
    }
}

#endif