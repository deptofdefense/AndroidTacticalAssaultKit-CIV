#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER3_H_INCLUDED

#include "renderer/Shader.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLBatchPointBuffer.h"

#include "core/GeoPoint2.h"
#include "port/Collection.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometryRenderer3 :
                    public TAK::Engine::Renderer::Core::GLMapRenderable2
                {
                public:
                    typedef std::shared_ptr<GLBatchGeometry3> SharedGLBatchGeometryPtr;
                public:
                    struct ENGINE_API CachePolicy {
                        enum
                        {
                            Lines = 0x01,
                            Points = 0x02,
                            Polygons = 0x04,
                        };

                        std::size_t enabledCaches;

                        CachePolicy() NOTHROWS;
                    };
                private:
                    struct FidComparator
                    {
                        bool operator()(const GLBatchGeometry3 *a, const GLBatchGeometry3 *b) const
                        {
                            if (a->featureId < b->featureId)
                                return true;
                            else if (a->featureId > b->featureId)
                                return false;
                            else
                                return (a->subid < b->subid);
                        }
                    };
                private:
                    struct BatchPointComparator
                    {
                        bool operator()(const GLBatchPoint3 *a, const GLBatchPoint3 *b) const
                        {
                            if (a->textureId < b->textureId)
                                return true;
                            else if (a->textureId > b->textureId)
                                return false;
                            else if (a->color < b->color)
                                return true;
                            else if (a->color > b->color)
                                return false;
                            else if (a->featureId < b->featureId)
                                return true;
                            else if (a->featureId > b->featureId)
                                return false;
                            else
                                return (a->subid < b->subid);
                        }
                    };

                private:
                    struct BatchPipelineState
                    {
                        int color = 0;
                        float lineWidth = 0;
                        int texId = 0;
                        int textureUnit = 0;
                    };
                    struct LinesBuffer
                    {
                        GLuint vbo;
                        GLsizei count;
                    };
                private:

                    /// <summary>
                    ///********************************************************************** </summary>

                    // tracks the geometry pointers
                    std::list<SharedGLBatchGeometryPtr> geoms;

                    std::list<GLBatchPolygon3 *> surfacePolys;
                    std::list<GLBatchPolygon3 *> spritePolys;
                    std::list<GLBatchLineString3 *> surfaceLines;
                    std::list<GLBatchLineString3 *> spriteLines;
                    std::vector<std::unique_ptr<GLBatchLineString3>> pointLollipops;
                    std::set<GLBatchPoint3 *, BatchPointComparator> batchPoints;
                    std::list<GLBatchPoint3 *> draw_points_;
                    std::list<GLBatchPoint3 *> labels;
                    std::list<GLBatchPoint3 *> loadingPoints;

                    std::set<GLBatchGeometry3 *, FidComparator> sortedPolys;
                    std::set<GLBatchGeometry3 *, FidComparator> sortedLines;

                    std::vector<LinesBuffer> surfaceLineBuffers;
                    std::vector<LinesBuffer> spriteLineBuffers;

                    std::map<std::pair<int, int>, GLBatchPointBuffer> pointsBuffers;
                    Util::MemBuffer2 pointsBuffer;

                    BatchPipelineState state;

                    std::unique_ptr<TAK::Engine::Renderer::GLRenderBatch2> batch;

                    struct
                    {
                        Renderer::Shader2 base;
                        GLint uProjectionHandle;
                        GLint uModelViewHandle;
                        GLint uTextureHandle;
                        GLint aTextureCoordsHandle;
                        GLint aVertexCoordsHandle;
                        GLint uColorHandle;
                        GLint uTexSizeHandle;
                        GLint uPointSizeHandle;
                    } textureShader;

                    struct
                    {
                        Renderer::Shader2 base;
                        GLint u_mvp;
                        GLint u_viewportSize;
                        GLint a_vertexCoord0;
                        GLint a_vertexCoord1;
                        GLint a_texCoord;
                        GLint a_color;
                        GLint a_normal;
                        GLint a_halfStrokeWidth;
                        GLint a_dir;
                        GLint a_pattern;
                        GLint a_factor;
                    } lineShader;

                    bool labelBackgrounds;
                    size_t fadingLabelsCount;
                    double drawResolution;

                    const CachePolicy cachePolicy;

                    Math::Matrix2 localFrame;

                    TAK::Engine::Core::GeoPoint2 batchCentroid;
                    Math::Point2<double> batchCentroidProj;
                    int batchSrid;
                    int batchTerrainVersion;
                    int rebuildBatchBuffers;
                public:
                    GLBatchGeometryRenderer3() NOTHROWS;
                    GLBatchGeometryRenderer3(const CachePolicy &cachePolicy) NOTHROWS;

                    virtual Util::TAKErr hitTest(int64_t *fid, const atakmap::feature::Point &loc, const double screen_x, const double screen_y, const double thresholdMeters, int64_t noid) const NOTHROWS;
                    virtual Util::TAKErr hitTest2(Port::Collection<int64_t> &fids, const atakmap::feature::Point &loc, const double screen_x, const double screen_y, const double resoution, const int radius, const int limit, int64_t noid) const NOTHROWS;

                    Util::TAKErr setBatch(Port::Collection<GLBatchGeometry3 *> &geoms) NOTHROWS;
                    Util::TAKErr setBatch(Port::Collection<SharedGLBatchGeometryPtr> &value) NOTHROWS;

                    virtual void draw(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int renderPass) NOTHROWS;

                private:
                    virtual void drawSurface(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;
                    virtual void drawSprites(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;

                    Util::TAKErr fillBatchLists(Port::Iterator2<SharedGLBatchGeometryPtr> &geoms) NOTHROWS;
                    Util::TAKErr fillBatchLists(Port::Iterator2<GLBatchGeometry3 *> &geoms) NOTHROWS;

                    Util::TAKErr createLabels() NOTHROWS;

                    Util::TAKErr extrudePoints() NOTHROWS;

                    Util::TAKErr batchDrawLollipops(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;
                    Util::TAKErr buildLineBuffers(std::vector<LinesBuffer> &bufs, const TAK::Engine::Renderer::Core::GLMapView2 &view, const std::list<GLBatchLineString3 *> &lines) NOTHROWS;
                    Util::TAKErr drawLineBuffers(const TAK::Engine::Renderer::Core::GLMapView2 &view, const std::vector<LinesBuffer> &bufs) NOTHROWS;

                    Util::TAKErr batchDrawPoints(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;

                    Util::TAKErr drawPoints(const TAK::Engine::Renderer::Core::GLMapView2 &view) NOTHROWS;

                    Util::TAKErr renderPointsBuffers(const TAK::Engine::Renderer::Core::GLMapView2 &view, GLBatchPointBuffer & batch_point_buffer) NOTHROWS;

                public:
                    virtual int getRenderPass() NOTHROWS;

                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;

                    virtual void release() NOTHROWS;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER2_H_INCLUDED
