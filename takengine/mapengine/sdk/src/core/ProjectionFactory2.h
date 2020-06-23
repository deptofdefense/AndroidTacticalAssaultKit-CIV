#ifndef TAK_ENGINE_CORE_PROJECTION_FACTORY_H_INCLUDED
#define TAK_ENGINE_CORE_PROJECTION_FACTORY_H_INCLUDED

#include <memory>
#include <set>


#include "util/Error.h"
#include "port/Platform.h"
#include "thread/Mutex.h"

#include "core/Projection.h"

namespace TAK {
    namespace Engine {
        namespace Core {

            using atakmap::core::Projection;

            typedef std::unique_ptr<Projection, void(*)(Projection *)> ProjectionPtr2;

            class ENGINE_API ProjectionSpi2 {
            protected:
                virtual ~ProjectionSpi2();
            public:
                static ProjectionPtr2 nullProjectionPtr();
            public:
                virtual Util::TAKErr create(ProjectionPtr2 &value, const int srid) NOTHROWS = 0;
            };

            /*class ProjectionFactory2 {
            public:
            ProjectionFactory2();
            ~ProjectionFactory2();
            public:*/

            ENGINE_API ProjectionPtr2 ProjectionFactory2_getProjection(int srid);
            ENGINE_API void ProjectionFactory2_registerSpi(std::shared_ptr<ProjectionSpi2>  *spi);
            ENGINE_API void ProjectionFactory2_unregisterSpi(std::shared_ptr<ProjectionSpi2> *spi);
            ENGINE_API void ProjectionFactory2_setPreferSdkProjections(bool sdk);
            /*private:
            static std::set<ProjectionSpi2 *> spis;
            static PGSC::Mutex factoryMutex;
            static bool sdkPreferred;
            };*/

        }
    }
}

#endif /* TAK_ENGINE_CORE_PROJECTION_FACTORY_H_INCLUDED */
