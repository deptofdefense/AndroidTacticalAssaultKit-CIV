#ifndef TAK_ENGINE_RENDERER_CORE_GLCONTENTINDICATOR_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLCONTENTINDICATOR_H_INCLUDED

#include "core/GeoPoint2.h"
#include "core/RenderContext.h"
#include "feature/Geometry2.h"
#include "feature/Style.h"
#include "port/Platform.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/feature/GLBatchGeometry3.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                /**
                 * Unless otherwise noted, all public functions are only safe
                 * to invoke on the GL thread.
                 */
                class GLContentIndicator : public GLMapRenderable2
                {
                public :
                    GLContentIndicator(TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
                public :
                    /**
                     * Sets the icon for the indicator at the provided location using the specified image.
                     */
                    void setIcon(const TAK::Engine::Core::GeoPoint2 &location, const char *iconUri) NOTHROWS;
                    /**
                     * Clears the indicator icon graphic from the map.
                     */
                    void clearIcon() NOTHROWS;
                    /**
                     * Sets the bounding geometry for the indicator
                     * @param bounds        The bounding geometry
                     * @param style         The style to render with
                     * @param clampToGround If `true`, draws the bounds on the
                     *                      terrain surface, if `false` draws
                     *                      in 3D using the geometry's 'z' 
                     *                      coordinates.
                     */
                    void setBounds(const TAK::Engine::Feature::Geometry2 &bounds, const atakmap::feature::Style &style, bool clampToGround) NOTHROWS;
                    /**
                     * Clears the indicator bounds graphic from the map
                     */
                    void clearBounds() NOTHROWS;
                    /**
                     * Indicates progress on the icon
                     * @param value The progress value, `-1` for indeterminate,
                     *              0-100 for percent
                     */
                    void showProgress(int value) NOTHROWS;
                    /**
                     * Clears the progress graphic on the icon.
                     */
                    void clearProgress() NOTHROWS;
                    /**
                     * Sets the display thresholds for the indicator
                     */
                    void setDisplayThresholds(const double minGsd, const double maxGsd) NOTHROWS;
                public : // GLMapRenderable2
                    void draw(const GLMapView2 &view, const int renderPass) NOTHROWS override;
                    void release() NOTHROWS override;
                    int getRenderPass() NOTHROWS override;
                    void start() NOTHROWS override;
                    void stop() NOTHROWS override;
                private :
                    TAK::Engine::Core::RenderContext &ctx_;
                    std::unique_ptr<Feature::GLBatchGeometry3> icon_;
                    std::unique_ptr<Feature::GLBatchGeometry3> bounds_;
                    bool clamp_to_ground_;
                    struct
                    {
                        bool show;
                        int value;
                    } progress_;
                    double min_resolution_;
                    double max_resolution_;
                };
            }
        }
    }
}

#endif

