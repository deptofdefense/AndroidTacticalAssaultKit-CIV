
#include <list>

#include "feature/Style.h"

#include "renderer/feature/GLGeometry.h"
#include "renderer/feature/GLCompositeStyle.h"

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;
using namespace atakmap::renderer::map;

/*GLCompositeStyle::CompositeStyleRenderContext::CompositeStyleRenderContext(const atakmap::renderer::map::GLMapView *view,
                                                                           const atakmap::feature::CompositeStyle *style,
                                                                           GLGeometry *geom) {
    GLStyle *s;
    for (int i = 0; i < style->getStyleCount(); i++) {
        GLStyleSpiArg arg;
        arg.geometry = geom->getSubject();
        arg.style = &style->getStyle(i);
        s = GLStyleFactory::create(arg);
        if (s == nullptr)
            continue;
        this->styles.push_back(std::make_pair(s, s->createRenderContext(view, geom)));
    }
}*/

GLCompositeStyle::CompositeStyleRenderContext::~CompositeStyleRenderContext() {
    for (std::pair<GLStyle *, StyleRenderContext *> &e : styles) {
        delete e.first;
    }
}

GLCompositeStyle::GLCompositeStyle(const atakmap::feature::CompositeStyle *style)
: GLStyle(style) { }

GLCompositeStyle::~GLCompositeStyle() { }

void GLCompositeStyle::draw(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) {
    
    CompositeStyleRenderContext *context = (CompositeStyleRenderContext *)ctx;
    
    for (std::pair<GLStyle *, StyleRenderContext *> &e : context->styles)
        e.first->draw(view, geometry, e.second);
}

void GLCompositeStyle::batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *geometry, StyleRenderContext *ctx) {
    
    CompositeStyleRenderContext *context = (CompositeStyleRenderContext *)ctx;

    for (std::pair<GLStyle *, StyleRenderContext *> &e : context->styles)
        e.first->batch(view, batch, geometry, e.second);
}

bool GLCompositeStyle::isBatchable(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) {
    
    CompositeStyleRenderContext *context = (CompositeStyleRenderContext *)ctx;
    
    for (std::pair<GLStyle *, StyleRenderContext *> &e : context->styles)
        if (!e.first->isBatchable(view, geometry, e.second))
            return false;
    
    return true;
}

StyleRenderContext *GLCompositeStyle::createRenderContext(const GLMapView *view, GLGeometry *geometry) {
    
    CompositeStyleRenderContext *context = new CompositeStyleRenderContext();
    const CompositeStyle *compositeStyle = static_cast<const CompositeStyle *>(this->style);
    
    GLStyle *s;
    for (int i = 0; i < compositeStyle->getStyleCount(); i++) {
        GLStyleSpiArg arg;
        arg.geometry = geometry->getSubject();
        arg.style = &compositeStyle->getStyle(i);
        s = GLStyleFactory::create(arg);
        if (s == nullptr)
            continue;
        context->styles.push_back(std::make_pair(s, s->createRenderContext(view, geometry)));
    }
    return context;
}

void GLCompositeStyle::releaseRenderContext(StyleRenderContext *ctx) {

    CompositeStyleRenderContext *context = (CompositeStyleRenderContext *)ctx;
    if (context == nullptr)
        return;
    
    for (std::pair<GLStyle *, StyleRenderContext *> &e : context->styles) {
        if (e.first != nullptr) {
            e.first->releaseRenderContext(e.second);
        }
    }
    
    delete context;
}

GLStyleSpi *GLCompositeStyle::getSpi() {
    static Spi spi;
    return &spi;
}

GLCompositeStyle::Spi::~Spi() { }

GLStyle *GLCompositeStyle::Spi::create(const atakmap::renderer::feature::GLStyleSpiArg &arg) {

    const Style *s = arg.style;
    const Geometry *g = arg.geometry;
    
    if (s == nullptr || g == nullptr)
        return nullptr;
    
    const CompositeStyle *cs = dynamic_cast<const CompositeStyle *>(s);
    if (cs == nullptr)
        return nullptr;

    return new GLCompositeStyle(cs);
}


