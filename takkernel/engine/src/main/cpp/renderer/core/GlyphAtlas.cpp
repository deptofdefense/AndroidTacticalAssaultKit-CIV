#include "renderer/core/GlyphAtlas.h"

#include "port/StringBuilder.h"
#include "renderer/BitmapFactory2.h"
#include "util/DataInput2.h"
#include "util/MathUtils.h"
#include "util/Memory.h"

#include <sstream>

// Use tinygltf's copy of JSON for Modern C++
#include <tinygltf/json.hpp>
using json = nlohmann::json;

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

namespace {
    TAKErr parseJSON(json* result, DataInput2* input) NOTHROWS;
    bool isSkipControlChar(int cp) NOTHROWS;
    bool isNewLineControlChar(int cp) NOTHROWS;
}

size_t GlyphAtlas::fillBuffers(std::vector<float>& pos, std::vector<float>& uvs, std::vector<uint16_t>& idxs,
    std::vector<float>& decor_pos, GlyphCursor& cursor, const GlyphBuffersOpts& opts,
    const uint32_t* codepoints, size_t num_codepoints) NOTHROWS {

    return this->fillBuffersOrMeasure_(&pos, &uvs, &idxs, &decor_pos, cursor, opts, codepoints, num_codepoints);
}

size_t GlyphAtlas::measureCodepoints(
    GlyphCursor& cursor,
    const GlyphBuffersOpts& opts,
    const uint32_t* codepoints, size_t num_codepoints) NOTHROWS {

    return this->fillBuffersOrMeasure_(nullptr, nullptr, nullptr, nullptr, cursor, opts, codepoints, num_codepoints);
}

