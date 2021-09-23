////============================================================================
////
////    FILE:           DatasetDescriptor.cpp
////
////    DESCRIPTION:    Implementation of the DatasetDescriptor class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 9, 2014   scott           Created.
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


#include "raster/DatasetDescriptor.h"

#include <algorithm>
#include <cstring>
#include <istream>
#include <iterator>
#include <limits>
#include <sstream>
#include <stdint.h>
#include <string>

#include "feature/Geometry.h"
#include "feature/GeometryCollection.h"
#include "feature/LineString.h"
#include "feature/ParseGeometry.h"
#include "feature/Polygon.h"
#include "feature/SpatialCalculator.h"
#include "port/StringBuilder.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/MosaicDatasetDescriptor.h"
#include "spi/ServiceProviderRegistry.h"
#include "util/Distance.h"

#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"

#ifdef MSVC
#include "vscompat.h"
#endif

#define MEM_FN( fn )    "atakmap::raster::DatasetDescriptor::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;

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


struct CoverageCloner
  : std::unary_function<CoverageMap::value_type, CoverageMap::value_type>
  {
    CoverageMap::value_type
    operator() (const CoverageMap::value_type& value)
        const
      { return CoverageMap::value_type (value.first, value.second->clone ()); }
  };


struct CoverageDeleter
  : std::unary_function<CoverageMap::value_type, void>
  {
    void
    operator() (const CoverageMap::value_type& value)
        const
      { delete value.second; }
  };



typedef std::map<TAK::Engine::Port::String,
                 const raster::DatasetDescriptor::Factory*,
                 TAK::Engine::Port::StringLess>
        FactoryMap;


class FactoryRegistry
  : public spi::ServiceProviderRegistry<raster::DatasetDescriptor::Factory>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Returns the (possibly NULL) DatasetDescriptor::Factory for the supplied
    // strategy.
    //
    const raster::DatasetDescriptor::Factory*
    getFactory (const char* strategy)
      {
        RWMutex& mutex (getMutex ());
        ReadLock lock(mutex);
        Range range (getStrategyRange (strategy));

        return range.first != range.second
            ? range.first->provider
            : nullptr;
      }
  };


typedef std::map<TAK::Engine::Port::String, std::pair<double, double>, TAK::Engine::Port::StringLess>
        ResolutionMap;


typedef std::map<TAK::Engine::Port::String, TAK::Engine::Port::String, TAK::Engine::Port::StringLess>
        StringMap;


typedef std::vector<TAK::Engine::Port::String>
        StringVector;


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
FactoryRegistry&
getRegistry ()
  {
    static FactoryRegistry registry;

    return registry;
  }


//
// Returns a map of coverages, keyed by imageryType, including the coverage for
// the whole dataset (with the NULL key).
//
CoverageMap
validateCoverages (const CoverageMap& coverages,
                   const std::vector<TAK::Engine::Port::String>& imageryTypes)
  {
    CoverageMap result;

    for (StringVector::size_type i (0); i < imageryTypes.size (); ++i)
      {
        TAK::Engine::Port::String imageryType (imageryTypes[i]);
        auto iter (coverages.find (imageryType));

        if (iter == coverages.end () || !iter->second)
          {
            std::ostringstream errString;
            errString << 
                MEM_FN ("validateCoverages") <<
                                   "No coverage found for imagery type " <<
                                   imageryType;

            throw std::invalid_argument (errString.str());
          }
        result.insert(*iter);
      }

    //
    // Add the MBR of the result as the coverage for the dataset.
    //

    CoverageMap::const_iterator entry;

    entry = coverages.find(TAK::Engine::Port::String());
    if (entry != coverages.end() && entry->second) {
        result.insert(std::make_pair(entry->first, entry->second));
    }
    else {
        std::unique_ptr<feature::Geometry> cov;
        const feature::Geometry *geom;
        for (entry = coverages.begin(); entry != coverages.end(); entry++)
        {
            geom = entry->second;
            if (!cov.get()) {
                cov.reset(geom->clone());
            }
            else {
                if (cov->getType() != feature::Geometry::COLLECTION) {
                    auto *c = new feature::GeometryCollection(feature::Geometry::_2D);
                    c->add(cov.get());
                    cov.reset(c);
                }

                static_cast<feature::GeometryCollection *>(cov.get())->add(geom);
            }
        }

        if (cov->getType() == feature::Geometry::COLLECTION) {
            feature::SpatialCalculator calc;
            feature::SpatialCalculator::Batch batch(&calc);
            feature::Geometry *aggregate = calc.getGeometry(calc.unaryUnion(calc.createGeometry(cov.get())));
            if (aggregate) {
                cov.reset(aggregate->clone());
                calc.destroyGeometry(aggregate);
            }
        }

        result.insert(std::make_pair(TAK::Engine::Port::String(), cov.release()));
    }

    return result;
  }


