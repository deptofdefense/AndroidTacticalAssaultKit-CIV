#ifndef TAK_ENGINE_ELEVATION_ELEVATIONSOURCE_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONSOURCE_H_INCLUDED

#include "elevation/ElevationChunkCursor.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class ENGINE_API ElevationSource
            {
            public:
                class ENGINE_API OnContentChangedListener
                {
                public:
                    virtual ~OnContentChangedListener() NOTHROWS = 0;
                public:
                    virtual Util::TAKErr onContentChanged(const ElevationSource &source) NOTHROWS = 0;
                };

                class QueryParameters;
            public:
                virtual ~ElevationSource() NOTHROWS = 0;
            public:
                virtual const char *getName() const NOTHROWS = 0;
                virtual Util::TAKErr query(ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS = 0;
                virtual Feature::Envelope2 getBounds() const NOTHROWS = 0;
                virtual Util::TAKErr addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS = 0;
                virtual Util::TAKErr removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS = 0;
            };

            ENGINE_API Util::TAKErr ElevationSource_accept(bool *value, ElevationChunkCursor &cursor, const ElevationSource::QueryParameters &filter) NOTHROWS;
            ENGINE_API bool ElevationSource_resolutionDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
            ENGINE_API bool ElevationSource_resolutionAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
            ENGINE_API bool ElevationSource_ceDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
            ENGINE_API bool ElevationSource_ceAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
            ENGINE_API bool ElevationSource_leDesc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;
            ENGINE_API bool ElevationSource_leAsc(ElevationChunkCursor &a, ElevationChunkCursor &b) NOTHROWS;

            class ENGINE_API ElevationSource::QueryParameters
            {
            public :
                enum Order {
                    /** low to high resolution */
                    ResolutionAsc,
                    /** high to low resolution */
                    ResolutionDesc,
                    /** low to high error */
                    CEAsc,
                    /** high to low error */
                    CEDesc,
                    /** low to high error */
                    LEAsc,
                    /** high to low error */
                    LEDesc,
                };
            public :
                QueryParameters() NOTHROWS;
                QueryParameters(const QueryParameters &other) NOTHROWS;
                ~QueryParameters() NOTHROWS;
            public :
                Feature::Geometry2Ptr_const spatialFilter;
                double targetResolution;
                double maxResolution;
                double minResolution;
                Port::Collection<Port::String>::Ptr types;
                std::unique_ptr<bool, void(*)(const bool *)> authoritative;
                double minCE;
                double minLE;
                Port::Collection<Order>::Ptr order;
                std::unique_ptr<unsigned int, void(*)(const unsigned int *)> flags;
            };
        }
    }
}

#endif
