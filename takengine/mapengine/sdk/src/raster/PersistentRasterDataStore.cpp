////============================================================================
////
////    FILE:           PersistentRasterDataStore.cpp
////
////    DESCRIPTION:    Implementation of the
////                    PersistentRasterDataStore class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 27, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/PersistentRasterDataStore.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#ifdef MSVC
#include <Windows.h>
#endif

#include "db/CatalogDatabase.h"
#include "db/Cursor.h"
#include "db/Statement.h"
#include "db/WhereClauseBuilder.h"
#include "feature/GeometryCollection.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/SpatialCalculator.h"
#include "raster/DatasetDescriptor.h"
#include "raster/LayerDatabase.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"
#include "port/Collections.h"
#include "port/Iterator.h"
#include "port/STLSetAdapter.h"
#include "port/STLVectorAdapter.h"
#include "port/StringBuilder.h"

#define MEM_FN( fn ) \
        "atakmap::raster::PersistentRasterDataStore::" fn ": "

#define RESOLVE_COLLECTION_COVERAGES 1
#define CURRENCY_VERSION_FULL_SCAN 2

////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

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

#define LARGE_DATASET_RECURSE_LIMIT 5000

namespace                               // Open unnamed namespace.
{


enum
  {
    // XXX - when this gets bumped, the full scan version may need to be
    //       removed
    CURRENCY_VERSION    = 3
  };


typedef std::map<int64_t, std::shared_ptr<raster::DatasetDescriptor>>   DescriptorMap;
typedef raster::DatasetDescriptor::DescriptorSet        DescriptorSet;

typedef db::DataStore::QueryParameters::Order   Order;
typedef std::shared_ptr<Order>    OrderPtr;


int strcmp2(const char *lhs, const char *rhs)
 {
     if (lhs && rhs)    return std::strcmp(lhs, rhs);
     else if (lhs)      return 1;
     else if (rhs)      return -1;
     else               return 0;
 }

///=============================================================================
///
///  class DatasetOrderAppender
///
///     Unary functor that appends an ORDER BY expression for a
///     QueryParameters::Order supported by DatasetQueryParameters to a
///     WhereClauseBuilder.
///
///=============================================================================


class DatasetOrderAppender
  : public std::unary_function<void, OrderPtr>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    DatasetOrderAppender (db::WhereClauseBuilder& wheres)
      : wheres (wheres),
        first (true)
      { }

    void
    operator() (const OrderPtr& order)
      { appendOrder (order.get ()); }

    void
    appendOrder (const Order* order);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef db::DataStore::QueryParameters::Name        OrderByName;
    typedef db::DataStore::QueryParameters::Provider    OrderByProvider;
    typedef db::DataStore::QueryParameters::Resolution  OrderByResolution;
    typedef db::DataStore::QueryParameters::Type        OrderByType;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    db::WhereClauseBuilder& wheres;
    bool first;
  };


///=============================================================================
///
///  class FileCursorImpl
///
///     Implementation of db::FileCursor.
///
///=============================================================================


class FileCursorImpl
  : public db::FileCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FileCursorImpl (const std::shared_ptr<db::CatalogDatabase::Cursor> &subject)
      : FileCursor (subject),
        catCursor (subject.get())
      { }

    const char*
    getFile ()
        const
        throw (CursorError) override
      { return catCursor->getPath (); }

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    db::CatalogDatabase::Cursor* catCursor;
  };


class GenerateCurrency
  : public db::CatalogDatabase::Currency
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const CURRENCY_NAME;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    GenerateCurrency (const DescriptorSet& descriptors)
      : factories (descriptors.size ())
      {
        auto end (descriptors.end ());

        std::size_t idx(0);
        for (auto iter (descriptors.begin ());
             iter != end;
             ++iter)
          {
            const char* datasetType ((*iter)->getProvider ());
            const raster::DatasetDescriptor::Factory* factory
                (raster::DatasetDescriptor::getFactory (datasetType));

            if (factory)
              {
                factories[idx++] = factory;
              }
            else
              {
                std::ostringstream err;
                err << MEM_FN ("GenerateCurrency::GenerateCurrency") <<
                          "No Factory found for dataset type: " <<
                          datasetType;

                throw std::invalid_argument (err.str());
              }
          }
      }


    //==================================
    //  db::CatalogDatabase::Currency INTERFACE
    //==================================


    atakmap::util::BlobImpl
    getData (const char* filePath)
        const override;

    const char*
    getName ()
        const
        NOTHROWS override
      { return CURRENCY_NAME; }

    unsigned long
    getVersion ()
        const
        NOTHROWS override
      { return CURRENCY_VERSION; }

    bool
    isValid (const char* filePath,
             unsigned long version,
             const atakmap::util::BlobImpl& data)
        const override
      { return false; }                 // Not a Currency validator.


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef std::vector<const raster::DatasetDescriptor::Factory*>
            FactoryVector;


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    FactoryVector factories;
  };


///=============================================================================
///
///  class LayerCursorImpl
///
///     Implementation of RasterDataStore::DatasetDescriptorCursor.
///
///=============================================================================


class LayerCursorImpl
  : public raster::RasterDataStore::DatasetDescriptorCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


      LayerCursorImpl(DescriptorMap &descriptorMap_,
                      Mutex& descriptorMutex,
                     std::unique_ptr<db::Cursor> &&subject,
                     std::size_t colID,
                     std::size_t colInfo)
        throw (CursorError)
      : DatasetDescriptorCursor (std::move(subject)),
        colID (colID),
        colInfo (colInfo),
        descriptorMutex(descriptorMutex),
        descriptorMap(descriptorMap_)
      {
      }


    ~LayerCursorImpl ()
        NOTHROWS override
      { }


    //==================================
    //  raster::RasterDataStore::DatasetDescriptorCursor INTERFACE
    //==================================

    bool
    moveToNext() NOTHROWS
        override;

    raster::DatasetDescriptor&
    get ()
        const
        throw (CursorError) override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t colID;
    std::size_t colInfo;
    DescriptorMap &descriptorMap;
    Mutex& descriptorMutex;
  };