//
// Returns a sorted vector of unique strings, with NULL removed.
//
std::vector<TAK::Engine::Port::String>
validateImageryTypes (const std::vector<TAK::Engine::Port::String>& types)
  {
    std::set<TAK::Engine::Port::String, TAK::Engine::Port::StringLess> typeSet (types.begin (),
                                                      types.end ());

    typeSet.erase (TAK::Engine::Port::String());    // Remove any NULL entry.
    if (typeSet.empty ())
      {
        throw std::invalid_argument (MEM_FN ("validateImageryTypes")
                                     "Received empty imagery types vector");
      }

    return std::vector<TAK::Engine::Port::String> (typeSet.begin (), typeSet.end ());
  }


//
// Returns a map of resolution ranges, keyed by imageryType, including the
// resolution range supported by the whole dataset (with the NULL key).
//
ResolutionMap
validateResolutions (const ResolutionMap& resolutions,
                     const std::vector<TAK::Engine::Port::String>& imageryTypes)
  {
    ResolutionMap result;

    //
    // Compute the resolution range for the dataset as the intersection of the
    // ranges for the imagery types.
    //

    double minRes (std::numeric_limits<double>::min ());
    double maxRes (std::numeric_limits<double>::max ());

    for (StringVector::size_type i (0); i < imageryTypes.size (); ++i)
      {
        TAK::Engine::Port::String imageryType (imageryTypes[i]);
        auto iter (resolutions.find (imageryType));

        if (iter == resolutions.end ())
          {
            std::ostringstream errString;
            errString << MEM_FN ("validateResolutions") <<
                                   "No resolution range found for imagery type " <<
                                   imageryType;

            throw std::invalid_argument (errString.str());
          }
        result.insert (*iter);
        minRes = std::max (minRes, iter->second.first);
        maxRes = std::min (maxRes, iter->second.second);
      }

    result.insert (std::make_pair (TAK::Engine::Port::String(),
                                   std::make_pair (minRes, maxRes)));

    return result;
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


const char* const DatasetDescriptor::URI_CHARACTER_ENCODING = "UTF-8";


DatasetDescriptor::~DatasetDescriptor ()
    NOTHROWS
  {
    std::for_each (coverages_.begin (), coverages_.end (), CoverageDeleter ());
  }


const feature::Geometry*
DatasetDescriptor::getCoverage (const char* imageryType)
    const
    NOTHROWS
  {
    auto iter (coverages_.find (imageryType));

    return iter != coverages_.end () ? iter->second : NULL;
  }


const char*
DatasetDescriptor::getExtraData (const char* key)
    const
    NOTHROWS
  {
    auto iter (extra_data_.find (key));

    return iter != extra_data_.end ()
        ? static_cast<const char*> (iter->second)
        : nullptr;
  }


const char*
DatasetDescriptor::getExtraData (const DatasetDescriptor& descriptor,
                                 const char* key,
                                 const char* defaultValue)
    NOTHROWS
  {
    const char* extraData (descriptor.getExtraData (key));

    return !extraData ? defaultValue : extraData;
  }


const void*
DatasetDescriptor::getLocalData (const char* key)
    const
    NOTHROWS
  {
    auto iter
        (local_data_.find (key));

    return iter != local_data_.end () ? iter->second : nullptr;
  }

double
DatasetDescriptor::computeGSD (unsigned long width,
                               unsigned long height,
                               const core::GeoPoint& ul,
                               const core::GeoPoint& ur,
                               const core::GeoPoint& lr,
                               const core::GeoPoint& ll)
    NOTHROWS
  {
    double rangeUL_LR (util::distance::calculateRange (ul, lr));
    double rangeUR_LL (util::distance::calculateRange (ur, ll));

    return sqrt (rangeUL_LR * rangeUR_LL) / hypot (width, height);
  }


DatasetDescriptor::DescriptorSet*
DatasetDescriptor::create (const char* filePath,
                           const char* workingDir,
                           const char* typeHint,
                           CreationCallback* callback)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("create") "Received NULL filePath");
      }

    FactoryArgs args (filePath, workingDir);

    return getRegistry ().create (args, typeHint, callback);
  }


feature::Geometry*
DatasetDescriptor::createSimpleCoverage (const core::GeoPoint& ul,
                                         const core::GeoPoint& ur,
                                         const core::GeoPoint& lr,
                                         const core::GeoPoint& ll)
    NOTHROWS
  {
    feature::LineString bounds (feature::Geometry::_2D);

    bounds.addPoint (ul.longitude, ul.latitude);
    bounds.addPoint (ur.longitude, ur.latitude);
    bounds.addPoint (lr.longitude, lr.latitude);
    bounds.addPoint (ll.longitude, ll.latitude);
    bounds.addPoint (ul.longitude, ul.latitude);
    return new feature::Polygon (bounds);
  }


