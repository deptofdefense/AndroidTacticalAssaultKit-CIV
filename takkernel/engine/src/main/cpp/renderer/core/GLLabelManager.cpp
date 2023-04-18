#include "renderer/core/GLLabelManager.h"

#include "core/Ellipsoid.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/core/GLMapView2.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

namespace {
TAK::Engine::Port::String defaultFontName;
GLText2* defaultText(nullptr);
std::unique_ptr<TextFormatParams> defaultTextFormatParams(nullptr);

inline GlyphHAlignment toGlyphHAlignment(TextAlignment a) {
    switch (a) {
        case TextAlignment::TETA_Left: return GlyphHAlignment_Left;
        default:
        case TextAlignment::TETA_Center: return GlyphHAlignment_Center;
        case TextAlignment::TETA_Right: return GlyphHAlignment_Right;
    }
}

class AtlasTextFormat : public TextFormat2 {
public:
    ~AtlasTextFormat() override;
    float getStringWidth(const char* text) NOTHROWS override;
    float getCharPositionWidth(const char* text, int position) NOTHROWS override;
    float getCharWidth(const unsigned int chr) NOTHROWS override;
    float getCharHeight() NOTHROWS override;
    float getDescent() NOTHROWS override;
    float getStringHeight(const char* text) NOTHROWS override;
    float getBaselineSpacing() NOTHROWS override;
    int getFontSize() NOTHROWS override;
    TAK::Engine::Util::TAKErr loadGlyph(BitmapPtr& value, const unsigned int c) NOTHROWS override;
    GLGlyphBatchFactory* ftor_{nullptr};
    GLText2* gltext_{nullptr};
    double font_size{0.0};
    GlyphBuffersOpts opts;
};

inline double defaultGlyphPointSize() NOTHROWS {
#ifdef __ANDROID__
    return static_cast<double>(GLMapRenderGlobals_getRelativeDisplayDensity());
#else
    return 1.4;
#endif
}

}  // namespace

float GLLabelManager::defaultFontSize = 0;

GLLabelManager::GLLabelManager()
    : labelRotation(0.0),
      absoluteLabelRotation(false),
      labelFadeTimer(-1LL),
      map_idx_(0),
      always_render_idx_(NO_ID),
      draw_version_(-1),
      replace_labels_(true),
      visible_(true),
      glyph_batch_factory_(nullptr, nullptr) {
    label_priorities_.insert(std::make_pair(Priority::TEP_High, std::set<uint32_t>()));
    label_priorities_.insert(std::make_pair(Priority::TEP_Standard, std::set<uint32_t>()));
    label_priorities_.insert(std::make_pair(Priority::TEP_Low, std::set<uint32_t>()));
}

GLLabelManager::~GLLabelManager() { this->stop(); }

void GLLabelManager::resetFont() NOTHROWS {
    Lock lock(mutex_);

    draw_version_ = -1;
    defaultFontSize = 0;
    defaultText = nullptr;
    defaultTextFormatParams = nullptr;
    defaultFontName = "";
    loadedFonts_.clear();
    invalidFonts_.clear();
    glyph_batch_factory_.reset();
}

uint32_t GLLabelManager::addLabel(GLLabel& label) NOTHROWS {
    Lock lock(mutex_);

    draw_version_ = -1;

    replace_labels_ = true;
    label_priorities_[label.priority_].insert(map_idx_);
    labels_[map_idx_] = std::move(label);
    return map_idx_++;
}

void GLLabelManager::removeLabel(const uint32_t id) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    label_priorities_[labels_[id].priority_].erase(id);
    labels_.erase(id);

    if (always_render_idx_ == id) always_render_idx_ = NO_ID;
}

void GLLabelManager::setGeometry(const uint32_t id, const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setGeometry(geometry);
}

void GLLabelManager::setAltitudeMode(const uint32_t id, const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setAltitudeMode(altitude_mode);
}

void GLLabelManager::setText(const uint32_t id, TAK::Engine::Port::String text) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setText(text);
}

void GLLabelManager::setTextFormat(const uint32_t id, const TextFormatParams* fmt) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;

    // RWI - If it's the default font just reset to null
    if (fmt == nullptr ||
        (fmt->size == defaultFontSize && fmt->fontName == nullptr && !fmt->bold && !fmt->italic && !fmt->underline && !fmt->strikethrough))
        labels_[id].setTextFormat(nullptr);
    else {
        labels_[id].setTextFormat(fmt);
    }
}

