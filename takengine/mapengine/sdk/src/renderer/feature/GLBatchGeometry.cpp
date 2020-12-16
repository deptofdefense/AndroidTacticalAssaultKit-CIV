
#include "renderer/feature/GLBatchGeometry.h"

#include "util/IO.h"

using namespace atakmap::renderer::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::util;

namespace
{
    struct SetGeometryArgs {
        GLBatchGeometry *geom;
        MemBufferT<uint8_t> *blob;
        int type;
    };
}

GLBatchGeometry::GLBatchGeometry(GLRenderContext *surface, int zOrder) {
    this->surface = surface;
    this->zOrder = zOrder;
}

GLBatchGeometry::~GLBatchGeometry() { }

void GLBatchGeometry::init(int64_t featureId, const char *name)
{
    this->featureId = featureId;
    this->name = name;
}

void GLBatchGeometry::setGeometry(MemBufferT<uint8_t> *blob, int type, int lod) {
    this->lod = lod;
    if (this->surface->isGLThread()) {
        this->setGeometryImpl(blob, type);
    }
    else {
        SetGeometryArgs *args = new SetGeometryArgs;
        args->geom = this;
        args->blob = blob;
        args->type = type;
        this->surface->runOnGLThread(setGeometryRunnable, args);
    }
}

void GLBatchGeometry::start()
{}

void GLBatchGeometry::stop()
{
    // XXX - 
}

void GLBatchGeometry::setGeometryRunnable(void *opaque) {
    SetGeometryArgs *args = static_cast<SetGeometryArgs *>(opaque);
    args->geom->setGeometryImpl(args->blob, args->type);
}
