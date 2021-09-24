#include "renderer/raster/tilereader/GLTileMesh.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapView2.h"
#include "math/Utils.h"
#include "util/MathUtils.h"
#include "util/ConfigOptions.h"

using namespace TAK::Engine::Renderer::Raster;

namespace {
    int getConfigOption(const char *option, int defVal)
    {
        using namespace TAK::Engine;

        Port::String sval;
        Util::TAKErr err = Util::ConfigOptions_getOption(sval, option);
        if (err == Util::TE_InvalidArg) return defVal;
        int n;
        if (Port::String_parseInteger(&n, sval) == Util::TE_Ok) return n;
        return defVal;
    }
}


GLTileMesh::GLTileMesh(double width, double height, float u, float v, atakmap::raster::DatasetProjection *img2lla)
{
    commonInit(0, 0, width, height, 0.0f, 0.0f, u, v, img2lla);
}

GLTileMesh::GLTileMesh(double x, double y, double width, double height, float u0, float v0, float u1, float v1,
             atakmap::raster::DatasetProjection *img2lla) {
    commonInit(x, y, width, height, u0, v0, u1, v1, img2lla);
}

GLTileMesh::GLTileMesh(const TAK::Engine::Math::Point2<double> &imgUL, const TAK::Engine::Math::Point2<double> &imgUR,
                       const TAK::Engine::Math::Point2<double> &imgLR, const TAK::Engine::Math::Point2<double> &imgLL,
                       const TAK::Engine::Math::Matrix2 &img2uv,
                       atakmap::raster::DatasetProjection *img2lla,
                       int estimatedSubdivisions)
{
    commonInit(imgUL, imgUR, imgLR, imgLL, img2uv, img2lla, estimatedSubdivisions);
}

GLTileMesh::~GLTileMesh() { release(); }


TAK::Engine::Util::TAKErr GLTileMesh::commonInit(double x, double y, double width, double height, float u0, float v0, float u1, float v1,
                atakmap::raster::DatasetProjection *img2lla)
{
    Math::Matrix2 m;
    Math::Matrix2_mapQuads(&m, x, y, x + width, y, x + width, y + height, x, y + height, u0, v0, u1, v0, u1, v1, u0, v1);
    return commonInit(Math::Point2<double>(x, y), Math::Point2<double>(x + width, y), Math::Point2<double>(x + width, y + height),
               Math::Point2<double>(x, y + height), m, img2lla, estimateSubdivisions(x, y, width, height, img2lla));
}

    
TAK::Engine::Util::TAKErr GLTileMesh::commonInit(TAK::Engine::Math::Point2<double> imgUL, TAK::Engine::Math::Point2<double> imgUR,
                                                 TAK::Engine::Math::Point2<double> imgLR, TAK::Engine::Math::Point2<double> imgLL,
                                                 const TAK::Engine::Math::Matrix2 &img2uv, atakmap::raster::DatasetProjection *img2lla,
                                                 int estimatedSubdivisions) {
    mesh_coords_ = nullptr;
    mesh_coords_size_ = 0;
    mesh_verts_ = nullptr;
    mesh_verts_size_ = 0;
    mesh_tex_coords_ = nullptr;
    mesh_tex_coords_size_ = 0;
    mesh_indices_ = nullptr;
    mesh_indices_size_ = 0;
    num_coords_ = 0;
    num_indices_ = 0;
    mesh_verts_draw_version_ = 0;
    vert_mode_ = 0;
    mesh_dirty_ = false;
    this->img2lla_ = nullptr;
    this->estimated_subdivisions_ = 0;
    use_lcs_ = false;
    return resetMesh(imgUL, imgUR, imgLR, imgLL, img2uv, img2lla, estimatedSubdivisions);
}




TAK::Engine::Util::TAKErr GLTileMesh::resetMesh(double x, double y, double width, double height, float u0, float v0, float u1, float v1,
                           atakmap::raster::DatasetProjection *img2lla)
{
    Math::Matrix2 m;
    Math::Matrix2_mapQuads(&m, x, y, x + width, y, x + width, y + height, x, y + height, u0, v0, u1, v0, u1, v1, u0, v1);

    return resetMesh(Math::Point2<double>(x, y), Math::Point2<double>(x + width, y), Math::Point2<double>(x + width, y + height),
              Math::Point2<double>(x, y + height), m, img2lla,
                    estimateSubdivisions(x, y, width, height, img2lla));
}