void GLLabelManager::setVisible(const uint32_t id, bool visible) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setVisible(visible);
}

void GLLabelManager::setAlwaysRender(const uint32_t id, bool always_render) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setAlwaysRender(always_render);
    if (always_render)
        always_render_idx_ = id;
    else if (always_render_idx_ == id)
        always_render_idx_ = NO_ID;
}

void GLLabelManager::setMaxDrawResolution(const uint32_t id, double max_draw_resolution) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    replace_labels_ = true;
    labels_[id].setMaxDrawResolution(max_draw_resolution);
}

void GLLabelManager::setAlignment(const uint32_t id, TextAlignment alignment) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setAlignment(alignment);
}

void GLLabelManager::setVerticalAlignment(const uint32_t id, VerticalAlignment vertical_alignment) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setVerticalAlignment(vertical_alignment);
}

void GLLabelManager::setDesiredOffset(const uint32_t id, const TAK::Engine::Math::Point2<double>& desired_offset) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setDesiredOffset(desired_offset);
}

void GLLabelManager::setColor(const uint32_t id, int color) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setColor(color);
}

void GLLabelManager::setBackColor(const uint32_t id, int color) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setBackColor(color);
}

void GLLabelManager::setFill(const uint32_t id, bool fill) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setFill(fill);
}

void GLLabelManager::setRotation(const uint32_t id, const float rotation, const bool absolute) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setRotation(rotation, absolute);
}

void GLLabelManager::getSize(const uint32_t id, atakmap::math::Rectangle<double>& size_rect) NOTHROWS {
    Lock lock(mutex_);

    if (map_idx_ <= id) return;

    GLLabel& label = labels_[id];
    size_rect.width = label.labelSize.width;
    size_rect.height = label.labelSize.height;

    if (!label.transformed_anchor_.empty()) {
        size_rect.x = label.transformed_anchor_[0].render_xyz_.x;
        size_rect.y = label.transformed_anchor_[0].render_xyz_.y;
    }

    if (size_rect.width == 0 && size_rect.height == 0) {
        GLText2* gltext = label.gltext_;
        if (!gltext)
            gltext = getDefaultText();
        if (gltext) {
            size_rect.width = gltext->getTextFormat().getStringWidth(label.text_.c_str());
            size_rect.height = gltext->getTextFormat().getStringHeight(label.text_.c_str());
        }
    }
}

void GLLabelManager::setPriority(const uint32_t id, const Priority priority) NOTHROWS {
    Lock lock(mutex_);

    for (auto it = label_priorities_.begin(); it != label_priorities_.end(); it++) {
        if (it->first == priority) continue;

        if (it->second.find(id) != it->second.end()) {
            it->second.erase(id);
        }
    }
    label_priorities_[priority].insert(id);

    labels_[id].setPriority(priority);
}

void GLLabelManager::setHints(const uint32_t id, const unsigned int hints) NOTHROWS
{
    if (map_idx_ <= id) return;

    draw_version_ = -1;

    labels_[id].setHints(hints);
}

unsigned int GLLabelManager::getHints(const uint32_t id) NOTHROWS
{
    if (map_idx_ <= id) return 0u;

    const auto label = labels_.find(id);
    if(label == labels_.end())
        return 0u;

    return label->second.hints_;
}

void GLLabelManager::setVisible(bool visible) NOTHROWS {
    Lock lock(mutex_);

    visible_ = visible;
}

