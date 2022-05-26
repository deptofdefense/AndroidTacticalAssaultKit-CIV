#include "feature/FeatureSet.h"

using namespace atakmap::feature;

#define MEM_FN( fn )    "atakmap::feature::FeatureSet::" fn ": "

FeatureSet::FeatureSet (int64_t id,
                        unsigned long version,
                        const char* provider,
                        const char* type,
                        const char* name,
                        double minResolution,
                        double maxResolution)
    : ID(id),
      version(version),
      owner(nullptr),
      provider(provider),
      type(type),
      name(name),
      minResolution(minResolution),
      maxResolution(maxResolution)
{}

FeatureSet::FeatureSet (const char* provider,
                        const char* type,
                        const char* name,
                        double minResolution,
                        double maxResolution)
  : ID (FeatureDataStore::ID_NONE),
    version (FeatureDataStore::VERSION_NONE),
    owner (nullptr),
    provider (provider),
    type (type),
    name (name),
    minResolution (minResolution),
    maxResolution (maxResolution)
  {
    if (!provider)
      {
        throw std::invalid_argument (MEM_FN ("FeatureSet")
                                     "Received NULL provider");
      }
    if (!type)
      {
        throw std::invalid_argument (MEM_FN ("FeatureSet")
                                     "Received NULL type");
      }
    if (!name)
      {
        throw std::invalid_argument (MEM_FN ("FeatureSet")
                                     "Received NULL name");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("FeatureSet")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("FeatureSet")
                                     "Received negative maxResolution");
      }
  }

FeatureSet::~FeatureSet ()
    NOTHROWS
    { }


FeatureDataStore::FeatureCursor*
FeatureSet::getFeatures ()
    const
    NOTHROWS
try
  {
    typedef std::shared_ptr<QueryParams::Order>   OrderPtr;

    if (!queryParams)
      {
        queryParams = QueryParamsPtr (new QueryParams ());
        queryParams->featureSetIDs = std::vector<int64_t> (1, ID);
        queryParams->orders = std::vector<OrderPtr>
                                  (1, OrderPtr (new QueryParams::Name ()));
      }

    return owner ? owner->queryFeatures (*queryParams) : nullptr;
  }
catch (...)
  { return nullptr; }


int64_t
FeatureSet::getID ()
    const
    NOTHROWS
    { return ID; }

//
// Returns the ground sample distance (in meters/pixel) of the "highest
// resolution" at which the features should be displayed.  A value of 0.0
// indicates that there is no maximum.
//
// N.B.:    As "resolution" increases (in the conventional sense), the
//          number of meters/pixel decreases; thus the value returned by
//          getMaxResolution will be less than or equal to the value
//          returned by getMinResolution.
//
double
FeatureSet::getMaxResolution ()
    const
    NOTHROWS
    { return maxResolution; }

//
// Returns the ground sample distance (in meters/pixel) of the "lowest
// resolution" at which the features should be displayed.  A value of 0.0
// indicates that there is no minimum.
//
// N.B.:    As "resolution" decreases (in the conventional sense), the
//          number of meters/pixel increases; thus the value returned by
//          getMinResolution will be greater than or equal to the value
//          returned by getMaxResolution.
//
double
FeatureSet::getMinResolution ()
    const
    NOTHROWS
    { return minResolution; }

const char*
FeatureSet::getName ()
    const
    NOTHROWS
    { return name; }

const char*
FeatureSet::getProvider ()
    const
    NOTHROWS
    { return provider; }

const char*
FeatureSet::getType ()
    const
    NOTHROWS
    { return type; }

unsigned long
FeatureSet::getVersion ()
    const
    NOTHROWS
    { return version; }

#undef MEM_FN