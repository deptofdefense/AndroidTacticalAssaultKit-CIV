#ifndef TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINSLOPEANGLELAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINSLOPEANGLELAYER_H_INCLUDED

#include "elevation/TerrainSlopeAngleLayer.h"
#include "renderer/core/GLLayer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                class ENGINE_API GLTerrainSlopeAngleLayer : public TAK::Engine::Renderer::Core::GLLayer2,
                                                            public TAK::Engine::Elevation::TerrainSlopeAngleLayer::SlopeAngleListener,
                                                            public TAK::Engine::Core::Layer2::VisibilityListener
                {
                public :
                    GLTerrainSlopeAngleLayer(TAK::Engine::Elevation::TerrainSlopeAngleLayer &subject) NOTHROWS;
                    ~GLTerrainSlopeAngleLayer() NOTHROWS;
                public: // GLLayer2
                    virtual TAK::Engine::Core::Layer2 &getSubject() NOTHROWS;
                public : // GLMapRenderable2
                    virtual void draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS;
                    virtual void release() NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                private :
                    void drawSlopeAngle(const TAK::Engine::Renderer::Core::GLGlobeBase &view) NOTHROWS;
                    void drawLegend(const TAK::Engine::Renderer::Core::GLGlobeBase &view) NOTHROWS;
                public : // TerrainSlopeAngleLayer::SlopeAngleListener
                    virtual Util::TAKErr onColorChanged(const TAK::Engine::Elevation::TerrainSlopeAngleLayer& subject, const float alpha) NOTHROWS;
                public : // Layer2::VisibilityListener
                    virtual Util::TAKErr layerVisibilityChanged(const TAK::Engine::Core::Layer2 &layer, const bool visible) NOTHROWS;
                private :
                    TAK::Engine::Elevation::TerrainSlopeAngleLayer &subject_;
                    float alpha_;
                    bool visible_;
                    unsigned int texture_id_;
                    unsigned int legend_vbo_;
                };

            }
        }
    }
}

#endif