TAK::Engine::Util::TAKErr GLTileMesh::resetMesh(TAK::Engine::Math::Point2<double> imgUL, TAK::Engine::Math::Point2<double> imgUR,
                                                TAK::Engine::Math::Point2<double> imgLR, TAK::Engine::Math::Point2<double> imgLL,
                                                TAK::Engine::Math::Matrix2 img2uv, atakmap::raster::DatasetProjection *img2lla,
                                                int estimatedSubdivisions) {
    this->img_ul_ = imgUL;
    this->img_ur_ = imgUR;
    this->img_lr_ = imgLR;
    this->img_ll_ = imgLL;
    this->img2uv_ = img2uv;
    TAK::Engine::Util::TAKErr err = this->img2uv_.createInverse(&this->uv2img_);
    if (err != TAK::Engine::Util::TE_Ok)
        return err;
    this->img2lla_ = img2lla;
    this->estimated_subdivisions_ = estimatedSubdivisions;

    // if it doesn't compute, that's ok
    this->centroid_ = TAK::Engine::Core::GeoPoint2(0, 0);
    atakmap::core::GeoPoint centroidgp(this->centroid_);
    this->use_lcs_ = this->img2lla_->imageToGround(
        atakmap::math::PointD((imgUL.x + imgUR.x + img_lr_.x + imgLL.x) / 4.0, (imgUL.y + imgUR.y + imgLR.y + imgLL.y) / 4.0, 0.0), &centroidgp);
    atakmap::core::GeoPoint_adapt(&this->centroid_, centroidgp);

    this->mesh_dirty_ = true;
    return TAK::Engine::Util::TE_Ok;
}

void GLTileMesh::validateMesh()
{
    if (!mesh_dirty_) return;

    num_indices_ = GLTexture2_getNumQuadMeshIndices(this->estimated_subdivisions_, this->estimated_subdivisions_);
    num_coords_ = GLTexture2_getNumQuadMeshVertices(this->estimated_subdivisions_, this->estimated_subdivisions_);

    if (this->mesh_tex_coords_ == nullptr || mesh_tex_coords_size_ < (num_coords_ * 2)) {
        if (mesh_tex_coords_)
            delete[] mesh_tex_coords_;

        mesh_tex_coords_size_ = num_coords_ * 2;
        mesh_tex_coords_ = new float[mesh_tex_coords_size_];
    }

    if (num_coords_ > 4) {
        if (mesh_indices_ == nullptr || mesh_indices_size_ < num_indices_) {
            if (mesh_indices_)
                delete[] mesh_indices_;

            mesh_indices_size_ = num_indices_;
            mesh_indices_ = new uint16_t[mesh_indices_size_];
        }
    } else if (mesh_indices_) {
        delete[] mesh_indices_;
        mesh_indices_ = nullptr;
        mesh_indices_size_ = 0;
    }

    if (mesh_verts_ == nullptr || mesh_verts_size_ < (num_coords_ * 3)) {
        if (mesh_verts_)
            delete[] mesh_verts_;

        mesh_verts_size_ = num_coords_ * 3;
        mesh_verts_ = new float[mesh_verts_size_];
    }

    Math::Point2<double> scratchD(0, 0, 0);

    img2uv_.transform(&scratchD, img_ul_);
    auto u0 = (float)scratchD.x;
    auto v0 = (float)scratchD.y;
    img2uv_.transform(&scratchD, img_ur_);
    auto u1 = (float)scratchD.x;
    auto v1 = (float)scratchD.y;
    img2uv_.transform(&scratchD, img_lr_);
    auto u2 = (float)scratchD.x;
    auto v2 = (float)scratchD.y;
    img2uv_.transform(&scratchD, img_ll_);
    auto u3 = (float)scratchD.x;
    auto v3 = (float)scratchD.y;

    GLTexture2_createQuadMeshTexCoords(mesh_tex_coords_, atakmap::math::Point<float>(u0, v0), atakmap::math::Point<float>(u1, v1),
                                       atakmap::math::Point<float>(u2, v2), atakmap::math::Point<float>(u3, v3),
                                        estimated_subdivisions_, estimated_subdivisions_);

    if (mesh_indices_ != nullptr) {
        GLTexture2_createQuadMeshIndexBuffer(mesh_indices_, estimated_subdivisions_, estimated_subdivisions_);
    }

    vert_mode_ = GL_TRIANGLE_STRIP;

    // XXX - generate the LLA coords from the texcoords
    if (mesh_coords_ == nullptr || mesh_coords_size_ < (num_coords_ * 2)) {
        if (mesh_coords_)
            delete[] mesh_coords_;
        mesh_coords_size_ = num_coords_ * 2;
        mesh_coords_ = new double[mesh_coords_size_];
    }

    scratchD.z = 0;

    atakmap::core::GeoPoint scratchGeo;

    float *meshTexCoordsPtr = mesh_tex_coords_;
    double *meshCoordsPtr = mesh_coords_;
    for (std::size_t i = 0u; i < num_coords_; i++) {
        scratchD.x = *meshTexCoordsPtr;
        meshTexCoordsPtr++;
        scratchD.y = *meshTexCoordsPtr;
        meshTexCoordsPtr++;

        // XXX - uv2img transform pointD
        uv2img_.transform(&scratchD, scratchD);

        img2lla_->imageToGround(atakmap::math::PointD(scratchD.x, scratchD.y, scratchD.z), &scratchGeo);
        *meshCoordsPtr = scratchGeo.longitude;
        meshCoordsPtr++;
        *meshCoordsPtr = scratchGeo.latitude;
        meshCoordsPtr++;
    }

    mesh_verts_draw_version_ = -1;
    mesh_dirty_ = false;
}

