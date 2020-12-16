////============================================================================
////
////    FILE:           OGR_DriverDefinition.cpp
////
////    DESCRIPTION:    Implementation of OGR_DriverDefinition static member
////                    functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 16, 2015  scott           Created.
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


#include "feature/OGR_DriverDefinition.h"

#include <map>
#include <stdexcept>
#include <utility>

#include "thread/Lock.h"
#include "thread/Mutex.h"

#include "feature/OGRDriverDefinition2.h"
//#include "feature/SingletonDriverDefinition2Spi.h"
#include "util/Memory.h"

#define MEM_FN( fn )    "atakmap::feature::OGR_DriverDefinition::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

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

class DriverAdapter : public TAK::Engine::Feature::OGRDriverDefinition2
{
public:
    DriverAdapter(const char *filePath, const atakmap::feature::OGR_DriverDefinition *impl) NOTHROWS;
    ~DriverAdapter() NOTHROWS override;
public: // driver definition
    const char* getDriverName() const NOTHROWS override;
    atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding getFeatureEncoding() const NOTHROWS override;
    TAK::Engine::Util::TAKErr getStyle(TAK::Engine::Port::String &value, const OGRFeature&, const OGRGeometry&) NOTHROWS override;
    const char* getType() const NOTHROWS override;
    unsigned int parseVersion() const NOTHROWS override;
    TAK::Engine::Util::TAKErr skipFeature(bool *value, const OGRFeature&) NOTHROWS override;
    TAK::Engine::Util::TAKErr skipLayer(bool *value, const OGRLayer&) NOTHROWS override;
    bool layerNameIsPath() const NOTHROWS override;
private :
    TAK::Engine::Port::String filePath;
    const atakmap::feature::OGR_DriverDefinition * const impl;
};

class DriverAdapterSpi : public TAK::Engine::Feature::OGRDriverDefinition2Spi
{
public:
    DriverAdapterSpi(const atakmap::feature::OGR_DriverDefinition *impl) NOTHROWS;
public:
    TAK::Engine::Util::TAKErr create(TAK::Engine::Feature::OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS override;
    const char* getType() const NOTHROWS override;
public:
    const atakmap::feature::OGR_DriverDefinition * const impl;
};

typedef std::map<TAK::Engine::Port::String,
                 std::shared_ptr<const DriverAdapterSpi>,
                 TAK::Engine::Port::StringLess>
        DriverMap;

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

Mutex &
mutex()
    NOTHROWS
  {
      static Mutex m(TEMT_Recursive);
      return m;
  }

DriverMap&
getDriverMap ()
  {
    //
    // The extra parens around DriverMap() keep the statement from being
    // parsed as a function declaration.
    //
    static DriverMap driverMap;
    return driverMap;
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
namespace feature                       // Open feature namespace.
{


///=====================================
///  OGR_DriverDefinition MEMBER FUNCTIONS
///=====================================

OGR_DriverDefinition::~OGR_DriverDefinition ()
    NOTHROWS
  { }

const OGR_DriverDefinition*
OGR_DriverDefinition::getDriver (const char* driverName)
  {
    if (!driverName)
      {
        throw std::invalid_argument (MEM_FN ("getDriver")
                                     "Received NULL driverName");
      }

    Lock lock(mutex());
    DriverMap& driverMap (getDriverMap ());

    DriverMap::const_iterator iter (driverMap.find (driverName));

    if (iter == driverMap.end())
        return nullptr;

    return iter->second->impl;
  }


void
OGR_DriverDefinition::registerDriver (const OGR_DriverDefinition* driver)
  {
    if (driver && driver->getDriverName ())
      {
        Lock lock(mutex());
        DriverMap& driverMap(getDriverMap());

        DriverMap::const_iterator iter(driverMap.find(driver->getDriverName()));
        if (iter != driverMap.end())
        {
            TAK::Engine::Feature::OGRDriverDefinition2_unregisterSpi(iter->second.get());
        }

        std::shared_ptr<DriverAdapterSpi> spiPtr(new DriverAdapterSpi(driver));

        TAK::Engine::Feature::OGRDriverDefinition2_registerSpi(spiPtr);

        driverMap.insert (std::make_pair
                                     (TAK::Engine::Port::String (driver->getDriverName ()),
                                     spiPtr));
      }
  }


void
OGR_DriverDefinition::unregisterDriver (const OGR_DriverDefinition* driver)
  {
    if (driver && driver->getDriverName ())
      {
        Lock lock(mutex());
        DriverMap& driverMap(getDriverMap());

        driverMap.erase (driver->getDriverName ());
      }
  }


}                                       // Close feature namespace.
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


namespace                               // Open unnamed namespace.
{

    DriverAdapter::DriverAdapter(const char *filePath_, const atakmap::feature::OGR_DriverDefinition *impl_) NOTHROWS :
        filePath(filePath_),
        impl(impl_)
    {}
    DriverAdapter::~DriverAdapter() NOTHROWS
    {}
    const char* DriverAdapter::getDriverName() const NOTHROWS
    {
        return impl->getDriverName();
    }
    atakmap::feature::FeatureDataSource::FeatureDefinition::Encoding DriverAdapter::getFeatureEncoding() const NOTHROWS
    {
        return impl->getFeatureEncoding();
    }
    TAK::Engine::Util::TAKErr DriverAdapter::getStyle(TAK::Engine::Port::String &value, const OGRFeature &f, const OGRGeometry &g) NOTHROWS
    {
        try {
            auto style = impl->getStyle(filePath, f, g);
            if (style) {
                value = style;
            }
            return TAK::Engine::Util::TE_Ok;
        } catch (...) {
            return TAK::Engine::Util::TE_Err;
        }
    }
    const char* DriverAdapter::getType() const NOTHROWS
    {
        return impl->getType();
    }
    unsigned int DriverAdapter::parseVersion() const NOTHROWS
    {
        return impl->parseVersion();
    }
    TAK::Engine::Util::TAKErr DriverAdapter::skipFeature(bool *value, const OGRFeature &f) NOTHROWS
    {
        try {
            *value = impl->skipFeature(f);
            return TAK::Engine::Util::TE_Ok;
        } catch (...) {
            return TAK::Engine::Util::TE_Err;
        }
    }
    TAK::Engine::Util::TAKErr DriverAdapter::skipLayer(bool *value, const OGRLayer &l) NOTHROWS
    {
        try {
            *value = impl->skipLayer(l);
            return TAK::Engine::Util::TE_Ok;
        } catch (...) {
            return TAK::Engine::Util::TE_Err;
        }
    }
    bool DriverAdapter::layerNameIsPath() const NOTHROWS
    {
        return impl->layerNameIsPath();
    }

    DriverAdapterSpi::DriverAdapterSpi(const atakmap::feature::OGR_DriverDefinition *impl_) NOTHROWS :
        impl(impl_)
    {}

    TAK::Engine::Util::TAKErr DriverAdapterSpi::create(TAK::Engine::Feature::OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS
    {
        value = TAK::Engine::Feature::OGRDriverDefinition2Ptr(new DriverAdapter(path, impl), TAK::Engine::Util::Memory_deleter_const<TAK::Engine::Feature::OGRDriverDefinition2, DriverAdapter>);
        return TAK::Engine::Util::TE_Ok;
    }

    const char* DriverAdapterSpi::getType() const NOTHROWS
    {
        return impl->getDriverName();
    }
}                                       // Close unnamed namespace.