size_t GlyphAtlas::fillBuffersOrMeasure_(std::vector<float>*pos, std::vector<float>*uvs, std::vector<uint16_t>*idxs,
    std::vector<float>* decor_pos, GlyphCursor & cursor, const GlyphBuffersOpts & opts,
    const uint32_t * codepoints, size_t num_codepoints) NOTHROWS {

    if (!String_equal(opts.fontName, this->fontName.c_str()) || !String_equal(opts.style, this->style.c_str()))
        return 0;

    const uint32_t *cp = codepoints;
    size_t num_processed = 0;

    double point_size = opts.point_size;

    bool break_signal = false;

    const bool is_building = pos && uvs && idxs;

    while (num_codepoints && !break_signal) {

        int current_cp = *cp++;

        // just ended a line (or string)?
        if (cursor.end_of_line || cursor.end_of_string) {
            cursor.kerning_cp = 0;
            cursor.line_min_x = cursor.x_origin;
            cursor.line_max_x = cursor.x_origin;
            cursor.line_min_y = cursor.y;
            cursor.line_max_y = cursor.y;
            cursor.advance = 0.0;
            cursor.line_add_count = 0;
            cursor.line_decor_add_count = 0;
        }

        // just ended a string?
        if (cursor.end_of_string) {
            cursor.string_min_x = cursor.x_origin;
            cursor.string_max_x = cursor.x_origin;
            cursor.string_min_y = cursor.y_origin;
            cursor.string_max_y = cursor.y_origin;
            cursor.string_add_count = 0;
            cursor.string_decor_add_count = 0;
        }

        cursor.end_of_string = false;
        cursor.end_of_line = false;

        if (isNewLineControlChar(current_cp)) {

            if (is_building)
                finishDecor_(cursor, *decor_pos, opts, true, true);

            alignLine_(pos, uvs, decor_pos, cursor, opts);

            // move cursor
            cursor.x = cursor.x_origin;
            cursor.y -= this->line_height_ * point_size;
            
            // set signals
            cursor.end_of_line = true;
            break_signal = opts.break_on_end_of_line;

        } else if (current_cp == 0) {

            if (is_building)
                finishDecor_(cursor, *decor_pos, opts, true, true);

            alignLine_(pos, uvs, decor_pos, cursor, opts);
            alignString_(pos, uvs, decor_pos, cursor, opts);

            // set signals
            cursor.end_of_string = true;
            break_signal = opts.break_on_end_of_string;

        } else if (!isSkipControlChar(current_cp)) {

            auto it = this->glyphs_.find(current_cp);
            if (it == this->glyphs_.end())
                break;

            uint16_t idx = 0;
            if (is_building)
                idx = static_cast<uint16_t>(pos->size() / getStride_(*pos, *uvs));// (idxs->size() / 6) * 4);

            if (UINT16_MAX - idx < 4)
                break;

            // advance cursor X
            double kerning_advance = 0.0;
            getKerningAdvance(&kerning_advance, cursor.kerning_cp, it->first);
            cursor.x += (cursor.advance + kerning_advance) * point_size;

            double left = cursor.x;
            double right = cursor.x;
            double bottom = cursor.y;
            double top = cursor.y;

            if (it->second.has_glyph_bounds) {
                left = cursor.x + it->second.left * point_size;
                right = cursor.x + it->second.right * point_size;
                bottom = cursor.y + it->second.bottom * point_size;
                top = cursor.y + it->second.top * point_size;

                if (is_building) {
                    pos->push_back(static_cast<float>(left));
                    pos->push_back(static_cast<float>(bottom));
                    pos->push_back(static_cast<float>(cursor.z));
                    uvs->push_back(it->second.uv_left);
                    uvs->push_back(it->second.uv_bottom);

                    pos->push_back(static_cast<float>(left));
                    pos->push_back(static_cast<float>(top));
                    pos->push_back(static_cast<float>(cursor.z));
                    uvs->push_back(it->second.uv_left);
                    uvs->push_back(it->second.uv_top);

                    pos->push_back(static_cast<float>(right));
                    pos->push_back(static_cast<float>(top));
                    pos->push_back(static_cast<float>(cursor.z));
                    uvs->push_back(it->second.uv_right);
                    uvs->push_back(it->second.uv_top);

                    pos->push_back(static_cast<float>(right));
                    pos->push_back(static_cast<float>(bottom));
                    pos->push_back(static_cast<float>(cursor.z));
                    uvs->push_back(it->second.uv_right);
                    uvs->push_back(it->second.uv_bottom);

                    idxs->push_back(idx + 0);
                    idxs->push_back(idx + 1);
                    idxs->push_back(idx + 2);

                    idxs->push_back(idx + 0);
                    idxs->push_back(idx + 2);
                    idxs->push_back(idx + 3);
                }

                cursor.line_add_count++;
                cursor.string_add_count++;
            }

            // current line metrics
            cursor.line_min_x = std::min(cursor.line_min_x, left);
            cursor.line_max_x = std::max(cursor.line_max_x, right);
            cursor.line_min_y = std::min(cursor.line_min_y, bottom);
            cursor.line_max_y = std::max(cursor.line_max_y, top);

            // advance info
            cursor.kerning_cp = it->first;
            cursor.advance = it->second.advance;

            // change in underline?
            if (cursor.underlining != opts.underline) {

                if (!cursor.underlining) {
                    cursor.underline_begin_x = cursor.x;
                }
                else if (is_building) {
                    finishDecor_(cursor, *decor_pos, opts, false, true);
                }

                cursor.underlining = opts.underline;
            }

            // change in strokethrough?
            if (cursor.striking != opts.strikethrough) {

                if (!cursor.striking) {
                    cursor.strike_begin_x = cursor.x;
                }
                else if (is_building) {
                    finishDecor_(cursor, *decor_pos, opts, true, false);
                }

                cursor.striking = opts.strikethrough;
            }
        }

        --num_codepoints;
        ++num_processed;
    }

    return num_processed;
}

void GlyphAtlas::finishDecor_(GlyphCursor& cursor, std::vector<float>& decor_pos,
    const GlyphBuffersOpts& opts, bool finish_strike, bool finish_underline) NOTHROWS {

    // end underline and strike
    if (cursor.underlining && finish_underline) {
        
        double underline_height = underline_thickness_ * opts.point_size;

        addLineDecor_(cursor, decor_pos, cursor.underline_begin_x, cursor.y - underline_height,
                cursor.z, cursor.line_max_x - cursor.underline_begin_x - cursor.advance, underline_height);
    }
    if (cursor.striking && finish_strike) {

        double strike_height = 0.06 * opts.point_size;
        double y = (cursor.line_max_y + cursor.line_min_y - strike_height) / 2.0;

        addLineDecor_(cursor, decor_pos, cursor.strike_begin_x, y, cursor.z,
                      cursor.line_max_x - cursor.strike_begin_x - cursor.advance, strike_height);
    }
}

bool GlyphAtlas::supportsCodepoint(uint32_t codepoint) NOTHROWS {
    return glyphs_.find(codepoint) != glyphs_.end();
}

