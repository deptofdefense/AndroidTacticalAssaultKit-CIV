#ifndef TAK_ENGINE_RASTER_DATASETPROJECTION2_H_INCLUDED
#define TAK_ENGINE_RASTER_DATASETPROJECTION2_H_INCLUDED

#include <memory>

#include "util/Error.h"
#include "core/GeoPoint2.h"
#include "math/Matrix2.h"
#include "math/Point2.h"

namespace TAK {
	namespace Engine {
		namespace Raster {
			class ENGINE_API DatasetProjection2 {
			public:
                DatasetProjection2() NOTHROWS;

				virtual ~DatasetProjection2() NOTHROWS;

				/**
				 * Performs the image-to-ground function for the specified pixel in the
				 * image.
				 *
				 * @param ground    If non-<code>null</code> returns the geodetic coordinate
				 *                  for the given pixel coordinate
				 * @param image     The image pixel
				 *
				 * @return  The geodetic coordinate for the specified pixel coordinate. If
				 *          <code>ground</code> was non-<code>null</code>,
				 *          <code>ground</code> is updated and returned.
				 */
				virtual Util::TAKErr imageToGround(Core::GeoPoint2 *ground, const Math::Point2<double> &image) const NOTHROWS = 0;

				/**
				 * Performs the ground-to-image function for the specified geodetic
				 * coordinate.
				 *
				 * @param image     If non-<code>null</code> returns the pixel coordinate
				 *                  for the given geodetic coordinate
				 * @param ground    A geodetic coordinate
				 *
				 * @return  The pixel coordinate for the specified geodetic coordinate. If
				 *          <code>image</code> was non-<code>null</code>, <code>image</code>
				 *          is updated and returned.
				 */
				virtual Util::TAKErr groundToImage(Math::Point2<double> *image, const Core::GeoPoint2 &ground) const NOTHROWS = 0;
			};

			typedef std::unique_ptr<DatasetProjection2, void(*)(const DatasetProjection2 *)> DatasetProjection2Ptr;

            ENGINE_API Util::TAKErr DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const Math::Matrix2 &img2proj) NOTHROWS;
            ENGINE_API Util::TAKErr DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const Math::Matrix2 &img2proj, const Math::Matrix2 &proj2img) NOTHROWS;
            ENGINE_API Util::TAKErr DatasetProjection2_create(DatasetProjection2Ptr &value, const int srid, const std::size_t width, const std::size_t height, const Core::GeoPoint2 &ul, const Core::GeoPoint2 &ur, const Core::GeoPoint2 &lr, const Core::GeoPoint2 &ll) NOTHROWS;
		}
	}
}

#endif
