#include "pch.h"

#include "model/OBJSceneInfoSpi.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace takenginetests {

	TEST(OBJSceneIfoSpiTests, testTeapotOBJInfoLoad) {
            std::string resource = TAK::Engine::Tests::getResource("teapot.obj");
            OBJSceneInfoSpi spi;
            
            std::vector<SceneInfoPtr> sceneInfos;
            TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> adapter(sceneInfos);
            TAKErr code = spi.create(adapter, resource.c_str());

            ASSERT_EQ((int)TE_Ok, (int)code);
            ASSERT_EQ((size_t)1, (size_t)sceneInfos.size());

            ASSERT_STREQ(resource.c_str(), sceneInfos[0]->uri);
            ASSERT_STREQ("teapot.obj", sceneInfos[0]->name);
        }

        TEST(OBJSceneIfoSpiTests, testZippedTeapotOBJInfoLoad) {
            std::string resource = TAK::Engine::Tests::getResource("teapot_inside.zip");
            OBJSceneInfoSpi spi;

            std::vector<SceneInfoPtr> sceneInfos;
            TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> adapter(sceneInfos);
            TAKErr code = spi.create(adapter, resource.c_str());

            ASSERT_EQ((int)TE_Ok, (int)code);
            ASSERT_EQ((size_t)1, (size_t)sceneInfos.size());

            ASSERT_STREQ((resource + "\\teapot.obj").c_str(), sceneInfos[0]->uri);
            ASSERT_STREQ("teapot_inside.zip", sceneInfos[0]->name);
        }
}