void GlyphAtlas::alignLine_(std::vector<float>* pos,
    std::vector<float>* uvs,
    std::vector<float>* decor_pos,
    GlyphCursor& cursor,
    const GlyphBuffersOpts& opts) NOTHROWS {

    // x offset to align as desired
    float align_offset = 0.f;
    auto line_width = static_cast<float>(cursor.line_max_x - cursor.line_min_x);
    switch (opts.h_alignment) {
    case GlyphHAlignment_Left: align_offset = static_cast<float>(cursor.line_min_x); break;
    case GlyphHAlignment_Center: align_offset = static_cast<float>(cursor.line_min_x + (line_width / 2.0)); break;
    case GlyphHAlignment_Right: align_offset = static_cast<float>(cursor.line_min_x + line_width); break;
    }

    if (pos && uvs) {
        const size_t stride = getStride_(*pos, *uvs);

        size_t glyph_step = stride * 4;

        for (size_t c = cursor.line_add_count, i = pos->size() - glyph_step; c > 0; --c, i -= glyph_step) {
            (*pos)[i] -= align_offset;
            (*pos)[i + stride] -= align_offset;
            (*pos)[i + 2 * stride] -= align_offset;
            (*pos)[i + 3 * stride] -= align_offset;
        }
    }

    if (decor_pos) {
        size_t glyph_step = 5;

        for (size_t c = cursor.line_decor_add_count, i = decor_pos->size() - glyph_step; c > 0; --c, i -= glyph_step) {
            (*decor_pos)[i] -= align_offset;
        }
    }

    // adjust min/max x
    cursor.line_min_x -= align_offset;
    cursor.line_max_x -= align_offset;

    // update string metrics
    cursor.string_min_x = std::min(cursor.line_min_x, cursor.string_min_x);
    cursor.string_max_x = std::max(cursor.line_max_x, cursor.string_max_x);
    cursor.string_min_y = std::min(cursor.line_min_y, cursor.string_min_y);
    cursor.string_max_y = std::max(cursor.line_max_y, cursor.string_max_y);
}

size_t GlyphAtlas::getStride_(const std::vector<float>& pos,
    const std::vector<float>& uvs) NOTHROWS {
    size_t stride = 3;
    // uvs buffer same as pos buffer?
    if (&uvs == &pos) {
        stride += 2;
    }
    return stride;
}

void GlyphAtlas::alignString_(std::vector<float>* pos,
    std::vector<float>* uvs,
    std::vector<float>* decor_pos,
    GlyphCursor& cursor,
    const GlyphBuffersOpts& opts) NOTHROWS {

    float align_offset = 0.f;
    auto string_width = static_cast<float>(cursor.string_max_x - cursor.string_min_x);
    switch (opts.h_alignment) {
    case GlyphHAlignment_Left: align_offset = 0.f; break;
    case GlyphHAlignment_Center: align_offset = static_cast<float>(string_width / 2.0); break;
    case GlyphHAlignment_Right: align_offset = static_cast<float>(string_width); break;
    }

    if (pos && uvs) {
        const size_t stride = getStride_(*pos, *uvs);

        size_t glyph_step = stride * 4;

        for (size_t c = cursor.string_add_count, i = pos->size() - glyph_step; c > 0; --c, i -= glyph_step) {
            (*pos)[i] += align_offset;
            (*pos)[i + stride] += align_offset;
            (*pos)[i + 2 * stride] += align_offset;
            (*pos)[i + 3 * stride] += align_offset;
        }
    }

    if (decor_pos) {
        size_t glyph_step = 5;

        for (size_t c = cursor.string_decor_add_count, i = decor_pos->size() - glyph_step; c > 0; --c, i -= glyph_step) {
            (*decor_pos)[i] += align_offset;
        }
    }

    cursor.string_min_x += align_offset;
    cursor.string_max_x += align_offset;

    // update total metrics
    cursor.min_x = std::min(cursor.min_x, cursor.string_min_x);
    cursor.max_x = std::max(cursor.max_x, cursor.string_max_x);
    cursor.min_y = std::min(cursor.min_y, cursor.string_min_y);
    cursor.max_y = std::max(cursor.max_y, cursor.string_max_y);
    cursor.min_z = std::min(cursor.min_z, cursor.z);
    cursor.max_z = std::max(cursor.max_z, cursor.z);
}

