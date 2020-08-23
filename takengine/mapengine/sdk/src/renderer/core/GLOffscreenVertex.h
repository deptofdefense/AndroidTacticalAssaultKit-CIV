#ifndef TAK_ENGINE_CORE_GLOFFSCREEN_VERTEX_H_INCLUDED
#define TAK_ENGINE_CORE_GLOFFSCREEN_VERTEX_H_INCLUDED

#include "core/GeoPoint2.h"
#include "math/Point2.h"
#include "math/Statistics.h"
#include "port/Platform.h"
#include "port/Collection.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core 
            {

                /**
                * This is a simple structure used by the {@link GLMapView} to represent a vertex in the offscreen
                * surface for rendering
                *
                * @author CSRA
                */
                class ENGINE_API GLOffscreenVertex
                {
                    /**
                    * This is the geo point that defines the location of the vertex on the map
                    */
                public:
                    
                    TAK::Engine::Core::GeoPoint2 geo;                
                    TAK::Engine::Core::GeoPoint2 altitudeAdjustedGeo;
                    /**
                    * This point is used to store the projection for the geo point
                    */
                    Math::Point2<double> proj;

                    /**
                    * This value tells the version of the of the geo point which needs to be incremented
                    * every time it changes. It is also the base version that the {@link #projVersion} and
                    * {@link #altVersion} try to match
                    */
                    int geoVersion;

                    /**
                    * This value tells the version of the projection which helps to detect when it is not
                    * in sync with the {@link #geoVersion}
                    */
                    int projVersion;

                    /**
                    * This value tells the version of the altitude which helps to detect when it is not
                    * in sync with the {@link #geoVersion}
                    */
                    int altVersion;

                    double elevationOffset;

                    double elevationScaleFactor;

                    /**
                    * The raw elevation value for this vertex
                    */
                public:
                    /**
                    * This is the constructor for the GLOffscreenVertex
                    */
                    GLOffscreenVertex() NOTHROWS;
                    ~GLOffscreenVertex() NOTHROWS;
                    /**
                    * This will clear all the version values
                    */
                    void clearVersions() NOTHROWS;

                    /**
                    * When called this will use the altitude value stored in the {@link #geo} and then
                    * if it is valid it will update the altitude value in the {@link #altitudeAdjustedGeo}
                    * after applying the elevation offset and scale value.
                    */
                    void adjust() NOTHROWS;

                    double getElevation() const NOTHROWS;

                    double getAdjustedElevation() const NOTHROWS;

                    void setLocation(const double latitude, const double longitude) NOTHROWS;

                    void setElevation(const double elevation) NOTHROWS;

                };

                /**
                * This will take the array of vertices and compute an offset value by using taking an average
                * of all the vertices with a valid elevation set
                * @param vertices The vertices to compute the elevation offset for
                * @param count The number of vertices in the array to include so going from [0, count)
                * @return The elevation offset that was computed
                */
                Util::TAKErr GLOffscreenVertex_computeElevationStatistics(Math::Statistics *value, const GLOffscreenVertex *vertices, const std::size_t count) NOTHROWS;

            }
        }
    }
}

#endif