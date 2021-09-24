#ifndef TAK_ENGINE_FEATURE_OGRDRIVERDEFINITION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_OGRDRIVERDEFINITION2_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

#include "feature/FeatureDataSource.h"

class OGRFeature;
class OGRGeometry;
class OGRLayer;

namespace TAK {
    namespace Engine {
        namespace Feature {

            class ENGINE_API OGRDriverDefinition2 : public TAK::Engine::Util::NonCopyable
            {
            protected:
                virtual ~OGRDriverDefinition2() NOTHROWS = 0;
            public :
                virtual const char* getDriverName() const NOTHROWS = 0;
                virtual atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding getFeatureEncoding() const NOTHROWS = 0;
                virtual Util::TAKErr setGeometry(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition> &featureDefinition,
                                                 const OGRFeature &, const OGRGeometry &) const NOTHROWS = 0;
                virtual Util::TAKErr getStyle(Port::String &value, const OGRFeature&, const OGRGeometry&) NOTHROWS = 0;
                virtual const char* getType() const NOTHROWS = 0;
                virtual unsigned int parseVersion() const NOTHROWS = 0;
                virtual Util::TAKErr skipFeature(bool *value, const OGRFeature&) NOTHROWS = 0;
                virtual Util::TAKErr skipLayer(bool *value, const OGRLayer&) NOTHROWS = 0;
                virtual bool layerNameIsPath() const NOTHROWS = 0;
            };

            typedef std::unique_ptr<OGRDriverDefinition2, void(*)(const OGRDriverDefinition2 *)> OGRDriverDefinition2Ptr;

            class ENGINE_API OGRDriverDefinition2Spi : public TAK::Engine::Util::NonCopyable
            {
            protected :
                virtual ~OGRDriverDefinition2Spi() NOTHROWS = 0;
            public :
                virtual Util::TAKErr create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS = 0;
                virtual const char* getType() const NOTHROWS = 0;
            };

			ENGINE_API Util::TAKErr OGRDriverDefinition2_create(OGRDriverDefinition2Ptr &value, const char *path, const char *type) NOTHROWS;
			ENGINE_API Util::TAKErr OGRDriverDefinition2_registerSpi(const std::shared_ptr<OGRDriverDefinition2Spi> &spi) NOTHROWS;
			ENGINE_API Util::TAKErr OGRDriverDefinition2_unregisterSpi(const OGRDriverDefinition2Spi *spi) NOTHROWS;
        }
    }
}

#endif  // #ifndef TAK_ENGINE_FEATURE_OGRDRIVERDEFINITION2_H_INCLUDED
