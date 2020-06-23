#ifndef TAK_ENGINE_ELEVATION_ELEVATIONCHUNKFACTORY_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONCHUNKFACTORY_H_INCLUDED

#include "elevation/ElevationChunk.h"
#include "feature/Polygon2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class ENGINE_API Sampler
            {
            public :
                virtual ~Sampler() NOTHROWS = 0;
            public :
                virtual Util::TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS = 0;
            public :
                virtual Util::TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS;
            };

            typedef std::unique_ptr<Sampler, void(*)(const Sampler *)> SamplerPtr;

            class ENGINE_API DataLoader
            {
            public :
                virtual ~DataLoader() NOTHROWS = 0;
            public :
                virtual Util::TAKErr createData(ElevationChunkDataPtr &value) NOTHROWS = 0;
            };

            typedef std::unique_ptr<DataLoader, void(*)(const DataLoader *)> DataLoaderPtr;

            ENGINE_API Util::TAKErr ElevationChunkFactory_create(ElevationChunkPtr &value, const char *type, const char *uri, const unsigned int flags, const double resolution, const Feature::Polygon2 &bounds, const double ce, const double le, const bool authoritative, DataLoaderPtr &&dataLoader) NOTHROWS;
            ENGINE_API Util::TAKErr ElevationChunkFactory_create(ElevationChunkPtr &value, const char *type, const char *uri, const unsigned int flags, const double resolution, const Feature::Polygon2 &bounds, const double ce, const double le, const bool authoritative, SamplerPtr &&sampler) NOTHROWS;
        }
    }
}

#endif
