


#include "renderer/map/layer/raster/gdal/GdalGraphicUtils.h"

#include "util/Logging.h"

#include "renderer/GL.h"

#include <stdexcept>
#include <cstdint>
#include <cstdlib>
#include <cstring>

using namespace atakmap::raster;

using namespace atakmap::renderer::map::layer::raster::gdal;

using namespace atakmap::util;

using namespace atakmap::raster::tilereader;

namespace {
    
    inline bool isLittleEndian() {
        uint32_t v = 1;
        return (*((uint8_t *)&v)) != 0;
    }
    
    template<class T>
    class ScopePointer
    {
        public :
        ScopePointer(size_t len);
        ~ScopePointer();
        public :
        T &operator[](const size_t i);
        public :
        const size_t capacity;
        T * const ptr;
    };
    
    int getBufferSize(int glTexFormat, int glTexType, int width, int height);
    
    void MonoToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                      void *buffer, const uint8_t *data, int width, int height);
    void MonoToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                            void *buffer, const uint8_t *data, int width, int height);
    void MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                void *buffer, const uint8_t *data, int width, int height);
    void MonoToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                 void *buffer, const uint8_t *data, int width, int height);
    void MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                       void *buffer, const uint8_t *data, int width, int height);
    void MonoToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height);
    void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height);
    
    
    // tile data to bitmap
    void MonoToBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void MonoAlphaBIPtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void MonoAlphaBSQtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void MonoAlphaBILtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGB_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGB_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGB_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGBA_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGBA_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void RGBA_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void ARGB_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void ARGB_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    void ARGB_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width, int height);
    
    // bitmap to tile data
    void ARGBtoMono(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoMonoAlphaBIP(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoMonoAlphaBSQ(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoMonoAlphaBIL(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGB_BIP(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGB_BSQ(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGB_BIL(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGBA_BIP(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGBA_BSQ(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoRGBA_BIL(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoARGB_BIP(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoARGB_BSQ(uint32_t *argb, uint8_t *data, int width, int height);
    void ARGBtoARGB_BIL(uint32_t *argb, uint8_t *data, int width, int height);
}

GdalGraphicUtils::GdalGraphicUtils()
{}

int GdalGraphicUtils::getBufferFormat(TileReader::Format format)
{
    switch (format) {
        case TileReader::Format::MONOCHROME:
            return GL_LUMINANCE;
        case TileReader::Format::MONOCHROME_ALPHA:
            return GL_LUMINANCE_ALPHA;
        case TileReader::Format::RGB:
            return GL_RGB;
        case TileReader::Format::RGBA:
            return GL_RGBA;
        case TileReader::Format::ARGB:
            return GL_RGBA;
        default:
            throw std::invalid_argument("format");
    }
}

int GdalGraphicUtils::getBufferType(TileReader::Format format)
{
    switch (format) {
        case TileReader::Format::MONOCHROME:
            return GL_UNSIGNED_BYTE;
        case TileReader::Format::MONOCHROME_ALPHA:
            return GL_UNSIGNED_BYTE;
        case TileReader::Format::RGB:
            return GL_UNSIGNED_BYTE;
        case TileReader::Format::RGBA:
            return GL_UNSIGNED_BYTE;
        case TileReader::Format::ARGB:
            return GL_UNSIGNED_BYTE;
        default:
            throw std::invalid_argument("format");
    }
}

void *GdalGraphicUtils::createBuffer(const void *data, int width, int height, TileReader::Interleave interleave, TileReader::Format format, int glFormat, int glType)
{
    void *retval = new uint8_t[getBufferSize(glFormat, glType, width, height)];
    fillBuffer(retval, data, width, height, interleave, format, glFormat,
               glType);
    return retval;
}

void GdalGraphicUtils::freeBuffer(void *buffer)
{
    auto *data = static_cast<uint8_t *>(buffer);
    delete[] data;
}

void GdalGraphicUtils::fillBuffer(void *retval, const void *data, int width,
                                  int height, TileReader::Interleave interleave,
                                  TileReader::Format format, int glFormat, int glType)
{
    const auto *ptr = static_cast<const uint8_t *>(data);
    fillBuffer(retval, ptr, width, height, interleave, format, glFormat, glType);
}

void GdalGraphicUtils::fillBuffer(void *retval, const uint8_t *data, int width,
                                  int height, TileReader::Interleave interleave,
                                  TileReader::Format format, int glFormat, int glType) {
    
    switch (format) {
        case TileReader::Format::MONOCHROME:
            switch (glFormat) {
                case GL_LUMINANCE:
                    switch (glType) {
                        case GL_UNSIGNED_BYTE:
                            MonoToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                         retval, data, width, height);
                            break;
                        default:
                            throw std::invalid_argument("unknown");
                    }
                    break;
                case GL_LUMINANCE_ALPHA:
                    switch (glType) {
                        case GL_UNSIGNED_BYTE:
                            MonoToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                            break;
                        default:
                            throw std::invalid_argument("unknown");
                    }
                    break;
                case GL_RGB:
                    switch (glType) {
                        case GL_UNSIGNED_BYTE:
                            MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(retval,
                                                                   data, width, height);
                            break;
                        case GL_UNSIGNED_SHORT_5_6_5:
                            MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                          retval, data, width, height);
                            break;
                        default:
                            throw std::invalid_argument("unknown");
                    }
                    break;
                case GL_RGBA:
                    switch (glType) {
                        case GL_UNSIGNED_BYTE:
                            MonoToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(retval,
                                                                    data, width, height);
                            break;
                        case GL_UNSIGNED_SHORT_5_5_5_1:
                            MonoToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                             retval, data, width, height);
                            break;
                        default:
                            throw std::invalid_argument("unknown");
                    }
                    break;
                default:
                    throw std::invalid_argument("Illegal State");
            }
            break;
        case TileReader::Format::MONOCHROME_ALPHA:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                               retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                                   retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                             retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BSQ:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                               retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                                   retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                             retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BIL:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                               retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                                   retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                             retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
            }
            break;
        case TileReader::Format::RGB:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                              retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                        retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BSQ:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                              retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                        retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BIL:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                    retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                          retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                              retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                        retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
            }
            break;
        case TileReader::Format::RGBA:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BSQ:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BIL:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
            }
            break;
        case TileReader::Format::ARGB:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BSQ:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
                case TileReader::Interleave::BIL:
                    switch (glFormat) {
                        case GL_LUMINANCE:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                                                     retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_LUMINANCE_ALPHA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                                           retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGB:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                                               retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_6_5:
                                    ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                                                      retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        case GL_RGBA:
                            switch (glType) {
                                case GL_UNSIGNED_BYTE:
                                    ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                                                retval, data, width, height);
                                    break;
                                case GL_UNSIGNED_SHORT_5_5_5_1:
                                    ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                                         retval, data, width, height);
                                    break;
                                default:
                                    throw std::invalid_argument("unknown");
                            }
                            break;
                        default:
                            throw std::invalid_argument("Illegal State");
                    }
                    break;
            }
            break;
        default:
            throw std::invalid_argument("Illegal State");
    }
}

/*PixelFormat GdalGraphicUtils::getBitmapConfig(TileReader::Format format) {
    switch (format) {
        case TileReader::Format::MONOCHROME:
        case TileReader::Format::RGB:
            // XXX - opengl is skewing texture subimages that don't have
            // power of 2 dimensions for non-argb32 bitmaps
            // return Bitmap.Config.RGB_565;
        case TileReader::Format::MONOCHROME_ALPHA:
        case TileReader::Format::RGBA:
        case TileReader::Format::ARGB:
        default:
            return PixelFormat::Format32bppArgb;
    }
}*/

/*Bitmap ^GdalGraphicUtils::createBitmap(array<System::Byte> ^data, int width, int height,
                                       TileReader::Interleave interleave, TileReader::Format format) {
    
    pin_ptr<uint8_t> ptr = &data[0];
    return createBitmap(ptr, width, height, interleave, format,
                        getBitmapConfig(format));
}

Bitmap ^GdalGraphicUtils::createBitmap(void *data, int width, int height,
                                       TileReader::Interleave interleave, TileReader::Format format) {
    
    return createBitmap(data, width, height, interleave, format,
                        getBitmapConfig(format));
}

Bitmap ^GdalGraphicUtils::createBitmap(void *tileData, int width, int height,
                                       TileReader::Interleave interleave, TileReader::Format format,
                                       PixelFormat config)
{
    Bitmap ^bitmap = gcnew Bitmap(width, height, PixelFormat::Format32bppArgb);
    BitLocker bits(bitmap, PixelFormat::Format32bppArgb);
    
    uint32_t *argb = bits.get<uint32_t>();
    uint8_t *data = static_cast<uint8_t *>(tileData);
    
    switch (format) {
        case TileReader::Format::MONOCHROME:
            MonoToBitmap(argb, data, width, height);
            break;
            
#define INTERLEAVE_CASE(ileave, st) \
case TileReader::Interleave::ileave : \
st##ileave##toBitmap(argb, data, width, height); \
break;
            
#define INTERLEAVE_SWITCH(st) \
switch(interleave) { \
INTERLEAVE_CASE(BIP, st); \
INTERLEAVE_CASE(BIL, st); \
INTERLEAVE_CASE(BSQ, st); \
default : throw std::invalid_argument("Illegal State"); \
}
            
#define INTERLEAVED_FORMAT_CASE(l, st) \
case TileReader::Format::l : \
INTERLEAVE_SWITCH(st);
            
            INTERLEAVED_FORMAT_CASE(MONOCHROME_ALPHA, MonoAlpha);
            INTERLEAVED_FORMAT_CASE(RGB, RGB_);
            INTERLEAVED_FORMAT_CASE(RGBA, RGBA_);
            INTERLEAVED_FORMAT_CASE(ARGB, ARGB_);
#undef INTERLEAVE_CASE
#undef INTERLEAVE_SWITCH
#undef INTERLEAVED_FORMAT_CASE
        default: throw std::invalid_argument("Illegal State");
    }
    
    
    if (config == bitmap->PixelFormat)
        return bitmap;
    
    Bitmap ^retval = gcnew Bitmap(width, height, config);
    Graphics ^g2d = Graphics::FromImage(retval);
    g2d->DrawImage(bitmap, 0, 0);
    delete g2d;
    
    return retval;
}*/

/**************************************************************************/

/*void GdalGraphicUtils::getBitmapData(Bitmap ^bitmap, array<System::Byte> ^data, int width,
                                     int height, TileReader::Interleave interleave,
                                     TileReader::Format format)
{
    pin_ptr<uint8_t> ptr = &data[0];
    getBitmapData(bitmap, ptr, width, height, interleave, format);
}

void GdalGraphicUtils::getBitmapData(Bitmap ^bitmap, void *dst, int width,
                                     int height, TileReader::Interleave interleave,
                                     TileReader::Format format)
{
    BitLocker bdata(bitmap, PixelFormat::Format32bppArgb);
    
    uint8_t *data = static_cast<uint8_t *>(dst);
    uint32_t *argb = bdata.get<uint32_t>();
    
    switch (format) {
        case TileReader::Format::MONOCHROME:
            ARGBtoMono(argb, data, width, height);
            break;
        case TileReader::Format::MONOCHROME_ALPHA:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    ARGBtoMonoAlphaBIP(argb, data, width, height);
                    break;
                case TileReader::Interleave::BIL:
                    ARGBtoMonoAlphaBIL(argb, data, width, height);
                    break;
                case TileReader::Interleave::BSQ:
                    ARGBtoMonoAlphaBSQ(argb, data, width, height);
                    break;
                default:
                    throw std::invalid_argument("Illegal State");
            }
            break;
        case TileReader::Format::RGB:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    ARGBtoRGB_BIP(argb, data, width, height);
                    break;
                case TileReader::Interleave::BIL:
                    ARGBtoRGB_BIL(argb, data, width, height);
                    break;
                case TileReader::Interleave::BSQ:
                    ARGBtoRGB_BSQ(argb, data, width, height);
                    break;
                default:
                    throw std::invalid_argument("Illegal State");
            }
            break;
        case TileReader::Format::RGBA:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    ARGBtoRGBA_BIP(argb, data, width, height);
                    break;
                case TileReader::Interleave::BIL:
                    ARGBtoRGBA_BIL(argb, data, width, height);
                    break;
                case TileReader::Interleave::BSQ:
                    ARGBtoRGBA_BSQ(argb, data, width, height);
                    break;
                default:
                    throw std::invalid_argument("Illegal State");
            }
            break;
        case TileReader::Format::ARGB:
            switch (interleave) {
                case TileReader::Interleave::BIP:
                    ARGBtoARGB_BIP(argb, data, width, height);
                    break;
                case TileReader::Interleave::BIL:
                    ARGBtoARGB_BIL(argb, data, width, height);
                    break;
                case TileReader::Interleave::BSQ:
                    ARGBtoARGB_BSQ(argb, data, width, height);
                    break;
                default:
                    throw std::invalid_argument("Illegal State");
            }
            break;
        default:
            throw std::invalid_argument("Illegal State");
    }
}*/

namespace {
    template<class T>
    ScopePointer<T>::ScopePointer(size_t len) :
    capacity(sizeof(T)*len),
    ptr(new T[len])
    {}
    
    template<class T>
    ScopePointer<T>::~ScopePointer()
    {
        delete ptr;
    }
    
    template<class T>
    T &ScopePointer<T>::operator[](const size_t i)
    {
        return ptr[i];
    }
    
    /*BitLocker::BitLocker(Bitmap ^bmp, PixelFormat fmt) :
    bitmap(getFormatBitmap(bmp, fmt)),
    data(bmp->LockBits(System::Drawing::Rectangle(0, 0, bmp->Width, bmp->Height), ImageLockMode::ReadOnly, fmt))
    {
        cleanup = (bitmap != bmp);
    }
    
    BitLocker::~BitLocker()
    {
        bitmap->UnlockBits(data);
        if (cleanup)
            delete bitmap;
    }
    
    void *BitLocker::raw()
    {
        return data->Scan0.ToPointer();
    }
    
    template<class T>
    T *BitLocker::get()
    {
        return static_cast<T *>(data->Scan0.ToPointer());
    }
    
    Bitmap ^BitLocker::getFormatBitmap(Bitmap ^bmp, PixelFormat fmt)
    {
        if (fmt == bmp->PixelFormat)
            return bmp;
        Bitmap ^retval = gcnew Bitmap(bmp->Width, bmp->Height, fmt);
        Graphics ^g2d = Graphics::FromImage(retval);
        g2d->DrawImage(bmp, 0, 0);
        delete g2d;
        return retval;
    }*/
    
    int getBufferSize(int glTexFormat, int glTexType, int width, int height)
    {
        int bytesPerPixel;
        if (glTexFormat == GL_LUMINANCE) {
            bytesPerPixel = 1;
        }
        else if (glTexFormat == GL_LUMINANCE_ALPHA
                 || glTexType == GL_UNSIGNED_SHORT_5_5_5_1
                 || glTexType == GL_UNSIGNED_SHORT_5_6_5) {
            bytesPerPixel = 2;
        }
        else if (glTexFormat == GL_RGB) {
            bytesPerPixel = 3;
        }
        else if (glTexFormat == GL_RGBA) {
            bytesPerPixel = 4;
        }
        else {
            throw std::invalid_argument("Illegal State");
        }
        return bytesPerPixel * (width * height);
    }
    
    /*************************************************************************/
    
#define BUFFER_DECLS(scanlineSize) \
uint8_t *pBuffer = static_cast<uint8_t *>(buffer); \
ScopePointer<uint8_t> scanline((scanlineSize));
    
#define COPY_SCANLINE() \
memcpy(pBuffer, scanline.ptr, scanline.capacity); \
pBuffer += scanline.capacity;
    
#define COPY_BUFFER(size) \
const uint8_t *ptr = &data[0]; \
memcpy(buffer, ptr, (size));
    
    void MonoToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                      void *buffer, const uint8_t *data, int width, int height)
    {
        COPY_BUFFER(width * height);
    }
    
    void MonoToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                            void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 3);
        int didx = 0;
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                 void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int didx = 0;
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                       void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int p;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int p;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | 0x01);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | 0x01);
                    
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoAlphaBIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width);
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                scanline[j] = data[idx];
                idx += 2;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        COPY_BUFFER(width * height * 2);
    }
    
    void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 3);
        int didx = 0;
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx];
                didx += 2;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int didx = 0;
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx++];
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height) {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int p;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx] >> 3) & 0xFF;
                    didx += 2;
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx] >> 3) & 0xFF;
                    didx += 2;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoAlphaBIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int p;
        int alpha;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    alpha = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    alpha = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoAlphaBSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        COPY_BUFFER(width * height);
    }
    
    void MonoAlphaBSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx0++];
                scanline[sidx++] = data[didx1++];
                ;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height)
    {
        ::MonoToBuffer__GL_RGB__GL_UNSIGNED_BYTE(buffer, data, width, height);
    }
    
    void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                p = data[didx0++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx1++];
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height)
    {
        ::MonoToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(buffer, data, width,
                                                      height);
    }
    
    void MonoAlphaBSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        int p;
        int alpha;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] >> 3) & 0xFF;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] >> 3) & 0xFF;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoAlphaBILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        // XXX -
