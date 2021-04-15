
#include "core/GeoPoint.h"
#include "core/ProjectionFactory2.h"

#include "feature/Envelope.h"

#include "raster/DataStoreRasterLayer.h"
#include "raster/DatasetDescriptor.h"

#include "renderer/map/layer/raster/GLDatasetRasterLayer.h"

using namespace atakmap::core;
using namespace atakmap::raster;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::map::layer;
using namespace atakmap::renderer::map::layer::raster;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

GLDatasetRasterLayer::Spi::Spi() {
}

GLDatasetRasterLayer::Spi::~Spi() {
}

unsigned int GLDatasetRasterLayer::Spi::getPriority() const throw() {
    return 3;
}

atakmap::renderer::map::layer::GLLayer *GLDatasetRasterLayer::Spi::create(const GLLayerSpiArg &arg) const {
    
    GLRenderContext *context = arg.context->getRenderContext();
    atakmap::core::Layer *layer = arg.layer;
    
    atakmap::raster::DatasetRasterLayerImpl *rasterLayer;
    if ((rasterLayer = dynamic_cast<atakmap::raster::DatasetRasterLayerImpl *>(layer))) {
        return new GLDatasetRasterLayer(context, rasterLayer);
    }
    return NULL;
}

/*java::util::Collection<com::atakmap::map::layer::raster::RasterDataStore::DatasetQueryParameters::Order*> *const GLDatasetRasterLayer::ORDER_GSD = java::util::Collections::singleton<com::atakmap::map::layer::raster::RasterDataStore::DatasetQueryParameters::Order*>(com::atakmap::map::layer::raster::RasterDataStore::DatasetQueryParameters::GSD::INSTANCE);*/

GLDatasetRasterLayer::GLDatasetRasterLayer(GLRenderContext *surface, atakmap::raster::DatasetRasterLayerImpl *subject)
: GLAbstractDataStoreRasterLayer(surface, subject) {
    this->autoSelectValue = "";
}

GLAsynchronousMapRenderable::ViewState *GLDatasetRasterLayer::newViewStateInstance() {
    return new State(this);
}

void GLDatasetRasterLayer::start() {
    
}

void GLDatasetRasterLayer::stop() {
    
}

void GLDatasetRasterLayer::query(const ViewState *viewState, QueryContext *retval) {

    const State *state = static_cast<const State *>(viewState);
    bool selectionIsStickyAuto = false;
    DatasetRasterLayerImpl *layer = static_cast<DatasetRasterLayerImpl *>(this->subject);
    size_t limit = layer->getDatasetLimit();
    PGSC::String selection = state->selection;
    
    // if we are in auto-select, "stick" on the current auto-select value if
    // it is still in bounds AND we haven't exceeded the maximum resolution
    if (!state->selection && limit > 0 && state->renderables.size() > 0) {
        double maxRes = NAN;
        double minRes = NAN;
        bool inBounds = false;
        const char *autoSelect = "";

        DatasetDescriptorList::const_iterator end = state->renderables.end();
        for (DatasetDescriptorList::const_iterator it = state->renderables.begin(); it != end; ++it) {
            const DatasetDescriptor *info = it->get();
            if(isnan(maxRes) || (*it)->getMaxResolution(NULL) < maxRes) {
                maxRes = info->getMaxResolution(NULL);
                autoSelect = info->getName();
            }
            if(isnan(minRes) || info->getMinResolution(NULL) > minRes) {
                minRes = info->getMinResolution(NULL);
            }
            Envelope mbb = info->getMinimumBoundingBox();
            inBounds |= state->intersectsBounds(mbb.maxY, mbb.minX, mbb.minY, mbb.maxX);
        }

        if (inBounds && maxRes <= state->drawMapResolution && minRes >= state->drawMapResolution) {
            // sticking on the current selection -- no need to fire off a
            // projection change, conditionally fire an auto-select as we
            // may be here as a result of going into auto-select mode.
            selection = autoSelect;
            selectionIsStickyAuto = true;
        }
    }

    QueryContextImpl *retvalImpl = static_cast<QueryContextImpl *>(retval);
    
    std::vector<const char *> querySelections;
    if (selection && strcmp(selection, "") != 0) {
        querySelections.push_back(selection);
    }
    this->queryImpl(querySelections, state, retvalImpl, true);

    // if we are in autoselect mode and there is a limit on the selection,
    // re-query using the selected datasets
    if ((!selection || strlen(selection) == 0) && limit > 0 && retvalImpl->descriptors.size() > 0) {
        //state->queryParams.names.clear();
        querySelections.clear();
        DatasetDescriptorList::iterator end = retvalImpl->descriptors.end();
        for (DatasetDescriptorList::iterator it = retvalImpl->descriptors.begin(); it != end; ++it) {
            //state->queryParams.names.push_back((*it)->getName());
            querySelections.push_back((*it)->getName());
        }
        retvalImpl->descriptors.clear();
        this->queryImpl(querySelections, state, retvalImpl, false);
    }
    
    if (selectionIsStickyAuto) {
        if (this->autoSelectValue != selection) {
            this->autoSelectValue = selection;
        //    layer->setAutoSelectValue(this->autoSelectValue);
        }
    }
}

