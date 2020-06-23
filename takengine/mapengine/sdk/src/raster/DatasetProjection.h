#ifndef ATAKMAP_RASTER_DATASETPROJECTION_H_INCLUDED
#define ATAKMAP_RASTER_DATASETPROJECTION_H_INCLUDED

#include "math/Point.h"
#include "core/GeoPoint.h"

namespace atakmap {
    namespace raster {
        class DatasetProjection {
        public:
            virtual ~DatasetProjection();
            
            /**
             * Performs the image-to-ground function for the specified pixel in the
             * image.
             *
             * @param image     The image pixel
             * @param ground    If non-<code>null</code> returns the geodetic coordinate
             *                  for the given pixel coordinate
             *
             * @return  The geodetic coordinate for the specified pixel coordinate. If
             *          <code>ground</code> was non-<code>null</code>,
             *          <code>ground</code> is updated and returned.
             */
            virtual bool imageToGround(const math::PointD &image, core::GeoPoint *ground) const = 0;
            
            /**
             * Performs the ground-to-image function for the specified geodetic
             * coordinate.
             *
             * @param ground    A geodetic coordinate
             * @param image     If non-<code>null</code> returns the pixel coordinate
             *                  for the given geodetic coordinate
             *
             * @return  The pixel coordinate for the specified geodetic coordinate. If
             *          <code>image</code> was non-<code>null</code>, <code>image</code>
             *          is updated and returned.
             */
            virtual bool groundToImage(const core::GeoPoint &ground, math::PointD *image) const = 0;
        };
    }
}

#endif // ATAKMAP_RASTER_DATASETPROJECTION_H_INCLUDED
