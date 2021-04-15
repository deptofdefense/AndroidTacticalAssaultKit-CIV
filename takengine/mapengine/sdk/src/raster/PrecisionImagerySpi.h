#ifndef TAK_ENGINE_RASTER_PRECISIONIMAGERYSPI_H_INCLUDED
#define TAK_ENGINE_RASTER_PRECISIONIMAGERYSPI_H_INCLUDED

#include "raster/PrecisionImagery.h"

namespace TAK {
	namespace Engine {
		namespace Raster {

			class ENGINE_API PrecisionImagerySpi {
			public:
                PrecisionImagerySpi() NOTHROWS;

				virtual ~PrecisionImagerySpi() NOTHROWS;

				virtual Util::TAKErr create(PrecisionImageryPtr &result, const char *URI) const NOTHROWS = 0;

				virtual Util::TAKErr isSupported(const char *URI) const NOTHROWS = 0;

				virtual const char *getType() const NOTHROWS = 0;

				virtual int getPriority() const NOTHROWS = 0;
			};

			typedef std::unique_ptr<PrecisionImagerySpi, void(*)(const PrecisionImagerySpi *)> PrecisionImagerySpiPtr;
		}
	}
}

#endif