#ifndef TAK_ENGINE_RENDERER_FEATURE_GLPersistentFeatureDataStoreRendererLAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLPersistentFeatureDataStoreRendererLAYER_H_INCLUDED

#include "feature/FeatureLayer2.h"

#include "renderer/map/layer/GLLayer.h"
#include "renderer/map/layer/GLLayerSpi.h"
#include "renderer/feature/GLPersistentFeatureDataStoreRenderer.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                
                class GLPersistentFeatureDataStoreRendererLayer : public GLPersistentFeatureDataStoreRenderer,
                                                                     public atakmap::renderer::map::layer::GLLayer {
                public:
                     GLPersistentFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject) NOTHROWS;
                     GLPersistentFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS;
                     virtual ~GLPersistentFeatureDataStoreRendererLayer() NOTHROWS;
                     virtual atakmap::core::Layer *getSubject();
                     virtual void start();
                                                                         
                private:
                     TAK::Engine::Feature::FeatureLayer2 &subject;
                };
                
                class GLPersistentFeatureDataStoreRendererLayerSpi : public atakmap::renderer::map::layer::GLLayerSpi {
                public:
                    GLPersistentFeatureDataStoreRendererLayerSpi();
                    virtual ~GLPersistentFeatureDataStoreRendererLayerSpi();
                    virtual atakmap::renderer::map::layer::GLLayer *create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const;
                    virtual unsigned int getPriority() const throw();
                };
            }
        }
    }
}

#endif
