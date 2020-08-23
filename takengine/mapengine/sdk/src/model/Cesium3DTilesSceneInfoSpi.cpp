
#include <vector>
#include "model/Cesium3DTilesSceneInfoSpi.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"
#include "port/String.h"
#include "port/StringBuilder.h"
#include "formats/cesium3dtiles/C3DTTileset.h"
#include "util/DataInput2.h"
#include "util/Memory.h"
#include "math/Utils.h"
#include "core/ProjectionFactory3.h"
#include "math/Matrix2.h"
#include "feature/Envelope2.h"
#include "formats/cesium3dtiles/B3DM.h"
#include "model/MeshTransformer.h"
#include "port/Collection.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Formats::Cesium3DTiles;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;

namespace {
	bool isExt(const char *URI, const char *ext) NOTHROWS;

	bool isFileSystemTileset(
		TAK::Engine::Port::String *dirPath,
		TAK::Engine::Port::String *tilesetPath,
		bool *isB3DM,
		const char *URI) NOTHROWS;

	bool isStreamingTileset(const char *URI);
	TAKErr rootVisitor(void *opaque, const C3DTTileset *tileset, const C3DTTile *root) NOTHROWS;
}

Cesium3DTilesSceneInfoSpi::Cesium3DTilesSceneInfoSpi() NOTHROWS
{}

Cesium3DTilesSceneInfoSpi::~Cesium3DTilesSceneInfoSpi() NOTHROWS 
{}

int Cesium3DTilesSceneInfoSpi::getPriority() const NOTHROWS {
	return 1;
}

const char* Cesium3DTilesSceneInfoSpi::getStaticName() NOTHROWS {
	return "Cesium3DTiles";
}

const char* Cesium3DTilesSceneInfoSpi::getName() const NOTHROWS {
	return getStaticName();
}

bool Cesium3DTilesSceneInfoSpi::isSupported(const char* URI) NOTHROWS {
	bool result = false;
	C3DTTileset_isSupported(&result, URI);
	return result || isExt(URI, ".b3dm");
}

TAK::Engine::Util::TAKErr Cesium3DTilesSceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr>& scenes, const char* URI) NOTHROWS {

	TAK::Engine::Port::String tilesetPath;
	TAK::Engine::Port::String dirPath;
	bool isB3DM = false;

	if (!isFileSystemTileset(&dirPath, &tilesetPath, &isB3DM, URI) &&
		!isStreamingTileset(URI))
		return TE_Unsupported;

	if (isB3DM) {

		ScenePtr scenePtr(nullptr, nullptr);
		DataInput2Ptr inputPtr(nullptr, nullptr);

		// XXX-- URI_open()
		TAKErr code = IO_openFileV(inputPtr, URI);
		TE_CHECKRETURN_CODE(code);
		code = B3DM_parse(scenePtr, inputPtr.get(), dirPath);
		TE_CHECKRETURN_CODE(code);

		Matrix2 localFrame;
		GeoPoint2 locPoint;
		Envelope2 aabb;

		// should always be the case
		if (scenePtr->getRootNode().getLocalFrame())
			localFrame = *scenePtr->getRootNode().getLocalFrame();

		MeshTransformOptions aabb_src;
		aabb_src.srid = 4978;
		aabb_src.localFrame = Matrix2Ptr(new Matrix2(localFrame), Memory_deleter_const<Matrix2>);

		MeshTransformOptions aabb_dst;
		aabb_dst.srid = B3DM_getSRID();

		Envelope2 aabbSrc = scenePtr->getAABB();
		Mesh_transform(&aabb, aabbSrc, aabb_src, aabb_dst);

		Projection2Ptr proj(nullptr, nullptr);
		code = ProjectionFactory3_create(proj, 4978);
		TE_CHECKRETURN_CODE(code);

		Point2<double> centerPoint;
		localFrame.transform(&centerPoint, centerPoint);
		proj->inverse(&locPoint, centerPoint);
		
		SceneInfoPtr model(new (std::nothrow) SceneInfo());
		if (!model)
			return TE_OutOfMemory;

		model->type = getStaticName();
		model->altitudeMode = TAK::Engine::Feature::TEAM_ClampToGround;
		model->minDisplayResolution = 1.0;//std::numeric_limits<double>::max();
		model->maxDisplayResolution = 0.0;
		model->srid = B3DM_getSRID();
		model->uri = URI;
		model->aabb = Envelope2Ptr(new Envelope2(aabb), Memory_deleter_const<Envelope2>);
		model->localFrame = Matrix2Ptr(new Matrix2(), Memory_deleter_const<Matrix2>);
		model->location =
			TAK::Engine::Core::GeoPoint2Ptr(new TAK::Engine::Core::GeoPoint2(locPoint), Memory_deleter_const<TAK::Engine::Core::GeoPoint2>);

		return scenes.add(model);
	}
	
	SceneInfoPtr model(new (std::nothrow) SceneInfo());
	if (!model)
		return TE_OutOfMemory;

	model->type = getStaticName();
	model->altitudeMode = TAK::Engine::Feature::TEAM_Absolute;
	model->minDisplayResolution = std::numeric_limits<double>::max();
	model->maxDisplayResolution = 0.0;
	model->srid = 4326;

	if (tilesetPath != "") {
		model->uri = dirPath;
		IO_getName(model->name, dirPath.get());

		// offline type
		DataInput2Ptr inputPtr(nullptr, nullptr);
		TAKErr code = IO_openFileV(inputPtr, tilesetPath.get());
		TE_CHECKRETURN_CODE(code);
		code = C3DTTileset_parse(inputPtr.get(), model.get(), rootVisitor);
		if (code != TE_Ok)
			return code;

	} else {
		//TODO-- streaming type
		return TE_Unsupported;
	}

	return scenes.add(model);
}

