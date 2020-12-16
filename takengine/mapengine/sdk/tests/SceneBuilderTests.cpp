#include "pch.h"

#include "model/SceneBuilder.h"
#include "model/MeshBuilder.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace takenginetests {

	void buildBasicMesh(MeshPtr &meshPtr) {
		MeshBuilder meshBuilder(TEDM_Triangles, TEVA_Position | TEVA_TexCoord0);
		float texCoords[3] = { 0, 1, 2 };
		meshBuilder.addVertex(0, 1, 2, texCoords, 0, 0, 0, 1, 1, 1, 1);

		meshBuilder.build(meshPtr);
	}

	TEST(SceneBuilderTests, testMeshBuilder) {
		MeshPtr meshPtr(nullptr, nullptr);
		buildBasicMesh(meshPtr);
		ASSERT_EQ((int)meshPtr->getDrawMode(), (int)TEDM_Triangles);
	}

	TEST(SceneBuilderTests, testSceneBuilder) {
		SceneBuilder builder(true);
		MeshPtr meshPtr(nullptr, nullptr);
		buildBasicMesh(meshPtr);
		TAKErr code = builder.addMesh(std::move(meshPtr), nullptr);
		ASSERT_EQ((int)code, (int)TE_Ok);

		ScenePtr scenePtr(nullptr, nullptr);
		code = builder.build(scenePtr);
		ASSERT_EQ((int)code, (int)TE_Ok);
	}

	TEST(SceneBuilderTests, testSceneHierarchyBuild) {
		SceneBuilder builder(true);
		TAK::Engine::Math::Matrix2 mat;
		mat.setToIdentity();
		TAKErr code = builder.push(&mat);
		MeshPtr meshPtr(nullptr, nullptr);
		buildBasicMesh(meshPtr);
		code = builder.addMesh(std::move(meshPtr), nullptr);
		ASSERT_EQ((int)code, (int)TE_Ok);

		ScenePtr scenePtr(nullptr, nullptr);
		code = builder.build(scenePtr);
		ASSERT_EQ((int)code, (int)TE_Ok);
	}
}