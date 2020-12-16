#include "RapidPositioningControlB.h"
#include "util/IO.h"

namespace atakmap {
    namespace raster {
        namespace gdal {

            namespace {

                /** The index containing the latitude. */
                const int P = 0;

                /** The index containing the longitude. */
                const int L = 1;

                /** The index containing the elevation */
                const int H = 2;

                /** The term exponents */
                const int EXPONENTS[RapidPositioningControlB::numCoeffs][3] = {// P L H
                    {
                        0, 0, 0
                    }, // 1
                    {
                        0, 1, 0
                    }, // 2
                    {
                        1, 0, 0
                    }, // 3
                    {
                        0, 0, 1
                    }, // 4
                    {
                        1, 1, 0
                    }, // 5
                    // P L H
                    {
                        0, 1, 1
                    }, // 6
                    {
                        1, 0, 1
                    }, // 7
                    {
                        0, 2, 0
                    }, // 8
                    {
                        2, 0, 0
                    }, // 9
                    {
                        0, 0, 2
                    }, // 10
                    // P L H
                    {
                        1, 1, 1
                    }, // 11
                    {
                        0, 3, 0
                    }, // 12
                    {
                        2, 1, 0
                    }, // 13
                    {
                        0, 1, 2
                    }, // 14
                    {
                        1, 2, 0
                    }, // 15
                    // P L H
                    {
                        3, 0, 0
                    }, // 16
                    {
                        1, 0, 2
                    }, // 17
                    {
                        0, 2, 1
                    }, // 18
                    {
                        2, 0, 1
                    }, // 19
                    {
                        0, 0, 3
                    } // 20
                };

                const int PARTIAL_DERIVATIVE_COEFF_COUNT = 10;
                const int PARTIAL_DERIVATIVE_COEFF_P[PARTIAL_DERIVATIVE_COEFF_COUNT] = {
                    2, 4, 6, 8, 10, 12, 14, 15, 16, 18
                };

                const int PARTIAL_DERIVATIVE_COEFF_L[PARTIAL_DERIVATIVE_COEFF_COUNT] = {
                    1, 4, 5, 7, 10, 11, 12, 13, 14, 17
                };



                double normalize(double value, double offset, double scale) {
                    return (value - offset) / scale;
                }

                double unnormalize(double value, double offset, double scale) {
                    return (value * scale) + offset;
                }

                double polynomial(double normalizedLatitude,
                                  double normalizedLongitude, double normalizedElevation,
                                  double *coeff) {
                    double vars[][4] =
                    {
                        {
                            1.0,
                                normalizedLatitude,
                                normalizedLatitude * normalizedLatitude,
                                normalizedLatitude * normalizedLatitude
                                * normalizedLatitude
                        },
                        {
                            1.0,
                            normalizedLongitude,
                            normalizedLongitude * normalizedLongitude,
                            normalizedLongitude * normalizedLongitude
                            * normalizedLongitude
                        },
                        {
                            1.0,
                            normalizedElevation,
                            normalizedElevation * normalizedElevation,
                            normalizedElevation * normalizedElevation
                            * normalizedElevation
                        },
                    };

                    double retval = 0;

                    for (int i = 0; i < 20; i++)
                        retval += coeff[i] * vars[P][EXPONENTS[i][P]]
                        * vars[L][EXPONENTS[i][L]] * vars[H][EXPONENTS[i][H]];

                    return retval;
                }


                double partialDerivativeP(double normalizedLatitude,
                                          double normalizedLongitude, double normalizedElevation,
                                          double *coeff) {
                    double vars[][4] = {
                        {
                            1.0, 1.0, normalizedLatitude,
                                normalizedLatitude * normalizedLatitude
                        },
                        {
                            1.0,
                            normalizedLongitude,
                            normalizedLongitude * normalizedLongitude,
                            normalizedLongitude * normalizedLongitude
                            * normalizedLongitude
                        },
                        {
                            1.0,
                            normalizedElevation,
                            normalizedElevation * normalizedElevation,
                            normalizedElevation * normalizedElevation
                            * normalizedElevation
                        },
                    };

                    double retval = 0;

                    for (int i = 0; i < PARTIAL_DERIVATIVE_COEFF_COUNT; i++)
                        retval += coeff[PARTIAL_DERIVATIVE_COEFF_P[i]]
                        * vars[P][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][P]]
                        * vars[L][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][L]]
                        * vars[H][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][H]];

