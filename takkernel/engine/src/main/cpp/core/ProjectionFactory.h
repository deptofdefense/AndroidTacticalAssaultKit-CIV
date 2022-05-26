#ifndef ATAKMAP_CORE_PROJECTION_FACTORY_H_INCLUDED
#define ATAKMAP_CORE_PROJECTION_FACTORY_H_INCLUDED

#include "port/Platform.h"

namespace atakmap {
namespace core {

class Projection;
class ProjectionSpi;

class ENGINE_API ProjectionFactory {
private :
    ProjectionFactory();
    ~ProjectionFactory();
public :
    static Projection *getProjection(int srid);
    static void registerSpi(ProjectionSpi *spi);
    static void unregisterSpi(ProjectionSpi *spi);
    static void setPreferSdkProjections(bool sdk);
}; // end class ProjectionFactory

} // end namespace atakmap::core
} // end namespace atakmap

#endif // ATAKMAP_CORE_PROJECTION_FACTORY_H_INCLUDED