void GLLabelManager::draw(const GLGlobeBase& view, const int render_pass) NOTHROWS {
    Lock lock(mutex_);

    if (labels_.empty() || !visible_) return;

    defaultText = getDefaultText();

    if (!this->batch_) {
        this->batch_.reset(new GLRenderBatch2(0xFFFF));
    }

    bool depthRestore = false;
    if (view.renderPass->drawTilt == 0) {
        depthRestore = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);
    }

    bool didAnimate = false;
    try {
        // Make sure all font atlases are preloaded in anticipation of rendering them
        GLGlyphBatchFactory* factory = defaultGlyphBatchFactory();
        for (const auto& it : labels_) {
            auto label = it.second;
            auto opts = toGlyphBuffersOpts(&label, false);
            loadAtlas(*factory, opts.fontName, opts.style, "msdf");
        }

        if (!glyph_batch_) {
            glyph_batch_.reset(new GLGlyphBatch());
        }
        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
        GLES20FixedPipeline::getInstance()->glPushMatrix();

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glOrthof((float)view.renderPass->left, (float)view.renderPass->right, (float)view.renderPass->bottom, (float)view.renderPass->top,
                                                     (float)view.renderPass->near, (float)view.renderPass->far);

        batch_->begin();
        {
            float mx[16];
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, mx);
            this->batch_->setMatrix(GL_PROJECTION, mx);
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, mx);
            this->batch_->setMatrix(GL_MODELVIEW, mx);
        }

        std::vector<GLLabel::LabelPlacement> label_placements;
        std::vector<uint32_t> renderedLabels;

        if (draw_version_ != view.drawVersion) {
            draw_version_ = view.drawVersion;
            replace_labels_ = true;
        }
        if (always_render_idx_ != NO_ID) {
            GLLabel& label = labels_[always_render_idx_];

            const Feature::Geometry2* geometry = label.getGeometry();
            if (geometry != nullptr) {
                GLText2 *gltext = label.gltext_;
                if (!gltext)
                    gltext = defaultText;

                AtlasTextFormat tf;
                tf.ftor_ = factory;
                tf.gltext_ = gltext;
                tf.font_size = gltext->getTextFormat().getFontSize();
                tf.opts = toGlyphBuffersOpts(&label);
                TextFormat2& rtf = shouldUseFallbackMethod(label) ?
                    gltext->getTextFormat() : tf;

                label.validate(view, rtf);
                for(auto &placement : label.transformed_anchor_) {
                    bool didReplace;
                    if(label.place(placement, view, tf, label_placements, didReplace))
                        label_placements.push_back(placement);
                }
                bool fallback_method = shouldUseFallbackMethod(label);
                if (fallback_method) {
                    label.batch(view, *gltext, *(batch_.get()), render_pass);
                } else {
                    batchGlyphs(view, *glyph_batch_, label);
                }
                renderedLabels.push_back(always_render_idx_);
            }
        }
        draw(view, Priority::TEP_High, label_placements, render_pass, renderedLabels);
        draw(view, Priority::TEP_Standard, label_placements, render_pass, renderedLabels);
        draw(view, Priority::TEP_Low, label_placements, render_pass, renderedLabels);
        batch_->end();

        //
        // GLGlyphBatch rendering
        //

        if (view.renderPass->drawTilt == 0) {
            depthRestore = glIsEnabled(GL_DEPTH_TEST);
            glDisable(GL_DEPTH_TEST);
        }

        // Draw underground portion
        if (view.renderPass->drawTilt > 0) {
            int depthFunc;
            glGetIntegerv(GL_DEPTH_FUNC, &depthFunc);
            glDepthMask(GL_FALSE);
            glDepthFunc(GL_GEQUAL);
            drawBatchedGlyphs(view, true);
            glDepthFunc(depthFunc);
            glDepthMask(GL_TRUE);
        }

        drawBatchedGlyphs(view, false);

        this->glyph_batch_->clearBatchedGlyphs();

        // Check to see if we have any legacy drawing requiring XRay rendering.
        bool legacyDrawing = false;
        for(uint32_t& label_id : renderedLabels) {
            GLLabel &label = labels_[label_id];
            if (shouldUseFallbackMethod(label) && label.hints_ & GLLabel::XRay) {
                legacyDrawing = true;
                break;
            }
        }
        // If so, draw XRay as necessary
        if (legacyDrawing) {
            glDisable(GL_DEPTH_TEST);
            batch_->begin();
            {
                float mx[16];
                GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, mx);
                this->batch_->setMatrix(GL_PROJECTION, mx);
                GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, mx);
                this->batch_->setMatrix(GL_MODELVIEW, mx);
            }
            for (uint32_t &label_id : renderedLabels) {
                GLLabel &label = labels_[label_id];
                if (shouldUseFallbackMethod(label) && label.hints_ & GLLabel::XRay) {
                    GLText2 *gltext = label.gltext_;
                    if (!gltext)
                        gltext = defaultText;
                    label.batch(view, *gltext, *batch_, GLGlobeBase::XRay);
                }

                // mark if any animated
                didAnimate |= label.did_animate_;
            }
            batch_->end();
            glEnable(GL_DEPTH_TEST);
        }

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        GLES20FixedPipeline::getInstance()->glPopMatrix();

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
        GLES20FixedPipeline::getInstance()->glPopMatrix();
    } catch (...) {
        // ignored
    }

    if (depthRestore)
        glEnable(GL_DEPTH_TEST);

    replace_labels_ = false;

    // if an animation was performed, queue up a refresh
    if(didAnimate)
        view.context.requestRefresh();
}

