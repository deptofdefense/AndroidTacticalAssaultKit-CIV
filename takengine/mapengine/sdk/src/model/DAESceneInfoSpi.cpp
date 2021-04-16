
#include "model/DAESceneInfoSpi.h"
#include "util/IO2.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

DAESceneInfoSpi::DAESceneInfoSpi() NOTHROWS 
{}

DAESceneInfoSpi::~DAESceneInfoSpi() NOTHROWS {

}

int DAESceneInfoSpi::getPriority() const NOTHROWS {
    return 1;
}

const char *DAESceneInfoSpi::getStaticName() NOTHROWS {
    return "DAE";
}

const char *DAESceneInfoSpi::getName() const NOTHROWS {
    return getStaticName();
}

bool DAESceneInfoSpi::isSupported(const char *path) NOTHROWS {
    // just file extension for now
    const char *ext = strrchr(path, '.');
    if (!ext)
        return false;

    int comp = -1;
    Port::String_compareIgnoreCase(&comp, ext, ".dae");
    if (comp != 0)
        return false;

    bool exists = false;
    IO_existsV(&exists, path);

    //TODO-- basic quick and dirty validation of dae contents

    return exists;
}

TAK::Engine::Util::TAKErr DAESceneInfoSpi::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {

    // sanity check
    if (!isSupported(path))
        return TE_Unsupported;

    SceneInfoPtr model(new (std::nothrow) SceneInfo());
    if (!model)
        return TE_OutOfMemory;

    model->uri = path;
    return scenes.add(model);
}