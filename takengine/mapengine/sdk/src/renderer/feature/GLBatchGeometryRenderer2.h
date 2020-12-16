#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER2_H_INCLUDED

#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/GLMapView.h"

#include "renderer/feature/GLBatchPoint2.h"
#include "renderer/feature/GLBatchPolygon2.h"
#include "renderer/feature/GLBatchLineString2.h"

#include "port/Collection.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchGeometryRenderer2 : 
                    public atakmap::renderer::map::GLMapRenderable
                {
                private :
                    typedef std::unique_ptr<float, void(*)(const float *)> FloatBufferPtr;
                public :
                    typedef std::shared_ptr<GLBatchGeometry2> SharedGLBatchGeometryPtr;
                public :
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
                private :
                    struct FidComparator
                    {
                        bool operator()(const GLBatchGeometry2 *a, const GLBatchGeometry2 *b) const
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
                        bool operator()(const GLBatchPoint2 *a, const GLBatchPoint2 *b) const
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

                private:
                    struct VectorProgram
                    {
                        int programHandle {0};
                        int uProjectionHandle {0};
                        int uModelViewHandle {0};
                        int aVertexCoordsHandle {0};
                        int uColorHandle {0};

                        std::size_t vertSize;

                        VectorProgram(std::size_t vertSize);
                    };
                private :
                    struct LineBatchRecord
                    {
                        float lineWidth;
                        int color;
                        float r;
                        float g;
                        float b;
                        float a;
                        std::size_t numVerts;

                        /** pointer, not owned */
                        float *buffer;
                    };
                private:

                    /// <summary>
                    ///********************************************************************** </summary>

                    // tracks the geometry pointers
                    std::list<SharedGLBatchGeometryPtr> geoms;

                    std::list<GLBatchPolygon2 *> polys;
                    std::list<GLBatchLineString2 *> lines;
                    std::set<GLBatchPoint2 *, BatchPointComparator> batchPoints;
                    std::list<GLBatchPoint2 *> labels;
                    std::list<GLBatchPoint2 *> loadingPoints;

                    std::set<GLBatchGeometry2 *, FidComparator> sortedPolys;
                    std::set<GLBatchGeometry2 *, FidComparator> sortedLines;

                    Util::array_ptr<float> pointsBuffer;
                    std::size_t pointsBufferLength;
                    int pointsBufferPosition;
                    std::size_t pointsBufferLimit;

                    Util::array_ptr<float> pointsVertsTexCoordsBuffer;

                    Util::array_ptr<int> textureAtlasIndicesBuffer;
                    std::size_t textureAtlasIndicesBufferLength;
                    int textureAtlasIndicesBufferPosition;
                    std::size_t textureAtlasIndicesBufferLimit;

                    BatchPipelineState state;

                    std::unique_ptr<atakmap::renderer::GLRenderBatch> batch;

                    int textureProgram;
                    int tex_uProjectionHandle;
                    int tex_uModelViewHandle;
                    int tex_uTextureHandle;
                    int tex_aTextureCoordsHandle;
                    int tex_aVertexCoordsHandle;
                    int tex_uColorHandle;

                    std::unique_ptr<VectorProgram> vectorProgram2d;
                    std::unique_ptr<VectorProgram> vectorProgram3d;

                    std::list<LineBatchRecord> cachedLines;
                    std::list<FloatBufferPtr> cachedLinesPages;

                    bool labelBackgrounds;
                    size_t fadingLabelsCount;

                    const CachePolicy cachePolicy;
                public:
                    GLBatchGeometryRenderer2() NOTHROWS;
                    GLBatchGeometryRenderer2(const CachePolicy &cachePolicy) NOTHROWS;

                    virtual Util::TAKErr hitTest(int64_t *fid, const atakmap::feature::Point &loc, const double thresholdMeters, int64_t noid) const NOTHROWS;
                    virtual Util::TAKErr hitTest2(Port::Collection<int64_t> &fids, const atakmap::feature::Point &loc, const double resoution, const int radius, const int limit, int64_t noid) const NOTHROWS;

                    Util::TAKErr setBatch(Port::Collection<GLBatchGeometry2 *> &geoms) NOTHROWS;
                    Util::TAKErr setBatch(Port::Collection<SharedGLBatchGeometryPtr> &value) NOTHROWS;

                    virtual void draw(const atakmap::renderer::map::GLMapView *view);

                private:
                    Util::TAKErr fillBatchLists(Port::Iterator2<SharedGLBatchGeometryPtr> &geoms) NOTHROWS;
                    Util::TAKErr fillBatchLists(Port::Iterator2<GLBatchGeometry2 *> &geoms) NOTHROWS;

                    Util::TAKErr batchDrawLinesProjected(const atakmap::renderer::map::GLMapView *view) NOTHROWS;
                    Util::TAKErr batchDrawCachedLinesProjected(const atakmap::renderer::map::GLMapView *view) NOTHROWS;

                    Util::TAKErr renderLinesBuffers(const VectorProgram &vectorProgram, const LineBatchRecord &record) NOTHROWS;

                    Util::TAKErr batchDrawPoints(const atakmap::renderer::map::GLMapView *view) NOTHROWS;

                    Util::TAKErr renderPointsBuffers(const atakmap::renderer::map::GLMapView *view) NOTHROWS;

                public:
                    virtual void start();
                    virtual void stop();

                    virtual void release();

                    /// <summary>
                    ///*********************************************************************** </summary>

                private:
                    //[DllImport("unknown")]
                    static void fillVertexArrays(float *translations, int *texAtlasIndices, int iconSize, int textureSize, float *vertsTexCoords, int count, float relativeScaling);

                    /// <summary>
                    /// Expands a buffer containing a line strip into a buffer containing lines.
                    /// None of the properties of the specified buffers (e.g. position, limit)
                    /// are modified as a result of this method.
                    /// </summary>
                    /// <param name="size">              The vertex size, in number of elements </param>
                    /// <param name="linestrip">         The pointer to the base of the line strip buffer </param>
                    /// <param name="linestripPosition"> The position of the linestrip buffer (should
                    ///                          always be <code>linestrip.position()</code>). </param>
                    /// <param name="lines">             The pointer to the base of the destination
                    ///                          buffer for the lines </param>
                    /// <param name="linesPosition">     The position of the lines buffer (should always
                    ///                          be <code>lines.position()</code>). </param>
                    /// <param name="count">             The number of points in the line string to be
                    ///                          consumed. </param>
                    static void expandLineStringToLines(size_t size, float *verts, size_t vertsOff, float *lines, size_t linesOff, size_t count);
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER2_H_INCLUDED