void GLLabelManager::draw(const GLGlobeBase& view, const Priority priority,
                          std::vector<GLLabel::LabelPlacement>& label_placements, int render_pass, std::vector<uint32_t> &renderedLabels ) NOTHROWS {
    auto ids_it = label_priorities_.find(priority);
    if (ids_it == label_priorities_.end()) return;
    auto &ids = ids_it->second;

    //first render all GLLabels that do not need replacing,
    // store GLLabels that need to be replaced for later placement
    std::vector<uint32_t > replaced_labels;
    for (auto it = ids.begin(); it != ids.end(); it++) {
        const uint32_t label_id = *it;
        if (label_id == always_render_idx_) continue;

        auto label_it = labels_.find(label_id);
        if (label_it == labels_.end()) continue;

        GLLabel& label = label_it->second;

        if (label.text_.empty()) continue;

        if (!label.shouldRenderAtResolution(view.renderPass->drawMapResolution)) continue;

        GLText2* gltext = label.gltext_;
        if (!gltext) gltext = defaultText;

        AtlasTextFormat tf;
        tf.ftor_ = defaultGlyphBatchFactory();
        tf.gltext_ = gltext;
        tf.font_size = gltext->getTextFormat().getFontSize();
        tf.opts = toGlyphBuffersOpts(&label);
        TextFormat2& rtf = shouldUseFallbackMethod(label) ?
            gltext->getTextFormat() : tf;

        label.validate(view, rtf);

        bool didReplace = false;
        bool canDraw = false;

        std::vector<GLLabel::LabelPlacement> temporaryPlacements;
        for(auto &placement : label.transformed_anchor_)
        {
            Point2<double> xyz(placement.anchor_xyz_);

            // skip points outside of the near and far clip planes
            if(placement.anchor_xyz_.z > 1.f) continue;

            // confirm location is in view
            if (!atakmap::math::Rectangle<double>::contains(view.renderPass->left, view.renderPass->bottom, view.renderPass->right, view.renderPass->top, xyz.x, xyz.y)) continue;

            canDraw = true;

            if (replace_labels_)
            {
                if(label.place(placement, view, tf, label_placements, didReplace))
                {
                    if(!didReplace)
                    {
                        temporaryPlacements.push_back(placement);
                    }
                    else
                    {
                        replaced_labels.push_back(label_id);
                        break;
                    }
                }
            }
        }

        if(!didReplace && canDraw)
        {
            label_placements.insert(label_placements.end(), temporaryPlacements.begin(), temporaryPlacements.end());
            bool fallback_method = shouldUseFallbackMethod(label);
            if (fallback_method) {
                label.batch(view, *gltext, *(batch_.get()), render_pass);
            } else {
                batchGlyphs(view, *glyph_batch_, label);
            }
            renderedLabels.push_back(label_id);
        }

#if 0
        // debug draw label placement bounds
        for(auto &placement : label.transformed_anchor_) {
            float bnds[10u];
            bnds[0u] = placement.rotatedRectangle[0].x;
            bnds[1u] = placement.rotatedRectangle[0].y;
            bnds[2u] = placement.rotatedRectangle[1].x;
            bnds[3u] = placement.rotatedRectangle[1].y;
            bnds[4u] = placement.rotatedRectangle[2].x;
            bnds[5u] = placement.rotatedRectangle[2].y;
            bnds[6u] = placement.rotatedRectangle[3].x;
            bnds[7u] = placement.rotatedRectangle[3].y;
            bnds[8u] = placement.rotatedRectangle[0].x;
            bnds[9u] = placement.rotatedRectangle[0].y;
            batch_->setLineWidth(3.f);
            batch_->batch(0, GL_LINE_STRIP, 5u, 2u, 8u, bnds, 0u, nullptr, 1.f, 1.f, 1.f, 1.f);
        }
#endif
    }

    //rePlace and render all labels that haven't been rendered
    for(int label_id : replaced_labels)
    {
        GLLabel& label = labels_[label_id];
        GLText2* gltext = label.gltext_;
        if (!gltext) gltext = defaultText;
        AtlasTextFormat tf;
        tf.ftor_ = defaultGlyphBatchFactory();
        tf.gltext_ = gltext;
        tf.font_size = gltext->getTextFormat().getFontSize();
        tf.opts = toGlyphBuffersOpts(&label);
        TextFormat2& rtf = shouldUseFallbackMethod(label) ?
            gltext->getTextFormat() : tf;
        for(auto &placement : label.transformed_anchor_) {
            bool didReplace;
            if(label.place(placement, view, rtf, label_placements, didReplace)) {
                label_placements.push_back(placement);
            }
        }
        bool fallback_method = shouldUseFallbackMethod(label);
        if (fallback_method) {
            label.batch(view, *gltext, *(batch_.get()), render_pass);
        } else {
            batchGlyphs(view, *glyph_batch_, label);
        }
        renderedLabels.push_back(label_id);
    }

}


