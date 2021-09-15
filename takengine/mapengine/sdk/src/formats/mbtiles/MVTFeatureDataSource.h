#ifndef TAK_ENGINE_FORMATS_MBTILES_MVTFEATUREDATASOURCE_H_INCLUDED
#define TAK_ENGINE_FORMATS_MBTILES_MVTFEATUREDATASOURCE_H_INCLUDED

#include "feature/FeatureDataSource2.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace MBTiles {
                class ENGINE_API MVTFeatureDataSource : public Feature::FeatureDataSource2
                {
                public:
                    MVTFeatureDataSource() NOTHROWS;
                    ~MVTFeatureDataSource() NOTHROWS;
                public:
                    virtual Util::TAKErr parse(Feature::FeatureDataSource2::ContentPtr& content, const char* file) NOTHROWS;
                    virtual const char* getName() const NOTHROWS;
                    virtual int parseVersion() const NOTHROWS;
                };
            }
        }
    }
}

#endif