DatasetDescriptor*
DatasetDescriptor::decode (const ByteBuffer& buffer)
    throw (util::IO_Error)
  {
    std::istringstream strm (std::ios_base::in | std::ios_base::binary);

    strm.str (std::string (buffer.first, buffer.second));
    return decode (strm);
  }


DatasetDescriptor*
DatasetDescriptor::decode (std::istream& strm)
    throw (util::IO_Error)
  {
    static char validHeader[3] = { 0x00, 0x01, static_cast<char>(0xFFu) };
    char header[3] = { };

    if (!strm.read (header, 3))
      {
        throw util::IO_Error (MEM_FN ("decode") "Unexpected EOF");
      }

    if (std::strncmp (header, validHeader, 3))
      {
        throw util::IO_Error (MEM_FN ("decode")
                              "Unsupported DatasetDescriptor serialization");
      }

    int version (strm.get ());

    if (version < 0)
      {
        throw util::IO_Error (MEM_FN ("decode") "Unexpected EOF");
      }
    if (version == 0x07)
      {
        return decodeVersion7(strm);
      }
    else if (version == 0x08)
      {
        return decodeVersion8(strm);
      }
    else if (version == 0x09)
      {
        return decodeVersion9(strm);
      }
    else if (version == 0x0A)
      {
        return decodeVersion10(strm);
      }
    else
      {
        throw util::IO_Error (MEM_FN ("decode")
                              "Unsupported DatasetDescriptor serialization");
      }
  }


DatasetDescriptor::ByteBuffer
DatasetDescriptor::encode (int64_t layerID)
    throw (util::IO_Error)
  {
    std::ostringstream strm (std::ios_base::out | std::ios_base::binary);

    encode (layerID, strm);

    std::string encoding (strm.str ());
    std::size_t length (encoding.size ());
    auto* buff (new unsigned char[length]);

    std::memcpy (buff, encoding.data (), length);
    return atakmap::util::makeBlobWithDeleteCleanup(buff, buff + length);
  }


void
DatasetDescriptor::encode (int64_t layer_id,
                           std::ostream& strm)
    throw (util::IO_Error)
  {
    using namespace util;

    strm.put (static_cast<char>(0x00)).put (static_cast<char>(0x01)).put (static_cast<char>(0xFFu)).put (static_cast<char>(0x0A));   // Header & version.
    write<int> (strm, type_);
    write (strm, layer_id);
    writeUTF (strm, name_);
      
      TAK::Engine::Port::String URIStr = uri_.get();
      TAK::Engine::Util::File_getStoragePath(URIStr);
      uri_ = static_cast<const char *>(URIStr.get());
      
    writeUTF (strm, uri_);
    writeUTF (strm, provider_);
    writeUTF (strm, dataset_type_);
    write<int> (strm, static_cast<int>(imagery_types_.size ()));

    for (StringVector::const_iterator iter (imagery_types_.begin ());
         iter != imagery_types_.end ();
         ++iter)
      {
        const TAK::Engine::Port::String& iType (*iter);
        ResolutionMap::const_iterator resIter (resolutions_.find (iType));
        const feature::Geometry* coverage (getCoverage (iType));

        writeUTF (strm, iType);
        write (strm, resIter->second.first);
        write (strm, resIter->second.second);
        write<int> (strm, static_cast<int>(coverage->computeWKB_Size ()));
        coverage->toWKB (strm);
      }

    // write aggregate coverage
    write<int>(strm, static_cast<int>(getCoverage()->computeWKB_Size()));
    getCoverage()->toWKB(strm);

    write (strm, spatial_reference_id_);
    write (strm, isRemote ());
    write (strm, !!working_directory_);
    if (working_directory_)
      {
        writeUTF (strm, working_directory_);
      }
    write<uint32_t> (strm, static_cast<uint32_t>(extra_data_.size ()));
    for (StringMap::const_iterator iter (extra_data_.begin ());
         iter != extra_data_.end ();
         ++iter)
      {
        writeUTF (strm, iter->first);
        writeUTF (strm, iter->second);
      }

    encodeDetails (strm);               // Add derived class encoded content.
  }


TAK::Engine::Port::String
DatasetDescriptor::formatImageryType (const char* baseType,
                                      double resolution)
    NOTHROWS
  {
    StringBuilder strm;

    strm << baseType;
    if (resolution < 1)
      {
        int cm (static_cast<int>(round (resolution * 10) * 10));

        if (cm == 100)
          {
            strm << '1';
          }
        else
          {
            strm << "0_" << cm;
          }
      }
    else
      {
        int meters = static_cast<int>(ceil (resolution));

        if (meters > 1000)
          {
            meters = static_cast<int>(ceil (meters / 100.0) * 100);
          }
        else if (meters > 100)
          {
            meters = static_cast<int>(ceil (meters / 25.0) * 25);
          }
        else if (meters > 10)
          {
            meters = static_cast<int>(ceil (meters / 5.0) * 5);
          }
        strm << meters;
      }

    return strm.c_str ();
  }