void GLLabelManager::release() NOTHROWS {}

int GLLabelManager::getRenderPass() NOTHROWS { return GLMapView2::Sprites; }

void GLLabelManager::start() NOTHROWS {}

void GLLabelManager::stop() NOTHROWS {
    Lock lock(mutex_);

    labels_.clear();
}
TextFormatParams* GLLabelManager::getDefaultTextFormatParams() NOTHROWS {
    if (defaultTextFormatParams == nullptr) {
        TAKErr code;
        Port::String opt;
        code = ConfigOptions_getOption(opt, "default-font-size");
        float fontSize;
        if (code == TE_Ok)
            fontSize = (float)atof(opt);
        else
            fontSize = 14.0f;
        ConfigOptions_getOption(defaultFontName, "default-font-name");
        if (code != TE_Ok)
            defaultFontName = "Arial";

        defaultTextFormatParams.reset(new TextFormatParams(defaultFontName.get(), fontSize));
    }
    return defaultTextFormatParams.get();
}

GLText2* GLLabelManager::getDefaultText() NOTHROWS {
    if (defaultText == nullptr) {
        TAKErr code;
        TextFormat2Ptr fmt(nullptr, nullptr);
        if (!defaultFontSize) {
            Port::String opt;
            code = ConfigOptions_getOption(opt, "default-font-size");
            if (code == TE_Ok)
                defaultFontSize = (float)atof(opt);
            else
                defaultFontSize = 14.0f;
        }
        code = TextFormat2_createDefaultSystemTextFormat(fmt, defaultFontSize);
        if (code == TE_Ok) {
            std::shared_ptr<TextFormat2> sharedFmt = std::move(fmt);
            defaultText = GLText2_intern(sharedFmt);
        }
    }
    return defaultText;
}

TAK::Engine::Renderer::Core::GLGlyphBatchFactory* GLLabelManager::defaultGlyphBatchFactory() NOTHROWS {
    if (glyph_batch_factory_ == nullptr) {
        glyph_batch_factory_ = std::unique_ptr<GLGlyphBatchFactory, void(*)(const GLGlyphBatchFactory *)>(new GLGlyphBatchFactory(), Memory_deleter_const<GLGlyphBatchFactory>);
        const char* fontName = getDefaultTextFormatParams()->fontName;
        loadAtlas(*glyph_batch_factory_, fontName, "regular", "msdf");
        loadAtlas(*glyph_batch_factory_, fontName, "bold", "msdf");
        loadAtlas(*glyph_batch_factory_, fontName, "italic", "msdf");
        loadAtlas(*glyph_batch_factory_, fontName, "bolditalic", "msdf");
    }

    return glyph_batch_factory_.get();
}

