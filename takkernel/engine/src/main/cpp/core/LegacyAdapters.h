#ifndef TAK_ENGINE_CORE_LEGACYADAPTERS_H_INCLUDED
#define TAK_ENGINE_CORE_LEGACYADAPTERS_H_INCLUDED

#include <memory>

#include "core/Layer.h"
#include "core/Layer2.h"
#include "core/Projection.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory2.h"
#include "core/ProjectionSpi.h"
#include "core/ProjectionSpi3.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            /*****************************************************************/
            // Layer Adaptation

            //Util::TAKErr LegacyAdapters_adapt(Layer2Ptr &value, atakmap::core::LayerPtr &&layer) NOTHROWS;
            ENGINE_API Util::TAKErr LegacyAdapters_adapt(std::shared_ptr<Layer2> &value, const std::shared_ptr<atakmap::core::Layer> &layer) NOTHROWS;

            //Util::TAKErr LegacyAdapters_adapt(atakmap::core::LayerPtr &value, Layer2Ptr &&layer) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(std::shared_ptr<atakmap::core::Layer> &value, const std::shared_ptr<Layer2> &layer) NOTHROWS;

			ENGINE_API Util::TAKErr LegacyAdapters_find(std::shared_ptr<atakmap::core::Layer> &value, const Layer2 &layer) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_find(std::shared_ptr<Layer2> &value, const atakmap::core::Layer &layer) NOTHROWS;

            /*****************************************************************/
            // Projection Adaptation

			ENGINE_API Util::TAKErr LegacyAdapters_adapt(Projection2Ptr &value, std::unique_ptr<Projection, void(*)(const Projection *)> &&proj) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(Projection2Ptr &value, std::unique_ptr<Projection, void(*)(Projection *)> &&proj) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(std::unique_ptr<Projection, void(*)(const Projection *)> &value, Projection2Ptr &&proj) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(std::unique_ptr<Projection, void(*)(Projection *)> &value, Projection2Ptr &&proj) NOTHROWS;

			ENGINE_API Util::TAKErr LegacyAdapters_adapt(ProjectionSpi3Ptr &value, std::unique_ptr<atakmap::core::ProjectionSpi, void(*)(const atakmap::core::ProjectionSpi *)> &&spi) NOTHROWS;
			ENGINE_API Util::TAKErr LegacyAdapters_adapt(ProjectionSpi3Ptr &value, std::unique_ptr<ProjectionSpi2, void(*)(const ProjectionSpi2 *)> &&spi) NOTHROWS;
        }
    }
}

#endif
