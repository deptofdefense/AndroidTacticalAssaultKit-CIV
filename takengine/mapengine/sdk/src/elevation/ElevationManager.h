#ifndef TAK_ENGINE_ELEVATION_ELEVATIONMANAGER_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONMANAGER_H_INCLUDED

#include "feature/Geometry2.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "elevation/ElevationChunkCursor.h"
#include "elevation/ElevationData.h"
#include "elevation/ElevationDataSpi.h"
#include "elevation/ElevationSource.h"
#include "raster/mosaic/MosaicDatabase2.h"
#include "raster/ImageInfo.h"
#include <map>

namespace TAK {
    namespace Engine {
        namespace Elevation {

            ///=============================================================================
            ///
            ///  struct TAK::Engine::Elevation::QueryParameters
            ///
            ///     Container class that holds parameters that help to narrow down the search area
            ///     for querys performed on the ElevationManager.
            ///
            ///==============================================================================

            struct ENGINE_API ElevationManagerQueryParameters
            {
                double minResolution;
                double maxResolution;
                Feature::Geometry2Ptr spatialFilter;
                std::unique_ptr<Port::Set<Port::String>, void(*)(const Port::Set<Port::String>  *)> types;
                int elevationModel;
                bool preferSpeed;
                bool interpolate;

                ElevationManagerQueryParameters() NOTHROWS;
                ElevationManagerQueryParameters(const ElevationManagerQueryParameters &other) NOTHROWS;
            };


            ///=============================================================================
            ///
            ///     Static methods used for registering and unregistering SPI classes that are suitable for converting results
            ///     from a MosaicDatabase search into an ElevationData object that can be used to gather elevation data for given points.
            ///
            ///==============================================================================
            /**
            * Registers an Elevation source to use when querying for data. At least one database must be registered using this
            * method or else elevation cannot be queried. Additionally, at least one SPI must be specified using the TAK::Engine::Elevation::ElevationManager_registerDataSpi
            * method or else elevation cannot be queried. This method does not take ownership of the pointer passed to it, the user
            * is still responsible for manageing the memory and lifecycle of the object.
            *
            * @param database A pointer to a MosaicDatabase that should be used to query for elevation data.
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if mosaic database was successfully registered.
            */
            ENGINE_API Util::TAKErr ElevationManager_registerElevationSource(std::shared_ptr<TAK::Engine::Raster::Mosaic::MosaicDatabase2> database) NOTHROWS;

            /**
            * Unregisters an Elevation source that has been previously registered using the ElevationManager::registerElevationSource method.
            * This method will not free any memory after unregistering the database, the user is responsible for manageing the memory and lifecycle
            * of the object.
            *
            * @param database A pointer to a MosaicDatabase that has been previously registered using ElevationManager::registerElevationSource
            *                 and should no longer be used for future queries.
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if the mosaic database was successfully unregistered.
            */
            ENGINE_API Util::TAKErr ElevationManager_unregisterElevationSource(std::shared_ptr<TAK::Engine::Raster::Mosaic::MosaicDatabase2> database) NOTHROWS;

            /**
            * Queries all databases previously registered through the ElevationManager::registerElevationSource method to find sources that
            * would be suitable for retrieving elevation data.
            *
            * @param cursor OUTPUT VALUE A cursor that will be filled with entries in all registered databases that match the requirements
            *               specified by the params argument
            * @param params Paramaters that will define the area to be searched for suitable elevation sources
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if the cursor argument has been sucessfully filled and contains valid data.
            */
            ENGINE_API Util::TAKErr ElevationManager_queryElevationData(TAK::Engine::Raster::Mosaic::MosaicDatabase2::CursorPtr &cursor, const ElevationManagerQueryParameters &params) NOTHROWS;

