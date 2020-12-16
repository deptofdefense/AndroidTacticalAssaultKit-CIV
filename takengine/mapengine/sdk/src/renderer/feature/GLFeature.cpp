
#include "feature/Feature.h"
#include "renderer/feature/GLFeature.h"
#include "renderer/feature/GLGeometry.h"
#include "renderer/feature/GLStyle.h"
#include "renderer/feature/GLStyleSpi.h"

//#include "renderer/feature/style/GLStyleSpi_CLI.h"

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;
using namespace atakmap::renderer::map;

namespace {
    class FeatureRunnable {
        GLFeature *owner;
        PGSC::RefCountablePtr<atakmap::feature::Feature> feature;
    public:
        FeatureRunnable(const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature, GLFeature *owner)
        : owner(owner), feature(feature) { }
        
        static void glRun(void *opaque) {
            FeatureRunnable *runnable = (FeatureRunnable *)opaque;
            PGSC::RefCountablePtr<atakmap::feature::Feature> &feature = runnable->feature;
            atakmap::renderer::feature::GLFeature *owner = runnable->owner;
            if (!Feature::isSame(*owner->subject, *feature)) {
                owner->subject = feature;
                owner->release();
            }
            delete runnable;
        }
    };
}
                
GLFeature::GLFeature(GLRenderContext *context, const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature)
: context(context),
  subject(feature),
  initialized(false),
  style(nullptr),
  geometry(nullptr),
  styleCtx(nullptr) { }

void GLFeature::init(const GLMapView *view) {
    // XXX--get rid of the const_cast
    geometry = std::unique_ptr<GLGeometry>(GLGeometry::createRenderer(const_cast<Geometry *>(&subject->getGeometry())));
    
    GLStyleSpiArg arg;
    arg.style = subject->getStyle();
    arg.geometry = &subject->getGeometry();
    
    style = std::unique_ptr<GLStyle>(GLStyleFactory::create(arg));
    if (style != nullptr)
        this->styleCtx = style->createRenderContext(view, geometry.get());
    
    initialized = true;
}

Feature *GLFeature::getSubject() {
    return subject.get();
}

void GLFeature::draw(const GLMapView *view) {
    if (!initialized)
        init(view);
    if (style == nullptr)
        return;
    style->draw(view, geometry.get(), styleCtx);
}

void GLFeature::release()
{
    if (style != nullptr) {
        style->releaseRenderContext(styleCtx);
        styleCtx = nullptr;
        style = nullptr;
    }
    if (geometry != nullptr)
        geometry = nullptr;
    initialized = false;
}

bool GLFeature::isBatchable(const GLMapView *view) {
    if (!initialized)
        init(view);
    return (style == nullptr || style->isBatchable(view, geometry.get(), styleCtx));
}

void GLFeature::batch(const GLMapView *view, GLRenderBatch *batch) {
    if (!initialized)
        init(view);
    if (style == nullptr)
        return;
    style->batch(view, batch, geometry.get(), styleCtx);
}

void GLFeature::update(const PGSC::RefCountablePtr<atakmap::feature::Feature> &feature) {
    if (this->context->isGLThread() && !Feature::isSame(*subject, *feature)) {
        this->subject = feature;
        release();
    } else if (!context->isGLThread()) {
        // XXX - if feature IDs can be assumed to be the same, we can
        //       compare versions here as version must be monotonically
        //       increasing
        context->runOnGLThread(FeatureRunnable::glRun, new FeatureRunnable(feature, this));
    }
}

void GLFeature::start() { }

void GLFeature::stop()
{
    // XXX - 
}
