
#include "renderer/map/layer/raster/GLAbstractDataStoreRasterLayer.h"
#include "renderer/map/layer/raster/GLMapLayerFactory.h"
#include "math/Utils.h"

using namespace atakmap;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::map::layer::raster;
using namespace atakmap::raster;

// RenderableListIterator

GLAbstractDataStoreRasterLayer::RenderableListIterator::~RenderableListIterator() {
    
}

bool GLAbstractDataStoreRasterLayer::RenderableListIterator::hasNext() {
    return pos != end;
}

GLMapRenderable *GLAbstractDataStoreRasterLayer::RenderableListIterator::get() {
    return *pos;
}

GLMapRenderable *GLAbstractDataStoreRasterLayer::RenderableListIterator::next() {
    GLMapRenderable *result = *pos;
    ++pos;
    return result;
}

// QueryContextImpl

GLAbstractDataStoreRasterLayer::QueryContextImpl::~QueryContextImpl() {
    
}

// GLAbstractDataStoreRasterLayer

GLAbstractDataStoreRasterLayer::GLAbstractDataStoreRasterLayer(GLRenderContext *surface, AbstractDataStoreRasterLayer *subject)
: surface(surface),
  subject(subject) { }

void GLAbstractDataStoreRasterLayer::initImpl(const GLMapView *view) {
    GLAsynchronousMapRenderable::initImpl(view);

    this->subject->addSelectionListener(this);
    this->selection = this->subject->getSelection();

    (static_cast<AbstractDataStoreRasterLayer *>(this->subject))->getDataStore()->addContentListener(this);
    this->subject->addSelectionVisibilityListener(this);

    //TODO(bergeronj)--this->subject->registerService(this);
}

void GLAbstractDataStoreRasterLayer::releaseImpl() {
    // no need to call next 3 with the lock as the registration may only
    // ever occur on the GL context thread, which we are currently on
    this->subject->removeSelectionListener(this);
    (static_cast<AbstractDataStoreRasterLayer*>(this->subject))->getDataStore()->removeContentListener(this);
    this->subject->removeSelectionVisibilityListener(this);

    //TODO(bergeornj)-- this->subject->unregisterService(this);

    GLAsynchronousMapRenderable::releaseImpl();
}

atakmap::port::Iterator<GLMapRenderable *> *GLAbstractDataStoreRasterLayer::getRenderablesIterator() {
    cachedRenderablesIterator.reset(renderables.begin(), renderables.end());
    return &cachedRenderablesIterator;
}

void GLAbstractDataStoreRasterLayer::releaseRenderablesIterator(port::Iterator<GLMapRenderable *> *iter) {
}

void GLAbstractDataStoreRasterLayer::resetQueryContext(QueryContext *pendingData) {
    QueryContextImpl *impl = static_cast<QueryContextImpl *>(pendingData);
    impl->descriptors.clear();
}

void GLAbstractDataStoreRasterLayer::releaseQueryContext(QueryContext *pendingData) {
    delete pendingData;
}

GLAbstractDataStoreRasterLayer::QueryContext *GLAbstractDataStoreRasterLayer::createQueryContext() {
    // XXX - sorted lowest GSD to highest GSD (back to front render order)
    return new QueryContextImpl();
}

class GLAbstractDataStoreRasterLayer::UpdateRenderablesImpl {
public:
    void invoke(GLRenderContext *context, RenderableList &renderables, QueryContextImpl *pendingData) {
        
        releaseList.swap(renderables);
        
        DatasetDescriptorList::iterator end = pendingData->descriptors.end();
        DatasetDescriptorList::iterator it = pendingData->descriptors.begin();
        for (; it != end; ++it) {
            updateRenderableImpl(context, renderables, it->get());
        }
        
        context->runOnGLThread(UpdateRenderablesImpl::releaseRenderablesRunnable, this);
    }
    
private:
    void releaseRenderables() {
        RenderableList::iterator end = releaseList.end();
        for (RenderableList::iterator it = releaseList.begin(); it != end; ++it) {
            auto ptr = *it;
            printf("Release %p", ptr);
            (*it)->release();
            delete *it;
        }
    }
    
    static void releaseRenderablesRunnable(void *opaque) {
        UpdateRenderablesImpl *runnable = static_cast<UpdateRenderablesImpl *>(opaque);
        runnable->releaseRenderables();
        delete runnable;
    }
    
    void updateRenderableImpl(GLRenderContext *context, RenderableList &renderables, const atakmap::raster::DatasetDescriptor *dd) {
        
        RenderableList::iterator end = releaseList.end();
        for (RenderableList::iterator it = releaseList.begin(); it != end; ++it) {
            if (*(*it)->getInfo() == *dd) {
                renderables.push_back(*it);
                releaseList.erase(it);
                return;
            } else {
                printf("IGNORING THING THING THING THING");
            }
        }
        
        GLMapLayer *layer = GLMapLayerFactory::create(context, dd);
        if (layer) {
            renderables.push_back(layer);
        }
    }
    
    RenderableList releaseList;
};

