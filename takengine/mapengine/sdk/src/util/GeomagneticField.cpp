#include "util/GeomagneticField.h"

#include "formats/wmm/GeomagnetismHeader.h"
#include "port/String.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/ConfigOptions.h"

using namespace TAK::Engine::Util;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;

namespace
{
    struct GeomagneticFieldState
    {
        GeomagneticFieldState() NOTHROWS :
            mutex(TEMT_Recursive),
            loaded(false),
            timeVersion(0u),
            ellipsoid(),
            geoid()
        {}

        Mutex mutex;
        bool loaded;
        std::shared_ptr<MAGtype_MagneticModel> model;
        std::shared_ptr<MAGtype_MagneticModel> timeModel;
        uint32_t timeVersion;
        MAGtype_Ellipsoid ellipsoid;
        MAGtype_Geoid geoid;
    } state;

    void initWMM() NOTHROWS;
    bool isLeapYear(const std::size_t year) NOTHROWS;
    TAKErr getDayOfYear(std::size_t *value, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
    TAKErr GeomagneticField_getElements(MAGtype_GeoMagneticElements *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS;
}

#define GET_ELEMENT_FIELD_IMPL(f) \
    TAKErr code(TE_Ok); \
    MAGtype_GeoMagneticElements GeoMagneticElements; \
    memset(&GeoMagneticElements, 0, sizeof(MAGtype_GeoMagneticElements)); \
    code = GeomagneticField_getElements(&GeoMagneticElements, p, year, month, day); \
    TE_CHECKRETURN_CODE(code); \
    *value = GeoMagneticElements.f

TAKErr TAK::Engine::Util::GeomagneticField_getDeclination(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(Decl);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getInclination(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(Incl);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getFieldStrength(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(F);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getHorizontalStrength(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(H);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getX(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(X);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getY(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(Y);
    return TE_Ok;
}
TAKErr TAK::Engine::Util::GeomagneticField_getZ(double *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
{
    GET_ELEMENT_FIELD_IMPL(Z);
    return TE_Ok;
}

namespace
{
    void initWMM() NOTHROWS
    {
        TAKErr code(TE_Ok);
        int success;

        // init the ellipsoid/geom
        success = MAG_SetDefaults(&state.ellipsoid, &state.geoid);
        if(!success)
            return;

        do {
        // initialize from the coefficient file if necessary
            TAK::Engine::Port::String coffFilePath;
            code = ConfigOptions_getOption(coffFilePath, "world-magnetic-model-file");
            if(code != TE_Ok)
                Logger_log(TELL_Warning, "WMM COF file is not configured");
            TE_CHECKBREAK_CODE(code);

            MAGtype_MagneticModel *model[1];

#ifdef __ANDROID__
            success = MAG_robustReadMagModels(coffFilePath.get(), &model, 1u);
#else
            success = MAG_robustReadMagModels(coffFilePath.get(), (MAGtype_MagneticModel *(*)[])&model, 1u);
#endif
            if(!success)
                return;

            std::unique_ptr<MAGtype_MagneticModel, int(*)(MAGtype_MagneticModel *)> mmptr(model[0], MAG_FreeMagneticModelMemory);

            state.model = std::move(mmptr);
        } while(false);
    }

    bool isLeapYear(const std::size_t year) NOTHROWS
    {
        return !(year%400u) || ((year%100u) && !(year%4u));
    }
    TAKErr getDayOfYear(std::size_t *value, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
    {
        static std::size_t dayInYear[12u] = {0u, 31u, 59u, 90u, 120u, 151u, 181u, 212u, 243u, 273u, 304u, 334u};
        if(month > 11u)
            return TE_InvalidArg;
        *value = dayInYear[month] + day;
        if(isLeapYear(year))
            *value = (*value) + 1;
        return TE_Ok;
    }
    TAKErr GeomagneticField_getElements(MAGtype_GeoMagneticElements *value, const GeoPoint2 &p, const std::size_t year, const std::size_t month, const std::size_t day) NOTHROWS
    {
        TAKErr code(TE_Ok);
        int success;

        // check for valid date
        if(year < 1970u || year > (1u<<24u))
            return TE_InvalidArg;
        if(month > 11u)
            return TE_InvalidArg;
        if(day < 1u || day > 31u)
            return TE_InvalidArg;

        std::size_t dayOfYear;
        code = getDayOfYear(&dayOfYear, year, month, day);
        TE_CHECKRETURN_CODE(code);

        double days = 365.0;
        if (isLeapYear(year))
            days += 1.0;

        std::shared_ptr<MAGtype_MagneticModel> tmm;

        {
            Lock lock(state.mutex);
            code = lock.status;
            TE_CHECKRETURN_CODE(code);

            if(!state.loaded) {
                initWMM();
                state.loaded = true;
            }
            if(!state.model.get())
                return TE_InvalidArg;

            // initialize the 'timely' model as needed
            auto requestedTime = static_cast <uint32_t>((year << 8u) | (dayOfYear));
            if(requestedTime != state.timeVersion) {
                int nMax = 0;
                if (nMax < state.model->nMax)
                    nMax = state.model->nMax;
                int NumTerms = ((nMax + 1) * (nMax + 2) / 2);

                std::unique_ptr<MAGtype_MagneticModel, int(*)(MAGtype_MagneticModel *)> tmmPtr(MAG_AllocateModelMemory(NumTerms), MAG_FreeMagneticModelMemory);

                MAGtype_Date UserDate;
                UserDate.DecimalYear = year + ((dayOfYear-1u) / days);
                UserDate.Year = static_cast<int>(year);
                UserDate.Month = static_cast<int>(month);
                UserDate.Day = static_cast<int>(day);

                success = MAG_TimelyModifyMagneticModel(UserDate, state.model.get(), tmmPtr.get()); /* Time adjust the coefficients, Equation 19, WMM Technical report */
                if(!success)
                    return TE_Err;

                state.timeModel = std::move(tmmPtr);
                state.timeVersion = requestedTime;
            }

            tmm = state.timeModel;
        }
        MAGtype_CoordGeodetic CoordGeodetic;
        MAGtype_CoordSpherical CoordSpherical;

        memset(&CoordGeodetic, 0, sizeof(MAGtype_CoordGeodetic));
        memset(&CoordSpherical, 0, sizeof(MAGtype_CoordSpherical));

        CoordGeodetic.lambda = p.longitude;
        CoordGeodetic.phi = p.latitude;
        CoordGeodetic.HeightAboveEllipsoid = (p.altitude / 1000.0); // meters to km
        CoordGeodetic.UseGeoid = 0;

        success = MAG_GeodeticToSpherical(state.ellipsoid, CoordGeodetic, &CoordSpherical); /*Convert from geodetic to Spherical Equations: 17-18, WMM Technical report*/
        if(!success)
            return TE_Err;

        success = MAG_Geomag(state.ellipsoid, CoordSpherical, CoordGeodetic, tmm.get(), value); /* Computes the geoMagnetic field elements and their time change*/
        if(!success)
            return TE_Err;

        // XXX - inspection of the source indicates that GV is always
        //       calculated regardless of return value
        success = MAG_CalculateGridVariation(CoordGeodetic, value);
#if 0
        if(!success) {
            Logger_log(TELL_Debug, "MAG_CalculateGridVariation %d", success);
            return TE_Err;
        }
#endif
        return code;
    }
}