void GLTileMesh::setLCS(const TAK::Engine::Renderer::Core::GLGlobeBase &view, bool primary) {
    Math::Matrix2 scratchM;
    scratchM.setToIdentity();
    scratchM.concatenate(view.renderPass->scene.forwardTransform);

    Math::Point2<double> pointD;
    TAK::Engine::Core::GeoPoint2 geo;

    if (!primary) {
        if (centroid_.longitude >= 0.0)
            geo = TAK::Engine::Core::GeoPoint2(centroid_.latitude, centroid_.longitude - 360.0, 0.0, TAK::Engine::Core::AltitudeReference::HAE);
        else
            geo = TAK::Engine::Core::GeoPoint2(centroid_.latitude, centroid_.longitude + 360.0, 0.0, TAK::Engine::Core::AltitudeReference::HAE);
        view.renderPass->scene.projection->forward(&pointD, geo);

        scratchM.translate(pointD.x, pointD.y, pointD.z);
    } else {
        scratchM.translate(centroid_proj_.x, centroid_proj_.y, centroid_proj_.z);
    }

    double scratchD[16];
    float scratchF[16];
    scratchM.get(scratchD, Math::Matrix2::COLUMN_MAJOR);
    for (int i = 0; i < 16; i++)
        scratchF[i] = (float)scratchD[i];
    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glLoadMatrixf(scratchF);
}

void GLTileMesh::drawMesh(const TAK::Engine::Renderer::Core::GLGlobeBase &view, int texId, float r, float g, float b, float a) {
    validateMesh();

    Math::Point2<double> pointD;
    TAK::Engine::Core::GeoPoint2 geo;

    if (mesh_verts_draw_version_ != view.renderPass->drawSrid) {
        if (use_lcs_) {
            view.renderPass->scene.projection->forward(&centroid_proj_, centroid_);
        } else {
            centroid_proj_.x = 0;
            centroid_proj_.y = 0;
            centroid_proj_.z = 0;
        }

        double *meshCoordsPtr = mesh_coords_;
        float *meshVertsPtr = mesh_verts_;
        for (std::size_t i = 0u; i < num_coords_; i++) {
            double lon = *meshCoordsPtr;
            meshCoordsPtr++;
            double lat = *meshCoordsPtr;
            meshCoordsPtr++;
            geo = TAK::Engine::Core::GeoPoint2(lat, lon, 0.0, TAK::Engine::Core::AltitudeReference::HAE);

            view.renderPass->scene.projection->forward(&pointD, geo);
            *meshVertsPtr = (float)(pointD.x - centroid_proj_.x);
            meshVertsPtr++;
            *meshVertsPtr = (float)(pointD.y - centroid_proj_.y);
            meshVertsPtr++;
            *meshVertsPtr = (float)(pointD.z - centroid_proj_.z);
            meshVertsPtr++;
        }

        mesh_verts_draw_version_ = view.renderPass->drawSrid;
    }

    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glPushMatrix();

    const int pumps = view.renderPass->crossesIDL ? 2 : 1;
    for (int i = 0; i < pumps; i++) {
        setLCS(view, i % 2 == 0);

        if (mesh_indices_ != nullptr) {
            GLTexture2_draw(texId, vert_mode_, num_indices_, 2, GL_FLOAT, mesh_tex_coords_, 3, GL_FLOAT, mesh_verts_, GL_UNSIGNED_SHORT,
                            mesh_indices_, r, g, b, a);
        } else {
            GLTexture2_draw(texId, vert_mode_, num_coords_, 2, GL_FLOAT, mesh_tex_coords_, 3, GL_FLOAT, mesh_verts_, r, g, b, a);
        }
    }

    fixedPipe->glPopMatrix();
}

