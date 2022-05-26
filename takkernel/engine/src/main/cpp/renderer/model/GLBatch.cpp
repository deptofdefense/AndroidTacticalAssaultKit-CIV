#include "renderer/model/GLBatch.h"

#include <cmath>
#include <unordered_set>

#include "core/ProjectionFactory3.h"
#include "math/Mesh.h"
#include "math/Utils.h"
#include "math/Vector4.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapView2.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "renderer/GLES20FixedPipeline.h"
#include "util/MathUtils.h"
#include "renderer/GLDepthSampler.h"
#include "renderer/GLWorkers.h"
#include "util/Tasking.h"
#include "renderer/GLES20FixedPipeline.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

GLBatch::Texture::Texture() NOTHROWS :
    tex_id(0) {}

GLBatch::Texture::~Texture() {
    this->release();
}

void GLBatch::Texture::release() NOTHROWS {
    if (tex_id) {
        glDeleteTextures(1, &tex_id);
        tex_id = 0;
    }
}

GLBatch::Buffer::Buffer() NOTHROWS :
    is_vbo(0),
    is_free(0) {
    u.ptr = nullptr;
}

GLBatch::Buffer::~Buffer() NOTHROWS {
    this->release();
}

void GLBatch::Buffer::release() NOTHROWS {

    if (this->is_free)
        ::free(this->u.ptr);
    else if (this->is_vbo)
        glDeleteBuffers(1u, &this->u.vbo_id);

    this->u.ptr = nullptr;
    this->is_free = 0;
    this->is_vbo = 0;
}

