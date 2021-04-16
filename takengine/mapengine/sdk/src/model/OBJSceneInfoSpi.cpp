
#include "model/OBJSceneInfoSpi.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

namespace {
    bool isOBJ(const char *path) {
        const char *ext = strrchr(path, '.');
        if (ext && _strcmpi(ext, ".obj") == 0)
            return true;

        return false;
    }

    TAKErr createFromOBJFile(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {
        return TE_Unsupported;
    }

    TAKErr findObjs(const char *path, std::vector<TAK::Engine::Port::String> &objs) NOTHROWS {
        bool isFile = false;
        bool isDirectory = false;
        IO_isFileV(&isFile, path);
        IO_isDirectoryV(&isDirectory, path);
        TAKErr code(TE_Ok);

        if (isFile && isOBJ(path)) {
            objs.push_back(TAK::Engine::Port::String(path));
        }
        else if (isDirectory) {
            TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files(objs);
            code = IO_listFilesV(files, path, TAK::Engine::Util::TELFM_RecursiveFiles, isOBJ);
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
}

OBJSceneInfoSpi::~OBJSceneInfoSpi() NOTHROWS
{}

int OBJSceneInfoSpi::getPriority() const NOTHROWS {
    return 2;
}

const char *OBJSceneInfoSpi::getName() const NOTHROWS {
    return "OBJ";
}

bool OBJSceneInfoSpi::isSupported(const char *path) NOTHROWS {
    // just file extension for now
    std::vector<TAK::Engine::Port::String> objs;
    findObjs(path, objs);
    return objs.size() > 0;
}

TAKErr OBJSceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {

    TAKErr code(TE_Ok);

    std::vector<TAK::Engine::Port::String> filesVector;
    code = findObjs(path, filesVector);
    TE_CHECKRETURN_CODE(code);

    for (const TAK::Engine::Port::String &file : filesVector) {
        SceneInfoPtr scene = std::make_shared<SceneInfo>();
        scene->uri = file;
        scene->type = this->getName();

        TAK::Engine::Port::String name;
        code = IO_getName(name, path);
        TE_CHECKRETURN_CODE(code);

        scene->name = name;
        scenes.add(scene);
    }

    return filesVector.empty() ? TE_Unsupported : code;
}