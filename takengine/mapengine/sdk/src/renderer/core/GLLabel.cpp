#include "renderer/core/GLLabel.h"

#include <algorithm>
#include <cmath>

#include "feature/Geometry2.h"
#include "feature/LegacyAdapters.h"
#include "feature/LineString.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "math/Rectangle.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText2.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "util/Distance.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

#define ONE_EIGHTY_OVER_PI 57.295779513082320876798154814105

atakmap::renderer::GLNinePatch* GLLabel::small_nine_patch_ = nullptr;

static float DistanceSquared(float ax, float ay, float bx, float by) {
    float dx = ax - bx;
    float dy = ay - by;
    return dx * dx + dy * dy;
}

static bool IntersectLine(TAK::Engine::Math::Point2<float>& pIntersection, float a1x, float a1y, float a2x, float a2y, float b1x, float b1y,
                          float b2x, float b2y) {
    // code from
    // http://csharphelper.com/blog/2014/08/determine-where-two-lines-intersect-in-c/

    // Get the segments' parameters.
    float dx12 = a2x - a1x;
    float dy12 = a2y - a1y;
    float dx34 = b2x - b1x;
    float dy34 = b2y - b1y;

    // Solve for t1 and t2
    float denominator = (dy12 * dx34 - dx12 * dy34);

    float t1 = ((a1x - b1x) * dy34 + (b1y - a1y) * dx34) / denominator;
    if (isinf(t1)) {
        // The lines are parallel (or close enough to it).
        return false;
    }

    // the lines intersect
    float t2 = ((b1x - a1x) * dy12 + (a1y - b1y) * dx12) / -denominator;

    // The segments intersect if t1 and t2 are between 0 and 1.
    if ((t1 >= 0) && (t1 <= 1) && (t2 >= 0) && (t2 <= 1)) {
        pIntersection = TAK::Engine::Math::Point2<float>(a1x + dx12 * t1, a1y + dy12 * t1);
        return true;
    }
    return false;
}

static bool IntersectRectangle(TAK::Engine::Math::Point2<float>& p1, float p2x, float p2y, float rx0, float ry0, float rx1, float ry1) {
    auto isects = std::vector<TAK::Engine::Math::Point2<float>>();
    // intersect left
    TAK::Engine::Math::Point2<float> i1, i2, i3, i4;
    if (IntersectLine(i1, p1.x, p1.y, p2x, p2y, rx0, ry0, rx0, ry1)) {
        isects.push_back(i1);  // intersect left
    }
    if (IntersectLine(i2, p1.x, p1.y, p2x, p2y, rx0, ry0, rx1, ry0)) {
        isects.push_back(i2);  // intersect top
    }
    if (IntersectLine(i3, p1.x, p1.y, p2x, p2y, rx0, ry1, rx1, ry1)) {
        isects.push_back(i3);  // intersect bottom
    }
    if (IntersectLine(i4, p1.x, p1.y, p2x, p2y, rx1, ry0, rx1, ry1)) {
        isects.push_back(i4);  // intersect right
    }

    if (isects.size() < 1) return false;

    auto isect = isects[0];
    auto dist2 = DistanceSquared(isect.x, isect.y, p1.x, p1.y);
    for (size_t i = 1; i < isects.size(); i++) {
        float d2 = DistanceSquared(isects[i].x, isects[i].y, p1.x, p1.y);
        if (d2 < dist2) {
            isect = isects[i];
            dist2 = d2;
        }
    }

    p1 = isect;
    return true;
}

static bool FindIntersection(TAK::Engine::Math::Point2<float>& p1, TAK::Engine::Math::Point2<float>& p2, float rx0, float ry0, float rx1,
                             float ry1) {
    // if endpoint 1 is not contained, find intersection
    if (!atakmap::math::Rectangle<float>::contains(rx0, ry0, rx1, ry1, p1.x, p1.y) &&
        !IntersectRectangle(p1, p2.x, p2.y, rx0, rx0, rx1, ry1)) {
        // no intersection point
        return false;
    }
    // if endpoint 2 is not contained, find intersection
    if (!atakmap::math::Rectangle<float>::contains(rx0, ry0, rx1, ry1, p2.x, p2.y) &&
        !IntersectRectangle(p2, p1.x, p1.y, rx0, rx0, rx1, ry1)) {
        // no intersection point
        return false;
    }

    return true;
}

