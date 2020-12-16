////============================================================================
////
////    FILE:           FeatureDataSource.cpp
////
////    DESCRIPTION:    Implementation of FeatureDataSource static member
////                    functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 22, 2015  scott           Created.
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


#include "feature/FeatureDataSource.h"

#include <cstddef>
#include <cstring>
#include <map>
#include <memory>
#include <stdexcept>
#include <utility>

#include "feature/Feature.h"
#include "feature/Geometry.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "port/String.h"
#include "util/Memory.h"


#define MEM_FN( fn )    "atakmap::feature::FeatureDataSource::" fn ": "


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


typedef std::map<TAK::Engine::Port::String,
                 const feature::FeatureDataSource*,
                 TAK::Engine::Port::StringLess>
        ProviderMap;


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


#if 0
PGSC::Guard<ProviderMap>&
#else
ProviderMap&
#endif
getProviderMap ()
  {
    //
    // The extra parens around ProviderMap() keep the statement from being
    // parsed as a function declaration.
    //
#if 0
    static PGSC::Guard<ProviderMap> providerMap
        ((ProviderMap ()),
         PGSC::Mutex::Attr (PGSC::Mutex::Attr::RECURSIVE));
#else
    static ProviderMap providerMap;
#endif

    return providerMap;
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


inline
void
FeatureDataSource::FeatureDefinition::copyBuffer (const ByteBuffer& buff)
  {
    std::size_t bytes (buff.second - buff.first);
    char* rawBuff (new char[bytes]);

    rawGeometry = std::memcpy (rawBuff, buff.first, bytes);
    bufferTail = rawBuff + bytes;
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


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
///  FeatureDataSource MEMBER FUNCTIONS
///=====================================

FeatureDataSource::~FeatureDataSource()
    NOTHROWS
{ }


const FeatureDataSource*
FeatureDataSource::getProvider (const char* providerName)
  {
    if (!providerName)
      {
        throw std::invalid_argument (MEM_FN ("getProvider")
                                     "Received NULL providerName");
      }

#if 0
    PGSC::Guard<ProviderMap>& providerMap (getProviderMap ());
    PGSC::Lock lock (providerMap);
    ProviderMap::const_iterator iter (providerMap (lock).find (providerName));

    return iter != providerMap(lock).end() ? iter->second : NULL;
#else
    ProviderMap& providerMap(getProviderMap());
    ProviderMap::const_iterator iter(providerMap.find(providerName));

    return iter != providerMap.end() ? iter->second : NULL;
#endif
  }


FeatureDataSource::Content*
FeatureDataSource::parse (const char* filePath,
                          const char* providerHint)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("parse") "Received NULL filePath");
      }

    Content* result (nullptr);
#if 0
    PGSC::Guard<ProviderMap>& providerMap (getProviderMap ());
    PGSC::Lock lock (providerMap);
    ProviderMap::iterator end (providerMap (lock).end ());
#else
    ProviderMap& providerMap(getProviderMap());
    auto end(providerMap.end());
#endif

    if (providerHint)
      {
#if 0
        ProviderMap::iterator iter (providerMap (lock).find (providerHint));
#else
        auto iter(providerMap.find(providerHint));
#endif

        if (iter != end)
          {
            result = iter->second->parseFile (filePath);
          }
      }
    else
      {
#if 0
        for (ProviderMap::iterator iter (providerMap (lock).begin ());
#else
        for (auto iter(providerMap.begin());
#endif
             !result && iter != end;
             ++iter)
          {
            try
              {
                result = iter->second->parseFile (filePath);
              }
            catch (...)
              { }
          }
      }

    return result;
  }


void
FeatureDataSource::registerProvider (const FeatureDataSource* provider)
  {
    if (provider && provider->getName ())
      {
#if 0
        PGSC::Guard<ProviderMap>& providerMap (getProviderMap ());
        PGSC::Lock lock (providerMap);

        providerMap (lock).insert (std::make_pair
                                       (PGSC::String (provider->getName ()),
                                        provider));
#else
        ProviderMap& providerMap(getProviderMap());

        providerMap.insert(std::make_pair
              (TAK::Engine::Port::String(provider->getName()),
              provider));
#endif
      }
  }


void
FeatureDataSource::unregisterProvider (const FeatureDataSource* provider)
  {
    if (provider && provider->getName ())
      {
#if 0
        PGSC::Guard<ProviderMap>& providerMap (getProviderMap ());
        PGSC::Lock lock (providerMap);

        providerMap (lock).erase (provider->getName ());
#else
          ProviderMap& providerMap(getProviderMap());

          providerMap.erase(provider->getName());
#endif
      }
  }


FeatureDataSource::Content::~Content()
    NOTHROWS
{ }


///=====================================
///  FeatureDataSource::FeatureDefinition MEMBER FUNCTIONS
///=====================================



FeatureDataSource::FeatureDefinition::~FeatureDefinition()
NOTHROWS
{
    freeGeometry();
    freeStyle();
}