class ValidateCurrency
  : public db::CatalogDatabase::Currency
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  db::CatalogDatabase::Currency INTERFACE
    //==================================


    atakmap::util::BlobImpl
    getData (const char* filePath)      // Not a Currency generator.
        const override
      {
          return atakmap::util::makeBlob (nullptr, nullptr);
      }

    const char*
    getName ()
        const
        NOTHROWS override
      { return GenerateCurrency::CURRENCY_NAME; }

    unsigned long
    getVersion ()
        const
        NOTHROWS override
      { return CURRENCY_VERSION; }

    bool
    isValid (const char* filePath,
             unsigned long version,
             const atakmap::util::BlobImpl& data)
        const override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    ~ValidateCurrency ()                // Private per PGSC::RefCountable.
        NOTHROWS
      { }

  };


    class StringResultIterator : public atakmap::port::Iterator<const char *>
    {
    public:
        StringResultIterator(atakmap::db::Cursor *result, size_t strIdx);
        ~StringResultIterator() override;
    public:
        bool hasNext() override;
        const char *next() override;
        const char *get() override;
    private :
        std::vector<const char *> impl;
        size_t idx;
    };
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


namespace                               // Open unnamed namespace.
{


const char* const
GenerateCurrency::CURRENCY_NAME ("PersistentRasterDataStore.Currency");


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


void
appendSpatialFilter (const db::DataStore::QueryParameters::SpatialFilter* filter,
                     db::WhereClauseBuilder& wheres)
  {
    typedef db::DataStore::QueryParameters::PointFilter         PointFilter;
    typedef db::DataStore::QueryParameters::RegionFilter        RegionFilter;

    const PointFilter *pointFilter(nullptr);
    const RegionFilter *regionFilter(nullptr);

    if ((pointFilter = dynamic_cast<const PointFilter*> (filter)), pointFilter != nullptr)
      {
        const core::GeoPoint& point (pointFilter->point);

        if (!point.isValid ())
          {
            throw std::invalid_argument (MEM_FN ("appendSpatialFilter")
                                         "Received invalid PointFilter");
          }

        wheres.newConjunct ().append ("layers.minlat <= ?")
              .newConjunct ().append ("layers.maxlat >= ?")
              .addArg (point.latitude)
              .addArg (point.latitude)
              .newConjunct ().append ("layers.minlon <= ?")
              .newConjunct ().append ("layers.maxlon >= ?")
              .addArg (point.longitude)
              .addArg (point.longitude);
      }
    else if ((regionFilter = dynamic_cast<const RegionFilter*> (filter)), regionFilter != nullptr)
      {
        if (!regionFilter->isValid ())
          {
            throw std::invalid_argument (MEM_FN ("appendSpatialFilter")
                                         "Received invalid RegionFilter");
          }

        //
        // Accept any Dataset that intersects or contains the region.
        //
        const core::GeoPoint& ul (regionFilter->upperLeft);
        const core::GeoPoint& lr (regionFilter->lowerRight);

        wheres.newConjunct ().append ("layers.minlat <= ?")
              .newConjunct ().append ("layers.maxlat >= ?")
              .addArg (ul.latitude)
              .addArg (lr.latitude)
              .newConjunct ().append ("layers.minlon <= ?")
              .newConjunct ().append ("layers.maxlon >= ?")
              .addArg (lr.longitude)
              .addArg (ul.longitude);
      }
    else
      {
        throw std::invalid_argument (MEM_FN ("appendSpatialFilter")
                                     "Received unrecognized SpatialFilter type");
      }
  }


void
buildWheres (const raster::RasterDataStore::DatasetQueryParameters& params,
             db::WhereClauseBuilder& wheres)
  {
    if (!params.IDs.empty())
      {
          wheres.newConjunct().appendIn("layers.id", params.IDs);
      }
    if (!params.names.empty ())
      {
        wheres.newConjunct ().appendIn ("layers.name", params.names);
      }
    if (!params.providers.empty ())
      {
        wheres.newConjunct ().appendIn ("layers.provider", params.providers);
      }
    if (!params.types.empty ())
      {
        wheres.newConjunct ().appendIn ("layers.datasettype", params.types);
      }
    if (!params.imageryTypes.empty ())
      {
        wheres.newConjunct ()
              .append ("layers.id IN (SELECT DISTINCT imagerytypes.layerid "
                       "FROM imagerytypes WHERE ")
              .appendIn ("imagerytypes.name", params.imageryTypes)
              .append (")");
      }
    if (params.spatialFilter.get ())
      {
        appendSpatialFilter (params.spatialFilter.get (), wheres);
      }
    if (params.minResolution)
      {
        wheres.newConjunct ()
              .append ("layers.maxgsd <= ?").addArg (params.minResolution);
      }
    if (params.maxResolution)
      {
        wheres.newConjunct ()
              .append ("layers.mingsd >= ?").addArg (params.maxResolution);
      }
    if (params.locality != params.ALL)
      {
        wheres.newConjunct ().append ("layers.remote = ?");
        wheres.addArg (params.locality == params.REMOTE_ONLY ? "1" : "0");
      }
  }


void
discoverAvailableMounts(std::set<std::string> &mounts)
  {
#ifdef MSVC
    int drivesMask = GetLogicalDrives();
    for (char i = 0; i < 26; i++) {
        if (!(drivesMask&(1u << i)))
            continue;
        char drive[4] = { static_cast<char>('A' + i), ':', '\0' };
        const bool exists = util::pathExists(drive);
        if (exists)
            mounts.insert(std::string(drive));
    }
#endif
  }

///=====================================
///  DatasetOrderAppender MEMBER FUNCTIONS
///=====================================


void
DatasetOrderAppender::appendOrder (const Order* order)
  {
    const OrderByName *byName (nullptr);
    const OrderByProvider *byProvider(nullptr);
    const OrderByResolution *byResolution(nullptr);
    const OrderByType *byType(nullptr);

    if ((byName = dynamic_cast<const OrderByName*> (order)), byName != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("name");
      }
    else if ((byProvider = dynamic_cast<const OrderByProvider*> (order)), byProvider != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("provider");
      }
    else if ((byResolution = dynamic_cast<const OrderByResolution*> (order)), byResolution != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("maxgsd");
      }
    else if ((byType = dynamic_cast<const OrderByType*> (order)), byType != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("datasettype");
      }
    else
      {
        throw std::invalid_argument (MEM_FN ("DatasetOrderAppender")
                                     "Received unsupported Order type");
      }
    first = false;
  }


///=====================================
///  GenerateCurrency MEMBER FUNCTIONS
///=====================================


atakmap::util::BlobImpl
GenerateCurrency::getData (const char* filePath)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("GenerateCurrency::getData")
                                     "Received NULL filePath");
      }

    std::ostringstream strm (std::ios_base::out | std::ios_base::binary);
    auto end (factories.end ());

    util::write (strm, factories.size ());
    for (auto iter (factories.begin ());
         iter != end;
         ++iter)
      {
        const char* factoryType ((*iter)->getStrategy ());

        util::write<unsigned short>(strm, (*iter)->getVersion());
        util::write<unsigned int>(strm, static_cast<unsigned int>(std::strlen(factoryType)));
        strm << factoryType;
      }
    const bool isDir = util::isDirectory(filePath);
    int64_t size;
    int64_t lastModified;
    TAKErr code(TE_Ok);
    bool is_network_drive(false);
    if (isDir) 
        code = IO_isNetworkDirectory(&is_network_drive, filePath);

    bool isLargeDataset = false;
    if (isDir) {
        std::size_t cnt;
        if (is_network_drive)
            code = IO_getFileCount(&cnt, filePath, LARGE_DATASET_RECURSE_LIMIT+1);
        else
            code = IO_getFileCount(&cnt, filePath);
        // XXX - no appropriate failover
        if (code != TE_Ok)
            throw std::runtime_error("Error obtaining file count");
        size = cnt;
        isLargeDataset = (cnt > LARGE_DATASET_RECURSE_LIMIT);
    }
    
    if(isLargeDataset || is_network_drive) {
        lastModified = -1LL;
    } else if (isDir) {
        lastModified = util::getLastModified(filePath);
    } else {
        size = util::getFileSize(filePath);
        lastModified = util::getLastModified(filePath);
    }

    util::write(strm, isDir);
    util::write (strm, size);
    util::write (strm, lastModified);

    std::string contents (strm.str ());
    std::size_t length (contents.size ());
    auto* buff (new unsigned char[length]);

    std::memcpy (buff, contents.data (), length);
    return atakmap::util::makeBlobWithDeleteCleanup(buff, buff + length);
  }


