#ifndef TAK_ENGINE_FEATURE_KMLFEATUREDATASOURCE_H_INCLUDED
#define TAK_ENGINE_FEATURE_KMLFEATUREDATASOURCE_H_INCLUDED

#include "feature/FeatureDataSource2.h"
#include "feature/Feature2.h"
#include "port/Platform.h"


namespace TAK {
    namespace Engine {
        namespace Feature {

			class ENGINE_API KmlFeatureDataSource : public FeatureDataSource2
			{
			public:
				KmlFeatureDataSource() NOTHROWS;
			public:
				virtual TAK::Engine::Util::TAKErr parse(ContentPtr &content, const char *file) NOTHROWS;
				virtual const char *getName() const NOTHROWS;
				virtual int parseVersion() const NOTHROWS;
			private:
			
			};
        }
    }
}

#endif
