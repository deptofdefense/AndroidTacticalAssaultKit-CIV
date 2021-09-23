/*
 * RapidPositioningControlB.java
 *
 * Created on June 21, 2013, 4:36 PM
 */

package com.atakmap.map.layer.raster.gdal;

import org.gdal.gdal.Dataset;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.PointD;

/**
 * @author Developer
 */
class RapidPositioningControlB {

    private final static double DEFAULT_ELEVATION = 0.0d;

    /** The index containing the latitude. */
    private final static int P = 0;

    /** The index containing the longitude. */
    private final static int L = 1;

    /** The index containing the elevation */
    private final static int H = 2;

    /** The term exponents */
    private final static int[][] EXPONENTS = new int[][] {// P L H
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
            }, // 20
    };

    /**
     * The indices of the coefficents that will be used when computing the partial derivative with
     * respect to latitude.
     */

    private final static int[] PARTIAL_DERIVATIVE_COEFF_P = new int[] {
            2, 4, 6,
            8, 10, 12, 14, 15, 16, 18
    };

    /**
     * The indices of the coefficents that will be used when computing the partial derivative with
     * respect to longitude.
     */

    private final static int[] PARTIAL_DERIVATIVE_COEFF_L = new int[] {
            1, 4, 5,
            7, 10, 11, 12, 13, 14, 17
    };

    /**
     * The indices of the coefficents that will be used when computing the partial derivative with
     * respect to elevation.
     */

    private final static int[] PARTIAL_DERIVATIVE_COEFF_H = new int[] {
            3, 5, 6,
            9, 10, 13, 16, 17, 18, 19
    };

    /**************************************************************************/

    /** Line (row) numerator coefficients */
    private final double[] lineNumCoeff;

    /** Line (row) denominator coefficients */
    private final double[] lineDenCoeff;

    /** Sample (column) numerator coefficients */
    private final double[] sampleNumCoeff;

    /** Sample (column) denominator coefficients */
    private final double[] sampleDenCoeff;

    /** Line (row) normalization offset */
    private final double lineOffset;

    /** Line (row) normalization scale factor */
    private final double lineScale;

    /** Sample (column) normalization offset */
    private final double sampleOffset;

    /** Sample (column) normalization scale factor */
    private final double sampleScale;

    /** Latitude normalization offset */
    private final double latitudeOffset;

    /** Latitude normalization scale factor */
    private final double latitudeScale;

    /** Longitude normalization offset */
    private final double longitudeOffset;

    /** Longitude normalization scale factor */
    private final double longitudeScale;

    /** Elevation normalization offset */
    private final double heightOffset;

    /** Elevation normalization scale factor */
    private final double heightScale;

    /** The default elevation -- supplied when no elevation is provided */
    private double defaultElevation;

    private double convergenceCriteria;

    /**
     * Creates a new instance of RapidPositioningControlB
     * 
     * @param dataset The dataset
     */
    RapidPositioningControlB(Dataset dataset) {
        this.lineOffset = Integer.parseInt(dataset.GetMetadataItem("LINE_OFF",
                "RPC"));
        this.lineScale = Integer.parseInt(dataset.GetMetadataItem("LINE_SCALE",
                "RPC"));

        this.sampleOffset = Integer.parseInt(dataset.GetMetadataItem(
                "SAMP_OFF", "RPC"));
        this.sampleScale = Integer.parseInt(dataset.GetMetadataItem(
                "SAMP_SCALE", "RPC"));

        this.latitudeOffset = Double.parseDouble(dataset.GetMetadataItem(
                "LAT_OFF", "RPC"));
        this.latitudeScale = Double.parseDouble(dataset.GetMetadataItem(
                "LAT_SCALE", "RPC"));

        this.longitudeOffset = Double.parseDouble(dataset.GetMetadataItem(
                "LONG_OFF", "RPC"));
        this.longitudeScale = Double.parseDouble(dataset.GetMetadataItem(
                "LONG_SCALE", "RPC"));

        this.heightOffset = Double.parseDouble(dataset.GetMetadataItem(
                "HEIGHT_OFF", "RPC"));
        this.heightScale = Double.parseDouble(dataset.GetMetadataItem(
                "HEIGHT_SCALE", "RPC"));

        this.lineNumCoeff = new double[20];
        this.lineDenCoeff = new double[20];
        this.sampleNumCoeff = new double[20];
        this.sampleDenCoeff = new double[20];

        String[] lineNumCoeffStrs = dataset.GetMetadataItem("LINE_NUM_COEFF",
                "RPC").split("\\s");
        String[] lineDenCoeffStrs = dataset.GetMetadataItem("LINE_DEN_COEFF",
                "RPC").split("\\s");
        String[] sampNumCoeffStrs = dataset.GetMetadataItem("SAMP_NUM_COEFF",
                "RPC").split("\\s");
        String[] sampDenCoeffStrs = dataset.GetMetadataItem("SAMP_DEN_COEFF",
                "RPC").split("\\s");

        // coefficients
        for (int i = 0; i < 20; i++) {
            this.lineNumCoeff[i] = Double.parseDouble(lineNumCoeffStrs[i]);
            this.lineDenCoeff[i] = Double.parseDouble(lineDenCoeffStrs[i]);
            this.sampleNumCoeff[i] = Double.parseDouble(sampNumCoeffStrs[i]);
            this.sampleDenCoeff[i] = Double.parseDouble(sampDenCoeffStrs[i]);
        }

        // default to a normalized height of 0
        this.defaultElevation = RapidPositioningControlB.unnormalize(0,
                this.heightOffset, this.heightScale);

        this.convergenceCriteria = 0.0001d;
    }

    /**
     * Sets the default elevation. This is the elevation that will be used when none is provided.
     * 
     * @param defaultElevation The new default elevation value, specified in meters above the
     *            ellipsoid
     */
    public void setDefaultElevation(double defaultElevation) {
        this.defaultElevation = defaultElevation;
    }

    public PointD forward(GeoPoint p) {
        return this.forward(p, this.defaultElevation);
    }

    public PointD forward(GeoPoint p, double elevation) {
        // normalize the ground position
        final double normLat = normalize(p.getLatitude(), this.latitudeOffset,
                this.latitudeScale);
        final double normLon = normalize(p.getLongitude(),
                this.longitudeOffset, this.longitudeScale);
        final double normEle = normalize(elevation, this.heightOffset,
                this.heightScale);

        // compute the normalized row and column
        final double normRow = polynomial(normLat, normLon, normEle,
                this.lineNumCoeff)
                / polynomial(normLat, normLon, normEle, this.lineDenCoeff);
        final double normCol = polynomial(normLat, normLon, normEle,
                this.sampleNumCoeff)
                / polynomial(normLat, normLon, normEle, this.sampleDenCoeff);

        // unnormalize and return
        return new PointD(unnormalize(normCol, this.sampleOffset,
                this.sampleScale), unnormalize(normRow, this.lineOffset,
                this.lineScale));
    }

    public GeoPoint inverse(PointD p) {
        // default to the specified default elevation, then attempt to
        // determine a more accurate elevation using the user-supplied
        // elevation data

        double elevation = this.defaultElevation;
        GeoPoint estimate = this.inverse(p, elevation);
        if (estimate == null)
            return null;

        return this.inverse(p, elevation);
    }

    /*
     * The inverse of the ground -> image function can be computed in the same way it is done for
     * RSM -- an estimate will be made and tested for convergence. A partial derivative matrix will
     * be used to determine the ground position estimate steps during each iteration.
     */

    public GeoPoint inverse(PointD point, double elevation) {
        // normalize the image position
        final double normRow = normalize(point.y, this.lineOffset,
                this.lineScale);
        final double normCol = normalize(point.x, this.sampleOffset,
                this.sampleScale);

        // the normalized ground position
        final double normEle = normalize(elevation, this.heightOffset,
                this.heightScale);
        double normLat = 0.0d; // seed the normalized latitude
        double normLon = 0.0d; // seed the normalized longitude

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

        double[][] pdMatrix = new double[2][2];

        final int maxIterations = 15;

        boolean converged = false;
        double test;
        double minTest = Double.MAX_VALUE;
        double minNormLat = Double.NaN;
        double minNormLon = Double.NaN;

        int i = 0;
        do {
            numRowEst = polynomial(normLat, normLon, normEle, this.lineNumCoeff);
            denRowEst = polynomial(normLat, normLon, normEle, this.lineDenCoeff);
            rowEst = numRowEst / denRowEst;

            numColEst = polynomial(normLat, normLon, normEle,
                    this.sampleNumCoeff);
            denColEst = polynomial(normLat, normLon, normEle,
                    this.sampleDenCoeff);
            colEst = numColEst / denColEst;

            deltaRow = normRow - rowEst;
            deltaCol = normCol - colEst;

            test = Math.sqrt(deltaRow * deltaRow + deltaCol * deltaCol);
            if (test < minTest) {
                minTest = test;
                minNormLat = normLat;
                minNormLon = normLon;
            }

            // check for convergence
            converged = (test <= this.convergenceCriteria);
            if (!converged) {
                // compute the partial derivatives of the numerator/denominator
                // for the column and row, with respect to latitude and
                // longitude
                double derivRowNumer_wrtLatitude = partialDerivativeP(normLat,
                        normLon, normEle, this.lineNumCoeff);
                double derivRowDenom_wrtLatitude = partialDerivativeP(normLat,
                        normLon, normEle, this.lineDenCoeff);
                double derivColNumer_wrtLatitude = partialDerivativeP(normLat,
                        normLon, normEle, this.sampleNumCoeff);
                double derivColDenom_wrtLatitude = partialDerivativeP(normLat,
                        normLon, normEle, this.sampleNumCoeff);

                double derivRowNumer_wrtLongitude = partialDerivativeL(normLat,
                        normLon, normEle, this.lineNumCoeff);
                double derivRowDenom_wrtLongitude = partialDerivativeL(normLat,
                        normLon, normEle, this.lineDenCoeff);
                double derivColNumer_wrtLongitude = partialDerivativeL(normLat,
                        normLon, normEle, this.sampleNumCoeff);
                double derivColDenom_wrtLongitude = partialDerivativeL(normLat,
                        normLon, normEle, this.sampleNumCoeff);

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

                inverse2x2(new double[][] {
                        {
                                derivRow_wrtLongitude, derivRow_wrtLatitude
                        },
                        {
                                derivCol_wrtLongitude, derivCol_wrtLatitude
                        }
                },
                        pdMatrix);

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
            if (minTest == Double.MAX_VALUE)
                throw new IllegalStateException();
            normLat = minNormLat;
            normLon = minNormLon;
        }

        // unnormalize and return the ground position
        return new GeoPoint(unnormalize(normLat, this.latitudeOffset,
                this.latitudeScale), unnormalize(normLon, this.longitudeOffset,
                this.longitudeScale));
    }

    /**************************************************************************/

    /**
     * Computes the result of the polynomial as defined in STDI-0002 v2.1, pages 58-59.
     * 
     * @param normalizedLatitude The normalized latitude
     * @param normalizedLongitude The normalized longitude
     * @param normalizedElevation The normalized elevation
     * @param coeff The polynomial coefficients
     * @return The result of the polynomial
     */
    private static double polynomial(double normalizedLatitude,
            double normalizedLongitude, double normalizedElevation,
            double[] coeff) {
        double[][] vars = new double[][] {
                {
                        1.0d,
                        normalizedLatitude,
                        normalizedLatitude * normalizedLatitude,
                        normalizedLatitude * normalizedLatitude
                                * normalizedLatitude
                },
                {
                        1.0d,
                        normalizedLongitude,
                        normalizedLongitude * normalizedLongitude,
                        normalizedLongitude * normalizedLongitude
                                * normalizedLongitude
                },
                {
                        1.0d,
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

    /**
     * Computes the partial derivative of the polynomial with respect to latitude.
     * 
     * @param normalizedLatitude The normalized latitude
     * @param normalizedLongitude The normalized longitude
     * @param normalizedElevation The normalized elevation
     * @param coeff The polynomial coefficients
     * @return The result of the derivative of the polynomial with respect to latitude
     */
    private static double partialDerivativeP(double normalizedLatitude,
            double normalizedLongitude, double normalizedElevation,
            double[] coeff) {
        double[][] vars = new double[][] {
                {
                        1.0d, 1.0d, normalizedLatitude,
                        normalizedLatitude * normalizedLatitude
                },
                {
                        1.0d,
                        normalizedLongitude,
                        normalizedLongitude * normalizedLongitude,
                        normalizedLongitude * normalizedLongitude
                                * normalizedLongitude
                },
                {
                        1.0d,
                        normalizedElevation,
                        normalizedElevation * normalizedElevation,
                        normalizedElevation * normalizedElevation
                                * normalizedElevation
                },
        };

        double retval = 0;

        for (int i = 0; i < PARTIAL_DERIVATIVE_COEFF_P.length; i++)
            retval += coeff[PARTIAL_DERIVATIVE_COEFF_P[i]]
                    * vars[P][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][P]]
                    * vars[L][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][L]]
                    * vars[H][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_P[i]][H]];

        return retval;
    }

    /**
     * Computes the partial derivative of the polynomial with respect to longitude.
     * 
     * @param normalizedLatitude The normalized latitude
     * @param normalizedLongitude The normalized longitude
     * @param normalizedElevation The normalized elevation
     * @param coeff The polynomial coefficients
     * @return The result of the derivative of the polynomial with respect to longitude
     */
    private static double partialDerivativeL(double normalizedLatitude,
            double normalizedLongitude, double normalizedElevation,
            double[] coeff) {
        double[][] vars = new double[][] {
                {
                        1.0d,
                        normalizedLatitude,
                        normalizedLatitude * normalizedLatitude,
                        normalizedLatitude * normalizedLatitude
                                * normalizedLatitude
                },
                {
                        1.0d, 1.0d, normalizedLongitude,
                        normalizedLongitude * normalizedLongitude
                },
                {
                        1.0d,
                        normalizedElevation,
                        normalizedElevation * normalizedElevation,
                        normalizedElevation * normalizedElevation
                                * normalizedElevation
                },
        };

        double retval = 0;

        for (int i = 0; i < PARTIAL_DERIVATIVE_COEFF_L.length; i++)
            retval += coeff[PARTIAL_DERIVATIVE_COEFF_L[i]]
                    * vars[P][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][P]]
                    * vars[L][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][L]]
                    * vars[H][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_L[i]][H]];

        return retval;
    }

    /**
     * Computes the partial derivative of the polynomial with respect to elevation.
     * 
     * @param normalizedLatitude The normalized latitude
     * @param normalizedLongitude The normalized longitude
     * @param normalizedElevation The normalized elevation
     * @param coeff The polynomial coefficients
     * @return The result of the derivative of the polynomial with respect to elevation
     */
    private static double partialDerivativeH(double normalizedLatitude,
            double normalizedLongitude, double normalizedElevation,
            double[] coeff) {
        double[][] vars = new double[][] {
                {
                        1.0d,
                        normalizedLatitude,
                        normalizedLatitude * normalizedLatitude,
                        normalizedLatitude * normalizedLatitude
                                * normalizedLatitude
                },
                {
                        1.0d,
                        normalizedLongitude,
                        normalizedLongitude * normalizedLongitude,
                        normalizedLongitude * normalizedLongitude
                                * normalizedLongitude
                },
                {
                        1.0d, 1.0d, normalizedElevation,
                        normalizedElevation * normalizedElevation
                },
        };

        double retval = 0;

        for (int i = 0; i < PARTIAL_DERIVATIVE_COEFF_H.length; i++)
            retval += coeff[PARTIAL_DERIVATIVE_COEFF_H[i]]
                    * vars[P][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_H[i]][P]]
                    * vars[L][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_H[i]][L]]
                    * vars[H][EXPONENTS[PARTIAL_DERIVATIVE_COEFF_H[i]][H]];

        return retval;
    }

    /**
     * Normalizes the specified value
     * 
     * @param value An unnormalized value
     * @param offset The normalization offset
     * @param scale The normalization scale factor
     * @return The value, normalized
     */
    private static double normalize(double value, double offset, double scale) {
        return (value - offset) / scale;
    }

    /**
     * Unnormalizes the specified value
     * 
     * @param value A normalized value
     * @param offset The normalization offset
     * @param scale The normalization scale factor
     * @return The value, unnormalized
     */
    private static double unnormalize(double value, double offset, double scale) {
        return (value * scale) + offset;
    }

    /**
     * Computes the inverse of the specified 2x2 matrix.
     * 
     * @param matrix A 2x2 matrix, specified in row-major order
     * @param dst An optionally pre-allocated 2x2 matrix to store the inverse of the matrix in
     * @return The specified destination matrix, or a newly allocated 2x2 matrix if <code>dst</code>
     *         was null, containing the inverse of the specified matrix.
     * @throws UnsupportedOperationException if the determinant of the specified matrix is zero.
     */

    private static double[][] inverse2x2(double[][] matrix, double[][] dst) {
        if (dst == null)
            dst = new double[2][2];

        final double determinant = determinant2x2(matrix);
        if (determinant == 0)
            throw new UnsupportedOperationException(
                    "Cannot invert matrix with determinant of zero!!!");

        dst[0][0] = matrix[1][1] / determinant;
        dst[0][1] = -matrix[0][1] / determinant;
        dst[1][0] = -matrix[1][0] / determinant;
        dst[1][1] = matrix[0][0] / determinant;

        return dst;
    }

    /**
     * Returns the determinant of the specified 2x2 matrix.
     * 
     * @param matrix A 2x2 matrix, specified in row-major order
     * @return The determinant of the specified matrix.
     */

    private static double determinant2x2(double[][] matrix) {
        return (matrix[0][0] * matrix[1][1]) - (matrix[0][1] * matrix[1][0]);
    }
}
