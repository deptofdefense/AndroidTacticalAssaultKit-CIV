#ifndef TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROLIMPL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROLIMPL_H_INCLUDED

#include "IlluminationControl.h"
#include "formats/slat/helpers.h"
#include "formats/slat/deltat.h"
#include "formats/slat/CelestialIllumination.h"
#include "renderer/elevation/GLTerrainTile_decls.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                namespace Controls {
                    class ENGINE_API IlluminationControlImpl : public IlluminationControl {
                    private :
                        const double illumMaxLux{10000.0};
                        const double illumMaxBrightness{1.0};
                        const double illumMinBrightness{0.0};
                        bool isEnabled {false};
                        struct {
                            DateTime value;
                            double julian;
                        } dateTime;
                        short skyCondition;
                        observer location;
                        double totalIllumination;
                        Formats::SLAT::aux_data sunData;
                        Formats::SLAT::aux_data moonData;
                        double luxToBrightness(double lux);
                    public :
                        IlluminationControlImpl();
                        ~IlluminationControlImpl() NOTHROWS = default;

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
                        void setSimulatedDateTime(const DateTime &dateTime) NOTHROWS override;
                        DateTime getSimulatedDateTime() const NOTHROWS override;

                        /**
                         * Enable or disable sun/moon illumination calculations.
                         * When disabled, illumination calculation will assume that light source angle
                         * is coincident with the normal of the tangent plane at the scene focus point.
                         *
                         * @param enabled Boolean value indicating whether or not illumination calculations are enabled.
                         */
                        void setEnabled(bool enabled) NOTHROWS override;

                        /**
                         * Enabled status of the sun/moon illumination calculations.
                         *
                         * @return Boolean value indicating whether or not illumination calculations are enabled.
                         */
                        bool getEnabled() const NOTHROWS override;

                        /**
                         * Set the sky condition that will be used for the illuminance calculation.
                         *
                         * @param condition Value 1-10 indicating the condition of the sky.
                         *                  =  1 ... Average clear sky, < 70% covered by (scattered)
                         *                           clouds; direct rays of the Sun and Moon are
                         *                           unobstructed relative to the location of
                         *                           interest;
                         *                  =  2 ... Sun/Moon easily visible but direct rays
                         *                           obstructed by thin clouds;
                         *                  =  3 ... Direct rays of Sun/Moon are obstructed by
                         *                           average clouds;
                         *                  = 10 ... Dark stratus clouds cover the entire sky.
                         */
                        void setSkyCondition(short condition) NOTHROWS;

                        /**
                         * Set the observer position location that will be used for the
                         * illuminance calculation.
                         *
                         * @param latitude  Latitude.
                         * @param longitude Longitude.
                         */
                        void setLocation(double latitude, double longitude) NOTHROWS;

                        /**
                         * Calculate the sun/moon illuminance for the currently set time and location.
                         *
                         * @return Status indicating whether or not the operation succeeded.
                         */
                        bool computeIlluminance() NOTHROWS;

                        /**
                         * Configure the terrain tile renderer for the current sun/moon illuminance calculation.
                         *
                         * @param ctx Context for the terrain tile renderer.
                         * @param owner The globe (contains the geodetic location of the observer).
                         */
                        void configureIllumination(Elevation::TerrainTileRenderContext &ctx) NOTHROWS;

                        double getBrightness() NOTHROWS;
                    };
                }
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_CORE_CONTROLS_ILLUMINATIONCONTROLIMPL_H_INCLUDED
