
#include <CoreGraphics/CoreGraphics.h>

#include "renderer/BitmapFactory.h"

using namespace atakmap::util;
using namespace atakmap::renderer;

namespace {

    void createBitmap(Bitmap &bitmap, int width, int height, int format, int dataType);
    
    struct CoreGraphicsSequentialDataProviderImpl {
        
        CoreGraphicsSequentialDataProviderImpl(DataInput &input, const atakmap::util::CancelationToken &cancelToken)
        : rewindInput(input),
        cancelationToken(cancelToken) {
            callbacks.getBytes = getBytesImpl;
            callbacks.releaseInfo = releaseInfoImpl;
            callbacks.rewind = rewindImpl;
            callbacks.skipForward = skipForwardImpl;
            callbacks.version = 0;
        }
        
        ~CoreGraphicsSequentialDataProviderImpl() { }
        
        static size_t getBytesImpl(void *info, void *bytes, size_t size) {
            CoreGraphicsSequentialDataProviderImpl *impl = static_cast<CoreGraphicsSequentialDataProviderImpl *>(info);
            size_t result = impl->rewindInput.read(static_cast<uint8_t *>(bytes), size);
            return result == DataInput::EndOfStream ? 0 : result;
        }
        
        static void releaseInfoImpl(void *info) {
            // nothing
        }
        
        static void rewindImpl(void *info) {
            static_cast<CoreGraphicsSequentialDataProviderImpl *>(info)->rewindInput.rewind();
        }
        
        static off_t skipForwardImpl(void *info, off_t count) {
            CoreGraphicsSequentialDataProviderImpl *impl = static_cast<CoreGraphicsSequentialDataProviderImpl *>(info);
            return impl->rewindInput.skip(count);
        }
        
        RewindDataInput rewindInput;
        atakmap::util::CancelationToken cancelationToken;
        CGDataProviderSequentialCallbacks callbacks;
    };

    Bitmap createBitmapWithCGImage(CGImageRef image, size_t width, size_t height, bool verticalFlip) {
        
        BitmapOptions opts;
        opts.width = width & INT_MAX;
        opts.height = height & INT_MAX;
        opts.dataType = GL_UNSIGNED_BYTE;
        opts.format = GL_RGBA;
        Bitmap bitmap = BitmapFactory::create(opts);
        
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(bitmap.data, width, height, 8, 4 * width,
                                                     colorSpace, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        
        CGColorSpaceRelease(colorSpace);
        CGContextClearRect(context, CGRectMake( 0, 0, width, height));
        CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
        if (verticalFlip) {
            CGAffineTransform flipTransform = CGAffineTransformMake(1, 0, 0, -1, 0, height);
            CGContextConcatCTM(context, flipTransform);
        }
        CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
        
        CGContextRelease(context);
        
        return bitmap;
    }
    
    void createBitmapWithBitmap(atakmap::renderer::Bitmap *r, const atakmap::renderer::Bitmap &src, size_t width, size_t height, bool verticalFlip) {
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGBitmapInfo bitmapInfo = kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big;
        
        CGDataProviderRef dataProvider = CGDataProviderCreateWithData(NULL, src.data, src.dataLen, NULL);
        
        CGImageRef srcImage = CGImageCreate(src.width, src.height, 8, 32, src.width * 4, colorSpace, bitmapInfo, dataProvider, NULL, FALSE, kCGRenderingIntentDefault);
        
        CGColorSpaceRelease(colorSpace);
        
        *r = createBitmapWithCGImage(srcImage, width, height, verticalFlip);
        
        CGImageRelease(srcImage);
        CGDataProviderRelease(dataProvider);
    }
    
    atakmap::renderer::Bitmap getScaledInstanceImpl(atakmap::renderer::Bitmap b, int w, int h) {
        atakmap::renderer::Bitmap r;
        createBitmapWithBitmap(&r, b, w, h, false);
        return r;
    }
    
    void releaseBitmapImpl(atakmap::renderer::Bitmap bitmap) {
        delete [] static_cast<uint8_t *>(bitmap.data);
    }
    
    int getBytesPerPixel(int format, int dataType) {
        
        if ((format == GL_RGBA || format == GL_RGB) && dataType == GL_UNSIGNED_BYTE) {
            return 4;
        }
        
        throw std::invalid_argument("unknown format or dataType");
    }
    
    void createBitmap(Bitmap &bitmap, int width, int height, int format, int dataType) {
        int bytesPerPixel = getBytesPerPixel(format, dataType);
        bitmap.width = width;
        bitmap.height = height;
        bitmap.dataLen = width * height * bytesPerPixel;
        bitmap.data = new uint8_t[bitmap.dataLen];
        bitmap.format = format;
        bitmap.dataType = dataType;
        bitmap.releaseData = releaseBitmapImpl;
        bitmap.getScaledInstance = getScaledInstanceImpl;
    }
}

Bitmap BitmapFactory::create(const BitmapOptions &options) {
    Bitmap bitmap;
    createBitmap(bitmap, options.width, options.height, options.format, options.dataType);
    return bitmap;
}

void foo() {
    
}

BitmapFactory::DecodeResult BitmapFactory::decode(DataInput &input, Bitmap &dst, const BitmapDecodeOptions *opts) {
    
    DecodeResult result = Unsupported;
    util::CancelationToken cancelToken;
    
    if (opts != NULL) {
        cancelToken = opts->cancelationToken;
    }
    
    CoreGraphicsSequentialDataProviderImpl impl(input, cancelToken);
    
    CGDataProviderRef dataProvider = CGDataProviderCreateSequential(&impl, &impl.callbacks);
    CGImageRef image = NULL;
    
    image = CGImageCreateWithJPEGDataProvider(dataProvider, NULL, false, kCGRenderingIntentDefault);
    if (!image) {
        image = CGImageCreateWithPNGDataProvider(dataProvider, NULL, false, kCGRenderingIntentDefault);
    }
    
    CGDataProviderRelease(dataProvider);
    
    if (image) {
        dst = createBitmapWithCGImage(image, CGImageGetWidth(image), CGImageGetHeight(image), false);
        result = Success;
    } else {
        result = Unsupported;
    }
    
    CGImageRelease(image);
    
    return result;
}
