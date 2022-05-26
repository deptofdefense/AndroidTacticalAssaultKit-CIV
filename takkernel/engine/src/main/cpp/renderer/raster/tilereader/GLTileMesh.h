#ifndef ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILEMESH_H_INCLUDED
#define ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILEMESH_H_INCLUDED

#include "raster/DatasetProjection.h"
#include "math/Matrix2.h"
#include "math/Point2.h"
#include "core/GeoPoint2.h"
#include "renderer/core/GLGlobeBase.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {


                class GLTileMesh {
                  private:
                    double *mesh_coords_;
                    size_t mesh_coords_size_;
                    float *mesh_verts_;
                    size_t mesh_verts_size_;
                    float *mesh_tex_coords_;
                    size_t mesh_tex_coords_size_;
                    uint16_t *mesh_indices_;
                    size_t mesh_indices_size_;
                    size_t num_coords_;
                    size_t num_indices_;
                    int mesh_verts_draw_version_;
                    int vert_mode_;
                    bool mesh_dirty_;

                    atakmap::raster::DatasetProjection *img2lla_;
                    Math::Matrix2 img2uv_;
                    Math::Matrix2 uv2img_;
                    Math::Point2<double> img_ul_;
                    Math::Point2<double> img_ur_;
                    Math::Point2<double> img_lr_;
                    Math::Point2<double> img_ll_;
                    int estimated_subdivisions_;

                    TAK::Engine::Core::GeoPoint2 centroid_;
                    Math::Point2<double> centroid_proj_;
                    bool use_lcs_;

                  public:
                    GLTileMesh(double width, double height, float u, float v, atakmap::raster::DatasetProjection *img2lla);
                    GLTileMesh(double x, double y, double width, double height, float u0, float v0, float u1, float v1,
                               atakmap::raster::DatasetProjection *img2lla);
                    GLTileMesh(const Math::Point2<double> &imgUL, const Math::Point2<double> &imgUR, const Math::Point2<double> &imgLR,
                               const Math::Point2<double> &imgLL, const Math::Matrix2 &img2uv,
                               atakmap::raster::DatasetProjection *img2lla,
                               int estimatedSubdivisions);
                    ~GLTileMesh();

                    // These return TE_Err if img2uv cannot be inverted
                    TAK::Engine::Util::TAKErr resetMesh(double x, double y, double width, double height, float u0, float v0, float u1, float v1,
                                   atakmap::raster::DatasetProjection *img2lla);
                    TAK::Engine::Util::TAKErr resetMesh(Math::Point2<double> imgUL, Math::Point2<double> imgUR, Math::Point2<double> imgLR,
                                   Math::Point2<double> imgLL, Math::Matrix2 img2uv,
                                   atakmap::raster::DatasetProjection *img2lla,
                                   int estimatedSubdivisions);
 
                    void drawMesh(const Renderer::Core::GLGlobeBase &view, int texId, float r, float g, float b, float a);
                    void release();

                    static int estimateSubdivisions(double ulLat, double ulLng, double lrLat, double lrLng);
 

                  private:
                    TAK::Engine::Util::TAKErr commonInit(double x, double y, double width, double height, float u0, float v0, float u1,
                                                         float v1, atakmap::raster::DatasetProjection *img2lla);
                    TAK::Engine::Util::TAKErr commonInit(Math::Point2<double> imgUL, Math::Point2<double> imgUR, Math::Point2<double> imgLR,
                               Math::Point2<double> imgLL, const Math::Matrix2 &img2uv, atakmap::raster::DatasetProjection *img2lla,
                               int estimatedSubdivisions);
                    void validateMesh();
                    void setLCS(const Renderer::Core::GLGlobeBase &view, bool primary);
 
                    static int estimateSubdivisions(double x, double y, double width, double height,
                                                    atakmap::raster::DatasetProjection *img2lla);
                };


            }
        }
    }
}



#endif

