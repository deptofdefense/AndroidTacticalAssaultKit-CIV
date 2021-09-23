#include "pch.h"

#define use_namespace
#include <PRI.h>

#include <util/Memory.h>


using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace takenginetests {

	TEST(PRITests, testIsPRI) {
		std::string resource = TAK::Engine::Tests::getResource("WDC-v1.12C-4k-11988_11988_02APR17WV021200017APR02160743-P1BS-501237378070_01_P001.NTF");

		bool result = PRI::isPRI(resource.c_str());
		ASSERT_EQ(result, true);
	}

	TEST(PRITests, testPRI_imageToGround) {
		std::string resource = TAK::Engine::Tests::getResource("WDC-v1.12C-4k-11988_11988_02APR17WV021200017APR02160743-P1BS-501237378070_01_P001.NTF");

		PRI pri(resource.c_str(), false);
		iai::ErrorCoordinate result = pri.imageToGround(iai::PixelCoordinate(512.28, 512.41));
		double delta(0.0);
		delta = 38.864404593817596 - result.getLat();
		ASSERT_LT(delta * delta, 1e-16);
		delta = -77.075446036405069 - result.getLon();
		ASSERT_LT(delta * delta, 1e-14);
		delta = 12.294744676174922 - result.getElev();
		ASSERT_LT(delta * delta, 1e-6);
		delta = 2.3940957913640251 - result.getCE();
		ASSERT_LT(delta * delta, 1e-6);
		delta = 5.0002972397727676 - result.getLE();
		ASSERT_LT(delta * delta, 1e-6);
	}
}
