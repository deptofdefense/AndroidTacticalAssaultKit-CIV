#include "renderer/core/GLLabelManager.h"

#include "core/Ellipsoid.h"
#include "renderer/GLES20FixedPipeline.h"
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
GLText2* defaultText(nullptr);
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
      visible_(true) {
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

    if (this->batch_.get() == nullptr) {
        this->batch_.reset(new GLRenderBatch2(0xFFFF));
    }

    bool depthRestore = false;
    if (view.renderPass->drawTilt == 0) {
        depthRestore = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);
    }

    bool didAnimate = false;
    try {
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
                label.validate(view, *gltext);
                for(auto &placement : label.transformed_anchor_) {
                    bool didReplace;
                    if(label.place(placement, view, *gltext, label_placements, didReplace))
                        label_placements.push_back(placement);
                }
                label.batch(view, *gltext, *(batch_.get()), render_pass);
                renderedLabels.push_back(always_render_idx_);
            }
        }
        draw(view, Priority::TEP_High, label_placements, render_pass, renderedLabels);
        draw(view, Priority::TEP_Standard, label_placements, render_pass, renderedLabels);
        draw(view, Priority::TEP_Low, label_placements, render_pass, renderedLabels);
        batch_->end();

        glDisable(GL_DEPTH_TEST);
        batch_->begin();
        {
            float mx[16];
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, mx);
            this->batch_->setMatrix(GL_PROJECTION, mx);
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, mx);
            this->batch_->setMatrix(GL_MODELVIEW, mx);
        }
        for(uint32_t& label_id : renderedLabels)
        {
            GLLabel& label = labels_[label_id];
            if(label.hints_ & GLLabel::XRay) {
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

        label.validate(view, *gltext);

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
                if(label.place(placement, view, *gltext, label_placements, didReplace))
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
            label.batch(view, *gltext, *(batch_.get()), render_pass);
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
        for(auto &placement : label.transformed_anchor_) {
            bool didReplace;
            if(label.place(placement, view, *gltext, label_placements, didReplace)) {
                label_placements.push_back(placement);
            }
        }
        label.batch(view, *gltext, *(batch_.get()), render_pass);
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
