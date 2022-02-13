#include "renderer/elevation/TerrainTileShaders.h"

using namespace TAK::Engine::Renderer::Elevation;

TerrainTileShader::TerrainTileShader() NOTHROWS :
    handle(base.handle),
    uMVP(base.uMVP),
    uTexture(base.uTexture),
    uSunPosition(base.uSunPosition),
    uColor(base.uColor),
    uInvModelView(base.uInvModelView),
    aTexCoords(base.aTexCoords),
    aVertexCoords(base.aVertexCoords),
    aNormals(base.aNormals),
    uNormalMatrix(base.uNormalMatrix)
{}
TerrainTileShader::TerrainTileShader(const TerrainTileShader &other) NOTHROWS :
    handle(base.handle),
    uMVP(base.uMVP),
    uTexture(base.uTexture),
    uSunPosition(base.uSunPosition),
    uColor(base.uColor),
    uInvModelView(base.uInvModelView),
    aTexCoords(base.aTexCoords),
    aVertexCoords(base.aVertexCoords),
    aNormals(base.aNormals),
    uNormalMatrix(base.uNormalMatrix)
{
    *this = other;
}

TerrainTileShader &TerrainTileShader::operator=(const TerrainTileShader &other) NOTHROWS
{
    base = other.base;
    uModelViewOffscreen = other.uModelViewOffscreen;
    uLocalTransform = other.uLocalTransform;
    uTexWidth = other.uTexWidth;
    uTexHeight = other.uTexHeight;
    uElevationScale = other.uElevationScale;
    aNoDataFlag = other.aNoDataFlag;
    uMinAmbientLight = other.uMinAmbientLight;
    uLightSourceContribution = other.uLightSourceContribution;
    uLightSourceNormal = other.uLightSourceNormal;

    return *this;
}
