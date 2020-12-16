#include "renderer/model/SceneObjectControl.h"

using namespace TAK::Engine::Renderer::Model;

SceneObjectControl::~SceneObjectControl() NOTHROWS
{}

SceneObjectControl::UpdateListener::~UpdateListener() NOTHROWS
{}

const char *TAK::Engine::Renderer::Model::SceneObjectControl_getType() NOTHROWS
{
    return "TAK.Engine.Renderer.Model.SceneObjectControl";
}