TAKErr GLBatch::Buffer::load(const void* data, size_t bufSize, bool use_vbo) NOTHROWS {

    if (!data && bufSize != 0)
        return TE_InvalidArg;

    // realloc case
    if (this->is_free && !use_vbo) {
        void *new_ptr = realloc(this->u.ptr, bufSize);
        if (!new_ptr)
            return TE_OutOfMemory;
        memcpy(new_ptr, data, bufSize);
        this->is_free = 1;
        this->is_vbo = 0;
        this->u.ptr = new_ptr;
        return TE_Ok;
    }

    // done?
    if (bufSize == 0)
        return TE_Ok;

    if (use_vbo) {
        GLuint vbo_ = GL_NONE;
        glGenBuffers(1u, &vbo_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, bufSize, data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        if (vbo_ == GL_NONE)
            return TE_OutOfMemory;
        this->release();
        this->u.vbo_id = vbo_;
        this->is_vbo = true;
    } else {
        void* ptr = malloc(bufSize);
        if (!ptr)
            return TE_OutOfMemory;
        memcpy(ptr, data, bufSize);
        this->release();
        this->u.ptr = ptr;
        this->is_free = 1;
    }

    return TE_Ok;
}

namespace {
    bool renderAttrsEq(const RenderAttributes& a, const RenderAttributes& b) NOTHROWS {
        return a.opaque == b.opaque &&
            a.textureIds[0] == b.textureIds[0] &&
            a.colorPointer == b.colorPointer &&
            a.normals == b.normals &&
            a.lighting == b.lighting &&
            a.windingOrder == b.windingOrder &&
            a.points == b.points;
    }

    void setVertexAttribPtr(GLuint& bound_array_buffer,
        GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const GLBatch::Buffer* buf, size_t offset) {

        const void* pointer = buf->is_vbo ? 
            reinterpret_cast<const void*>(offset) : static_cast<const uint8_t*>(buf->u.ptr) + offset;

        // bind if not
        if (buf->is_vbo && bound_array_buffer != buf->u.vbo_id) {
            glBindBuffer(GL_ARRAY_BUFFER, buf->u.vbo_id);
            bound_array_buffer = buf->u.vbo_id;
        }

        glVertexAttribPointer(index,
            size,
            type,
            normalized,
            stride,
            pointer);
    }

    bool streamsAreEq(const VertexStreamConfig* a, size_t a_count,
        const VertexStreamConfig* b, size_t b_count) NOTHROWS {

        if (a_count != b_count)
            return false;

        return a->attribute == b->attribute &&
            a->buffer == b->buffer &&
            a->offset == b->offset &&
            a->stride == b->stride &&
            a->size == b->size &&
            a->type == b->type;
    }

    template <size_t alignment>
    inline size_t alignSize(size_t size) {
        return (size + alignment - 1) & ~(alignment - 1);
    }

    const VertexStreamConfig* streamForAttr(VertexAttribute attr, const VertexStreamConfig* streams, size_t num_streams) {
        for (size_t i = 0; i < num_streams; ++i) {
            if (streams[i].attribute == attr)
                return streams + i;
        }
        return nullptr;
    }

    //

    constexpr uint32_t PREPARE_BIT_ = 1;
    constexpr uint32_t OP_MASK_ = 0xff;
    constexpr uint32_t OPERAND_SHIFT_ = 8;

    // opcodes
    constexpr uint32_t OP_SHADER_ = ((1 << 1) | PREPARE_BIT_);
    constexpr uint32_t OP_STREAMS_ = ((2 << 1) | PREPARE_BIT_);
    constexpr uint32_t OP_LF_ = ((3 << 1) | PREPARE_BIT_);
    constexpr uint32_t OP_TEX_ = ((4 << 1) | PREPARE_BIT_);
    constexpr uint32_t OP_DRAWA_ = (1 << 1);
    constexpr uint32_t OP_DRAWE_ = (2 << 1);
    constexpr uint32_t OP_DRAWE32_ = (3 << 1);

    // shader set flags (8 slots)
    constexpr uint32_t LF_SET_FLAG_ = 1;
}

GLBatch::GLBatch(size_t shader_count,
    size_t texture_count,
    size_t buffer_count,
    size_t raw_size,
    size_t op_count) NOTHROWS
: shader_begin_(reinterpret_cast<ShaderPtr*>(this + 1)),
  shader_end_(shader_begin_ + shader_count),
  texture_end_(this->texture_begin_() + texture_count),
  buffer_end_(this->buffer_begin_() + buffer_count),
  raw_end_(this->raw_begin_() + raw_size),
  op_end_(this->op_begin_() + op_count) {

    // ctor all resource shared pointers

    for (ShaderPtr* s = this->shader_begin_; s < this->shader_end_; ++s)
        ::new(static_cast<void*>(s)) ShaderPtr();
    for (TexturePtr* t = this->texture_begin_(); t < this->texture_end_; ++t)
        ::new(static_cast<void*>(t)) TexturePtr();
    for (BufferPtr* b = this->buffer_begin_(); b < this->buffer_end_; ++b)
        ::new(static_cast<void*>(b)) BufferPtr();
}

GLBatch::~GLBatch() NOTHROWS {

    // dtor all resource shared pointers

    for (ShaderPtr* s = this->shader_begin_; s < this->shader_end_; ++s)
        s->~ShaderPtr();
    for (TexturePtr* t = this->texture_begin_(); t < this->texture_end_; ++t)
        t->~TexturePtr();
    for (BufferPtr* b = this->buffer_begin_(); b < this->buffer_end_; ++b)
        b->~BufferPtr();
}

TAKErr GLBatch::setShader(size_t index, std::shared_ptr<const Shader>& shader) NOTHROWS {

    if (index >= this->shaderCount())
        return TE_InvalidArg;

    this->shader_begin_[index] = shader;
    return TE_Ok;
}

GLBatch::Buffer* GLBatch::bufferPtrAt(size_t index) NOTHROWS {

    if (index >= this->bufferCount())
        return nullptr;

    return this->buffer_begin_()[index].get();
}

GLBatchBuilder::GLBatchBuilder() NOTHROWS :
    shader_index_(SIZE_MAX),
    streams_index_(SIZE_MAX),
    lf_index_(SIZE_MAX),
    shader_dirty_(false),
    lf_dirty_(false),
    streams_dirty_(false),
    textures_dirty_(false) {

    for (size_t i = 0; i < MAX_TEXTURE_SLOTS; ++i)
        this->textures_state_.texture_indices[i] = SIZE_MAX;
}

TAKErr GLBatchBuilder::setShader(size_t* shader_index, const RenderAttributes& attrs) NOTHROWS {

    size_t index = SIZE_MAX;

    for (size_t i = 0; i < this->shaders_.size(); ++i) {
        if (renderAttrsEq(this->shaders_[i].attrs, attrs)) {
            index = i;
            break;
        }
    }

    if (index == std::numeric_limits<size_t>::max()) {
        index = this->shaders_.size();
        this->shaders_.push_back({
            attrs,
            index
            });
    }

    if (this->shader_index_ != index)
        this->shader_dirty_ = true;

    this->shader_index_ = index;

    if (shader_index)
        *shader_index = index;

    return TE_Ok;
}

TAKErr GLBatchBuilder::setLocalFrame(const TAK::Engine::Math::Matrix2& localFrame) NOTHROWS {

    size_t index = this->lfs_.size();
    this->lfs_.push_back(LocalFrame_ {
        localFrame,
        SIZE_MAX,
        });

    this->lf_index_ = index;
    this->lf_dirty_ = true;

    return TE_Ok;
}

TAKErr GLBatchBuilder::setStreams(const VertexStreamConfig* streams, size_t count) NOTHROWS {

    size_t index = SIZE_MAX;
    for (size_t i = 0; i < this->streams_.size(); ++i) {
        if (streamsAreEq(this->streams_[i].streams, this->streams_[i].num_streams, streams, count)) {
            index = i;
            break;
        }
    }

    if (index == SIZE_MAX) {
        
        index = this->streams_.size();

        // add frame
        StreamsFrame_ frame;
        for (size_t i = 0; i < count; ++i)
            frame.streams[i] = streams[i];
        frame.num_streams = count;
        this->streams_.push_back(frame);
    }

    if (this->streams_index_ != index)
        this->streams_dirty_ = true;

    this->streams_index_ = index;

    return TE_Ok;
}

TAKErr GLBatchBuilder::setTexture(const GLBatch::TexturePtr& texture, size_t index) NOTHROWS {

    if (index >= MAX_TEXTURE_SLOTS)
        return TE_InvalidArg;

    size_t texture_index = SIZE_MAX;
    for (size_t i = 0; i < this->textures_.size(); ++i) {
        if (this->textures_[i] == texture) {
            texture_index = i;
            break;
        }
    }

    if (texture_index >= this->textures_.size()) {
        texture_index = this->textures_.size();
        this->textures_.push_back(texture);
    }

    if (this->textures_state_.texture_indices[index] != texture_index) {
        this->textures_dirty_ = true;
    }

    this->textures_state_.texture_indices[index] = texture_index;

    return TE_Ok;
}

TAKErr GLBatchBuilder::drawArrays(GLenum draw_mode, size_t vert_count) NOTHROWS {

    if (vert_count > std::numeric_limits<uint32_t>::max())
        return TE_InvalidArg;

    this->prepareForDraw(draw_mode);

    uint32_t instr = (draw_mode << OPERAND_SHIFT_) | OP_DRAWA_;
    this->instrs_.push_back(instr);

    instr = static_cast<uint32_t>(vert_count);
    this->instrs_.push_back(instr);

    return TE_Ok;
}

TAKErr GLBatchBuilder::drawElements(GLenum draw_mode, size_t index_count, size_t buffer_index) NOTHROWS {
    return this->drawElements(draw_mode, GL_UNSIGNED_SHORT, index_count, buffer_index);
}

TAKErr GLBatchBuilder::drawElements(GLenum draw_mode, GLenum type, size_t index_count, size_t buffer_index) NOTHROWS {

    if (index_count > std::numeric_limits<uint32_t>::max())
        return TE_InvalidArg;
    if (buffer_index > 0xff)
        return TE_InvalidArg;

    this->prepareForDraw(draw_mode);

    uint32_t op = OP_DRAWE_;
    if (type == GL_UNSIGNED_INT)
        op = OP_DRAWE32_;

    uint32_t instr = (draw_mode << (OPERAND_SHIFT_ + 8)) | (static_cast<uint32_t>(buffer_index) << OPERAND_SHIFT_) | op;
    this->instrs_.push_back(instr);

    instr = static_cast<uint32_t>(index_count);
    this->instrs_.push_back(instr);

    return TE_Ok;
}

TAKErr GLBatchBuilder::prepareForDraw(GLenum draw_mode) NOTHROWS {

    TAKErr code = TE_Ok;

    // No shader specified?
    if (!this->shader_dirty_ && this->shaders_.size() == 0) {

        if (this->streams_index_ >= this->streams_.size())
            return TE_IllegalState;

        // determine from streams
        RenderAttributes attrs;
        const StreamsFrame_& streams = this->streams_[this->streams_index_];
        for (size_t i = 0; i < streams.num_streams; ++i) {
            switch (streams.streams[i].attribute) {
            case TEVA_Color: attrs.colorPointer = true; break;
            case TEVA_Normal: attrs.lighting = true; attrs.normals = true; break;
            case TEVA_TexCoord0: attrs.textureIds[0] = 1; break;
            }
        }

        if (draw_mode == GL_POINTS)
            attrs.points = true;

        code = this->setShader(nullptr, attrs);
        if (code != TE_Ok)
            return code;
    }

    // if shader switch, everything else must follow
    if (this->shader_dirty_) {
        this->streams_dirty_ = true;
    }

    if (this->shader_dirty_) {

        // trigger set of various uniforms after shader swap
        uint32_t set_flags = !this->lf_dirty_ ? LF_SET_FLAG_ : 0;

        uint32_t instr = (static_cast<uint32_t>(set_flags) << (OPERAND_SHIFT_ + 16)) |
            (static_cast<uint32_t>(this->shader_index_) << OPERAND_SHIFT_) | OP_SHADER_;
        this->instrs_.push_back(instr);
        this->shader_dirty_ = false;
    }

    if (this->streams_dirty_) {
        StreamsFrame_& config = this->streams_[this->streams_index_];

        uint32_t bits = 0;
        uint32_t a = 0; //< RESERVED
        uint32_t b = 0; //< RESERVED
        for (size_t i = 0; i < config.num_streams; ++i) {
            bits |= config.streams[i].attribute;
        }

        uint32_t instr = (bits << 16) | (a << 12) | (b << 8) | OP_STREAMS_;
        this->instrs_.push_back(instr);

#define ENCODE_STREAM_(attr) \
        { \
        const VertexStreamConfig* stream = streamForAttr(attr, config.streams, config.num_streams); \
        if (stream) { \
            size_t buffer_index = this->ensureBuffer(stream->buffer); \
            instr = (stream->stride << 8) | (buffer_index & 0xff); \
            this->instrs_.push_back(instr); \
            instr = static_cast<uint32_t>(stream->offset); \
            this->instrs_.push_back(instr); \
        } \
        }

        ENCODE_STREAM_(TEVA_Position);
        ENCODE_STREAM_(TEVA_Color);
        ENCODE_STREAM_(TEVA_Normal);
        ENCODE_STREAM_(TEVA_TexCoord0);

        this->streams_dirty_ = false;
    }

    if (this->textures_dirty_) {
        
        TexturesFrame_& frame = this->textures_state_;
        const size_t texture_index = frame.texture_indices[0];
    
        uint32_t instr = (static_cast<uint32_t>(texture_index) << OPERAND_SHIFT_) | OP_TEX_;
        this->instrs_.push_back(instr);

        this->textures_dirty_ = false;
    }

    if (this->lf_dirty_) {

        if (this->lf_index_ >= this->lfs_.size())
            return TE_IllegalState;

        LocalFrame_& lf = this->lfs_[this->lf_index_];

        if (lf.raw_offset >= this->raw_.size()) {
            size_t raw_offset = this->raw_.size();

            double mxd[16];
            float mxf[16];
            lf.matrix.get(mxd, Matrix2::COLUMN_MAJOR);
            for (std::size_t i = 0u; i < 16u; i++)
                mxf[i] = static_cast<float>(mxd[i]);

            this->raw_.insert(this->raw_.end(), reinterpret_cast<const uint8_t*>(mxf),
                reinterpret_cast<const uint8_t*>(mxf) + sizeof(mxf));

            lf.raw_offset = raw_offset;
        }

        uint32_t instr = (static_cast<uint32_t>(lf.raw_offset) << OPERAND_SHIFT_) | OP_LF_;
        this->instrs_.push_back(instr);

        this->lf_dirty_ = false;
    }
    
    return TE_Ok;
}

size_t GLBatchBuilder::ensureBuffer(const GLBatch::BufferPtr& buf) NOTHROWS {

    size_t index = SIZE_MAX;
    for (size_t i = 0; i < this->buffers_.size(); ++i) {
        if (this->buffers_[i] == buf) {
            index = i;
            break;
        }
    }

    if (index >= this->buffers_.size()) {
        index = this->buffers_.size();
        this->buffers_.push_back(buf);
    }

    return index;
}

namespace {
    void GLBatch_release(GLBatch* batch) {
        if (batch)
            batch->~GLBatch();
        ::free(batch);
    }
}

GLBatch* GLBatchBuilder::createBatch() NOTHROWS {

    size_t alignedRawSize = alignSize<sizeof(std::max_align_t)>(this->raw_.size());
    size_t alloc_size = sizeof(GLBatch);
    alloc_size += this->shaders_.size() * GLBatch::SHADER_SIZE_;
    alloc_size += this->buffers_.size() * GLBatch::BUFFER_SIZE_;
    alloc_size += this->textures_.size() * GLBatch::TEXTURE_SIZE_;
    alloc_size += alignedRawSize;
    alloc_size += this->instrs_.size() * sizeof(uint32_t);

    void* ptr = malloc(alloc_size);
    if (!ptr)
        return nullptr;
    
    // initialize all shader operands
    GLBatch* batch = ::new(ptr) GLBatch(
        this->shaders_.size(),
        this->textures_.size(),
        this->buffers_.size(),
        alignSize<sizeof(std::max_align_t)>(this->raw_.size()),
        this->instrs_.size());

    for (size_t i = 0; i < this->buffers_.size(); ++i)
        batch->buffer_begin_()[i] = this->buffers_[i];
    for (size_t i = 0; i < this->textures_.size(); ++i) {
        auto tex = this->textures_[i];
        batch->texture_begin_()[i] = tex;
    }

    std::copy(this->instrs_.begin(), this->instrs_.end(), batch->op_begin_());
    std::copy(this->raw_.begin(), this->raw_.end(), batch->raw_begin_());

    return batch;
}

TAKErr GLBatchBuilder::build(GLBatchPtr& result) NOTHROWS {

    GLBatch* batch = createBatch();
    if (!batch)
        return TE_OutOfMemory;

    result = GLBatchPtr(batch, GLBatch_release);
    return TE_Ok;
}

TAKErr GLBatchBuilder::buildShared(SharedGLBatchPtr& result) NOTHROWS {

    GLBatch* batch = createBatch();
    if (!batch)
        return TE_OutOfMemory;

    result = SharedGLBatchPtr(batch, GLBatch_release);
    return TE_Ok;
}

TAKErr GLBatchBuilder::shaderRenderAttributes(RenderAttributes* result, size_t index) NOTHROWS {

    if (index >= this->shaders_.size())
        return TE_InvalidArg;
    if (!result)
        return TE_InvalidArg;

    *result = this->shaders_[index].attrs;
    return TE_Ok;
}

TAKErr GLBatchBuilder::addBuffer(size_t* buffer_index, const GLBatch::BufferPtr& buffer) NOTHROWS {
    
    if (!buffer_index)
        return TE_InvalidArg;

    size_t index = this->buffers_.size();
    this->buffers_.push_back(buffer);
    *buffer_index = index;

    return TE_Ok;
}

const float IDENT_MATRIX_[16] = {
    1.0f, 0.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.0f, 0.0f,
    0.0f, 0.0f, 1.0f, 0.0f,
    0.0f, 0.0f, 0.0f, 1.0f
};

TAKErr GLBatch::execute(RenderState& state, const Matrix2& forwardTransform, const float* proj) const NOTHROWS {
    ExecuteState_ execute_state{state, nullptr};
    return executeImpl_(execute_state, forwardTransform, proj);
}

TAKErr GLBatch::execute(GLDepthSampler& sampler, const Math::Matrix2& forwardTransform, const float* proj) const NOTHROWS {
    ExecuteState_ execute_state{RenderState_getCurrent(), &sampler};
    return executeImpl_(execute_state, forwardTransform, proj);
}

template <typename TA, typename TB>
inline static void matrixMult(float* dst, TA* a, TB* b) {
    dst[0] = (float)(a[0] * b[0] + a[4] * b[1] + a[8] * b[2] + a[12] * b[3]);
    dst[1] = (float)(a[1] * b[0] + a[5] * b[1] + a[9] * b[2] + a[13] * b[3]);
    dst[2] = (float)(a[2] * b[0] + a[6] * b[1] + a[10] * b[2] + a[14] * b[3]);
    dst[3] = (float)(a[3] * b[0] + a[7] * b[1] + a[11] * b[2] + a[15] * b[3]);
    dst[4] = (float)(a[0] * b[4] + a[4] * b[5] + a[8] * b[6] + a[12] * b[7]);
    dst[5] = (float)(a[1] * b[4] + a[5] * b[5] + a[9] * b[6] + a[13] * b[7]);
    dst[6] = (float)(a[2] * b[4] + a[6] * b[5] + a[10] * b[6] + a[14] * b[7]);
    dst[7] = (float)(a[3] * b[4] + a[7] * b[5] + a[11] * b[6] + a[15] * b[7]);
    dst[8] = (float)(a[0] * b[8] + a[4] * b[9] + a[8] * b[10] + a[12] * b[11]);
    dst[9] = (float)(a[1] * b[8] + a[5] * b[9] + a[9] * b[10] + a[13] * b[11]);
    dst[10] = (float)(a[2] * b[8] + a[6] * b[9] + a[10] * b[10] + a[14] * b[11]);
    dst[11] = (float)(a[3] * b[8] + a[7] * b[9] + a[11] * b[10] + a[15] * b[11]);
    dst[12] = (float)(a[0] * b[12] + a[4] * b[13] + a[8] * b[14] + a[12] * b[15]);
    dst[13] = (float)(a[1] * b[12] + a[5] * b[13] + a[9] * b[14] + a[13] * b[15]);
    dst[14] = (float)(a[2] * b[12] + a[6] * b[13] + a[10] * b[14] + a[14] * b[15]);
    dst[15] = (float)(a[3] * b[12] + a[7] * b[13] + a[11] * b[14] + a[15] * b[15]);
}

TAKErr GLBatch::executeImpl_(ExecuteState_& execState, const Math::Matrix2& forwardTransform, const float* proj) const NOTHROWS {
    
    TAKErr code(TE_Ok);

    const uint32_t *ip = this->op_begin_();
    const uint32_t *end = this->op_end_;

    GLuint bound_array_buffer = 0;

    std::shared_ptr<const Shader> last_shader;

    const bool is_depth_sampler = execState.depth_sampler != nullptr;
    RenderState& state = execState.renderState;

    GLint aVertexCoords = -1;
    GLint aColorPointer = -1;
    GLint aNormals = -1;
    GLint aTextureCoords = -1;
    GLint uModelView = -1;
    GLint uMVP = -1;

    if (execState.depth_sampler) {
        aVertexCoords = execState.depth_sampler->attributeVertexCoords();
        uMVP = execState.depth_sampler->uniformMVP();

        // no face culling for depth sampler
        glDisable(GL_CULL_FACE);
    } else {
        GLenum front_face = GL_CCW;
        if (front_face != GL_NONE) {
            if (state.cull.face != GL_BACK) {
                state.cull.face = GL_BACK;
                glCullFace(state.cull.face);
            }
            if (state.cull.front != front_face) {
                state.cull.front = (GLint)front_face;
                glFrontFace(state.cull.front);
            }
            if (!state.cull.enabled) {
                state.cull.enabled = true;
                glEnable(GL_CULL_FACE);
            }
        }

        if (!state.blend.enabled) {
            state.blend.enabled = true;
            glEnable(GL_BLEND);
        }
        if (state.blend.src != GL_SRC_ALPHA || state.blend.dst != GL_ONE_MINUS_SRC_ALPHA) {
            state.blend.src = GL_SRC_ALPHA;
            state.blend.dst = GL_ONE_MINUS_SRC_ALPHA;
            glBlendFunc(state.blend.src, state.blend.dst);
        }
    }

    while (ip != end) {
     
        // instruction bits
        uint32_t instr = *ip++;

        // prepare bit indicates it is not a draw, but some state change before a draw
        if (instr & PREPARE_BIT_) {

            const uint32_t op = instr & OP_MASK_;
            switch (op) {
            case OP_SHADER_: {

                if (is_depth_sampler)
                    break;

                std::shared_ptr<const Shader> shader = this->shader_begin_[(instr >> OPERAND_SHIFT_) & 0xffff];
                if (!shader)
                    return TE_Canceled;

                if (shader != state.shader) {
                    last_shader = state.shader;
                    state.shader = shader;

                    aVertexCoords = shader->aVertexCoords;
                    aColorPointer = shader->aColorPointer;
                    aNormals = shader->aNormals;
                    aTextureCoords = shader->aTextureCoords;
                    uModelView = shader->uModelView;

                    glUseProgram(state.shader->handle);

                    glUniformMatrix4fv(state.shader->uProjection, 1, false, proj);

                    if (state.shader->textured)
                        glUniformMatrix4fv(state.shader->uTextureMx, 1, false, IDENT_MATRIX_);

                    if (state.shader->uColor != -1)
                        glUniform4f(state.shader->uColor, 1.0f, 1.0f, 1.0f, 1.0f);

                    if(state.shader->uPointSize != -1)
                        glUniform1f(state.shader->uPointSize, 8.0f);
                }

                uint32_t set_flags = instr >> (OPERAND_SHIFT_ + 16);

                // set flag to set uModelView to forwardTransform
                if (set_flags & LF_SET_FLAG_ /*&& state.shader->uModelView != -1*/) {
                    float mv[16];
                    double a[16];
                    forwardTransform.get(a, Matrix2::COLUMN_MAJOR);
                    for (size_t i = 0; i < 16; ++i)
                        mv[i] = static_cast<float>(a[i]);
                    glUniformMatrix4fv(uModelView, 1, false, mv);
                }
            }
                break;
            case OP_STREAMS_: {

                if (is_depth_sampler) {
                    glEnableVertexAttribArray(0);
                    glDisableVertexAttribArray(1);
                    glDisableVertexAttribArray(2);
                }

                // enable/disable
                //const GLuint a = 0;//(instr >> OPERAND_SHIFT_) & 0xf;
                //const GLuint b = 2;//(instr >> (OPERAND_SHIFT_ + 4)) & 0xf;
#define Shader_numAttribs(s) ((s ? static_cast<GLuint>(s->numAttribs) : 0))
                const GLuint a = Shader_numAttribs(last_shader.get());
                const GLuint b = Shader_numAttribs(state.shader.get());
#undef Shader_numAttribs
                for (GLuint i = a; i < b; i++) glEnableVertexAttribArray(i);
                for (GLuint i = a; i > b; i--) glDisableVertexAttribArray(i);

                // attribs
                uint32_t bits = (instr >> (OPERAND_SHIFT_ + 8)) /*& 0xffff*/;
                const GLBatch::Buffer* buffer = nullptr;
                GLsizei stride = 0;
                size_t offset = 0;

#define ATTRIB_OPERS_() \
                instr = *ip++; \
                buffer = this->buffer_begin_()[instr & 0xff].get(); \
                stride = static_cast<GLsizei>((instr >> 8) & 0xffffff); \
                instr = *ip++; \
                offset = instr;

                if (bits & TEVA_Position) {
                    ATTRIB_OPERS_();
                    setVertexAttribPtr(bound_array_buffer, aVertexCoords,
                        3, GL_FLOAT, false, stride, buffer, offset);
                }
                if (bits & TEVA_Color) {
                    ATTRIB_OPERS_();
                    if (!is_depth_sampler)
                        setVertexAttribPtr(bound_array_buffer, aColorPointer,
                            4, GL_UNSIGNED_BYTE, GL_TRUE, stride, buffer, offset);
                }
                if (bits & TEVA_Normal) {
                    ATTRIB_OPERS_();
                    if (!is_depth_sampler)
                        setVertexAttribPtr(bound_array_buffer, aNormals,
                            3, GL_FLOAT, false, stride, buffer, offset);
                }
                if (bits & TEVA_TexCoord0) {
                    ATTRIB_OPERS_();
                    if (!is_depth_sampler)
                        setVertexAttribPtr(bound_array_buffer, aTextureCoords,
                            2, GL_FLOAT, false, stride, buffer, offset);
                }
            } 
                break;

            case OP_LF_: {
                size_t matrix_offset = instr >> OPERAND_SHIFT_;
                const float* b = reinterpret_cast<const float*>(this->raw_begin_() + matrix_offset);
                float mv[16];
                double a[16];

                forwardTransform.get(a, Matrix2::COLUMN_MAJOR);
                matrixMult(mv, a, b);
                
                if (uModelView != -1)
                    glUniformMatrix4fv(uModelView, 1, false, mv);

                if (uMVP != -1) {
                    float mvp[16];
                    matrixMult(mvp, proj, mv);
                    glUniformMatrix4fv(uMVP, 1, false, mvp);
                }
            }
                break;

            case OP_TEX_: {
                const size_t texture_index = (instr >> OPERAND_SHIFT_);
                auto tex = this->texture_begin_()[texture_index];
                GLuint texId = tex ? tex->tex_id : 0;

                // skip texture op when depth sampling
                if (is_depth_sampler)
                    break;

                if (texId == 0) {
                    ip = end; // cut out early if textures aren't loaded
                } else {
                    glActiveTexture(GL_TEXTURE0 + /*texture coord index*/0);
                    glBindTexture(GL_TEXTURE_2D, texId);
                    glUniform1i(state.shader->uTexture, /*texture coord index*/0);
                    glActiveTexture(GL_TEXTURE0);
                }
            }
                break;
            default:
                ip = end;
                code = TE_IllegalState;
                break;
            }

        } else {
            switch (instr & OP_MASK_) {
            case OP_DRAWA_: {
                const GLenum draw_mode = instr >> OPERAND_SHIFT_;
                instr = *ip++;
                const GLsizei num_verts = instr;
                const GLsizei vertRenderLimit = (3 * 0xFFFF);
                for (GLint i = 0; i < num_verts; i += vertRenderLimit)
                    glDrawArrays(draw_mode, i, std::min(vertRenderLimit, num_verts - i));
            }
                break;
            case OP_DRAWE_: {
                GLenum draw_mode = instr >> (OPERAND_SHIFT_ + 8);
                size_t buffer_index = (instr >> (OPERAND_SHIFT_)) & 0xff;
                instr = *ip++;
                GLsizei index_count = instr;
                const void* ptr = this->buffer_begin_()[buffer_index]->u.ptr;
                glDrawElements(draw_mode, index_count, GL_UNSIGNED_SHORT, ptr);
            }
                break;
            case OP_DRAWE32_: {
                GLenum draw_mode = instr >> (OPERAND_SHIFT_ + 8);
                size_t buffer_index = (instr >> (OPERAND_SHIFT_)) & 0xff;
                instr = *ip++;
                GLsizei index_count = instr;
                const void* ptr = this->buffer_begin_()[buffer_index]->u.ptr;
                glDrawElements(draw_mode, index_count, GL_UNSIGNED_INT, ptr);
            }
                break;
            default:
                ip = end;
                code = TE_IllegalState;
                break;
            }
        }
    }

    if (bound_array_buffer)
        glBindBuffer(GL_ARRAY_BUFFER, 0);

    return code;
}