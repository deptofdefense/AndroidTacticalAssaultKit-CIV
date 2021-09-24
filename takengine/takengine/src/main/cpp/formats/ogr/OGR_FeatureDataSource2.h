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


#ifndef ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE2_H_INCLUDED
#define ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE2_H_INCLUDED

#include <cstddef>


#include "feature/FeatureDataSource2.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace OGR{
class ENGINE_API OGR_FeatureDataSource2 : public Feature::FeatureDataSource2 {
    public:
    static const char* const DEFAULT_STROKE_COLOR_PROPERTY;
    static const char* const DEFAULT_STROKE_WIDTH_PROPERTY;

    OGR_FeatureDataSource2();
    ~OGR_FeatureDataSource2() NOTHROWS { }
                
    virtual const char* getName() const NOTHROWS {
#ifdef __ANDROID__
        return "ogr";
#else
        return "OGR";
#endif
    }

    virtual Util::TAKErr parse(ContentPtr& content, const char* file) NOTHROWS;

    virtual int parseVersion() const NOTHROWS { return 18; }

    private:
    std::size_t areaThreshold;
};
}
}
}
}

#endif  // #ifndef ATAKMAP_FEATURE_OGR_FEATURE_DATA_SOURCE_H_INCLUDED
