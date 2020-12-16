
#include <algorithm>

#include "renderer/GLRenderBatch.h"
#include "renderer/feature/GLFeatureLayer.h"
#include "feature/FeatureDataStore.h"
#include "feature/FeatureLayer.h"
#include "feature/Feature.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/feature/GLFeature.h"
#include "port/Iterator.h"
//#include "private/Util.h"

using namespace atakmap;
using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

namespace
{
    /*class GLFeatureComparer : public IComparer <atakmap::cpp_cli::renderer::feature::GLFeature ^>
    {
        private :
        GLFeatureComparer();
        public :
        virtual int Compare(atakmap::cpp_cli::renderer::feature::GLFeature ^o1, atakmap::cpp_cli::renderer::feature::GLFeature ^o2);
        internal :
        static IComparer <atakmap::cpp_cli::renderer::feature::GLFeature ^> ^ const INSTANCE = gcnew GLFeatureComparer();
    };*/
}


/*class ReleaseRunnable : public GLRenderContext::GLRunnable
{
public:
    virtual void glRun(Object ^opaque)
    {
        ICollection<GLFeature ^> ^releaseList = (ICollection<GLFeature ^> ^)opaque;
        for each (GLFeature ^f in releaseList)
            f->release();
    }
};*/

class GLBatchRenderer : public GLMapRenderable {
private:
    GLFeatureLayer *owner;
    
public:
    GLBatchRenderer(GLFeatureLayer *owner) : owner(owner) { }
    
    virtual void draw(const GLMapView *view) {
        bool inBatch = false;
        for (const std::pair<int64_t, GLFeature *> &entry : owner->features) {
            GLFeature *feature = entry.second;
            if (feature->isBatchable(view)) {
                if (!inBatch) {
                    owner->batch->begin();
                    inBatch = true;
                }
                feature->batch(view, owner->batch);
            } else {
                if (inBatch)
                    owner->batch->end();
                feature->draw(view);
            }
        }
        if (inBatch)
            owner->batch->end();
    }
    
    virtual void release() {}
    
    virtual void start() {}
    virtual void stop() {}
};

class GLFeatureIterator : public atakmap::port::Iterator<GLMapRenderable *> {
public:
    GLFeatureIterator(std::vector<GLMapRenderable *>::iterator first, std::vector<GLMapRenderable *>::iterator last)
    : cur(NULL), it(first), end(last) { }
    
    virtual ~GLFeatureIterator() { }
    
    virtual bool hasNext() {
        return it != end;
    }
    
    virtual GLMapRenderable *next() {
        cur = NULL;
        if (it != end) {
            cur = *it;
            ++it;
        }
        return cur;
    }
    
    virtual GLMapRenderable *get() {
        return cur;
    }
    
private:
    GLMapRenderable *cur;
    std::vector<GLMapRenderable *>::iterator it;
    std::vector<GLMapRenderable *>::iterator end;
};

GLFeatureLayer::Spi::Spi() { }

GLFeatureLayer::Spi::~Spi() { }

atakmap::renderer::map::layer::GLLayer *GLFeatureLayer::Spi::create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const {
    FeatureLayer *layerImpl = dynamic_cast<FeatureLayer *>(args.layer);
    if (layerImpl == nullptr)
        return nullptr;
    return new GLFeatureLayer(args.context->getRenderContext(), layerImpl);
}

unsigned int GLFeatureLayer::Spi::getPriority() const throw() {
    return 1;
}

GLFeatureLayer::GLFeatureLayer(GLRenderContext *context, atakmap::feature::FeatureLayer *subject)
: context(context),
  subject(subject),
  dataStore(&subject->getDataStore()),
  batch(NULL)
  { }

GLFeatureLayer::~GLFeatureLayer() { }

atakmap::core::Layer *GLFeatureLayer::getSubject() {
    return subject;
}

void GLFeatureLayer::contentChanged(atakmap::feature::FeatureDataStore &store) {
    invalidate();
}

void GLFeatureLayer::draw(const GLMapView *view) {
    if (!this->subject->isVisible())
        return;
    GLAsynchronousMapRenderable::draw(view);
}

void GLFeatureLayer::start() {
    
}

void GLFeatureLayer::stop() {
    
}

const char *GLFeatureLayer::getBackgroundThreadName() const {
    return "GLFeatureLayerWorker";
}

