#include "pch.h"

#include <cmath>

#include <core/GeoPoint2.h>
#include <math/Point2.h>
#include <renderer/Tessellate.h>
#include <renderer/GLTriangulate.h>
#include <util/Memory.h>


using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace TAK::Engine::Tests;

namespace takenginetests {

	class TessellationTests : public ::testing::Test
	{
	protected:

		void SetUp() override
		{
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
	};

	TEST_F(TessellationTests, tessellate_polygon_QuadNoThreshold) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		src.stride = 16u;
		std::size_t resultCount;
		TAKErr code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());

		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_EQ((size_t)6u, resultCount);
	}

	TEST_F(TessellationTests, tessellate_polygon_InvalidPointCount) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		src.stride = 16u;
		std::size_t resultCount;

		code = Tessellate_polygon<double>(result, &resultCount, src, 0u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);

		code = Tessellate_polygon<double>(result, &resultCount, src, 1u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);

		code = Tessellate_polygon<double>(result, &resultCount, src, 2u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);
	}

	TEST_F(TessellationTests, tessellate_polygon_NullResultCount) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		src.stride = 16u;

		code = Tessellate_polygon<double>(result, nullptr, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);
	}

	TEST_F(TessellationTests, tessellate_polygon_NullSrcData) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = nullptr;
		src.size = 2u;
		src.stride = 16u;
		std::size_t resultCount;

		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);
	}

	TEST_F(TessellationTests, tessellate_polygon_InvalidSrcSize) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		std::size_t resultCount;

		// too small
		src.size = 0u;
		src.stride = src.size * sizeof(double);
		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);

		// too small
		src.size = 1u;
		src.stride = src.size * sizeof(double);
		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);

		// too big
		src.size = 4u;
		src.stride = src.size * sizeof(double);
		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);
	}

	TEST_F(TessellationTests, tessellate_polygon_InvalidSrcStride) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		std::size_t resultCount;

		// not specified
		src.stride = 0;
		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);

		// too small
		src.stride = src.size * sizeof(double) - 1u;
		code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_NE((int)TE_Ok, (int)code);
	}

	TEST_F(TessellationTests, tessellate_polygon_MinimumValidPointCount) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		TAKErr code(TE_Ok);

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		src.stride = 16u;
		std::size_t resultCount;

		code = Tessellate_polygon<double>(result, &resultCount, src, 3u, 0.0, Tessellate_WGS84Algorithm());
		ASSERT_EQ((int)TE_Ok, (int)code);
	}
#if 0
	// benchmarking utility ~125k points, ~80ms
	TEST_F(TessellationTests, tessellate_QuadThreshold) {
		GeoPoint2 a(35.88427, -150.31091);
		GeoPoint2 b(55.31723, -33.40704);
		GeoPoint2 c(28.74988, -48.55265);
		GeoPoint2 d(11.50095, -120.34446);

		double pts[8];
		pts[0] = a.longitude;
		pts[1] = a.latitude;
		pts[2] = b.longitude;
		pts[3] = b.latitude;
		pts[4] = c.longitude;
		pts[5] = c.latitude;
		pts[6] = d.longitude;
		pts[7] = d.latitude;

		VertexDataPtr result(nullptr, nullptr);
		VertexData src;
		src.data = pts;
		src.size = 2u;
		src.stride = 16u;
		std::size_t resultCount;
		int64_t s = TAK::Engine::Port::Platform_systime_millis();
		TAKErr code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 45000 * 25.0 / 16.0, Tessellate_WGS84Algorithm());
		//TAKErr code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0.725, Tessellate_CartesianAlgorithm());
		//TAKErr code = Tessellate_polygon<double>(result, &resultCount, src, 4u, 0, Tessellate_WGS84Algorithm());
		int64_t e = TAK::Engine::Port::Platform_systime_millis();

		Logger_log(TELL_Info, "Tessellate in %dms, count=%u", (int)(e - s), resultCount);

		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_TRUE(resultCount < 150000);
		ASSERT_EQ((int)124506, (int)resultCount);
	}
#endif

	TEST_F(TessellationTests, tessellate_algorithm_cartesian_intersect) {
		typedef Math::Point2<double> point2d;
		const point2d origin1(0, 0, 0);
		const point2d dir1(1, 1, 1);
		const point2d origin2(0, 3, 3);
		const point2d dir2(1, 0, 0);
		const point2d expected(3, 3, 3);
		const Algorithm &algo = Tessellate_CartesianAlgorithm();
		point2d actual;
		TAKErr code = algo.intersect(actual, origin1, dir1, origin2, dir2);
		ASSERT_TRUE(TE_Ok == code);
		const double delX = actual.x - expected.x;
		const double delY = actual.y - expected.y;
		const double delZ = actual.z - expected.z;
		ASSERT_TRUE(1e-12 > (delX * delX + delY * delY + delZ * delZ));
	}

	void roundTripIntersection(const Math::Point2<double>& reference) {
		typedef Math::Point2<double> point2d;
		// arbitrary choices of direction
		static const double out1 = 40 * M_PI / 180.;
		static const double out2 = -55 * M_PI / 180.;
		static const double in1 = out1 - M_PI;
		static const double in2 = out2 + M_PI;
		const point2d outbound1(sin(out1), cos(out1), 0);
		const point2d outbound2(sin(out2), cos(out2), 0);
		const point2d inbound1(sin(in1), cos(in1), 0);
		const point2d inbound2(sin(in2), cos(in2), 0);

		const Algorithm &algo = Tessellate_WGS84Algorithm();
		point2d origin1 = algo.interpolate(reference, outbound1, 700.0);
		point2d origin2 = algo.interpolate(reference, outbound2, 300.0);

		point2d intersect;
		TAKErr code = algo.intersect(intersect, origin1, inbound1, origin2, inbound2);
		ASSERT_TRUE(TE_Ok == code);

		const double distance = algo.distance(reference, intersect);
		// expect more than double the usual error because of 
		// outbound calculation plus inbound calculationis times two!
		ASSERT_FALSE(4e-2 < distance);
	}

	TEST_F(TessellationTests, tessellate_algorithm_wgs84_intersect) {

		// Pick a point, and two arbitrary directions
		// calculate points at a distance.
		// Then flip the directions and see if the 
		// two points and return directions result
		// in the original reference point.
		// Repeat for all four quadrants of the globe.

		static const Math::Point2<double> references[4] = {
			Math::Point2<double>(50, 30, 0),
			Math::Point2<double>(-50, 30, 0),
			Math::Point2<double>(-50, -30, 0),
			Math::Point2<double>(50, -30, 0)
		};

		for (std::size_t index = 0; 4 > index; ++index) {
			roundTripIntersection(references[index]);
		}
	}
}
