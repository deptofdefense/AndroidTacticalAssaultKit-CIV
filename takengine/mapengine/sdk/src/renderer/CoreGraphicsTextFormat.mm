
#include <Foundation/Foundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include "renderer/CoreGraphicsTextFormat.h"

#include "renderer/BitmapFactory.h"

using namespace atakmap::renderer;


namespace {
    struct FontMetrics {
        CGFloat ascent, descent, leading, width, height;
    };
    
    CTLineRef getLine(CTFontRef fontRef, const char *text) {
        NSDictionary* attributes = [NSDictionary dictionaryWithObjectsAndKeys:
                                    (__bridge id)fontRef, kCTFontAttributeName,
                                    kCFBooleanTrue, kCTForegroundColorFromContextAttributeName,
                                    nil];
        
        NSAttributedString* as = [[NSAttributedString alloc] initWithString:[NSString stringWithCString:text encoding:NSUTF8StringEncoding]
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
}



TextFormat *TextFormat::createDefaultSystemTextFormat(float textSize) {
    static CoreGraphicsTextFormat tf("Helvetica", textSize);
    return &tf;
}


CoreGraphicsTextFormat::CoreGraphicsTextFormat(const char *fontName, float fontSize)
: fontRef(NULL),
fontSize(fontSize) {
    
    fontRef = (void *)CTFontCreateWithName(CFSTR("Helvetica"), fontSize, nil);
    FontMetrics metrics;
    CTLineRef line = getLine((CTFontRef)fontRef, "|");
    getFontMetricsForString(line, &metrics);
    CFRelease(line);
    
    commonDescent = metrics.descent;
    commonStringHeight = metrics.height;
    baselineSpacing = (-metrics.ascent + metrics.descent);
    
    for (int i = 0; i < 126 - 32 + 1; ++i) {
        asciiWidths[i] = 0.f;
    }
}

CoreGraphicsTextFormat::~CoreGraphicsTextFormat() {
    CFRelease((CTFontRef)fontRef);
}

float CoreGraphicsTextFormat::getStringWidth(const char *text) {
    const char *cp = text;
    float width = 0.f;
    while (*cp) {
        width += getCharWidth(*cp++);
    }
    return width;
}

float CoreGraphicsTextFormat::getStringHeight(const char *text) {
    FontMetrics fm;
    CTLineRef line = getLine((CTFontRef)fontRef, text);
    getFontMetricsForString(line, &fm);
    CFRelease(line);
    return fm.height;
}

float CoreGraphicsTextFormat::getCharPositionWidth(const char *text, int position) {
    return position * this->fontSize;
}

float CoreGraphicsTextFormat::getCharWidth(char chr) {
    if (chr >= 32 || chr <= 126) {
        
        float width = asciiWidths[chr - 32];
        if (width == 0.f) {
            width = measureCharacterWidth((CTFontRef)fontRef, chr);
            asciiWidths[chr - 32] = width;
        }
        
        return width;
    }
    
    return measureCharacterWidth((CTFontRef)fontRef, chr);
}

float CoreGraphicsTextFormat::getCharHeight() {
    return this->commonStringHeight;
}

float CoreGraphicsTextFormat::getDescent() {
    return this->commonDescent;
}

float CoreGraphicsTextFormat::getStringHeight() {
    return this->commonStringHeight;
}

float CoreGraphicsTextFormat::getBaselineSpacing() {
    return this->baselineSpacing;
}

int CoreGraphicsTextFormat::getFontSize() {
    return this->fontSize;
}

Bitmap CoreGraphicsTextFormat::loadGlyph(char c) {

    char text[] = { c, '\0' };
    FontMetrics fm;
    CTLineRef line = getLine((CTFontRef)fontRef, text);
    getFontMetricsForString(line, &fm);
    
    size_t width = (size_t)ceilf(fm.width);
    size_t height = (size_t)ceilf(fm.height);
    
    BitmapOptions opts;
    opts.width = ceilf(fm.width);
    opts.height = ceilf(fm.height);
    opts.format = GL_RGBA;
    opts.dataType = GL_UNSIGNED_BYTE;
    
    Bitmap b = BitmapFactory::create(opts);
    memset(b.data, 0, b.dataLen);
    
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGBitmapInfo bitmapInfo = kCGImageAlphaPremultipliedLast;
    CGContextRef ctx = CGBitmapContextCreate(b.data, width, height, 8, width*4, space, bitmapInfo);
    CGColorSpaceRelease(space);
    CGFloat x = 0.0;
    CGFloat y = fm.descent;
    CGContextSetRGBFillColor(ctx, 1.f, 1.f, 1.f, 1.f);
    CGContextSetTextPosition(ctx, x, y);
    CGContextSetTextDrawingMode(ctx, kCGTextFill);
    CTLineDraw(line, ctx);
    CFRelease(line);
    CGContextRelease(ctx);
    
    return b;
}