void GLFeatureLayer::releaseImpl() {
    this->subject->getDataStore().removeContentListener(this);
    for (const std::pair<int64_t, GLFeature *> &feature : features)
        feature.second->release();
    features.clear();
    batch = nullptr;
    GLAsynchronousMapRenderable::releaseImpl();
}

void GLFeatureLayer::initImpl(const GLMapView *view) {
    batch = new GLRenderBatch(5000);
    renderList.push_back(new GLBatchRenderer(this));
    this->subject->getDataStore().addContentListener(this);
}

void GLFeatureLayer::resetQueryContext(QueryContext *pendingData) {
    static_cast<FeatureQueryContext *>(pendingData)->clear();
}

void GLFeatureLayer::releaseQueryContext(QueryContext *pendingData) {
    delete pendingData;
}

GLFeatureLayer::QueryContext *GLFeatureLayer::createQueryContext() {
    return new FeatureQueryContext(this->context);
}

atakmap::port::Iterator<GLMapRenderable *> *GLFeatureLayer::getRenderablesIterator() {
    return new GLFeatureIterator(renderList.begin(), renderList.end());
}

void GLFeatureLayer::releaseRenderablesIterator(port::Iterator<GLMapRenderable *> *iter) {
    delete iter;
}

bool GLFeatureLayer::updateRenderableLists(QueryContext *pendingData) {
    
    static_cast<FeatureQueryContext *>(pendingData)->update(this->features);
    
    
    /*Dictionary<System::Int64, GLFeature ^> ^scratch = gcnew Dictionary<System::Int64, GLFeature ^>();
    
     
    for each(GLFeature ^f in features)
        scratch[f->getSubject()->getId()] = f;
    
    features->Clear();
    
    for each (cpp_cli::feature::Feature ^feature in data) {
        GLFeature ^glfeature = nullptr;
        if (scratch->TryGetValue(feature->getId(), glfeature)) {
            scratch->Remove(feature->getId());
            if (!cpp_cli::feature::Feature::isSame(glfeature->getSubject(), feature))
                glfeature->update(feature);
            
        } else {
            glfeature = gcnew GLFeature(context, feature);
        }
        features->Add(glfeature);
    }
    data->Clear();
    
    if (scratch->Count > 0)
        context->runOnGLThread(gcnew ReleaseRunnable(), scratch->Values);*/
    
    return true;
}

void GLFeatureLayer::query(const ViewState *state, QueryContext *result) {
    if (state->westBound > state->eastBound) {
        // Crosses IDL
        ViewState mutableState;
        mutableState.copy(state);
        double w = state->westBound;
        double e = state->eastBound;
        mutableState.eastBound = 180;
        queryImpl(&mutableState, result);
        mutableState.eastBound = e;
        mutableState.westBound = -180;
        queryImpl(&mutableState, result);
        mutableState.westBound = w;
    } else {
        queryImpl(state, result);
    }

}

/*void GLFeatureLayer::resetPendingData(ICollection<cpp_cli::feature::Feature ^> ^data) {
data->Clear();
}

void GLFeatureLayer::releasePendingData(ICollection<cpp_cli::feature::Feature ^> ^data)
{
data->Clear();
}

ICollection<cpp_cli::feature::Feature ^> ^GLFeatureLayer::createPendingData()
{
return gcnew LinkedList<cpp_cli::feature::Feature ^>();
}

bool GLFeatureLayer::updateRenderableReleaseLists(ICollection<cpp_cli::feature::Feature ^> ^data)
{
Dictionary<System::Int64, GLFeature ^> ^scratch = gcnew Dictionary<System::Int64, GLFeature ^>();
for each(GLFeature ^f in features)
    scratch[f->getSubject()->getId()] = f;
features->Clear();

for each (cpp_cli::feature::Feature ^feature in data) {
    GLFeature ^glfeature = nullptr;
    if (scratch->TryGetValue(feature->getId(), glfeature)) {
        scratch->Remove(feature->getId());
        if (!cpp_cli::feature::Feature::isSame(glfeature->getSubject(), feature))
            glfeature->update(feature);
        
    } else {
        glfeature = gcnew GLFeature(context, feature);
    }
    features->Add(glfeature);
}
data->Clear();

if (scratch->Count > 0)
    context->runOnGLThread(gcnew ReleaseRunnable(), scratch->Values);

return true;
}*/

