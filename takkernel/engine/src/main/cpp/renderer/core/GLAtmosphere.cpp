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

#include <algorithm>
#include <iostream>
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;

#define NUMSTEPS 16u
#define NUMVERTS (7u*6u*NUMSTEPS*NUMSTEPS)

const char *FRAG_SHADER =
#include "GLAtmosphere.frag"
;

const char * VERT_SHADER = R"(

#version 100
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

constexpr double planetRadius =  6356752.3142;
constexpr double planetEllipsoid = 6378137.0;
constexpr double atmosphereHeight = 100e3;


Vector4<double> rsi(Vector4<double> r0, Vector4<double> ray_dir, double sr) {
    // ray-sphere intersection that assumes
    // the sphere is centered at the origin.
    // No intersection when result.x > result.y
    ray_dir.normalize(&ray_dir);
    double a = ray_dir.dot(&ray_dir);
    double b = 2.0 * ray_dir.dot(&r0);
    double c = r0.dot(&r0) - (sr * sr);
    double d = (b*b) - 4.0*a*c;
    if (d < 0.0) return Vector4<double>(1e15,-1e15, 0);
    return Vector4<double>(
    (-b - sqrt(d))/(2.0*a),
    (-b + sqrt(d))/(2.0*a), 0);
}

Vector4<double> atmosphere_intersect(Vector4<double> ray_dir, Vector4<double> r0, double  rPlanet, double rAtmos)
{
    Vector4<double> no_intersect = Vector4<double>(1e15, -1e15,0);
    bool outside = (r0.length() - rAtmos) > 0.0;
    // Calculate the step size of the primary ray.
    Vector4<double> p = rsi(r0, ray_dir, rAtmos);
    if (p.x > p.y) return no_intersect;
    if(p.y < 0.0) return no_intersect;
    Vector4<double> planet_p = rsi(r0, ray_dir, rPlanet);
    if(planet_p.x < 0.0)
        planet_p.x = 1e15;

    if(planet_p.y > planet_p.x)
        return no_intersect;

    p.y = std::min(p.y, planet_p.x);
    if(!outside)
        p.x = 0;

    return p;
}

Vector4<double> atmosphere(Vector4<double> ray_dir, Vector4<double> r0, Vector4<double> pSun, double iSun, Vector4<double> atmosphere_intersection,
        double rPlanet, double rAtmos)
{
    const Vector4<double> kRlh(5.5e-6, 13.0e-6, 22.4e-6);	// Rayleigh scattering coefficient
    constexpr double kMie = 21e-6;		// Mie scattering coefficient
    constexpr double shRlh = 8e3;		// Rayleigh scale height
    constexpr double shMie = 1.2e3;		// Mie scale height
    constexpr double MieG  =0.758;			// Mie preferred scattering direction
    constexpr std::size_t iSteps = 16;

    pSun.normalize(&pSun);
    ray_dir.normalize(&ray_dir);

    // Normalize the sun and view directions.

    Vector4<double> p = atmosphere_intersection;

    double iStepSize = (p.y - p.x) / float(iSteps);

    double iTime = 0.0;

    // Initialize accumulators for Rayleigh and Mie scattering.
    Vector4<double> totalRlh(0,0,0);
    Vector4<double> totalMie(0,0,0);

    // Initialize optical depth accumulators for the primary ray.
    double iOdRlh = 0.0;
    double iOdMie = 0.0;

    // Calculate the Rayleigh and Mie phases.
    double mu = ray_dir.dot(&pSun);
    double mumu = mu * mu;
    double gg = MieG * MieG;
    double pRlh = 3.0 / (16.0 * M_PI) * (1.0 + mumu);
    double pMie = 3.0 / (8.0 * M_PI) * ((1.0 - gg) * (mumu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * MieG, 1.5) * (2.0 + gg));

    // Sample the primary ray.
    for (int i = 0; i < iSteps; i++) {

        // Calculate the primary ray sample position.
        Vector4<double> iPos(0,0,0);
        ray_dir.multiply(( p.x + iTime + iStepSize * 0.5), &iPos);
        iPos.add(&r0, &iPos);

        // Calculate the height of the sample.
        double iHeight = iPos.length() - rPlanet;
        iHeight = std::max(5000.0, iHeight);

        // Calculate the optical depth of the Rayleigh and Mie scattering for this step.
        double odStepRlh = exp(-iHeight / shRlh) * iStepSize;
        double odStepMie = exp(-iHeight / shMie) * iStepSize;

        // Accumulate optical depth.
        iOdRlh += odStepRlh;
        iOdMie += odStepMie;

        // Calculate attenuation.
        Vector4<double> attn(0,0,0);// = exp(-(kMie * (iOdMie) + kRlh * (iOdRlh)));
        attn.x = exp(-(kMie * (iOdMie) + kRlh.x * (iOdRlh)));
        attn.y = exp(-(kMie * (iOdMie) + kRlh.y * (iOdRlh)));
        attn.z = exp(-(kMie * (iOdMie) + kRlh.z * (iOdRlh)));

        Vector4<double> rlhInc(attn), mieInc(attn);
        rlhInc.multiply(odStepRlh, &rlhInc);
        mieInc.multiply(odStepMie, &mieInc);

        // Accumulate scattering.
        totalRlh.add(&rlhInc, &totalRlh);
        totalMie.add(&mieInc, &totalMie);

        // Increment the primary ray time.
        iTime += iStepSize;
    }

    totalRlh.multiply(pRlh, &totalRlh);
    totalRlh = Vector4<double>(totalRlh.x * kRlh.x, totalRlh.y * kRlh.y, totalRlh.z * kRlh.z);
    totalMie.multiply(pMie * kMie, &totalMie);

    Vector4<double> ret = totalRlh;
    ret.add(&totalMie, &ret);
    ret.multiply(iSun, &ret);
    return ret;
    // Calculate and return the final color.
}

