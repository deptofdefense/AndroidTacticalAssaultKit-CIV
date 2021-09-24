#ifndef TAK_ENGINE_FORMATS_DTEDELEVATIONSOURCE_H_INCLUDED
#define TAK_ENGINE_FORMATS_DTEDELEVATIONSOURCE_H_INCLUDED

#include "elevation/ElevationSource.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace DTED {
                class ENGINE_API DtedElevationSource : public Elevation::ElevationSource
                {
                public :
                    DtedElevationSource(const char *dir) NOTHROWS;
                    ~DtedElevationSource() NOTHROWS override = default;
                public :
                    const char *getName() const NOTHROWS override;
                    Util::TAKErr query(Elevation::ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS override;
                    Feature::Envelope2 getBounds() const NOTHROWS override;
                    Util::TAKErr addOnContentChangedListener(Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS override;
                    Util::TAKErr removeOnContentChangedListener(Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS override;
                private :
                    TAK::Engine::Port::String dir_;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_FORMATS_DTEDELEVATIONSOURCE_H_INCLUDED
