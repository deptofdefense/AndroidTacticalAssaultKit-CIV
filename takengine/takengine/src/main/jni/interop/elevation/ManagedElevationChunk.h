//
// Created by GeoDev on 12/13/2019.
//

#ifndef TAKENGINE_INTEROP_ELEVATION_MANAGEDELEVATIONCHUNK_H_INCLUDED
#define TAKENGINE_INTEROP_ELEVATION_MANAGEDELEVATIONCHUNK_H_INCLUDED

#include <jni.h>

#include <elevation/ElevationChunk.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Elevation {
            class ManagedElevationChunk : public TAK::Engine::Elevation::ElevationChunk
            {
            public :
                ManagedElevationChunk(JNIEnv *env, jobject impl) NOTHROWS;
                virtual ~ManagedElevationChunk() NOTHROWS;
            public :
                virtual const char *getUri() const NOTHROWS;
                virtual const char *getType() const NOTHROWS;
                virtual double getResolution() const NOTHROWS;
                virtual const TAK::Engine::Feature::Polygon2 *getBounds() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr createData(TAK::Engine::Elevation::ElevationChunkDataPtr &value) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS;
                virtual double getCE() const NOTHROWS;
                virtual double getLE() const NOTHROWS;
                virtual bool isAuthoritative() const NOTHROWS;
                virtual unsigned int getFlags() const NOTHROWS;
            public :
                jobject impl;
            private :
                TAK::Engine::Port::String uri;
                TAK::Engine::Port::String type;
                TAK::Engine::Feature::Geometry2Ptr_const bounds;
            };
        }
    }
}

#endif //TAKENGINE_INTEROP_ELEVATION_MANAGEDELEVATIONCHUNK_H_INCLUDED
