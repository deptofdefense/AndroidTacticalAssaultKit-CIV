#ifdef MSVC
#include "renderer/model/SceneObjectControl2.h"

using namespace TAK::Engine::Renderer::Model;

SceneObjectControl2::~SceneObjectControl2() NOTHROWS
{}

const char *TAK::Engine::Renderer::Model::SceneObjectControl2_getType() NOTHROWS
{
    return "TAK.Engine.Renderer.Model.SceneObjectControl2";
}
#endif