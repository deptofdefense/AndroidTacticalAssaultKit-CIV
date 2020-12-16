
#include "renderer/feature/GLPersistentFeatureDataStoreRendererLayer.h"
#include "feature/PersistentFeatureDataStore2.h"
#include "feature/RuntimeFeatureDataStore2.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    GLPersistentFeatureDataStoreRendererOptions defaultOptions() {
        GLPersistentFeatureDataStoreRendererOptions opts;
        opts.skipSameLodOptimization = true;
        return opts;
    }
}

GLPersistentFeatureDataStoreRendererLayer::GLPersistentFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject) NOTHROWS :
GLPersistentFeatureDataStoreRenderer(surface, subject.getDataStore(), GLBatchGeometryRenderer2::CachePolicy(), defaultOptions()),
subject(subject) {

    //TODO-- move to start
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLPersistentFeatureDataStoreRenderer::getOrFetchIcon: Failed to acquire mutex");
    
    this->subject.registerService(this->hittest);
}

GLPersistentFeatureDataStoreRendererLayer::GLPersistentFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS :
GLPersistentFeatureDataStoreRenderer(surface, subject.getDataStore(), cachingPolicy, defaultOptions()),
subject(subject)  {

//TODO-- move to start
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLPersistentFeatureDataStoreRenderer::getOrFetchIcon: Failed to acquire mutex");
    
    this->subject.registerService(this->hittest);

}

GLPersistentFeatureDataStoreRendererLayer::~GLPersistentFeatureDataStoreRendererLayer() NOTHROWS
{ }

atakmap::core::Layer *GLPersistentFeatureDataStoreRendererLayer::getSubject() {
    return &subject;
}

void GLPersistentFeatureDataStoreRendererLayer::start() {
}

GLPersistentFeatureDataStoreRendererLayerSpi::GLPersistentFeatureDataStoreRendererLayerSpi()
{ }

GLPersistentFeatureDataStoreRendererLayerSpi::~GLPersistentFeatureDataStoreRendererLayerSpi()
{ }

atakmap::renderer::map::layer::GLLayer *GLPersistentFeatureDataStoreRendererLayerSpi::create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const {

    TAK::Engine::Feature::FeatureLayer2 *featureLayer2 = dynamic_cast<TAK::Engine::Feature::FeatureLayer2 *>(args.layer);
    if (featureLayer2) {
        
        TAK::Engine::Feature::FeatureDataStore2 *featureDataStore2 =
            dynamic_cast<TAK::Engine::Feature::PersistentFeatureDataStore2 *>(&featureLayer2->getDataStore());
        if (!featureDataStore2) {
            featureDataStore2 = dynamic_cast<TAK::Engine::Feature::RuntimeFeatureDataStore2 *>(&featureLayer2->getDataStore());
        }
        
        if (featureDataStore2) {
            return new GLPersistentFeatureDataStoreRendererLayer(args.context->getRenderContext(), *featureLayer2);
        }
    }
    
    return nullptr;
}

unsigned int GLPersistentFeatureDataStoreRendererLayerSpi::getPriority() const throw() {
    return 3;
}