void
FeatureDataSource::FeatureDefinition::setExtrude(double value) {
    extrude = value;
}

void
FeatureDataSource::FeatureDefinition::setAltitudeMode(int value)
{
    altitudeMode = value;
}

void
FeatureDataSource::FeatureDefinition::setStyle(const char* styleString)
{
    freeStyle();
    if(styleString) {
        TAK::Engine::Util::array_ptr<char> ogr(new char[strlen(styleString)+1u]);
        strcpy(ogr.get(), styleString);
        rawStyle = ogr.release();
    }
    styling = OGR;
}


void
FeatureDataSource::FeatureDefinition::setStyle(const Style* style)
{
    freeStyle();
    rawStyle = style;
    styling = STYLE;
}

FeatureDataSource::FeatureDefinition::FeatureDefinition
    (const char* name,
     const util::AttributeSet& attributes)
  : name (name),
    encoding (GEOMETRY),
    styling (STYLE),
    rawGeometry (nullptr),
    rawStyle (nullptr),
    bufferTail (nullptr),
    attributes (attributes)
  {
    if (!name)
      {
        throw std::invalid_argument
                  (MEM_FN ("FeatureDefinition::FeatureDefinition")
                   "Received NULL name");
      }
  }


Feature*
FeatureDataSource::FeatureDefinition::getFeature ()
  {
    std::unique_ptr<Geometry> geo;

    switch (encoding)
      {
      case WKT:

        geo.reset (parseWKT (static_cast<const char*> (rawGeometry)));
        break;

      case WKB:

        geo.reset (parseWKB (ByteBuffer (static_cast<const unsigned char*>
                                             (rawGeometry),
                                         static_cast<const unsigned char*>
                                             (bufferTail))));
        break;

      case BLOB:

        geo.reset (parseBlob (ByteBuffer (static_cast<const unsigned char*>
                                              (rawGeometry),
                                          static_cast<const unsigned char*>
                                              (bufferTail))));
        break;

      case GEOMETRY:

        geo.reset (static_cast<Geometry*> (const_cast<void*> (rawGeometry)));
        rawGeometry = nullptr;             // Adopted by Feature.
        break;
      }

    std::unique_ptr<Style> style;

    switch (styling)
      {
      case OGR:

        style.reset (Style::parseStyle (static_cast<const char*> (rawStyle)));
        break;

      case STYLE:

        style.reset (static_cast<Style*> (const_cast<void*> (rawStyle)));
        rawStyle = nullptr;                // Adopted by Feature.
        break;
      }

    return new Feature (name, geo.release (), style.release (), attributes);
  }


void
FeatureDataSource::FeatureDefinition::setGeometry (const char* geometryString)
  {
    if (!geometryString)
      {
        throw std::invalid_argument (MEM_FN ("setGeometry")
                                     "Received NULL geometry WKT string");
      }
    freeGeometry ();
    TAK::Engine::Util::array_ptr<char> wkt(new char[strlen(geometryString)+1u]);
    strcpy(wkt.get(), geometryString);
    rawGeometry = wkt.release();
    encoding = WKT;
  }


void
FeatureDataSource::FeatureDefinition::setGeometry (const Geometry* geometry)
  {
    if (!geometry)
      {
        throw std::invalid_argument (MEM_FN ("setGeometry")
                                     "Received NULL Geometry");
      }
    freeGeometry ();
    rawGeometry = geometry;
    encoding = GEOMETRY;
  }


void
FeatureDataSource::FeatureDefinition::setGeometry (const ByteBuffer& buffer,
                                                   Encoding bufferType)
  {
    if (!buffer.first || !buffer.second)
      {
        throw std::invalid_argument (MEM_FN ("setGeometry")
                                     "Received NULL geometry ByteBuffer");
      }
    if (bufferType != WKB && bufferType != BLOB)
      {
        throw std::invalid_argument (MEM_FN ("setGeometry")
                                     "Received invalid geometry ByteBuffer "
                                     "encoding");
      }
    freeGeometry ();
    copyBuffer (buffer);
    encoding = bufferType;
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=====================================
///  FeatureDataSource::FeatureDefinition MEMBER FUNCTIONS
///=====================================


void
FeatureDataSource::FeatureDefinition::freeGeometry ()
  {
    if (encoding == GEOMETRY)
      {
        delete static_cast<const Geometry*> (rawGeometry);
      }
    else
      {
        delete[] static_cast<const char*> (rawGeometry);
        encoding = GEOMETRY;
      }
    rawGeometry = bufferTail = nullptr;
  }


void
FeatureDataSource::FeatureDefinition::freeStyle ()
  {
    if (styling == STYLE)
      {
        delete static_cast<const Style*> (rawStyle);
      }
    else
      {
        delete[] static_cast<const char*> (rawStyle);
        styling = STYLE;
      }
    rawStyle = nullptr;
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