GLLabel::GLLabel()
    : canDraw(false),
      geometry_(nullptr, nullptr),
      altitude_mode_(TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround),
      visible_(true),
      always_render_(false),
      max_draw_resolution_(0.0),
      alignment_(TextAlignment::TETA_Center),
      vertical_alignment_(VerticalAlignment::TEVA_Top),   
      color_r_(1),
      color_g_(1),
      color_b_(1),
      color_a_(1),
      back_color_r_(0),
      back_color_g_(0),
      back_color_b_(0),
      back_color_a_(0),
      fill_(false),
      projected_size_(std::numeric_limits<double>::quiet_NaN()),   
      mark_dirty_(true),
      draw_version_(-1),
      gltext_(nullptr)
{
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;
}

GLLabel::GLLabel(const GLLabel& rhs) : GLLabel() {
    Feature::Geometry_clone(geometry_, *(rhs.geometry_.get()));
    altitude_mode_ = rhs.altitude_mode_;
    text_ = rhs.text_;
    desired_offset_ = rhs.desired_offset_;
    max_draw_resolution_ = rhs.max_draw_resolution_;
    visible_ = rhs.visible_;
    always_render_ = rhs.always_render_;
    alignment_ = rhs.alignment_;
    vertical_alignment_ = rhs.vertical_alignment_;
    rotation_ = rhs.rotation_;
    labelRect = rhs.labelRect;
    canDraw = rhs.canDraw;
    draw_version_ = rhs.draw_version_;
    color_a_ = rhs.color_a_;
    color_r_ = rhs.color_r_;
    color_g_ = rhs.color_g_;
    color_b_ = rhs.color_b_;
    back_color_a_ = rhs.back_color_a_;
    back_color_r_ = rhs.back_color_r_;
    back_color_g_ = rhs.back_color_g_;
    back_color_b_ = rhs.back_color_b_;
    fill_ = rhs.fill_;
    gltext_ = rhs.gltext_;
}

GLLabel::GLLabel(GLLabel&& rhs) NOTHROWS : GLLabel() {
    geometry_ = std::move(rhs.geometry_);
    altitude_mode_ = rhs.altitude_mode_;
    text_ = rhs.text_;
    desired_offset_ = rhs.desired_offset_;
    max_draw_resolution_ = rhs.max_draw_resolution_;
    visible_ = rhs.visible_;
    always_render_ = rhs.always_render_;
    alignment_ = rhs.alignment_;
    vertical_alignment_ = rhs.vertical_alignment_;
    rotation_ = rhs.rotation_;
    labelRect = rhs.labelRect;
    canDraw = rhs.canDraw;
    draw_version_ = rhs.draw_version_;
    color_a_ = rhs.color_a_;
    color_r_ = rhs.color_r_;
    color_g_ = rhs.color_g_;
    color_b_ = rhs.color_b_;
    back_color_a_ = rhs.back_color_a_;
    back_color_r_ = rhs.back_color_r_;
    back_color_g_ = rhs.back_color_g_;
    back_color_b_ = rhs.back_color_b_;
    fill_ = rhs.fill_;
    gltext_ = rhs.gltext_;
}

GLLabel::GLLabel(Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text, Point2<double> desired_offset,
                 double max_draw_resolution, TextAlignment alignment, VerticalAlignment vertical_alignment, int color, int backColor,
                 bool fill, TAK::Engine::Feature::AltitudeMode altitude_mode)
    : canDraw(false),
      geometry_(std::move(geometry)),
      altitude_mode_(altitude_mode),
      text_(text.get() != nullptr ? text.get() : ""),
      desired_offset_(desired_offset),
      max_draw_resolution_(max_draw_resolution),
      alignment_(alignment),
      vertical_alignment_(vertical_alignment),
      visible_(true),
      always_render_(false),
      color_r_(1),
      color_g_(1),
      color_b_(1),
      color_a_(1),
      back_color_r_(0),
      back_color_g_(0),
      back_color_b_(0),
      back_color_a_(0),
      fill_(fill),
      projected_size_(std::numeric_limits<double>::quiet_NaN()),
      mark_dirty_(true),
      draw_version_(-1),
      gltext_(nullptr)
{
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;

    setColor(color);
    setBackColor(backColor);
}

