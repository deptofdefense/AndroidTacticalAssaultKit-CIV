#include "feature/FeatureSet2.h"

using namespace TAK::Engine::Feature;

FeatureSet2::FeatureSet2(const FeatureSet2 &other) NOTHROWS :
    id(other.id),
    version(other.version),
    provider(other.provider),
    type(other.type),
    name(other.name),
    minGsd(other.minGsd),
    maxGsd(other.maxGsd)
{}

FeatureSet2::FeatureSet2(const int64_t id_, const char *provider_, const char *type_, const char *name_, const double minGsd_, const double maxGsd_, const int64_t version_) NOTHROWS :
    id(id_),
    version(version_),
    provider(provider_),
    type(type_),
    name(name_),
    minGsd(minGsd_),
    maxGsd(maxGsd_)
{}

const char *FeatureSet2::getProvider() const NOTHROWS
{
    return this->provider;
}

const char *FeatureSet2::getType() const NOTHROWS
{
    return this->type;
}

const char *FeatureSet2::getName() const NOTHROWS
{
    return this->name;
}

const int64_t FeatureSet2::getId() const NOTHROWS
{
    return this->id;
}

const double FeatureSet2::getMinResolution() const NOTHROWS
{
    return this->minGsd;
}

const double FeatureSet2::getMaxResolution() const NOTHROWS
{
    return this->maxGsd;
}

const int64_t FeatureSet2::getVersion() const NOTHROWS
{
    return this->version;
}