///=====================================
///  LayerCursorImpl MEMBER FUNCTIONS
///=====================================

bool
LayerCursorImpl::moveToNext() NOTHROWS
  {
    bool retval = DatasetDescriptorCursor::moveToNext();
    if (retval) {
        int64_t layerID(getLong(colID));

        Lock lock(descriptorMutex);
        DescriptorMap::const_iterator iter(descriptorMap.find(layerID));

        if (iter == descriptorMap.end())
        {
            try
            {
                std::shared_ptr<raster::DatasetDescriptor> dataset;
                dataset.reset(raster::DatasetDescriptor::decode
                    (getBlob(colInfo)));
                iter = descriptorMap.insert(std::make_pair(layerID,
                    dataset)).first;
            }
            catch (const util::IO_Error& ioExc)
            {
                retval = false;
                atakmap::util::Logger::log(atakmap::util::Logger::Error, "LayerCursor caught IO_Error decoding dataset BLOB for Layer (ID=%d): %s", layerID, ioExc.what());
            }
        }
    }

    return retval;
  }

raster::DatasetDescriptor&
LayerCursorImpl::get ()
    const
    throw (CursorError)
  {
    int64_t layerID (getLong (colID));

    Lock lock(descriptorMutex);
    DescriptorMap::const_iterator iter (descriptorMap.find (layerID));

    if (iter == descriptorMap.end())
      {
        throw std::runtime_error("Illegal state");
      }

    return *(iter->second);
  }


///=====================================
///  ValidateCurrency MEMBER FUNCTIONS
///=====================================


bool
ValidateCurrency::isValid (const char* filePath,
                           unsigned long version,
                           const atakmap::util::BlobImpl& data)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("ValidateCurrency::isValid")
                                     "Received NULL filePath");
      }
    if (!data.first || !data.second)
      {
        throw std::invalid_argument (MEM_FN ("ValidateCurrency::isValid")
                                     "Received invalid Currency data");
      }

