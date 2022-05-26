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

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace {
    LASPointH LASReader_GetNextPoint(LASReaderH reader, const std::size_t pointInterval) NOTHROWS
    {
        for (std::size_t i = 1; i < pointInterval; i++) {
            // read the point, break on EOF
            if (!::LASReader_GetNextPoint(reader))
                break;
        }
        // read point at interval
        return ::LASReader_GetNextPoint(reader);
    }
}

const char *LASSceneSPI::getType() const NOTHROWS {
    return "LAS";
}

int LASSceneSPI::getPriority() const NOTHROWS {
    return 1;
}

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

typedef struct {
    double r;  // [0, 1]
    double g;  // [0, 1]
    double b;  // [0, 1]
} rgb;

typedef struct {
    double h;  // [0, 360]
    double s;  // [0, 1]
    double v;  // [0, 1]
} hsv;

rgb hsv2rgb(hsv HSV) {
    rgb RGB;
    double H = HSV.h, S = HSV.s, V = HSV.v, P, Q, T, fract;

    (H == 360.) ? (H = 0.) : (H /= 60.);
    fract = H - floor(H);

    P = V * (1. - S);
    Q = V * (1. - S * fract);
    T = V * (1. - S * (1. - fract));

    if (0. <= H && H < 1.)
        RGB = { V,  T,  P};
    else if (1. <= H && H < 2.)
        RGB = { Q, V, P};
    else if (2. <= H && H < 3.)
        RGB = {P, V, T};
    else if (3. <= H && H < 4.)
        RGB = {P, Q, V};
    else if (4. <= H && H < 5.)
        RGB = {T, P, V};
    else if (5. <= H && H < 6.)
        RGB = {V, P, Q};
    else
        RGB = {0., 0., 0.};

    return RGB;
}

TAKErr TAK::Engine::Model::LASSceneSPI_computeZColor(double z, float* r, float* g, float* b) {
    double meanRaw = 3117099.9999999995;
    double stdDevRaw = 4822999.9999999991;

    double val = phi(z, meanRaw, stdDevRaw);
    auto RGB = hsv2rgb({ val * 360.0, 1, 1 });
    *r = float(RGB.r);
    *g = float(RGB.g);
    *b = float(RGB.b);
    return TE_Ok;
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
    bool hasColorData = LASHeader_GetDataFormatId(*header) != 0 && LASHeader_GetDataFormatId(*header) != 1;
    unsigned int pointRecordsCount = LASHeader_GetPointRecordsCount(*header);
    constexpr unsigned int pointLimit = 1000000;
    unsigned int pointsToRead = atakmap::math::min(pointLimit, pointRecordsCount);
    unsigned int skipFactor =   pointRecordsCount / pointsToRead;

    TAK::Engine::Feature::Envelope2 aabb(LASHeader_GetMinX(*header), LASHeader_GetMinY(*header), LASHeader_GetMinZ(*header),
                                         LASHeader_GetMaxX(*header), LASHeader_GetMaxY(*header), LASHeader_GetMaxZ(*header));

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
        double z = LASPoint_GetZ(point) - center.z;

        if (hasColorData) {
            LASPoint_GetColorRGB(point, rgb);
        } else {
            unsigned short val = LASPoint_GetIntensity(point);
            rgb[0] = val;
            rgb[1] = val;
            rgb[2] = val;
        }

        meshBuilder.addVertex(x, y, z, 0, 0, 0, 0, 0, (float)rgb[0] / USHRT_MAX, (float)rgb[1] / USHRT_MAX, (float)rgb[2] / USHRT_MAX, 1);
        // TODO
        // if (useZColor) {
        //    LASSceneSPI_computeZColor(rawZ, &r, &g, &b);
        //}

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