GLLabel::GLLabel(const TextFormatParams &fmt,
                 TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                 Math::Point2<double> desired_offset, double max_draw_resolution,
                 TextAlignment alignment,
                 VerticalAlignment vertical_alignment, int color,
                 int fill_color, bool fill,
                 TAK::Engine::Feature::AltitudeMode altitude_mode) :
    GLLabel(std::move(geometry), text, desired_offset, max_draw_resolution, alignment, vertical_alignment, color, fill_color, fill, altitude_mode)
{
    gltext_ = GLText2_intern(fmt);
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;
}

GLLabel::GLLabel(const TextFormatParams &fmt,
                 TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                 Math::Point2<double> desired_offset, double max_draw_resolution,
                 TextAlignment alignment,
                 VerticalAlignment vertical_alignment, int color,
                 int fill_color, bool fill,
                 TAK::Engine::Feature::AltitudeMode altitude_mode,
                 float rotation, bool rotationAbsolute) :
    GLLabel(std::move(geometry), text, desired_offset, max_draw_resolution, alignment, vertical_alignment, color, fill_color, fill, altitude_mode)
{
    gltext_ = GLText2_intern(fmt);
    rotation_.angle_ = rotation;
    rotation_.absolute_ = rotationAbsolute;
    rotation_.explicit_ = true;
}

GLLabel& GLLabel::operator=(GLLabel&& rhs) NOTHROWS {
    if (this != &rhs) {
        geometry_ = std::move(rhs.geometry_);
        altitude_mode_ = rhs.altitude_mode_;
        text_ = rhs.text_;
        desired_offset_ = rhs.desired_offset_;
        max_draw_resolution_ = rhs.max_draw_resolution_;
        visible_ = rhs.visible_;
        always_render_ = rhs.always_render_;
        alignment_ = rhs.alignment_;
        vertical_alignment_ = rhs.vertical_alignment_;
        rotation_ = rhs.rotation_;
        labelRect = rhs.labelRect;
        canDraw = rhs.canDraw;
        draw_version_ = rhs.draw_version_;
        color_a_ = rhs.color_a_;
        color_r_ = rhs.color_r_;
        color_g_ = rhs.color_g_;
        color_b_ = rhs.color_b_;
        back_color_a_ = rhs.back_color_a_;
        back_color_r_ = rhs.back_color_r_;
        back_color_g_ = rhs.back_color_g_;
        back_color_b_ = rhs.back_color_b_;
        fill_ = rhs.fill_;
        gltext_ = rhs.gltext_;
    }

    return *this;
}

