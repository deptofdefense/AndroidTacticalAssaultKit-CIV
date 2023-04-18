#include "renderer/core/GLGlyphBatch.h"

#include "thread/Mutex.h"
#include "thread/Lock.h"

#include <climits>

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Thread;

namespace {
    void insertQuad(std::vector<float>& verts, std::vector<uint16_t>& vertIdxs, std::vector<int32_t>& vertLabelDataIds, int32_t labelDataId,
                    float x, float y, float z, float width, float height) NOTHROWS;

    int32_t insertLabelData(std::vector<float> &labelData,
                            float color_r, float color_g, float color_b, float color_a,
                            float outline_r, float outline_g, float outline_b, float outline_a,
                            float x, float y, float z, float anchorXOffset, float anchorYOffset,
                            float rotationRads, float fontSize, float buffer, float outlineBuffer,
                            float xrayAlpha) NOTHROWS;
    // dst_count, src_count
    std::pair<size_t, size_t> decode_utf8(uint32_t* dst, const char* src, size_t dst_size) NOTHROWS;
    std::string encode_utf8(unsigned int cp);

    uint32_t packColor(float r, float g, float b, float a) {
        return (((unsigned int) (r * 0xFFu) << 24u) & 0xFF000000u) |
               (((unsigned int) (g * 0xFFu) << 16u) & 0xFF0000u) |
               (((unsigned int) (b * 0xFFu) << 8u) & 0xFF00u) |
               (((unsigned int) (a * 0xFFu)) & 0xFFu);
    }

    uint32_t packHalfInts(int32_t i1, int32_t i2) {
        i1 = atakmap::math::clamp(i1, SHRT_MIN, SHRT_MAX) + 32767;
        i2 = atakmap::math::clamp(i2, SHRT_MIN, SHRT_MAX) + 32767;
        auto ui1 = reinterpret_cast<uint32_t&>(i1);
        auto ui2 = reinterpret_cast<uint32_t&>(i2);
        return (ui1 << 16) | ui2 & 0xFFFFu;
    }

    uint32_t packFloats(float f1, int decPlaces1, float f2, int decPlaces2) {
        auto i1 = static_cast<int32_t>(f1 * (decPlaces1 * 10.0));
        auto i2 = static_cast<int32_t>(f2 * (decPlaces2 * 10.0));
        return packHalfInts(i1, i2);
    }

    struct GlyphBatchShaders {
        std::shared_ptr<const GlyphShader> sdf_glyph_prog;
        std::shared_ptr<const GlyphShader> msdf_glyph_prog;
        std::shared_ptr<const GlyphShader> texture_glyph_prog;
        std::shared_ptr<const GlyphShader> vector_glyph_prog;
    };
    std::map<const TAK::Engine::Core::RenderContext *, GlyphBatchShaders> shaders;

    struct {
        TAK::Engine::Thread::Mutex mutex;
        std::map<const TAK::Engine::Core::RenderContext *, std::unique_ptr<TAK::Engine::Renderer::GLRenderBatch2>> value;
    } spriteBatch;
}

#define DRAWS_DECORATION_ID -1
#define DRAWS_FILL_ID -2