void GLTileMesh::release() {
    if (mesh_coords_ != nullptr) {
        delete[] mesh_coords_;
        mesh_coords_ = nullptr;
        mesh_coords_size_ = 0;
    }
    if (mesh_verts_ != nullptr) {
        delete[] mesh_verts_;
        mesh_verts_ = nullptr;
        mesh_verts_size_ = 0;
    }
    if (mesh_tex_coords_ != nullptr) {
        delete[] mesh_tex_coords_;
        mesh_tex_coords_ = nullptr;
        mesh_tex_coords_size_ = 0;
    }
    if (mesh_indices_ != nullptr) {
        delete[] mesh_indices_;
        mesh_indices_ = nullptr;
        mesh_indices_size_ = 0;
    }
    num_coords_ = 0;
    num_indices_ = 0;

    mesh_verts_draw_version_ = -1;

    mesh_dirty_ = true;
}

int GLTileMesh::estimateSubdivisions(double ulLat, double ulLng, double lrLat, double lrLng) {
    const int minGridSize = getConfigOption("glquadtilenode2.minimum-grid-size", 1);
    const int maxGridSize = getConfigOption("glquadtilenode2.maximum-grid-size", 32);

    const int subsX = Util::MathUtils_clamp(
        Util::MathUtils_nextPowerOf2((int)ceil((ulLat - lrLat) / TAK::Engine::Renderer::Core::GLMapView2::getRecommendedGridSampleDistance())),
                                        minGridSize, maxGridSize);
    const int subsY = Util::MathUtils_clamp(
        Util::MathUtils_nextPowerOf2((int)ceil((lrLng - ulLng) / TAK::Engine::Renderer::Core::GLMapView2::getRecommendedGridSampleDistance())),
                                        minGridSize, maxGridSize);

    return atakmap::math::max(subsX, subsY);
}

int GLTileMesh::estimateSubdivisions(double x, double y, double width, double height, atakmap::raster::DatasetProjection *img2lla) {
    double minLat;
    double minLng;
    double maxLat;
    double maxLng;

    atakmap::math::PointD p = atakmap::math::PointD(0.0, 0.0);
    atakmap::core::GeoPoint g;

    p.x = x;
    p.y = y;
    img2lla->imageToGround(p, &g);
    minLat = g.latitude;
    minLng = g.longitude;
    maxLat = g.latitude;
    maxLng = g.longitude;

    p.x = x + width;
    p.y = y;
    img2lla->imageToGround(p, &g);
    minLat = atakmap::math::min(g.latitude, minLat);
    minLng = atakmap::math::min(g.longitude, minLng);
    maxLat = atakmap::math::max(g.latitude, maxLat);
    maxLng = atakmap::math::max(g.longitude, maxLng);

    p.x = x + width;
    p.y = y + height;
    img2lla->imageToGround(p, &g);
    minLat = atakmap::math::min(g.latitude, minLat);
    minLng = atakmap::math::min(g.longitude, minLng);
    maxLat = atakmap::math::max(g.latitude, maxLat);
    maxLng = atakmap::math::max(g.longitude, maxLng);

    p.x = x;
    p.y = y + height;
    img2lla->imageToGround(p, &g);
    minLat = atakmap::math::min(g.latitude, minLat);
    minLng = atakmap::math::min(g.longitude, minLng);
    maxLat = atakmap::math::max(g.latitude, maxLat);
    maxLng = atakmap::math::max(g.longitude, maxLng);


    const int minGridSize = getConfigOption("glquadtilenode2.minimum-grid-size", 1);
    const int maxGridSize = getConfigOption("glquadtilenode2.maximum-grid-size", 32);

    const int subsX = Util::MathUtils_clamp(
        Util::MathUtils_nextPowerOf2((int)ceil((maxLat - minLat) / TAK::Engine::Renderer::Core::GLMapView2::getRecommendedGridSampleDistance())),
        minGridSize, maxGridSize);
    const int subsY = Util::MathUtils_clamp(
        Util::MathUtils_nextPowerOf2((int)ceil((maxLng - minLng) / TAK::Engine::Renderer::Core::GLMapView2::getRecommendedGridSampleDistance())),
        minGridSize, maxGridSize);

    return atakmap::math::max(subsX, subsY);
}
