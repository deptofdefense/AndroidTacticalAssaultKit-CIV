
#include <Foundation/Foundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include "renderer/BitmapFactory.h"

#include "renderer/GLText2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            
            class CoreGraphicsTextFormat2 : public TextFormat2
            {
            public:
                CoreGraphicsTextFormat2(const char *name, float size) NOTHROWS;
                virtual ~CoreGraphicsTextFormat2() NOTHROWS;
                
                virtual float getStringWidth(const char *text) NOTHROWS;
                virtual float getCharPositionWidth(const char *text, int position) NOTHROWS;
                virtual float getCharWidth(const char chr) NOTHROWS;
                virtual float getCharHeight() NOTHROWS;
                virtual float getDescent() NOTHROWS;
                virtual float getStringHeight(const char *text) NOTHROWS;
                virtual float getBaselineSpacing() NOTHROWS;
                virtual int getFontSize() NOTHROWS;
                virtual Util::TAKErr loadGlyph(BitmapPtr &value, const char c) NOTHROWS;
                
            private:
                CTFontRef fontRef;
                float commonDescent;
                float commonStringHeight;
                float baselineSpacing;
                float fontSize;
                float asciiWidths[126 - 32 - 1];
            };
        }
    }
}

namespace {
    struct FontMetrics {
        CGFloat ascent, descent, leading, width, height;
    };
    
    CTLineRef getLine(CTFontRef fontRef, const char *text) {
        NSDictionary* attributes = [NSDictionary dictionaryWithObjectsAndKeys:
                                    (__bridge id)fontRef, kCTFontAttributeName,
                                    kCFBooleanTrue, kCTForegroundColorFromContextAttributeName,
                                    nil];
        
        NSAttributedString* as = [[NSAttributedString alloc] initWithString:[NSString stringWithCString:text encoding:NSASCIIStringEncoding]
                                                                 attributes:attributes];
        
        CTLineRef line = CTLineCreateWithAttributedString((CFAttributedStringRef)as);
        return line;
    }
    
    void getFontMetricsForString(CTLineRef line, FontMetrics *outMetrics) {
        
        CGFloat ascent, descent, leading;
        double fWidth = CTLineGetTypographicBounds(line, &ascent, &descent, &leading);
        
        outMetrics->ascent = -ascent;
        outMetrics->descent = descent;
        outMetrics->leading = leading;
        outMetrics->width = fWidth;
        outMetrics->height = (ascent + descent);
    }
    
    float measureCharacterWidth(CTFontRef fontRef, char chr) {
        char str[] = { chr, 0 };
        CTLineRef line = getLine(fontRef, str);
        FontMetrics fm;
        getFontMetricsForString(line, &fm);
        CFRelease(line);
        return ceilf(fm.width);
    }
    
    void noopTextFormat2(const TAK::Engine::Renderer::TextFormat2 *) {
        
    }
    
    void deleteData(const uint8_t *data) {
        delete [] data;
    }
    
    void deleteBitmap2(const TAK::Engine::Renderer::Bitmap2 *bitmap) {
        delete bitmap;
    }
}

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine;

Util::TAKErr TAK::Engine::Renderer::TextFormat2_createDefaultSystemTextFormat(TextFormat2Ptr &value, const float textSize) NOTHROWS {
    static CoreGraphicsTextFormat2 tf("Helvetica", textSize);
    value = TextFormat2Ptr(&tf, ::noopTextFormat2);
    return Util::TE_Ok;
}


CoreGraphicsTextFormat2::CoreGraphicsTextFormat2(const char *fontName, float fontSize) NOTHROWS
: fontRef(NULL),
fontSize(fontSize) {
    
    fontRef = CTFontCreateWithName(CFSTR("Helvetica"), fontSize, nil);
    FontMetrics metrics;
    CTLineRef line = getLine(fontRef, "|");
    getFontMetricsForString(line, &metrics);
    CFRelease(line);
    
    commonDescent = metrics.descent;
    commonStringHeight = metrics.height;
    baselineSpacing = (-metrics.ascent + metrics.descent);
    
    for (int i = 0; i < 126 - 32 + 1; ++i) {
        asciiWidths[i] = 0.f;
    }
}

CoreGraphicsTextFormat2::~CoreGraphicsTextFormat2() NOTHROWS {
    CFRelease((CTFontRef)fontRef);
}

float CoreGraphicsTextFormat2::getStringWidth(const char *text) NOTHROWS {
    float width = 0.f;
    const char *cp = text;
    while (*cp) {
        width += getCharWidth(*cp++);
    }
    return width;
}

float CoreGraphicsTextFormat2::getStringHeight(const char *text) NOTHROWS {
    FontMetrics fm;
    CTLineRef line = getLine((CTFontRef)fontRef, text);
    getFontMetricsForString(line, &fm);
    CFRelease(line);
    return fm.height;
}

float CoreGraphicsTextFormat2::getCharPositionWidth(const char *text, int position) NOTHROWS {
    return position * this->fontSize;
}

float CoreGraphicsTextFormat2::getCharWidth(const char chr) NOTHROWS {
    if (chr >= 32 && chr <= 126) {
        float width = asciiWidths[chr - 32];
        if (width == 0.f) {
            width = measureCharacterWidth((CTFontRef)fontRef, chr);
            asciiWidths[chr - 32] = width;
        }
        return width;
    }
    return measureCharacterWidth((CTFontRef)fontRef, chr);
}

float CoreGraphicsTextFormat2::getCharHeight() NOTHROWS{
    return this->commonStringHeight;
}

float CoreGraphicsTextFormat2::getDescent() NOTHROWS{
    return this->commonDescent;
}

float CoreGraphicsTextFormat2::getBaselineSpacing() NOTHROWS{
    return this->baselineSpacing;
}

int CoreGraphicsTextFormat2::getFontSize() NOTHROWS {
    return this->fontSize;
}

Util::TAKErr CoreGraphicsTextFormat2::loadGlyph(BitmapPtr &value, const char c) NOTHROWS {
    char text[] = { c, '\0' };
    FontMetrics fm;
    CTLineRef line = getLine(fontRef, text);
    getFontMetricsForString(line, &fm);
    
    size_t width = (size_t)ceilf(fm.width);
    size_t height = (size_t)ceilf(fm.height);
    size_t dataLen = width * height * 4;
    
    Bitmap2::DataPtr data(new uint8_t[dataLen], ::deleteData);
    memset(data.get(), 0, dataLen);
    
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGBitmapInfo bitmapInfo = kCGImageAlphaPremultipliedLast;
    CGContextRef ctx = CGBitmapContextCreate(data.get(), width, height, 8, width * 4, space, bitmapInfo);
    CGColorSpaceRelease(space);
    CGFloat x = 0.0;
    CGFloat y = fm.descent;
    CGContextSetRGBFillColor(ctx, 1.f, 1.f, 1.f, 1.f);
    CGContextSetTextPosition(ctx, x, y);
    CGContextSetTextDrawingMode(ctx, kCGTextFill);
    CTLineDraw(line, ctx);
    CFRelease(line);
    CGContextRelease(ctx);
    
    value = TAK::Engine::Renderer::BitmapPtr(new Bitmap2(std::move(data), width, height, Bitmap2::ARGB32), ::deleteBitmap2);

    return Util::TE_Ok;
}