TAK::Engine::Port::String
DatasetDescriptor::formatResolution(double resolution) throw()
  {
    std::ostringstream strm;
    if (resolution < 1) {
        const int cm = (int)round(resolution * 10) * 10;
        if (cm == 100) { // XXX - 
            strm << "1m";
        }
        else {
            strm << cm << "cm";
        }
    } else {
        const int meters = (int)round(resolution);
        if (meters > 1000){
            strm << (int)round(meters / 1000.0) << "km";
        }
        else if (meters > 100) {
            strm << ((int)round(meters / 25.0) * 25) << "m";
        }
        else if (meters > 10) {
            strm << ((int)round(meters / 5.0) * 5) << "m";
        }
        else {
            strm << meters << "m";
        }
    }

    return strm.str().c_str();
  }

const DatasetDescriptor::Factory*
DatasetDescriptor::getFactory (const char* factoryType)
  { return getRegistry ().getFactory (factoryType); }


double
DatasetDescriptor::getMaxResolution (const char* imageryType)
    const
    NOTHROWS
  {
    auto iter (resolutions_.find (imageryType));

    return iter != resolutions_.end ()
        ? iter->second.second
        : std::numeric_limits<double>::quiet_NaN ();
  }


double
DatasetDescriptor::getMinResolution (const char* imageryType)
    const
    NOTHROWS
  {
    auto iter (resolutions_.find (imageryType));

    return iter != resolutions_.end ()
        ? iter->second.first
        : std::numeric_limits<double>::quiet_NaN ();
  }

void DatasetDescriptor::deleteDatasetDescriptor(const atakmap::raster::DatasetDescriptor *datasetDescriptor) NOTHROWS {
    delete datasetDescriptor;
}

bool
DatasetDescriptor::isSupported (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("isSupported")
                                     "Received NULL filePath");
      }

    return getRegistry ().isSupported (FactoryArgs (filePath, NULL),
                                       DEFAULT_PROBE);
  }


void
DatasetDescriptor::registerFactory (const Factory* factory)
  {
    if (factory && factory->getStrategy ())
      {
        getRegistry ().registerProvider (factory);
      }
  }


const void*
DatasetDescriptor::setLocalData (const char* key,
                                 const void* value)
    NOTHROWS
  {
    const void* oldValue (local_data_[key]);

    local_data_[key] = value;
    return oldValue;
  }


void
DatasetDescriptor::unregisterFactory (const Factory* factory)
  {
    if (factory && factory->getStrategy ())
      {
        getRegistry ().unregisterProvider (factory);
      }
  }


///=====================================
///  DatasetDescriptor::Factory MEMBER FUNCTIONS
///=====================================


