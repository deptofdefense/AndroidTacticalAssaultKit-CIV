
#include <CoreGraphics/CoreGraphics.h>

#include "renderer/BitmapFactory2.h"

// SDK includes
#include "util/Memory.h"
#include "util/IO2.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

namespace {
    struct CoreGraphicsSequentialDataProviderImpl {

        CoreGraphicsSequentialDataProviderImpl(MemoryInput2 &input)
        : input(input) {
            callbacks.getBytes = getBytesImpl;
            callbacks.releaseInfo = releaseInfoImpl;
            callbacks.rewind = rewindImpl;
            callbacks.skipForward = skipForwardImpl;
            callbacks.version = 0;
        }

        ~CoreGraphicsSequentialDataProviderImpl() { }

        static size_t getBytesImpl(void *info, void *bytes, size_t size) {
            CoreGraphicsSequentialDataProviderImpl *impl = static_cast<CoreGraphicsSequentialDataProviderImpl *>(info);
            size_t numRead = 0;
            impl->input.read(static_cast<uint8_t *>(bytes), &numRead, size);
            return numRead;
        }

        static void releaseInfoImpl(void *info) {
            // nothing
        }

        static void rewindImpl(void *info) {
            CoreGraphicsSequentialDataProviderImpl *impl = static_cast<CoreGraphicsSequentialDataProviderImpl *>(info);
            impl->input.reset();
        }

        static off_t skipForwardImpl(void *info, off_t count) {
            CoreGraphicsSequentialDataProviderImpl *impl = static_cast<CoreGraphicsSequentialDataProviderImpl *>(info);
            TAKErr code = impl->input.skip(count);
            if (code != TE_Ok)
                return 0;
            return count;
        }

        MemoryInput2 &input;
        atakmap::util::CancelationToken cancelationToken;
        CGDataProviderSequentialCallbacks callbacks;
    };

    void deleteBitmap2(const TAK::Engine::Renderer::Bitmap2 *bitmap) {
        delete bitmap;
    }

    BitmapPtr createBitmapWithCGImage(CGImageRef image, size_t width, size_t height, bool verticalFlip) {

        BitmapPtr bitmap(new Bitmap2(width, height, Bitmap2::RGBA32), ::deleteBitmap2);

        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(bitmap->getData(), width, height, 8, 4 * width,
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

    void freeData(const uint8_t *data) {
        ::free((void *)data);
    }

    TAKErr readToMemory(DataInput2 &input, MemoryInput2 &dest) {

        size_t len = 0;
        size_t capacity = 1024;
        uint8_t *dataPtr = static_cast<uint8_t *>(::malloc(capacity));
        if (!dataPtr) {
            return TE_OutOfMemory;
        }

        std::unique_ptr<const uint8_t, void (*)(const uint8_t *)> data(dataPtr, freeData);
        do {
            size_t left = capacity - len;
            if (left == 0) {
                size_t newCapacity = (3 * len) / 2;
                uint8_t *newDataPtr = static_cast<uint8_t *>(::realloc(dataPtr, newCapacity));
                if (!newDataPtr) {
                    return TE_OutOfMemory;
                }
                dataPtr = newDataPtr;
                capacity = newCapacity;
                data.release();
                data = std::unique_ptr<const uint8_t, void (*)(const uint8_t *)>(dataPtr, freeData);
                left = capacity - len;
            }

            size_t numRead = 0;
            TAKErr code = input.read(dataPtr + len, &numRead, left);
            if (code == TE_EOF) {
                break;
            }
            TE_CHECKRETURN_CODE(code);

            len += numRead;

        } while (true);

        return dest.open(std::move(data), len);
    }
}

TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts) NOTHROWS {

    MemoryInput2 memoryInput;
    TAKErr code = readToMemory(input, memoryInput);
    TE_CHECKRETURN_CODE(code);

    CoreGraphicsSequentialDataProviderImpl impl(memoryInput);
    CGDataProviderRef dataProvider = CGDataProviderCreateSequential(&impl, &impl.callbacks);
    CGImageRef image = NULL;

    image = CGImageCreateWithJPEGDataProvider(dataProvider, NULL, false, kCGRenderingIntentDefault);
    if (!image) {
        image = CGImageCreateWithPNGDataProvider(dataProvider, NULL, false, kCGRenderingIntentDefault);
    }

    CGDataProviderRelease(dataProvider);

    code = TE_Unsupported;
    if (image) {
        result = createBitmapWithCGImage(image, CGImageGetWidth(image), CGImageGetHeight(image), false);
        code = TE_Ok;
    }

    CGImageRelease(image);

    return code;
}


TAKErr TAK::Engine::Renderer::BitmapFactory2_decode(BitmapPtr &result, const char *bitmapFilePath, const BitmapDecodeOptions *opts) NOTHROWS
{
    TAKErr code(TE_Ok);

    Util::FileInput2 input;
    code = input.open(bitmapFilePath);
    TE_CHECKRETURN_CODE(code);

    code = BitmapFactory2_decode(result, input, opts);
    TE_CHECKRETURN_CODE(code);

    code = input.close();

    return code;
}
