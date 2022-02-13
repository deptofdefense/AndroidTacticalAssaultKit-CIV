////============================================================================
////
////    FILE:           ImageDatasetDescriptor.cpp
////
////    DESCRIPTION:    Implementation of ImageDatasetDescriptor class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 16, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/ImageDatasetDescriptor.h"

#include <cmath>

#include "feature/Geometry.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "util/IO.h"

#define MEM_FN( fn ) \
        "atakmap::raster::ImageDatasetDescriptor::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


typedef std::map<TAK::Engine::Port::String, const feature::Geometry*, TAK::Engine::Port::StringLess>
        CoverageMap;


typedef std::map<TAK::Engine::Port::String, std::pair<double, double>, TAK::Engine::Port::StringLess>
        ResolutionMap;


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


inline
std::size_t
computeLevelCount (double minResolution,
                   double maxResolution)
  {
    return maxResolution
        ? static_cast<std::size_t>(1 + std::ceil (std::log (minResolution / maxResolution) / M_LN2))
        : 1u;
  }


inline
CoverageMap
createCoverageMap (const char* key,
                   const feature::Geometry* value)
  {
    CoverageMap result;

    result.insert (std::make_pair (TAK::Engine::Port::String (key), value));
    return result;
  }


inline
ResolutionMap
createResolutionMap (const char* key,
                     std::pair<double, double> resolutions)
  {
    ResolutionMap result;

    result.insert (std::make_pair (TAK::Engine::Port::String (key), resolutions));
    return result;
  }


inline
ResolutionMap
createResolutionMap (const char* key,
                     double maxResolution,
                     std::size_t resolutionCount)
  {
    if (!resolutionCount)
      {
        throw std::invalid_argument (MEM_FN ("createResolutionMap")
                                     "Invalid resolutionCount of 0");
      }

    return createResolutionMap (key,
                                std::make_pair
                                    (maxResolution * (1L << (resolutionCount - 1)),
                                     maxResolution));
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


ImageDatasetDescriptor::ImageDatasetDescriptor(const char* name,
                                                const char* URI,
                                                const char* provider,
                                                const char* datasetType,
                                                const char* imageryType,
                                                std::size_t width,
                                                std::size_t height,
                                                std::size_t resolutionCount,
                                                const core::GeoPoint& ul,
                                                const core::GeoPoint& ur,
                                                const core::GeoPoint& lr,
                                                const core::GeoPoint& ll,
                                                int referenceID,
                                                bool isRemote,
                                                const char* workingDir,
                                                const StringMap& extraData)
  : DatasetDescriptor(IMAGE,
                      0,
                      name,
                      URI,
                      provider,
                      datasetType,
                      StringVector (1, imageryType),
                      createResolutionMap (imageryType,
                                           computeGSD (static_cast<unsigned long>(width), static_cast<unsigned long>(height), ur, lr, ll, ul),
                                           resolutionCount),
                     createCoverageMap (imageryType,
                                        createSimpleCoverage(ul, ur, lr, ll)),
                     referenceID,
                     isRemote,
                     workingDir,
                     extraData),
    imageryType (imageryType),
    width (width),
    height (height),
    ul (ul),
    ur (ur),
    lr (lr),
    ll (ll),
    levelCount(resolutionCount),
	isPrecisionImagery(false)
  { }


ImageDatasetDescriptor::ImageDatasetDescriptor (const char* name,
                                                const char* URI,
                                                const char* provider,
                                                const char* datasetType,
                                                const char* imageryType,
                                                std::size_t width,
                                                std::size_t height,
                                                double resolution,
                                                std::size_t resolutionCount,
                                                const core::GeoPoint& ul,
                                                const core::GeoPoint& ur,
                                                const core::GeoPoint& lr,
                                                const core::GeoPoint& ll,
                                                int referenceID,
                                                bool isRemote,
                                                const char* workingDir,
                                                const StringMap& extraData)
  : DatasetDescriptor (IMAGE,
                       0,
                       name,
                       URI,
                       provider,
                       datasetType,
                       StringVector (1, imageryType),
                       createResolutionMap (imageryType,
                                            resolution,
                                            resolutionCount),
                       createCoverageMap (imageryType,
                                          createSimpleCoverage (ul, ur, lr, ll)),
                       referenceID,
                       isRemote,
                       workingDir,
                       extraData),
    imageryType (imageryType),
    width (width),
    height (height),
    ul (ul),
    ur (ur),
    lr (lr),
    ll (ll),
    levelCount(resolutionCount),
	isPrecisionImagery(false)
  { }

ImageDatasetDescriptor::ImageDatasetDescriptor(const char* name,
                                               const char* URI,
                                               const char* provider,
                                               const char* datasetType,
                                               const char* imageryType,
                                               std::size_t width,
                                               std::size_t height,
                                               double resolution,
                                               std::size_t resolutionCount,
                                               const core::GeoPoint& ul,
                                               const core::GeoPoint& ur,
                                               const core::GeoPoint& lr,
                                               const core::GeoPoint& ll,
                                               int referenceID,
                                               bool isRemote,
                                               bool precisionImagery,
                                               const char* workingDir,
                                               const StringMap& extraData)
  : DatasetDescriptor(IMAGE,
                      0,
                      name,
                      URI,
                      provider,
                      datasetType,
                      StringVector(1, imageryType),
                      createResolutionMap(imageryType,
                          resolution,
                          resolutionCount),
                      createCoverageMap(imageryType,
                          createSimpleCoverage(ul, ur, lr, ll)),
                      referenceID,
                      isRemote,
                      workingDir,
                      extraData),
    imageryType(imageryType),
    width(width),
    height(height),
    ul(ul),
    ur(ur),
    lr(lr),
    ll(ll),
    levelCount(resolutionCount),
    isPrecisionImagery(precisionImagery)
{ }
    
TAK::Engine::Util::TAKErr ImageDatasetDescriptor::clone(DatasetDescriptorUniquePtr &value) const NOTHROWS {
    value = DatasetDescriptorUniquePtr(new ImageDatasetDescriptor(*this), DatasetDescriptor::deleteDatasetDescriptor);
    return TAK::Engine::Util::TE_Ok;
}

}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


