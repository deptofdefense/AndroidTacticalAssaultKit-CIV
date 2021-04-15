#include "model/SceneInfo.h"

#include <map>
#include <vector>

#include "feature/Point2.h"
#include "feature/GeometryCollection2.h"
#include "feature/GeometryTransformer.h"
#include "port/STLVectorAdapter.h"
#include "util/Memory.h"
#include "util/CopyOnWrite.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;

SceneInfo::SceneInfo() NOTHROWS
	: minDisplayResolution(156412.0),
	  maxDisplayResolution(0.0),
      resolution(0.0),
      location(nullptr, nullptr),
      localFrame(nullptr, nullptr),
      resourceAliases(nullptr, nullptr),
	  altitudeMode(TEAM_Absolute),
	  srid(-1),
      aabb(nullptr, nullptr)
{}

SceneInfo::SceneInfo(const SceneInfo &other) NOTHROWS
    : minDisplayResolution(other.minDisplayResolution),
      maxDisplayResolution(other.maxDisplayResolution),
      resolution(other.resolution),
      uri(other.uri),
      name(other.name),
      type(other.type),
      location(std::move(GeoPoint2Ptr(other.location.get() ? new GeoPoint2(*other.location) : nullptr, Memory_deleter_const<GeoPoint2>))),
      localFrame(other.localFrame ? new(std::nothrow) Matrix2(*other.localFrame) : nullptr, Memory_deleter_const<Matrix2>),
      resourceAliases(nullptr, nullptr),
      altitudeMode(other.altitudeMode),
      srid(other.srid),
      aabb(std::move(Envelope2Ptr(other.aabb.get() ? new Envelope2(*other.aabb) : nullptr, Memory_deleter_const<Envelope2>)))
{
    if (other.resourceAliases.get()) {
        do {
            TAKErr code(TE_Ok);
            // XXX - const cast....
            auto &resource_aliases = const_cast<Collection<ResourceAlias> &>(*other.resourceAliases);
            if (resource_aliases.empty())
                break;

            std::unique_ptr<STLVectorAdapter<ResourceAlias>> aliases(new STLVectorAdapter<ResourceAlias>());

            Collection<ResourceAlias>::IteratorPtr iter(nullptr, nullptr);
            code = resource_aliases.iterator(iter);
            TE_CHECKBREAK_CODE(code);
            do {
                ResourceAlias alias;
                code = iter->get(alias);
                TE_CHECKBREAK_CODE(code);

                code = aliases->add(alias);
                TE_CHECKBREAK_CODE(code);

                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKBREAK_CODE(code);

            this->resourceAliases = ResourceAliasCollectionPtr(aliases.release(), Memory_deleter_const<Collection<ResourceAlias>, STLVectorAdapter<ResourceAlias>>);
        } while (false);
    }
}

SceneInfo &SceneInfo::operator=(const SceneInfo &other) NOTHROWS
{
    this->name = other.name;
    this->uri = other.uri;
    this->type = other.type;
    if (other.location.get())
        this->location = GeoPoint2Ptr(new GeoPoint2(*other.location), Memory_deleter_const<GeoPoint2>);
    else
        this->location.reset();
    if (other.localFrame.get())
        this->localFrame = Matrix2Ptr_const(new Matrix2(*other.localFrame), Memory_deleter_const<Matrix2>);
    else
        this->localFrame.reset();
    this->srid = other.srid;
    this->altitudeMode = other.altitudeMode;
    this->minDisplayResolution = other.minDisplayResolution;
    this->maxDisplayResolution = other.maxDisplayResolution; \
    this->resolution = other.resolution;
    if (other.aabb)
        aabb = Envelope2Ptr(new Envelope2(*other.aabb), Memory_deleter_const<Envelope2>);
    return *this;
}

SceneInfo::~SceneInfo() NOTHROWS
{}

TAKErr TAK::Engine::Model::SceneInfo_getBoundingBoxWGS84(Envelope2 *value, const SceneInfo &info) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (!info.aabb.get())
        return TE_InvalidArg;

    const Envelope2 &aabb = *info.aabb;

    TAKErr code(TE_Ok);
    TAK::Engine::Math::Point2<double> scratch;

    GeometryCollection2 points;
    code = points.setDimension(3u);
    scratch.x = aabb.minX;
    scratch.y = aabb.minY;
    scratch.z = aabb.minZ;
    if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.minX;
    scratch.y = aabb.maxY;
    scratch.z = aabb.minZ;
    if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.maxX;
    scratch.y = aabb.maxY;
    scratch.z = aabb.minZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.maxX;
    scratch.y = aabb.minY;
    scratch.z = aabb.minZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.minX;
    scratch.y = aabb.minY;
    scratch.z = aabb.maxZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.minX;
    scratch.y = aabb.maxY;
    scratch.z = aabb.maxZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.maxX;
    scratch.y = aabb.maxY;
    scratch.z = aabb.maxZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);
    scratch.x = aabb.maxX;
    scratch.y = aabb.minY;
    scratch.z = aabb.maxZ;
        if (info.localFrame.get()) {
        code = info.localFrame->transform(&scratch, scratch);
        TE_CHECKRETURN_CODE(code);
    }
    code = points.addGeometry(TAK::Engine::Feature::Point2(scratch.x, scratch.y, scratch.z));
    TE_CHECKRETURN_CODE(code);

    Geometry2Ptr xformed(nullptr, nullptr);
    code = GeometryTransformer_transform(xformed, points, info.srid, 4326);
    TE_CHECKRETURN_CODE(code);

    code = xformed->setDimension(2);
    TE_CHECKRETURN_CODE(code);

    code = xformed->getEnvelope(value);
    TE_CHECKRETURN_CODE(code);

    return code;
}

