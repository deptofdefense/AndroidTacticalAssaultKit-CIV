#ifndef TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED

#include <array>
#include <string>
#include <vector>
#include <array>

#include "core/RenderContext.h"
#include "feature/AltitudeMode.h"
#include "feature/Geometry2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText2.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                class GLGlobeBase;

                // Text Alignment for multi-line labels
                enum TextAlignment
                {
                    TETA_Left, TETA_Center, TETA_Right
                };

                // Vertical Alignment for labels in relation to their geometry and anchor
                enum VerticalAlignment
                {
                    TEVA_Top, TEVA_Middle, TEVA_Bottom };

                // Priority for labels, determines render order
                enum Priority
                {
                    TEP_High, TEP_Standard, TEP_Low
                };
                class GLLabel;

                class ENGINE_API GLLabel
                {
                private :
                    struct LabelPlacement
                    {
                        TAK::Engine::Math::Point2<double> anchor_xyz_;
                        TAK::Engine::Math::Point2<double> render_xyz_;
                        struct {
                            double angle_ {0.0};
                            bool absolute_ {false};
                        } rotation_;
                        bool can_draw_ {true};
                        std::array<TAK::Engine::Math::Point2<float>,4> rotatedRectangle;
                        void recompute(const double absoluteRotation, const float width, const float height) NOTHROWS;
                    };
                public :
                    enum Hints
                    {
                    /** floats the label along associated linestring geometry, maintaining at the associated specified weight with respect to the portion that is visible in screenspace */
                        WeightedFloat       = 0x00000001u,
                    /** label is duplicated */
                        DuplicateOnSplit    = 0x00000002u,
                    /** floats the label vertically above the associated geometry based on tilt */
                        AutoSurfaceOffsetAdjust   = 0x00000004u,
                    /** Render an XRay pass */
                        XRay = 0x00000008u,
                    /** Scroll long labels */
                        ScrollingText = 0x00000010u,
                    };
                public:
                    GLLabel();
                    GLLabel(GLLabel&&) NOTHROWS;
                    GLLabel(const GLLabel&);
                    GLLabel(TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment = TextAlignment::TETA_Center, VerticalAlignment vertical_alignment = VerticalAlignment::TEVA_Top, 
                            int color = 0xFFFFFFFF, int fill_color = 0x00000000, bool fill = false,
                            TAK::Engine::Feature::AltitudeMode altitude_mode = TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround,
                            Priority priority = Priority::TEP_Standard);
                    GLLabel(TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment, VerticalAlignment vertical_alignment, 
                            int color, int fill_color, bool fill,
                            TAK::Engine::Feature::AltitudeMode altitude_mode, 
                            float rotation, bool rotationAbsolute,
                            Priority priority);
                    GLLabel(const TextFormatParams &fmt,
                            TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment, VerticalAlignment vertical_alignment, 
                            int color, int fill_color, bool fill,
                            TAK::Engine::Feature::AltitudeMode altitude_mode,
                            Priority priority);
                    GLLabel(const TextFormatParams &fmt,
                            TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment, VerticalAlignment vertical_alignment, 
                            int color, int fill_color, bool fill,
                            TAK::Engine::Feature::AltitudeMode altitude_mode,
                            float rotation, bool rotationAbsolute,
                            Priority priority = Priority::TEP_Standard);

                    ~GLLabel() NOTHROWS;
                    GLLabel& operator=(GLLabel&&) NOTHROWS;
                    void setGeometry(const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS;
                    const TAK::Engine::Feature::Geometry2* getGeometry() const NOTHROWS;
                    void setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS;
                    void setText(const TAK::Engine::Port::String text) NOTHROWS;
                    void setTextFormat(const TextFormatParams* fmt) NOTHROWS;
                    void setVisible(const bool visible) NOTHROWS;
                    void setAlwaysRender(const bool always_render) NOTHROWS;
                    void setMaxDrawResolution(const double max_draw_resolution) NOTHROWS;
                    void setAlignment(const TextAlignment alignment) NOTHROWS;
                    void setVerticalAlignment(const VerticalAlignment vertical_alignment) NOTHROWS;
                    void setDesiredOffset(const Math::Point2<double>& desired_offset) NOTHROWS;
                    void setColor(const int color) NOTHROWS;
                    void setBackColor(const int color) NOTHROWS;
                    void setFill(const bool fill) NOTHROWS;
                    void setRotation(const float rotation, const bool absolute_rotation) NOTHROWS;
                    void setPriority(const Priority priority) NOTHROWS;
                    bool shouldRenderAtResolution(const double draw_resolution) const NOTHROWS;
                    void validate(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const GLText2 &text) NOTHROWS;
                public :
                    void setHints(const unsigned int hints) NOTHROWS;
                    void setPlacementInsets(const float left, const float right, const float bottom, const float top) NOTHROWS;
                    Util::TAKErr setFloatWeight(const float weight) NOTHROWS;

                    void getRotation(float &angle, bool &absolute) const NOTHROWS;
                private:
                    bool place(LabelPlacement &placement, const GLGlobeBase& view, const GLText2& gl_text, const std::vector<LabelPlacement>& label_rects, bool &rePlaced) NOTHROWS;
                    void draw(const GLGlobeBase& view, GLText2& gl_text) NOTHROWS;
                    void batch(const GLGlobeBase& view, GLText2& gl_text, GLRenderBatch2& batch, int render_pass) NOTHROWS;
                    void batch(const GLGlobeBase& view, GLText2& gl_text, GLRenderBatch2& batch, const LabelPlacement &anchor, int render_pass) NOTHROWS;
                    atakmap::renderer::GLNinePatch* getSmallNinePatch(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                    void marqueeAnimate(const int64_t animDelta) NOTHROWS;
                public:
                    /** The size of the label as it will be placed on the screen*/
                    struct {
                        float width;
                        float height;
                    } labelSize;
                private:
                    static atakmap::renderer::GLNinePatch* small_nine_patch_;
                private:
                    TAK::Engine::Feature::Geometry2Ptr_const geometry_;
                    TAK::Engine::Feature::AltitudeMode altitude_mode_;
                    std::string text_;
                    Math::Point2<double> desired_offset_;
                    bool visible_;
                    bool always_render_;
                    double max_draw_resolution_;
                    TextAlignment alignment_;
                    VerticalAlignment vertical_alignment_;
                    Priority priority_;
                    float color_r_;
                    float color_g_;
                    float color_b_;
                    float color_a_;
                    float back_color_r_;
                    float back_color_g_;
                    float back_color_b_;
                    float back_color_a_;
                    bool fill_;
                    std::vector<LabelPlacement> transformed_anchor_;
                    double projected_size_;
                    struct {
                        float angle_;
                        bool absolute_;
                        bool explicit_;
                    } rotation_;
                    struct {
                        float left_ { 0.f };
                        float right_ { 0.f };
                        float bottom_ { 0.f };
                        float top_ { 0.f };
                    } insets_;
                    struct {
                        float offset_ {0.f};
                        int64_t timer_ {3000LL};
                    } marquee_;
                    /** the size of the text, ignoring any clamping for scrolling labels */
                    struct {
                        float width;
                        float height;
                    } textSize;
                    float float_weight_;
                    bool mark_dirty_;
                    int draw_version_;
                    unsigned int hints_;
                    /** if `nullptr`, uses system default */
                    GLText2 *gltext_;
                    bool did_animate_;

                    friend class GLLabelManager;
                    friend class LabelBoundingRectangle;
                };
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED