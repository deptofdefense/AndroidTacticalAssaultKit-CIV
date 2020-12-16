#ifndef TAK_ENGINE_FEATURE_GPXDRIVERDEFINITION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_GPXDRIVERDEFINITION2_H_INCLUDED

#include "feature/DefaultDriverDefinition2.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            /**
             * Support loading GPX files as ATAK map items
             *
             * @author byoung
             */
            class ENGINE_API GpxDriverDefinition2 : public DefaultDriverDefinition2
            {
            public :
                class ENGINE_API Spi;
            public :
                GpxDriverDefinition2() NOTHROWS;
            public :
                virtual Util::TAKErr skipFeature(bool *value, const OGRFeature&) NOTHROWS;
                virtual Util::TAKErr skipLayer(bool *value, const OGRLayer&) NOTHROWS;
            };

            class ENGINE_API GpxDriverDefinition2::Spi : public OGRDriverDefinition2Spi
            {
            public:
                virtual Util::TAKErr create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS;
                virtual const char *getType() const NOTHROWS;
            };
        }
    }
}

#endif
