
#include <map>
#include <vector>
#include <sstream>
#include "model/ResourceMapper.h"
#include "port/String.h"
#include "util/IO2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

ResourceAlias::ResourceAlias()
{}

ResourceAlias::ResourceAlias(const char *rr, const char *tp)
    : resourceRef(rr ? rr : ""), targetPath(tp ? tp : "")
{}

ResourceAlias::~ResourceAlias()
{}

const char *ResourceAlias::getResourceRef() const {
    return resourceRef.get();
}

const char *ResourceAlias::getTargetPath() const {
    return targetPath.get();
}

bool ResourceAlias::operator==(const ResourceAlias &other) const {
    return resourceRef == other.resourceRef;
}

bool ResourceAlias::operator!=(const ResourceAlias &other) const {
    return resourceRef != other.resourceRef;
}

//
// ResourceMapper
//

ResourceMapper::ResourceMapper()
{}

TAK::Engine::Util::TAKErr ResourceMapper::loadAliases(const TAK::Engine::Port::Collection<ResourceAlias> &aliases) NOTHROWS {
    TAKErr code(TE_Ok);
    if (!const_cast<TAK::Engine::Port::Collection<ResourceAlias> &>(aliases).empty()) {
        TAK::Engine::Port::Collection<ResourceAlias>::IteratorPtr iter(nullptr, nullptr);
        code = const_cast<TAK::Engine::Port::Collection<ResourceAlias> &>(aliases).iterator(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            ResourceAlias alias;
            code = iter->get(alias);
            TE_CHECKRETURN_CODE(code);

            TE_BEGIN_TRAP() {
                this->keys.push_back(alias.getResourceRef());
                this->lookup.insert(std::make_pair(keys.back().c_str(), alias.getTargetPath()));
            } TE_END_TRAP(code);
            TE_CHECKRETURN_CODE(code);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
    }
    return code;
}

ResourceMapper::~ResourceMapper() 
{}

TAKErr ResourceMapper::getResourceRefTargetPath(TAK::Engine::Port::String &targetPath, const char *resourceRef) const NOTHROWS {

    if (!resourceRef)
        return TE_InvalidArg;

    TAKErr code(TE_Unsupported);

    auto it = lookup.find(resourceRef);
    if (it != lookup.end()) {
        targetPath = it->second.c_str();
        code = TE_Ok;
    }

    return code;
}

bool ResourceMapper::hasResourceAliases() const {
    return !lookup.empty();
}

TAK::Engine::Util::TAKErr TAK::Engine::Model::ResourceMapper_getResourceMappingAbsolutePath(
    TAK::Engine::Port::String &result, 
    const ResourceMapper &resourceMapper,
    const char *resourceRef, 
    const char *pathBase) NOTHROWS {

    if (!resourceRef || !pathBase)
        return TE_InvalidArg;

    TAK::Engine::Port::String workingString;
    TAKErr code = resourceMapper.getResourceRefTargetPath(workingString, resourceRef);
    TE_CHECKRETURN_CODE(code);

    TE_BEGIN_TRAP() {

        std::ostringstream ss;
        ss << pathBase << TAK::Engine::Port::Platform_pathSep() << workingString;

        code = IO_correctPathSeps(workingString, ss.str().c_str());
        TE_CHECKRETURN_CODE(code);

        code = IO_getAbsolutePath(workingString, workingString.get());
        TE_CHECKRETURN_CODE(code);

        result = workingString;

    } TE_END_TRAP(code);

    return code;
}
