#include "pch.h"

#include <vector>
#include "util/DataInput2.h"
#include "model/Cesium3DTilesSceneInfoSpi.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;

namespace takenginetests {

	class Cesium3DTilesSceneInfoTests : public ::testing::Test
	{
	protected:
		void assertSampleScene(std::vector<SceneInfoPtr>& scenes, const char *base_path) {
			Cesium3DTilesSceneInfoSpi spi;
			ASSERT_TRUE(scenes.size() == 1);
			SceneInfo* info = scenes[0].get();
			ASSERT_NE(nullptr, info);
			ASSERT_STREQ(info->name.get(), "Sample Tileset");
			ASSERT_STREQ(info->type.get(), spi.getName());
			ASSERT_STREQ(info->uri.get(), base_path);
		}
	};

    TEST_F(Cesium3DTilesSceneInfoTests, testInvalidPath) {
        Cesium3DTilesSceneInfoSpi spi;
        std::vector<SceneInfoPtr> scenes;
        STLVectorAdapter<SceneInfoPtr> adapter(scenes);
        TAKErr code = spi.create(adapter, "?doesn't exist?");
        ASSERT_TRUE(code == TE_Unsupported);
    }

    TEST_F(Cesium3DTilesSceneInfoTests, testSampleDatasetPathURI) {
        std::string resource = TAK::Engine::Tests::getResource("CesiumSample");
        Cesium3DTilesSceneInfoSpi spi;
        std::vector<SceneInfoPtr> scenes;
        STLVectorAdapter<SceneInfoPtr> adapter(scenes);
        TAKErr code = spi.create(adapter, resource.c_str());
        ASSERT_TRUE(code == TE_Ok);
        assertSampleScene(scenes, resource.c_str());
    }

    TEST_F(Cesium3DTilesSceneInfoTests, testSampleDatasetZipURI) {
        std::string resource = TAK::Engine::Tests::getResource("CesiumSample.zip");
        Cesium3DTilesSceneInfoSpi spi;
        std::vector<SceneInfoPtr> scenes;
        STLVectorAdapter<SceneInfoPtr> adapter(scenes);
        TAKErr code = spi.create(adapter, resource.c_str());
        ASSERT_TRUE(code == TE_Ok);
        assertSampleScene(scenes, resource.c_str());
    }

    TEST_F(Cesium3DTilesSceneInfoTests, testSampleDatasetTilesetJSONURI) {
        std::string basePath = TAK::Engine::Tests::getResource("CesiumSample");
        std::string resource = basePath + Platform_pathSep() + "tileset.json";
        Cesium3DTilesSceneInfoSpi spi;
        std::vector<SceneInfoPtr> scenes;
        STLVectorAdapter<SceneInfoPtr> adapter(scenes);
        TAKErr code = spi.create(adapter, resource.c_str());
        ASSERT_TRUE(code == TE_Ok);
        assertSampleScene(scenes, basePath.c_str());
    }
}