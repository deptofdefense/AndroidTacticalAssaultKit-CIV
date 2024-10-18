#ifdef MSVC
#include "LASSceneSpi.h"
#include "LASSceneInfoSpi.h"
#include "port/STLVectorAdapter.h"
#include "liblas/capi/liblas.h"
#include "math/Utils.h"
#include "model/MeshBuilder.h"
#include "db/Database2.h"
#include "db/DatabaseInformation.h"
#include "db/DatabaseFactory.h"
#include "model/SceneBuilder.h"
#include "formats/las/LAS.h"
#include "formats/cesium3dtiles/C3DTTileset.h"
#include "formats/cesium3dtiles/PNTS.h"
#include <cmath>
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"

using namespace TAK::Engine::Formats::LAS;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace {
LASPointH LASReader_GetNextPoint(LASReaderH reader, const std::size_t pointInterval) NOTHROWS {
    for (std::size_t i = 1; i < pointInterval; i++) {
        // read the point, break on EOF
        if (!::LASReader_GetNextPoint(reader)) break;
    }
    // read point at interval
    return ::LASReader_GetNextPoint(reader);
}
}  // namespace

namespace TAK {
    namespace Engine {
        namespace Model {
            extern double LASSceneInfoSpi_getZScale(LASHeaderH header);
        }
    }
}


const char *LASSceneSPI::getType() const NOTHROWS {
    return "LAS";
}

int LASSceneSPI::getPriority() const NOTHROWS {
    return 1;
}

// Calculates phi [0..1] along normal distribution
double phi(double x, double mu, double sigma) {
    // constants
    double a1 = 0.254829592;
    double a2 = -0.284496736;
    double a3 = 1.421413741;
    double a4 = -1.453152027;
    double a5 = 1.061405429;
    double p = 0.3275911;

    // Save the sign of x
    int sign = 1;
    if (x < mu) sign = -1;
    x = fabs(x - mu) / sqrt(2.0 * sigma * sigma);

    // A&S formula 7.1.26
    double t = 1.0 / (1.0 + p * x);
    double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-x * x);

    return 0.5 * (1.0 + sign * y);
}

void hsv2rgb(double h, double s, double v, float *r, float *g, float *b) {

    (h == 360.) ? (h = 0.) : (h /= 60.);
    double fract = h - floor(h);

    double P = v * (1. - s);
    double Q = v * (1. - s * fract);
    double T = v * (1. - s * (1. - fract));
#define RGB(_r, _g, _b) { *r = float(_r); *g = float(_g); *b = float(_b); }

    if (0. <= h && h < 1.)
        RGB(v, T, P)
    else if (1. <= h && h < 2.)
        RGB(Q, v, P)
    else if (2. <= h && h < 3.)
        RGB(P, v, T)
    else if (3. <= h && h < 4.)
        RGB(P, Q, v)
    else if (4. <= h && h < 5.)
        RGB(T, P, v)
    else if (5. <= h && h < 6.)
        RGB(v, P, Q)
    else
        RGB(0., 0., 0.)
#undef RGB
}

TAKErr TAK::Engine::Model::LASSceneSPI_computeZColor(float* r, float* g, float* b, double mean, double stddev, double z) {
    double val = phi(z, mean, stddev);
    hsv2rgb(val * 360.0, 1, 1, r, g, b);
    return TE_Ok;
}
TAKErr TAK::Engine::Model::LASSceneSPI_computeZColor(double z, float *r, float *g, float *b) {
    return LASSceneSPI_computeZColor(r, g, b, 10., 6., z);
}

/**
 * This is a very basic scene implementation suitable only for small datasets.
 * We artifically limit the size to 1,000,000 points.
 */