DatasetDescriptor::DescriptorSet*
DatasetDescriptor::Factory::create (const FactoryArgs& args,
                                    const char* strategy,
                                    CreationCallback* callback)
    const
  {
    DescriptorSet* result (nullptr);

    if (!args.first)
      {
        throw std::invalid_argument (MEM_FN ("Factory::create")
                                     "Received FactoryArgs with NULL filePath");
      }

    if (!strategy || !std::strcmp (strategy, getStrategy ()))
      {
        if (callback && callback->isProbeOnly ())
          {
            callback->setProbeResult (probeFile (args.first, *callback));
          }
        else
          {
            result = createImpl (args.first, args.second, callback);
          }
      }

    return result;
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


DatasetDescriptor::DatasetDescriptor (Type type,
                                      int64_t layer_id,
                                      const char* name,
                                      const char* uri,
                                      const char* provider,
                                      const char* dataset_type,
                                      const StringVector& imagery_types_arg,
                                      const ResolutionMap& resolutions_arg,
                                      const CoverageMap& coverages_arg,
                                      int reference_id,
                                      bool is_remote,
                                      const char* working_dir,
                                      const StringMap& extra_data)
  : type_ (type),
    layer_id_ (layer_id),
    spatial_reference_id_ (reference_id),
    name_ (name),
    uri_ (uri),
    provider_ (provider),
    dataset_type_ (dataset_type),
    working_directory_ (working_dir),
    imagery_types_ (validateImageryTypes (imagery_types_arg)),
    coverages_ (validateCoverages (coverages_arg, imagery_types_)),
    resolutions_ (validateResolutions (resolutions_arg, imagery_types_)),
    extra_data_ (extra_data),
    minimum_bounding_box_ (coverages_[TAK::Engine::Port::String()]->getEnvelope()),
    is_local_ (!is_remote)
  {
    if (!name)
      {
        throw std::invalid_argument (MEM_FN ("DatasetDescriptor")
                                     "Received NULL dataset name");
      }
    if (!uri)
      {
        throw std::invalid_argument (MEM_FN ("DatasetDescriptor")
                                     "Received NULL dataset URI");
      }
    if (!provider)
      {
        throw std::invalid_argument (MEM_FN ("DatasetDescriptor")
                                     "Received NULL dataset service provider");
      }
    if (!dataset_type)
      {
        throw std::invalid_argument (MEM_FN ("DatasetDescriptor")
                                     "Received NULL dataset type");
      }
  }


DatasetDescriptor::DatasetDescriptor (const DatasetDescriptor& rhs)
  : type_ (rhs.type_),
    layer_id_ (rhs.layer_id_),
    spatial_reference_id_ (rhs.spatial_reference_id_),
    name_ (rhs.name_),
    uri_ (rhs.uri_),
    provider_ (rhs.provider_),
    dataset_type_ (rhs.dataset_type_),
    working_directory_ (rhs.working_directory_),
    imagery_types_ (rhs.imagery_types_),
    coverages_ (cloneCoverageMap (rhs.coverages_)),
    resolutions_ (rhs.resolutions_),
    extra_data_ (rhs.extra_data_),
    minimum_bounding_box_ (rhs.minimum_bounding_box_),
    is_local_ (rhs.is_local_),
    local_data_ (rhs.local_data_)
  { }


DatasetDescriptor&
DatasetDescriptor::operator= (const DatasetDescriptor& rhs)
  {
    if (&rhs != this)
      {
        type_ = rhs.type_;
        layer_id_ = rhs.layer_id_;
        spatial_reference_id_ = rhs.spatial_reference_id_;
        name_ = rhs.name_;
        uri_ = rhs.uri_;
        provider_ = rhs.provider_;
        dataset_type_ = rhs.dataset_type_;
        working_directory_ = rhs.working_directory_;
        imagery_types_ = rhs.imagery_types_;
        coverages_ = cloneCoverageMap (rhs.coverages_);
        resolutions_ = rhs.resolutions_;
        extra_data_ = rhs.extra_data_;
        minimum_bounding_box_ = rhs.minimum_bounding_box_;
        is_local_ = rhs.is_local_;
        local_data_ = rhs.local_data_;
      }

    return *this;
  }

CoverageMap
DatasetDescriptor::cloneCoverageMap(const CoverageMap& coverageMap)
  {
    CoverageMap result;
    std::transform(coverageMap.begin(), coverageMap.end(),
        std::inserter(result, result.end()),
        CoverageCloner());
    return result;
  }

DatasetDescriptor::Factory::Factory (const char* strategy,
                                     unsigned int priority)
  : strategy_ (strategy),
    priority_ (priority)
  {
    if (!strategy)
      {
        throw std::invalid_argument (MEM_FN ("Factory::Factory")
                                     "Received NULL strategy");
      }
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


DatasetDescriptor*
DatasetDescriptor::decodeVersion7 (std::istream& strm)
    throw (util::IO_Error)
  {
    using namespace util;

    DatasetDescriptor* result (nullptr);
    Type descriptorType (read<Type> (strm));
    int64_t layerID (read<int32_t> (strm));
    TAK::Engine::Port::String name (readUTF (strm));
    TAK::Engine::Port::String URI (readUTF (strm));
    TAK::Engine::Port::String provider (readUTF (strm));
    TAK::Engine::Port::String datasetType (readUTF (strm));
    std::size_t typeCount (read<uint32_t> (strm));
    StringVector imageryTypes (typeCount);
    ResolutionMap resolutions;
    CoverageMap coverages;
    std::vector<std::unique_ptr<const feature::Geometry>> coverageReaper;

    for (std::size_t i (0); i < typeCount; ++i)
      {
        TAK::Engine::Port::String imageryType (readUTF (strm));
        auto minRes = read<double>(strm);
        auto maxRes = read<double>(strm);

        imageryTypes[i] = imageryType;
        resolutions.insert (std::make_pair (imageryType,
                                            std::make_pair (minRes, maxRes)));
        read<uint32_t> (strm);               // Gobble WKB size.
        std::unique_ptr<feature::Geometry> coverage (feature::parseWKB (strm));

        if (!coverage)
          {
            throw IO_Error (MEM_FN ("decode")
                            "Parse of dataset coverage failed");
          }
        coverages.insert (std::make_pair (imageryType, coverage.get()));
        coverageReaper.emplace_back(std::move(coverage));
      }

    int referenceID (read<int32_t> (strm));
    bool isRemote (read<bool> (strm));
    TAK::Engine::Port::String workingDirectory (read<bool> (strm) ? readUTF (strm) : nullptr);
    std::size_t dataCount (read<uint32_t> (strm));
    StringMap extraData;

    for (std::size_t i (0); i < dataCount; ++i)
      {
        TAK::Engine::Port::String k(readUTF(strm));
        TAK::Engine::Port::String v(readUTF(strm));

        extraData[k.get()] = v.get();
      }

    switch (descriptorType)
      {
      case IMAGE:
          {
            std::size_t width (read<uint32_t> (strm));
            std::size_t height (read<uint32_t> (strm));
            TAK::Engine::Port::String imageType (imageryTypes.front ());
            ResolutionMap::const_iterator resIter (resolutions.find (imageType));
            CoverageMap::const_iterator covIter (coverages.find (imageType));

            if (resIter == resolutions.end ())
              {
                std::ostringstream msg;
                msg << MEM_FN("decode") <<
                    "Resolutions not found for \"" <<
                    imageType <<
                    "\" imagery type";
                throw IO_Error (msg.str().c_str());
              }
            if (covIter == coverages.end ())
              {
                std::ostringstream msg;
                msg << MEM_FN("decode") <<
                    "Coverage not found for \"" <<
                    imageType <<
                    "\" imagery type";
                throw IO_Error (msg.str().c_str());
              }

            result = new ImageDatasetDescriptor (layerID,
                                                 name.get(),
                                                 URI.get(),
                                                 provider.get(),
                                                 datasetType.get(),
                                                 imageType.get(),
                                                 width,
                                                 height,
                                                 resIter->second.first,
                                                 resIter->second.second,
                                                 covIter->second,
                                                 referenceID,
                                                 isRemote,
                                                 false,
                                                 workingDirectory.get(),
                                                 extraData);
          }
        break;

      case MOSAIC:
          {
            TAK::Engine::Port::String mosaicFile (read<bool> (strm) ? readUTF (strm) : nullptr);
            TAK::Engine::Port::String mosaicProvider (read<bool> (strm)
                                             ? readUTF (strm)
                                             : nullptr);

            result = new MosaicDatasetDescriptor (layerID,
                                                  name.get(),
                                                  URI.get(),
                                                  provider.get(),
                                                  datasetType.get(),
                                                  mosaicFile.get(),
                                                  mosaicProvider.get(),
                                                  imageryTypes,
                                                  resolutions,
                                                  coverages,
                                                  referenceID,
                                                  isRemote,
                                                  workingDirectory.get(),
                                                  extraData);
          }
        break;
      }

    return result;
  }

DatasetDescriptor*
DatasetDescriptor::decodeVersion8(std::istream& strm)
      throw (util::IO_Error)
  {
      using namespace util;

      DatasetDescriptor* result(nullptr);
      Type descriptorType(read<Type>(strm));
      auto layerID(read<int64_t>(strm));
      TAK::Engine::Port::String name(readUTF(strm));
      TAK::Engine::Port::String URI(readUTF(strm));
      TAK::Engine::Port::String provider(readUTF(strm));
      TAK::Engine::Port::String datasetType(readUTF(strm));
      std::size_t typeCount(read<uint32_t>(strm));
      StringVector imageryTypes(typeCount);
      ResolutionMap resolutions;
      CoverageMap coverages;
      std::vector<std::unique_ptr<const feature::Geometry>> coverageReaper;

      for (std::size_t i(0); i < typeCount; ++i)
      {
          TAK::Engine::Port::String imageryType(readUTF(strm));
          auto minRes = read<double>(strm);
          auto maxRes = read<double>(strm);

          imageryTypes[i] = imageryType.get();
          resolutions.insert(std::make_pair(imageryType.get(),
              std::make_pair(minRes, maxRes)));
          read<uint32_t>(strm);               // Gobble WKB size.
          std::unique_ptr<feature::Geometry> coverage(feature::parseWKB(strm));

          if (!coverage)
          {
              throw IO_Error(MEM_FN("decode")
                  "Parse of dataset coverage failed");
          }
          coverages.insert(std::make_pair(imageryType.get(), coverage.get()));
          coverageReaper.emplace_back(std::move(coverage));
      }

      int referenceID(read<int32_t>(strm));
      bool isRemote(read<bool>(strm));
      TAK::Engine::Port::String workingDirectory(read<bool>(strm) ? readUTF(strm) : nullptr);
      std::size_t dataCount(read<uint32_t>(strm));
      StringMap extraData;

      for (std::size_t i(0); i < dataCount; ++i)
      {
          TAK::Engine::Port::String k(readUTF(strm));
          TAK::Engine::Port::String v(readUTF(strm));

          extraData.insert(std::make_pair(k.get(), v.get()));
      }

      switch (descriptorType)
      {
      case IMAGE:
      {
          std::size_t width(read<uint32_t>(strm));
          std::size_t height(read<uint32_t>(strm));
          TAK::Engine::Port::String imageType(imageryTypes.front());
          ResolutionMap::const_iterator resIter(resolutions.find(imageType));
          CoverageMap::const_iterator covIter(coverages.find(imageType));

          if (resIter == resolutions.end())
          {
                std::ostringstream msg;
                msg << MEM_FN("decode") <<
                    "Resolutions not found for \"" <<
                    imageType <<
                    "\" imagery type";
                throw IO_Error (msg.str().c_str());
          }
          if (covIter == coverages.end())
          {
                std::ostringstream msg;
                msg << MEM_FN("decode") <<
                    "Coverages not found for \"" <<
                    imageType <<
                    "\" imagery type";
                throw IO_Error (msg.str().c_str());
          }

          result = new ImageDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              imageType.get(),
              width,
              height,
              resIter->second.first,
              resIter->second.second,
              covIter->second,
              referenceID,
              isRemote,
              false,
              workingDirectory.get(),
              extraData);
      }
      break;

      case MOSAIC:
      {
          TAK::Engine::Port::String mosaicFile(read<bool>(strm) ? readUTF(strm) : nullptr);
          TAK::Engine::Port::String mosaicProvider(read<bool>(strm)
              ? readUTF(strm)
              : nullptr);

          result = new MosaicDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              mosaicFile.get(),
              mosaicProvider.get(),
              imageryTypes,
              resolutions,
              coverages,
              referenceID,
              isRemote,
              workingDirectory.get(),
              extraData);
      }
      break;
      }

      return result;
  }

