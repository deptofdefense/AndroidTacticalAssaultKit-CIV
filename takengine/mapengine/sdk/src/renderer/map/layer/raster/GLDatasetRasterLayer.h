#ifndef ATAKMAP_GLDATASETRASTERLAYERATAKMAP_
#define ATAKMAP_GLDATASETRASTERLAYERATAKMAP_

#include "raster/DatasetRasterLayerImpl.h"

#include "renderer/map/layer/raster/GLAbstractDataStoreRasterLayer.h"
#include "renderer/map/layer/GLLayerSpi.h"

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {
                    class GLDatasetRasterLayer : public GLAbstractDataStoreRasterLayer {
                    public:
                        class Spi : public GLLayerSpi {
                        public:
                            Spi();

                            virtual ~Spi();
                            virtual unsigned int getPriority() const throw();
                            virtual atakmap::renderer::map::layer::GLLayer *create(const GLLayerSpiArg &) const;
                        };

                    private:
                        //TODO(bergeronj)--static Collection<RasterDataStore::DatasetQueryParameters::Order *> *const ORDER_GSD;

                        PGSC::String autoSelectValue;

                    public:
                        GLDatasetRasterLayer(GLRenderContext *surface, atakmap::raster::DatasetRasterLayerImpl *subject);

                        /// <summary>
                        ///*********************************************************************** </summary>
                        // GL Asynchronous Map Renderable

                        virtual ViewState *newViewStateInstance();

                        virtual void query(const ViewState *state, QueryContext *retval);
                        
                        virtual void start();
                        virtual void stop();

                        /// <summary>
                        ///*********************************************************************** </summary>

                    protected:
                        class State : public GLAbstractDataStoreRasterLayer::State {
                        public:
                            DatasetDescriptorList renderables;

                        public:
                            State(GLDatasetRasterLayer *outerInstance);

                            virtual ~State();
                            
                            virtual void set(const GLMapView *view);
                            virtual void copy(const ViewState *view);
                            
                            bool intersectsBounds(double north, double west, double south, double east) const;
                            
                        protected:
                            virtual void updateQueryParams();
                        };

                    private:
                        void updateStateRenderables(State *state);
                        void queryImpl(std::vector<const char *> &queryNames, const State *state, QueryContextImpl *retval, bool notify);
                    };
                }
            }
        }
    }
}


#endif
