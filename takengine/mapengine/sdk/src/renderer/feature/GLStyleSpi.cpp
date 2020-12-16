

#include "thread/Lock.h"
#include "renderer/feature/GLStyleSpi.h"
#include "renderer/feature/GLStyle.h"

using namespace atakmap::renderer::feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

GLStyleSpi::~GLStyleSpi() { }

std::list<GLStyleSpi *> GLStyleFactory::spis;
Mutex GLStyleFactory::mutex;

void GLStyleFactory::registerSpi(atakmap::renderer::feature::GLStyleSpi *spi) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLStyleFactory::unregisterSpi: failed to acquire mutex");
    spis.push_back(spi);
}

void GLStyleFactory::unregisterSpi(atakmap::renderer::feature::GLStyleSpi *spi) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLStyleFactory::unregisterSpi: failed to acquire mutex");
    std::list<GLStyleSpi *>::iterator it = std::find(spis.begin(), spis.end(), spi);
    if (it != spis.end()) {
        spis.erase(it);
    }
}

GLStyle *GLStyleFactory::create(const atakmap::renderer::feature::GLStyleSpiArg &style) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("GLStyleFactory::unregisterSpi: failed to acquire mutex");
    GLStyle *retval = NULL;
    
    std::list<GLStyleSpi *>::iterator it = spis.begin();
    std::list<GLStyleSpi *>::iterator end = spis.end();
    for (; it != end; ++it) {
        if ((retval = (*it)->create(style))) {
            break;
        }
    }
    
    return retval;
}