unsigned int RGBA(double r, double g, double b, double a = 1.0)
{
    r = std::min(std::max(r, 0.), 1.) * 255.;
    g = std::min(std::max(g, 0.), 1.) * 255.;
    b = std::min(std::max(b, 0.), 1.) * 255.;
    a = std::min(std::max(a, 0.), 1.) * 255.;

    unsigned int ret = 0;
    ret |= ((unsigned char)r) << 0;
    ret |= ((unsigned char)g) << 8;
    ret |= ((unsigned char)b) << 16;
    ret |= ((unsigned char)a) << 24;
    return ret;
}

void GLAtmosphere::ComputeInsideTexture()
{
    constexpr std::size_t insideWidth = 64;
    constexpr std::size_t insideHeight = 64;

    unsigned int texture[insideHeight * insideWidth];
    for(std::size_t x = 0;x < insideWidth;++x)
    {
        double height = atmosphereHeight * ((double)x / (double)(insideWidth - 1)) ;
        Vector4<double> location(0,0,planetRadius+height);
        for(std::size_t y = 0;y < insideHeight;++y)
        {
            double angle = (M_PI / double(insideHeight - 1)) * double(insideHeight - y);
            Vector4<double> ray(0, -sin(angle),-cos(angle));

            Vector4<double> intersection = atmosphere_intersect(ray, location, planetRadius, planetRadius + atmosphereHeight);
            Vector4<double> col = atmosphere(ray, location, Vector4<double>(0,0,-1), 33, intersection, planetEllipsoid, planetEllipsoid + atmosphereHeight);
            col = Vector4<double>(1.0 - exp(-1.0 * col.x), 1.0 - exp(-1.0 * col.y), 1.0 - exp(-1.0 * col.z));
            texture[y * insideWidth + x] = RGBA(col.x, col.y, col.z);
        }
    }
    glGenTextures(1, &insideTextureHandle);
    glBindTexture(GL_TEXTURE_2D, insideTextureHandle);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, insideWidth, insideHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, texture);
    glBindTexture(GL_TEXTURE_2D, 0);
}

double findHeightForLength(double length)
{
    const std::size_t numSteps = 20;
    double upper = M_PI/2.;
    double lower = 0;
    double mid = (upper + lower) / 2.;
    for(std::size_t i = 0;i < numSteps;++i)
    {
        mid = (upper + lower) / 2.;
        double l = cos(mid) * 2. * (planetRadius + atmosphereHeight);
        if(l < length)
            upper = mid;
        else
            lower = mid;
    }
    return sin(mid) * (planetRadius + atmosphereHeight);
}