bool GLAbstractDataStoreRasterLayer::updateRenderableLists(QueryContext *pendingData) {
    UpdateRenderablesImpl *updateImpl = new UpdateRenderablesImpl();
    updateImpl->invoke(surface, this->renderables, static_cast<QueryContextImpl *>(pendingData));
    return true;
}

GLAbstractDataStoreRasterLayer::ViewState *GLAbstractDataStoreRasterLayer::newViewStateInstance() {
    return new State(this);
}


atakmap::core::Layer *GLAbstractDataStoreRasterLayer::getSubject() {
    return this->subject;
}

struct SelectionChangeArgs {
    PGSC::String selection;
    GLAbstractDataStoreRasterLayer *target;
};

void GLAbstractDataStoreRasterLayer::selectionChanged(atakmap::raster::RasterLayer &layer) {
    
    SelectionChangeArgs *args = new SelectionChangeArgs();
    args->selection = layer.isAutoSelect() ? "" : layer.getSelection();
    args->target = this;
    
    // XXX- sketchy thread safety
    surface->runOnGLThread(updateSelectionRunnable, args);
}

void GLAbstractDataStoreRasterLayer::contentChanged(atakmap::raster::RasterDataStore &dataStore) {

    // XXX- sketchy thread safety
    surface->runOnGLThread(updateContentRunnable, this);
}

void GLAbstractDataStoreRasterLayer::selectionVisibilityChanged(atakmap::raster::RasterLayer &layer) {
    
    // XXX- sketchy thread safety
    surface->runOnGLThread(updateContentRunnable, this);
}

void GLAbstractDataStoreRasterLayer::updateSelectionRunnable(void *opaque) {
    SelectionChangeArgs* args = static_cast<SelectionChangeArgs *>(opaque);
    args->target->selection = args->selection;
    args->target->invalidate();
    delete args;
}

void GLAbstractDataStoreRasterLayer::updateContentRunnable(void *opaque) {
    GLAbstractDataStoreRasterLayer *target = static_cast<GLAbstractDataStoreRasterLayer *>(opaque);
    target->invalidate();
}

void GLAbstractDataStoreRasterLayer::updateSelectionVisibilityRunnable(void *opaque) {
    GLAbstractDataStoreRasterLayer *target = static_cast<GLAbstractDataStoreRasterLayer *>(opaque);
    target->invalidate();
}

                        /// <summary>
                        ///*********************************************************************** </summary>

/*TODO(bergeronj)--synchronized RasterDataAccess accessRasterData(GeoPoint point) {
    Iterator<GLMapLayer*> iter = (static_cast<std::list<GLMapLayer*>>(this->renderable))->begin();
    GLMapLayer *layer;
    MapData *dataAccess = 0;
    int srid = -1;
    while (iter->hasNext()) {
        layer = iter->next();
        dataAccess = layer->getMapData(point, this->preparedState.drawMapResolution);
        srid = layer->getInfo().getSpatialReferenceID();
        if (dataAccess != 0) {
            break;
        }
        iter++;
    }

    if (dataAccess == 0) {
        return 0;
    }

    return RasterDataAccessBridge::getInstance(srid, dataAccess);
}*/

GLAbstractDataStoreRasterLayer::State::~State() { }

void GLAbstractDataStoreRasterLayer::State::set(const GLMapView *view) {
    ViewState::set(view);
    this->selection = ownerInst->selection;
    this->updateQueryParams();
}

void GLAbstractDataStoreRasterLayer::State::copy(const ViewState *view) {
    ViewState::copy(view);
    this->selection = static_cast<const State *>(view)->selection;
    this->updateQueryParams();
}

void GLAbstractDataStoreRasterLayer::State::updateQueryParams() {
    
    atakmap::core::GeoPoint normalizedUL = this->upperLeft;
    atakmap::core::GeoPoint normalizedLR = this->lowerRight;
    normalizedUL.latitude = math::clamp(normalizedUL.latitude, -90.0, 90.0);
    normalizedUL.longitude = math::clamp(normalizedUL.longitude, -180.0, 180.0);
    normalizedLR.latitude = math::clamp(normalizedLR.latitude, -90.0, 90.0);
    normalizedLR.longitude = math::clamp(normalizedLR.longitude, -180.0, 180.0);
    
    if (dynamic_cast<atakmap::raster::RasterDataStore::DatasetQueryParameters::RegionFilter *>(this->queryParams.spatialFilter.get()) != 0) {
        RasterDataStore::DatasetQueryParameters::RegionFilter *roi = static_cast<atakmap::raster::RasterDataStore::DatasetQueryParameters::RegionFilter *>(this->queryParams.spatialFilter.get());
        roi->upperLeft = normalizedUL;
        roi->lowerRight = normalizedLR;
    } else {
        this->queryParams.spatialFilter =
            PGSC::RefCountableIndirectPtr<RasterDataStore::DatasetQueryParameters::SpatialFilter>(new RasterDataStore::DatasetQueryParameters::RegionFilter(normalizedUL, normalizedLR));
    }

    this->queryParams.minResolution = this->drawMapResolution;
}

