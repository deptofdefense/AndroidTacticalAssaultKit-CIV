#include "pch.h"

#include <vector>

#include "core/GeoPoint2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace {

	struct PointBearingCase
	{
		const GeoPoint2 point1;
		const double bearing1;
		const GeoPoint2 point2;
		const double bearing2;
		const GeoPoint2 expected;
	};
}

namespace takenginetests {

	TEST(GeoPoint2Tests, testLineOfBearingIntersection) {
		TAKErr code(TE_Ok);

		typedef std::vector<PointBearingCase> PointBearingCases;
		// case for each hemisphere quadrant
		const PointBearingCases cases = {
			{ GeoPoint2(31.8853, 0.2545), 108.55,
			  GeoPoint2(29.0034, 2.5735), 32.44,
			  GeoPoint2((30. + (47. / 60) + (02. / 3600)), (03. + (53. / 60) + (43. / 3600))) },
			{ GeoPoint2(-31.8853, 0.2545), 32.44,
			  GeoPoint2(-29.0034, 2.5735), 330.22,
			  GeoPoint2(-(28. + (49. / 60) + (38. / 3600)), (02. + (27. / 60) + (30. / 3600))) },
			{ GeoPoint2(31.8853, -0.2545), 183.44,
			  GeoPoint2(29.0034, -2.5735), 173.66,
			  GeoPoint2((16. + (56. / 60) + (10. / 3600)), -(1. + (10. / 60) + (59. / 3600))) },
			{ GeoPoint2(-31.8853, -0.2545), 32.44,
			  GeoPoint2(-29.0034, -2.5735), 108.55,
			  GeoPoint2(-(30. + (1. / 60) + (21. / 3600)), (1. + (6. / 60) + (27. / 3600))) }
		};

		// from https://www.tceq.texas.gov/gis/geocoord.html, DMS errors at 31 degrees North Latitude
		// for integer seconds ouputs are 30.7984 meters in latitude and 26.5294 meters in longitude.

		// from https://www.movable-type.co.uk/scripts/latlong-vectors.html#intersection
		// on-line calcuator only returns DMS, so our expectation for accuracy is nominally
		static const double error = pow(30.7984 * 30.7984 + 26.5294 * 26.5294, 0.5);

		for (PointBearingCases::const_iterator itr = cases.begin(); cases.end() != itr; ++itr) {
			GeoPoint2 intersection;
			code = GeoPoint2_lobIntersection(intersection, itr->point1, itr->bearing1, itr->point2, itr->bearing2);
			ASSERT_TRUE(TE_Ok == code);
			const double distance = GeoPoint2_distance(intersection, itr->expected, true);
			ASSERT_TRUE(error > distance);
		}
	}

	TEST(GeoPoint2Tests, testSlantAngleCalc) {
		GeoPoint2 ptA(35.7244055, -78.8885789, 576.11, TAK::Engine::Core::AltitudeReference::HAE);
		GeoPoint2 ptB(35.7303185, -78.8979835, 75.829, TAK::Engine::Core::AltitudeReference::HAE);

		double slantQuick = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, true);
		double slantSlow = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, false);
		double delta = slantQuick - slantSlow;
		ASSERT_TRUE(delta < 1.0);
	}

	TEST(GeoPoint2Tests, testSlantAngleDirNeg) {
		GeoPoint2 ptA(35.7244055, -78.8885789, 576.11, TAK::Engine::Core::AltitudeReference::HAE);
		GeoPoint2 ptB(35.7303185, -78.8979835, 75.829, TAK::Engine::Core::AltitudeReference::HAE);

		double slantQuick = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, true);
		double slantSlow = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, false);
		ASSERT_TRUE(slantQuick < 0);
		ASSERT_TRUE(slantSlow < 0);
	}

	TEST(GeoPoint2Tests, testSlantAngleDirPos) {
		GeoPoint2 ptB(35.7244055, -78.8885789, 576.11, TAK::Engine::Core::AltitudeReference::HAE);
		GeoPoint2 ptA(35.7303185, -78.8979835, 75.829, TAK::Engine::Core::AltitudeReference::HAE);

		double slantQuick = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, true);
		double slantSlow = TAK::Engine::Core::GeoPoint2_slantAngle(ptA, ptB, false);
		ASSERT_TRUE(slantQuick > 0);
		ASSERT_TRUE(slantSlow > 0);
	}
}