TAKErr GLLabelManager::loadAtlas(GLGlyphBatchFactory& factory, const char* font, const char* style, const char* type) NOTHROWS {
    TAKErr code;
    std::shared_ptr<GlyphAtlas> atlas;
    GlyphAtlasOpts opts;

    if(!font)   font = "";
    if(!style)  style = "";
    if(!type)   type = "";

    std::tuple<std::string, std::string, std::string> tuple = std::make_tuple(font, style, type);
    if (std::find(loadedFonts_.begin(), loadedFonts_.end(), tuple) != loadedFonts_.end()) {
        return TE_Ok;
    }
    if (std::find(invalidFonts_.begin(), invalidFonts_.end(), tuple) != invalidFonts_.end()) {
        return TE_Unsupported;
    }

    TAK::Engine::Port::String fontAtlasPath;
    code = ConfigOptions_getOption(fontAtlasPath, "default-font-atlas-path");
    if (code != TE_Ok) {
        return TE_Err;
    }

    // rounds atlas width/height to the next power of 2 when calculating uvs
    opts.power_of_2_scaled_uvs = true;

    code = GlyphAtlas_open(atlas, fontAtlasPath, font, style, type, &opts);
    if (code == TE_Ok && atlas) {
        TAK::Engine::Renderer::BitmapPtr bitmap(nullptr, nullptr);
        code = GlyphAtlas_loadBitmap(bitmap, atlas, fontAtlasPath, font, style, type, &opts);
        if (code == TE_Ok && bitmap) {
            std::shared_ptr<GLTexture2> tex = std::make_shared<GLTexture2>(bitmap->getWidth(), bitmap->getHeight(), bitmap->getFormat());
            code = tex->load(*bitmap);
            if (code == TE_Ok) {
                factory.addAtlas(atlas, tex);
                loadedFonts_.emplace_back(tuple);
                return TE_Ok;
            }
        }
    }

    invalidFonts_.emplace_back(tuple);
    return TE_Unsupported;
}

void GLLabelManager::batchGlyphs(const GLGlobeBase& view, GLGlyphBatch& glyphBatch, GLLabel& label) NOTHROWS {
    if (label.text_.empty())
        return;
    for (const auto& a : label.transformed_anchor_)
        if (a.can_draw_) batchGlyphs(view, glyphBatch, label, a);
}

void GLLabelManager::batchGlyphs(const GLGlobeBase& view, GLGlyphBatch& glyphBatch, GLLabel& label, const GLLabel::LabelPlacement& placement) NOTHROWS {
    GLGlyphBatchFactory* factory = defaultGlyphBatchFactory();

    GlyphBuffersOpts opts = toGlyphBuffersOpts(&label);
    GLText2 *gltext = label.gltext_;
    if (!gltext) gltext = defaultText;

    // placement is determined by GLLabel
    opts.renderX = static_cast<float>(placement.render_xyz_.x);
    opts.renderY = static_cast<float>(placement.render_xyz_.y);
    opts.renderZ = static_cast<float>(placement.render_xyz_.z);
    opts.anchorX = static_cast<float>(placement.anchor_xyz_.x);
    opts.anchorY = static_cast<float>(placement.anchor_xyz_.y);
    opts.anchorZ = static_cast<float>(placement.anchor_xyz_.z);

    double rotate = placement.rotation_.angle_;
    if (placement.rotation_.absolute_)
        rotate = fmod(rotate + view.renderPass->drawRotation, 360.0);
    opts.rotation = static_cast<float>(atakmap::math::toRadians(rotate));

    if (label.textFormatParams_ && label.textFormatParams_->size != 0)
        opts.font_size = label.textFormatParams_->size;
    else
        opts.font_size = getDefaultTextFormatParams()->size;

    factory->batch(*glyph_batch_, label.text_.c_str(), opts, *gltext);
}

