#include "pch.h"

#include "model/KMZSceneInfoSpi.h"
#include "model/DAESceneInfoSpi.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace takenginetests {

	TEST(KMZSceneInfoSpiTests, testMackyBldg) {
		std::string resource = TAK::Engine::Tests::getResource("MackyBldg.kmz");

		std::shared_ptr<DAESceneInfoSpi> daeSupport = std::make_shared< DAESceneInfoSpi>();
		TAKErr code = SceneInfoFactory_registerSpi(daeSupport);
		ASSERT_EQ((int)code, (int)TE_Ok);

		KMZSceneInfoSpi spi;
		TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> scenes;
		code = spi.create(scenes, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);

	}

	TEST(KMZSceneInfoSpiTests, testOakGrove) {
		std::string resource = TAK::Engine::Tests::getResource("Oak_Grove.kmz");

		std::shared_ptr<DAESceneInfoSpi> daeSupport = std::make_shared< DAESceneInfoSpi>();
		TAKErr code = SceneInfoFactory_registerSpi(daeSupport);
		ASSERT_EQ((int)code, (int)TE_Ok);

		KMZSceneInfoSpi spi;
		TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> scenes;
		code = spi.create(scenes, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);
	}
}