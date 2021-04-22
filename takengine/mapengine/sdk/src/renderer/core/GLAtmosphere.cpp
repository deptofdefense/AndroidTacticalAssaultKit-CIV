#include <sstream>
#include <vector>

#include "renderer/core/GLAtmosphere.h"
#include "core/ProjectionFactory3.h"

#include "core/GeoPoint2.h"
#include "math/Point2.h"
#include "math/Matrix2.h"
#include "renderer/GL.h"
#include "renderer/Shader.h"
#include "renderer/RenderAttributes.h"
#include "renderer/GLES20FixedPipeline.h"

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;

const char *FRAG_SHADER =
#include "GLAtmosphere.frag"
;

const char * VERT_SHADER = R"(
attribute vec4 aVertexCoords;
attribute vec3 aEyeRay;
uniform highp vec3 campos;
uniform vec3 uSunPos;
varying highp vec3 eyeRay;
void main() {
    eyeRay = aEyeRay;
    gl_Position = aVertexCoords;
}
)";

void GLAtmosphere::init() NOTHROWS
{
    const char* vertShaderSource = VERT_SHADER;
    const char* fragShaderSource = FRAG_SHADER;

    program = std::make_shared<atakmap::renderer::Program>();

    program->create(vertShaderSource, fragShaderSource);

    if(program->program == 0)
    {
        program.reset();
        return;
    }
    uCampos = glGetUniformLocation(program->program, "campos");
    uSunpos = glGetUniformLocation(program->program, "uSunPos");
    aVertexCoordsHandle = glGetAttribLocation(program->program, "aVertexCoords");
    aEyeRayHandle = glGetAttribLocation(program->program, "aEyeRay");
}

Matrix2 ComputeInverse(const TAK::Engine::Core::MapSceneModel2& mapSceneModel2) NOTHROWS
{
    Matrix2 mvp;
    float matrixF[16u];
    atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, matrixF);
    for(std::size_t i = 0u; i < 16u; i++)
        mvp.set(i%4, i/4, matrixF[i]);

    mvp.concatenate(mapSceneModel2.forwardTransform);

    Matrix2 imvp;
    mvp.createInverse(&imvp);

    return imvp;
}

void GLAtmosphere::DrawAtmosphere(const GLGlobeBase& view) const NOTHROWS
{
    const MapSceneModel2& scene = view.renderPasses[0].scene;
    bool isFlat = scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE;

    MapSceneModel2 globe = scene;
    if(isFlat)
    {
        GeoPoint2 focusGeo;
        scene.projection->inverse(&focusGeo, scene.camera.target);

        globe.set(scene.displayDpi,
                scene.width,
                scene.height,
                4978,
                focusGeo,
                scene.focusX, scene.focusY,
                scene.camera.azimuth,
                90.0 + scene.camera.elevation,
                scene.gsd,
                scene.camera.mode);
        GLGlobeBase_glScene(globe);
    }

    //get camera position in 3d mode
    Vector4<double> campos(0,0,0);
    if(isFlat)
    {
        // obtain the eye position in LLA
        Point2<double> eyeECEF;
        GeoPoint2 eyeLLA;
        scene.projection->inverse(&eyeLLA, scene.camera.location);

        // transform the eye position from LLA to ECEF
        // obtain ECEF projection
        Projection2Ptr ecef(nullptr, nullptr);
        ProjectionFactory3_create(ecef, 4978);

        // transform from LLA to ECEF
        ecef->forward(&eyeECEF, eyeLLA);
        campos = Vector4<double>(eyeECEF.x, eyeECEF.y, eyeECEF.z);
    }
    else
    {
        campos = Vector4<double>(scene.camera.location.x, scene.camera.location.y,
                                  scene.camera.location.z);
    }

    //atmosphere breaks down at very low angles and high elevation, just cut out in those cases
    if(isFlat)
    {
        double hae = scene.camera.location.z;
        double elev = fabs(scene.camera.elevation);
        if(hae > 100000.0)
            return;
        if(hae < 100000.0 &&  elev < 2.5)
        {
            double s = 1.0 - ((elev - 1.0) / 1.5);
            s *= s;
            double maxHAE = 20000. * s + 100000. * (1. - s);
            if(hae > maxHAE)
                return;
        }
    }

    Matrix2 inv = ComputeInverse(globe);
    glUseProgram(program->program);

    glUniform3f(uCampos, (float)campos.x, (float)campos.y, (float)campos.z);
    glUniform3f(uSunpos,  (float)campos.x, (float)campos.y, (float)campos.z);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    DrawQuad(inv, campos);
    
    glEnable(GL_DEPTH_TEST);
}


void GLAtmosphere::DrawQuad(const Matrix2& inv, const Vector4<double>& camposv) const NOTHROWS
{
    const float minx = -1;
    const float miny = -1;
    const float maxx = 1;
    const float maxy = 1;

    const float z = 1.0f;

    constexpr int numsteps = 16;
    constexpr int vertsSize = 7*6*numsteps*numsteps;

    float verts[vertsSize];

    //add extra verts for higher fidelity eye ray
    float stepsize = (maxx - minx) / numsteps;

    auto addPoint = [&inv, &camposv, &z, &verts](const Point2<double>& p, int& index)
    {
        Point2<double> fp;
        inv.transform(&fp, Point2<double>(p.x, p.y, z));
        Vector4<double> eyeDir(fp.x, fp.y, fp.z);
        eyeDir.subtract(&camposv, &eyeDir);
        eyeDir.normalize(&eyeDir);

        verts[index++] = (float)p.x;verts[index++] = (float)p.y;verts[index++] = (float)p.z;verts[index++] = 1.0f;
        verts[index++] = (float)eyeDir.x;verts[index++] = (float)eyeDir.y;verts[index++] = (float)eyeDir.z;
    };

    int index = 0;

    for(int i=0;i < numsteps;++i)
    {
        for (int j = 0; j < numsteps; ++j)
        {
            float x = (stepsize * (float)i) + minx;
            float y = (stepsize * (float)j) + miny;

            float x1 = (stepsize * ((float)i + 1.f)) + minx;
            float y1 = (stepsize * ((float)j + 1.f)) + miny;

            addPoint(Point2<double>(x, y, z), index);
            addPoint(Point2<double>(x1, y, z), index);
            addPoint(Point2<double>(x1, y1, z), index);

            addPoint(Point2<double>(x, y, z), index);
            addPoint(Point2<double>(x, y1, z), index);
            addPoint(Point2<double>(x1, y1, z), index);
        }
    }

    glEnableVertexAttribArray(aVertexCoordsHandle);
    glVertexAttribPointer(aVertexCoordsHandle, 4, GL_FLOAT, GL_FALSE, 28, (float*)&verts[0]);

    glEnableVertexAttribArray(aEyeRayHandle);
    glVertexAttribPointer(aEyeRayHandle, 3, GL_FLOAT, GL_FALSE, 28, (float*)&verts[0] + 4);

    glDrawArrays(GL_TRIANGLES, 0, vertsSize / 7);
}

TAK::Engine::Util::TAKErr TAK::Engine::Renderer::Core::GLAtmosphere::draw(const GLGlobeBase &view) NOTHROWS
{
    if (!program || !program->program)
    {
        init();
        if (!program || !program->program)
            return Util::TAKErr::TE_Err;
    }

    DrawAtmosphere(view);
    return Util::TAKErr::TE_Ok;
}