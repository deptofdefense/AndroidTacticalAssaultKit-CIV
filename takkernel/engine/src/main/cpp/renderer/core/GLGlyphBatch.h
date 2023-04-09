#ifndef TAK_ENGINE_RENDERER_CORE_GLGLYPHBATCH_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLGLYPHBATCH_H_INCLUDED

#include "math/Matrix2.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLText2.h"
#include "renderer/Shader.h"
#include "renderer/core/GlyphAtlas.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                constexpr int LabelDataStride = 9;

                struct GlyphShader {
                    GLint uEdgeSoftness;
                    GLint uRadius;
                    GLint uLabelDataTexture;
                    GLint uLabelDataTextureSize;
                    GLint uXrayPass;
                    GLint aLabelDataIndex;
                    std::shared_ptr<const Shader2> shader;
                };

                TAK::Engine::Util::TAKErr GlyphShader_create(std::shared_ptr<const GlyphShader>& shader, const TAK::Engine::Core::RenderContext& rc, GlyphRenderMethod method) NOTHROWS;
                TAK::Engine::Util::TAKErr GlyphShader_create(GlyphShader& shader, const TAK::Engine::Core::RenderContext& rc, GlyphRenderMethod method) NOTHROWS;

                class GLGlyphBatchFactory;
                class GLLabelManager;

                /**
                 * Represents static resources for drawing a batched set of glyphs that can reference multiple atlas textures
                 */
                class GLGlyphBatch {
                public:
                    GLGlyphBatch() : texture_(nullptr, GLGlyphBatch::deleteTexture) { };
                    TAK::Engine::Util::TAKErr draw(const TAK::Engine::Core::RenderContext& rc, const unsigned int glyphMask, const float mvp[16]) NOTHROWS;
                    void clearBatchedGlyphs() NOTHROWS;
                private :
                    TAK::Engine::Util::TAKErr drawLegacy(const TAK::Engine::Core::RenderContext& rc, const unsigned int glyphMask, const float proj[16]) NOTHROWS;
                private :
                    static void deleteTexture(TAK::Engine::Renderer::GLTexture2 *texture);
                private:
                    friend class GLGlyphBatchFactory;
                    friend class GLLabelManager;

                    std::vector<float> verts_;
                    std::vector<float> labelData_;
                    std::vector<int32_t> vertLabelDataIds_;
                    std::unique_ptr<TAK::Engine::Renderer::GLTexture2, void(*)(TAK::Engine::Renderer::GLTexture2 *)> texture_;

                    struct Draw_ {
                        enum TargetLayer { Fill, Glyph, Decoration };

                        GlyphRenderMethod method;
                        TargetLayer targetLayer_;
                        GLuint texId_;
                        std::vector<uint16_t> idxs_;
                        // Group Draw_ by method
                        bool operator<(const Draw_& d) const {
                            if (targetLayer_ != d.targetLayer_)
                                return targetLayer_ < d.targetLayer_;
                            else
                                return method < d.method;
                        }
                    };

                    struct LegacyDraw_ {
                        unsigned int codepoint;
                        double x;
                        double y;
                        int32_t labelDataId;
                        GLText2& gltext;
                    };
                    std::vector<std::pair<TAK::Engine::Math::Matrix2, std::vector<LegacyDraw_>>> legacyDrawBatch_;

                    std::map<GLuint, Draw_> drawMap_;
                };

                struct GlyphMeasurements {
                    double min_x, min_y, min_z, max_x, max_y, max_z;
                    double descender;
                    double line_height;
                };

                /**
                 * Fills a GLGlyphBatch from given utf8 strings. A factory can be thought of as a "font" in a sense. It can contain multiple atlases that support
                 * different sets of code points and produces a single batch to support drawing a whole string of glyphs.
                 */
                class GLGlyphBatchFactory {
                public:
                    TAK::Engine::Util::TAKErr batch(GLGlyphBatch& glyphBatch, const char* utf8, const GlyphBuffersOpts& opts, GLText2& gltext) NOTHROWS;

                    /**
                     * Add an atlas, texture pair to the factory
                     * 
                     * @param atlas the atlas
                     * @param tex the texture containing the glyphs
                     */
                    TAK::Engine::Util::TAKErr addAtlas(const std::shared_ptr<GlyphAtlas>& atlas, const std::shared_ptr<GLTexture2>& tex) NOTHROWS;

                    /**
                     * Get the number of attached atlases
                     */
                    size_t atlasCount() const NOTHROWS;

                    /**
                     * Measure the string bounds.
                     */
                    TAK::Engine::Util::TAKErr measureStringBounds(
                        GlyphMeasurements* measurements,
                        const GlyphBuffersOpts& opts,
                        const char* utf8,
                        const GLText2& gltext) NOTHROWS;

                private:
                    std::vector<std::shared_ptr<GlyphAtlas>> atlas_stack_;
                    std::vector<std::shared_ptr<GLTexture2>> tex_stack_;
                };
            }
        }
    }
}

#endif