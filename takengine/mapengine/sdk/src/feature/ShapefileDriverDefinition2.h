#ifndef TAK_ENGINE_FEATURE_SHAPEFILEDRIVERDEFINITION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_SHAPEFILEDRIVERDEFINITION2_H_INCLUDED

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
            class ENGINE_API ShapefileDriverDefinition2 : public DefaultDriverDefinition2
            {
            public :
                class ENGINE_API Spi;
            public :
                ShapefileDriverDefinition2() NOTHROWS;
            };

            class ENGINE_API ShapefileDriverDefinition2::Spi : public OGRDriverDefinition2Spi
            {
            public:
                virtual Util::TAKErr create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS;
                virtual const char *getType() const NOTHROWS;
            };
        }
    }
}

#endif
