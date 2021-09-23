#include "pch.h"
#include "feature/LineString2.h"
#include "feature/Polygon2.h"
#include "feature/SpatialCalculator2.h"
#include "raster/osm/OSMUtils.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

using namespace atakmap::raster::osm;

namespace takenginetests {

	TEST(SpatialCalcualtor2Tests, testGEOSSupport) {
		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);
		TAK::Engine::Port::String version;
		code = calc.getGEOSVersion(&version);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, version.get());
	}

	TEST(SpatialCalcualtor2Tests, testRoundTripWKT) {
		TAKErr code(TE_Ok);
		int64_t handle(NULL);
		SpatialCalculator2 calc(NULL);

		const TAK::Engine::Port::String wktSource("POLYGON((10 10, 10 20, 20 20, 20 15, 10 10))");
		code = calc.createGeometryFromWkt(&handle, wktSource.get());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(1 == handle);

		TAK::Engine::Port::String wktSink;
		code = calc.getGeometryAsWkt(&wktSink, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wktSource == wktSink);
	}

	TEST(SpatialCalcualtor2Tests, testRoundTripWKB) {
		TAKErr code(TE_Ok);
		int64_t handle(NULL);
		SpatialCalculator2 calc(NULL);
		const std::size_t len(21);
		const uint8_t wkb[len]{ 0x00,0x00, 0x00, 0x00, 0x01, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x40, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		code = calc.createGeometryFromWkb(&handle, wkb, len);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(1 == handle);

		TAK::Engine::Port::String wktSink;
		const TAK::Engine::Port::String wktSource("POINT(2 4)");
		code = calc.getGeometryAsWkt(&wktSink, handle);

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wktSource == wktSink);

	}

	TEST(SpatialCalcualtor2Tests, testRoundTripBlob) {
		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);

		int64_t handleSource(NULL);
		const TAK::Engine::Port::String wktSource("POLYGON((10 10, 10 20, 20 20, 20 15, 10 10))");
		code = calc.createGeometryFromWkt(&handleSource, wktSource.get());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleSource);

		std::size_t len(NULL);
		SpatialCalculator2::BlobPtr blob(NULL, NULL);
		code = calc.getGeometryAsBlob(blob, &len, handleSource);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, blob.get());
		ASSERT_TRUE(0 < len);

		int64_t handleSink(NULL);
		code = calc.createGeometryFromBlob(&handleSink, blob.get(), len);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleSink);
		ASSERT_FALSE(handleSink == handleSource);

		TAK::Engine::Port::String wktSink;
		code = calc.getGeometryAsWkt(&wktSink, handleSink);

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wktSource == wktSink);
	}

	TEST(SpatialCalcualtor2Tests, testRoundTripGeom) {
		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);

		int64_t handleSource(NULL);
		const TAK::Engine::Port::String wktSource("POLYGON((10 10, 10 20, 20 20, 20 15, 10 10))");
		code = calc.createGeometryFromWkt(&handleSource, wktSource.get());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleSource);

		Geometry2Ptr geom(NULL, NULL);
		code = calc.getGeometry(geom, handleSource);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, geom.get());

		int64_t handleSink(NULL);
		code = calc.createGeometry(&handleSink, *geom);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleSink);
		ASSERT_FALSE(handleSink == handleSource);

		TAK::Engine::Port::String wktSink;
		code = calc.getGeometryAsWkt(&wktSink, handleSink);

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wktSource == wktSink);
	}

	TEST(SpatialCalcualtor2Tests, testPolygonAndType) {

		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);

		const Point2 a(10, 10), b(10, 20), c(20, 20), d(20, 15);
		int64_t handle(NULL);
		code = calc.createPolygon(&handle, a, b, c, d);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		TAK::Engine::Port::String wkt;
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);

		const TAK::Engine::Port::String wktReference("POLYGON((10 10, 10 20, 20 20, 20 15, 10 10))");
		ASSERT_TRUE(wktReference == wkt);

		GeometryClass geom_type(static_cast<GeometryClass>(-1)); // initialize with something wrong
		code = calc.getGeometryType(&geom_type, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_Polygon == geom_type);
	}

	TEST(SpatialCalcualtor2Tests, testGeomTypeAndDelete) {

		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);
		int64_t handle(NULL);

		typedef std::map<TAK::Engine::Port::String, GeometryClass> GeometryTypeMap;
		const GeometryTypeMap wktGeomMap = {
				{ "POINT(2 4)", TEGC_Point },
				{ "LINESTRING (30 10, 10 30, 40 40)", TEGC_LineString },
				{ "MULTIPOINT((2 4), (6 8))", TEGC_GeometryCollection },
				{ "MULTILINESTRING((2 4, 4 6, 6 8), (3 6, 6 9, 9 12))", TEGC_GeometryCollection },
				{ "MULTIPOLYGON(((40 40, 20 45, 45 30, 40 40)), ((20 35, 10 30, 10 10, 30 5, 45 20, 20 35), (30 20, 20 15, 20 25, 30 20)))", TEGC_GeometryCollection },
				{ "GEOMETRYCOLLECTION(POINT (40 10), LINESTRING(10 10, 20 20, 10 40), POLYGON((40 40, 20 45, 45 30, 40 40)))", TEGC_GeometryCollection }
		};

		for (GeometryTypeMap::const_iterator itr = wktGeomMap.begin(); wktGeomMap.end() != itr; ++itr) {
			code = calc.createGeometryFromWkt(&handle, itr->first);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(0 < handle);

			GeometryClass geom_type(static_cast<GeometryClass>(-1)); // initialize with something wrong
			code = calc.getGeometryType(&geom_type, handle);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(itr->second == geom_type);

			code = calc.deleteGeometry(handle);
			ASSERT_TRUE(TE_Ok == code);
			code = calc.getGeometryType(&geom_type, handle);
			ASSERT_FALSE(TE_Ok == code);
		}
	}

	TEST(SpatialCalcualtor2Tests, testGeomOperations) {

		// Create an array of wkt for polygons with different relationships. Using a one based index,
		// One intersects Two which intersects Three. One and Three do not intersect. One contains Four.
		// Multipolygons Five is One, Two, and Three, and Six is One, Two and Four for UnaryUnion and simplifies
		const TAK::Engine::Port::String polygons[] = {
			"POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))", // One
			"POLYGON((5 0, 25 0, 25 10, 5 10, 5 0 ))", // Two
			"POLYGON((20 0, 30 0, 30 10, 20 10, 20 0 ))", // Three
			"POLYGON((2 2, 2 8, 8 8, 8 2, 2 2 ))", // Four
			"MULTIPOLYGON(((0 0, 10 0, 10 10, 0 10, 0 0), (5 0, 25 0, 25 10, 5 10, 5 0 ), (20 0, 30 0, 30 10, 20 10, 20 0 )))", // Five
			"MULTIPOLYGON(((0 0, 10 0, 10 10, 0 10, 0 0), (5 0, 25 0, 25 10, 5 10, 5 0 ), (2 2, 2 8, 8 8, 8 2, 2 2 )))" // Six
		};


		TAKErr code(TE_Ok);
		int64_t handle(NULL);
		TAK::Engine::Port::String wkt, wktReference;
		SpatialCalculator2 calc(NULL);

		// Assert that handles correspond with one based index described above.
		for (std::size_t index = 0; 4 > index; ++index) {
			code = calc.createGeometryFromWkt(&handle, polygons[index].get());
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(index + 1 == handle);
		}

		// intersection tests
		bool intersected(false);
		code = calc.intersects(&intersected, 1, 2);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(intersected);

		code = calc.intersects(&intersected, 1, 3);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(intersected);

		code = calc.intersects(&intersected, 1, 4);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(intersected);

		// contain tests
		bool contained(false);
		code = calc.contains(&contained, 1, 2);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(contained);

		code = calc.contains(&contained, 1, 3);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(contained);

		code = calc.contains(&contained, 1, 4);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(contained);

		// intersection creation and update

		code = calc.createIntersection(&handle, 1, 2);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		wktReference = "POLYGON((10 0, 5 0, 5 10, 10 10, 10 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		code = calc.updateIntersection(2, 3, handle);
		ASSERT_TRUE(TE_Ok == code);

		wktReference = "POLYGON((25 0, 20 0, 20 10, 25 10, 25 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		// union create and update

		code = calc.createUnion(&handle, 1, 2);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		wktReference = "POLYGON((5 0, 0 0, 0 10, 5 10, 10 10, 25 10, 25 0, 10 0, 5 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		code = calc.updateUnion(2, 3, handle);
		ASSERT_TRUE(TE_Ok == code);

		wktReference = "POLYGON((20 0, 5 0, 5 10, 20 10, 25 10, 30 10, 30 0, 25 0, 20 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		// unary union creation and update

		code = calc.createUnaryUnion(&handle, 5);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		wktReference = "POLYGON((25 0, 20 0, 20 10, 25 10, 25 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		code = calc.updateUnaryUnion(6, handle);
		ASSERT_TRUE(TE_Ok == code);

		wktReference = "POLYGON((20 0, 5 0, 5 10, 20 10, 25 10, 30 10, 30 0, 25 0, 20 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		// difference creation and update

		code = calc.createDifference(&handle, 1, 2);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		wktReference = "POLYGON((5 0, 0 0, 0 10, 5 10, 5 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		code = calc.updateDifference(2, 3, handle);
		ASSERT_TRUE(TE_Ok == code);

		wktReference = "POLYGON((20 0, 5 0, 5 10, 20 10, 20 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		// simplify create and update

		code = calc.createSimplify(&handle, 5, 1.0, false);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		wktReference = "POLYGON((25 0, 20 0, 20 10, 25 10, 25 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);

		code = calc.updateSimplify(6, 1.0, true, handle);
		ASSERT_TRUE(TE_Ok == code);

		wktReference = "POLYGON((20 0, 5 0, 5 10, 30 10, 30 0, 20 0))";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktReference);
	}

	TEST(SpatialCalcualtor2Tests, testBufferCreateUpdate) {

		TAKErr code(TE_Ok);
		SpatialCalculator2 calc(NULL);
		Envelope2 envelope;

		int64_t handleOriginal(NULL);
		TAK::Engine::Port::String wkt = "POINT(10 10)";
		code = calc.createGeometryFromWkt(&handleOriginal, wkt);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleOriginal);

		int64_t handleMutable(NULL);
		code = calc.createBuffer(&handleMutable, handleOriginal, 3.0);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handleMutable);

		Geometry2Ptr geom(NULL, NULL);
		code = calc.getGeometry(geom, handleMutable);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, geom.get());
		ASSERT_TRUE(TEGC_Polygon == geom->getClass());

		code = geom->getEnvelope(&envelope);
		ASSERT_TRUE(TE_Ok == code);

		const double areaCreate = (envelope.maxX - envelope.minX) * (envelope.maxY - envelope.minY);

		code = calc.updateBuffer(handleOriginal, 30.0, handleMutable);
		ASSERT_TRUE(TE_Ok == code);

		code = calc.getGeometry(geom, handleMutable);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, geom.get());
		ASSERT_TRUE(TEGC_Polygon == geom->getClass());

		code = geom->getEnvelope(&envelope);
		ASSERT_TRUE(TE_Ok == code);

		const double areaUpdate = (envelope.maxX - envelope.minX) * (envelope.maxY - envelope.minY);

		ASSERT_TRUE(areaUpdate > areaCreate);
	}

	TEST(SpatialCalcualtor2Tests, testLineStringCreate) {

		TAKErr code(TE_Ok);
		int64_t handle(NULL);
		SpatialCalculator2 calc(NULL);

		TAK::Engine::Port::String wkt = "LINESTRING (10 10, 20 11, 30 10, 10 30, 40 40)";
		code = calc.createGeometryFromWkt(&handle, wkt);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(0 < handle);

		Geometry2Ptr geom(NULL, NULL);
		code = calc.getGeometry(geom, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(TEGC_LineString == geom->getClass());
		LineString2Ptr original(static_cast<LineString2*>(geom.release()), Memory_deleter_const<LineString2>);
		ASSERT_NE(nullptr, original.get());

		// do not preserve topology
		LineString2Ptr resultNoTopo(NULL, NULL);
		code = calc.createSimplify(resultNoTopo, *original, 10.0, false);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, resultNoTopo.get());
		ASSERT_TRUE(TEGC_LineString == resultNoTopo->getClass());

		code = calc.updateGeometry(*resultNoTopo, handle);
		ASSERT_TRUE(TE_Ok == code);

		const TAK::Engine::Port::String wktNoTopo = "LINESTRING(10 10, 20 11, 30 10, 10 30, 40 40)";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktNoTopo);

		// preserve topology
		LineString2Ptr resultWithTopo(NULL, NULL);
		code = calc.createSimplify(resultWithTopo, *original, 10.0, true);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_NE(nullptr, resultWithTopo.get());
		ASSERT_TRUE(TEGC_LineString == resultWithTopo->getClass());

		code = calc.updateGeometry(*resultWithTopo, handle);
		ASSERT_TRUE(TE_Ok == code);

		const TAK::Engine::Port::String wktWithTopo = "LINESTRING(10 10, 20 11, 30 10, 10 30, 40 40)";
		code = calc.getGeometryAsWkt(&wkt, handle);
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(wkt == wktWithTopo);
	}

	TEST(SpatialCalcualtor2Tests, testPolygonIntersects) {
		// simulate polygon tile download

		TAKErr code(TE_Ok);

		// L shaped polygon
		Point2 upperLeft(-92.06279, 35.20715);
		Point2 lowerRight(-92.02373, 35.17655);

		LineString2 ext;
		ext.setDimension(2u);
		ext.addPoint(-92.04476, 35.20751);
		ext.addPoint(-92.04529, 35.18834);
		ext.addPoint(-92.02300, 35.18829);
		ext.addPoint(-92.02302, 35.17658);
		ext.addPoint(-92.06268, 35.17632);
		ext.addPoint(-92.06330, 35.20749);
		ext.addPoint(-92.04476, 35.20751);

		Polygon2 poly(ext);

		const std::size_t level = 14u;
		const int stx = OSMUtils::mapnikTileX(level, upperLeft.x);
		const int sty = OSMUtils::mapnikTileY(level, upperLeft.y);
		const int ftx = OSMUtils::mapnikTileX(level, lowerRight.x);
		const int fty = OSMUtils::mapnikTileY(level, lowerRight.y);

		ASSERT_EQ(2, (ftx - stx + 1));
		ASSERT_EQ(2, (fty - sty + 1));

		SpatialCalculator2 calc(NULL);
		int64_t hpoly(0LL);
		code = calc.createGeometry(&hpoly, poly);
		ASSERT_TRUE(TE_Ok == code);

		// upper left tile, intersects
		{
			int64_t hultile(0LL);
			code = calc.createPolygon(&hultile,
				Point2(OSMUtils::mapnikTileLng(level, stx), OSMUtils::mapnikTileLat(level, sty)),
				Point2(OSMUtils::mapnikTileLng(level, stx + 1), OSMUtils::mapnikTileLat(level, sty)),
				Point2(OSMUtils::mapnikTileLng(level, stx + 1), OSMUtils::mapnikTileLat(level, sty + 1)),
				Point2(OSMUtils::mapnikTileLng(level, stx), OSMUtils::mapnikTileLat(level, sty + 1)));
			ASSERT_TRUE(TE_Ok == code);

			bool ulIsects;
			code = calc.intersects(&ulIsects, hpoly, hultile);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(ulIsects);
		}

		// upper-right tile, does not intersect
		{
			int64_t hurtile(0LL);
			code = calc.createPolygon(&hurtile,
				Point2(OSMUtils::mapnikTileLng(level, ftx), OSMUtils::mapnikTileLat(level, sty)),
				Point2(OSMUtils::mapnikTileLng(level, ftx + 1), OSMUtils::mapnikTileLat(level, sty)),
				Point2(OSMUtils::mapnikTileLng(level, ftx + 1), OSMUtils::mapnikTileLat(level, sty + 1)),
				Point2(OSMUtils::mapnikTileLng(level, ftx), OSMUtils::mapnikTileLat(level, sty + 1)));
			ASSERT_TRUE(TE_Ok == code);

			bool urIsects;
			code = calc.intersects(&urIsects, hpoly, hurtile);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_FALSE(urIsects);
		}

		// lower-right tile, does not intersect
		{
			int64_t hlrtile(0LL);
			code = calc.createPolygon(&hlrtile,
				Point2(OSMUtils::mapnikTileLng(level, ftx), OSMUtils::mapnikTileLat(level, fty)),
				Point2(OSMUtils::mapnikTileLng(level, ftx + 1), OSMUtils::mapnikTileLat(level, fty)),
				Point2(OSMUtils::mapnikTileLng(level, ftx + 1), OSMUtils::mapnikTileLat(level, fty + 1)),
				Point2(OSMUtils::mapnikTileLng(level, ftx), OSMUtils::mapnikTileLat(level, fty + 1)));
			ASSERT_TRUE(TE_Ok == code);

			bool lrIsects;
			code = calc.intersects(&lrIsects, hpoly, hlrtile);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(lrIsects);
		}

		// lower-left tile, does not intersect
		{
			int64_t hlltile(0LL);
			code = calc.createPolygon(&hlltile,
				Point2(OSMUtils::mapnikTileLng(level, stx), OSMUtils::mapnikTileLat(level, fty)),
				Point2(OSMUtils::mapnikTileLng(level, stx + 1), OSMUtils::mapnikTileLat(level, fty)),
				Point2(OSMUtils::mapnikTileLng(level, stx + 1), OSMUtils::mapnikTileLat(level, fty + 1)),
				Point2(OSMUtils::mapnikTileLng(level, stx), OSMUtils::mapnikTileLat(level, fty + 1)));
			ASSERT_TRUE(TE_Ok == code);

			bool llIsects;
			code = calc.intersects(&llIsects, hpoly, hlltile);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_TRUE(llIsects);
		}
	}
}