void GlyphAtlas::addLineDecor_(GlyphCursor& cursor, std::vector<float>& decor_pos, double x, double y,
                               double z, double width, double height) NOTHROWS {
    decor_pos.push_back(static_cast<float>(x));
    decor_pos.push_back(static_cast<float>(y));
    decor_pos.push_back(static_cast<float>(z));
    decor_pos.push_back(static_cast<float>(width));
    decor_pos.push_back(static_cast<float>(height));

    cursor.line_decor_add_count++;
    cursor.string_decor_add_count++;
}

// XXX - consider extending this to allow multiple results.
TAKErr GetAtlasResource(AtlasResource& result, const char* URI, const char* font, const char* style, const char* type) NOTHROWS {
    TAKErr code;

    // Get font resources file
    StringBuilder fontResourcesPath;
    code = StringBuilder_combine(fontResourcesPath, URI, Platform_pathSep(), "font_resources.json");
    TE_CHECKRETURN_CODE(code)

    std::unique_ptr<DataInput2, void (*)(const DataInput2*)> fontResourcesInput(nullptr, nullptr);
    code = IO_openFileV(fontResourcesInput, fontResourcesPath.c_str());
    TE_CHECKRETURN_CODE(code)

    json fontResourcesJson;
    code = parseJSON(&fontResourcesJson, fontResourcesInput.get());
    TE_CHECKRETURN_CODE(code)

    auto atlasesJson = fontResourcesJson.find("atlases");
    if (atlasesJson != fontResourcesJson.end() && atlasesJson->is_array()) {
        for (size_t i = 0; i < atlasesJson->size(); i++) {
            json atlasJson = (*atlasesJson)[i];
            if (atlasJson["fontName"] == font && atlasJson["style"] == style && atlasJson["type"] == type) {
                result.fontName = atlasJson["fontName"].get<std::string>().c_str();
                result.style = atlasJson["style"].get<std::string>().c_str();
                result.type = atlasJson["type"].get<std::string>().c_str();
                result.pngFilename = atlasJson["pngFilename"].get<std::string>().c_str();
                result.jsonFilename = atlasJson["jsonFilename"].get<std::string>().c_str();
                return TE_Ok;
            }
        }
    }
    return TE_Unsupported;
}

