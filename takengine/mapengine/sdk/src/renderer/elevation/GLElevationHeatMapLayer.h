#ifndef TAK_ENGINE_RENDERER_ELEVATION_GLELEVATIONHEATMAPLAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_GLELEVATIONHEATMAPLAYER_H_INCLUDED

#include "elevation/ElevationHeatMapLayer.h"
#include "renderer/core/GLLayer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                class ENGINE_API GLElevationHeatMapLayer : public TAK::Engine::Renderer::Core::GLLayer2,
                                                           public TAK::Engine::Elevation::ElevationHeatMapLayer::HeatMapListener,
                                                           public TAK::Engine::Core::Layer2::VisibilityListener
                {
                public :
                    GLElevationHeatMapLayer(TAK::Engine::Elevation::ElevationHeatMapLayer &subject) NOTHROWS;
                    ~GLElevationHeatMapLayer() NOTHROWS;
                public: // GLLayer2
                    virtual TAK::Engine::Core::Layer2 &getSubject() NOTHROWS;
                public : // GLMapRenderable2
                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderPass) NOTHROWS;
                    virtual void release() NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                public : // ElevationHeatMapLayer::HeatMapListener
                    virtual Util::TAKErr onColorChanged(const TAK::Engine::Elevation::ElevationHeatMapLayer& subject, const float saturation, const float value, const float alpha) NOTHROWS;
                    virtual Util::TAKErr onRangeChanged(const TAK::Engine::Elevation::ElevationHeatMapLayer& subject, const double max, const double min, const bool dynamicRange) NOTHROWS;
                public : // Layer2::VisibilityListener
                    virtual Util::TAKErr layerVisibilityChanged(const TAK::Engine::Core::Layer2 &layer, const bool visible) NOTHROWS;
                private :
                    TAK::Engine::Elevation::ElevationHeatMapLayer &subject_;
                    float saturation_;
                    float value_;
                    float alpha_;
                    struct {
                        struct {
                            double min_{ -100.0 };
                            double max_{ 8850.0 };
                        } absolute;
                        bool dynamic_{ false };
                    } range;
                    bool visible_;
                };

            }
        }
    }
}

#endif
