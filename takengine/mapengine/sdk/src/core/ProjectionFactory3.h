#ifndef TAK_ENGINE_CORE_PROJECTIONFACTORY3_H_INCLUDED
#define TAK_ENGINE_CORE_PROJECTIONFACTORY3_H_INCLUDED

#include "core/Projection2.h"
#include "core/ProjectionSpi3.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            ENGINE_API Util::TAKErr ProjectionFactory3_create(Projection2Ptr &value, const int srid) NOTHROWS;
			ENGINE_API Util::TAKErr ProjectionFactory3_registerSpi(ProjectionSpi3Ptr &&spi, const int priority) NOTHROWS;
			ENGINE_API Util::TAKErr ProjectionFactory3_registerSpi(const std::shared_ptr<ProjectionSpi3>  &spi, const int priority) NOTHROWS;
			ENGINE_API Util::TAKErr ProjectionFactory3_unregisterSpi(const ProjectionSpi3 &spi) NOTHROWS;
			ENGINE_API Util::TAKErr ProjectionFactory3_setPreferSdkProjections(const bool sdk) NOTHROWS;
        }
    }
}
#endif
