#ifndef TAK_ENGINE_RENDERER_CORE_GLLABELMANAGER_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLABELMANAGER_H_INCLUDED

#include "core/GeoPoint.h"
#include "feature/Geometry2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "renderer/core/GLLabel.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/GLRenderBatch2.h"
#include "renderer/GLRenderContext.h"
#include "renderer/GLTextureAtlas2.h"

#include <vector>

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                class ENGINE_API GLLabelManager : public TAK::Engine::Renderer::Core::GLMapRenderable2
                {
                public:
#ifdef __ANDROID__
                    // UINT32_MAX does not appear to be defined in
                    // <cstdint> or <stdint.h> for NDK
                    static const uint32_t NO_ID = 0xFFFFFFFFu;
#else
                    static const uint32_t NO_ID = UINT32_MAX;
#endif
                public:
                    GLLabelManager();
                    virtual ~GLLabelManager();
                public:
                    void resetFont() NOTHROWS;
                    uint32_t addLabel(GLLabel& label) NOTHROWS;
                    void removeLabel(const uint32_t id) NOTHROWS;
                    void setGeometry(const uint32_t id, const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS;
                    void setAltitudeMode(const uint32_t id, const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS;
                    void setText(const uint32_t id, const TAK::Engine::Port::String text) NOTHROWS;
                    void setVisible(const uint32_t id, const bool visible) NOTHROWS;
                    void setAlwaysRender(const uint32_t id, const bool always_render) NOTHROWS;
                    void setMaxDrawResolution(const uint32_t id, const double max_draw_resolution) NOTHROWS;
                    void setAlignment(const uint32_t id, const TextAlignment alignment) NOTHROWS;
                    void setVerticalAlignment(const uint32_t id, const VerticalAlignment alignment) NOTHROWS;
                    void setDesiredOffset(const uint32_t id, const Math::Point2<double>& desired_offset) NOTHROWS;
                    void setColor(const uint32_t id, const int color) NOTHROWS;
                    void setBackColor(const uint32_t id, const int color) NOTHROWS;
                    void setFill(const uint32_t id, const bool fill) NOTHROWS;
                    void getSize(const uint32_t id, atakmap::math::Rectangle<double>& size_rect) NOTHROWS;
                    void setVisible(const bool visible) NOTHROWS;
                public:
                    void draw(const GLMapView2& view, const int render_pass) NOTHROWS override;
                    void release() NOTHROWS override;
                    int getRenderPass() NOTHROWS override;
                    void start() NOTHROWS override;
                    void stop() NOTHROWS override;
                private:
                    static float defaultFontSize;
                    static GLText2* getDefaultText() NOTHROWS;
                public:
                    float labelRotation;
                    bool absoluteLabelRotation;
                    int64_t labelFadeTimer;
                private:
                    std::map<uint32_t, GLLabel> labels_;
                    uint32_t map_idx_;
                    uint32_t always_render_idx_;
                    int draw_version_;
                    bool replace_labels_;
                    Thread::Mutex mutex_;
                    std::unique_ptr<TAK::Engine::Renderer::GLRenderBatch2> batch_;
                    bool visible_;
                };

                typedef std::unique_ptr<GLLabelManager, void(*)(const GLLabelManager*)> GLLabelManagerPtr;
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLLABELMANAGER_H_INCLUDED
