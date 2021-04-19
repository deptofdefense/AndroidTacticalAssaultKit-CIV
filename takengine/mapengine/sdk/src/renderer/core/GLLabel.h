#ifndef TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED

#include <string>
#include <vector>

#include "feature/AltitudeMode.h"
#include "feature/Geometry2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/core/GLMapView2.h"
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
                // Text Alignment for multi-line labels
                enum TextAlignment
                {
                    TETA_Left, TETA_Center, TETA_Right
                };

                // Vertical Alignment for labels in relation to their geometry and anchor
                enum VerticalAlignment
                {
                    TEVA_Top, TEVA_Middle, TEVA_Bottom
                };

                class ENGINE_API GLLabel
                {
                public:
                    GLLabel();
                    GLLabel(GLLabel&&) NOTHROWS;
                    GLLabel(const GLLabel&);
                    GLLabel(TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment = TextAlignment::TETA_Center,
                            VerticalAlignment vertical_alignment = VerticalAlignment::TEVA_Top, int color = 0xFFFFFFFF,
                            int fill_color = 0x00000000, bool fill = false,
                            TAK::Engine::Feature::AltitudeMode altitude_mode = TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround);
                    GLLabel(const TextFormatParams &fmt,
                            TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment,
                            VerticalAlignment vertical_alignment, int color,
                            int fill_color, bool fill,
                            TAK::Engine::Feature::AltitudeMode altitude_mode);
                    GLLabel(const TextFormatParams &fmt,
                            TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desired_offset, double max_draw_resolution,
                            TextAlignment alignment,
                            VerticalAlignment vertical_alignment, int color,
                            int fill_color, bool fill,
                            TAK::Engine::Feature::AltitudeMode altitude_mode,
                            float rotation, bool rotationAbsolute);
                    ~GLLabel() = default;
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
                    bool shouldRenderAtResolution(const double draw_resolution) const NOTHROWS;
                    void validateProjectedLocation(const TAK::Engine::Renderer::Core::GLMapView2& view) NOTHROWS;
                private:
                    void place(const GLMapView2& view, GLText2& gl_text, std::vector<atakmap::math::Rectangle<double>>& label_rects) NOTHROWS;
                    void draw(const GLMapView2& view, GLText2& gl_text) NOTHROWS;
                    void batch(const GLMapView2& view, GLText2& gl_text, GLRenderBatch2& batch) NOTHROWS;
                    atakmap::renderer::GLNinePatch* getSmallNinePatch(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                public:
                    atakmap::math::Rectangle<double> labelRect;
                    bool canDraw;
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
                    float color_r_;
                    float color_g_;
                    float color_b_;
                    float color_a_;
                    float back_color_r_;
                    float back_color_g_;
                    float back_color_b_;
                    float back_color_a_;
                    bool fill_;
                    Math::Point2<double> pos_projected_;
                    Math::Point2<double> transformed_anchor_;
                    double projected_size_;
                    struct {
                        float angle_;
                        bool absolute_;
                        bool explicit_;
                    } rotation_;
                    bool mark_dirty_;
                    int draw_version_;
                    /** if `nullptr`, uses system default */
                    GLText2 *gltext_;

                    friend class GLLabelManager;
                };
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED