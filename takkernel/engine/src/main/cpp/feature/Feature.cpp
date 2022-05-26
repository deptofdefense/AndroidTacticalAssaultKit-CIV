
#include "feature/Feature.h"

#include "feature/FeatureDataStore.h"
#include "feature/Style.h"
#include "util/AttributeSet.h"

using namespace atakmap::feature;

using namespace atakmap::util;

#define MEM_FN( fn )    "atakmap::feature::Feature::" fn ": "

Feature::Feature(int64_t ID,
    int64_t setID,
    unsigned long version,
    const char* name,
    Geometry* geometry,   // Adopted (and destroyed) by Feature.
    Style* style,         // Adopted (and destroyed) by Feature.
    const AttributeSet& attributes)
    : ID(ID),
    setID(setID),
    version(version),
    name(name),
    geometry(geometry),
    style(style),
    attributes(attributes)
{
    /*if (!name)
    {
    throw std::invalid_argument (MEM_FN ("Feature")
    "Received NULL name");
    }
    if (!geometry)
    {
    throw std::invalid_argument (MEM_FN ("Feature")
    "Received NULL Geometry");
    }*/
}

Feature::Feature(const char* name,
    Geometry* geometry,   // Adopted (and destroyed) by Feature.
    Style* style,         // Adopted (and destroyed) by Feature.
    const AttributeSet& attributes)
    : ID(FeatureDataStore::ID_NONE),
    setID(FeatureDataStore::ID_NONE),
    version(FeatureDataStore::VERSION_NONE),
    name(name),
    geometry(geometry),
    style(style),
    attributes(attributes)
{
    if (!name)
    {
        throw std::invalid_argument(MEM_FN("Feature")
            "Received NULL name");
    }
    if (!geometry)
    {
        throw std::invalid_argument(MEM_FN("Feature")
            "Received NULL Geometry");
    }
}




Feature::~Feature()
    NOTHROWS
{ }

const AttributeSet&
Feature::getAttributes()
const
NOTHROWS
{
    return attributes;
}

int64_t
Feature::getFeatureSetID()
const
NOTHROWS
{
    return setID;
}

const Geometry&
Feature::getGeometry()
const
NOTHROWS
{
    return *geometry;
}

int64_t
Feature::getID()
const
NOTHROWS
{
    return ID;
}

const char*
Feature::getName()
const
NOTHROWS
{
    return name;
}

const Style*
Feature::getStyle()
const
NOTHROWS
{
    return style.get();
}

unsigned long
Feature::getVersion()
const
NOTHROWS
{
    return version;
}

bool Feature::isSame(const atakmap::feature::Feature &a, const atakmap::feature::Feature &b) {
    return (a.getID() == b.getID()) &&
        (a.getVersion() == b.getVersion()) &&
        (a.getID() != FeatureDataStore::ID_NONE && b.getID() != FeatureDataStore::ID_NONE) &&
        (a.getVersion() != FeatureDataStore::VERSION_NONE && b.getVersion() != FeatureDataStore::VERSION_NONE);
}

#undef MEM_FN
