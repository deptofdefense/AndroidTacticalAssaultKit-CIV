#ifndef TAK_ENGINE_RENDERER_CORE_GLANTIMERIDIANHELPER_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLANTIMERIDIANHELPER_H_INCLUDED

#include "core/GeoPoint2.h"
#include "feature/Envelope2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                class GLGlobe;
                class GLMapView2;
                /**
                * <P>This class is NOT thread-safe.
                *
                * @author Developer
                */
                class GLAntiMeridianHelper
                {
                public:
                    enum Hemisphere
                    {
                        East = 0,
                        West = 1,
                    };
                private:
                    bool wrap;
                    GLAntiMeridianHelper::Hemisphere primaryHemisphere;
                    TAK::Engine::Feature::Envelope2 westHemisphere;
                    TAK::Engine::Feature::Envelope2 eastHemisphere;

                public:
                    GLAntiMeridianHelper();
                    void update(const TAK::Engine::Renderer::Core::GLMapView2& view) NOTHROWS;
                    void update(const TAK::Engine::Renderer::Core::GLGlobe& view) NOTHROWS;
                    GLAntiMeridianHelper::Hemisphere getPrimaryHemisphere() const NOTHROWS;
                    Util::TAKErr getBounds(TAK::Engine::Feature::Envelope2 *value, const GLAntiMeridianHelper::Hemisphere hemi) const NOTHROWS;
                    void getBounds(TAK::Engine::Feature::Envelope2 *westHemi, TAK::Engine::Feature::Envelope2 *eastHemi) const NOTHROWS;
                    Util::TAKErr wrapLongitude(TAK::Engine::Core::GeoPoint2 *dst, const GLAntiMeridianHelper::Hemisphere hemisphere, const TAK::Engine::Core::GeoPoint2 &src) const NOTHROWS;
                    Util::TAKErr wrapLongitude(double* value, const GLAntiMeridianHelper::Hemisphere hemisphere, const double longitude) const NOTHROWS;
                    Util::TAKErr wrapLongitude(double* value, const double longitude) const NOTHROWS;
                };
            }
        }
    }
}

#endif
