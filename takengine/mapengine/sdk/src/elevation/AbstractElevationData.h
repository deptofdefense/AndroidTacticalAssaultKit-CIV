#ifndef TAK_ENGINE_ELEVATION_ABSTRACT_ELEVATION_DATA_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ABSTRACT_ELEVATION_DATA_H_INCLUDED

#include "elevation/ElevationData.h"
#include "port/String.h"

///=============================================================================
///
///  class TAK::Engine::Elevation::AbstractElevationData
///
///     An abstract class that provides basic functionality for classes implementing
///     the ElevationData interface. The only method that needs to be overwridden 
///     is the ElevationData::getElevation(double latitude, double longitude) method,
///     but overriding ElevationData::getElevation(Port::Vector<atakmap::core::GeoPoint>::IteratorPtr &points, Port::Vector<double> &elevations, Hints hint)
///     is also recommended, since the default implementation ignores the hint argument.
///     See TAK::Engine::Elevation::ElevationData for documentation on method usage.
///
///==============================================================================

namespace TAK {
    namespace Engine {
        namespace Elevation {

            class ENGINE_API AbstractElevationData : public ElevationData 
            {
            public :
                AbstractElevationData(int model, Port::String type, double resolution) NOTHROWS;
            protected :
                virtual ~AbstractElevationData() NOTHROWS;
            public :
                virtual int getElevationModel() NOTHROWS;
                virtual Util::TAKErr getType(Port::String &type) NOTHROWS;
                virtual double getResolution() NOTHROWS;
                virtual Util::TAKErr getElevation(double *value, const double latitude, const double longitude) NOTHROWS = 0;

                virtual Util::TAKErr getElevation(
                    double *value,
                    Port::Collection<Core::GeoPoint2>::IteratorPtr &points,
                    const Hints &hint) NOTHROWS;
            private:
                int model;
                Port::String type;
                double resolution;
            };

        }
    }
}

#endif