#ifndef ATAKMAP_CORE_PROJECTION_SPI_H_INCLUDED
#define ATAKMAP_CORE_PROJECTION_SPI_H_INCLUDED

namespace atakmap {
namespace core {

class Projection;

class ProjectionSpi {
public :
    virtual ~ProjectionSpi() {};
public :
    virtual Projection *create(int srid) = 0;
}; // end class ProjectionSpi

} // end namespace atakmap::core
} // end namespace atakmap

#endif // ATAKMAP_CORE_PROJECTION_SPI_H_INCLUDED
