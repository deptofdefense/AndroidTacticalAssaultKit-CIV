#ifndef TAK_ENGINE_RASTER_PRECISIONIMAGERY_H_INCLUDED
#define TAK_ENGINE_RASTER_PRECISIONIMAGERY_H_INCLUDED

#include "raster/ImageInfo.h"
#include "raster/DatasetProjection2.h"

namespace TAK {
	namespace Engine {
		namespace Raster {
			class ENGINE_API PrecisionImagery {
			public:
                PrecisionImagery() NOTHROWS;

				virtual ~PrecisionImagery() NOTHROWS;
				
				/** The Mensurated Product type */
				virtual const char *getType() const NOTHROWS = 0;
				
				/** The basic product properties (e.g. path, name, resolution, corner coords */
				virtual Util::TAKErr getInfo(ImageInfo *) const NOTHROWS = 0;
				
				/** The Image-to-Ground and Ground-to-Image Point Mensuration functions */
				virtual Util::TAKErr getDatasetProjection(DatasetProjection2Ptr &proj) const NOTHROWS = 0;
			};

			typedef std::unique_ptr<PrecisionImagery, void(*)(const PrecisionImagery *)> PrecisionImageryPtr;
		}
	}
}

#endif