void GLLabel::setGeometry(const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS {
    geometry_.release();
    TAK::Engine::Feature::Geometry_clone(geometry_, geometry);

    // need to invalidate the projected point
    mark_dirty_ = true;
}

const TAK::Engine::Feature::Geometry2* GLLabel::getGeometry() const NOTHROWS { return (geometry_.get()); }

void GLLabel::setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS { altitude_mode_ = altitude_mode; }

void GLLabel::setText(TAK::Engine::Port::String text) NOTHROWS {
    if (text.get() != nullptr)
        text_ = text.get();
    else
        text_.clear();
}

void GLLabel::setTextFormat(const TextFormatParams* fmt) NOTHROWS {
    if (fmt != nullptr) 
        gltext_ = GLText2_intern(*fmt);
    else
        gltext_ = nullptr;
}

void GLLabel::setVisible(const bool visible) NOTHROWS { visible_ = visible; }

void GLLabel::setAlwaysRender(const bool always_render) NOTHROWS { always_render_ = always_render; }

void GLLabel::setMaxDrawResolution(const double max_draw_resolution) NOTHROWS { max_draw_resolution_ = max_draw_resolution; }

void GLLabel::setAlignment(const TextAlignment alignment) NOTHROWS { alignment_ = alignment; }

void GLLabel::setVerticalAlignment(const VerticalAlignment vertical_alignment) NOTHROWS { vertical_alignment_ = vertical_alignment; }

void GLLabel::setDesiredOffset(const Math::Point2<double>& desired_offset) NOTHROWS { desired_offset_ = desired_offset; }

void GLLabel::setColor(const int color) NOTHROWS {
    color_a_ = static_cast<float>((color >> 24) & 0xFF) / 255.0f;
    color_r_ = static_cast<float>((color >> 16) & 0xFF) / 255.0f;
    color_g_ = static_cast<float>((color >> 8) & 0xFF) / 255.0f;
    color_b_ = static_cast<float>((color >> 0) & 0xFF) / 255.0f;
}

void GLLabel::setBackColor(const int color) NOTHROWS {
    back_color_a_ = static_cast<float>((color >> 24) & 0xFF) / 255.0f;
    back_color_r_ = static_cast<float>((color >> 16) & 0xFF) / 255.0f;
    back_color_g_ = static_cast<float>((color >> 8) & 0xFF) / 255.0f;
    back_color_b_ = static_cast<float>((color >> 0) & 0xFF) / 255.0f;
}

void GLLabel::setFill(const bool fill) NOTHROWS { fill_ = fill; }

bool GLLabel::shouldRenderAtResolution(const double draw_resolution) const NOTHROWS {
    if (!visible_) return false;
    if (max_draw_resolution_ != 0.0 && draw_resolution >= max_draw_resolution_ && !always_render_) return false;
    return true;
}

void GLLabel::place(const GLMapView2& view, GLText2& gl_text, std::vector<atakmap::math::Rectangle<double>>& label_rects) NOTHROWS {
    view.scene.forwardTransform.transform(&transformed_anchor_, pos_projected_);

    const auto xpos = static_cast<float>(transformed_anchor_.x);
    const auto ypos = static_cast<float>(transformed_anchor_.y);

    labelRect.x = xpos;
    labelRect.y = ypos;

    const auto textDescent = gl_text.getTextFormat().getDescent();
    const auto textBaseline = gl_text.getTextFormat().getBaselineSpacing();

    if (!text_.empty()) {
        const char* text = text_.c_str();
        float offy = 0;
        float offtx = 0;
        float textWidth = std::min(gl_text.getTextFormat().getStringWidth(text), (float)(view.right - 20));
        float textHeight = gl_text.getTextFormat().getStringHeight(text);

        if (!std::isnan(projected_size_)) {
            double availableWidth = projected_size_ / view.drawMapResolution;
            if (availableWidth < textWidth) {
                canDraw = false;
                return;
            }
        }

        offtx = (float)desired_offset_.x;
        offy = (float)desired_offset_.y;

        if (offy != 0.0 && view.drawTilt > 0.0) {
            offy *= (float)(1.0f + view.drawTilt / 100.0f);
        }

        switch (vertical_alignment_) {
            case VerticalAlignment::TEVA_Top:
                offy += textDescent + textHeight;
                break;
            case VerticalAlignment::TEVA_Middle:
                offy += ((textDescent + textHeight) / 2.0f);
                break;
            case VerticalAlignment::TEVA_Bottom:
                break;
        }

        labelRect.x += (offtx - textWidth / 2.0);
        labelRect.y += static_cast<double>(offy) - static_cast<double>(textBaseline);
        labelRect.width = textWidth;
        labelRect.height = textHeight;
    }

    bool overlaps = false;
    bool rePlaced = false;
    for (auto it = label_rects.begin(); it != label_rects.end(); it++) {
        overlaps = atakmap::math::Rectangle<double>::intersects(
            labelRect.x + 1.0, labelRect.y + textDescent, labelRect.x + labelRect.width - 2.0,
            labelRect.y + labelRect.height - (2.0 * textDescent), it->x + 1.0, it->y + textDescent, it->x + it->width - 2.0,
            it->y + it->height - (2.0 * textDescent));

        if (overlaps && !rePlaced) {
            rePlaced = true;
            double leftShift = abs((labelRect.x + labelRect.width) - it->x);
            double rightShift = abs(labelRect.x - (it->x + it->width));
            if (rightShift < leftShift && rightShift < (labelRect.width / 2.0f)) {
                // shift right of compared label rect
                labelRect.x = it->x + it->width;
            } else if (leftShift < (labelRect.width / 2.0f)) {
                // shift left of compared label rect
                labelRect.x = it->x - labelRect.width;
            } else {
                break;
            }
            overlaps = atakmap::math::Rectangle<double>::intersects(
                labelRect.x + 1.0, labelRect.y + textDescent, labelRect.x + labelRect.width - 2.0,
                labelRect.y + labelRect.height - (2.0 * textDescent), it->x + 1.0, it->y + textDescent, it->x + it->width - 2.0,
                it->y + it->height - (2.0 * textDescent));
        }
        if (overlaps) break;
    }

    canDraw = !overlaps;
}

void GLLabel::draw(const GLMapView2& view, GLText2& gl_text) NOTHROWS {
    if (!text_.empty()) {
        const char* text = text_.c_str();

        try {
            GLES20FixedPipeline::getInstance()->glPushMatrix();

            const auto xpos = static_cast<float>(transformed_anchor_.x);
            const auto ypos = static_cast<float>(transformed_anchor_.y);
            const auto zpos = static_cast<float>(transformed_anchor_.z);
            GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, zpos);
            float rotate = rotation_.angle_;
            if (this->rotation_.absolute_) {
                rotate = (float)fmod(rotate + view.drawRotation, 360.0);
            }
            GLES20FixedPipeline::getInstance()->glRotatef(rotate, 0.0f, 0.0f, 1.0f);
            GLES20FixedPipeline::getInstance()->glTranslatef((float)labelRect.x - xpos, (float)labelRect.y - ypos, 0.0f - zpos);

            gl_text.draw(text, 1.0f, 1.0f, 1.0f, 1.0f);

            GLES20FixedPipeline::getInstance()->glPopMatrix();
        } catch (std::out_of_range&) {
            // ignored
        }
    }
}

