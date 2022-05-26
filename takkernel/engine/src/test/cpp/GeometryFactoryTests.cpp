#include "pch.h"

#include <core/GeoPoint2.h>
#include <feature/GeometryCollection2.h>
#include <feature/GeometryFactory.h>
#include <feature/LineString2.h>
#include <feature/Point2.h>
#include <feature/Polygon2.h>
#include <math/Point2.h>
#include <port/Collection.h>
#include "port/STLVectorAdapter.h"
#include "renderer/Tessellate.h"
#include <util/Memory.h>


using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace TAK::Engine::Tests;

namespace takenginetests {

	class GeometryFactoryTests : public ::testing::Test
	{
	protected:

		void SetUp() override
		{
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
	};

	TEST_F(GeometryFactoryTests, extrudeInvalidDimension) {
		TAKErr code(TE_Ok);

		Geometry2Ptr value(nullptr, nullptr);
		Geometry2Ptr point2D(new Point2(0, 0), Memory_deleter_const<Geometry2, Point2>);
		code = GeometryFactory_extrude(value, *point2D, 10.0);
		ASSERT_TRUE(TE_InvalidArg == code);
	}

	TEST_F(GeometryFactoryTests, extrudePoint) {
		TAKErr code(TE_Ok);

		Geometry2Ptr value(nullptr, nullptr);

		Geometry2Ptr pointGeo(new Point2(0, 0, 0), Memory_deleter_const<Geometry2, Point2>);
		Point2 point(0, 0, 0);
		code = GeometryFactory_extrude(value, *pointGeo, 10.0);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_LineString == value->getClass());
		const LineString2& lineString = static_cast<const LineString2&>(*value);
		ASSERT_TRUE(2 == lineString.getNumPoints());
		code = lineString.get(&point, 1);
		ASSERT_TRUE(TE_Ok == code);
		const Point2 expected(0, 0, 10.0);
		ASSERT_TRUE((expected.x == point.x) &&
			(expected.y == point.y) &&
			(expected.z == point.z));


		// and try something invalid
		code = GeometryFactory_extrude(value, *pointGeo, 10.0, TEEH_IncludeBottomFace);
		ASSERT_TRUE(TE_InvalidArg == code);
	}

	void assertGeometryType(const GeometryCollection2& collection, const GeometryClass& expected) {
		TAKErr code(TE_Ok);

		STLVectorAdapter<std::shared_ptr<Geometry2>> geometries;
		code = collection.getGeometries(geometries);
		ASSERT_TRUE(TE_Ok == code);
		Collection<std::shared_ptr<Geometry2>>::IteratorPtr itr(nullptr, nullptr);
		code = geometries.iterator(itr);
		ASSERT_TRUE(TE_Ok == code);

		do {
			std::shared_ptr<Geometry2> element;
			code = itr->get(element);
			if (TE_Ok == code) {
				ASSERT_TRUE(expected == element->getClass());
				code = itr->next();
			}
		} while (TE_Ok == code);
	}

