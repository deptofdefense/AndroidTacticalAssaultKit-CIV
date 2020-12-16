#ifndef ATAKMAP_RASTER_GDAL_RAPIDPOSITIONINGCONTROLB_H_INCLUDED
#define ATAKMAP_RASTER_GDAL_RAPIDPOSITIONINGCONTROLB_H_INCLUDED

#include "math/Point.h"
#include "gdal_priv.h"
#include "core/GeoPoint.h"
#include <vector>
#include <stdexcept>

namespace atakmap {
    namespace raster {
        namespace gdal {

            class RapidPositioningControlB {

            public:
                RapidPositioningControlB(GDALDataset *dataset);

                void setDefaultElevation(double defaultElevation);
                math::PointD forward(const core::GeoPoint &p);
                math::PointD forward(const core::GeoPoint &p, const double elevation);
                core::GeoPoint inverse(const math::PointD &p) throw (std::invalid_argument);
                core::GeoPoint inverse(const math::PointD point, const double elevation) throw (std::invalid_argument);

                static const int numCoeffs = 20;
            private:
                double line_num_coeff_[numCoeffs];

                /** Line (row) denominator coefficients */
                double line_den_coeff_[numCoeffs];

                /** Sample (column) numerator coefficients */
                double sample_num_coeff_[numCoeffs];

                /** Sample (column) denominator coefficients */
                double sample_den_coeff_[numCoeffs];

                /** Line (row) normalization offset */
                double line_offset_;

                /** Line (row) normalization scale factor */
                double line_scale_;

                /** Sample (column) normalization offset */
                double sample_offset_;

                /** Sample (column) normalization scale factor */
                double sample_scale_;

                /** Latitude normalization offset */
                double latitude_offset_;

                /** Latitude normalization scale factor */
                double latitude_scale_;

                /** Longitude normalization offset */
                double longitude_offset_;

                /** Longitude normalization scale factor */
                double longitude_scale_;

                /** Elevation normalization offset */
                double height_offset_;

                /** Elevation normalization scale factor */
                double height_scale_;

                /** The default elevation -- supplied when no elevation is provided */
                double default_elevation_;

                double convergence_criteria_;

                int max_iterations_;

            };
        }
    }
}

#endif