void GLLabel::batch(const GLMapView2& view, GLText2& gl_text, GLRenderBatch2& batch) NOTHROWS {
    if (!text_.empty()) {
        const char* text = text_.c_str();
#if 0
        float zpos = (float)std::max(transformedAnchor.z, 0.0);

        if (view.drawTilt > 0.0) {
            zpos -= 1;
        }
#else
        auto zpos = (float)transformed_anchor_.z;
#endif

        double rotate = rotation_.angle_;
        if (this->rotation_.absolute_) {
            rotate = fmod(rotate + view.drawRotation, 360.0);
        }

        size_t lineCount = gl_text.getLineCount(text);
        float lineHeight = gl_text.getTextFormat().getCharHeight();
        float lineWidth = gl_text.getTextFormat().getStringWidth(text);

        if (rotate != 0.0) {
            const auto xpos = static_cast<float>(transformed_anchor_.x);
            const auto ypos = static_cast<float>(transformed_anchor_.y);
            batch.pushMatrix(GL_MODELVIEW);
            float mx[16];
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, mx);
            Matrix2 matrix(mx[0], mx[1], mx[2], mx[3], mx[4], mx[5], mx[6], mx[7], mx[8], mx[9], mx[10], mx[11], mx[12], mx[13], mx[14],
                           mx[15]);

            matrix.rotate((360 - rotate) / ONE_EIGHTY_OVER_PI, 0, 0, 1);

            double v;
            matrix.get(&v, 0, 0);
            mx[0] = (float)v;
            matrix.get(&v, 0, 1);
            mx[1] = (float)v;
            matrix.get(&v, 0, 2);
            mx[2] = (float)v;
            matrix.get(&v, 0, 3);
            mx[3] = (float)v;
            matrix.get(&v, 1, 0);
            mx[4] = (float)v;
            matrix.get(&v, 1, 1);
            mx[5] = (float)v;
            matrix.get(&v, 1, 2);
            mx[6] = (float)v;
            matrix.get(&v, 1, 3);
            mx[7] = (float)v;
            matrix.get(&v, 2, 0);
            mx[8] = (float)v;
            matrix.get(&v, 2, 1);
            mx[9] = (float)v;
            matrix.get(&v, 2, 2);
            mx[10] = (float)v;
            matrix.get(&v, 2, 3);
            mx[11] = (float)v;
            matrix.get(&v, 3, 0);
            mx[12] = (float)v;
            matrix.get(&v, 3, 1);
            mx[13] = (float)v;
            matrix.get(&v, 3, 2);
            mx[14] = (float)v;
            matrix.get(&v, 3, 3);
            mx[15] = (float)v;
            batch.setMatrix(GL_MODELVIEW, mx);

            Math::Point2<double> labelCorner;
            matrix.transform(&labelCorner, Math::Point2<double>(xpos, ypos));

            labelCorner.x += labelRect.x - xpos;
            labelCorner.y += labelRect.y - ypos;

            if (fill_) {
                getSmallNinePatch(view.context);
                if (small_nine_patch_ != nullptr) {
                    small_nine_patch_->batch(batch, (float)labelCorner.x - 4.0f, (float)labelCorner.y - ((lineCount - 1) * lineHeight) - 4.0f,
                                          zpos, (float)labelRect.width + 8.0f, (float)labelRect.height, back_color_r_, back_color_g_,
                                          back_color_b_, back_color_a_);
                }
            }

            if (lineCount == 1 || alignment_ == TextAlignment::TETA_Left && lineWidth <= labelRect.width) {
                gl_text.batch(batch, text, (float)labelCorner.x, (float)labelCorner.y, zpos, color_r_, color_g_, color_b_, color_a_);
            } else {
                size_t pos = 0;
                std::string token;
                std::string strText = text_;
                float offx = 0.0;
                float offy = 0.0;
                while ((pos = strText.find("\n")) != std::string::npos) {
                    token = strText.substr(0, pos);
                    const char* token_text = token.c_str();
                    float tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                    if (tokenLineWidth > labelRect.width) {
                        size_t numChars = (size_t)((labelRect.width / tokenLineWidth) * token.length()) - 2;
                        token = token.substr(0, numChars);
                        token += "...";
                        token_text = token.c_str();
                        tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                    }
                    switch (alignment_) {
                        case TextAlignment::TETA_Left:
                            offx = 0;
                            break;
                        case TextAlignment::TETA_Center:
                            offx = ((float)labelRect.width - tokenLineWidth) / 2.0f;
                            break;
                        case TextAlignment::TETA_Right:
                            offx = (float)labelRect.width - tokenLineWidth;
                            break;
                    }
                    gl_text.batch(batch, token.c_str(), (float)labelCorner.x + offx, (float)labelCorner.y - offy, zpos, color_r_, color_g_,
                                 color_b_, color_a_);
                    offy += gl_text.getTextFormat().getStringHeight(token.c_str());
                    strText.erase(0, pos + 1);
                }

                text = strText.c_str();
                lineWidth = gl_text.getTextFormat().getStringWidth(text);
                if (lineWidth > labelRect.width) {
                    size_t numChars = (size_t)((labelRect.width / lineWidth) * strText.length()) - 1;
                    strText = strText.substr(0, numChars);
                    strText += "...";
                    text = strText.c_str();
                    lineWidth = gl_text.getTextFormat().getStringWidth(text);
                }
                switch (alignment_) {
                    case TextAlignment::TETA_Left:
                        offx = 0;
                        break;
                    case TextAlignment::TETA_Center:
                        offx = ((float)labelRect.width - lineWidth) / 2.0f;
                        break;
                    case TextAlignment::TETA_Right:
                        offx = (float)labelRect.width - lineWidth;
                        break;
                }
                gl_text.batch(batch, text, (float)labelCorner.x + offx, (float)labelCorner.y - offy, zpos, color_r_, color_g_, color_b_,
                             color_a_);
            }

            batch.popMatrix(GL_MODELVIEW);
        } else {
            if (fill_) {
                getSmallNinePatch(view.context);
                if (small_nine_patch_ != nullptr) {
                    small_nine_patch_->batch(batch, (float)labelRect.x - 4.0f, (float)labelRect.y - ((lineCount - 1) * lineHeight) - 4.0f,
                                          zpos, (float)labelRect.width + 8.0f, (float)labelRect.height, back_color_r_, back_color_g_,
                                          back_color_b_, back_color_a_);
                }
            }

            if (lineCount == 1 || alignment_ == TextAlignment::TETA_Left && lineWidth <= labelRect.width) {
                gl_text.batch(batch, text, (float)labelRect.x, (float)labelRect.y, zpos, color_r_, color_g_, color_b_, color_a_);
            } else {
                size_t pos = 0;
                std::string token;
                std::string strText = text_;
                float offx = 0.0;
                float offy = 0.0;
                while ((pos = strText.find("\n")) != std::string::npos) {
                    token = strText.substr(0, pos);
                    const char* token_text = token.c_str();
                    float tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                    if (tokenLineWidth > labelRect.width) {
                        size_t numChars = (size_t)((labelRect.width / tokenLineWidth) * token.length()) - 2;
                        token = token.substr(0, numChars);
                        token += "...";
                        token_text = token.c_str();
                        tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                    }
                    switch (alignment_) {
                        case TextAlignment::TETA_Left:
                            offx = 0;
                            break;
                        case TextAlignment::TETA_Center:
                            offx = ((float)labelRect.width - tokenLineWidth) / 2.0f;
                            break;
                        case TextAlignment::TETA_Right:
                            offx = (float)labelRect.width - tokenLineWidth;
                            break;
                    }

                    gl_text.batch(batch, token_text, (float)labelRect.x + offx, (float)labelRect.y - offy, zpos, color_r_, color_g_, color_b_,
                                 color_a_);
                    offy += gl_text.getTextFormat().getStringHeight(token_text);
                    strText.erase(0, pos + 1);
                }

                text = strText.c_str();
                lineWidth = gl_text.getTextFormat().getStringWidth(text);
                if (lineWidth > labelRect.width) {
                    size_t numChars = (size_t)((labelRect.width / lineWidth) * strText.length()) - 1;
                    strText = strText.substr(0, numChars);
                    strText += "...";
                    text = strText.c_str();
                    lineWidth = gl_text.getTextFormat().getStringWidth(text);
                }
                switch (alignment_) {
                    case TextAlignment::TETA_Left:
                        offx = 0;
                        break;
                    case TextAlignment::TETA_Center:
                        offx = ((float)labelRect.width - lineWidth) / 2.0f;
                        break;
                    case TextAlignment::TETA_Right:
                        offx = (float)labelRect.width - lineWidth;
                        break;
                }

                gl_text.batch(batch, text, (float)labelRect.x + offx, (float)labelRect.y - offy, zpos, color_r_, color_g_, color_b_,
                             color_a_);
            }
        }
    }
}