TAKErr TAK::Engine::Renderer::Core::GlyphAtlas_open(std::shared_ptr<GlyphAtlas>& result, const char* URI, const char* font,
    const char* style, const char* type, const GlyphAtlasOpts* opts) NOTHROWS {

    TAKErr code;

    std::shared_ptr<GlyphAtlas> atlas = std::make_shared<GlyphAtlas>();

    AtlasResource atlasResource;
    code = GetAtlasResource(atlasResource, URI, font, style, type);
    if (code == TE_Unsupported)
        return code;
    TE_CHECKRETURN_CODE(code)

    StringBuilder jsonPath;
    code = StringBuilder_combine(jsonPath, URI,
                                 Platform_pathSep(), atlasResource.jsonFilename);
    TE_CHECKRETURN_CODE(code)

    std::unique_ptr<DataInput2, void(*)(const DataInput2*)> jsonInput(nullptr, nullptr);
    code = IO_openFileV(jsonInput, jsonPath.c_str());
    TE_CHECKRETURN_CODE(code)

    json atlasJson;
    code = parseJSON(&atlasJson, jsonInput.get());
    TE_CHECKRETURN_CODE(code)

    bool bottomY = false;

    atlas->fontName = font;
    atlas->style = style;

    /*
    not included--
    "distanceRange":16,
    */
    auto atl = atlasJson.find("atlas");
    if (atl != atlasJson.end() && atl->is_object()) {
        auto val = atl->find("size");
        if (val != atl->end() && val->is_number()) {
            atlas->atlas_size_ = val->get<double>();
        }
        val = atl->find("width");
        if (val != atl->end() && val->is_number_integer()) {
            atlas->atlas_width_ = val->get<int>();
        }
        val = atl->find("height");
        if (val != atl->end() && val->is_number_integer()) {
            atlas->atlas_height_ = val->get<int>();
        }
        val = atl->find("yOrigin");
        if (val != atl->end() && val->is_string()) {
            std::string yOrigin = val->get<std::string>();
            if (yOrigin == "bottom")
                bottomY = true;
        }
        val = atl->find("type");
        if (val != atl->end() && val->is_string()) {
            std::string type = val->get<std::string>();
            if (type == "sdf")
                atlas->method_ = GlyphRenderMethod_SDF;
            else if (type == "msdf")
                atlas->method_ = GlyphRenderMethod_MSDF;
            else
                return TE_Err;
        }
    }

    /*
    not included--
    "emSize":1,
    */
    auto metrics = atlasJson.find("metrics");
    if (metrics != atlasJson.end() && metrics->is_object()) {
        auto val = metrics->find("lineHeight");
        if (val != metrics->end() && val->is_number()) {
            atlas->line_height_ = val->get<double>();
        }
        val = metrics->find("ascender");
        if (val != metrics->end() && val->is_number()) {
            atlas->ascender_ = val->get<double>();
        }
        val = metrics->find("descender");
        if (val != metrics->end() && val->is_number()) {
            atlas->descender_ = val->get<double>();
        }
        val = metrics->find("underlineThickness");
        if (val != metrics->end() && val->is_number()) {
            atlas->underline_thickness_ = val->get<double>();
        }
    }

    auto glyphs = atlasJson.find("glyphs");
    if (glyphs != atlasJson.end() && glyphs->is_array()) {

        auto width = static_cast<double>(atlas->atlas_width_);
        auto height = static_cast<double>(atlas->atlas_height_);

        if (opts && opts->power_of_2_scaled_uvs) {
            if (!MathUtils_isPowerOf2(atlas->atlas_width_))
                width = static_cast<double>(1 << MathUtils_nextPowerOf2(atlas->atlas_width_));
            if (!MathUtils_isPowerOf2(atlas->atlas_height_))
                height = static_cast<double>(1 << MathUtils_nextPowerOf2(atlas->atlas_height_));
        }

        for (size_t i = 0; i < glyphs->size(); ++i) {
            json glyph = (*glyphs)[i];
            GlyphAtlas::Glyph_ parsedGlyph;
            uint32_t uni = 0;

            auto val = glyph.find("unicode");
            if (val != glyph.end() && val->is_number_integer()) {
                uni = val->get<uint32_t>();
            }

            val = glyph.find("advance");
            if (val != glyph.end() && val->is_number()) {
                parsedGlyph.advance = val->get<double>();
            }

            auto atlasBounds = glyph.find("atlasBounds");
            if (atlasBounds != glyph.end() && atlasBounds->is_object()) {
                val = atlasBounds->find("left");
                if (val != atlasBounds->end() && val->is_number()) {
                    parsedGlyph.uv_left = static_cast<float>(val->get<double>() / width);
                }
                val = atlasBounds->find("right");
                if (val != atlasBounds->end() && val->is_number()) {
                    parsedGlyph.uv_right = static_cast<float>(val->get<double>() / width);
                }
                val = atlasBounds->find("bottom");
                if (val != atlasBounds->end() && val->is_number()) {
                    if (bottomY)
                        parsedGlyph.uv_bottom = static_cast<float>((atlas->atlas_height_ - val->get<double>()) / height);
                    else
                        parsedGlyph.uv_bottom = static_cast<float>(val->get<double>() / height);
                }
                val = atlasBounds->find("top");
                if (val != atlasBounds->end() && val->is_number()) {
                    if (bottomY)
                        parsedGlyph.uv_top = static_cast<float>((atlas->atlas_height_ - val->get<double>()) / height);
                    else
                      parsedGlyph.uv_top = static_cast<float>(val->get<double>() / height);
                }
            }

            parsedGlyph.has_glyph_bounds = false;

            auto planeBounds = glyph.find("planeBounds");
            if (planeBounds != glyph.end() && atlasBounds->is_object()) {

                parsedGlyph.has_glyph_bounds = true;

                val = planeBounds->find("left");
                if (val != planeBounds->end() && val->is_number()) {
                    parsedGlyph.left = val->get<double>();
                } else {
                    parsedGlyph.has_glyph_bounds = false;
                }
                val = planeBounds->find("right");
                if (val != planeBounds->end() && val->is_number()) {
                    parsedGlyph.right = val->get<double>();
                } else {
                    parsedGlyph.has_glyph_bounds = false;
                }
                val = planeBounds->find("bottom");
                if (val != planeBounds->end() && val->is_number()) {
                    parsedGlyph.bottom = val->get<double>();
                } else {
                    parsedGlyph.has_glyph_bounds = false;
                }
                val = planeBounds->find("top");
                if (val != planeBounds->end() && val->is_number()) {
                    parsedGlyph.top = val->get<double>();
                } else {
                    parsedGlyph.has_glyph_bounds = false;
                }
            }

            atlas->glyphs_.insert(std::pair<uint32_t, GlyphAtlas::Glyph_>(uni, parsedGlyph));
        }

        auto kerning = atlasJson.find("kerning");
        if (kerning != atlasJson.end() && kerning->is_array()) {

            for (size_t i = 0; i < kerning->size(); ++i) {
                json glyphKerning = (*kerning)[i];

                uint32_t uni1 = 0;
                uint32_t uni2 = 0;
                double adv = 0.0;

                auto val = glyphKerning.find("unicode1");
                if (val != glyphKerning.end() && val->is_number_integer()) {
                    uni1 = val->get<uint32_t>();
                }

                val = glyphKerning.find("unicode2");
                if (val != glyphKerning.end() && val->is_number_integer()) {
                    uni2 = val->get<uint32_t>();
                }

                val = glyphKerning.find("advance");
                if (val != glyphKerning.end() && val->is_number()) {
                    adv = val->get<double>();
                }

                auto it = atlas->glyphs_.find(uni1);
                if (it != atlas->glyphs_.end()) {
                    it->second.kerning.push_back(std::pair<uint32_t, double>(uni2, adv));
                }
            }
        }
    }
    
    result = atlas;
    return TE_Ok;
}