TAKErr TAK::Engine::Renderer::Core::GlyphShader_create(std::shared_ptr<const GlyphShader>& result, const TAK::Engine::Core::RenderContext& rc, GlyphRenderMethod method) NOTHROWS {
    std::shared_ptr<GlyphShader> gs = std::make_shared<GlyphShader>();
    GlyphShader_create(*gs, rc, method);
    result = gs;
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GlyphShader_create(GlyphShader& result, const TAK::Engine::Core::RenderContext& rc, GlyphRenderMethod method) NOTHROWS {
    const char* vert =
#include "Glyph.vert"
        ;

    const char* vert_vector =
#include "GlyphVector.vert"
        ;

    const char* frag = 
#include "GlyphTexture.frag"
        ;

    const char* frag_vector =
#include "GlyphVector.frag"
        ;

    const char* sdf_frag = 
#include "GlyphSDF.frag"
        ;

    const char* msdf_frag =
#include "GlyphMSDF.frag"
        ;

    const char* needed_vert = vert;
    const char* needed_frag;
    switch (method) {
        case GlyphRenderMethod_SDF:
        needed_frag = sdf_frag;
            break;
        case GlyphRenderMethod_MSDF:
            needed_frag = msdf_frag;
            break;
        default:
        case GlyphRenderMethod_Texture:
            needed_frag = frag;
            break;
        case GlyphRenderMethod_Vector:
            needed_vert = vert_vector;
            needed_frag = frag_vector;
            break;
    }

    std::shared_ptr<const Shader2> shader;
    TAKErr code = Shader_compile(shader, rc, needed_vert, needed_frag);
    if (code == TE_Ok) {
        result.shader = shader;
        result.uEdgeSoftness = glGetUniformLocation(shader->handle, "uEdgeSoftness");
        result.uRadius = glGetUniformLocation(shader->handle, "uRadius");
        result.uLabelDataTexture = glGetUniformLocation(shader->handle, "uLabelDataTexture");
        result.uLabelDataTextureSize = glGetUniformLocation(shader->handle, "uLabelDataTextureSize");
        result.uXrayPass = glGetUniformLocation(shader->handle, "uXrayPass");
        result.aLabelDataIndex = glGetAttribLocation(shader->handle, "aLabelDataIndex");
    }

    return code;
}

Mutex& shadersMutex() NOTHROWS
{
    static Mutex m;
    return m;
}


const GlyphShader* getGlyphProg(const TAK::Engine::Core::RenderContext& rc, GlyphRenderMethod method) {
    Lock lock(shadersMutex());
    GlyphBatchShaders &s = shaders[&rc];

    switch (method) {
    case GlyphRenderMethod_Texture: {
        if (!s.texture_glyph_prog)
            GlyphShader_create(s.texture_glyph_prog, rc, GlyphRenderMethod_Texture);
        return s.texture_glyph_prog.get();
    }

    case GlyphRenderMethod_SDF: {
        if (!s.sdf_glyph_prog)
            GlyphShader_create(s.sdf_glyph_prog, rc, GlyphRenderMethod_SDF);
        return s.sdf_glyph_prog.get();
    }
    case GlyphRenderMethod_MSDF: {
        if (!s.msdf_glyph_prog)
            GlyphShader_create(s.msdf_glyph_prog, rc, GlyphRenderMethod_MSDF);
        return s.msdf_glyph_prog.get();
    }
    case GlyphRenderMethod_Vector: {
        if (!s.vector_glyph_prog)
            GlyphShader_create(s.vector_glyph_prog, rc, GlyphRenderMethod_Vector);
        return s.vector_glyph_prog.get();
    }
    }

    return nullptr;
}

//
// GLGlyphBatch
//

TAKErr GLGlyphBatch::draw(const TAK::Engine::Core::RenderContext& rc, const unsigned int glyphMask, const float mvp[16]) NOTHROWS {
    const bool xrayPass = !!glyphMask;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const GlyphShader* curr_prog = nullptr;
    GLuint curr_tex_id = 0;

    // Sort draws to ensure proper rendering order
    std::vector<Draw_> draws;
    for (auto& drawPair : drawMap_)
        draws.push_back(drawPair.second);
    std::sort(draws.begin(), draws.end());

    for (auto& draw : draws) {
        if (draw.idxs_.empty())
            continue;

        auto tex_id = draw.texId_;

        // switch programs
        const GlyphShader* draw_prog = getGlyphProg(rc, draw.method);
        if (!draw_prog)
            return TE_Err;

        if (curr_prog != draw_prog) {
            curr_prog = draw_prog;
            glUseProgram(curr_prog->shader->handle);
            glUniformMatrix4fv(curr_prog->shader->uMVP, 1, false, mvp);
        }

        // switch texture
        if (curr_tex_id != tex_id) {
            curr_tex_id = tex_id;
            if (tex_id != 0 && tex_id != DRAWS_FILL_ID && tex_id != DRAWS_DECORATION_ID) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, tex_id);
                glUniform1i(curr_prog->shader->uTexture, 0);
            } else {
                glBindTexture(GL_TEXTURE_2D, 0);
            }
        }

        // Wire up label data to a texture
        const size_t labelDataCount = this->labelData_.size();
        auto texDimSize = static_cast<size_t>(std::ceil(std::sqrt(labelDataCount)));
        this->labelData_.reserve(texDimSize * texDimSize);
        const void *labelDataptr = this->labelData_.data();

        // Create texture if missing or not large enough
        if (!texture_ || texture_->getTexWidth() < texDimSize) {
            texture_.reset(new GLTexture2(texDimSize, texDimSize, GL_RED, GL_FLOAT));
        }

        // Load label data to texture
        glActiveTexture(GL_TEXTURE1);
        texture_->load(labelDataptr, 0, 0, texDimSize, texDimSize);
        glBindTexture(GL_TEXTURE_2D, texture_->getTexId());
        glUniform1i(curr_prog->uLabelDataTexture, 1);
        glUniform1i(curr_prog->uLabelDataTextureSize, static_cast<GLint>(texDimSize));
        glUniform1i(curr_prog->uXrayPass, static_cast<GLint>(xrayPass ? 1 : 0));
        glActiveTexture(GL_TEXTURE0);

        const GLint posSize = 3;
        const GLint uvSize = 2;
        const GLsizei vertexStride = (posSize + uvSize) * sizeof(float);

        // glEnableVertexAttribArray
        glEnableVertexAttribArray(curr_prog->shader->aVertexCoords);
        if (curr_prog->shader->aTexCoords != -1)
            glEnableVertexAttribArray(curr_prog->shader->aTexCoords);
        if (curr_prog->aLabelDataIndex != -1)
            glEnableVertexAttribArray(curr_prog->aLabelDataIndex);

        // glVertexAttribPointer
        const float* vertptr = this->verts_.data();
        glVertexAttribPointer(curr_prog->shader->aVertexCoords, posSize,
            GL_FLOAT, GL_FALSE, vertexStride, vertptr);
        if (curr_prog->shader->aTexCoords != -1)
            glVertexAttribPointer(curr_prog->shader->aTexCoords, uvSize, GL_FLOAT,
                                  false, vertexStride, vertptr + posSize);
        if (curr_prog->aLabelDataIndex != -1)
            glVertexAttribIPointer(curr_prog->aLabelDataIndex, 1, GL_INT,
                                   0, this->vertLabelDataIds_.data());

        // Set uniforms
        if (draw.method == GlyphRenderMethod_Vector) {
            // Give background fills rounded corners & antialiased edges
            float edgeSoftness = draw.targetLayer_ == Draw_::Fill ? 0.1f : 0.0f;
            float radius = draw.targetLayer_ == Draw_::Fill ? 1.f : 0.0f;
            glUniform1f(curr_prog->uEdgeSoftness, edgeSoftness);
            glUniform1f(curr_prog->uRadius, radius);
        }

        // main text
        glDrawElements(GL_TRIANGLES, static_cast<GLsizei>(draw.idxs_.size()),
                       GL_UNSIGNED_SHORT, draw.idxs_.data());

        // glDisableVertexAttribArray
        glDisableVertexAttribArray(curr_prog->shader->aVertexCoords);
        if (curr_prog->shader->aTexCoords != -1)
            glDisableVertexAttribArray(curr_prog->shader->aTexCoords);
        if (curr_prog->aLabelDataIndex != -1)
            glDisableVertexAttribArray(curr_prog->aLabelDataIndex);
    }

    glUseProgram(GL_NONE);
    glDisable(GL_BLEND);

    return TE_Ok;
}

