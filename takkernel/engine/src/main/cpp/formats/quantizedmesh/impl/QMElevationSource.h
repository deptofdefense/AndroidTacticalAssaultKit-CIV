#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMELEVATIONSOURCE_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMELEVATIONSOURCE_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"
#include "port/Vector.h"
#include "util/Error.h"

#include "formats/quantizedmesh/QMESourceLayer.h"
#include "elevation/ElevationSource.h"


namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {


/**
 * Virtual database for quantized mesh elevation data (stored in .terrain files)
 */
class QMElevationSource : public Elevation::ElevationSource
{
public:
    QMElevationSource(std::shared_ptr<QMESourceLayer> layer) NOTHROWS;
    virtual ~QMElevationSource() NOTHROWS;
    virtual const char *getName() const NOTHROWS;
    virtual Util::TAKErr query(Elevation::ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS;
    virtual Feature::Envelope2 getBounds() const NOTHROWS;
    virtual Util::TAKErr addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS;
    virtual Util::TAKErr removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS;

private:
    std::shared_ptr<QMESourceLayer> layer;
    Feature::Envelope2 bounds;
};

}
}
}
}
}

#endif

