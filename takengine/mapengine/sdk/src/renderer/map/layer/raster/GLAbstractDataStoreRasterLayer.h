#ifndef ATAKMAP_RENDERER_GLABSTRACTDATASTORERASTERLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_GLABSTRACTDATASTORERASTERLAYER_H_INCLUDED

#include <string>
#include <map>
#include <list>

#include "port/Iterator.h"

#include "raster/DatasetDescriptor.h"
#include "raster/RasterLayer.h"
#include "raster/AbstractDataStoreRasterLayer.h"
#include "raster/RasterDataStore.h"
#include "raster/RasterDataAccess.h"

#include "renderer/map/GLAsynchronousMapRenderable.h"
#include "renderer/map/layer/raster/GLMapLayer.h"

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {

                    class GLAbstractDataStoreRasterLayer : public GLAsynchronousMapRenderable,
                                                           public GLLayer,
                                                           public atakmap::raster::RasterLayer::SelectionListener,
                                                           public atakmap::raster::RasterDataStore::ContentListener,
                                                           /*public atakmap::raster::RasterDataAccessService,*/
                                                           public atakmap::raster::RasterLayer::SelectionVisibilityListener {

                    protected:
                        atakmap::renderer::GLRenderContext *surface;
                        atakmap::raster::AbstractDataStoreRasterLayer *subject;
                        PGSC::String selection;

                        typedef std::vector<atakmap::renderer::map::layer::raster::GLMapLayer *> RenderableList;
                        typedef std::vector<atakmap::raster::DatasetDescriptorUniquePtr> DatasetDescriptorList;
                                                               
                                                               
                        class RenderableListIterator : public port::Iterator<GLMapRenderable *> {
                        public:
                            RenderableListIterator() { }
                            RenderableListIterator(RenderableList::iterator start, RenderableList::iterator end)
                            : pos(start), end(end) { }
                            inline void reset(RenderableList::iterator start, RenderableList::iterator end) {
                                pos = start;
                                this->end = end;
                            }
                            virtual ~RenderableListIterator();
                            virtual bool hasNext();
                            virtual GLMapRenderable *next();
                            virtual GLMapRenderable *get();
                        private:
                            RenderableList::iterator pos;
                            RenderableList::iterator end;
                        };
                                                               
                        class State : public ViewState {
                        public:
                            State(GLAbstractDataStoreRasterLayer *ownerInst)
                            : ownerInst(ownerInst) { }
                            
                            virtual ~State();
                            
                           PGSC::String selection;
                           atakmap::raster::RasterDataStore::DatasetQueryParameters queryParams;
                           
                           virtual void set(const GLMapView *view);
                           
                           virtual void copy(const ViewState *view);
                           
                        protected:
                            virtual void updateQueryParams();
                            
                            GLAbstractDataStoreRasterLayer *ownerInst;
                        };
                                                               
                        class UpdateRenderablesImpl;
                                                               
                        RenderableList renderables;

                        GLAbstractDataStoreRasterLayer(atakmap::renderer::GLRenderContext *surface, atakmap::raster::AbstractDataStoreRasterLayer *subject);

                        virtual void initImpl(const GLMapView *view);

                        virtual void releaseImpl();

                        virtual port::Iterator<GLMapRenderable *> *getRenderablesIterator();
                        virtual void releaseRenderablesIterator(port::Iterator<GLMapRenderable *> *iter);
                        
                        class QueryContextImpl : public QueryContext {
                        public:
                            virtual ~QueryContextImpl();
                            DatasetDescriptorList descriptors;
                        };
                                                               
                        virtual void resetQueryContext(QueryContext *pendingData);
                        virtual void releaseQueryContext(QueryContext *pendingData);
                        virtual QueryContext *createQueryContext();
                          
                        virtual bool updateRenderableLists(QueryContext *pendingData);
                                                               
                        ViewState *newViewStateInstance();
                        
                    public:
                        virtual atakmap::core::Layer *getSubject();
                                                               
                        virtual void selectionChanged(atakmap::raster::RasterLayer &layer);
                        virtual void contentChanged(atakmap::raster::RasterDataStore &dataStore);
                        virtual void selectionVisibilityChanged(atakmap::raster::RasterLayer &layer);
                                                               
                        //using GLAsynchronousMapRenderable::release;
                                                               
                    private:
                        static void updateSelectionRunnable(void *opaque);
                        static void updateContentRunnable(void *opaque);
                        static void updateSelectionVisibilityRunnable(void *opaque);
                                                               
                        RenderableListIterator cachedRenderablesIterator;                                                       
                    };
                }
            }
        }
    }
}


#endif	//#ifndef ATAKMAP_GLABSTRACTDATASTORERASTERLAYERATAKMAP_
