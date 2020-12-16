
#include <list>

#include "port/Iterator.h"

#include "renderer/feature/GLGeometryCollectionStyle.h"

#include "renderer/feature/GLGeometryCollection.h"

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::feature;
using namespace atakmap::renderer::map;

namespace {
    class Ctx : public atakmap::renderer::feature::StyleRenderContext {
    public:
        std::list<std::pair<GLStyle *, StyleRenderContext *>> styles;

    public:
        Ctx(GLStyleSpi *spi, const GLMapView *view, const Style *style, GLGeometryCollection *collection) {
            
            std::auto_ptr<atakmap::port::Iterator<GLGeometry *>> it(collection->getIterator());
            while (it->hasNext()) {
                GLGeometry *geom = it->get();
                // XXX - implement with spi
                GLStyle *s = nullptr;
                //s = spi->create(Pair.<Style, Geometry>create(style, geom.getSubject()));
                StyleRenderContext *ctx = (s != nullptr) ? s->createRenderContext(view, geom) : nullptr;
                this->styles.push_back(std::pair<GLStyle *, StyleRenderContext *>(s, ctx));
            }
        }
    };
}


GLGeometryCollectionStyle::GLGeometryCollectionStyle(const atakmap::feature::Style *style, GLStyleSpi *spi)
: GLStyle(style), spi(spi) { }

GLGeometryCollectionStyle::~GLGeometryCollectionStyle() { }

StyleRenderContext *GLGeometryCollectionStyle::createRenderContext(const GLMapView *view, GLGeometry *geometry) {
    return new Ctx(spi, view, style, (GLGeometryCollection *)geometry);
}

void GLGeometryCollectionStyle::releaseRenderContext(StyleRenderContext *context)
{
    if (context == nullptr)
        return;
    Ctx *ctx = (Ctx *)context;
    for (std::pair<GLStyle *, StyleRenderContext *> &e : ctx->styles) {
        if (e.first != nullptr && e.second != nullptr)
            e.first->releaseRenderContext(e.second);
    }

}

void GLGeometryCollectionStyle::draw(const GLMapView *view, GLGeometry *g, StyleRenderContext *context) {

    Ctx *ctx = (Ctx *)context;

    GLGeometryCollection *geometry = (GLGeometryCollection *)g;
    
    std::auto_ptr<port::Iterator<GLGeometry *>> it(geometry->getIterator());
    GLGeometry *child;
    for (std::pair<GLStyle *, StyleRenderContext *> &e : ctx->styles) {
        child = it->next();
        if (e.first == nullptr)
            continue;
        e.first->draw(view, child, e.second);
    }
}

void GLGeometryCollectionStyle::batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *g, StyleRenderContext *context) {
    
    Ctx *ctx = (Ctx *)context;

    GLGeometryCollection *geometry = (GLGeometryCollection *)g;
    std::auto_ptr<port::Iterator<GLGeometry *>> it(geometry->getIterator());
    GLGeometry *child;
    
    for (std::pair<GLStyle *, StyleRenderContext *> &e : ctx->styles) {
        child = it->next();
        if (e.first == nullptr)
            continue;
        e.first->batch(view, batch, child, e.second);
    }
}

bool GLGeometryCollectionStyle::isBatchable(const GLMapView *view, GLGeometry *g, StyleRenderContext *context) {
    
    Ctx *ctx = (Ctx *)context;

    GLGeometryCollection *geometry = (GLGeometryCollection *)g;
    std::auto_ptr<port::Iterator<GLGeometry *>> it(geometry->getIterator());
    GLGeometry *child;
    for (std::pair<GLStyle *, StyleRenderContext *> &e : ctx->styles) {
        child = it->next();
        if (e.first == nullptr)
            continue;
        if (!e.first->isBatchable(view, child, e.second))
            return false;
    }
    
    return true;
}
