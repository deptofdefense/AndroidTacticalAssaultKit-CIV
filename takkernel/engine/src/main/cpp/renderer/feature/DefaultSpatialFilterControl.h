#ifndef TAK_ENGINE_RENDERER_FEATURE_DEFAULTSPATIALFILTERCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_DEFAULTSPATIALFILTERCONTROL_H_INCLUDED

#include <vector>

#include "feature/Geometry.h"
#include "feature/FeatureDefinition2.h"
#include "feature/SpatialCalculator2.h"
#include "feature/SpatialFilter.h"
#include "renderer/feature/SpatialFilterControl.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class DefaultSpatialFilterControl : public TAK::Engine::Renderer::Feature::SpatialFilterControl
                {
                public:
                    DefaultSpatialFilterControl(bool *invalidateToken = nullptr) NOTHROWS;
                public :
                    Util::TAKErr accept(bool *value, TAK::Engine::Feature::FeatureDefinition2 &feature) NOTHROWS;
                    Util::TAKErr accept(bool *value, const atakmap::feature::Geometry &geom) NOTHROWS;
                    Util::TAKErr accept(bool *value, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS;
                    TAK::Engine::Feature::Envelope2 getIncludeMinimumBoundingBox() const NOTHROWS;
                public:
                    Util::TAKErr setSpatialFilters(Port::Collection<std::shared_ptr<Engine::Feature::SpatialFilter>> *spatial_filters) NOTHROWS override;
                private:
                    mutable Thread::Mutex mutex_;
                    std::shared_ptr<Engine::Feature::SpatialCalculator2> spatial_calculator_;
                    std::vector<int64_t> include_filter_ids_;
                    std::vector<int64_t> exclude_filter_ids_;
                    std::shared_ptr<Engine::Feature::Envelope2> include_spatial_filter_envelope_;
                    bool *invalidate_token_;
                };
            }
        }
    }
}

#endif