TAKErr LASSceneSPI::create(ScenePtr &scene, const char *URI, ProcessingCallback *callbacks,
                           const Collection<ResourceAlias> *resourceAliases) const NOTHROWS {
    auto readerObj = LASReader_Create(URI);
    std::unique_ptr<LASReaderH, void (*)(LASReaderH *)> reader(&readerObj, [](LASReaderH *r) { LASReader_Destroy(*r); });
    if (*reader == nullptr) {
        LASError_Print(LASError_GetLastErrorMsg());
        LASError_Pop();
        return TE_Unsupported;
    }

    auto headerObj = LASReader_GetHeader(*reader);
    std::unique_ptr<LASHeaderH, void (*)(LASHeaderH *)> header(&headerObj, [](LASHeaderH *h) { LASHeader_Destroy(*h); });
    if (*header == nullptr) {
        LASError_Print(LASError_GetLastErrorMsg());
        LASError_Pop();
        return TE_Unsupported;
    }

    double zScale = LASSceneInfoSpi_getZScale(*header);

    bool has_color = LAS_HasColor(*header);

    // XXX-- libLAS has a bug in the zip reader that is triggered by the LAS_HasIntensity(reader, header); call. This happens on select sets, but the work around
    //       here  opens a new reader which works as expected and leaves the current reader is a good state.
    bool has_intensity = LAS_HasIntensity(URI);
    
    double mean = (LASHeader_GetMinZ(*header) + LASHeader_GetMaxZ(*header)) / 2.0;
    double stddev = (LASHeader_GetMaxZ(*header) - LASHeader_GetMinZ(*header)) * 0.25;
    unsigned int pointRecordsCount = LASHeader_GetPointRecordsCount(*header);
    constexpr unsigned int pointLimit = 1000000;
    unsigned int pointsToRead = atakmap::math::min(pointLimit, pointRecordsCount);
    unsigned int skipFactor =   pointRecordsCount / pointsToRead;

    TAK::Engine::Feature::Envelope2 aabb(LASHeader_GetMinX(*header), LASHeader_GetMinY(*header), LASHeader_GetMinZ(*header) * zScale,
                                         LASHeader_GetMaxX(*header), LASHeader_GetMaxY(*header), LASHeader_GetMaxZ(*header) * zScale);

    TAK::Engine::Math::Point2<double> center((aabb.minX + aabb.maxX) / 2.0, (aabb.minY + aabb.maxY) / 2.0, (aabb.minZ + aabb.maxZ) / 2.0);

    Material material;
    material.color = 0xFFFFFFFF;
    material.propertyType = Material::TEPT_Diffuse;

    MeshBuilder meshBuilder(DrawMode::TEDM_Points, TEVA_Position | TEVA_Color);
    meshBuilder.reserveVertices(pointsToRead);
    meshBuilder.addMaterial(material);

    unsigned int lasReaderPos = 0;
    LASPointH point;
    unsigned short rgb[3] = {0, 0, 0};

    long pointRecordIndex = 0;
    long callbackIndex = -1;

    constexpr long callbackCount = 360;
    const long callbackLength = (pointsToRead / callbackCount) ? (pointsToRead / callbackCount) : 1;

    bool cancel = false;

    while ((point = LASReader_GetNextPoint(*reader, skipFactor)) != nullptr && (cancel = *callbacks->cancelToken) == false) {
        double x = LASPoint_GetX(point) - center.x;
        double y = LASPoint_GetY(point) - center.y;
        double z = LASPoint_GetZ(point) * zScale - center.z;

        if (has_color) {
            LASPoint_GetColorRGB(point, rgb);
        } else if (has_intensity) {
            unsigned short val = LASPoint_GetIntensity(point);
            rgb[0] = val;
            rgb[1] = val;
            rgb[2] = val;
        } else {
            float fr, fg, fb;
            TAK::Engine::Model::LASSceneSPI_computeZColor(&fr, &fg, &fb, mean, stddev, LASPoint_GetZ(point));

            rgb[0] = uint16_t(fr * std::numeric_limits<uint16_t>::max());
            rgb[1] = uint16_t(fg * std::numeric_limits<uint16_t>::max());
            rgb[2] = uint16_t(fb * std::numeric_limits<uint16_t>::max());
        }

        meshBuilder.addVertex(x, y, z, 0, 0, 0, 0, 0, (float)rgb[0] / USHRT_MAX, (float)rgb[1] / USHRT_MAX, (float)rgb[2] / USHRT_MAX, 1);

        ++pointRecordIndex;

        // gate progress callbacks to callbackCount times
        long currentCallbackIndex = pointRecordIndex / callbackLength;
        if (currentCallbackIndex != callbackIndex) {
            callbackIndex = currentCallbackIndex;

            // normalize to int range for callback
            double percent = static_cast<double>(pointRecordIndex) / pointsToRead;
            callbacks->progress(callbacks->opaque, static_cast<int>(INT_MAX * percent), INT_MAX);
        }
    }

    if (!cancel) {
        MeshPtr meshPtr(nullptr, nullptr);
        meshBuilder.build(meshPtr);

        auto localFrame = Matrix2();
        SceneBuilder sceneBuilder(true);
        sceneBuilder.addMesh(std::move(meshPtr), &localFrame);
        sceneBuilder.build(scene);
    }

    return cancel ? TE_Canceled : TE_Ok;
}
#endif