void GLAtmosphere::ComputeOutsideTexture()
{
    constexpr std::size_t outsideWidth = 1;
    constexpr std::size_t outsideHeight = 64;

    unsigned int texture[outsideWidth * outsideHeight];
    for(std::size_t x = 0;x < outsideWidth;++x)
    {
        for(std::size_t y = 0;y < outsideHeight;++y)
        {
            constexpr double maxAtmosphereLength = atmosphereHeight * 25.;

            double length = maxAtmosphereLength * (double)( y) / (double)outsideHeight;
            Vector4<double> ray(0, -1, 0);
            double height = findHeightForLength(length);

            height = std::max(planetRadius + 2000., height);
            Vector4<double> location = Vector4<double>(0, (planetRadius + atmosphereHeight) * 2.0, height);

            Vector4<double> intersection = atmosphere_intersect(ray, location, planetRadius, planetRadius + atmosphereHeight);
            Vector4<double> col = atmosphere(ray, location, Vector4<double>(0,0,-1), 33, intersection, planetEllipsoid, planetEllipsoid + atmosphereHeight);
            col = Vector4<double>(1.0 - exp(-1.0 * col.x), 1.0 - exp(-1.0 * col.y), 1.0 - exp(-1.0 * col.z));

            texture[y * outsideWidth + x] = RGBA(col.x, col.y, col.z);
        }
    }
    glGenTextures(1, &outsideTextureHandle);
    glBindTexture(GL_TEXTURE_2D, outsideTextureHandle);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, outsideWidth, outsideHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, texture);
    glBindTexture(GL_TEXTURE_2D, 0);
}


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
    uAlpha = glGetUniformLocation(program->program, "alpha");
    aVertexCoordsHandle = glGetAttribLocation(program->program, "aVertexCoords");
    aEyeRayHandle = glGetAttribLocation(program->program, "aEyeRay");

    ComputeOutsideTexture();

    ComputeInsideTexture();

#ifdef __APPLE__
    if(!vbo) {
        glGenBuffers(1u, &vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, NUMVERTS*sizeof(float), nullptr, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    }
#endif
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
        double elev = std::max(fabs(scene.camera.elevation), 1.0);
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

    auto alpha = (float)1;
    Controls::IlluminationControlImpl *illuminationControl = view.getIlluminationControl();
    if (illuminationControl->getEnabled())
    {
        alpha = (float)illuminationControl->getBrightness();
    }

    Matrix2 inv = ComputeInverse(globe);
    glUseProgram(program->program);

    glUniform3f(uCampos, (float)campos.x, (float)campos.y, (float)campos.z);
    glUniform3f(uSunpos,  (float)campos.x, (float)campos.y, (float)campos.z);
    glUniform1f(uAlpha, alpha);

    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);

    double height = campos.length();
    if(height < planetRadius + atmosphereHeight)
        glBindTexture(GL_TEXTURE_2D, insideTextureHandle);
    else
        glBindTexture(GL_TEXTURE_2D, outsideTextureHandle);

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

    float verts[NUMVERTS];

    //add extra verts for higher fidelity eye ray
    float stepsize = (maxx - minx) / NUMSTEPS;

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

    for(int i=0;i < NUMSTEPS;++i)
    {
        for (int j = 0; j < NUMSTEPS; ++j)
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

    const uint8_t *vertptr;
#ifdef __APPLE__
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    // orphan
    glBufferData(GL_ARRAY_BUFFER, NUMVERTS*sizeof(float), nullptr, GL_STREAM_DRAW);
    glBufferSubData(GL_ARRAY_BUFFER, 0, NUMVERTS*sizeof(float), verts);
    vertptr = (const uint8_t *)0;
#else
    vertptr = reinterpret_cast<const uint8_t *>(&verts[0]);
#endif

    glEnableVertexAttribArray(aVertexCoordsHandle);
    glVertexAttribPointer(aVertexCoordsHandle, 4, GL_FLOAT, GL_FALSE, 28, vertptr);
    glEnableVertexAttribArray(aEyeRayHandle);
    glVertexAttribPointer(aEyeRayHandle, 3, GL_FLOAT, GL_FALSE, 28, vertptr+16);

    glDrawArrays(GL_TRIANGLES, 0, NUMVERTS / 7);
#ifdef __APPLE__
    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
#endif
    glDisableVertexAttribArray(aVertexCoordsHandle);
    glDisableVertexAttribArray(aEyeRayHandle);
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