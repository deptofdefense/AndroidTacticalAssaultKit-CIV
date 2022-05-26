#ifndef TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROL_H_INCLUDED

#include "formats/slat/CelestialIllumination.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                namespace Controls {
                    class ENGINE_API IlluminationControl {
                    public :
                        struct DateTime {
                            short year;
                            short month;
                            short day;
                            short hour;
                            short minute;
                            short second;
                        };
                    protected :
                        ~IlluminationControl() NOTHROWS = default;

                    public :
                        /**
                         * Sets the Gregorian date from which a Julian date will subsequently be computed.
                         *
                         * @param year Four-digit year
                         * @param month Two-digit month
                         * @param day Two-digit day
                         * @param hour Two-digit hour (24hr)
                         * @param min Two-digit minutes
                         * @param sec Two-digit seconds
                         */
                        virtual void setSimulatedDateTime(const DateTime &dateTime) NOTHROWS = 0;

                        virtual DateTime getSimulatedDateTime() const NOTHROWS = 0;

                        /**
                         * Enable or disable sun/moon illumination calculations.
                         * When disabled, illumination calculation will assume that light source angle
                         * is coincident with the normal of the tangent plane at the scene focus point.
                         *
                         * @param enabled Boolean value indicating whether or not illumination calculations are enabled.
                         */
                        virtual void setEnabled(bool enabled) NOTHROWS = 0;

                        /**
                         * Enabled status of the sun/moon illumination calculations.
                         *
                         * @return Boolean value indicating whether or not illumination calculations are enabled.
                         */
                        virtual bool getEnabled() const NOTHROWS = 0;
                    };
                }
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROL_H_INCLUDED