#ifdef MSVC
    // XXX - hack to silently ignore datasets that reside on a drive that is
    //       temporarily unavailable

    std::string filePathStr(filePath);
    if (filePathStr.find(':') != std::string::npos) {
        std::string drive = filePathStr.substr(0, filePathStr.find(':')+1);
        if (!util::pathExists(drive.c_str()))
            return true;
    }
#endif

    bool valid(util::pathExists(filePath) && (version == getVersion()));
    if (!valid)
        return false;

    if (valid)
      {
        std::istringstream strm (std::string (data.first, data.second),
                                 std::ios_base::in | std::ios_base::binary);
        auto sourceCount (util::read<std::size_t> (strm));

        for (std::size_t i (0); valid && i < sourceCount; ++i)
          {
            auto parseVersion (util::read<unsigned short> (strm));
            auto nameLength (util::read<unsigned int> (strm));
            TAK::Engine::Util::array_ptr<char> factoryName (new char[nameLength + 1]);

            strm.get (factoryName.get (), nameLength + 1);

            const raster::DatasetDescriptor::Factory* factory
                (raster::DatasetDescriptor::getFactory (factoryName.get()));

            valid = factory && parseVersion == factory->getVersion ();
          }
        if (!valid)
            return false;

        if (valid) {
            const bool isDir = util::read<bool>(strm);
            if (util::isDirectory(filePath) != isDir) return false;

            TAKErr code(TE_Ok);
            bool is_network_drive(false);
            if (isDir) 
                code = IO_isNetworkDirectory(&is_network_drive, filePath);
            const auto size = util::read<int64_t>(strm);
            if (isDir) {
                std::size_t actual;
                if (is_network_drive)
                    code = IO_getFileCount(&actual, filePath, LARGE_DATASET_RECURSE_LIMIT + 1);
                else
                    code = IO_getFileCount(&actual, filePath);
                if (code != TE_Ok)
                    return false;
                if (actual != size) 
                    return false;
            } else {
                if (util::getFileSize(filePath) != size) 
                    return false;
            }

            const auto lastModified = util::read<int64_t>(strm);
            if (!isDir || (!is_network_drive && (size <= LARGE_DATASET_RECURSE_LIMIT))) {
                if (util::getLastModified(filePath) != lastModified)
                    return false;
            }
        }
    }

    return true;
}

    StringResultIterator::StringResultIterator(atakmap::db::Cursor *result, size_t strIdx) :
        idx(0)
    {
        std::vector<const char *>::iterator it;
        const char *str;
        char *c;
        size_t len;
        while (result->moveToNext()) {
            str = result->getString(strIdx);
            if (str) {
                len = strlen(str);
                c = new char[len + 1];
                memcpy(c, str, len);
                c[len] = '\0';
            }
            else {
                c = nullptr;
            }
            impl.push_back(c);
        }
    }
    StringResultIterator::~StringResultIterator()
    {
        std::vector<const char *>::iterator it;
        const char *cstr;
        for (it = impl.begin(); it != impl.end(); it++) {
            cstr = *it;
            delete[] cstr;
        }
        impl.clear();
    }

    bool StringResultIterator::hasNext()
    {
        return idx < impl.size();
    }

    const char *StringResultIterator::next()
    {
        if (!hasNext())
            throw std::out_of_range("no such element");
        idx++;
        return impl[idx-1];
    }

    const char *StringResultIterator::get()
    {
        if (!idx || idx > impl.size())
            throw std::out_of_range("no such element");
        return impl[idx-1];
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


PersistentRasterDataStore::PersistentRasterDataStore (const char* databasePath,
                                                      const char* workingDir)
  : LocalRasterDataStore (workingDir),
    databasePath (databasePath),
    layerDB (LayerDatabase::createDatabase (databasePath))
  {
//    if (!databasePath)
//      {
//        throw std::invalid_argument
//                  (MEM_FN ("PersistentRasterDataStore")
//                   "Received NULL databasePath");
//      }
    if (!layerDB.get ())
      {
        throw std::runtime_error (MEM_FN ("PersistentRasterDataStore")
                                  "Failed to create LayerDatabase");
      }
    layerDB->getCurrencyRegistry ().registerCurrency (new ValidateCurrency);

    discoverAvailableMounts(this->availableMounts);
  }

PersistentRasterDataStore::~PersistentRasterDataStore()
    NOTHROWS
{
    dispose();

    std::list<BackgroundCoverageResolver *>::iterator it;
    for (it = canceledResolvers.begin(); it != canceledResolvers.end(); it++)
        delete *it;
    canceledResolvers.clear();
}

void
PersistentRasterDataStore::dispose ()
  {
      Lock lock(getMutex());

    clearInfoCacheMap(infoCache);
    clearPendingCoveragesMap();

    layerDB.reset (nullptr);
  }


feature::UniqueGeometryPtr
PersistentRasterDataStore::getCoverage (const char* dataset,
                                        const char* imageryType)
  {
    if (!dataset && !imageryType)
      {
        throw std::invalid_argument (MEM_FN ("getCoverage")
                                     "Received NULL dataset and imageryType");
      }

    Lock lock(getMutex());

    auto entry = infoCache.find(std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String>(dataset, imageryType));
    QueryInfoSpec spec{ NAN, NAN, nullptr, 0 };
    if (entry != infoCache.end())
        spec = entry->second;
    if (!spec.count) {
        spec = validateQueryInfoSpecNoSync(dataset, imageryType);
    }

    // clone; client responsible
    feature::Geometry *retval = nullptr;
    if (spec.coverage)
        retval = spec.coverage->clone();
    return feature::UniqueGeometryPtr(retval, feature::destructGeometry);
  }


atakmap::port::Iterator<const char *> *
PersistentRasterDataStore::getDatasetNames ()
    const
  {
      db::WhereClauseBuilder wheres;

      if (!availableMounts.empty()) {
          std::vector<TAK::Engine::Port::String> args;
          std::set<std::string>::iterator it;
          for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
              std::ostringstream strm;
              strm << *it;
              strm << "%";
              args.push_back(TAK::Engine::Port::String(strm.str().c_str()));
          }
          wheres.appendIn("layers.path", args);
      }

      bool whereClause = !wheres.empty();

      std::ostringstream sqlStrm;
      sqlStrm << "SELECT DISTINCT name from layers";
      if (!wheres.empty())
      {
          if (whereClause)
              sqlStrm << " WHERE";
          sqlStrm << " " << wheres.getClause();
      }

      std::unique_ptr<db::Cursor> cursor
          (layerDB->query(sqlStrm.str().c_str(), wheres.getArgs()));

    return new StringResultIterator(cursor.get(), 0);
  }


