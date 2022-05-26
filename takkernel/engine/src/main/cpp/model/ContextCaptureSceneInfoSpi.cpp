
#include <vector>
#include "model/ContextCaptureSceneInfoSpi.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"
#include "model/ContextCaptureGeoreferencer.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

namespace {
	bool hasExt(const char *path, const char *ext);
	bool isZipFile(const char *path);
	bool isOBJFile(const char *path);
	TAKErr findObj(const char *path, std::vector<TAK::Engine::Port::String> &objs) NOTHROWS;
}

ContextCaptureSceneInfoSpi::ContextCaptureSceneInfoSpi() NOTHROWS
{}

ContextCaptureSceneInfoSpi::~ContextCaptureSceneInfoSpi() NOTHROWS {

}

int ContextCaptureSceneInfoSpi::getPriority() const NOTHROWS {
	return 1;
}

const char *ContextCaptureSceneInfoSpi::getStaticName() NOTHROWS {
	return "ContextCapture";
}

const char *ContextCaptureSceneInfoSpi::getName() const NOTHROWS {
	return getStaticName();
}

bool ContextCaptureSceneInfoSpi::isSupported(const char *path) NOTHROWS {
	
	if (!hasExt(path, ".zip"))
		return false;

	std::vector<TAK::Engine::Port::String> objs;
	findObj(path, objs);
	
	if (objs.size() == 0)
		return false;

	TAK::Engine::Port::String locPath;
	return TE_Ok == TAK::Engine::Model::ContextCaptureGeoreferencer::locateMetadataFile(locPath, objs.front().get());
}

TAK::Engine::Util::TAKErr ContextCaptureSceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {

	std::vector<TAK::Engine::Port::String> objs;
	findObj(path, objs);

	if (objs.size() == 0)
		return TE_Unsupported;

	SceneInfoPtr model(new (std::nothrow) SceneInfo());
	if (!model)
		return TE_OutOfMemory;

	model->uri = objs.front();

	TAK::Engine::Model::ContextCaptureGeoreferencer georef;
	if (TE_Ok != georef.locate(*model))
		return TE_Unsupported;

	model->type = getStaticName();
	ContextCaptureGeoreferencer::getDatasetName(model->name, model->uri.get());
	model->minDisplayResolution = 32;
    // 1.625cm
    ContextCapture_resolution(&model->resolution, 22u);

	return scenes.add(model);
}

namespace {
	bool hasExt(const char *path, const char *ext) {
		const char *lastExt = strrchr(path, '.');
		if (lastExt) {
			int cmp = -1;
			TAK::Engine::Port::String_compareIgnoreCase(&cmp, ext, lastExt);
			if (cmp == 0)
				return true;
		}
		return false;
	}

	bool isZipFile(const char *path) {
		return hasExt(path, ".zip");
	}

	bool isOBJFile(const char *path) {
		return hasExt(path, ".obj");
	}

	TAKErr findObj(const char *path, std::vector<TAK::Engine::Port::String> &objs) NOTHROWS {
		bool isDirectory = false;
		IO_isDirectoryV(&isDirectory, path);
		TAKErr code(TE_Ok);

		if (isDirectory) {
			TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files(objs);
			code = IO_listFilesV(files, path, TAK::Engine::Util::TELFM_RecursiveFiles, isOBJFile, 1);
			TE_CHECKRETURN_CODE(code);
		}

		return code;
	}
}