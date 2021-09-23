
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>
#include <string.h> // memcpy

#include "renderer/BitmapCompositor.h"

using namespace atakmap::renderer;

namespace {
    const void *directProviderGetBytePointerImpl(void *info) {
        return info;
    }
    
    size_t dataProviderGetBytesAtPositionImpl(void *info, void *buffer, off_t pos, size_t cnt) {
        memcpy(buffer, static_cast<const uint8_t *>(info) + pos, cnt);
        return cnt;
    }
    
    void dataProviderReleaseBytePointerImpl(void *info, const void *pointer) {
        // nothing
    }
    
    void dataProviderReleaseInfoImpl(void * __nullable info) {
        // nothing
    }
}

class CoreGraphicsBitmapCompositor : public BitmapCompositor {
public:
    CoreGraphicsBitmapCompositor(const Bitmap &dstBitmap) {
        
        size_t width = dstBitmap.width;
        size_t height = dstBitmap.height;
        
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        cgContext = CGBitmapContextCreate(dstBitmap.data, width, height, 8, 4 * width,
                              colorSpace, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        CGColorSpaceRelease(colorSpace);
        
        this->canvasWidth = width;
        this->canvasHeight = height;
    }
    
    
    virtual ~CoreGraphicsBitmapCompositor() {
        CGContextRelease(cgContext);
    }
protected:
    virtual void compositeImpl_DEBUG(float destX, float destY, float destW, float destH,
                               float srcX, float srcY, float srcW, float srcH,
                               const Bitmap &src) {
        
        CGRect rect = CGRectMake(destX + 5, destY + 5, destW - 10, destH - 10);
        CGFloat color[] = {
            0.0,
            1.0,
            1.0,
            1.0
        };
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGColorRef cgColor = CGColorCreate(colorSpace, color);
        
        CGContextSetFillColorWithColor(this->cgContext, cgColor);
        CGContextAddRect(this->cgContext, rect);
        CGContextDrawPath(this->cgContext, kCGPathFill);
        
        CGColorSpaceRelease(colorSpace);
        CGColorRelease(cgColor);
        
    }
    
    virtual void compositeImpl(float destX, float destY, float destW, float destH,
                                   float srcX, float srcY, float srcW, float srcH,
                                   const Bitmap &src) {
    
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGBitmapInfo bitmapInfo = kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big;
        
        CGDataProviderDirectCallbacks callbacks;
        callbacks.getBytePointer = directProviderGetBytePointerImpl;
        callbacks.getBytesAtPosition = dataProviderGetBytesAtPositionImpl;
        callbacks.releaseBytePointer = dataProviderReleaseBytePointerImpl;
        callbacks.releaseInfo = dataProviderReleaseInfoImpl;
        callbacks.version = 0;
        
        CGDataProviderRef dataProvider = CGDataProviderCreateDirect(const_cast<void *>(src.data), src.dataLen, &callbacks);
        
        size_t width = src.width;
        size_t height = src.height;
        
        CGImageRef srcImage = CGImageCreate(width, height, 8, 32, width * 4, colorSpace, bitmapInfo, dataProvider, NULL, FALSE, kCGRenderingIntentDefault);
        
        CGColorSpaceRelease(colorSpace);
        
        if (srcX != 0 || srcY != 0 || srcW != src.width || srcH != src.height) {
            //TODO--Add transform
        }
        
        CGRect destRect = CGRectMake(destX, destY, destW, destH);
        CGContextDrawImage(cgContext, destRect, srcImage);
        
        CGImageRelease(srcImage);
        CGDataProviderRelease(dataProvider);
    }
    
    virtual void debugTextImpl(float destX, float destY, float destW, float destH, const char *text) {
        
        CFStringRef textStringRef = CFStringCreateWithCString(kCFAllocatorDefault, text, kCFStringEncodingUTF8);
        
        //    create attributed string
        CFMutableAttributedStringRef attrStr = CFAttributedStringCreateMutable(kCFAllocatorDefault, 0);
        CFAttributedStringReplaceString (attrStr, CFRangeMake(0, 0), textStringRef);
        
        //    create font
        CTFontRef font = CTFontCreateWithName(CFSTR("Helvetica"), 48, NULL);
        CFAttributedStringSetAttribute(attrStr, CFRangeMake(0, CFAttributedStringGetLength(attrStr)), kCTFontAttributeName, font);
        CFRelease(font);
        
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGFloat color[] = {
            1.0,
            0.0,
            0.0,
            1.0
        };
        CGColorRef cgColor = CGColorCreate(colorSpace, color);
        CFAttributedStringSetAttribute(attrStr, CFRangeMake(0, CFAttributedStringGetLength(attrStr)), kCTForegroundColorAttributeName, cgColor);
        
        CGRect rect = CGRectMake(destX, destY, destW, destH);
        CGContextAddRect(this->cgContext, rect);
        CGContextSetStrokeColorWithColor(this->cgContext, cgColor);
        CGContextDrawPath(this->cgContext, kCGPathStroke);
        
        CTLineRef line = CTLineCreateWithAttributedString(attrStr);
        CGContextSetTextPosition(this->cgContext, destX, destY + destH / 2);
        CTLineDraw(line, this->cgContext);
        
        CFRelease(line);
        CFRelease(cgColor);
        CFRelease(colorSpace);
    }
    
private:
    CGContextRef cgContext;
    float canvasWidth;
    float canvasHeight;
};

BitmapCompositor *BitmapCompositor::create(const atakmap::renderer::Bitmap &dstBitmap) {
    return new CoreGraphicsBitmapCompositor(dstBitmap);
}