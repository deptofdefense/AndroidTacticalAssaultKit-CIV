#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER4_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER4_H_INCLUDED

#include <memory>

#include "renderer/GLOffscreenFramebuffer.h"
#include "renderer/Shader.h"
#include "renderer/GLVertexArray.h"
#include "renderer/core/GLDirtyRegion.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/feature/GLBatchGeometryShaders.h"

#include "core/GeoPoint2.h"
#include "port/Collection.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                struct PointBufferLayout {
                    GLfloat position[3];
                    GLshort rotation[2];
                    GLushort spriteBottomLeft[2];
                    GLushort spriteDimensions[2];
                    GLfloat pointSize;
                    GLubyte color[4];
                    GLuint id;
                    GLfloat absoluteRotationFlag;
                };

                // Various buffer size and offset constants
                constexpr GLuint POINT_VERTEX_SIZE = sizeof(PointBufferLayout);
                constexpr GLuint POINT_VERTEX_POSITION_OFFSET = offsetof(PointBufferLayout, position);
                constexpr GLuint POINT_VERTEX_ROTATION_OFFSET = offsetof(PointBufferLayout, rotation);
                constexpr GLuint POINT_VERTEX_SPRITE_BOTTOM_LEFT_OFFSET = offsetof(PointBufferLayout, spriteBottomLeft);
                constexpr GLuint POINT_VERTEX_SPRITE_DIMENSIONS_OFFSET = offsetof(PointBufferLayout, spriteDimensions);
                constexpr GLuint POINT_VERTEX_POINT_SIZE_OFFSET = offsetof(PointBufferLayout, pointSize);
                constexpr GLuint POINT_VERTEX_COLOR_OFFSET = offsetof(PointBufferLayout, color);
                constexpr GLuint POINT_VERTEX_ID_OFFSET = offsetof(PointBufferLayout, id);
                constexpr GLuint POINT_VERTEX_ABSOLUTE_ROTATION_FLAG_OFFSET = offsetof(PointBufferLayout, absoluteRotationFlag);

                struct LineBufferLayout {
                    GLfloat position0[3];
                    GLfloat position1[3];
                    GLubyte color[4];
                    GLuint pattern;
                    GLubyte normal;
                    GLubyte halfStrokeWidth;
                    GLubyte dir;
                    GLubyte factor;
                    GLuint id;
                };

                constexpr GLuint LINE_VERTEX_SIZE = sizeof(LineBufferLayout);
                constexpr GLuint LINE_VERTEX_POSITION0_OFFSET = offsetof(LineBufferLayout, position0);
                constexpr GLuint LINE_VERTEX_POSITION1_OFFSET = offsetof(LineBufferLayout, position1);
                constexpr GLuint LINE_VERTEX_COLOR_OFFSET = offsetof(LineBufferLayout, color);
                constexpr GLuint LINE_VERTEX_PATTERN_OFFSET = offsetof(LineBufferLayout, pattern);
                constexpr GLuint LINE_VERTEX_NORMAL_OFFSET = offsetof(LineBufferLayout, normal);
                constexpr GLuint LINE_VERTEX_HALF_STROKE_WIDTH_OFFSET = offsetof(LineBufferLayout, halfStrokeWidth);
                constexpr GLuint LINE_VERTEX_DIR_OFFSET = offsetof(LineBufferLayout, dir);
                constexpr GLuint LINE_VERTEX_FACTOR_OFFSET = offsetof(LineBufferLayout, factor);
                constexpr GLuint LINE_VERTEX_ID_OFFSET = offsetof(LineBufferLayout, id);

                struct PolygonBufferLayout {
                    GLfloat position0[3];
                    GLubyte color[4];
                    GLfloat outlineWidth;
                    GLfloat exteriorVertex;
                    GLuint id;
                };

                constexpr GLuint POLYGON_VERTEX_SIZE = sizeof(PolygonBufferLayout);
                constexpr GLuint POLYGON_VERTEX_POSITION_OFFSET = offsetof(PolygonBufferLayout, position0);
                constexpr GLuint POLYGON_VERTEX_COLOR_OFFSET = offsetof(PolygonBufferLayout, color);
                constexpr GLuint POLYGON_VERTEX_OUTLINE_WIDTH_OFFSET = offsetof(PolygonBufferLayout, outlineWidth);
                constexpr GLuint POLYGON_VERTEX_EXTERIOR_VERTEX_OFFSET = offsetof(PolygonBufferLayout, exteriorVertex);
                constexpr GLuint POLYGON_VERTEX_ID_OFFSET = offsetof(PolygonBufferLayout, id);

                // TODO jgm: I don't see why these can't be constants?
                struct ENGINE_API BatchGeometryBufferLayout
                {
                    // XXX - union here
                    struct {
                        struct {
                            TAK::Engine::Renderer::GLVertexArray position;
                            TAK::Engine::Renderer::GLVertexArray color;
                            TAK::Engine::Renderer::GLVertexArray normal;
                            TAK::Engine::Renderer::GLVertexArray id;
                        } lines;
                        struct {
                            TAK::Engine::Renderer::GLVertexArray position0;
                            TAK::Engine::Renderer::GLVertexArray position1;
                            TAK::Engine::Renderer::GLVertexArray color;
                            TAK::Engine::Renderer::GLVertexArray normal;
                            TAK::Engine::Renderer::GLVertexArray halfStrokeWidth;
                            TAK::Engine::Renderer::GLVertexArray dir;
                            TAK::Engine::Renderer::GLVertexArray pattern;
                            TAK::Engine::Renderer::GLVertexArray factor;
                            TAK::Engine::Renderer::GLVertexArray id;
                        } antiAliasedLines;
                        struct {
                            TAK::Engine::Renderer::GLVertexArray position;
                            TAK::Engine::Renderer::GLVertexArray color;
                            TAK::Engine::Renderer::GLVertexArray outlineWidth;
                            TAK::Engine::Renderer::GLVertexArray exteriorVertex;
                            TAK::Engine::Renderer::GLVertexArray normal;
                            TAK::Engine::Renderer::GLVertexArray id;
                        } polygons;
                        struct {
                            TAK::Engine::Renderer::GLVertexArray position;
                            TAK::Engine::Renderer::GLVertexArray fillColor;
                            TAK::Engine::Renderer::GLVertexArray strokeColor;
                            TAK::Engine::Renderer::GLVertexArray normal;
                            TAK::Engine::Renderer::GLVertexArray edge;
                            TAK::Engine::Renderer::GLVertexArray strokeWidth;
                            TAK::Engine::Renderer::GLVertexArray id;
                        } strokedPolygons;
                        struct {
                            TAK::Engine::Renderer::GLVertexArray position;
                            TAK::Engine::Renderer::GLVertexArray rotation;
                            TAK::Engine::Renderer::GLVertexArray pointSize;
                            TAK::Engine::Renderer::GLVertexArray spriteBottomLeft;
                            TAK::Engine::Renderer::GLVertexArray spriteDimensions;
                            TAK::Engine::Renderer::GLVertexArray color;
                            TAK::Engine::Renderer::GLVertexArray normal;
                            TAK::Engine::Renderer::GLVertexArray id;
                            TAK::Engine::Renderer::GLVertexArray absoluteRotationFlag;
                        } points;
                    } vertex;
                };

                class ENGINE_API GLBatchGeometryRenderer4 :
                    public TAK::Engine::Renderer::Core::GLMapRenderable2
                {
                public :
                    enum Program {
                        Lines,
                        AntiAliasedLines,
                        Polygons,
                        StrokedPolygons,
                        Points,
                    };
                    struct PrimitiveBuffer {
                        GLuint vbo{ GL_NONE };
                        GLuint ibo{ GL_NONE };
                        GLsizei count{ 0u };
                        GLenum mode{ GL_NONE };
                        GLuint texid{ GL_NONE };
                    };
                    struct BatchState {
                        TAK::Engine::Core::GeoPoint2 centroid;
                        Math::Point2<double> centroidProj;
                        int srid{ -1 };
                        Math::Matrix2 localFrame;
                    };
                private :
                    struct VertexBuffer
                    {
                        PrimitiveBuffer primitive;
                        BatchGeometryBufferLayout layout;
                    };
                public:
                    GLBatchGeometryRenderer4(const TAK::Engine::Core::RenderContext &context) NOTHROWS;

                    Util::TAKErr setBatchState(const BatchState &surface, const BatchState &sprites) NOTHROWS;
                    /**
                     * <P>Ownership of the buffer is transferred as a result of this call.
                     */
                    Util::TAKErr addBatchBuffer(const Program program, const PrimitiveBuffer &buffer, const BatchGeometryBufferLayout &layout, const int renderPass) NOTHROWS;
                    /**
                     * Marks all buffers currently owned for release. Will be released on next call to `draw` or `release`.
                     * 
                     * <P>This may be invoked from other threads, but the caller must externally synchronize.
                     */
                    void markForRelease() NOTHROWS;

                    /**
                     * Performs a hit test by rendering all objects with their IDs in place of their color attribute and reading the contents
                     * of the back buffer at the specified x and y screen coordinates.
                     * 
                     * @param featureIds Output parameter which will contain the IDs of the features that have been hit.
                     * @param view The current GLGlobeBase, used for rendering the features.
                     * @param screenX The x screen coordinate.
                     * @param screenY The y screen coordinate.
                     */
                    void hitTest(Port::Collection<uint32_t> &hitids, const Core::GLGlobeBase &view, const float screenX, const float screenY);
                private:
                    /**
                     * Draws the contents of the buffers. 
                     * 
                     * @param view The current GLGlobeBase, used for rendering the features.
                     * @param renderPass The current render pass. Should be `GLGlobeBase::Surface` for rendering features to the terrain surface, 
                     *                   `GLGlobeBase::Sprites` for rendering 3D features, or both bitwise or'd togethor.
                     * @param drawForHitTest If true then the features' color attribute will be replaced with either the upper or lower half of their feature id,
                     *                       depending on what `hitTestPass` is set to. Otherwise, the features will be rendered normally.
                     * @param hitTestPass Determines which half of feature ID will be used as the color attribute. If 0 then the upper half will be used.
                     *                    If 1 then the lower half will be used.
                     */
                    void draw(const Core::GLGlobeBase::State &view, const int renderPass, const bool drawForHitTest);
                    virtual void drawSurface(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, const bool drawForHitTest) NOTHROWS;
                    virtual void drawSprites(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, const bool drawForHitTest) NOTHROWS;


                    Util::TAKErr drawLineBuffers(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, const BatchState &ctx, const std::vector<VertexBuffer> &bufs, const bool drawForHitTest) NOTHROWS;
                    Util::TAKErr drawPolygonBuffers(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, const BatchState &ctx, const std::vector<VertexBuffer> &bufs, const bool drawForHitTest) NOTHROWS;
                    Util::TAKErr batchDrawPoints(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, const BatchState &ctx, const bool drawForHitTest) NOTHROWS;

                    Util::TAKErr drawPoints(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view) NOTHROWS;
                    Util::TAKErr renderPointsBuffers(const TAK::Engine::Renderer::Core::GLGlobeBase::State &view, GLBatchPointBuffer & batch_point_buffer) NOTHROWS;
                public:
                    virtual void draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS override;
                    virtual int getRenderPass() NOTHROWS override;

                    virtual void start() NOTHROWS override;
                    virtual void stop() NOTHROWS override;

                    virtual void release() NOTHROWS override;
                private:
                    const TAK::Engine::Core::RenderContext &context;
                    std::vector<VertexBuffer> surfaceLineBuffers;
                    std::vector<VertexBuffer> spriteLineBuffers;
                    std::vector<VertexBuffer> surfacePolygonBuffers;
                    std::vector<VertexBuffer> spritePolygonBuffers;

                    std::vector<VertexBuffer> pointsBuffers;

                    Core::GLDirtyRegion surfaceCoverage;

                    PointsShader pointShader;
                    AntiAliasedLinesShader lineShader;
                    PolygonsShader polygonsShader;

                    struct {
                        BatchState surface;
                        BatchState sprites;
                    } batchState;
                    std::vector<GLuint> markedForRelease;
                    bool pointsNeedSort = false;

                    struct {
                        std::shared_ptr<GLOffscreenFramebuffer> pixel;
                        std::shared_ptr<GLOffscreenFramebuffer> tile;
                    } fbo;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER4_H_INCLUDED
