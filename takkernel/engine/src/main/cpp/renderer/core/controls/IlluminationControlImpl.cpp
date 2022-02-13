
#include "IlluminationControlImpl.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                namespace Controls {
                    IlluminationControlImpl::IlluminationControlImpl() {}

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
                    void IlluminationControlImpl::setSimulatedDateTime(const DateTime &dateTime_) NOTHROWS
                    {
                        dateTime.value = dateTime_;
                        dateTime.julian = cd2jd(dateTime.value.year, dateTime.value.month, dateTime.value.day, dateTime.value.hour, dateTime.value.minute, dateTime.value.second);
                    }

                    IlluminationControl::DateTime IlluminationControlImpl::getSimulatedDateTime() const NOTHROWS
                    {
                        return dateTime.value;
                    }

                    /**
                     * Enable or disable sun/moon illumination calculations.
                     * When disabled, illumination calculation will assume that light source angle
                     * is coincident with the normal of the tangent plane at the scene focus point.
                     *
                     * @param enabled Boolean value indicating whether or not illumination calculations are enabled.
                     */
                    void IlluminationControlImpl::setEnabled(bool enabled) NOTHROWS
                    {
                        isEnabled = enabled;
                    }

                    /**
                     * Enabled status of the sun/moon illumination calculations.
                     *
                     * @return Boolean value indicating whether or not illumination calculations are enabled.
                     */
                    bool IlluminationControlImpl::getEnabled() const NOTHROWS
                    {
                        return isEnabled;
                    }

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
                    void IlluminationControlImpl::setSkyCondition(short condition) NOTHROWS
                    {
                        skyCondition = condition; // could add error checking and ensure 1-10
                    }

                    /**
                     * Set the observer position location that will be used for the
                     * illuminance calculation.
                     *
                     * @param latitude  Latitude.
                     * @param longitude Longitude.
                     */
                    void IlluminationControlImpl::setLocation(double latitude, double longitude) NOTHROWS
                    {
                        location.latitude = latitude;
                        location.longitude = longitude;
                    }

                    /**
                     * Calculate the sun/moon illuminance for the currently set time and location.
                     *
                     * @return Status indicating whether or not the operation succeeded.
                     */
                    bool IlluminationControlImpl::computeIlluminance() NOTHROWS
                    {
                        skyCondition = 1; // Average clear sky
                        double deltaT = 0.0f;
                        deltat(nullptr, dateTime.julian, &deltaT); // compute TT-UT1
                        return Formats::SLAT::CelestialIllumination::illuminance(dateTime.julian,
                               deltaT, &location, skyCondition, &totalIllumination,
                               &sunData, &moonData) == 0;
                    }

                    //https://stackoverflow.com/questions/60879998/three-js-fragment-shader-with-ambient-light-intensity
                    double IlluminationControlImpl::luxToBrightness(double lux)
                    {
                        if (lux >= illumMaxLux) return illumMaxBrightness;
                        double MIN = pow((illumMinBrightness), 2.2) * (illumMaxLux);
                        if (lux <= MIN) return illumMinBrightness;
                        return pow(lux, (1.0 / 2.2)) / pow(illumMaxLux, (1.0 / 2.2));
                    }

                    /**
                     * Configure the terrain tile renderer for the current sun/moon illuminance calculation.
                     *
                     * @param ctx Context for the terrain tile renderer.
                     * @param owner The globe (contains the geodetic location of the observer).
                     */
                    void IlluminationControlImpl::configureIllumination(Elevation::TerrainTileRenderContext &ctx) NOTHROWS
                    {
                        ctx.numLightSources = 2;
                        ctx.lightSources[0].altitude = sunData.alt;
                        ctx.lightSources[0].azimuth = sunData.az;
                        ctx.lightSources[0].intensity = (float) (luxToBrightness(sunData.ill));
                        ctx.lightSources[1].altitude = moonData.alt;
                        ctx.lightSources[1].azimuth = moonData.az;
                        ctx.lightSources[1].intensity = (float) (luxToBrightness(moonData.ill));

                        //TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Info, ">illumination {%lf,%lf} sun:{alt=%f, az=%f, lux=%f} moon:{alt=%f, az=%f, lux=%f}", location.latitude, location.longitude, (float)sunData.alt, (float)sunData.az, (float)sunData.ill, (float)moonData.alt, (float)moonData.az, (float)moonData.ill);
                        //TAK::Engine::Util::Logger_log(TAK::Engine::Util::TELL_Info, "<illumination {%04d-%02d-%02dH%02d:%02d:%02d} {%lf,%lf} sun:{alt=%f, az=%f, lux=%f} moon:{alt=%f, az=%f, lux=%f}", (int)dateTime.value.year, (int)dateTime.value.month, (int)dateTime.value.day, (int)dateTime.value.hour, (int)dateTime.value.minute, (int)dateTime.value.second, location.latitude, location.longitude, ctx.lightSources[0].altitude, ctx.lightSources[0].azimuth, ctx.lightSources[0].intensity, ctx.lightSources[1].altitude, ctx.lightSources[1].azimuth, ctx.lightSources[1].intensity);
                    }

                    double IlluminationControlImpl::getBrightness() noexcept
                    {
                        return luxToBrightness(sunData.ill);
                    }
                }
            }
        }
    }
}