            ENGINE_API Util::TAKErr ElevationManager_queryElevationSources(ElevationChunkCursorPtr &value, const ElevationSource::QueryParameters &params) NOTHROWS;
            ENGINE_API Util::TAKErr ElevationManager_queryElevationSourcesCount(std::size_t *value, const ElevationSource::QueryParameters &params) NOTHROWS;

            /**
            * Returns the elevation, as meters, at the specified location. A value
            * of <code>NaN</code> is returned if no elevation is available.
            *
            * @param latitude  The latitude of the location to find the elevation of.
            * @param longitude The longitude of the location to find the elevation of.
            * @param filter    Additional parameters used to filter which source to use when selecting elevation.
            *
            * @return  TAK::Engine::Elevation::Altitude The elevation value at the specified location, in meters, or
            *          <code>NaN</code> if not available.
            */
            ENGINE_API Util::TAKErr ElevationManager_getElevation(double *value, Port::String *source, const double latitude, const double longitude, const ElevationManagerQueryParameters &filter) NOTHROWS;

            ENGINE_API Util::TAKErr ElevationManager_getElevation(double *value, Port::String *source, const double latitude, const double longitude, const ElevationSource::QueryParameters &filter) NOTHROWS;

            /**
            * Returns elevation values for a set of points.
            *
            * @param points        The points representing locations to find the elevation of.
            * @param elevations    OUTPUT VALUE Returns the elevation values for the items
            *                      specified in the points argument, in the same order as the items
            *                      specified in the points argument.
            * @param filter        Additional parameters used to filter which source to use when selecting elevation.
            * @param hint          If this value contains non NAN members, specifies a minimum
            *                      bounding box containing all points. The
            *                      implementation may use this information to prefetch
            *                      all data that will be required up front, possibly
            *                      reducing IO.
            */
            ENGINE_API Util::TAKErr ElevationManager_getElevation(
                    double *elevations,
                    Port::Collection<Core::GeoPoint2>::IteratorPtr &points,
                    const ElevationManagerQueryParameters &filter,
                    const ElevationData::Hints &hint) NOTHROWS;

            ENGINE_API Util::TAKErr ElevationManager_getElevation(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride, const ElevationSource::QueryParameters &filter) NOTHROWS;

            /**
             * Returns the Geoid height at the specified location.
             */
            ENGINE_API Util::TAKErr ElevationManager_getGeoidHeight(
                    double *height,
                    const double latitude,
                    const double longitude) NOTHROWS;

            /**
            * Given a result for a MosaicDatabase query, generate a new ElevationData object.
            *
            * @param value OUTPUT VALUE Returns a pointer to a new ElevationData class that can be used to query elevation values for geographic
            *                           locations.
            * @param info Frame object that details information about an elevation source, as generated by a query to a MosaicDatabase.
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if the new ElevationData object was created successfully and contains valid data.
            */
            ENGINE_API Util::TAKErr ElevationManager_create(ElevationDataPtr &value, const Raster::ImageInfo &info) NOTHROWS;

            /**
            * Registers an SPI object suitable for generating ElevationData objects, given the results generated by a MosaicDatabase query. Registering
            * an SPI with this method is required before any results can be returned from the ElevationManager::queryElevationData method.
            *
            * @param spi An SPI object to register for use within the ElevationManager.
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if the SPI was sucessfully registered for use.
            */
            ENGINE_API Util::TAKErr ElevationManager_registerDataSpi(std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi> spi) NOTHROWS;

            /**
            * Unregisters an SPI object that was previously registered using the TAK::Engine::Elevation::ElevationManager_registerDataSpi method.
            *
            * @param spi An SPI object that was previously registered that should no longer be used for creating ElevationData objects within the ElevationManager.
            *
            * @return TAK::Engine::Util::TAKErr Returns Util::TE_Ok if the SPI was sucessfully unregistered.
            */
            ENGINE_API Util::TAKErr ElevationManager_unregisterDataSpi(std::shared_ptr<TAK::Engine::Elevation::ElevationDataSpi>spi) NOTHROWS;
        }
    }
}

#endif
