#ifndef TAK_ENGINE_RASTER_PRECISIONIMAGERYFACTORY_H_INCLUDED
#define TAK_ENGINE_RASTER_PRECISIONIMAGERYFACTORY_H_INCLUDED

#include "raster/PrecisionImagerySpi.h"

namespace TAK {
	namespace Engine {
		namespace Raster {
			ENGINE_API Util::TAKErr PrecisionImageryFactory_register(const std::shared_ptr<PrecisionImagerySpi> &spi) NOTHROWS;
			ENGINE_API Util::TAKErr PrecisionImageryFactory_unregister(const std::shared_ptr<PrecisionImagerySpi> &spi) NOTHROWS;
			ENGINE_API Util::TAKErr PrecisionImageryFactory_create(PrecisionImageryPtr &result, const char *URI, const char *hint = nullptr) NOTHROWS;
			ENGINE_API Util::TAKErr PrecisionImageryFactory_isSupported(const char *URI, const char *hint = nullptr) NOTHROWS;
		}
	}
}

#endif