#if 0
        uint8_t *pBuffer = static_cast<uint8_t *>(buffer);
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                buffer.put(data, (i * width) * 2, width);
#endif
    }
    
    void MonoAlphaBILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0;
        int didx1;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx0 = i * width * 2;
            didx1 = didx0 + width;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx0++];
                scanline[sidx++] = data[didx1++];
                ;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                        void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 3);
        int didx;
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx = i * width * 2;
            for (int j = 0; j < width; j++) {
                p = data[didx++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int didx0 = 0;
        int didx1 = (width * height);
        int sidx;
        uint8_t p;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            didx0 = i * width * 2;
            didx1 = didx0 + width;
            for (int j = 0; j < width; j++) {
                p = data[didx0++];
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = p;
                scanline[sidx++] = data[didx1++];
            }
            COPY_SCANLINE();
        }
    }
    
    void MonoAlphaBILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                               void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx;
        int sidx;
        int p;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx = i * width * 2;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx = i * width * 2;
                for (int j = 0; j < width; j++) {
                    p = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | p);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void MonoAlphaBILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                                  void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0;
        int didx1;
        int sidx;
        int p;
        int alpha;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * width * 2;
                didx1 = didx0 + width;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] >> 3) & 0xFF;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * width * 2;
                didx1 = didx0 + width;
                for (int j = 0; j < width; j++) {
                    p = (data[didx0++] >> 3) & 0xFF;
                    alpha = ((data[didx1++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((p << 3) | (p >> 2));
                    scanline[sidx++] = (uint8_t)((p << 6) | (p << 1) | alpha);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width);
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx++];
                g = data[idx++];
                b = data[idx++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[didx++];
                g = data[didx++];
                b = data[didx++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height)
    {
        COPY_BUFFER((width * height * 3));
    }
    
    void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        //Log::d(GdalGraphicUtils::TAG, "RGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5");
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 3);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 4);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = data[didx1++] >> 2;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = data[didx1++] >> 2;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                         void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width);
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                               void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                   void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width*3);
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width*4);
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = (uint8_t)0xFF;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                             void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 3);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | 0x01);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGBA_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width);
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx++];
                g = data[idx++];
                b = data[idx++];
                idx++; // alpha
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            COPY_SCANLINE();
        }
    }
    
    void RGBA_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 2);
        int didx = 0;
        int sidx = 0;
        int r;
        int g;
        int b;
        uint8_t a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[didx++];
                g = data[didx++];
                b = data[didx++];
                a = data[didx++]; // alpha
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            COPY_SCANLINE();
        }
    }
    
    void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        BUFFER_DECLS(width * 3);
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                didx++; // alpha
            }
            COPY_SCANLINE();
        }
    }
    
    void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        const uint8_t *ptr = &data[0];
        memcpy(buffer, ptr, width * height * 4);
    }
    
    void RGBA_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    didx++; // alpha
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    didx++; // alpha
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGBA_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                COPY_SCANLINE();
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                COPY_SCANLINE();
            }
        }
    }
    
    void RGBA_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        ::RGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(buffer, data, width,
                                                        height);
    }
    
    void RGBA_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        uint8_t a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                a = data[idx3++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        ::RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(buffer, data, width, height);
    }
    
    void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 4);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height) {
        ::RGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(buffer, data, width,
                                                         height);
    }
    
    void RGBA_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0 = 0;
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int didx3 = didx2 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        int a;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void RGBA_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width);
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        int r;
        int g;
        int b;
        uint8_t a;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                a = data[idx3++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 3);
        int idx0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 4);
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void RGBA_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void RGBA_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int didx3;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = didx2 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4);
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = didx2 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BIPToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width);
        int idx = 0;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                idx++; // alpha
                r = data[idx++];
                g = data[idx++];
                b = data[idx++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BIPToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx = 0;
        int sidx = 0;
        uint8_t a;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                a = data[didx++];
                r = data[didx++];
                g = data[didx++];
                b = data[didx++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = a;
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 3);
        int didx = 0;
        int sidx = 0;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                didx++; // alpha
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BIPToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    didx++; // alpha
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    didx++; // alpha
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 2) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 4);
        int didx = 0;
        int sidx = 0;
        uint8_t a;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                a = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = data[didx++];
                scanline[sidx++] = a;
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BIPToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    a = ((data[didx++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    r = (data[didx++] >> 3) & 0xFF;
                    g = (data[didx++] >> 3) & 0xFF;
                    b = (data[didx++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        
    }
    void ARGB_BSQToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width);
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BSQToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = 0;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 3);
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 4);
        int idx0 = (width * height);
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = 0;
        int sidx;
        for (int i = 0; i < height; i++) {
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BSQToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0 = (width * height);
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int sidx;
        int r;
        int g;
        int b;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = data[didx1++] >> 2;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = data[didx1++] >> 2;
                    b = (data[didx2++] >> 3) & 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BSQToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0 = (width * height);
        int didx1 = didx0 + (width * height);
        int didx2 = didx1 + (width * height);
        int didx3 = 0;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] >> 3) & 0xFF;
                    g = (data[didx1++] >> 3) & 0xFF;
                    b = (data[didx2++] >> 3) & 0xFF;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0xFF;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BILToBuffer__GL_LUMINANCE__GL_UNSIGNED_BYTE(
                                                          void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width);
        int idx0 = 0;
        int idx1;
        int idx2;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[j] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BILToBuffer__GL_LUMINANCE_ALPHA__GL_UNSIGNED_BYTE(
                                                                void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        int r;
        int g;
        int b;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = i * (width * 4);
            sidx = 0;
            for (int j = 0; j < width; j++) {
                r = data[idx0++];
                g = data[idx1++];
                b = data[idx2++];
                scanline[sidx++] = (uint8_t)((((r * 66 + g * 129 + b * 25) + 128) >> 8) + 16);
                scanline[sidx++] = (uint8_t)data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_BYTE(
                                                    void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width*3);
        int idx0 = 0;
        int idx1;
        int idx2;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_SHORT_5_5_5_1(
                                                              void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int didx3;
        int sidx;
        int r;
        int g;
        int b;
        int a;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = i * (width * 4);
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline.ptr[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                didx3 = i * (width * 4);
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 3;
                    b = (data[didx2++] & 0xFF) >> 3;
                    a = ((data[didx3++] & 0xFF) != 0xFF) ? 0x00 : 0x01;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 2));
                    scanline[sidx++] = (uint8_t)((g << 6) | (b << 1) | a);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BILToBuffer__GL_RGB__GL_UNSIGNED_SHORT_5_6_5(
                                                           void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width * 2);
        int didx0;
        int didx1;
        int didx2;
        int sidx;
        int r;
        int g;
        int b;
        if (isLittleEndian()) {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
        else {
            for (int i = 0; i < height; i++) {
                sidx = 0;
                didx0 = i * (width * 4) + width;
                didx1 = didx0 + width;
                didx2 = didx1 + width;
                for (int j = 0; j < width; j++) {
                    r = (data[didx0++] & 0xFF) >> 3;
                    g = (data[didx1++] & 0xFF) >> 2;
                    b = (data[didx2++] & 0xFF) >> 3;
                    scanline[sidx++] = (uint8_t)((r << 3) | (g >> 3));
                    scanline[sidx++] = (uint8_t)((g << 5) | b);
                }
                memcpy(pBuffer, scanline.ptr, scanline.capacity);
                pBuffer += scanline.capacity;
            }
        }
    }
    
    void ARGB_BILToBuffer__GL_RGBA__GL_UNSIGNED_BYTE(
                                                     void *buffer, const uint8_t *data, int width, int height)
    {
        auto *pBuffer = static_cast<uint8_t *>(buffer);
        ScopePointer<uint8_t> scanline(width*4);
        int idx0 = 0;
        int idx1;
        int idx2;
        int idx3;
        int sidx;
        for (int i = 0; i < height; i++) {
            idx0 = i * (width * 4) + width;
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = i * (width * 4);
            sidx = 0;
            for (int j = 0; j < width; j++) {
                scanline[sidx++] = data[idx0++];
                scanline[sidx++] = data[idx1++];
                scanline[sidx++] = data[idx2++];
                scanline[sidx++] = data[idx3++];
            }
            memcpy(pBuffer, scanline.ptr, scanline.capacity);
            pBuffer += scanline.capacity;
        }
    }
    
    /*************************************************************************/
    
    
    void MonoToBitmap(uint32_t *argb, const uint8_t *data, int width,
                      int height) {
        const int numPixels = (width * height);
        int b;
        for (int i = 0; i < numPixels; i++) {
            b = (data[i] & 0xFF);
            argb[i] = 0xFF000000 | (b << 16) | (b << 8) | b;
        }
    }
    
    void MonoAlphaBIPtoBitmap(uint32_t *argb, const uint8_t *data,
                              int width, int height) {
        const int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx++] & 0xFF);
            argb[i] = (b << 16) | (b << 8) | b;
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }
    
    void MonoAlphaBSQtoBitmap(uint32_t *argb, const uint8_t *data,
                              int width, int height) {
        const int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 16) | (b << 8) | b;
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }
    
    void MonoAlphaBILtoBitmap(uint32_t *argb, const uint8_t *data,
                              int width, int height) {
        int b;
        int idx0;
        int idx1;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 2);
            idx1 = idx0 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 16) | (b << 8) | b;
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 24);
            }
        }
    }
    
    void RGB_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                         int height) {
        const int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            argb[i] = 0xFF000000;
            
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx++] & 0xFF);
            argb[i] |= b;
        }
    }
    
    void RGB_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                         int height) {
        const int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            argb[i] = 0xFF000000;
            
            b = (data[idx0++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx2++] & 0xFF);
            argb[i] |= b;
        }
    }
    
    void RGB_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                         int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                argb[i] = 0xFF000000;
                
                b = (data[idx0++] & 0xFF);
                argb[i] |= (b << 16);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx2++] & 0xFF);
                argb[i] |= b;
            }
        }
    }
    
    void RGBA_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height) {
        const int numPixels = (width * height);
        int b;
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx++] & 0xFF);
            argb[i] = (b << 16);
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx++] & 0xFF);
            argb[i] |= b;
            b = (data[idx++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }
    
    void RGBA_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height) {
        const int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 16);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx2++] & 0xFF);
            argb[i] |= b;
            b = (data[idx3++] & 0xFF);
            argb[i] |= (b << 24);
        }
    }
    
    void RGBA_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 16);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx2++] & 0xFF);
                argb[i] |= b;
                b = (data[idx3++] & 0xFF);
                argb[i] |= (b << 24);
            }
        }
    }
    
    void ARGB_BIPtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height)
    {
        // XXX - need endian swap or Bitmap always native order
        memcpy(argb, data, width*height * 4);
    }
    
    void ARGB_BSQtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height) {
        const int numPixels = (width * height);
        int b;
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            b = (data[idx0++] & 0xFF);
            argb[i] = (b << 24);
            b = (data[idx1++] & 0xFF);
            argb[i] |= (b << 16);
            b = (data[idx2++] & 0xFF);
            argb[i] |= (b << 8);
            b = (data[idx3++] & 0xFF);
            argb[i] |= b;
        }
    }
    
    void ARGB_BILtoBitmap(uint32_t *argb, const uint8_t *data, int width,
                          int height) {
        int b;
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                b = (data[idx0++] & 0xFF);
                argb[i] = (b << 24);
                b = (data[idx1++] & 0xFF);
                argb[i] |= (b << 16);
                b = (data[idx2++] & 0xFF);
                argb[i] |= (b << 8);
                b = (data[idx3++] & 0xFF);
                argb[i] |= b;
            }
        }
    }
    
    
    /*************************************************************************/
    
    void ARGBtoMono(uint32_t *argb, uint8_t *data, int width,
                    int height)
    {
        const int numPixels = (width * height);
        for (int i = 0; i < numPixels; i++)
            data[i] = (uint8_t)(argb[i] & 0xFF);
    }
    
    void ARGBtoMonoAlphaBIP(uint32_t *argb, uint8_t *data, int width,
                            int height)
    {
        const int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (uint8_t)(argb[i] & 0xFF);
            data[idx++] = (uint8_t)((argb[i] >> 24) & 0xFF);
        }
    }
    
    void ARGBtoMonoAlphaBSQ(uint32_t *argb, uint8_t *data, int width,
                            int height)
    {
        const int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (uint8_t)(argb[i] & 0xFF);
            data[idx1++] = (uint8_t)((argb[i] >> 24) & 0xFF);
        }
    }
    
    void ARGBtoMonoAlphaBIL(uint32_t *argb, uint8_t *data, int width,
                            int height)
    {
        int idx0;
        int idx1;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 2);
            idx1 = idx0 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (uint8_t)(argb[i] & 0xFF);
                data[idx1++] = (uint8_t)((argb[i] >> 24) & 0xFF);
            }
        }
    }
    
    void ARGBtoRGB_BIP(uint32_t *argb, uint8_t *data, int width,
                       int height)
    {
        const int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (uint8_t)((argb[i] >> 16) & 0xFF);
            data[idx++] = (uint8_t)((argb[i] >> 8) & 0xFF);
            data[idx++] = (uint8_t)(argb[i] & 0xFF);
        }
    }
    
    void ARGBtoRGB_BSQ(uint32_t *argb, uint8_t *data, int width,
                       int height)
    {
        const int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (uint8_t)((argb[i] >> 16) & 0xFF);
            data[idx1++] = (uint8_t)((argb[i] >> 8) & 0xFF);
            data[idx2++] = (uint8_t)(argb[i] & 0xFF);
        }
    }
    
    void ARGBtoRGB_BIL(uint32_t *argb, uint8_t *data, int width,
                       int height)
    {
        int idx0;
        int idx1;
        int idx2;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (uint8_t)((argb[i] >> 16) & 0xFF);
                data[idx1++] = (uint8_t)((argb[i] >> 8) & 0xFF);
                data[idx2++] = (uint8_t)(argb[i] & 0xFF);
            }
        }
    }
    
    void ARGBtoRGBA_BIP(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        const int numPixels = (width * height);
        int idx = 0;
        for (int i = 0; i < numPixels; i++) {
            data[idx++] = (uint8_t)((argb[i] >> 16) & 0xFF);
            data[idx++] = (uint8_t)((argb[i] >> 8) & 0xFF);
            data[idx++] = (uint8_t)(argb[i] & 0xFF);
            data[idx++] = (uint8_t)((argb[i] >> 24) & 0xFF);
        }
    }
    
    void ARGBtoRGBA_BSQ(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        const int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (uint8_t)((argb[i] >> 16) & 0xFF);
            data[idx1++] = (uint8_t)((argb[i] >> 8) & 0xFF);
            data[idx2++] = (uint8_t)(argb[i] & 0xFF);
            data[idx3++] = (uint8_t)((argb[i] >> 24) & 0xFF);
        }
    }
    
    void ARGBtoRGBA_BIL(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (uint8_t)((argb[i] >> 16) & 0xFF);
                data[idx1++] = (uint8_t)((argb[i] >> 8) & 0xFF);
                data[idx2++] = (uint8_t)(argb[i] & 0xFF);
                data[idx3++] = (uint8_t)((argb[i] >> 24) & 0xFF);
            }
        }
    }
    
    void ARGBtoARGB_BIP(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        // XXX - endian ???
        memcpy(data, argb, width*height * 4);
    }
    
    void ARGBtoARGB_BSQ(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        const int numPixels = (width * height);
        int idx0 = 0;
        int idx1 = idx0 + (width * height);
        int idx2 = idx1 + (width * height);
        int idx3 = idx2 + (width * height);
        for (int i = 0; i < numPixels; i++) {
            data[idx0++] = (uint8_t)((argb[i] >> 24) & 0xFF);
            data[idx1++] = (uint8_t)((argb[i] >> 16) & 0xFF);
            data[idx2++] = (uint8_t)((argb[i] >> 8) & 0xFF);
            data[idx3++] = (uint8_t)(argb[i] & 0xFF);
        }
    }
    
    void ARGBtoARGB_BIL(uint32_t *argb, uint8_t *data, int width,
                        int height)
    {
        int idx0;
        int idx1;
        int idx2;
        int idx3;
        for (int i = 0; i < height; i++) {
            idx0 = (i * width * 3);
            idx1 = idx0 + width;
            idx2 = idx1 + width;
            idx3 = idx2 + width;
            for (int j = 0; j < width; j++) {
                data[idx0++] = (uint8_t)((argb[i] >> 24) & 0xFF);
                data[idx1++] = (uint8_t)((argb[i] >> 16) & 0xFF);
                data[idx2++] = (uint8_t)((argb[i] >> 8) & 0xFF);
                data[idx3++] = (uint8_t)(argb[i] & 0xFF);
            }
        }
    }
}
