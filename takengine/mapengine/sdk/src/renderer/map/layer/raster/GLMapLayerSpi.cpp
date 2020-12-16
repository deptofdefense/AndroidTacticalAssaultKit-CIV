
#include <stdexcept>
#include <cstring>

#include "renderer/map/layer/raster/GLMapLayerSpi.h"

using namespace atakmap::renderer::map::layer::raster;

GLMapLayerSPI::~GLMapLayerSPI() throw() { }

bool GLMapLayerSPI::checkSupportedTypes(const char *const *supportedTypes, const char *type) {
    
    if (!supportedTypes || !type) {
        throw std::invalid_argument("null arguments");
    }
    
    while(*supportedTypes) {
        if (strcmp(*supportedTypes, type) == 0) {
            return true;
        }
        ++supportedTypes;
    }
    
    return false;
}