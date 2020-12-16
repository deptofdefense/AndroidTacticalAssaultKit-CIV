
#include "raster/DatasetDescriptor.h"

#include "renderer/map/layer/raster/GLMapLayerFactory.h"
#include "renderer/map/layer/raster/GLMapLayer.h"

#include "thread/Mutex.h"
#include "thread/Lock.h"

using namespace atakmap::renderer;
using namespace atakmap::renderer::map::layer::raster;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

GLMapLayerFactory::GLMapLayerSpiList GLMapLayerFactory::spis;
Mutex GLMapLayerFactory::mutex;

GLMapLayerFactory::GLMapLayerFactory() {
}

GLMapLayer *GLMapLayerFactory::create(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLMapLayerFactory::create: Failed to acquire mutex");
    
    GLMapLayer *retval;
    GLMapLayerSpiList::iterator end = spis.end();
    for (GLMapLayerSpiList::iterator it = spis.begin(); it != end; ++it) {
        if ((retval = (*it)->createLayer(context, info))) {
            return retval;
        }
    }
    
    return NULL;
}

void GLMapLayerFactory::registerSpi(GLMapLayerSPI *spi) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLMapLayerFactory::registerSpi: Failed to acquire mutex");
    
    spis.push_back(spi);
}

void GLMapLayerFactory::unregisterSpi(GLMapLayerSPI *spi) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLMapLayerFactory::unregisterSpi: Failed to acquire mutex");
    
    GLMapLayerSpiList::iterator end = spis.end();
    for (GLMapLayerSpiList::iterator it = spis.begin(); it != end; ++it) {
        if ((*it) == spi) {
            spis.erase(it);
            break;
        }
    }
}

