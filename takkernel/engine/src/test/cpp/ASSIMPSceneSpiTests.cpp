#include "pch.h"

#include "model/KMZSceneInfoSpi.h"
#include "model/DAESceneInfoSpi.h"
#include "model/ASSIMPSceneSpi.h"
#include "port/STLVectorAdapter.h"
#include "util/IO2.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace takenginetests{

TEST(ASSIMPSceneSpiTests, testTeapotOBJLoad) {
	std::string resource = TAK::Engine::Tests::getResource("teapot.obj");

	ASSIMPSceneSpi spi;
	ScenePtr scenePtr(nullptr, nullptr);
	TAKErr code = spi.create(scenePtr, resource.c_str(), nullptr, nullptr);
	ASSERT_EQ((int)code, (int)TE_Ok);

	//ASSERT_EQ((size_t)1, scenePtr->get());

	// expect only 1 root node for simple OBJ
	const SceneNode& root = scenePtr->getRootNode();
	ASSERT_TRUE(root.hasChildren());

	unsigned int meshIndex = 0;
	//code = root.getMeshIndex(&meshIndex);
	//ASSERT_EQ((int)TE_IllegalState, (int)code);

	TAK::Engine::Port::Collection<std::shared_ptr<SceneNode>>::IteratorPtr iterPtr(nullptr, nullptr);
	code = root.getChildren(iterPtr);
	ASSERT_EQ((int)TE_Ok, (int)code);

	std::shared_ptr<SceneNode> itemPtr;
	code = iterPtr->get(itemPtr);
	ASSERT_EQ((int)TE_Ok, (int)code);

	ASSERT_NE(nullptr, itemPtr.get());
	ASSERT_FALSE(itemPtr->hasChildren());

	std::shared_ptr<const Mesh> meshPtr;
	//code = scenePtr->loadMesh(meshPtr, meshIndex);
	ASSERT_EQ((int)TE_Ok, (int)code);
	//ASSERT_NE(nullptr, meshPtr.get());
}

TEST(ASSIMPSceneSpiTests, testOakGroveChickenCoop) {
	std::string kmzResource = TAK::Engine::Tests::getResource("Oak_Grove.kmz");
	std::string resource = TAK::Engine::Tests::getResource("Oak_Grove.kmz\\files\\chickencoop.dae");

	ASSIMPSceneSpi spi;
	KMZSceneInfoSpi infoSpi;

	std::shared_ptr<DAESceneInfoSpi> daeSpi(new DAESceneInfoSpi());

	SceneInfoFactory_registerSpi(daeSpi);

	TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> sceneInfos;
	infoSpi.create(sceneInfos, kmzResource.c_str());

	SceneInfoPtr sceneInfo;
	sceneInfos.get(sceneInfo, 0);

	ScenePtr scenePtr(nullptr, nullptr);
	TAKErr code = spi.create(scenePtr, resource.c_str(), nullptr, sceneInfo->resourceAliases.get());
	ASSERT_EQ((int)code, (int)TE_Ok);
}
}