#include "pch.h"

#include "util/DataInput2.h"
#include "formats/cesium3dtiles/C3DTTileset.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::Cesium3DTiles;

namespace takenginetests {

	class C3DTTilesetTests : public ::testing::Test
	{
	protected:

		struct VisitorInfo {
			size_t visitCount = 0;
		};

		static void visitorSimpleCommon(VisitorInfo* visitInfo, const C3DTTileset* tileset, const C3DTTile* tile) {
			visitInfo->visitCount++;
			ASSERT_NE(nullptr, tileset);
			ASSERT_STREQ(tileset->asset.version, "version_value");
			ASSERT_STREQ(tileset->asset.tilesetVersion, "tileset_value");
			ASSERT_EQ(tileset->geometricError, 1.234);
			TAK::Engine::Port::String extrasName = "";
			tileset->extras.getString(&extrasName, "name");
			ASSERT_STREQ(extrasName.get(), "name_value");
			ASSERT_NE(nullptr, tile);
			ASSERT_TRUE(tile->refine == C3DTRefine::Add);
			ASSERT_EQ(tile->geometricError, 12345.0);
		}

		static TAKErr visitorSimpleRegion(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
			VisitorInfo *visitInfo = static_cast<VisitorInfo *>(opaque);
			visitorSimpleCommon(visitInfo, tileset, tile);
			EXPECT_TRUE(tile->boundingVolume.type == C3DTVolume::Region);
			EXPECT_EQ(tile->boundingVolume.object.region.west, 0.0);
			EXPECT_EQ(tile->boundingVolume.object.region.south, 1.0);
			EXPECT_EQ(tile->boundingVolume.object.region.east, 2.0);
			EXPECT_EQ(tile->boundingVolume.object.region.north, 3.0);
			EXPECT_EQ(tile->boundingVolume.object.region.minimumHeight, 4.0);
			EXPECT_EQ(tile->boundingVolume.object.region.maximumHeight, 5.0);
			return TE_Ok;
		}

		static TAKErr visitorSimpleBox(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
			VisitorInfo* visitInfo = static_cast<VisitorInfo*>(opaque);
			visitorSimpleCommon(visitInfo, tileset, tile);
			EXPECT_TRUE(tile->boundingVolume.type == C3DTVolume::Box);
			EXPECT_EQ(tile->boundingVolume.object.box.center.x, 0.0);
			EXPECT_EQ(tile->boundingVolume.object.box.center.y, 1.0);
			EXPECT_EQ(tile->boundingVolume.object.box.center.z, 2.0);
			EXPECT_EQ(tile->boundingVolume.object.box.xDirHalfLen.x, 3.0);
			EXPECT_EQ(tile->boundingVolume.object.box.xDirHalfLen.y, 4.0);
			EXPECT_EQ(tile->boundingVolume.object.box.xDirHalfLen.z, 5.0);
			EXPECT_EQ(tile->boundingVolume.object.box.yDirHalfLen.x, 6.0);
			EXPECT_EQ(tile->boundingVolume.object.box.yDirHalfLen.y, 7.0);
			EXPECT_EQ(tile->boundingVolume.object.box.yDirHalfLen.z, 8.0);
			EXPECT_EQ(tile->boundingVolume.object.box.zDirHalfLen.x, 9.0);
			EXPECT_EQ(tile->boundingVolume.object.box.zDirHalfLen.y, 10.0);
			EXPECT_EQ(tile->boundingVolume.object.box.zDirHalfLen.z, 11.0);
			return TE_Ok;
		}

		static TAKErr visitorSimpleSphere(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
			VisitorInfo* visitInfo = static_cast<VisitorInfo*>(opaque);
			visitorSimpleCommon(visitInfo, tileset, tile);
			EXPECT_TRUE(tile->boundingVolume.type == C3DTVolume::Sphere);
			EXPECT_EQ(tile->boundingVolume.object.sphere.center.x, 0.0);
			EXPECT_EQ(tile->boundingVolume.object.sphere.center.y, 1.0);
			EXPECT_EQ(tile->boundingVolume.object.sphere.center.z, 2.0);
			EXPECT_EQ(tile->boundingVolume.object.sphere.radius, 3.0);
			return TE_Ok;
		}

