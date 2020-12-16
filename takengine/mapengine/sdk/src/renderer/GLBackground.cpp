#include "renderer/GLBackground.h"

#include <algorithm>
#include <cmath>

#include "renderer/GL.h"

#include "renderer/GLES20FixedPipeline.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

TAKErr TAK::Engine::Renderer::GLBackground_draw(float x0, float y0, float x1, float y1, float r, float g, float b, float a) NOTHROWS {
    float pointer[8];

    float extentY = std::fabs(y1 - y0);
    float extentX = std::fabs(x1 - x0);
    float maxExtent = std::max(extentY, extentX);
    float expandedExtent = maxExtent * 2.3f;

    pointer[0] = (-maxExtent);
    pointer[1] = (-maxExtent);
    pointer[2] = (-maxExtent);
    pointer[3] = (expandedExtent);
    pointer[4] = (expandedExtent);
    pointer[5] = (expandedExtent);
    pointer[6] = (expandedExtent);
    pointer[7] = (-maxExtent);

    try {
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glLoadIdentity();

        const float *vp = pointer;
        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, vp);

        if (a < 1) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        GLES20FixedPipeline::getInstance()->glColor4f(r, g, b, a);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        GLES20FixedPipeline::getInstance()->glPopMatrix();
    } catch (std::out_of_range &) {
        return TE_Err;
    }

    return TE_Ok;
}
