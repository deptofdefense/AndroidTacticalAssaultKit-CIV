////============================================================================
////
////    FILE:           OGR_FeatureDataSource.h
////
////    DESCRIPTION:    Feature data source that ingests ESRI shape files.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 15, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE_H_INCLUDED
#define ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE_H_INCLUDED

#include <cstddef>

#include "feature/FeatureDataSource.h"
#include "port/Platform.h"

#include "ogr_core.h"

namespace atakmap {
    namespace feature {
        class ENGINE_API OGR_FeatureDataSource : public FeatureDataSource
        {
        public:
            static const char* const DEFAULT_STROKE_COLOR_PROPERTY;
            static const char* const DEFAULT_STROKE_WIDTH_PROPERTY;
        public :
            OGR_FeatureDataSource();
            ~OGR_FeatureDataSource() NOTHROWS
            { }
        public ://  FeatureDataSource INTERFACE
            const char* getName() const NOTHROWS override
            {
#ifdef __ANDROID__
                return "ogr";
#else
                return "OGR";
#endif
            }

            Content* parseFile(const char* filePath) const override;

            unsigned int parseVersion() const NOTHROWS override
            { return 18; }
        public :
			static std::size_t ComputeAreaThreshold(unsigned int DPI);
            static std::size_t ComputeLevelOfDetail(std::size_t threshold, OGREnvelope env);
        private:
            std::size_t areaThreshold;
        };
    }
}

#endif  // #ifndef ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE_H_INCLUDED
