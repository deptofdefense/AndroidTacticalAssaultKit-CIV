#ifndef TAK_ENGINE_RENDERER_CORE_GLYPHATLAS_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLYPHATLAS_H_INCLUDED

#include <string>

#include "port/String.h"
#include "renderer/Bitmap2.h"

#include <unordered_map>
#include <vector>

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class GLGlyphBatchFactory;
            }

            class GLTextureAtlas2;

            namespace Core {
                struct GlyphCursor {
                    GlyphCursor(double x_origin, double y_origin)
                    : x_origin(x_origin), y_origin(y_origin),
                    x(x_origin), y(y_origin), z(0.0),
                    advance(0.0),
                    min_x(x_origin), min_y(y_origin), min_z(0.0),
                    max_x(x_origin), max_y(y_origin), max_z(0.0),
                    kerning_cp(0),
                    line_min_x(x_origin),
                    line_max_x(x_origin),
                    line_min_y(y_origin),
                    line_max_y(y_origin),
                    line_add_count(0),
                    string_add_count(0),
                    string_max_x(x_origin),
                    string_min_x(x_origin),
                    string_max_y(y_origin),
                    string_min_y(x_origin),
                    strike_begin_x(0.0),
                    underline_begin_x(0.0),
                    end_of_line(false),
                    end_of_string(false),
                    striking(false),
                    underlining(false),
                    line_decor_add_count(0),
                    string_decor_add_count(0)
                    {}

                    GlyphCursor() : GlyphCursor(0.0, 0.0) {}

                    inline void reposition(double x_origin_, double y_origin_) NOTHROWS {
                        this->x_origin = x_origin_;
                        this->y_origin = y_origin_;
                        this->x = x_origin_;
                        this->y = y_origin_;
                    }
                    
                    // the origin position
                    double x_origin;
                    double y_origin;

                    // current cursor location
                    double x, y, z;

                    // the advance for the previously rendered glyph
                    double advance;

                    // discovered bounds for measuring. These are updated after a string ends
                    double min_x;
                    double min_y;
                    double min_z;
                    double max_x;
                    double max_y;
                    double max_z;

                    // min/max of current line
                    double line_max_x;
                    double line_min_x;
                    double line_max_y;
                    double line_min_y;


                    // min/max of current string. These are updated after a line ends
                    double string_max_x;
                    double string_min_x;
                    double string_max_y;
                    double string_min_y;

                    double strike_begin_x;
                    double underline_begin_x;

                    // number of glyphs added on the current line
                    size_t line_add_count;

                    // number of glyphs added on the current string
                    size_t string_add_count;

                    size_t line_decor_add_count;
                    size_t string_decor_add_count;

                    // the codepoint to consider for kerning
                    uint32_t kerning_cp;

                    // true when cursor is in a state of "end of line"
                    bool end_of_line;

                    // true when cursor is in a state of "end of string"
                    bool end_of_string;

                    bool striking;
                    bool underlining;
                };

                enum GlyphHAlignment {
                    GlyphHAlignment_Left,
                    GlyphHAlignment_Right,
                    GlyphHAlignment_Center
                };

                struct GlyphBuffersOpts {

                    GlyphBuffersOpts()
                        : point_size(1.0), 
                        h_alignment(GlyphHAlignment_Left),
                        break_on_end_of_line(false),
                        break_on_end_of_string(false),
                        underline(false),
                        strikethrough(false),
                        fill(false)
                    {}

                    // base scale
                    double point_size;
                    GlyphHAlignment h_alignment;
                    bool break_on_end_of_line;
                    bool break_on_end_of_string;
                    TAK::Engine::Port::String fontName;
                    TAK::Engine::Port::String style;
                    float font_size;
                    float outline_weight;
                    bool underline;
                    bool strikethrough;
                    bool fill;
                    float text_color_red, text_color_green, text_color_blue, text_color_alpha;
                    float back_color_red, back_color_green, back_color_blue, back_color_alpha;
                    float outline_color_red, outline_color_green, outline_color_blue, outline_color_alpha;
                    float renderX, renderY, renderZ;
                    float anchorX, anchorY, anchorZ;
                    float rotation;
                    float xray_alpha;
                };

                struct GlyphAtlasOpts {
                    
                    GlyphAtlasOpts()
                        : power_of_2_scaled_uvs(false)
                    {}

                    // round texture width/height to the next power of 2 when
                    // finding uv coords
                    bool power_of_2_scaled_uvs;
                };

                struct AtlasResource {
                    TAK::Engine::Port::String fontName;
                    TAK::Engine::Port::String style;
                    TAK::Engine::Port::String type;
                    TAK::Engine::Port::String jsonFilename;
                    TAK::Engine::Port::String pngFilename;
                };

                enum GlyphRenderMethod {
                    GlyphRenderMethod_Texture,
                    GlyphRenderMethod_SDF,
                    GlyphRenderMethod_MSDF,
                    GlyphRenderMethod_Vector
                };

                class GlyphAtlas {
                public:
                    friend TAK::Engine::Util::TAKErr GlyphAtlas_open(std::shared_ptr<GlyphAtlas>& result, const char* URI, const char* font,
                        const char* style, const char* type, const GlyphAtlasOpts* opts) NOTHROWS;

                    friend TAK::Engine::Util::TAKErr GlyphAtlas_loadBitmap(BitmapPtr& result, const std::shared_ptr<GlyphAtlas> &atlas,
                        const char* URI, const char* font, const char* style, const char *type, const GlyphAtlasOpts* opts) NOTHROWS;
                private :
                    /**
                     * Add to position, texture coord and index buffers for a given set of UNICODE code points. This method will return 
                     * on the first unknown codepoint (given the atlas). This gives the calling code the chance to handle this. Usually
                     * by building another set of buffers for another atlas using the same GlyphCursor.
                     * 
                     * @param pos the x,y z positions
                     * @param uvs the u,v texture coords
                     * @param idxs the index buffer
                     * @param decor_pos the decor buffer
                     * @param cursor [in, out] the glyph cursor position to start and move from
                     * @param opts options for buffer creation
                     * @param codepoints the code points to add
                     * @param num_codepoints the number of codepoints from source to add
                     * 
                     * @return the number of codepoints added.
                     */
                    size_t fillBuffers(
                        std::vector<float>& pos,
                        std::vector<float>& uvs,
                        std::vector<uint16_t>& idxs,
                        std::vector<float>& decor_pos,
                        GlyphCursor& cursor,
                        const GlyphBuffersOpts& opts,
                        const uint32_t *codepoints, size_t num_codepoints) NOTHROWS;
                public :
                    size_t measureCodepoints(
                        GlyphCursor& cursor,
                        const GlyphBuffersOpts& opts,
                        const uint32_t* codepoints, size_t num_codepoints) NOTHROWS;
                    
                    inline GlyphRenderMethod renderMethod() const NOTHROWS { return method_; }

                    TAK::Engine::Util::TAKErr getKerningAdvance(double* result, uint32_t codepoint, uint32_t next_codepoint) NOTHROWS;

                    bool supportsCodepoint(uint32_t codepoint) NOTHROWS;

                    inline double getDescender() const NOTHROWS {
                        return descender_;
                    }

                    inline double getLineHeight() const NOTHROWS {
                        return line_height_;
                    }
                private:
                    static size_t getStride_(const std::vector<float>& pos,
                        const std::vector<float>& uvs) NOTHROWS;

                    /**
                     * Horizontally align line.
                     */
                    void alignLine_(std::vector<float>* pos,
                        std::vector<float>* uvs,
                        std::vector<float>* decor_pos,
                        GlyphCursor& cursor,
                        const GlyphBuffersOpts& opts) NOTHROWS;

                    /**
                     * Adjust string horizontally to be positioned back at origin.
                     */
                    void alignString_(std::vector<float>* pos,
                        std::vector<float>* uvs,
                        std::vector<float>* decor_pos,
                        GlyphCursor& cursor,
                        const GlyphBuffersOpts& opts) NOTHROWS;

                    size_t fillBuffersOrMeasure_(
                        std::vector<float>* pos,
                        std::vector<float>* uvs,
                        std::vector<uint16_t>* idxs,
                        std::vector<float>* decor_pos,
                        GlyphCursor& cursor,
                        const GlyphBuffersOpts& opts,
                        const uint32_t* codepoints, size_t num_codepoints) NOTHROWS;

                    void addLineDecor_(GlyphCursor& cursor, std::vector<float>& decor_pos,
                        double x, double y, double z, double width, double height) NOTHROWS;

                    void finishDecor_(GlyphCursor& cursor, std::vector<float>& decor_pos,
                        const GlyphBuffersOpts& opts, bool finish_strike, bool finish_underline) NOTHROWS;

                private:
                    struct Glyph_ {
                        double advance;
                        float uv_left;
                        float uv_right;
                        float uv_bottom;
                        float uv_top;

                        // plane bounds
                        double left;
                        double right;
                        double bottom;
                        double top;

                        // not expecting too many of these, so pretty fast brute force lookup is fine
                        std::vector<std::pair<uint32_t, double>> kerning;

                        bool has_glyph_bounds;
                    };

                    std::unordered_map<uint32_t, Glyph_> glyphs_;

                    double atlas_size_;
                    int atlas_width_;
                    int atlas_height_;

                    double line_height_;
                    double ascender_;
                    double descender_;
                    double underline_y_;
                    double underline_thickness_;

                    std::string fontName;
                    std::string style;
                    GlyphRenderMethod method_;

                    friend class TAK::Engine::Renderer::Core::GLGlyphBatchFactory;
                };

                TAK::Engine::Util::TAKErr GlyphAtlas_open(std::shared_ptr<GlyphAtlas>& result, const char* URI, const char* font,
                    const char* style, const char* type, const GlyphAtlasOpts* opts) NOTHROWS;

                TAK::Engine::Util::TAKErr GlyphAtlas_loadBitmap(BitmapPtr& result, const std::shared_ptr<GlyphAtlas> &atlas,
                    const char* URI, const char* font, const char* style, const char *type, const GlyphAtlasOpts* opts) NOTHROWS;
            }
        }
    }
}

#endif