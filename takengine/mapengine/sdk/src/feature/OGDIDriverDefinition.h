#ifndef TAK_ENGINE_FEATURE_OGDIDRIVERDEFINITION_H_INCLUDED
#define TAK_ENGINE_FEATURE_OGDIDRIVERDEFINITION_H_INCLUDED

#include "feature/DefaultDriverDefinition2.h"

namespace TAK {
	namespace Engine {
		namespace Feature {
			class ENGINE_API OGDIDriverDefinition : public DefaultDriverDefinition2
			{
			public:
				class ENGINE_API Spi;
			public:
				OGDIDriverDefinition() NOTHROWS;
			public:
				virtual Util::TAKErr getStyle(Port::String& value, const OGRFeature&, const OGRGeometry&) NOTHROWS;
			private:
				Util::TAKErr getFaccCodeStyle(Port::String& value, const OGRFeature&, const OGRGeometry&, const char* faccCode) NOTHROWS;
				int getFaccCodeColor(const char* faccCode, bool& usePen) NOTHROWS;
				const char* getFaccCodeIcon(const char* faccCode) NOTHROWS;
			};

			class ENGINE_API OGDIDriverDefinition::Spi : public OGRDriverDefinition2Spi
			{
			public:
				virtual Util::TAKErr create(OGRDriverDefinition2Ptr& value, const char* path) NOTHROWS;
				virtual const char* getType() const NOTHROWS;
			};
		}
	}
}

#endif