	TEST_F(GeometryFactoryTests, extrudeLine) {
		TAKErr code(TE_Ok);

		Geometry2Ptr value(nullptr, nullptr);

		Geometry2Ptr lineStringGeo(new LineString2, Memory_deleter_const<Geometry2, LineString2>);
		lineStringGeo->setDimension(3);
		LineString2& lineString = static_cast<LineString2&>(*lineStringGeo);
		code = lineString.addPoint(0, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = lineString.addPoint(1, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = lineString.addPoint(0, 1, 0);
		ASSERT_TRUE(TE_Ok == code);
		{
			// default case is to return a GeometryCollection
			code = GeometryFactory_extrude(value, *lineStringGeo, 10.0);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// three points in the line string should yield 2 polygons
			ASSERT_TRUE(2 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);

			// and now try to get one Polygon
			int hints = TEEH_GeneratePolygons;
			code = GeometryFactory_extrude(value, *lineStringGeo, 10.0, hints);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(TEGC_Polygon == value->getClass());
		}

		// close the line string
		code = lineString.addPoint(0, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		bool isClosed(false);
		code = lineString.isClosed(&isClosed);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(isClosed);

		{
			// default case is to return a GeometryCollection
			code = GeometryFactory_extrude(value, *lineStringGeo, 10.0);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// so expect 3 polygons
			ASSERT_TRUE(3 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);

			// and now try to get one Polygon
			int hints = TEEH_GeneratePolygons;
			code = GeometryFactory_extrude(value, *lineStringGeo, 10.0, hints);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(TEGC_Polygon == value->getClass());
		}

		// and try something invalid
		code = GeometryFactory_extrude(value, *lineStringGeo, 10.0, TEEH_IncludeBottomFace);
		ASSERT_TRUE(TE_InvalidArg == code);

	}

	TEST_F(GeometryFactoryTests, extrudePolygon) {
		TAKErr code(TE_Ok);

		bool isClosed(false);

		LineString2 exteriorRing;
		exteriorRing.setDimension(3);
		code = exteriorRing.addPoint(-1, -1, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = exteriorRing.addPoint(1, -1, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = exteriorRing.addPoint(1, 1, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = exteriorRing.addPoint(-1, 1, 0);
		ASSERT_TRUE(TE_Ok == code);
		// and to close ...
		code = exteriorRing.addPoint(-1, -1, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = exteriorRing.isClosed(&isClosed);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(isClosed);


		LineString2 interiorRing;
		interiorRing.setDimension(3);
		code = interiorRing.addPoint(0, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = interiorRing.addPoint(0.5, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = interiorRing.addPoint(0, 0.5, 0);
		ASSERT_TRUE(TE_Ok == code);
		// and to close ...
		code = interiorRing.addPoint(0, 0, 0);
		ASSERT_TRUE(TE_Ok == code);
		code = interiorRing.isClosed(&isClosed);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(isClosed);

		Polygon2 polygon(exteriorRing);
		code = polygon.addInteriorRing(interiorRing);
		ASSERT_TRUE(TE_Ok == code);

		Geometry2Ptr value(nullptr, nullptr);

		// should have a top and no bottom, because we're using the default, "no," hints
		code = GeometryFactory_extrude(value, polygon, 10.0);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
		{
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// so expect 8 polygons
			ASSERT_TRUE(8 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);
		}
		// get top and bottom in a collection
		int hints = TEEH_IncludeBottomFace;
		code = GeometryFactory_extrude(value, polygon, 10.0, hints);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
		{
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// so expect 9 polygons
			ASSERT_TRUE(9 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);
		}
		// and without top and bottom
		hints = TEEH_OmitTopFace;
		code = GeometryFactory_extrude(value, polygon, 10.0, hints);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
		{
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// so expect 7 polygons
			ASSERT_TRUE(7 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);
		}
		// and bottom only
		hints = TEEH_IncludeBottomFace | TEEH_OmitTopFace;
		code = GeometryFactory_extrude(value, polygon, 10.0, hints);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_GeometryCollection == value->getClass());
		{
			const GeometryCollection2& collection = static_cast<const GeometryCollection2&>(*value);
			// so expect 8 polygons
			ASSERT_TRUE(8 == collection.getNumGeometries());
			assertGeometryType(collection, TEGC_Polygon);
		}
		// and something invalid
		hints = TEEH_GeneratePolygons;
		code = GeometryFactory_extrude(value, polygon, 10.0, hints);
		ASSERT_TRUE(TE_InvalidArg == code);
	}

	double distanceCartesian(const Feature::Point2& point1, const Feature::Point2 &point2) {
		const double delX = point1.x - point2.x;
		const double delY = point1.y - point2.y;
		const double delZ = point1.z - point2.z;
		return sqrt(delX * delX + delY * delY + delZ * delZ);
	}

	double distanceWGS84(const Feature::Point2& point1, const Feature::Point2 &point2) {
		Core::GeoPoint2 gp1(point1.x, point1.y, point1.z, Core::HAE);
		Core::GeoPoint2 gp2(point2.x, point2.y, point2.z, Core::HAE);
		return Core::GeoPoint2_distance(gp1, gp2, false);
	}

	void assertRectangle(Geometry2Ptr& value, Feature::Point2 *expected, const double& epsilon,
		double(*distance)(const Feature::Point2&, const Feature::Point2&)) {
		TAKErr code(TE_Ok);
		ASSERT_TRUE(GeometryClass::TEGC_Polygon == value->getClass());
		const Polygon2 &polygon = static_cast<const Polygon2&>(*value);
		std::shared_ptr<LineString2> ring;
		code = polygon.getExteriorRing(ring);
		ASSERT_TRUE(TE_Ok == code);
		// exterior ring is closed, so 5 points
		const std::size_t maxPoints = 5;
		ASSERT_TRUE(maxPoints == ring->getNumPoints());
		for (std::size_t index = 0; maxPoints > index; ++index) {
			Feature::Point2 point(0, 0, 0);
			code = ring->get(&point, index);
			ASSERT_TRUE(TE_Ok == code);
			const Feature::Point2& expect = expected[index % 4];
			const double delta = (*distance)(point, expect);
			ASSERT_FALSE(epsilon < delta);
		}
	}

	TEST_F(GeometryFactoryTests, rectangleTwoCornersCartesian) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_CartesianAlgorithm();

		typedef Math::Point2<double> point2d;
		// try unconventional upper left, lower right
		const point2d ul(3, 3, 1);
		const point2d lr(5, 1, 2);
		Feature::Point2 expected[4] = {
			Feature::Point2(3, 1, 2),
			Feature::Point2(5, 1, 2),
			Feature::Point2(5, 3, 2),
			Feature::Point2(3, 3, 2)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, ul, lr, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 1e-6, &distanceCartesian);
	}

	TEST_F(GeometryFactoryTests, rectangleTwoCornersWGS84) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		typedef Math::Point2<double> point2d;
		// try unconventional upper left, lower right
		const point2d ul(20, 52, 100);
		const point2d lr(22, 50, 100);
		Feature::Point2 expected[4] = {
			Feature::Point2(20, 50, 100),
			Feature::Point2(22, 50, 100),
			Feature::Point2(22, 52, 100),
			Feature::Point2(20, 52, 100)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, ul, lr, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 1e-2, &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, rectangleThreePointsCartesian) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_CartesianAlgorithm();

		typedef Math::Point2<double> point2d;
		const point2d base1(1, 1, 1);
		const point2d base2(3, 1, 1);
		const point2d third(2, 3, 1);
		Feature::Point2 expected[4] = {
			Feature::Point2(1, 1, 1),
			Feature::Point2(3, 1, 1),
			Feature::Point2(3, 3, 1),
			Feature::Point2(1, 3, 1)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, base1, base2, third, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 1e-6, &distanceCartesian);
	}

	TEST_F(GeometryFactoryTests, rectangleThreePointsWGS84OnEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		typedef Math::Point2<double> point2d;
		const point2d base1(-.03, -.02, 1);
		const point2d base2(.03, -.02, 1);
		const point2d third(0.0, 0.02, 1);
		Feature::Point2 expected[4] = {
			Feature::Point2(-.03, -.02, 1),
			Feature::Point2(.03, -.02, 1),
			Feature::Point2(.03, .02, 1),
			Feature::Point2(-.03, .02, 1)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, base1, base2, third, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 2e-3, &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, rectangleThreePointsWGS84OffEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		typedef Math::Point2<double> point2d;
		const point2d base1(-.03, 80.02, 1);
		const point2d base2(.03, 80.02, 1);
		const point2d third(0.0, 79.98, 1);
		Feature::Point2 expected[4] = {
			Feature::Point2(-.03, 80.02, 1),
			Feature::Point2(.03, 80.02, 1),
			Feature::Point2(.03, 79.98, 1),
			Feature::Point2(-.03, 79.98, 1)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, base1, base2, third, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 2e-1, &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, rectangleCenterOrientedCartesian) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_CartesianAlgorithm();

		typedef Math::Point2<double> point2d;
		const double length(8), width(6);
		{
			const point2d center(3, 4, 1);
			const double orientation(0);
			Feature::Point2 expected[4] = {
				Feature::Point2(0, 0, 1),
				Feature::Point2(6, 0, 1),
				Feature::Point2(6, 8, 1),
				Feature::Point2(0, 8, 1)
			};
			Geometry2Ptr value(nullptr, nullptr);
			code = GeometryFactory_createRectangle(value, center, orientation, length, width, algo);
			ASSERT_TRUE(TE_Ok == code);
			assertRectangle(value, expected, 1e-6, &distanceCartesian);
		}
		{
			const point2d center(4, 3, 1);
			const double orientation(-450); // something not normalized
			Feature::Point2 expected[4] = {
				Feature::Point2(8, 6, 1),
				Feature::Point2(8, 0, 1),
				Feature::Point2(0, 0, 1),
				Feature::Point2(0, 6, 1)
			};
			Geometry2Ptr value(nullptr, nullptr);
			code = GeometryFactory_createRectangle(value, center, orientation, length, width, algo);
			ASSERT_TRUE(TE_Ok == code);
			assertRectangle(value, expected, 1e-6, &distanceCartesian);
		}
	}

	TEST_F(GeometryFactoryTests, rectangleCenterOrientedWGS84OnEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		typedef Math::Point2<double> point2d;
		const point2d center(0, 0, 1);
		const double orientation(0);
		const double length(.04 * GeoPoint2_approximateMetersPerDegreeLatitude(center.y));
		const double width(.06 * GeoPoint2_approximateMetersPerDegreeLongitude(center.y));
		Feature::Point2 expected[4] = {
			Feature::Point2(-.03, -.02, 1),
			Feature::Point2(.03, -.02, 1),
			Feature::Point2(.03, .02, 1),
			Feature::Point2(-.03, .02, 1)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, center, orientation, length, width, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 15., &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, rectangleCenterOrientedWGS84OffEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		typedef Math::Point2<double> point2d;
		const point2d center(0, 80, 1);
		const double orientation(0);
		const double length(.04 * GeoPoint2_approximateMetersPerDegreeLatitude(center.y));
		const double width(.06 * GeoPoint2_approximateMetersPerDegreeLongitude(center.y));

		Feature::Point2 expected[4] = {
			Feature::Point2(-.03, 79.98, 1),
			Feature::Point2(.03, 79.98, 1),
			Feature::Point2(.03, 80.02, 1),
			Feature::Point2(-.03, 80.02, 1)
		};
		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createRectangle(value, center, orientation, length, width, algo);
		ASSERT_TRUE(TE_Ok == code);
		assertRectangle(value, expected, 25., &distanceWGS84);
	}

	void assertEllipse(Geometry2Ptr &value, const Feature::Point2& expectedLower, const Feature::Point2& expectedUpper, const double epsilon,
		double(*distance)(const Feature::Point2&, const Feature::Point2&)) {

		TAKErr code(TE_Ok);

		ASSERT_TRUE(TEGC_Polygon == value->getClass());
		ASSERT_TRUE(3 == value->getDimension());

		const Feature::Polygon2& poly = static_cast<const Feature::Polygon2&>(*value);
		Feature::Envelope2 bounds;
		code = poly.getEnvelope(&bounds);
		ASSERT_TRUE(TE_Ok == code);
		const Feature::Point2 actualLower(bounds.minX, bounds.minY, bounds.minZ);
		const Feature::Point2 actualUpper(bounds.maxX, bounds.maxY, bounds.maxZ);

		double delta(0);

		delta = (*distance)(actualLower, expectedLower);
		ASSERT_FALSE(epsilon < delta);

		delta = (*distance)(actualUpper, expectedUpper);
		ASSERT_FALSE(epsilon < delta);
	}

	TEST_F(GeometryFactoryTests, ellipseMajorMinorOrientCartesian) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_CartesianAlgorithm();

		const Math::Point2<double> center(0, 0, 0);
		const double major(2000), minor(1000), orient(90);
		const Feature::Point2 expectedLower(-2000.0, -1000.0, 0.0);
		const Feature::Point2 expectedUpper(2000.0, 1000.0, 0.0);

		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createEllipse(value, center, orient, major, minor, algo);
		ASSERT_TRUE(TE_Ok == code);

		assertEllipse(value, expectedLower, expectedUpper, 1e-6, &distanceCartesian);
	}

	TEST_F(GeometryFactoryTests, ellipseMajorMinorOrientWGS84OnEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		const Math::Point2<double> center(0, 0, 0);
		const double major(.02 * GeoPoint2_approximateMetersPerDegreeLongitude(center.y));
		const double minor(.01 * GeoPoint2_approximateMetersPerDegreeLatitude(center.y));
		const double orient(0);
		const Feature::Point2 expectedLower(-0.01, -0.02, 0.0);
		const Feature::Point2 expectedUpper(0.01, 0.02, 0.0);

		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createEllipse(value, center, orient, major, minor, algo);
		ASSERT_TRUE(TE_Ok == code);

		assertEllipse(value, expectedLower, expectedUpper, 10., &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, ellipseMajorMinorOrientWGS84OffEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		const Math::Point2<double> center(0, 80, 0);
		const double major(.02 * GeoPoint2_approximateMetersPerDegreeLongitude(center.y));
		const double minor(.01 * GeoPoint2_approximateMetersPerDegreeLatitude(center.y));
		const double orient(90);
		const Feature::Point2 expectedLower(-0.02, 79.99, 0.0);
		const Feature::Point2 expectedUpper(0.02, 80.01, 0.0);

		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createEllipse(value, center, orient, major, minor, algo);
		ASSERT_TRUE(TE_Ok == code);

		assertEllipse(value, expectedLower, expectedUpper, 10., &distanceWGS84);
	}

	TEST_F(GeometryFactoryTests, ellipseRectangleOrientCartesian) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_CartesianAlgorithm();


		const Feature::Envelope2 bounds(0, 0, 0, 2000., 3000., 0);

		const Feature::Point2 expectedLower(bounds.minX, bounds.minY, bounds.minZ);
		const Feature::Point2 expectedUpper(bounds.maxX, bounds.maxY, bounds.maxZ);

		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createEllipse(value, bounds, algo);
		ASSERT_TRUE(TE_Ok == code);

		assertEllipse(value, expectedLower, expectedUpper, 1e-6, &distanceCartesian);
	}

	TEST_F(GeometryFactoryTests, ellipseMajorRectangleWGS84OffEquator) {
		TAKErr code(TE_Ok);

		Algorithm &algo = Tessellate_WGS84Algorithm();

		const double rangeX = .01 * GeoPoint2_approximateMetersPerDegreeLongitude(80.);
		const double rangeY = .02 * GeoPoint2_approximateMetersPerDegreeLatitude(80.); 

		const Feature::Envelope2 bounds(-.01, 79.98, 0., .01, 80.02, 0.);

		const Feature::Point2 expectedLower(bounds.minX, bounds.minY, bounds.minZ);
		const Feature::Point2 expectedUpper(bounds.maxX, bounds.maxY, bounds.maxZ);

		Geometry2Ptr value(nullptr, nullptr);
		code = GeometryFactory_createEllipse(value, bounds, algo);
		ASSERT_TRUE(TE_Ok == code);

		assertEllipse(value, expectedLower, expectedUpper, 10., &distanceWGS84);
	}
}