#ifndef TAK_ENGINE_MODEL_RESOURCEMAPPER_H_INCLUDED
#define TAK_ENGINE_MODEL_RESOURCEMAPPER_H_INCLUDED

#include <map>
#include <vector>
#include <string>
#include "port/String.h"
#include "port/Collection.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {

            struct ENGINE_API ResourceAlias {

                ResourceAlias();
                ResourceAlias(const char *resourceRef, const char *targetPath);
                ~ResourceAlias();

                const char *getResourceRef() const;
                const char *getTargetPath() const;

                bool operator==(const ResourceAlias &other) const;
                bool operator!=(const ResourceAlias &other) const;

            private:
                TAK::Engine::Port::String resourceRef;
                TAK::Engine::Port::String targetPath;
            };

            typedef std::unique_ptr<const TAK::Engine::Port::Collection<ResourceAlias>, 
                void(*)(const TAK::Engine::Port::Collection<ResourceAlias> *)> ResourceAliasCollectionPtr;

            class ENGINE_API ResourceMapper {
            public:
                ResourceMapper();
                ~ResourceMapper();

                TAK::Engine::Util::TAKErr loadAliases(const TAK::Engine::Port::Collection<ResourceAlias> &aliases) NOTHROWS;


                TAK::Engine::Util::TAKErr getResourceRefTargetPath(TAK::Engine::Port::String &targetPath, const char *resourceRef) const NOTHROWS;

                bool hasResourceAliases() const;

            private:

                struct LookupCmp {
                    bool operator()(const char *lhs, const char *rhs) const {
                        int v = -1;
                        TAK::Engine::Port::String_compareIgnoreCase(&v, lhs, rhs);
                        return v < 0;
                    }
                };

                std::map<const char *, std::string, LookupCmp> lookup;
                std::vector<std::string> keys;
            };

            TAK::Engine::Util::TAKErr ResourceMapper_getResourceMappingAbsolutePath(
                TAK::Engine::Port::String &result,
                const ResourceMapper &resourceMapper,
                const char *resourceRef,
                const char *pathBase) NOTHROWS;


        }
    }
}

#endif