atakmap::port::Iterator<const char *> *
PersistentRasterDataStore::getDatasetTypes ()
    const
  {
      db::WhereClauseBuilder wheres;

      if (!availableMounts.empty()) {
          std::vector<TAK::Engine::Port::String> args;
          std::set<std::string>::iterator it;
          for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
              std::ostringstream strm;
              strm << *it;
              strm << "%";
              args.push_back(TAK::Engine::Port::String(strm.str().c_str()));
          }
          wheres.appendIn("layers.path", args);
      }

      bool whereClause = !wheres.empty();

      std::ostringstream sqlStrm;
      sqlStrm << "SELECT DISTINCT datasettype from layers";
      if (!wheres.empty())
      {
          if (whereClause)
              sqlStrm << " WHERE";
          sqlStrm << " " << wheres.getClause();
      }

      std::unique_ptr<db::Cursor> cursor
          (layerDB->query(sqlStrm.str().c_str(), wheres.getArgs()));

    return new StringResultIterator(cursor.get(), 0);
  }


atakmap::port::Iterator<const char *> *
PersistentRasterDataStore::getImageryTypes ()
    const
  {
      db::WhereClauseBuilder wheres;

#if 0
      if (!availableMounts.empty()) {
          std::vector<PGSC::String> args;
          std::set<std::string>::iterator it;
          for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
              std::ostringstream strm;
              strm << *it;
              strm << "%";
              args.push_back(PGSC::String(strm.str().c_str()));
          }
          wheres.appendIn("layers.path", args);
      }
#endif

      bool whereClause = !wheres.empty();

      std::ostringstream sqlStrm;
      sqlStrm << "SELECT DISTINCT name from imagerytypes";
      if (!wheres.empty())
      {
          if (whereClause)
              sqlStrm << " WHERE";
          sqlStrm << " " << wheres.getClause();
      }

      std::unique_ptr<db::Cursor> cursor
          (layerDB->query(sqlStrm.str().c_str(), wheres.getArgs()));

    return new StringResultIterator(cursor.get(), 0);
  }

double
PersistentRasterDataStore::getMaximumResolution(const char *dataset, const char *imageryType)
  {
    if (!dataset && !imageryType)
    {
        throw std::invalid_argument(MEM_FN("getCoverage")
            "Received NULL dataset and imageryType");
    }

    Lock lock(getMutex());

    auto entry = infoCache.find(std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String>(dataset, imageryType));
    QueryInfoSpec spec{ NAN, NAN, nullptr, 0 };
    if (entry != infoCache.end())
        spec = entry->second;
    if (!spec.count) {
        spec = validateQueryInfoSpecNoSync(dataset, imageryType);
    }
    return spec.maxResolution;
  }

double
PersistentRasterDataStore::getMinimumResolution(const char *dataset, const char *imageryType)
  {
      if (!dataset && !imageryType)
      {
          throw std::invalid_argument(MEM_FN("getCoverage")
              "Received NULL dataset and imageryType");
      }

      Lock lock(getMutex());

      auto entry = infoCache.find(std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String>(dataset, imageryType));
      QueryInfoSpec spec{ NAN, NAN, nullptr, 0 };
      if (entry != infoCache.end())
          spec = entry->second;
      if (!spec.count) {
          spec = validateQueryInfoSpecNoSync(dataset, imageryType);
      }
      return spec.minResolution;
  }

atakmap::port::Iterator<const char *> *
PersistentRasterDataStore::getProviders ()
    const
  {
      db::WhereClauseBuilder wheres;

      if (!availableMounts.empty()) {
          std::vector<TAK::Engine::Port::String> args;
          std::set<std::string>::iterator it;
          for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
              std::ostringstream strm;
              strm << *it;
              strm << "%";
              args.push_back(TAK::Engine::Port::String(strm.str().c_str()));
          }
          wheres.appendIn("layers.path", args);
      }

    bool whereClause = !wheres.empty();

    std::ostringstream sqlStrm;
    sqlStrm << "SELECT DISTINCT provider from layers";
    if (!wheres.empty())
    {
        if (whereClause)
            sqlStrm << " WHERE";
        sqlStrm << " " << wheres.getClause();
    }

    std::unique_ptr<db::Cursor> cursor
        (layerDB->query (sqlStrm.str().c_str(), wheres.getArgs()));

    return new StringResultIterator(cursor.get(), 0);
  }


bool
PersistentRasterDataStore::isAvailable ()
    const
  {
    Lock lock(getMutex());

    return layerDB.get () != nullptr;
  }