		static TAKErr visitorSimpleUndefined(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
			VisitorInfo* visitInfo = static_cast<VisitorInfo*>(opaque);
			visitorSimpleCommon(visitInfo, tileset, tile);
			EXPECT_TRUE(tile->boundingVolume.type == C3DTVolume::Undefined);
			return TE_Ok;
		}

		static TAKErr visitorNever(void* opaque, const C3DTTileset* tileset, const C3DTTile* tile) {
			VisitorInfo* visitInfo = static_cast<VisitorInfo*>(opaque);
			visitorSimpleCommon(visitInfo, tileset, tile);
			return TE_Ok;
		}
	};

	TEST_F(C3DTTilesetTests, testSimpleRegionRoot) {
		std::string src = "{"
			"\"asset\":{\"version\":\"version_value\", \"tileset\":\"tileset_value\"},"
			"\"geometricError\":1.234,"
			"\"extras\":{\"name\":\"name_value\"},"
			"\"root\":{"
			"\"boundingVolume\": {"
			"\"region\": ["
			"0,"
			"1,"
			"2,"
			"3,"
			"4,"
			"5"
			"]"
			"},"
			"\"geometricError\": 12345,"
			"\"refine\" : \"ADD\""
			"}"
			"}";
		MemoryInput2 input;
		input.open((uint8_t*)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorSimpleRegion);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(visitInfo.visitCount == 1);
	}

	TEST_F(C3DTTilesetTests, testSimpleBoxRoot) {
		std::string src = "{"
			"\"asset\":{\"version\":\"version_value\", \"tileset\":\"tileset_value\"},"
			"\"geometricError\":1.234,"
			"\"extras\":{\"name\":\"name_value\"},"
			"\"root\":{"
			"\"boundingVolume\": {"
			"\"box\": [0,1,2,3,4,5,6,7,8,9,10,11]"
			"},"
			"\"geometricError\": 12345,"
			"\"refine\" : \"ADD\""
			"}"
			"}";
		MemoryInput2 input;
		input.open((uint8_t*)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorSimpleBox);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(visitInfo.visitCount == 1);
	}

	TEST_F(C3DTTilesetTests, testSimpleSphereRoot) {
		std::string src = "{"
			"\"asset\":{\"version\":\"version_value\", \"tileset\":\"tileset_value\"},"
			"\"geometricError\":1.234,"
			"\"extras\":{\"name\":\"name_value\"},"
			"\"root\":{"
			"\"boundingVolume\": {"
			"\"sphere\": [0,1,2,3]"
			"},"
			"\"geometricError\": 12345,"
			"\"refine\" : \"ADD\""
			"}"
			"}";
		MemoryInput2 input;
		input.open((uint8_t*)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorSimpleSphere);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(visitInfo.visitCount == 1);
	}

	TEST_F(C3DTTilesetTests, testSimpleUndefinedRoot) {
		std::string src = "{"
			"\"asset\":{\"version\":\"version_value\", \"tileset\":\"tileset_value\"},"
			"\"geometricError\":1.234,"
			"\"extras\":{\"name\":\"name_value\"},"
			"\"root\":{"
			"\"geometricError\": 12345,"
			"\"refine\" : \"ADD\""
			"}"
			"}";
		MemoryInput2 input;
		input.open((uint8_t*)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorSimpleUndefined);
		ASSERT_TRUE(code == TE_Ok);
		ASSERT_TRUE(visitInfo.visitCount == 1);
	}
	TEST_F(C3DTTilesetTests, testNoRoot) {
		std::string src = "{"
			"\"asset\":{\"version\":\"version_value\", \"tileset\":\"tileset_value\"},"
			"\"geometricError\":1.234,"
			"\"extras\":{\"name\":\"name_value\"}"
			"}";
		MemoryInput2 input;
		input.open((uint8_t *)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorNever);
		ASSERT_TRUE(code == TE_Err);
		ASSERT_TRUE(visitInfo.visitCount == 0);
	}

	TEST_F(C3DTTilesetTests, testGarbageIn) {
		std::string src = "junk";
		MemoryInput2 input;
		input.open((uint8_t*)src.c_str(), src.size());

		VisitorInfo visitInfo;
		TAKErr code = C3DTTileset_parse(&input, &visitInfo, visitorNever);
		ASSERT_TRUE(code == TE_Err);
		ASSERT_TRUE(visitInfo.visitCount == 0);
	}
}