TAKErr GLGlyphBatch::drawLegacy(const TAK::Engine::Core::RenderContext& rc, const unsigned int mask, const float proj[16]) NOTHROWS {
    if (legacyDrawBatch_.empty()) {
        return TE_Ok;
    }

    const bool xrayPass = !!mask;

    GLRenderBatch2 *batch = nullptr;
    {
        Lock lock(spriteBatch.mutex);
        auto &batchPtr = spriteBatch.value[&rc];
        if(!batchPtr)
            batchPtr.reset(new TAK::Engine::Renderer::GLRenderBatch2(0xFFFF));
        batch = batchPtr.get();
    }

    for (auto &drawBatch : legacyDrawBatch_) {
        batch->begin();
        {
            double mv[16];
            float mvf[16];
            drawBatch.first.get(mv, Matrix2::COLUMN_MAJOR);
            for (int a = 0; a < 16; a++)
                mvf[a] = static_cast<float>(mv[a]);
            batch->setMatrix(GL_PROJECTION, proj);
            batch->setMatrix(GL_MODELVIEW, mvf);
        }
        for (auto &draw : drawBatch.second) {
            int i = LabelDataStride * draw.labelDataId;
            int32_t text_color = reinterpret_cast<int32_t &>(labelData_[i]);
            float text_r = static_cast<float>((text_color >> 24) & 0xFFu) / 255.f;
            float text_g = static_cast<float>((text_color >> 16) & 0xFFu) / 255.f;
            float text_b = static_cast<float>((text_color >> 8) & 0xFFu) / 255.f;
            float text_a = static_cast<float>(text_color & 0xFFu) / 255.f;
            if (xrayPass) {
                text_a = labelData_[i + 8];
            }
            auto fontSize = static_cast<double>(draw.gltext.getTextFormat().getFontSize());
            std::string str = encode_utf8(draw.codepoint);
            draw.gltext.batch(*batch, str.c_str(), static_cast<float>(draw.x * fontSize),
                         static_cast<float>(draw.y * fontSize), 0.f,
                         text_r, text_g, text_b, text_a);
        }
        batch->end();
    }
    return TE_Ok;
}
void GLGlyphBatch::deleteTexture(TAK::Engine::Renderer::GLTexture2* value) {
    if (!value)
        return;
    value->release();
    delete value;
}

