#ifndef ATAKMAP_RENDERER_RASTER_GDAL_GDALGRAPHICUTILS_H_INCLUDED
#define ATAKMAP_RENDERER_RASTER_GDAL_GDALGRAPHICUTILS_H_INCLUDED

#include <cstdint>

#include "raster/tilereader/TileReader.h"

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {
                    namespace gdal {
                        class GdalGraphicUtils {
                        private:
                            GdalGraphicUtils();
                        public:
                            static int getBufferFormat(atakmap::raster::tilereader::TileReader::Format format);
                            
                            static int getBufferType(atakmap::raster::tilereader::TileReader::Format format);
                            
                            static void *createBuffer(const void *data, int width, int height, atakmap::raster::tilereader::TileReader::Interleave interleave, atakmap::raster::tilereader::TileReader::Format format, int glFormat, int glType);
                            
                            static void freeBuffer(void *data);
                            
                            static void fillBuffer(void *retval, const void *data, int width,
                                                   int height, atakmap::raster::tilereader::TileReader::Interleave interleave,
                                                   atakmap::raster::tilereader::TileReader::Format format, int glFormat, int glType);
                            
                            static void fillBuffer(void *retval, const uint8_t *data, int width,
                                                   int height, atakmap::raster::tilereader::TileReader::Interleave interleave,
                                                   atakmap::raster::tilereader::TileReader::Format format, int glFormat, int glType);
                            
                            
                            //TODO--static System::Drawing::Imaging::PixelFormat getBitmapConfig(atakmap::raster::tilereader::TileReader::Format format);
                            
                            /*static System::Drawing::Bitmap ^createBitmap(array<System::Byte> ^data, int width, int height,
                                                                         atakmap::raster::tilereader::TileReader::Interleave interleave, atakmap::raster::tilereader::TileReader::Format format);
                            
                            static System::Drawing::Bitmap ^createBitmap(void *data, int width, int height,
                                                                         atakmap::raster::tilereader::TileReader::Interleave interleave, atakmap::raster::tilereader::TileReader::Format format);
                            
                            static System::Drawing::Bitmap ^createBitmap(void *data, int width, int height,
                                                                         atakmap::raster::tilereader::TileReader::Interleave interleave, atakmap::raster::tilereader::TileReader::Format format,
                                                                         System::Drawing::Imaging::PixelFormat config);*/
                            
                            /**************************************************************************/
                            
                            /*static void getBitmapData(System::Drawing::Bitmap ^bitmap, array<System::Byte> ^data, int width,
                                                      int height, atakmap::raster::tilereader::TileReader::Interleave interleave,
                                                      atakmap::raster::tilereader::TileReader::Format format);
                            
                            static void getBitmapData(System::Drawing::Bitmap ^bitmap, void *data, int width,
                                                      int height, atakmap::raster::tilereader::TileReader::Interleave interleave,
                                                      atakmap::raster::tilereader::TileReader::Format format);*/
                        };
                    }
                }
            }
        }
    }
}
#endif // ATAKMAP_RENDERER_RASTER_GDAL_GDALGRAPHICUTILS_H_INCLUDED
