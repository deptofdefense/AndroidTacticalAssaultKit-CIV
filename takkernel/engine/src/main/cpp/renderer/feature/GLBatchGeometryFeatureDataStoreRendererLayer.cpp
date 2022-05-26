
#include "renderer/feature/GLBatchGeometryFeatureDataStoreRendererLayer.h"
#include "feature/DataSourceFeatureDataStore2.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    GLBatchGeometryFeatureDataStoreRendererOptions defaultOptions() {
        GLBatchGeometryFeatureDataStoreRendererOptions opts;
        opts.skipSameLodOptimization = true;
        return opts;
    }
}

GLBatchGeometryFeatureDataStoreRendererLayer::GLBatchGeometryFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject) NOTHROWS :
GLBatchGeometryFeatureDataStoreRenderer(surface, subject.getDataStore(), GLBatchGeometryRenderer2::CachePolicy(), defaultOptions()),
subject(subject) {

    //TODO-- move to start
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLBatchGeometryFeatureDataStoreRenderer::<init>: Failed to acquire mutex");
    
    this->subject.registerService(this->hittest);
}

GLBatchGeometryFeatureDataStoreRendererLayer::GLBatchGeometryFeatureDataStoreRendererLayer(atakmap::renderer::GLRenderContext *surface, TAK::Engine::Feature::FeatureLayer2 &subject, const GLBatchGeometryRenderer2::CachePolicy &cachingPolicy) NOTHROWS :
GLBatchGeometryFeatureDataStoreRenderer(surface, subject.getDataStore(), cachingPolicy, defaultOptions()),
subject(subject)  {

//TODO-- move to start
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLBatchGeometryFeatureDataStoreRenderer::<init>: Failed to acquire mutex");
    
    this->subject.registerService(this->hittest);

}

GLBatchGeometryFeatureDataStoreRendererLayer::~GLBatchGeometryFeatureDataStoreRendererLayer() NOTHROWS
{ }

atakmap::core::Layer *GLBatchGeometryFeatureDataStoreRendererLayer::getSubject() {
    return &subject;
}

void GLBatchGeometryFeatureDataStoreRendererLayer::start() {
}

GLBatchGeometryFeatureDataStoreRendererLayerSpi::GLBatchGeometryFeatureDataStoreRendererLayerSpi()
{ }

GLBatchGeometryFeatureDataStoreRendererLayerSpi::~GLBatchGeometryFeatureDataStoreRendererLayerSpi()
{ }

atakmap::renderer::map::layer::GLLayer *GLBatchGeometryFeatureDataStoreRendererLayerSpi::create(const atakmap::renderer::map::layer::GLLayerSpiArg &args) const {

    TAK::Engine::Feature::FeatureLayer2 *featureLayer2 = dynamic_cast<TAK::Engine::Feature::FeatureLayer2 *>(args.layer);
    if (featureLayer2) {
        
        TAK::Engine::Feature::DataSourceFeatureDataStore2 *featureDataStore2 = dynamic_cast<TAK::Engine::Feature::DataSourceFeatureDataStore2 *>(&featureLayer2->getDataStore());
        
        if (featureDataStore2) {
            return new GLBatchGeometryFeatureDataStoreRendererLayer(args.context->getRenderContext(), *featureLayer2);
        }
    }
    
    return nullptr;
}

unsigned int GLBatchGeometryFeatureDataStoreRendererLayerSpi::getPriority() const throw() {
    return 3;
}