DatasetDescriptor*
DatasetDescriptor::decodeVersion9(std::istream& strm)
      throw (util::IO_Error)
  {
      using namespace util;

      DatasetDescriptor* result(nullptr);
      Type descriptorType(read<Type>(strm));
      auto layerID(read<int64_t>(strm));
      TAK::Engine::Port::String name(readUTF(strm));
      TAK::Engine::Port::String uri(readUTF(strm));
      TAK::Engine::Port::String provider(readUTF(strm));
      TAK::Engine::Port::String datasetType(readUTF(strm));
      std::size_t typeCount(read<uint32_t>(strm));
      StringVector imageryTypes(typeCount);
      ResolutionMap resolutions;
      CoverageMap coverages;
      std::vector<std::unique_ptr<const feature::Geometry>> coverageReaper;

      TAK::Engine::Port::String URI = uri.get();
      TAK::Engine::Util::File_getRuntimePath(URI);
      
      for (std::size_t i(0); i < typeCount; ++i)
      {
          TAK::Engine::Port::String imageryType(readUTF(strm));
          auto minRes = read<double>(strm);
          auto maxRes = read<double>(strm);

          imageryTypes[i] = imageryType.get();
          resolutions.insert(std::make_pair(imageryType.get(),
              std::make_pair(minRes, maxRes)));
          read<uint32_t>(strm);               // Gobble WKB size.
          std::unique_ptr<feature::Geometry> coverage(feature::parseWKB(strm));

          if (!coverage)
          {
              throw IO_Error(MEM_FN("decode")
                  "Parse of dataset coverage failed");
          }
          coverages.insert(std::make_pair(imageryType.get(), coverage.get()));
          coverageReaper.emplace_back(std::move(coverage));
      }

      read<uint32_t>(strm);               // Gobble WKB size.
      std::unique_ptr<feature::Geometry> coverage(feature::parseWKB(strm));

      coverages.insert(std::make_pair(TAK::Engine::Port::String(), coverage.get()));

      int referenceID(read<int32_t>(strm));
      bool isRemote(read<bool>(strm));
      TAK::Engine::Port::String workingDirectory(read<bool>(strm) ? readUTF(strm) : nullptr);
      std::size_t dataCount(read<uint32_t>(strm));
      StringMap extraData;

      for (std::size_t i(0); i < dataCount; ++i)
      {
          TAK::Engine::Port::String k(readUTF(strm));
          TAK::Engine::Port::String v(readUTF(strm));

          extraData.insert(std::make_pair(k.get(), v.get()));
      }

      switch (descriptorType)
      {
      case IMAGE:
      {
          std::size_t width(read<uint32_t>(strm));
          std::size_t height(read<uint32_t>(strm));
          TAK::Engine::Port::String imageType(imageryTypes.front());
          ResolutionMap::const_iterator resIter(resolutions.find(imageType));
          CoverageMap::const_iterator covIter(coverages.find(imageType));

          if (resIter == resolutions.end())
          {
              std::ostringstream msg;
              msg << MEM_FN("decode") <<
                  "Resolutions not found for \"" <<
                  imageType <<
                  "\" imagery type";
              throw IO_Error(msg.str().c_str());
          }
          if (covIter == coverages.end())
          {
              std::ostringstream msg;
              msg << MEM_FN("decode") <<
                  "Coverage not found for \"" <<
                  imageType <<
                  "\" imagery type";
              throw IO_Error(msg.str().c_str());
          }

          result = new ImageDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              imageType.get(),
              width,
              height,
              resIter->second.first,
              resIter->second.second,
              covIter->second,
              referenceID,
              isRemote,
              false,
              workingDirectory.get(),
              extraData);
      }
      break;

      case MOSAIC:
      {
          TAK::Engine::Port::String mosaicFile(read<bool>(strm) ? readUTF(strm) : nullptr);
          TAK::Engine::Port::String mosaicProvider(read<bool>(strm)
              ? readUTF(strm)
              : nullptr);

          result = new MosaicDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              mosaicFile.get(),
              mosaicProvider.get(),
              imageryTypes,
              resolutions,
              coverages,
              referenceID,
              isRemote,
              workingDirectory.get(),
              extraData);
      }
      break;
      }

      return result;
  }

  DatasetDescriptor*
  DatasetDescriptor::decodeVersion10(std::istream& strm)
      throw (util::IO_Error)
  {
      using namespace util;

      DatasetDescriptor* result(nullptr);
      Type descriptorType(read<Type>(strm));
      auto layerID(read<int64_t>(strm));
      TAK::Engine::Port::String name(readUTF(strm));
      TAK::Engine::Port::String uri(readUTF(strm));
      TAK::Engine::Port::String provider(readUTF(strm));
      TAK::Engine::Port::String datasetType(readUTF(strm));
      std::size_t typeCount(read<uint32_t>(strm));
      StringVector imageryTypes(typeCount);
      ResolutionMap resolutions;
      CoverageMap coverages;
      std::vector<std::unique_ptr<const feature::Geometry>> coverageReaper;

      TAK::Engine::Port::String URI = uri.get();
      TAK::Engine::Util::File_getRuntimePath(URI);

      for (std::size_t i(0); i < typeCount; ++i)
      {
          TAK::Engine::Port::String imageryType(readUTF(strm));
          auto minRes = read<double>(strm);
          auto maxRes = read<double>(strm);

          imageryTypes[i] = imageryType.get();
          resolutions.insert(std::make_pair(imageryType.get(),
              std::make_pair(minRes, maxRes)));
          read<uint32_t>(strm);               // Gobble WKB size.
          std::unique_ptr<feature::Geometry> coverage(feature::parseWKB(strm));

          if (!coverage)
          {
              throw IO_Error(MEM_FN("decode")
                  "Parse of dataset coverage failed");
          }
          coverages.insert(std::make_pair(imageryType.get(), coverage.get()));
          coverageReaper.emplace_back(std::move(coverage));
      }

      read<uint32_t>(strm);               // Gobble WKB size.
      std::unique_ptr<feature::Geometry> coverage(feature::parseWKB(strm));

      coverages.insert(std::make_pair(TAK::Engine::Port::String(), coverage.get()));

      int referenceID(read<int32_t>(strm));
      bool isRemote(read<bool>(strm));
      TAK::Engine::Port::String workingDirectory(read<bool>(strm) ? readUTF(strm) : nullptr);
      std::size_t dataCount(read<uint32_t>(strm));
      StringMap extraData;

      for (std::size_t i(0); i < dataCount; ++i)
      {
          TAK::Engine::Port::String k(readUTF(strm));
          TAK::Engine::Port::String v(readUTF(strm));

          extraData[k.get()] = v.get();
      }

      switch (descriptorType)
      {
      case IMAGE:
      {
          std::size_t width(read<uint32_t>(strm));
          std::size_t height(read<uint32_t>(strm));
          bool precisionImagery(read<bool>(strm));
          TAK::Engine::Port::String imageType(imageryTypes.front());
          ResolutionMap::const_iterator resIter(resolutions.find(imageType));
          CoverageMap::const_iterator covIter(coverages.find(imageType));

          if (resIter == resolutions.end())
          {
              std::ostringstream msg;
              msg << MEM_FN("decode") <<
                  "Resolution not found for \"" <<
                  imageType <<
                  "\" imagery type";

              throw IO_Error(msg.str().c_str());
          }
          if (covIter == coverages.end())
          {
              std::ostringstream msg;
              msg << MEM_FN("decode") <<
                  "Coverage not found for \"" <<
                  imageType <<
                  "\" imagery type";

              throw IO_Error(msg.str().c_str());
          }

          result = new ImageDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              imageType.get(),
              width,
              height,
              resIter->second.first,
              resIter->second.second,
              covIter->second,
              referenceID,
              isRemote,
              precisionImagery,
              workingDirectory.get(),
              extraData);
      }
      break;

      case MOSAIC:
      {
          TAK::Engine::Port::String mosaicFile(read<bool>(strm) ? readUTF(strm) : nullptr);
          TAK::Engine::Port::String mosaicProvider(read<bool>(strm)
              ? readUTF(strm)
              : nullptr);

          result = new MosaicDatasetDescriptor(layerID,
              name.get(),
              URI.get(),
              provider.get(),
              datasetType.get(),
              mosaicFile.get(),
              mosaicProvider.get(),
              imageryTypes,
              resolutions,
              coverages,
              referenceID,
              isRemote,
              workingDirectory.get(),
              extraData);
      }
      break;
      }

      return result;
  }

}                                       // Close raster namespace.
}                                       // Close atakmap namespace.
