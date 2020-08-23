#ifndef TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED

#include "renderer/GL.h"
#include <string>
#include <vector>

#include "core/GeoPoint.h"
#include "feature/AltitudeMode.h"
#include "feature/Geometry2.h"
#include "port/Platform.h"
#include "port/String.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLRenderContext.h"
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
                    GLLabel(GLLabel&&);
                    GLLabel(const GLLabel&);
                    GLLabel(TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                            Math::Point2<double> desiredOffset, double maxDrawResolution,
                            TextAlignment alignment = TextAlignment::TETA_Center,
                            VerticalAlignment verticalAlignment = VerticalAlignment::TEVA_Top, int color = 0xFFFFFFFF,
                            int fillColor = 0x00000000, bool fill = false,
                            TAK::Engine::Feature::AltitudeMode altitudeMode = TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround);
                    ~GLLabel();
                    GLLabel& operator=(GLLabel&&);
                    void setGeometry(const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS;
                    const TAK::Engine::Feature::Geometry2* getGeometry() const NOTHROWS;
                    void setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitudeMode) NOTHROWS;
                    void setText(const TAK::Engine::Port::String text) NOTHROWS;
                    void setVisible(const bool visible) NOTHROWS;
                    void setAlwaysRender(const bool alwaysRender) NOTHROWS;
                    void setMaxDrawResolution(const double maxDrawResolution) NOTHROWS;
                    void setAlignment(const TextAlignment alignment) NOTHROWS;
                    void setVerticalAlignment(const VerticalAlignment verticalAlignment) NOTHROWS;
                    void setDesiredOffset(const Math::Point2<double>& desiredOffset) NOTHROWS;
                    void setColor(const int color) NOTHROWS;
                    void setBackColor(const int color) NOTHROWS;
                    void setFill(const bool fill) NOTHROWS;
                    bool shouldRenderAtResolution(const double drawResolution) const NOTHROWS;
                    void validateProjectedLocation(const TAK::Engine::Renderer::Core::GLMapView2& view) NOTHROWS;
                private:
                    void place(const GLMapView2& view, GLText2& glText, std::vector<atakmap::math::Rectangle<double>>& labelRects) NOTHROWS;
                    void draw(const GLMapView2& view, GLText2& glText) NOTHROWS;
                    void batch(const GLMapView2& view, GLText2& glText, GLRenderBatch2& batch) NOTHROWS;
                    atakmap::renderer::GLNinePatch* getSmallNinePatch(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                public:
                    atakmap::math::Rectangle<double> labelRect;
                    bool canDraw;
                private:
                    static atakmap::renderer::GLNinePatch* smallNinePatch;
                private:
                    TAK::Engine::Feature::Geometry2Ptr_const m_geometry;
                    TAK::Engine::Feature::AltitudeMode m_altitudeMode;
                    std::string m_text;
                    Math::Point2<double> m_desiredOffset;
                    bool m_visible;
                    bool m_alwaysRender;
                    double m_maxDrawResolution;
                    TextAlignment m_alignment;
                    VerticalAlignment m_verticalAlignment;
                    float m_colorR;
                    float m_colorG;
                    float m_colorB;
                    float m_colorA;
                    float m_backColorR;
                    float m_backColorG;
                    float m_backColorB;
                    float m_backColorA;
                    bool m_fill;
                    Math::Point2<double> posProjected;
                    Math::Point2<double> transformedAnchor;
                    double projectedSize;
                    float labelRotation;
                    bool absoluteLabelRotation;
                    bool markDirty;
                    int m_drawVersion;

                    friend class GLLabelManager;
                };
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLLABEL_H_INCLUDED