PersistentRasterDataStore::DatasetDescriptorCursor*
PersistentRasterDataStore::queryDatasets (const DatasetQueryParameters& params)
    const
  {
    enum
      {
        colID,
        colInfo
      };

    db::WhereClauseBuilder wheres;

    if (!availableMounts.empty()) {
        std::vector<TAK::Engine::Port::String> args;
        std::set<std::string>::iterator it;
        for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
            std::ostringstream strm;
            strm << *it;
            strm << "%";
            args.push_back(TAK::Engine::Port::String(strm.str().c_str()));
        }
        wheres.appendIn("layers.path", args);
    }
    buildWheres (params, wheres);
    bool whereClause = !wheres.empty();

    if (!params.orders.empty ())
      {
        std::for_each (params.orders.begin (), params.orders.end (),
                       DatasetOrderAppender (wheres));
      }
    if (params.resultLimit || params.resultOffset)
      {
        //
        // If an offset, but no limit was specified, use -1 for the limit.
        //
        wheres.append (" LIMIT ? OFFSET ?")
              .addArg (params.resultLimit
                       ? static_cast<long> (params.resultLimit) : -1)
              .addArg (params.resultOffset);
      }

    std::ostringstream sqlStrm;

    sqlStrm << "SELECT id, info FROM layers";
    if (!wheres.empty ())
      {
        if (whereClause)
            sqlStrm << " WHERE";
        sqlStrm << " " << wheres.getClause ();
      }

    std::unique_ptr<db::Cursor> result(layerDB->query(sqlStrm.str().c_str(),
        wheres.getArgs()));
    return new LayerCursorImpl (layerDescriptors,
                                descriptorsMutex,
                                std::move(result),
                                colID, colInfo);
  }


std::size_t
PersistentRasterDataStore::queryDatasetsCount
    (const DatasetQueryParameters& params)
    const
  {
    std::size_t resultCount (0);
    db::WhereClauseBuilder wheres;

    if (!availableMounts.empty()) {
        std::vector<TAK::Engine::Port::String> args;
        std::set<std::string>::iterator it;
        for (it = availableMounts.begin(); it != availableMounts.end(); it++) {
            std::ostringstream strm;
            strm << *it;
            strm << "%";
            args.push_back(TAK::Engine::Port::String(strm.str().c_str()));
        }
        wheres.appendIn("layers.path", args);
    }

    buildWheres (params, wheres);

    std::ostringstream sqlStrm;

    sqlStrm << "SELECT Count(1) FROM layers";
    if (!wheres.empty ())
      {
        sqlStrm << " WHERE " << wheres.getClause ();
      }

    std::unique_ptr<db::Cursor> cursor
        (layerDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));

    if (cursor->moveToNext ())
      {
        resultCount = cursor->getInt (0);
        if (params.resultOffset && params.resultOffset < resultCount)
          {
            resultCount -= params.resultOffset;
          }
        if (params.resultLimit)
          {
            resultCount = std::min (params.resultLimit, resultCount);
          }
      }

    return resultCount;
  }

void
PersistentRasterDataStore::destroyStringIterator(atakmap::port::Iterator<const char*> *it)
    const
{
    auto *impl = static_cast<StringResultIterator *>(it);
    delete impl;
}

db::FileCursor*
PersistentRasterDataStore::queryFiles ()
    const
  {
    std::unique_ptr<db::CatalogDatabase::Cursor> result(layerDB->queryCatalog());
    return new LayerDatabase::FileCursor (std::move(result));
  }


