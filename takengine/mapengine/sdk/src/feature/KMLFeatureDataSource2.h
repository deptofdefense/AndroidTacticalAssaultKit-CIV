#ifndef TAK_ENGINE_FEATURE_KMLFEATUREDATASOURCE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_KMLFEATUREDATASOURCE2_H_INCLUDED

#include "feature/FeatureDataSource2.h"
#include "feature/Feature2.h"
#include "port/Platform.h"

//XXX-- temporary solution for fallback
#include "feature/KmlFeatureDataSource.h"

namespace TAK {
    namespace Engine {
        namespace Feature {

            class ENGINE_API KMLFeatureDataSource2 : public FeatureDataSource2
            {
            public:
                KMLFeatureDataSource2() NOTHROWS;
                virtual ~KMLFeatureDataSource2() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr parse(ContentPtr &content, const char *file) NOTHROWS;
                virtual const char *getName() const NOTHROWS;
                virtual int parseVersion() const NOTHROWS;

            private:
                KmlFeatureDataSource legacyDataSource;
            };
        }
    }
}

#endif