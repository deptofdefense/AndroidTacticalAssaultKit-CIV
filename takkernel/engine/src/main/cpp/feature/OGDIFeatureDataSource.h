#ifndef TAK_ENGINE_FEATURE_OGDIFEATUREDATASOURCE_H_INCLUDED
#define TAK_ENGINE_FEATURE_OGDIFEATUREDATASOURCE_H_INCLUDED

#include "feature/FeatureDataSource2.h"
#include "feature/Feature2.h"
#include "port/Platform.h"


namespace TAK {
	namespace Engine {
		namespace Feature {

			class ENGINE_API OGDIFeatureDataSource : public FeatureDataSource2
			{
			public:
				OGDIFeatureDataSource() NOTHROWS;
			public:
				virtual TAK::Engine::Util::TAKErr parse(ContentPtr& content, const char* file) NOTHROWS override;
				virtual const char* getName() const NOTHROWS override;
				virtual int parseVersion() const NOTHROWS override;
			private:
				static std::size_t ComputeAreaThreshold(unsigned int DPI);

			};
		}
	}
}

#endif
