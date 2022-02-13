#ifndef TAK_ENGINE_CORE_PROJECTIONSPI2_H_INCLUDED
#define TAK_ENGINE_CORE_PROJECTIONSPI2_H_INCLUDED

#include <memory>

#include "core/Projection2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ENGINE_API ProjectionSpi3
            {
            protected:
                ~ProjectionSpi3() NOTHROWS;
            public :
                virtual Util::TAKErr create(Projection2Ptr &value, const int srid) NOTHROWS = 0;
            };

            typedef std::unique_ptr<ProjectionSpi3, void(*)(const ProjectionSpi3 *)> ProjectionSpi3Ptr;
        }
    }
}
#endif
