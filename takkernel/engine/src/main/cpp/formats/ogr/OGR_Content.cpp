#include "OGR_Content.h"


#include <stdexcept>

#include "feature/FeatureDataSource.h"

using namespace TAK::Engine::Formats::OGR;

#define MEM_FN( fn )    "atakmap::feature::OGR_Content::" fn ": "

OGR_Content::OGR_Content(const char* filePath, std::size_t areaThreshold) :
    OGR_Content(filePath, nullptr, areaThreshold)
{}

OGR_Content::OGR_Content(const char* filePath, char **openOptions, std::size_t areaThreshold)
    : impl(filePath, openOptions, areaThreshold)
{
}

atakmap::feature::FeatureDataSource::FeatureDefinition *OGR_Content::get() const
{
    std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition> fd;
    if (impl.createLegacyResult(fd) != Util::TE_Ok)
        throw std::invalid_argument(MEM_FN("OGR_Content::get") "Unable to create FeatureDefinition");
    
    return fd.release();
}

