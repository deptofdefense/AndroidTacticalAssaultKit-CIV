#include "renderer/RenderState.h"

#include "renderer/GL.h"

using namespace TAK::Engine::Renderer;

RenderState::RenderState() NOTHROWS
{
    // only obtainable via RenderState_getCurrent(), ok to fill with garbage
}

RenderState TAK::Engine::Renderer::RenderState_getCurrent() NOTHROWS
{
    RenderState current;

    // cull
    glGetIntegerv(GL_FRONT_FACE, &current.cull.front);
    glGetIntegerv(GL_CULL_FACE, &current.cull.face);
    current.cull.enabled = glIsEnabled(GL_CULL_FACE);

    // blend
    current.blend.enabled = glIsEnabled(GL_BLEND);
    glGetIntegerv(GL_BLEND_SRC_ALPHA, &current.blend.src);
    glGetIntegerv(GL_BLEND_DST_ALPHA, &current.blend.dst);

    // depth
    current.depth.enabled = glIsEnabled(GL_DEPTH_TEST);
    glGetBooleanv(GL_DEPTH_WRITEMASK, &current.depth.mask);
    glGetIntegerv(GL_DEPTH_FUNC, &current.depth.func);

    // shader -- no-op, empty shader

    return current;
}
void TAK::Engine::Renderer::RenderState_makeCurrent(const RenderState &state) NOTHROWS
{
    // depth
    if (state.depth.enabled)
        glEnable(GL_DEPTH_TEST);
    else
        glDisable(GL_DEPTH_TEST);
    glDepthFunc(state.depth.func);
    glDepthMask(state.depth.mask);

    // blend
    if (state.blend.enabled)
        glEnable(GL_BLEND);
    else
        glDisable(GL_BLEND);
    glBlendFunc(state.blend.src, state.blend.dst);

    // cull
    glCullFace(state.cull.face);
    glFrontFace(state.cull.front);
    if (state.cull.enabled)
        glEnable(GL_CULL_FACE);
    else
        glDisable(GL_CULL_FACE);

    // shader
    if (state.shader.get()) {
        glUseProgram(state.shader->handle);

        // XXX - enable attributes???
    }
}
