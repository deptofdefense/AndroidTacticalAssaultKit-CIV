
#ifndef ATAKMAP_RENDERER_FEATURE_GLFEATURELAYER_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GLFEATURELAYER_H_INCLUDED

#include <vector>
#include <unordered_map>

#include "feature/FeatureDataStore.h"

#include "renderer/map/GLAsynchronousMapRenderable.h"
#include "renderer/map/layer/GLLayer.h"
#include "renderer/map/layer/GLLayerSpi.h"

//#include "feature/Feature_CLI.h"
//#include "feature/FeatureDataStore_CLI.h"
//#include "feature/FeatureLayer_CLI.h"

//#include "renderer/GLRenderContext_CLI.h"

//#include "renderer/GLRenderBatch_CLI.h"
//#include "renderer/feature/GLFeature_CLI.h"

namespace atakmap {
    
    namespace feature {
        class Feature;
        class FeatureLayer;
        class FeatureDataStore;
    }
    
    namespace renderer {
        
        class GLRenderContext;
        class GLRenderBatch;
        
        namespace map {
            class GLMapRenderable;
            class GLMapView;
        }
        
        namespace feature {
            
            class GLFeature;
            
            class GLFeatureLayer: public atakmap::renderer::map::GLAsynchronousMapRenderable,
                                  public atakmap::renderer::map::layer::GLLayer,
                                  public atakmap::feature::FeatureDataStore::ContentListener
            {
            private:
                atakmap::renderer::GLRenderContext *context;
                atakmap::feature::FeatureLayer *subject;
                atakmap::feature::FeatureDataStore *dataStore;
                
            //internal:
            public:
                GLRenderBatch *batch;
                std::unordered_map<int64_t, GLFeature *> features;

            private:
                std::vector<atakmap::renderer::map::GLMapRenderable *> renderList;
                
            public:
                GLFeatureLayer(atakmap::renderer::GLRenderContext *context, atakmap::feature::FeatureLayer *subject);
                virtual ~GLFeatureLayer();
                
                virtual atakmap::core::Layer *getSubject() override;
                
                virtual void contentChanged(atakmap::feature::FeatureDataStore &store);
                
            public:
                virtual void draw(const atakmap::renderer::map::GLMapView *view) override;
                
                virtual void start() override;
                virtual void stop() override;
                
            protected:
                class FeatureQueryContext;
                
                virtual const char *getBackgroundThreadName() const;
                
                virtual void initImpl(const atakmap::renderer::map::GLMapView *view) override;
                
                virtual void releaseImpl() override;
                
                virtual void resetQueryContext(QueryContext *pendingData) override;
                virtual void releaseQueryContext(QueryContext *pendingData) override;
                virtual QueryContext *createQueryContext() override;
                virtual port::Iterator<GLMapRenderable *> *getRenderablesIterator() override;
                virtual void releaseRenderablesIterator(port::Iterator<GLMapRenderable *> *iter) override;
                virtual bool updateRenderableLists(QueryContext *pendingData) override;
                virtual void query(const ViewState *state, QueryContext *result) override;
                
            private:
                void queryImpl(const ViewState *state, QueryContext *result);
            
            public:
                class Spi : public atakmap::renderer::map::layer::GLLayerSpi {
                public:
                    Spi();
                    virtual ~Spi();
                    virtual atakmap::renderer::map::layer::GLLayer *create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const;
                    virtual unsigned int getPriority() const throw();
                };
            };
            
            class GLFeatureLayer::FeatureQueryContext : public GLAsynchronousMapRenderable::QueryContext {
            public:
                FeatureQueryContext(atakmap::renderer::GLRenderContext *context);
                virtual ~FeatureQueryContext();
                void clear();
                void addFeature(atakmap::feature::Feature *feature);
                void update(std::unordered_map<int64_t, GLFeature *> &glFeatures);
            private:
                atakmap::renderer::GLRenderContext *context;
                std::vector<PGSC::RefCountablePtr<atakmap::feature::Feature>> features;
            };
        }
    }
}

#endif
