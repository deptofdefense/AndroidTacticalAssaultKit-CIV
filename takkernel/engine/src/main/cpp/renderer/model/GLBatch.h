#ifndef TAK_ENGINE_RENDERER_MODEL_GLBATCH_H_INCLUDED
#define TAK_ENGINE_RENDERER_MODEL_GLBATCH_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/RenderContext.h"
#include "core/MapSceneModel2.h"
#include "feature/AltitudeMode.h"
#include "math/Matrix2.h"
#include "math/Point2.h"
#include "model/Mesh.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "renderer/RenderState.h"
#include "renderer/Shader.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"
#include "renderer/GLDepthSampler.h"
#include "model/VertexDataLayout.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Model {
                /**
                 * Encapsulated sequence of state changes and draw calls stored in a compact manner. Use GLBatchBuilder to for construction.
                 */
                class ENGINE_API GLBatch {
                public:
                    /**
                     * Minimal texture info
                     */
                    struct Texture {
                        Texture() NOTHROWS;

                        /**
                         * Destroys the texture by calling release(). WARNING: care must be taken to
                         * ensure this method is called within a valid OpenGL context that can manage this resource.
                         */
                        ~Texture() NOTHROWS;

                        /**
                         * Releases the texture data (if exists) and resets tex_id to zero. WARNING: care must be taken to 
                         * ensure this method is called within a valid OpenGL context that can manage this resource.
                         */
                        void release() NOTHROWS;

                        GLuint tex_id;
                    };

                    /**
                     * Minimal vertex buffer info
                     */
                    struct Buffer {

                        Buffer() NOTHROWS;

                        /**
                         * Destroys the buffer by calling release(). WARNING: care must be taken to
                         * ensure this method is called within a valid OpenGL context that can manage this resource.
                         */
                        ~Buffer() NOTHROWS;

                        union {
                            void* ptr;
                            GLuint vbo_id;
                        } u;

                        uint32_t is_vbo : 1;
                        uint32_t is_free : 1;

                        /**
                         * Free resources (must be called from GL thread when this->is_vbo == true && this->u.vbo_id != 0)
                         */
                        void release() NOTHROWS;

                        /**
                         * Initiate a load of vertex data to the buffer.
                         *
                         * Can call from non-OpenGL thread when each of the following is true:
                         *   - use_vbo == false
                         *   - this->is_vbo == false
                         */
                        Util::TAKErr load(const void* data, size_t size, bool use_vbo) NOTHROWS;
                    };

                    typedef std::shared_ptr<const Shader> ShaderPtr;
                    typedef std::shared_ptr<Texture> TexturePtr;
                    typedef std::shared_ptr<Buffer> BufferPtr;

                private:
                    GLBatch(size_t shader_count,
                        size_t texture_count,
                        size_t buffer_count,
                        size_t raw_size,
                        size_t op_count) NOTHROWS;

                public:
                    ~GLBatch() NOTHROWS;

                    /**
                     * Execute the batched operations
                     * 
                     * @param state the current render state
                     * @param forwardTransfor the forward transform applied to the local transforms as the ModelView matrix
                     * @param proj the projectio matrix float[16]
                     */
                    Util::TAKErr execute(RenderState& state, const Math::Matrix2& forwardTransform, const float* proj) const NOTHROWS;

                    /**
                     * Execute a batch with the depth sampler. This will ignore shader changes and streams that are not required
                     * by the depth sampler.
                     * 
                     * @param sampler the depth sampler to use
                     * @param forwardTransfor the forward transform applied to the local transforms as the ModelView matrix
                     * @param proj the projectio matrix float[16]
                     */
                    Util::TAKErr execute(GLDepthSampler& sampler, const Math::Matrix2& forwardTransform, const float* proj) const NOTHROWS;

                private:
                    struct ExecuteState_ {
                        RenderState renderState;
                        GLDepthSampler *depth_sampler;
                    };

                    Util::TAKErr executeImpl_(ExecuteState_& state, const Math::Matrix2& forwardTransform, const float* proj) const NOTHROWS;

                private:
                    friend class GLBatchBuilder;

                    enum {
                        SHADER_SIZE_ = sizeof(GLBatch::ShaderPtr),
                        TEXTURE_SIZE_ = sizeof(GLBatch::TexturePtr),
                        BUFFER_SIZE_ = sizeof(GLBatch::BufferPtr),
                        MATRIX_SIZE_ = sizeof(float) * 16,
                    };

                    ShaderPtr* shader_begin_;
                    ShaderPtr* shader_end_;
                    inline TexturePtr* texture_begin_() const NOTHROWS { return reinterpret_cast<TexturePtr*>(shader_end_); }
                    TexturePtr* texture_end_;
                    inline BufferPtr* buffer_begin_() const NOTHROWS { return reinterpret_cast<BufferPtr*>(texture_end_); }
                    BufferPtr* buffer_end_;
                    uint8_t* raw_begin_() const NOTHROWS { return reinterpret_cast<uint8_t*>(buffer_end_); }
                    uint8_t* raw_end_;
                    inline uint32_t* op_begin_() const NOTHROWS { return reinterpret_cast<uint32_t*>(raw_end_); }
                    uint32_t* op_end_;

                public:
                    /**
                     * Number of buffers in the batch
                     */
                    inline size_t bufferCount() const NOTHROWS { return static_cast<size_t>(buffer_end_ - buffer_begin_()); }

                    /**
                     * Number of shaders used by the batch
                     */
                    inline size_t shaderCount() const NOTHROWS { return static_cast<size_t>(shader_end_ - shader_begin_); }

                    /**
                     * Number of textures used by the batch
                     */
                    inline size_t textureCount() const NOTHROWS { return static_cast<size_t>(texture_end_ - texture_begin_()); }

                    /**
                     * Set the shader at the shader index
                     */
                    Util::TAKErr setShader(size_t index, std::shared_ptr<const Shader>& shader) NOTHROWS;

                    /**
                     * Get the buffer pointer at buffer index
                     */
                    Buffer* bufferPtrAt(size_t index) NOTHROWS;
                };

                /**
                 * Unique batch pointer
                 */
                typedef std::unique_ptr<GLBatch, void(*)(GLBatch*)> GLBatchPtr;
                
                /**
                 * Shared batch pointer
                 */
                typedef std::shared_ptr<GLBatch> SharedGLBatchPtr;

                /**
                 * Vertex stream descriptor
                 */
                struct VertexStreamConfig {
                    
                    /**
                     * The vertex buffer the stream uses
                     */
                    std::shared_ptr<GLBatch::Buffer> buffer;

                    /**
                     * Offset in bytes from buffer begin
                     */
                    size_t offset;

                    /**
                     * The vertex attribute the stream represents
                     */
                    TAK::Engine::Model::VertexAttribute attribute;

                    /**
                     * Byte stride
                     */
                    GLsizei stride;

                    /**
                     * Component type (GL_FLOAT, etc.)
                     */
                    GLenum type;

                    /**
                     * Number of components
                     */
                    uint8_t size;
                };


                /**
                 * Constructs a GLBatch
                 */
                class ENGINE_API GLBatchBuilder {
                public:
                    enum {
                        /**
                         * Current number of supported textures for the render state
                         */
                        MAX_TEXTURE_SLOTS = 1,

                        /**
                         * Max number of textures available per batch
                         */
                        MAX_TEXTURES = 0xff,

                        /**
                         * Number of buffers available per batch
                         */
                        MAX_BUFFERS = 0xff
                    };


                    GLBatchBuilder() NOTHROWS;

                    /**
                     * Set the current shader the draw calls that follow use.
                     * 
                     * @param attrs the RenderAttributes that define the shader
                     */
                    Util::TAKErr setShader(size_t* shader_index, const RenderAttributes& attrs) NOTHROWS;
                    
                    /**
                     * Set the local frame the draw calls that follow use.
                     */
                    Util::TAKErr setLocalFrame(const TAK::Engine::Math::Matrix2& localFrame) NOTHROWS;
                    
                    /**
                     * Set the vertex streams the draw calls that follow use.
                     */
                    Util::TAKErr setStreams(const VertexStreamConfig* streams, size_t count) NOTHROWS;

                    /**
                     * Set the texture the draw calls that follow use.
                     */
                    Util::TAKErr setTexture(const GLBatch::TexturePtr& texture, size_t index = 0) NOTHROWS;

                    /**
                     * Add a draw arrays call
                     * 
                     * @param draw_mode (GL_TRIANGLES, GL_POINTS, etc...)
                     * @param vert_count the number of verts passed to glDrawArrays
                     */
                    Util::TAKErr drawArrays(GLenum draw_mode, size_t vert_count) NOTHROWS;

                    /**
                     * Add a draw elements call
                     * 
                     * @param draw_mode (GL_TRIANGLES, GL_POINTS, etc...)
                     * @param index_count the number of indices passed to glDrawArrays
                     * @param buffer_index the buffer index for the buffer containing the indices
                     */
                    Util::TAKErr drawElements(GLenum draw_mode, size_t index_count, size_t buffer_index) NOTHROWS;

                    /**
                     * Add a draw elements call
                     *
                     * @param draw_mode (GL_TRIANGLES, GL_POINTS, etc...)
                     * @param index_count the number of indices passed to glDrawArrays
                     * @param buffer_index the buffer index for the buffer containing the indices
                     */
                    Util::TAKErr drawElements(GLenum draw_mode, GLenum type, size_t index_count, size_t buffer_index) NOTHROWS;

                    /**
                     * Create the GLBatch for the current state
                     */
                    Util::TAKErr build(GLBatchPtr& result) NOTHROWS;

                    /**
                     * Create the shared GLBatch for the current state
                     */
                    Util::TAKErr buildShared(SharedGLBatchPtr& result) NOTHROWS;

                    /**
                     * Get the RenderAttributes for the shader at a given index
                     */
                    Util::TAKErr shaderRenderAttributes(RenderAttributes* result, size_t index) NOTHROWS;

                    /**
                     * Add a buffer to the current batch
                     */
                    Util::TAKErr addBuffer(size_t* buffer_index, const GLBatch::BufferPtr& buffer) NOTHROWS;

                    /**
                     * Get the current buffer count
                     */
                    inline size_t bufferCount() const NOTHROWS { return buffers_.size(); }

                private:
                    Util::TAKErr prepareForDraw(GLenum draw_mode) NOTHROWS;
                    GLBatch* createBatch() NOTHROWS;

                    struct ShaderFrame_ {
                        RenderAttributes attrs;
                        size_t shader_index;
                    };

                    struct LocalFrame_ {
                        TAK::Engine::Math::Matrix2 matrix;
                        size_t raw_offset;
                    };

                    struct TexturesFrame_ {
                        size_t texture_indices[MAX_TEXTURE_SLOTS];
                    };

                    TexturesFrame_ textures_state_;

                    struct StreamsFrame_ {
                        VertexStreamConfig streams[12];
                        size_t num_streams;
                    };

                    size_t ensureBuffer(const GLBatch::BufferPtr& buf) NOTHROWS;
                public:
                    inline const GLBatch::BufferPtr& bufferAt(size_t index) const NOTHROWS { return buffers_[index]; }

                private:
                    std::vector<GLBatch::TexturePtr> textures_;
                    std::vector<ShaderFrame_> shaders_;
                    std::vector<LocalFrame_> lfs_;
                    std::vector<GLBatch::BufferPtr> buffers_;
                    std::vector<StreamsFrame_> streams_;
                    std::vector<uint32_t> instrs_;
                    std::vector<uint8_t> raw_;

                    size_t shader_index_;
                    size_t streams_index_;
                    size_t lf_index_;

                    bool shader_dirty_;
                    bool streams_dirty_;
                    bool lf_dirty_;
                    bool textures_dirty_;

                };

            }
        }
    }
}
#endif
