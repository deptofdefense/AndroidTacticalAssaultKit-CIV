#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERERLAYER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYFEATUREDATASTORERENDERERLAYER_H_INCLUDED

#include "feature/FeatureLayer2.h"

#include "renderer/map/layer/GLLayer.h"
#include "renderer/map/layer/GLLayerSpi.h"
#include "renderer/feature/GLBatchGeometryFeatureDataStoreRenderer.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                
                class GLBatchGeometryFeatureDataStoreRendererLayer : public GLBatchGeometryFeatureDataStoreRenderer,
                                                                     public atakmap::renderer::map::layer::GLLayer {
                public:
                     GLBatchGeometryFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject) NOTHROWS;
                     GLBatchGeometryFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS;
                     virtual ~GLBatchGeometryFeatureDataStoreRendererLayer() NOTHROWS;
                     virtual atakmap::core::Layer *getSubject();
                     virtual void start();
                                                                         
                private:
                     TAK::Engine::Feature::FeatureLayer2 &subject;
                };
                
                class GLBatchGeometryFeatureDataStoreRendererLayerSpi : public atakmap::renderer::map::layer::GLLayerSpi {
                public:
                    GLBatchGeometryFeatureDataStoreRendererLayerSpi();
                    virtual ~GLBatchGeometryFeatureDataStoreRendererLayerSpi();
                    virtual atakmap::renderer::map::layer::GLLayer *create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const;
                    virtual unsigned int getPriority() const throw();
                };
            }
        }
    }
}

#endif