void GLDatasetRasterLayer::queryImpl(std::vector<const char *> &queryNames, const State *state, QueryContextImpl *retval, bool notify) {
    
    RasterDataStore * const dataStore = this->subject->getDataStore();
    DatasetRasterLayerImpl *subjectLayer = static_cast<DatasetRasterLayerImpl*>(this->subject);
    PGSC::String autoSelect = "";
    int srid = -1;

    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error
            ("GLDatasetRsaterLayer::queryImpl: Failed to acquire mutex");
        
        if (!dataStore->isAvailable()) {
            return;
        }
        
        RasterDataStore::DatasetQueryParameters queryParams(state->queryParams);
        this->subject->filterQueryParams(queryParams);
        if (queryNames.size() > 0) {
            for (int i = 0; i < queryNames.size(); ++i) {
                queryParams.names.push_back(queryNames[i]);
            }
        }
        
        RasterDataStore::DatasetDescriptorCursor *result = nullptr;
        bool first = true;
        try {
            result = dataStore->queryDatasets(queryParams);
            size_t datasetCount = 0;
            const size_t limit = subjectLayer->getDatasetLimit();
            while (result->moveToNext()) {
                
                DatasetDescriptorUniquePtr layerInfo(NULL, NULL);
                result->get().clone(layerInfo);
                
                if (!subjectLayer->isVisible(layerInfo->getName())) {
                    continue;
                }
                if (first) {
                    // auto-select is dataset with maximum GSD
                    autoSelect = layerInfo->getName();
                    srid = layerInfo->getSpatialReferenceID();
                    first = false;
                }
                
                retval->descriptors.emplace_back(std::move(layerInfo));
                ++datasetCount;
                if (queryParams.names.size() == 0 && datasetCount == limit) {
                    break;
                }
            }
            
            delete result;
        } catch(...) {
            if (result) {
                delete result;
            }
            throw;
        }
    }

    if (notify) {
        if (strcmp(this->autoSelectValue, autoSelect) == 0) {
            this->autoSelectValue = autoSelect;
            //TODO(bergeronj)-- subjectLayer->setAutoSelectValue(this->autoSelectValue);
        }

        if (srid != state->drawSrid) {
            
            if (subjectLayer->getPreferredProjection() == NULL ||
                subjectLayer->getPreferredProjection()->getSpatialReferenceID() != srid) {
            
                //XXX-- assumes projections are cached internally or leaks them!
                TAK::Engine::Core::ProjectionPtr proj(TAK::Engine::Core::ProjectionFactory2::getProjection(srid));
                if (proj) {
                    subjectLayer->setPreferredProjection(proj.release());
                }
            }
        }
    }
}

void GLDatasetRasterLayer::updateStateRenderables(State *state) {
    state->renderables.clear();
    if (dynamic_cast<DatasetRasterLayerImpl *>(getSubject())->getDatasetLimit() > 0) {
        RenderableList::iterator end = renderables.end();
        for (RenderableList::iterator it = renderables.begin(); it != end; ++it) {
            DatasetDescriptorUniquePtr ptr(NULL, NULL);
            const DatasetDescriptor *info = (*it)->getInfo();
            info->clone(ptr);
            state->renderables.emplace_back(std::move(ptr));
        }
    }
}

GLDatasetRasterLayer::State::~State() { }

GLDatasetRasterLayer::State::State(GLDatasetRasterLayer *outerInstance)
: GLAbstractDataStoreRasterLayer::State(outerInstance) { }

void GLDatasetRasterLayer::State::set(const GLMapView *view) {
    GLAbstractDataStoreRasterLayer::State::set(view);
    GLDatasetRasterLayer *glLayer = static_cast<GLDatasetRasterLayer *>(ownerInst);
    glLayer->updateStateRenderables(this);
}

void GLDatasetRasterLayer::State::copy(const ViewState *view) {
    GLAbstractDataStoreRasterLayer::State::copy(view);
    this->renderables.clear();
    const State *other = static_cast<const State *>(view);
    DatasetDescriptorList::const_iterator end = other->renderables.end();
    for (DatasetDescriptorList::const_iterator it = other->renderables.begin(); it != end; ++it) {
        DatasetDescriptorUniquePtr ptr(NULL, NULL);
        (*it)->clone(ptr);
        this->renderables.push_back(std::move(ptr));
    }
}

void GLDatasetRasterLayer::State::updateQueryParams() {
    GLAbstractDataStoreRasterLayer::State::updateQueryParams();
//TODO(bergeronj)    this->queryParams->order = ORDER_GSD;
}

bool GLDatasetRasterLayer::State::intersectsBounds(double north, double west, double south, double east) const {
    return !(northBound < south ||
             southBound > north ||
             eastBound < west || westBound > east);
}