//
// GLGlyphBatchFactory
//
TAKErr GLGlyphBatchFactory::batch(GLGlyphBatch& glyphBatch, const char* utf8, const GlyphBuffersOpts& opts, GLText2& gltext) NOTHROWS {

    std::vector<float> decor_buf;
    std::vector<GLGlyphBatch::LegacyDraw_> legacyDraws;

    // A buffer of 0.5 seems to produce an appropriate visual weight.
    float text_buffer = 0.5f;
    // Add 0.05 for each additional weight unit. Limit buffer to be between 0.5 and 0.1.
    float outline_buffer = std::max(0.5f - (opts.outline_weight * 0.05f), 0.1f);
    int32_t labelDataId = insertLabelData(glyphBatch.labelData_,
                                          opts.text_color_red, opts.text_color_green,
                                          opts.text_color_blue, opts.text_color_alpha,
                                          opts.outline_color_red, opts.outline_color_green,
                                          opts.outline_color_blue, opts.outline_color_alpha,
                                          opts.renderX, opts.renderY, opts.renderZ,
                                            opts.anchorX - opts.renderX,
                                            opts.anchorY - opts.renderY, opts.rotation,
                                          opts.font_size, text_buffer, outline_buffer,
                                          opts.xray_alpha);

    GlyphCursor cursor;

    uint32_t codepoints[256];
        
    const char* cp = utf8;
    while (*cp) {

        auto decode_counts = decode_utf8(codepoints, cp, sizeof(codepoints) / sizeof(uint32_t));
        size_t dst_count = decode_counts.first;
        size_t src_count = decode_counts.second;
        size_t codepoint_index = 0;

        while (codepoint_index < dst_count) {

            size_t pre_atlas_codepoint_index = codepoint_index;

            // try each atlas at it
            for (size_t i = 0; i < atlas_stack_.size(); ++i) {

                // Get Draws_ for this atlas
                GLuint tex_id = this->tex_stack_[i]->getTexId();
                auto it = glyphBatch.drawMap_.find(tex_id);
                if (it == glyphBatch.drawMap_.end()) {
                    it = glyphBatch.drawMap_.insert(std::pair<GLuint, GLGlyphBatch::Draw_>(tex_id, GLGlyphBatch::Draw_())).first;

                    it->second.method = this->atlas_stack_[i]->renderMethod();
                    it->second.targetLayer_ = GLGlyphBatch::Draw_::Glyph;
                    it->second.texId_ = tex_id;
                }

                size_t startingSize = glyphBatch.verts_.size() / 5;
                size_t fill_count = this->atlas_stack_[i]->fillBuffers(glyphBatch.verts_, glyphBatch.verts_, it->second.idxs_, decor_buf,
                    cursor, opts, codepoints + codepoint_index, dst_count - codepoint_index);
                size_t endingSize = glyphBatch.verts_.size() / 5;
                for (int a = 0; a < endingSize - startingSize; a++)
                    glyphBatch.vertLabelDataIds_.push_back(labelDataId);

                codepoint_index += fill_count;

                // done with this buffer chunk
                if (codepoint_index == dst_count)
                    break;
            }

            // this means we've hit a codepoint we can't handle
            if (codepoint_index == pre_atlas_codepoint_index) {
                // advance codepoint
                unsigned int codepoint = codepoints[codepoint_index++];
                // advance cursor X
                cursor.x += cursor.advance * opts.point_size;

                // advance info
                auto fontSize = static_cast<double>(gltext.getTextFormat().getFontSize());
                double width = gltext.getTextFormat().getCharWidth(codepoint) / fontSize / opts.point_size;
                double height = gltext.getTextFormat().getCharHeight() / fontSize / opts.point_size;
                double fontDescent = gltext.getTextFormat().getDescent() / fontSize / opts.point_size;
                cursor.advance = width;
                cursor.kerning_cp = codepoint;
                cursor.line_min_x = std::min(cursor.line_min_x, cursor.x);
                cursor.line_max_x = std::max(cursor.line_max_x, cursor.x + width);
                cursor.line_min_y = std::min(cursor.line_min_y, cursor.y);
                cursor.line_max_y = std::max(cursor.line_max_y, cursor.y + height + fontDescent);

                GLGlyphBatch::LegacyDraw_ draw{codepoint, cursor.x, cursor.y + fontDescent, labelDataId, gltext};
                legacyDraws.push_back(draw);
            }
        }

        cp += src_count;
    }

    // has line decorations?
    if (!decor_buf.empty()) {
        auto it = glyphBatch.drawMap_.find(DRAWS_DECORATION_ID);
        if (it == glyphBatch.drawMap_.end()) {
            it = glyphBatch.drawMap_.insert(std::pair<GLuint, GLGlyphBatch::Draw_>(DRAWS_DECORATION_ID,
                                                           GLGlyphBatch::Draw_())).first;
            it->second.method = GlyphRenderMethod_Vector;
            it->second.targetLayer_ = GLGlyphBatch::Draw_::Decoration;
            it->second.texId_ = DRAWS_DECORATION_ID;
        }

        for (size_t i = 0; i < decor_buf.size(); i += 5) {
            float renderX = opts.renderX + (decor_buf[i + 0] * opts.font_size);
            float renderY = opts.renderY + (decor_buf[i + 1] * opts.font_size);
            float renderZ = opts.renderZ + (decor_buf[i + 2] * opts.font_size);
            int32_t decorLabelId = insertLabelData(glyphBatch.labelData_,
                                                   opts.text_color_red, opts.text_color_green,
                                                   opts.text_color_blue, opts.text_color_alpha,
                                                   opts.outline_color_red, opts.outline_color_green,
                                                   opts.outline_color_blue, opts.outline_color_alpha,
                                                   renderX, renderY, renderZ,
                                                   opts.anchorX - renderX,
                                                   opts.anchorY - renderY,
                                                   opts.rotation, opts.font_size, text_buffer,
                                                   outline_buffer, opts.xray_alpha);
            float width = decor_buf[i + 3];
            float height = decor_buf[i + 4];
            insertQuad(glyphBatch.verts_, it->second.idxs_, glyphBatch.vertLabelDataIds_, decorLabelId, 0, 0, 0, width, height);
        }
    }

    // Has fill?
    if (opts.fill) {
        auto it = glyphBatch.drawMap_.find(DRAWS_FILL_ID);
        if (it == glyphBatch.drawMap_.end()) {
            it = glyphBatch.drawMap_.insert(std::pair<GLuint, GLGlyphBatch::Draw_>(DRAWS_FILL_ID,
                                                                                    GLGlyphBatch::Draw_())).first;
            it->second.method = GlyphRenderMethod_Vector;
            it->second.targetLayer_ = GLGlyphBatch::Draw_::Fill;
            it->second.texId_ = DRAWS_FILL_ID;
        }

        float renderX = opts.renderX + static_cast<float>(cursor.min_x * opts.font_size);
        float renderY = opts.renderY + static_cast<float>(cursor.min_y * opts.font_size);
        float renderZ = opts.renderZ + static_cast<float>(cursor.min_z * opts.font_size);
        int32_t fillLabelId = insertLabelData(glyphBatch.labelData_,
                                              opts.back_color_red, opts.back_color_green,
                                              opts.back_color_blue, opts.back_color_alpha,
                                              0.f, 0.f, 0.f, 0.f,
                                              renderX, renderY, renderZ,
                                              opts.anchorX - renderX,
                                              opts.anchorY - renderY,
                                              opts.rotation, opts.font_size, 0.f, 0.f,
                                              opts.xray_alpha);

        auto width = static_cast<float>(cursor.max_x - cursor.min_x);
        auto height = static_cast<float>(cursor.max_y - cursor.min_y);
        insertQuad(glyphBatch.verts_, it->second.idxs_, glyphBatch.vertLabelDataIds_, fillLabelId, 0, 0, 0, width, height);
    }

    if (!legacyDraws.empty()) {
        Matrix2 matrix;
        matrix.translate(opts.renderX, opts.renderY, opts.renderZ);
        matrix.rotate(opts.rotation, opts.anchorX - opts.renderX, opts.anchorY - opts.renderY);
        glyphBatch.legacyDrawBatch_.emplace_back(matrix, legacyDraws);
    }

    return TE_Ok;
}