GlyphBuffersOpts GLLabelManager::toGlyphBuffersOpts(GLLabel* label, bool adjustFontNameIfInvalid) NOTHROWS {
    GlyphBuffersOpts opts;

    auto textFormatParams = label->textFormatParams_ ? label->textFormatParams_.get() : getDefaultTextFormatParams();
    std::string fontName = textFormatParams->fontName ? textFormatParams->fontName.get() : "";
    if (fontName.empty()) {
        fontName = getDefaultTextFormatParams()->fontName;
    }

    std::string style = "regular";
    if (textFormatParams->bold && textFormatParams->italic) {
        style = "bolditalic";
    } else if (textFormatParams->bold) {
        style = "bold";
    } else if (textFormatParams->italic) {
        style = "italic";
    }

    if (adjustFontNameIfInvalid) {
        std::tuple<std::string, std::string, std::string> tuple = std::make_tuple(fontName, style, "msdf");
        // If we can't load a particular font, then let's fallback to the default.
        if (std::find(invalidFonts_.begin(), invalidFonts_.end(), tuple) != invalidFonts_.end()) {
            fontName = getDefaultTextFormatParams()->fontName;
        }
    }

    opts.fontName = fontName.c_str();
    opts.style = style.c_str();
    opts.underline = textFormatParams->underline;
    opts.strikethrough = textFormatParams->strikethrough;
    opts.fill = label->fill_;
    opts.outline_weight = label->fill_ ? 0.f : 2.f;
    opts.text_color_red = label->color_r_;
    opts.text_color_green = label->color_g_;
    opts.text_color_blue = label->color_b_;
    opts.text_color_alpha = label->color_a_;
    opts.back_color_red = label->back_color_r_;
    opts.back_color_green = label->back_color_g_;
    opts.back_color_blue = label->back_color_b_;
    opts.back_color_alpha = label->back_color_a_;
    opts.outline_color_red = 0.0f;
    opts.outline_color_blue = 0.0f;
    opts.outline_color_green = 0.0f;
    opts.outline_color_alpha = 1.0f;
    opts.h_alignment = toGlyphHAlignment(label->alignment_);
    opts.point_size = defaultGlyphPointSize();
    opts.xray_alpha = label->hints_ & GLLabel::XRay ? 0.4f : 0.0f;
    return opts;
}

void GLLabelManager::drawBatchedGlyphs(const GLGlobeBase& view, const bool xrayPass) NOTHROWS {
    float proj[16];
    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, proj);

    glyph_batch_->draw(view.getRenderContext(), xrayPass, proj);
    glyph_batch_->drawLegacy(view.getRenderContext(), xrayPass, proj);
}

bool GLLabelManager::shouldUseFallbackMethod(GLLabel& label) NOTHROWS {
#ifdef __ANDROID__
    bool scrollingMarqueeNecesssary = (label.hints_ & GLLabel::ScrollingText) != 0 && label.textSize.width > GLLabel::MAX_TEXT_WIDTH*GLMapRenderGlobals_getRelativeDisplayDensity();
    return scrollingMarqueeNecesssary || defaultGlyphBatchFactory()->atlasCount() == 0;
#else
    return defaultGlyphBatchFactory()->atlasCount() == 0;
#endif
}

namespace {
    AtlasTextFormat::~AtlasTextFormat() = default;

    float AtlasTextFormat::getStringWidth(const char* text) NOTHROWS {
        GlyphMeasurements m{};
        ftor_->measureStringBounds(&m, opts, text, *gltext_);
        return static_cast<float>((m.max_x - m.min_x) * font_size);
    }

    float AtlasTextFormat::getCharPositionWidth(const char* text, int position) NOTHROWS {
        return getCharWidth(text[position]);
    }

    float AtlasTextFormat::getCharWidth(const unsigned int chr) NOTHROWS {
        char str[] = { static_cast<char>(chr), '\0' };
        return getStringWidth(str);
    }

    float AtlasTextFormat::getCharHeight() NOTHROWS {
        return getStringHeight("A");
    }

    float AtlasTextFormat::getDescent() NOTHROWS {
        GlyphMeasurements m{};
        ftor_->measureStringBounds(&m, opts, "A", *gltext_);
        return static_cast<float>(-m.descender * font_size);
    }

    float AtlasTextFormat::getStringHeight(const char* text) NOTHROWS {
        GlyphMeasurements m{};
        ftor_->measureStringBounds(&m, opts, text, *gltext_);
        return static_cast<float>((m.max_y - m.min_y) * font_size);
    }

    float AtlasTextFormat::getBaselineSpacing() NOTHROWS {
        GlyphMeasurements m{};
        ftor_->measureStringBounds(&m, opts, "A", *gltext_);
        return static_cast<float>(m.line_height * font_size);
    }

    int AtlasTextFormat::getFontSize() NOTHROWS {
        return static_cast<int>(font_size);
    }

    TAK::Engine::Util::TAKErr AtlasTextFormat::loadGlyph(BitmapPtr& value, const unsigned int c) NOTHROWS {
        return TE_Unsupported;
    }
    
}