atakmap::renderer::GLNinePatch* GLLabel::getSmallNinePatch(TAK::Engine::Core::RenderContext &surface) NOTHROWS {
    if (this->small_nine_patch_ == nullptr) {
        GLTextureAtlas2* atlas;
        GLMapRenderGlobals_getTextureAtlas2(&atlas, surface);
        small_nine_patch_ = new GLNinePatch(atlas, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
    }
    return small_nine_patch_;
}

void GLLabel::validateProjectedLocation(const GLMapView2& view) NOTHROWS {
    if (!mark_dirty_ && draw_version_ == view.drawVersion) return;
    if (geometry_.get() == nullptr) return;

    mark_dirty_ = false;
    draw_version_ = view.drawVersion;

    GeoPoint2 scratchGeo;
    switch (geometry_->getClass()) {
        case Feature::TEGC_Point: {
            const auto point = dynamic_cast<const Feature::Point2&>(*geometry_.get());
            scratchGeo.longitude = point.x;
            scratchGeo.latitude = point.y;
            scratchGeo.altitude = point.z;
            // if rotation was not explicitly specified, make relative
            if (!rotation_.explicit_)
                rotation_.absolute_ = false;
            projected_size_ = std::numeric_limits<double>::quiet_NaN();
        } break;
        case Feature::TEGC_LineString: {
            const auto lineString = dynamic_cast<const Feature::LineString2&>(*geometry_.get());
            if (lineString.getNumPoints() == 2) {
                atakmap::feature::GeometryPtr adaptedLineString(nullptr, nullptr);
                if (TAK::Engine::Feature::LegacyAdapters_adapt(adaptedLineString, lineString) == TE_Ok) {
                    double x1, x2;
                    double y1, y2;
                    lineString.getX(&x1, 0);
                    lineString.getX(&x2, 1);
                    lineString.getY(&y1, 0);
                    lineString.getY(&y2, 1);

                    TAK::Engine::Core::GeoPoint2 sp(y1, x1);
                    TAK::Engine::Core::GeoPoint2 ep(y2, x2);
                    try {
                        auto startPoint = TAK::Engine::Math::Point2<float>();
                        view.forward(&startPoint, sp);
                        auto endPoint = TAK::Engine::Math::Point2<float>();
                        view.forward(&endPoint, ep);

                        if (FindIntersection(startPoint, endPoint, (float)view.left, (float)view.bottom, (float)view.right,
                                             (float)view.top)) {
                            view.inverse(&sp, startPoint);
                            view.inverse(&ep, endPoint);
                            x1 = sp.longitude;
                            x2 = ep.longitude;
                            y1 = sp.latitude;
                            y2 = ep.latitude;
                        }

                        if (!rotation_.explicit_) {
                            rotation_.angle_ = (float)(atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x) * ONE_EIGHTY_OVER_PI);
                            if (rotation_.angle_ > 90 || rotation_.angle_ < -90) rotation_.angle_ += 180.0;
                            rotation_.absolute_ = false;
                        }
                        // auto intersectionGeomPtr = spatialCalc.getGeometry(intersectionId);
                        // if (intersectionGeomPtr != nullptr) {
                        //    switch (intersectionGeomPtr->getType()) {
                        //        case atakmap::feature::Geometry::Type::LINESTRING: {
                        //            auto intersectionLineString = static_cast<const atakmap::feature::LineString&>(*intersectionGeomPtr);
                        //            x1 = intersectionLineString.getX(0);
                        //            x2 = intersectionLineString.getX(1);
                        //            y1 = intersectionLineString.getY(0);
                        //            y2 = intersectionLineString.getY(1);
                        //        } break;
                        //    }
                        //}
                    } catch (...) { /* ignored */
                    }

                    projected_size_ = TAK::Engine::Core::GeoPoint2_distance(sp, ep, true);

                    scratchGeo.longitude = (x1 + x2) / 2.0;
                    scratchGeo.latitude = (y1 + y2) / 2.0;
                    // if rotation was not explicitly specified, align with segment
                }
            }
        } break;
            // case TEGC_Polygon:
            //	value = Geometry2Ptr(new Polygon2(static_cast<const
            // Polygon2&>(geometry)), Memory_deleter_const<Geometry2>);
            // break;
            // case TEGC_GeometryCollection:
            //	value = Geometry2Ptr(new GeometryCollection2(static_cast<const
            // GeometryCollection2&>(geometry)),
            // Memory_deleter_const<Geometry2>); 	break;
    }

    // Z/altitude
    bool belowTerrain = false;
    double posEl = 0.0;
    if (view.drawTilt > 0.0) {
        // XXX - altitude
        double alt = scratchGeo.altitude;
        double terrain;
        view.getTerrainMeshElevation(&terrain, scratchGeo.latitude, scratchGeo.longitude);
        if (std::isnan(alt) || altitude_mode_ == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround) {
            alt = terrain;
        } else if (altitude_mode_ == TAK::Engine::Feature::AltitudeMode::TEAM_Relative) {
            alt += terrain;
        } else if (alt < terrain) {
            // if the explicitly specified altitude is below the terrain,
            // float above and annotate appropriately
            belowTerrain = true;
            alt = terrain;
        }

        // note: always NaN if source alt is NaN
        double adjustedAlt = alt * view.elevationScaleFactor;

        // move up ~5 pixels from surface
        adjustedAlt += view.drawMapResolution * 25.0;

        scratchGeo.altitude = adjustedAlt;
        scratchGeo.altitudeRef = AltitudeReference::HAE;
        posEl = std::isnan(adjustedAlt) ? 0.0 : adjustedAlt;
    }

    view.scene.projection->forward(&pos_projected_, scratchGeo);
}