TAKErr GLGlyphBatchFactory::addAtlas(const std::shared_ptr<GlyphAtlas>& atlas, const std::shared_ptr<GLTexture2>& tex) NOTHROWS {
    this->atlas_stack_.push_back(atlas);
    this->tex_stack_.push_back(tex);
    return TE_Ok;
}

size_t GLGlyphBatchFactory::atlasCount() const NOTHROWS {
    return atlas_stack_.size();
}

TAK::Engine::Util::TAKErr GLGlyphBatchFactory::measureStringBounds(GlyphMeasurements* measurements, const GlyphBuffersOpts& opts, 
    const char* utf8, const GLText2& gltext) NOTHROWS {

    GlyphCursor cursor;

    uint32_t codepoints[256];
    double descender = 0.0;
    double line_height = 0.0;

    const char* cp = utf8;
    while (*cp) {

        auto decode_counts = decode_utf8(codepoints, cp, sizeof(codepoints) / sizeof(uint32_t));
        size_t dst_count = decode_counts.first;
        size_t src_count = decode_counts.second;
        size_t codepoint_index = 0;

        while (codepoint_index < dst_count) {

            size_t pre_atlas_codepoint_index = codepoint_index;

            // try each atlas at it
            for (size_t i = 0; i < atlas_stack_.size(); ++i) {

                // remember index into idx_buffer to add to the draw

                size_t measure_count = this->atlas_stack_[i]->measureCodepoints(cursor, opts,
                    codepoints + codepoint_index, dst_count - codepoint_index);

                codepoint_index += measure_count;

                if (measure_count) {
                    descender = std::min(descender, this->atlas_stack_[i]->getDescender() * opts.point_size);
                    line_height = std::max(line_height, this->atlas_stack_[i]->getLineHeight() * opts.point_size);
                }

                // done with this buffer chunk
                if (codepoint_index == dst_count)
                    break;
            }

            // this means we've hit a codepoint we can't handle
            if (codepoint_index == pre_atlas_codepoint_index) {
                // advance codepoint
                unsigned int codepoint = codepoints[codepoint_index++];
                // advance cursor X
                cursor.x += cursor.advance * opts.point_size;

                // advance info
                auto fontSize = static_cast<double>(gltext.getTextFormat().getFontSize());
                double width = gltext.getTextFormat().getCharWidth(codepoint) / fontSize / opts.point_size;
                double height = gltext.getTextFormat().getCharHeight() / fontSize / opts.point_size;
                double fontDescent = gltext.getTextFormat().getDescent() / fontSize / opts.point_size;
                cursor.advance = width;
                cursor.kerning_cp = codepoint;
                cursor.line_min_x = std::min(cursor.line_min_x, cursor.x);
                cursor.line_max_x = std::max(cursor.line_max_x, cursor.x + width);
                cursor.line_min_y = std::min(cursor.line_min_y, cursor.y);
                cursor.line_max_y = std::max(cursor.line_max_y, cursor.y + height + fontDescent);
                descender = std::min(descender, fontDescent);
                line_height = std::max(line_height, height);
            }
        }

        cp += src_count;
    }

    if (measurements) {
        measurements->min_x = cursor.min_x;
        measurements->max_x = cursor.max_x;
        measurements->min_y = cursor.min_y;
        measurements->max_y = cursor.max_y;
        measurements->min_z = cursor.min_z;
        measurements->max_z = cursor.max_z;
        measurements->descender = descender;
        measurements->line_height = line_height;
    }

    return TE_Ok;

}

