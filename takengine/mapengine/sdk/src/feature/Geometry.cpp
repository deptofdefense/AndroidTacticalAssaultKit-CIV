#include "feature/Geometry.h"

#include "util/IO.h"

using namespace atakmap::feature;

Geometry::~Geometry ()
    NOTHROWS
  { }


void
Geometry::setDimension (Dimension dim)
  {
    if (dim != dimension_)
      {
        changeDimension (dim);
        dimension_ = dim;
      }
  }

void atakmap::feature::destructGeometry(const Geometry *geom)
  {
    delete geom;
  }

void
atakmap::feature::Geometry::insertBlobHeader(std::ostream& strm,
                                             const Envelope& env)
  {
    atakmap::util::write<uint32_t>(strm.put(BLOB_START_BYTE).put(util::ENDIAN_BYTE),
        4326);       // Spatial reference ID.
    atakmap::util::write(strm, env.minX);
    atakmap::util::write(strm, env.minY);
    atakmap::util::write(strm, env.maxX);
    atakmap::util::write(strm, env.maxY).put(MBR_END_BYTE);
  }