ImageDatasetDescriptor::ImageDatasetDescriptor (int64_t layerID,
                                                const char* name,
                                                const char* URI,
                                                const char* provider,
                                                const char* datasetType,
                                                const char* imageryType,
                                                std::size_t width,
                                                std::size_t height,
                                                double minResolution,
                                                double maxResolution,
                                                const feature::Geometry* coverage,
                                                int referenceID,
                                                bool isRemote,
                                                bool precisionImagery,
                                                const char* workingDir,
                                                const StringMap& extraData)
  : DatasetDescriptor (IMAGE,
                       layerID,
                       name,
                       URI,
                       provider,
                       datasetType,
                       StringVector (1, imageryType),
                       createResolutionMap (imageryType,
                                            std::make_pair
                                                (minResolution,
                                                 maxResolution)),
                       createCoverageMap (imageryType, coverage->clone ()),
                       referenceID,
                       isRemote,
                       workingDir,
                       extraData),
    imageryType (imageryType),
    width (width),
    height (height),
    levelCount (computeLevelCount (minResolution, maxResolution)),
	isPrecisionImagery(precisionImagery)
  {
    //
    // Extract the corners from the Geometry, which was originally created as a
    // Polygon by DatasetDescriptor::createSimpleCoverage.
    //

    const auto* poly
        (dynamic_cast<const feature::Polygon*> (coverage));

    if (poly)
      {
        const feature::LineString ring (poly->getExteriorRing ());
        feature::Point point (ring.getPoint (0));

        ul.latitude = point.y;
        ul.longitude = point.x;
        point = ring.getPoint (1);
        ur.latitude = point.y;
        ur.longitude = point.x;
        point = ring.getPoint (2);
        lr.latitude = point.y;
        lr.longitude = point.x;
        point = ring.getPoint (3);
        ll.latitude = point.y;
        ll.longitude = point.x;
      }
    else
      {
        //
        // Inconceivable!  Fall back to using the Envelope.
        //

        feature::Envelope env (coverage->getEnvelope ());

        ul.longitude = ll.longitude = env.minX;
        ll.latitude = lr.latitude = env.minY;
        ur.longitude = lr.longitude = env.maxX;
        ul.latitude = ur.latitude = env.maxY;
      }
  }


///
///  atakmap::raster::DatasetDescriptor member functions.
///


void
ImageDatasetDescriptor::encodeDetails (std::ostream& strm)
    throw (util::IO_Error)
  {
    util::write<int> (strm, static_cast<int>(width));
    util::write<int> (strm, static_cast<int>(height));
    util::write<bool>(strm, isPrecisionImagery);
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.
