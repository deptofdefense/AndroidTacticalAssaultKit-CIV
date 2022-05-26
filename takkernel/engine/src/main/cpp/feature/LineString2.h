#ifndef TAK_ENGINE_FEATURE_LINESTRING2_H_INCLUDED
#define TAK_ENGINE_FEATURE_LINESTRING2_H_INCLUDED

#include "feature/Geometry2.h"
#include "feature/Point2.h"
#include "port/Platform.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API LineString2 : public Geometry2
            {
            public :
                /**
                 * Creates a new LineString2 with a default dimension of '2'.
                 */
                LineString2() NOTHROWS;
                LineString2(const LineString2 &other) NOTHROWS;
            private :
                void growPoints(const std::size_t newPointCapacity) NOTHROWS;
            public :
                /**
                 * Adds the specified point to the linestring. If the
                 * linestring is 3D, a z-coordinate of '0' will be assumed.
                 *
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure
                 */
                Util::TAKErr addPoint(const double x, const double y) NOTHROWS;
                /**
                 * Adds the specified point to the linestring. If the
                 * linestring is 2D, TE_IllegalState is returned.
                 *
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 * @param z The z-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure
                 */
                Util::TAKErr addPoint(const double x, const double y, const double z) NOTHROWS;
                /**
                 * Adds the specified points to the linestring. If 'ptsDim' is
                 * '2' and the linestring is 2D, the z-coordinate for all
                 * points will be assumed to be '0'.
                 *
                 * @param pts       The points, interleaved by component
                 * @param numPoints The number of points
                 * @param ptsDim    The dimension of the points
                 *
                 * @return  TE_Ok on success. TE_InvalidArg will be returned if
                 *          'ptsDim' is not '2' or '3', or if the linestring is
                 *          2D and 'ptsDim' is '3'.
                 */
                Util::TAKErr addPoints(const double *pts, const std::size_t numPts, const std::size_t ptsDim) NOTHROWS;

                /**
                 * Removes the point at the specified index from the linestring.
                 *
                 * @param i The index
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr removePoint(const std::size_t i) NOTHROWS;

                /**
                 * Removes a range of points from the linestring.
                 *
                 * @param offset    The index of the first point to be removed
                 * @param count     The number of points to be removed
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr removePoints(const std::size_t offset, const std::size_t count) NOTHROWS;

                /**
                 * Clears all points in the linestring.
                 */
                void clear() NOTHROWS;

                /**
                 * Returns the number of points in the linestring.
                 */
                std::size_t getNumPoints() const NOTHROWS;

                /**
                 * Returns the x-coordinate of the specified point.
                 *
                 * @param value Returns the x-coordinate
                 * @param i     The point index
                 *
                 * @return TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getX(double *value, const std::size_t i) const NOTHROWS;
                /**
                 * Returns the y-coordinate of the specified point.
                 *
                 * @param value Returns the y-coordinate
                 * @param i     The point index
                 *
                 * @return TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getY(double *value, const std::size_t i) const NOTHROWS;
                /**
                 * Returns the z-coordinate of the specified point. If the
                 * linestring is 2D, TE_IllegalState will be returned.
                 *
                 * @param value Returns the z-coordinate
                 * @param i     The point index
                 *
                 * @return TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr getZ(double *value, const std::size_t i) const NOTHROWS;
                /**
                 * Returns the specified point. The supplied Point2 will have
                 * its dimension reset to the dimension of this linestring.
                 *
                 * @param value Returns the point
                 * @param i     The point index
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr get(Point2 *value, const std::size_t i) const NOTHROWS;

                /**
                 * Sets the x-coordinate of the specified point.
                 *
                 * @param i The point index
                 * @param x The x-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr setX(const std::size_t i, const double x) NOTHROWS;
                /**
                 * Sets the y-coordinate of the specified point.
                 *
                 * @param i The point index
                 * @param y The y-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr setY(const std::size_t i, const double y) NOTHROWS;
                /**
                 * Sets the z-coordinate of the specified point. If the
                 * linestring is 2D, TE_IllegalState is returned.
                 *
                 * @param i The point index
                 * @param z The z-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr setZ(const std::size_t i, const double z) NOTHROWS;
                /**
                 * Sets the x,y coordinate values of the specified point. If
                 * The linestring is 3D, the z-cooridnate is NOT modified.
                 *
                 * @param i The point index
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr set(const std::size_t i, const double x, const double y) NOTHROWS;
                /**
                 * Sets the x,y,z coordinate values of the specified point. If
                 * the linestring is 3D, TE_IllegalState will be returned.
                 *
                 * @param i The point index
                 * @param x The x-coordinate
                 * @param y The y-coordinate
                 * @param z The z-coordinate
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr set(const std::size_t i, const double x, const double y, const double z) NOTHROWS;
                /**
                 * Sets the specified point on the linestring. If the
                 * linestring is 2D and the point is 3D, TE_IllegalState is
                 * returned. If the linestring is 3D and the point is 2D, the
                 * z-coordinate is assumed to be zero.
                 *
                 * @param i     The point index
                 * @param point The point
                 *
                 * @return  TE_Ok on succcess; various codes on failure.
                 */
                Util::TAKErr set(const std::size_t i, const Point2 &point) NOTHROWS;

                /**
                 * Returns 'true' if the linestring is closed (first point
                 * equals end point), 'false' otherwise. An empty linestring is
                 * not considered closed.
                 *
                 * @param value Returns the flag indicating whether or not the
                 *              linestring is closed.
                 *
                 * @return  TE_Ok on success; various codes on failure.
                 */
                Util::TAKErr isClosed(bool *value) const NOTHROWS;
            public : // Geometry implementation
                virtual std::size_t getDimension() const NOTHROWS;
                virtual Util::TAKErr getEnvelope(Envelope2 *value) const NOTHROWS;
            private :
                virtual Util::TAKErr setDimensionImpl(const std::size_t dimension) NOTHROWS;
                virtual bool equalsImpl(const Geometry2 &other) NOTHROWS;
            private :
                std::size_t dimension;
                std::size_t numPoints;
                Util::array_ptr<double> points;
                std::size_t pointsLength;
            };

            typedef std::unique_ptr<LineString2, void(*)(const LineString2 *)> LineString2Ptr;
        }
    }
}
#endif