namespace {
	bool isExt(const char* URI, const char* ext) NOTHROWS {
		const char* uriExt = strrchr(URI, '.');
		if (uriExt) {
			int cmp = -1;
			TAK::Engine::Port::String_compareIgnoreCase(&cmp, uriExt, ext);
			if (cmp == 0) {
				return true;
			}
		}
		return false;
	}

	bool testB3DM(TAK::Engine::Port::String* dirPath,
		TAK::Engine::Port::String* tilesetPath,
		TAK::Engine::Port::String &name,
		const char* URI) NOTHROWS {

		if (isExt(name, ".b3dm")) {
			TAK::Engine::Port::String dir;
			IO_getParentFile(dir, URI);
			TAK::Engine::Port::String ts;
			TAK::Engine::Port::StringBuilder sb;
			TAK::Engine::Port::StringBuilder_combine(sb, dir, TAK::Engine::Port::Platform_pathSep(), "tileset.json");
			*dirPath = dir;
			*tilesetPath = ts;
			return true;
		}

		return false;
	}

	bool isFileSystemTileset(
		TAK::Engine::Port::String *dirPath,
		TAK::Engine::Port::String *tilesetPath,
		bool* isB3DM,
		const char* URI) NOTHROWS {

		TAK::Engine::Port::String dir;
		TAK::Engine::Port::String ts;

		bool isDir = false;
		IO_isDirectoryV(&isDir, URI);
		if (isDir) {
			dir = URI;
			TAK::Engine::Port::StringBuilder sb;
			if (TAK::Engine::Port::StringBuilder_combine(sb, URI, TAK::Engine::Port::Platform_pathSep(), "tileset.json")
				!= TE_Ok) {
				return false;
			}
			bool exists = false;
			IO_existsV(&exists, sb.c_str());
			if (!exists)
				return false;
			ts = sb.c_str();
		} else {
			TAK::Engine::Port::String name;
			IO_getName(name, URI);
			int cmp = -1;
			TAK::Engine::Port::String_compareIgnoreCase(&cmp, name.get(), "tileset.json");
			if (cmp != 0) {
				*isB3DM = testB3DM(dirPath, tilesetPath, name, URI);
				return *isB3DM;
			}
			ts = URI;
			IO_getParentFile(dir, URI);
			IO_isDirectoryV(&isDir, dir.get());
			if (!isDir)
				return false;
		}

		if (dirPath)
			*dirPath = std::move(dir);
		if (tilesetPath)
			*tilesetPath = std::move(ts);
		return true;
	}

	bool isStreamingTileset(const char* URI) {
		//TODO--
		return false;
	}

	TAKErr setLocTransform(SceneInfo* sceneInfo, TAK::Engine::Math::Point2<double> &center, const C3DTTile* root) {
		TAK::Engine::Math::Matrix2 transform(
			root->transform[0], root->transform[4], root->transform[8], root->transform[12],
			root->transform[1], root->transform[5], root->transform[9], root->transform[13],
			root->transform[2], root->transform[6], root->transform[10], root->transform[14],
			root->transform[3], root->transform[7], root->transform[11], root->transform[15]);
		transform.transform(&center, center);
		Projection2Ptr ecefProj(nullptr, nullptr);
		TAKErr code = ProjectionFactory3_create(ecefProj, 4978);
		TE_CHECKRETURN_CODE(code);
		GeoPoint2 geo;
		code = ecefProj->inverse(&geo, center);
		TE_CHECKRETURN_CODE(code);
		sceneInfo->location = GeoPoint2Ptr(new GeoPoint2(geo), Memory_deleter_const<GeoPoint2>);
		return TE_Ok;
	}

	TAKErr rootVisitor(void* opaque, const C3DTTileset* tileset, const C3DTTile* root) NOTHROWS {

		auto *sceneInfo = static_cast<SceneInfo *>(opaque);

		tileset->extras.getString(&sceneInfo->name, "name");
		sceneInfo->localFrame = TAK::Engine::Math::Matrix2Ptr_const(new TAK::Engine::Math::Matrix2(), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
		switch (root->boundingVolume.type) {
			case C3DTVolume::Region:
				sceneInfo->location = GeoPoint2Ptr(new GeoPoint2(
					atakmap::math::toDegrees(root->boundingVolume.object.region.north + root->boundingVolume.object.region.south) / 2.0,
					atakmap::math::toDegrees(root->boundingVolume.object.region.east + root->boundingVolume.object.region.west) / 2.0),
					Memory_deleter_const<GeoPoint2>);
				break;
			case C3DTVolume::Box: {
				TAK::Engine::Math::Point2<double> center(
					root->boundingVolume.object.box.centerX,
					root->boundingVolume.object.box.centerY,
					root->boundingVolume.object.box.centerZ);
				TAKErr code = setLocTransform(sceneInfo, center, root);
				if (code != TE_Ok) return code;
			}
				break;
			case C3DTVolume::Sphere: {
				TAK::Engine::Math::Point2<double> center(
					root->boundingVolume.object.sphere.centerX,
					root->boundingVolume.object.sphere.centerY,
					root->boundingVolume.object.sphere.centerZ);
				TAKErr code = setLocTransform(sceneInfo, center, root);
				if (code != TE_Ok) return code;
			}
				break;
			default:
				return TE_Err;
		}

		return TE_Done;
	}
}