TAKErr GlyphAtlas::getKerningAdvance(double* result, uint32_t codepoint, uint32_t next_codepoint) NOTHROWS {
    auto it = this->glyphs_.find(codepoint);
    if (it != this->glyphs_.end()) {
        for (const auto& kp : it->second.kerning) {
            if (kp.first == next_codepoint) {
                *result = kp.second;
                return TE_Ok;
            }
        }
    }

    *result = 0.0;
    return TE_Unsupported;
}


TAKErr TAK::Engine::Renderer::Core::GlyphAtlas_loadBitmap(BitmapPtr &result, const std::shared_ptr<GlyphAtlas> &atlas,
    const char *URI, const char *font, const char* style, const char *type, const GlyphAtlasOpts *opts) NOTHROWS {

    TAKErr code;

    AtlasResource atlasResource;
    code = GetAtlasResource(atlasResource, URI, font, style, type);
    TE_CHECKRETURN_CODE(code)

    StringBuilder pngPath;
    code = StringBuilder_combine(pngPath, URI, Platform_pathSep(), atlasResource.pngFilename);
    TE_CHECKRETURN_CODE(code)

    std::unique_ptr<DataInput2, void(*)(const DataInput2*)> bitmapInput(nullptr, nullptr);
    code = IO_openFileV(bitmapInput, pngPath.c_str());
    TE_CHECKRETURN_CODE(code)

    return BitmapFactory2_decode(result, *bitmapInput, nullptr);
}

namespace {

    bool isSkipControlChar(int cp) NOTHROWS {
        switch (cp) {
        case '\r':
            //TODO-- add more, bell, etc.
            return true;
        }

        return false;
    }

    bool isNewLineControlChar(int cp) NOTHROWS {
        return cp == '\n';
    }

    //
    //XXX-- pulled from B3DM.cpp (we should make this common, shared)
    // {
    //

    // Adapt to DataInput2 to std::streambuf
    class DataInput2Streambuf : public std::streambuf {
    public:
        DataInput2Streambuf(TAK::Engine::Util::DataInput2* input);

        int_type underflow() override;

    private:
        TAK::Engine::Util::DataInput2* input;
        char curr;
    };

    DataInput2Streambuf::DataInput2Streambuf(TAK::Engine::Util::DataInput2* input)
        : input(input),
        curr(std::char_traits<char>::eof()) {}

    DataInput2Streambuf::int_type DataInput2Streambuf::underflow() {
        size_t nr = 0;
        char ch;
        input->read((uint8_t*)&ch, &nr, 1);
        if (nr == 1) {
            curr = ch;
            setg(&curr, &curr, &curr + 1);
            return std::char_traits<char>::to_int_type(static_cast<char>(curr));
        }
        return std::char_traits<char>::eof();
    }
    
    TAKErr parseJSON(json* result, DataInput2* input) NOTHROWS {
        DataInput2Streambuf buf(input);
        std::istream in(&buf);
        *result = json::parse(in, nullptr, false);
        if (result->is_discarded())
            return TE_Err;
        return TE_Ok;
    }

    //
    // }
    //
}