SceneInfoSpi::~SceneInfoSpi() NOTHROWS
{}

Georeferencer::~Georeferencer() NOTHROWS
{}

namespace {
    class SceneInfoFactoryRegistry {
    public:
        TAK::Engine::Util::TAKErr registerSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr);
        TAK::Engine::Util::TAKErr unregisterSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr);
        TAK::Engine::Util::TAKErr create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path, const char *hint) const NOTHROWS;
        bool isSupported(const char *path, const char *hint) const NOTHROWS;

    private:
        struct CStringLess {
            bool operator()(const char *lhs, const char *rhs) const NOTHROWS {
                return strcmp(lhs, rhs) < 0;
            }
        };

        std::multimap<const char *, std::shared_ptr<SceneInfoSpi>, CStringLess> hint_sorted;
        std::multimap<int, std::shared_ptr<SceneInfoSpi>> priority_sorted;
    };

    CopyOnWrite<SceneInfoFactoryRegistry> &sharedSceneInfoSpiRegistry() {
        static CopyOnWrite<SceneInfoFactoryRegistry> impl;
        return impl;
    }

    class GeoreferencerRegistry {
    public:
        TAK::Engine::Util::TAKErr registerGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS;
        TAK::Engine::Util::TAKErr unregisterGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS;
        TAK::Engine::Util::TAKErr locate(SceneInfo &sceneInfo) const NOTHROWS;

    private:
        std::vector<std::shared_ptr<Georeferencer>> georeferencers;
    };

    CopyOnWrite<GeoreferencerRegistry> &sharedGeoreferencerRegistry() {
        static CopyOnWrite<GeoreferencerRegistry> impl;
        return impl;
    }
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneInfoFactory_registerSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr) NOTHROWS {
    return sharedSceneInfoSpiRegistry().invokeWrite(&SceneInfoFactoryRegistry::registerSpi, spiPtr);
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneInfoFactory_unregisterSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr) NOTHROWS {
    return sharedSceneInfoSpiRegistry().invokeWrite(&SceneInfoFactoryRegistry::unregisterSpi, spiPtr);
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneInfoFactory_create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path, const char *hint) NOTHROWS {
    std::shared_ptr<const SceneInfoFactoryRegistry> registry = sharedSceneInfoSpiRegistry().read();
    TAKErr code(TE_Unsupported);
    
    if (registry) {
        code = registry->create(scenes, path, hint);
        if (code != TE_Ok)
            return code;

        std::shared_ptr<const GeoreferencerRegistry> georeferencerRegistry = sharedGeoreferencerRegistry().read();
        if (georeferencerRegistry) {
            TAK::Engine::Port::Collection<SceneInfoPtr>::IteratorPtr iterPtr(nullptr, nullptr);
            code = scenes.iterator(iterPtr);
            TE_CHECKRETURN_CODE(code);

            do {
                SceneInfoPtr sceneInfoPtr;
                code = iterPtr->get(sceneInfoPtr);
                TE_CHECKRETURN_CODE(code);

                if (sceneInfoPtr->srid == -1) {
                    if (georeferencerRegistry->locate(*sceneInfoPtr) != TE_Ok)
                        Logger_log(TELL_Debug, "Unable to georeference %s", sceneInfoPtr->name);
                }
                code = iterPtr->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);

            if (code == TE_Done)
                code = TE_Ok;

            TE_CHECKRETURN_CODE(code);
        }

    }
    return code;
}

