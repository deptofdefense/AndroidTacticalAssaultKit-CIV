#ifndef TAK_ENGINE_FEATURE_DEFAULTDRIVERDEFINITION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_DEFAULTDRIVERDEFINITION2_H_INCLUDED

#include "feature/OGRDriverDefinition2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

#include "feature/FeatureDataSource.h"

class OGRFeature;
class OGRGeometry;
class OGRLayer;

namespace TAK {
    namespace Engine {
        namespace Feature {
            class DefaultDriverDefinition2 : public OGRDriverDefinition2
            {
            public:
                DefaultDriverDefinition2(const char* driverName,
                                         const char* driverType,
                                         unsigned int version) NOTHROWS;
            protected :
                DefaultDriverDefinition2(const char* driverName,
                                         const char* driverType,
                                         unsigned int version,
                                         atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding,
                                         float strokeWidth = 2.0,
                                         unsigned int strokeColor = 0xFFFFFFFF) NOTHROWS;    // ARGB
            public:
                virtual const char* getDriverName() const NOTHROWS override;
                virtual atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding getFeatureEncoding() const NOTHROWS override;
                virtual Util::TAKErr setGeometry(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition> &featureDefinition,
                                                 const OGRFeature &, const OGRGeometry &) const NOTHROWS override;
                virtual Util::TAKErr getStyle(Port::String &value, const OGRFeature&, const OGRGeometry&) NOTHROWS override;
                virtual const char* getType() const NOTHROWS override;
                virtual unsigned int parseVersion() const NOTHROWS override;
                virtual Util::TAKErr skipFeature(bool *value, const OGRFeature&) NOTHROWS override;
                virtual Util::TAKErr skipLayer(bool *value, const OGRLayer&) NOTHROWS override;
                virtual bool layerNameIsPath() const NOTHROWS override;
            protected :
                const char* getDefaultLineStringStyle() const NOTHROWS;
                const char* getDefaultPointStyle() const NOTHROWS;
                const char* getDefaultPolygonStyle() const NOTHROWS;
            private:
                virtual Util::TAKErr createDefaultLineStringStyle(Port::String &value) const NOTHROWS;
                virtual Util::TAKErr createDefaultPointStyle(Port::String &value) const NOTHROWS;
                virtual Util::TAKErr createDefaultPolygonStyle(Port::String &value) const NOTHROWS;
            protected :
                Port::String driverName;
                Port::String driverType;
                unsigned int version;
                atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding encoding;
                float strokeWidth;
                unsigned int strokeColor;           // In 0xAARRGGBB format.
                mutable Port::String lineStringStyle;
                mutable Port::String pointStyle;
                mutable Port::String polygonStyle;
                static unsigned int currentColor;
                const static unsigned int colors[];
            };
        }
    }
}

#endif  // #ifndef TAK_ENGINE_FEATURE_DEFAULTDRIVERDEFINITION2_H_INCLUDED