void GLFeatureLayer::queryImpl(const ViewState *state, QueryContext *data) {

    FeatureQueryContext *featureQueryContext = static_cast<FeatureQueryContext *>(data);
    FeatureDataStore::FeatureQueryParameters params;
    params.spatialFilter = PGSC::RefCountableIndirectPtr<db::DataStore::QueryParameters::PointFilter::SpatialFilter>();
    params.maxResolution = state->drawMapResolution;
    params.visibleOnly = true;
    std::unique_ptr<FeatureDataStore::FeatureCursor> cursor(this->dataStore->queryFeatures(params));
    
    while (cursor->moveToNext()) {
        try {
            featureQueryContext->addFeature(cursor->get());
        } catch (FeatureDataStore::FeatureCursor::CursorError &e) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, e.what());
        }
    }
}

GLFeatureLayer::FeatureQueryContext::FeatureQueryContext(atakmap::renderer::GLRenderContext *context)
: context(context) { }

GLFeatureLayer::FeatureQueryContext::~FeatureQueryContext() { }

void GLFeatureLayer::FeatureQueryContext::addFeature(atakmap::feature::Feature *feature) {
    this->features.push_back(feature);
}

void GLFeatureLayer::FeatureQueryContext::clear() {
    this->features.clear();
}

void GLFeatureLayer::FeatureQueryContext::update(std::unordered_map<int64_t, GLFeature *> &glFeatures) {
    
    std::unordered_map<int64_t, GLFeature *> existingGLFeatures;
    existingGLFeatures.swap(glFeatures);
    
    for (const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature : this->features) {
        
        auto it = existingGLFeatures.find(feature->getID());
        
        if (it != existingGLFeatures.end()) {
            GLFeature *glFeature = it->second;
            glFeatures.insert(*it);
            existingGLFeatures.erase(it);
            if (!Feature::isSame(*glFeature->getSubject(), *feature)) {
                glFeature->update(feature);
            }

        } else {
            glFeatures.insert(std::pair<int64_t, GLFeature *>(feature->getID(),
                                                              new GLFeature(context, feature)));
        }
    }
}

/*namespace
{
    GLFeatureComparer::GLFeatureComparer()
    {}
    
    int GLFeatureComparer::Compare(atakmap::cpp_cli::renderer::feature::GLFeature ^o1, atakmap::cpp_cli::renderer::feature::GLFeature ^o2)
    {
        using namespace atakmap::cpp_cli::feature;
        
        Feature ^f1 = o1->getSubject();
        Feature ^f2 = o2->getSubject();
        
        switch (f1->getGeometry()->getGeomClass()) {
            case GeometryClass::GEOM_POINT :
                switch (f2->getGeometry()->getGeomClass()) {
                    case GeometryClass::GEOM_POINT :
                        break;
                    case GeometryClass::GEOM_LINESTRING:
                    case GeometryClass::GEOM_POLYGON:
                    case GeometryClass::GEOM_COLLECTION:
                        return 1;
                    default :
                        throw gcnew System::InvalidOperationException();
                }
                break;
            case GeometryClass::GEOM_LINESTRING:
                switch (f2->getGeometry()->getGeomClass()) {
                    case GeometryClass::GEOM_POINT:
                        return -1;
                    case GeometryClass::GEOM_LINESTRING:
                        break;
                    case GeometryClass::GEOM_POLYGON:
                    case GeometryClass::GEOM_COLLECTION:
                        return 1;
                    default:
                        throw gcnew System::InvalidOperationException();
                }
                break;
            case GeometryClass::GEOM_POLYGON:
                switch (f2->getGeometry()->getGeomClass()) {
                    case GeometryClass::GEOM_POINT:
                    case GeometryClass::GEOM_LINESTRING:
                        return -1;
                    case GeometryClass::GEOM_POLYGON:
                        break;
                    case GeometryClass::GEOM_COLLECTION:
                        return 1;
                    default:
                        throw gcnew System::InvalidOperationException();
                }
                break;
            case GeometryClass::GEOM_COLLECTION:
                switch (f2->getGeometry()->getGeomClass()) {
                    case GeometryClass::GEOM_POINT:
                    case GeometryClass::GEOM_LINESTRING:
                    case GeometryClass::GEOM_POLYGON:
                        return -1;
                    case GeometryClass::GEOM_COLLECTION:
                        break;
                    default:
                        throw gcnew System::InvalidOperationException();
                }
                break;
            default :
                throw gcnew System::InvalidOperationException();
        }
        
        if (f1->getId() > f2->getId())
            return 1;
        else if (f1->getId() < f2->getId())
            return -1;
        else
            return 0;
    }
}*/
