
#ifndef ATAKMAP_RENDERER_FEATURE_GLFEATURE_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GLFEATURE_H_INCLUDED

#include "renderer/map/GLMapBatchable.h"
#include "renderer/map/GLMapRenderable.h"

namespace atakmap {
    
    namespace feature {
        class Feature;
    }
    
    namespace renderer {
        
        class GLRenderContext;
        class GLRenderBatch;
        
        namespace map {
            class GLMapRenderable;
            class GLMapBatchable;
        }
        
        namespace feature {
            
            class GLGeometry;
            class GLStyle;
            class StyleRenderContext;
            
            class GLFeature: public atakmap::renderer::map::GLMapRenderable,
                             public atakmap::renderer::map::GLMapBatchable {
            public:
                GLFeature(GLRenderContext *context, const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature);
                
                atakmap::feature::Feature *getSubject();
                
                virtual void draw(const atakmap::renderer::map::GLMapView *view);
                virtual void release();
                virtual bool isBatchable(const atakmap::renderer::map::GLMapView *view);
                virtual void batch(const atakmap::renderer::map::GLMapView *view, GLRenderBatch *batch);
                void update(const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature);
                
                virtual void start();
                virtual void stop();
            private:
                void init(const atakmap::renderer::map::GLMapView *view);
                
                atakmap::renderer::GLRenderContext *context;
                
            //internal:
            public:
                PGSC::RefCountablePtr<atakmap::feature::Feature> subject;
                
            private:
                bool initialized;
                
                std::unique_ptr<atakmap::renderer::feature::GLStyle> style;
                std::unique_ptr<atakmap::renderer::feature::GLGeometry> geometry;
                atakmap::renderer::feature::StyleRenderContext *styleCtx;
                
            };
        }
    }
}

#endif