                    return retval;
                }

                double partialDerivativeL(double normalizedLatitude,
                                          double normalizedLongitude, double normalizedElevation,
                                          double *coeff) {
                    double vars[][4] = {
                        {
                            1.0,
                                normalizedLatitude,
                                normalizedLatitude * normalizedLatitude,
                                normalizedLatitude * normalizedLatitude
                                * normalizedLatitude
                        },
                        {
                            1.0, 1.0, normalizedLongitude,
                            normalizedLongitude * normalizedLongitude
                        },
                        {
                            1.0,
                            normalizedElevation,
                            normalizedElevation * normalizedElevation,
                            normalizedElevation * normalizedElevation
                            * normalizedElevation
                        },
                    };

                    double retval = 0;

                    for (int i = 0; i < PARTIAL_DERIVATIVE_COEFF_COUNT; i++)
                        retval += coeff[PARTIAL_DERIVATIVE_COEFF_L[i]]
                        * vars[P][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][P]]
                        * vars[L][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][L]]
                        * vars[H][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][H]];

                    return retval;
                }

                double determinant2x2(const double matrix[2][2]) {
                    return (matrix[0][0] * matrix[1][1]) - (matrix[0][1] * matrix[1][0]);
                }

                bool inverse2x2(const double matrix[2][2], double dst[2][2]) {
                    double determinant = determinant2x2(matrix);
                    if (determinant == 0)
                        return false;

                    dst[0][0] = matrix[1][1] / determinant;
                    dst[0][1] = -matrix[0][1] / determinant;
                    dst[1][0] = -matrix[1][0] / determinant;
                    dst[1][1] = matrix[0][0] / determinant;

                    return true;
                }

            }



            RapidPositioningControlB::RapidPositioningControlB(GDALDataset *dataset)
            {
                line_offset_ = util::parseASCIIInt(dataset->GetMetadataItem("LINE_OFF",
                    "RPC"));
                line_scale_ = util::parseASCIIInt(dataset->GetMetadataItem("LINE_SCALE",
                    "RPC"));

                sample_offset_ = util::parseASCIIInt(dataset->GetMetadataItem(
                    "SAMP_OFF", "RPC"));
                sample_scale_ = util::parseASCIIInt(dataset->GetMetadataItem(
                    "SAMP_SCALE", "RPC"));

                latitude_offset_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "LAT_OFF", "RPC"));
                latitude_scale_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "LAT_SCALE", "RPC"));

                longitude_offset_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "LONG_OFF", "RPC"));
                longitude_scale_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "LONG_SCALE", "RPC"));

                height_offset_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "HEIGHT_OFF", "RPC"));
                height_scale_ = util::parseASCIIDouble(dataset->GetMetadataItem(
                    "HEIGHT_SCALE", "RPC"));

                std::vector<std::string> lineNumCoeffStrs = util::splitString(
                    dataset->GetMetadataItem("LINE_NUM_COEFF", "RPC"), util::whitespaceDelims
                );
                std::vector<std::string> lineDenCoeffStrs = util::splitString(
                    dataset->GetMetadataItem("LINE_DEN_COEFF", "RPC"), util::whitespaceDelims
                );
                std::vector<std::string> sampNumCoeffStrs = util::splitString(
                    dataset->GetMetadataItem("SAMP_NUM_COEFF", "RPC"), util::whitespaceDelims
                );
                std::vector<std::string> sampDenCoeffStrs = util::splitString(
                    dataset->GetMetadataItem("SAMP_DEN_COEFF", "RPC"), util::whitespaceDelims
                );

                // coefficients
                for (int i = 0; i < 20; i++) {
                    line_num_coeff_[i] = util::parseASCIIDouble(lineNumCoeffStrs[i].c_str());
                    line_den_coeff_[i] = util::parseASCIIDouble(lineDenCoeffStrs[i].c_str());
                    sample_num_coeff_[i] = util::parseASCIIDouble(sampNumCoeffStrs[i].c_str());
                    sample_den_coeff_[i] = util::parseASCIIDouble(sampDenCoeffStrs[i].c_str());
                }

                // default to a normalized height of 0
                default_elevation_ = unnormalize(0, height_offset_, height_scale_);

                convergence_criteria_ = 0.0001;
                max_iterations_ = 15;

            }

            void RapidPositioningControlB::setDefaultElevation(double defaultElevation)
            {
                this->default_elevation_ = defaultElevation;
            }

            math::PointD RapidPositioningControlB::forward(const core::GeoPoint &p)
            {
                return forward(p, default_elevation_);
            }

            math::PointD RapidPositioningControlB::forward(const core::GeoPoint &p, const double elevation)
            {
                // normalize the ground position
                const double normLat = normalize(p.latitude, latitude_offset_,
                                                 latitude_scale_);
                const double normLon = normalize(p.longitude,
                                                 longitude_offset_, longitude_scale_);
                const double normEle = normalize(elevation, height_offset_,
                                                 height_scale_);

                // compute the normalized row and column
                const double normRow = polynomial(normLat, normLon, normEle,
                                                  line_num_coeff_)
                                                  / polynomial(normLat, normLon, normEle, line_den_coeff_);
                const double normCol = polynomial(normLat, normLon, normEle,
                                                  sample_num_coeff_)
                                                  / polynomial(normLat, normLon, normEle, sample_den_coeff_);

                // unnormalize and return
                return math::PointD(unnormalize(normCol, sample_offset_,
                    sample_scale_), unnormalize(normRow, line_offset_,
                    line_scale_));

            }

            core::GeoPoint RapidPositioningControlB::inverse(const math::PointD &p) throw (std::invalid_argument)
            {
                double elevation = default_elevation_;
                return inverse(p, elevation);

            }

            core::GeoPoint RapidPositioningControlB::inverse(const math::PointD point, const double elevation) throw (std::invalid_argument)
            {
                // normalize the image position
                const double normRow = normalize(point.y, line_offset_,
                                                 line_scale_);
                const double normCol = normalize(point.x, sample_offset_,
                                                 sample_scale_);

                // the normalized ground position
                const double normEle = normalize(elevation, height_offset_,
                                                 height_scale_);
                double normLat = 0.0; // seed the normalized latitude
                double normLon = 0.0; // seed the normalized longitude

                // the numerator and denominator for the row estimation
                double numRowEst;
                double denRowEst;

                // the numerator and denominator for the column estimation
                double numColEst;
                double denColEst;

                // the estimated image position
                double rowEst;
                double colEst;

                // difference between the actual and the estimate image position
                double deltaRow;
                double deltaCol;

                // ground position adustment
                double deltaLat;
                double deltaLon;

                double pdMatrix[2][2];

                const int maxIterations = 15;

                bool converged = false;
                double test;
                double minTest = HUGE_VAL;
                double minNormLat = NAN;
                double minNormLon = NAN;

                int i = 0;
                do {
                    numRowEst = polynomial(normLat, normLon, normEle, line_num_coeff_);
                    denRowEst = polynomial(normLat, normLon, normEle, line_den_coeff_);
                    rowEst = numRowEst / denRowEst;

                    numColEst = polynomial(normLat, normLon, normEle,
                                           sample_num_coeff_);
                    denColEst = polynomial(normLat, normLon, normEle,
                                           sample_den_coeff_);
                    colEst = numColEst / denColEst;

                    deltaRow = normRow - rowEst;
                    deltaCol = normCol - colEst;

                    test = sqrt(deltaRow * deltaRow + deltaCol * deltaCol);
                    if (test < minTest) {
                        minTest = test;
                        minNormLat = normLat;
                        minNormLon = normLon;
                    }

                    // check for convergence
                    converged = (test <= this->convergence_criteria_);
                    if (!converged) {
                        // compute the partial derivatives of the numerator/denominator
                        // for the column and row, with respect to latitude and
                        // longitude
                        double derivRowNumer_wrtLatitude = partialDerivativeP(normLat,
                                                                              normLon, normEle, line_num_coeff_);
                        double derivRowDenom_wrtLatitude = partialDerivativeP(normLat,
                                                                              normLon, normEle, line_den_coeff_);
                        double derivColNumer_wrtLatitude = partialDerivativeP(normLat,
                                                                              normLon, normEle, sample_num_coeff_);
                        double derivColDenom_wrtLatitude = partialDerivativeP(normLat,
                                                                              normLon, normEle, sample_num_coeff_);

                        double derivRowNumer_wrtLongitude = partialDerivativeL(normLat,
                                                                               normLon, normEle, line_num_coeff_);
                        double derivRowDenom_wrtLongitude = partialDerivativeL(normLat,
                                                                               normLon, normEle, line_den_coeff_);
                        double derivColNumer_wrtLongitude = partialDerivativeL(normLat,
                                                                               normLon, normEle, sample_num_coeff_);
                        double derivColDenom_wrtLongitude = partialDerivativeL(normLat,
                                                                               normLon, normEle, sample_num_coeff_);

                        // compute the partial derivatives for the row and columns,
                        // with respect to latitude and longitude

                        /*
                        * Let R(x,y,z) be the function that computes the row for a given x, y and z
                        * (longitude, latitude and elevation, respectively). The function R(x,y,z) is
                        * actually the result quotient of the functions Rnumer(x,y,z) and Rdenom(x,y,z),
                        * where Rnumer and Rdenom are the polynomials for the row numerator and denominator
                        * respectively. The partial derivative of R(x,y,z) with respect to latitude will
                        * therefore be the partial derivative of Rnumer(x,y,z)/Rdenom(x,y,z) which
                        * evaluates to: ( Rdenom(x,y,z)*[derivative Rnumer(x,y,z) w.r.t. y] -
                        * Rnumer(x,y,z)*[derivative Rdenom(x,y,z) w.r.t. y] ) / ( Rdenom(x,y,z) *
                        * Rdenom(x,y,z) The partial derivatives for the row w.r.t. longitude, column w.r.t.
                        * latitude and column w.r.t. latitude will be computed similarly, with the
                        * appropriate function substitutions.
                        */

                        double derivRow_wrtLatitude = (denRowEst
                                                       * derivRowNumer_wrtLatitude - numRowEst
                                                       * derivRowDenom_wrtLatitude)
                                                       / (denRowEst * denRowEst);
                        double derivRow_wrtLongitude = (denRowEst
                                                        * derivRowNumer_wrtLongitude - numRowEst
                                                        * derivRowDenom_wrtLongitude)
                                                        / (denRowEst * denRowEst);
                        double derivCol_wrtLatitude = (denColEst
                                                       * derivColNumer_wrtLatitude - numColEst
                                                       * derivColDenom_wrtLatitude)
                                                       / (denColEst * denColEst);
                        double derivCol_wrtLongitude = (denColEst
                                                        * derivColNumer_wrtLongitude - numColEst
                                                        * derivColDenom_wrtLongitude)
                                                        / (denColEst * denColEst);

                        // create a partial derivative matrix

                        // | pdRwrtLon prRwrtLat |
                        // | pdCwrtLon pdCwrtLat |
                        double derivMatrix[2][2] = {
                            { derivRow_wrtLongitude, derivRow_wrtLatitude },
                            { derivCol_wrtLongitude, derivCol_wrtLatitude }
                        };
                        if (!inverse2x2(derivMatrix, pdMatrix))
                            throw std::invalid_argument("Can't inverse matrix");

                        // determine the normalized lat/lon adjustments

                        // the deltas will be equal to the delta row/col estimates times
                        // the inverse of the partial derivative matrix

                        // | pdRwrtLon prRwrtLat | -1 X | deltaRow |
                        // | pdCwrtLon pdCwrtLat | | deltaCol |

                        deltaLon = pdMatrix[0][0] * deltaRow + pdMatrix[0][1]
                            * deltaCol;
                        deltaLat = pdMatrix[1][0] * deltaRow + pdMatrix[1][1]
                            * deltaCol;

                        // adjust the normalized lat/lon
                        normLat += deltaLat;
                        normLon += deltaLon;
                    }

                    if (++i > maxIterations)
                        break;
                } while (!converged);

                // the ground position could not be computed, return the derived
                // coorinate that resulted in our minimum test value
                if (!converged) {
                    if (minTest == HUGE_VAL)
                        throw std::invalid_argument("");
                    normLat = minNormLat;
                    normLon = minNormLon;
                }

                // unnormalize and return the ground position
                return core::GeoPoint(unnormalize(normLat, latitude_offset_,
                    latitude_scale_), unnormalize(normLon, longitude_offset_,
                    longitude_scale_));

            }
        }
    }
}