ENGINE_API bool TAK::Engine::Model::SceneInfoFactory_isSupported(const char *path, const char *hint) NOTHROWS {
    std::shared_ptr<const SceneInfoFactoryRegistry> registry = sharedSceneInfoSpiRegistry().read();
    if (registry) {
        return registry->isSupported(path, hint);
    }
    return false;
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneInfoFactory_registerGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS {
    return sharedGeoreferencerRegistry().invokeWrite(&GeoreferencerRegistry::registerGeoreferencer, georeferencer);
}

ENGINE_API TAK::Engine::Util::TAKErr TAK::Engine::Model::SceneInfoFactory_unregisterGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS {
    return sharedGeoreferencerRegistry().invokeWrite(&GeoreferencerRegistry::unregisterGeoreferencer, georeferencer);
}

namespace {
    template <typename Container, typename K>
    void eraseIf(Container &m, const K &key, const std::shared_ptr<SceneInfoSpi> &spi) {
        auto range = m.equal_range(key);
        for (auto it = range.first; it != range.second; ++it) {
            if (it->second == spi) {
                m.erase(it);
                break;
            }
        }
    }

    template <typename Iter>
    TAKErr createImpl(Iter begin, Iter end, TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {
        while (begin != end) {
            TAKErr code;
            if ((code = begin->second->create(scenes, path)) != TE_Unsupported) {
                return code;
            }
            ++begin;
        }
        return TE_Unsupported;
    }

    template <typename Iter>
    bool isSupportedImpl(Iter begin, Iter end, const char *path) NOTHROWS {
        while (begin != end) {
            if (begin->second->isSupported(path)) {
                return true;
            }
            ++begin;
        }
        return false;
    }

    TAK::Engine::Util::TAKErr SceneInfoFactoryRegistry::registerSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr) {
        hint_sorted.insert(std::make_pair(spiPtr->getName(), spiPtr));
        priority_sorted.insert(std::make_pair(spiPtr->getPriority(), spiPtr));
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SceneInfoFactoryRegistry::unregisterSpi(const std::shared_ptr<SceneInfoSpi> &spiPtr) {
        eraseIf(priority_sorted, spiPtr->getPriority(), spiPtr);
        eraseIf(hint_sorted, spiPtr->getName(), spiPtr);
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SceneInfoFactoryRegistry::create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path, const char *hint) const NOTHROWS {
        if (hint) {
            auto range = hint_sorted.equal_range(hint);
            return createImpl(range.first, range.second, scenes, path);
        }
        return createImpl(priority_sorted.begin(), priority_sorted.end(), scenes, path);
    }

    bool SceneInfoFactoryRegistry::isSupported(const char *path, const char *hint) const NOTHROWS {
        if (hint) {
            auto range = hint_sorted.equal_range(hint);
            return isSupportedImpl(range.first, range.second, path);
        }
        return isSupportedImpl(priority_sorted.begin(), priority_sorted.end(), path);
    }

    TAK::Engine::Util::TAKErr GeoreferencerRegistry::registerGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS {
        if (std::find(georeferencers.begin(), georeferencers.end(), georeferencer) == georeferencers.end()) {
            georeferencers.push_back(georeferencer);
        }
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr GeoreferencerRegistry::unregisterGeoreferencer(const std::shared_ptr<Georeferencer> &georeferencer) NOTHROWS {
        auto it = std::find(georeferencers.begin(), georeferencers.end(), georeferencer);
        if (it != georeferencers.end()) {
            georeferencers.erase(it);
        }
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr GeoreferencerRegistry::locate(SceneInfo &sceneInfo) const NOTHROWS {
        auto it = georeferencers.begin();
        auto end = georeferencers.end();
        if (it != end) {
            TAKErr code = (*it)->locate(sceneInfo);
            if (code != TE_Unsupported)
                return code;
            ++it;
        }
        return TE_Unsupported;
    }
}