void GLGlyphBatch::clearBatchedGlyphs() NOTHROWS {
    this->verts_.clear();
    this->labelData_.clear();
    this->drawMap_.clear();
    this->vertLabelDataIds_.clear();
    this->legacyDrawBatch_.clear();
}

namespace {
    void insertQuad(std::vector<float>& verts, std::vector<uint16_t>& vertIdxs, std::vector<int32_t>& vertLabelDataIds, int32_t labelDataId,
                    float x, float y, float z, float width, float height) NOTHROWS {
        size_t idx = verts.size() / 5;

        verts.push_back(x);
        verts.push_back(y);
        verts.push_back(z);
        verts.push_back(width);
        verts.push_back(height);

        verts.push_back(x);
        verts.push_back(y + height);
        verts.push_back(z);
        verts.push_back(width);
        verts.push_back(height);

        verts.push_back(x + width);
        verts.push_back(y + height);
        verts.push_back(z);
        verts.push_back(width);
        verts.push_back(height);

        verts.push_back(x + width);
        verts.push_back(y);
        verts.push_back(z);
        verts.push_back(width);
        verts.push_back(height);

        vertLabelDataIds.emplace_back(labelDataId);
        vertLabelDataIds.emplace_back(labelDataId);
        vertLabelDataIds.emplace_back(labelDataId);
        vertLabelDataIds.emplace_back(labelDataId);
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 0));
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 1));
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 2));
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 0));
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 3));
        vertIdxs.emplace_back(static_cast<uint16_t>(idx + 2));
    }

    int32_t insertLabelData(std::vector<float>& labelData,
                               float color_r, float color_g, float color_b, float color_a,
                               float outline_r, float outline_g, float outline_b, float outline_a,
                               float x, float y, float z, float anchorXOffset, float anchorYOffset,
                               float rotationRads, float fontSize, float buffer, float outlineBuffer,
                               float xrayAlpha) NOTHROWS {
        size_t index = labelData.size() / LabelDataStride;

        uint32_t color = packColor(color_r, color_g, color_b, color_a);
        labelData.push_back(reinterpret_cast<float&>(color));
        uint32_t outlineColor = packColor(outline_r, outline_g, outline_b, outline_a);
        labelData.push_back(reinterpret_cast<float&>(outlineColor));

        labelData.push_back(x);
        labelData.push_back(y);
        labelData.push_back(z);

        uint32_t anchorOffsets = packFloats(anchorXOffset, 1, anchorYOffset, 1);
        labelData.push_back(reinterpret_cast<float&>(anchorOffsets));

        uint32_t rotRadsFontSize = packFloats(rotationRads, 4, fontSize, 1);
        labelData.push_back(reinterpret_cast<float&>(rotRadsFontSize));

        uint32_t bufferValues = packFloats(buffer, 4, outlineBuffer, 4);
        labelData.push_back(reinterpret_cast<float&>(bufferValues));

        labelData.push_back(xrayAlpha);
        return static_cast<int32_t>(index);
    }

    size_t read_cp_bits(uint32_t &cp, const char* src, size_t expect) NOTHROWS {

        size_t i = 0;
        uint32_t bits = cp;

        while (src[i] && expect) {

            uint32_t b = *reinterpret_cast<const uint8_t*>(src + i);
            
            // unexpected
            if ((b & 0xc0) != 0x80) {
                break;
            }

            bits = (bits << 6) | (b & 0x3f);

            ++i;
            --expect;
        }

        cp = bits;
        return i;
    }

    // dst_count, src_count
    std::pair<size_t, size_t> decode_utf8(uint32_t* dst, const char* src, size_t dst_size) NOTHROWS {

        size_t src_i = 0;
        size_t dst_i = 0;

        while (src[src_i] && dst_i < dst_size) {
            
            uint32_t bits = *reinterpret_cast<const uint8_t*>(src + src_i);
            if (!(bits & 0x80)) {
                dst[dst_i++] = bits;
                src_i++;
            } else {
                size_t expect = 0;

                if ((bits & 0xf8) == 0xf0) { // length 4
                    bits = bits & 0x7;
                    expect = 3;
                } else if ((bits & 0xf0) == 0xe0) { // length 3
                    bits = bits & 0xf;
                    expect = 2;
                } else if ((bits & 0xe0) == 0xc0) { // length 2
                    bits = bits & 0x1f;
                    expect = 1;
                }

                // make sure we have enough
                size_t found = read_cp_bits(bits, src + src_i + 1, expect);
                if (found != expect) {
                    // malformed UTF8. Try and continue, but don't trust code point value.
                    bits = 0xffffffff;
                }

                dst[dst_i++] = bits;
                src_i += 1 + found;
            }
        }

        // null terminate. This is needed to trigger the end of the string in fillBuffers
        if (!src[src_i] && dst_i < dst_size) {
            dst[dst_i++] = 0;
        }

        return std::make_pair(dst_i, src_i);
    }

    std::string encode_utf8(unsigned int cp)
    {
        char c[5]={ 0x00,0x00,0x00,0x00,0x00 };
        if     (cp<=0x7F) { c[0] = cp;  }
        else if(cp<=0x7FF) { c[0] = (cp>>6)+192; c[1] = (cp&63)+128; }
        else if(0xd800<=cp && cp<=0xdfff) {} //invalid block of utf8
        else if(cp<=0xFFFF) { c[0] = (cp>>12)+224; c[1]= ((cp>>6)&63)+128; c[2]=(cp&63)+128; }
        else if(cp<=0x10FFFF) { c[0] = (cp>>18)+240; c[1] = ((cp>>12)&63)+128; c[2] = ((cp>>6)&63)+128; c[3]=(cp&63)+128; }
        return std::string(c);
    }
}