void
PersistentRasterDataStore::refresh ()
  {
    Lock lock(getMutex());

    // clear cached mounts
    this->availableMounts.clear();

    // discover available mounts
    discoverAvailableMounts(this->availableMounts);

    layerDB->validateCatalog();
    {
        Lock descLock(descriptorsMutex);
        layerDescriptors.clear();
    }
    notifyContentListeners ();
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


bool
PersistentRasterDataStore::addFileImpl (const char* filePath,
                                        const DescriptorSet& descriptors,
                                        const char* workingDir)
  {
    std::shared_ptr<GenerateCurrency> currency
        (new GenerateCurrency (descriptors));

    layerDB->addLayers (filePath,
                        LayerDatabase::DescriptorVector (descriptors.begin (),
                                                         descriptors.end ()),
                        workingDir,
                        *currency);

    invalidateCacheInfo(descriptors, false);

    return true;
  }


bool
PersistentRasterDataStore::clearImpl ()
  {
    bool modified (layerDescriptors.size () != 0);

    layerDescriptors.clear();
    layerDB->deleteAll ();
    clearInfoCacheMap(infoCache);
    clearPendingCoveragesMap();

    return modified;
  }

void
PersistentRasterDataStore::clearInfoCacheMap(InfoCacheMap &infoCache)
  {
    InfoCacheMap::iterator entry;
    for (entry = infoCache.begin(); entry != infoCache.end(); entry++)
      {
        if (entry->second.coverage)
            delete entry->second.coverage;
      }
    infoCache.clear();
  }

void
PersistentRasterDataStore::clearPendingCoveragesMap()
  {
    PendingCoveragesMap::iterator entry;
    for (entry = pendingCoverages.begin(); entry != pendingCoverages.end(); entry++)
    {
        entry->second->cancel();
        if (!entry->second->done)
            canceledResolvers.push_back(entry->second);
        else
            delete entry->second;
    }
    pendingCoverages.clear();
  }

bool
PersistentRasterDataStore::containsFileImpl (const char* filePath)
    const
  {
    bool contains = std::unique_ptr<db::Cursor> (layerDB->queryCatalogPath (filePath))
               ->moveToNext ();
    return contains;
  }

void
PersistentRasterDataStore::geometryResolvedNoSync(const char *dataset, const char *type, const feature::Geometry &coverage, bool notify)
{
    std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String> infoSpecKey(dataset, type);
    auto entry = infoCache.find(infoSpecKey);
    if (entry != infoCache.end()) {
        QueryInfoSpec spec = entry->second;
        if (spec.coverage) {
            delete spec.coverage;
            spec.coverage = nullptr;
        }

        spec.coverage = coverage.clone();
        infoCache[infoSpecKey] = spec;

        // XXX - consider persisting computed coverages

        if (notify)
            RasterDataStore::notifyContentListeners();
    }
}

const char*
PersistentRasterDataStore::getFileImpl (const DatasetDescriptor& descriptor)
    const
  {
    const char* result (nullptr);

    try
      {
        LayerDatabase::Cursor cursor
            (layerDB->queryLayers (descriptor.getName ()));

        while (!result && cursor.moveToNext ())
          {
            try
              {
                DatasetDescriptor* info (cursor.getLayerInfo ());

                if (info && descriptor == *info)
                  {
                    result = cursor.getPath ();
                  }
              }
            catch (const db::Cursor::CursorError&)
              { }
          }
      }
    catch (const db::Cursor::CursorError&)
      { }
    catch (const db::DB_Error&)
      { }

    return result;
  }

void
PersistentRasterDataStore::invalidateCacheInfo(const DescriptorSet &descs, const bool notify)
    NOTHROWS
{    
    InfoCacheMap::iterator infoCacheKeyIter;
    PendingCoveragesMap::iterator pendingCoverageEntryIter;

    std::set<std::string> types;

    TAK::Engine::Port::String dataset;
    PendingCoveragesMap::iterator pending;
    DescriptorSet::const_iterator desc;
    for (desc = descs.begin(); desc != descs.end(); desc++) {
        dataset = (*desc)->getName();
        infoCacheKeyIter = this->infoCache.begin();
        while (infoCacheKeyIter != this->infoCache.end()) {
            if (strcmp2(dataset, infoCacheKeyIter->first.first) == 0) {
                if (infoCacheKeyIter->second.coverage) {
                    delete infoCacheKeyIter->second.coverage;
                    infoCacheKeyIter->second.coverage = nullptr;
                }
                infoCacheKeyIter = this->infoCache.erase(infoCacheKeyIter);
            } else {
                infoCacheKeyIter++;
            }
        }

        pendingCoverageEntryIter = this->pendingCoverages.begin();
        while (pendingCoverageEntryIter != this->pendingCoverages.end()) {
            pending = pendingCoverageEntryIter;
            //if (Objects.equals(dataset, pending.getKey().first)) {
            if (strcmp2(dataset, pending->first.first) == 0) {
                if (!pending->second->isCanceled())
                    pending->second->cancel();
            }
            pendingCoverageEntryIter++;
        }

        std::vector<TAK::Engine::Port::String> imageryTypes = (*desc)->getImageryTypes();
        std::vector<TAK::Engine::Port::String>::iterator it;
        for (it = imageryTypes.begin(); it != imageryTypes.end(); it++)
            types.insert(std::string(*it));
    }

    std::set<std::string>::iterator type;
    for (type = types.begin(); type != types.end(); type++) {
        infoCacheKeyIter = this->infoCache.begin();
        while (infoCacheKeyIter != this->infoCache.end()) {
            if (strcmp2((*type).c_str(), infoCacheKeyIter->first.second) == 0) {
                if (infoCacheKeyIter->second.coverage) {
                    delete infoCacheKeyIter->second.coverage;
                    infoCacheKeyIter->second.coverage = nullptr;
                }
                infoCacheKeyIter = this->infoCache.erase(infoCacheKeyIter);
            } else {
                infoCacheKeyIter++;
            }
        }

        pendingCoverageEntryIter = this->pendingCoverages.begin();
        while (pendingCoverageEntryIter != this->pendingCoverages.end()) {
            pending = pendingCoverageEntryIter;
            //if (Objects.equals(type, pending.getKey().second)) {
            if (strcmp2((*type).c_str(), pending->first.second) == 0) {
                if (!pending->second->isCanceled())
                    pending->second->cancel();
            }
            pendingCoverageEntryIter++;
        }
    }

    if (notify) {
        atakmap::util::Logger::log(atakmap::util::Logger::Debug, "PersistentRasterDataStore: invalidate geometry invalidated");
        this->notifyContentListeners();
    }
}

bool
PersistentRasterDataStore::removeFileImpl (const char* filePath)
  {
    bool contentModified (false);

    try {
        std::vector<std::shared_ptr<const DatasetDescriptor>> descs;
        {
            TAK::Engine::Port::STLVectorAdapter<std::shared_ptr<const DatasetDescriptor>> descsWrap(descs);
            layerDB->getLayers(descsWrap, filePath);
        }

        DescriptorSet invalid;
        {
            struct Transmuter
            {
                DatasetDescriptor *operator()(const std::shared_ptr<const DatasetDescriptor> &src)
                {
                    return const_cast<DatasetDescriptor *>(src.get());
                }
            };
            TAK::Engine::Port::STLVectorAdapter<std::shared_ptr<const DatasetDescriptor>> descsWrap(descs);
            TAK::Engine::Port::STLSetAdapter<DatasetDescriptor *, DatasetDescriptor::PtrLess> invalidWrap(invalid);
            TAK::Engine::Port::Collections_transmute<std::shared_ptr<const DatasetDescriptor>, DatasetDescriptor *, Transmuter>(invalidWrap, descsWrap);
        }
        this->invalidateCacheInfo(invalid, false);
    } catch (...) {
        // XXX - ouch -- we failed to get the descriptors so we'll just nuke
        //       the whole cache!
        this->infoCache.clear();
        this->layerDescriptors.clear();

        PendingCoveragesMap::iterator resolver;
        for (resolver = this->pendingCoverages.begin(); resolver != this->pendingCoverages.end(); resolver++)
            resolver->second->cancel();
    }

    LayerDatabase::DescriptorVector descriptors(layerDB->getLayers(filePath));
    auto end(descriptors.end());

    for (auto iter (descriptors.begin ());
         iter != end;
         ++iter)
      {
        int64_t layerID ((*iter)->getLayerID ());
        auto mapIter (layerDescriptors.find (layerID));

        if (mapIter != layerDescriptors.end ())
          {
            layerDescriptors.erase (mapIter);
            contentModified = true;
          }

        delete *iter;
      }
    layerDB->deleteCatalogPath (filePath);
    return contentModified;
  }

PersistentRasterDataStore::QueryInfoSpec
PersistentRasterDataStore::validateQueryInfoSpecNoSync(const char *dataset, const char *imageryType)
  {
      QueryInfoSpec spec{ NAN, NAN, nullptr, 0 };

      DatasetQueryParameters params;
      if (dataset)
        params.names.push_back(dataset);
      if (imageryType)
          params.imageryTypes.push_back(imageryType);

      std::unique_ptr<DatasetDescriptorCursor> result(queryDatasets(params));
      if (result->moveToNext()) {
          std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String> infoSpecKey(dataset, imageryType);

          DatasetDescriptor *desc;

          desc = &result->get();

          spec.minResolution = desc->getMinResolution(imageryType);
          spec.maxResolution = desc->getMaxResolution(imageryType);
          spec.coverage = desc->getCoverage(imageryType);
          spec.count++;

          if (result->moveToNext()) {
              auto *scratch = new feature::GeometryCollection(feature::Geometry::_2D);
              scratch->add(spec.coverage);

              double minRes;
              double maxRes;
              do {
                  desc = &result->get();

                  minRes = desc->getMinResolution(imageryType);
                  maxRes = desc->getMaxResolution(imageryType);

                  if (minRes > spec.minResolution)
                      spec.minResolution = minRes;
                  if (maxRes < spec.maxResolution)
                      spec.maxResolution = maxRes;
                  scratch->add(desc->getCoverage(imageryType));
                  spec.count++;
              } while (result->moveToNext());

              spec.coverage = scratch;

              if (RESOLVE_COLLECTION_COVERAGES) {
                  auto entry = pendingCoverages.find(infoSpecKey);
                  if (entry == pendingCoverages.end() || entry->second->isCanceled()) {
                      BackgroundCoverageResolver *resolver;
                      if (entry != pendingCoverages.end()) {
                          resolver = entry->second;
                          canceledResolvers.push_back(resolver);
                      }

                      resolver = new BackgroundCoverageResolver(*this, dataset, imageryType, *scratch);
                      pendingCoverages[infoSpecKey] = resolver;
                      resolver->start();
                  }

                  std::list<BackgroundCoverageResolver *>::iterator canceled;
                  canceled = canceledResolvers.begin();
                  while (canceled != canceledResolvers.end())
                  {
                      if ((*canceled)->done) {
                          delete *canceled;
                          canceled = canceledResolvers.erase(canceled);
                      } else {
                          canceled++;
                      }
                  }
              }
          }
          else if(spec.coverage) {
              spec.coverage = spec.coverage->clone();
          }

          infoCache[infoSpecKey] = spec;
      }

      return spec;
  }

PersistentRasterDataStore::BackgroundCoverageResolver::BackgroundCoverageResolver(PersistentRasterDataStore &owner_, const char *dataset_, const char *type_, const feature::GeometryCollection &geom_) :
    dataset(dataset_),
    type(type_),
    geom(geom_),
    canceled(false),
    owner(owner_),
    worker(nullptr, nullptr),
    done(false)
{}

PersistentRasterDataStore::BackgroundCoverageResolver::~BackgroundCoverageResolver()
{
    this->canceled = true;
    if (this->worker) {
        this->worker->join();
        this->worker.reset();
    }
}

void PersistentRasterDataStore::BackgroundCoverageResolver::start()
{
    Thread_start(this->worker, &threadFn, static_cast<void *>(this));
}

void *PersistentRasterDataStore::BackgroundCoverageResolver::threadFn(void *opaque)
{
    auto *instance = static_cast<BackgroundCoverageResolver *>(opaque);
    instance->run();
    return nullptr;
}

void PersistentRasterDataStore::BackgroundCoverageResolver::run()
{
    class OnExit
    {
    public :
        OnExit(bool &exited_) :
            exited(exited_)
        {}
        ~OnExit()
        {
            exited = true;
        }
    private :
        bool &exited;
    };

    OnExit onExit(this->done);

    feature::SpatialCalculator calc(nullptr);

    feature::SpatialCalculator::Batch batch(&calc);
    if (this->canceled)
        return;

    std::unique_ptr<feature::Geometry> gunion(calc.getGeometry(calc.unaryUnion(calc.createGeometry(&this->geom))));
    {
        Lock lock(owner.getMutex());
        if (this->canceled)
            return;

        if (gunion.get())
            owner.geometryResolvedNoSync(this->dataset, this->type, *gunion.get(), true);
    }
}

void PersistentRasterDataStore::BackgroundCoverageResolver::cancel()
{
    canceled = true;
}

bool PersistentRasterDataStore::BackgroundCoverageResolver::isCanceled()
{
    return canceled;
}

}                                       // Close raster namespace.
}                                       // Close atakmap namespace.

