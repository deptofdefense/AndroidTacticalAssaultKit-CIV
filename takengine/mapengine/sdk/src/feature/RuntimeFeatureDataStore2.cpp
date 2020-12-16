
#include "feature/RuntimeFeatureDataStore2.h"

#include "feature/Style.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureSetCursor2.h"

#include "port/Iterator2.h"
#include "port/STLIteratorAdapter.h"
#include "port/Collections.h"

#include "thread/Lock.h"

#include "util/Memory.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace {
    template <typename T>
    void deleteDeleterFunc(const T *o) {
        delete o;
    }
    
    template <typename T, typename C>
    void castDeleteDeleterFunc(const T *o) {
        delete static_cast<const C *>(o);
    }
    
    template <typename T>
    void noopDeleterFunc(const T *)
    { }
    
    template <typename T>
    void featureRecordQuadtreeFunction(const std::shared_ptr<T> &object, atakmap::math::Point<double> &min, atakmap::math::Point<double> &max) {
        atakmap::feature::Envelope mbb = object->geom->getEnvelope();
        min.x = mbb.minX;
        min.y = mbb.minY;
        max.x = mbb.maxX;
        max.y = mbb.maxY;
    }
    
    template <typename MapType, typename ItemType>
    void insertNameindex(MapType &index, ItemType *item);
    
    template <typename MapType, typename ItemType>
    void insertNameindex(MapType &index, std::shared_ptr<ItemType> &item);
    
    template <typename MapType, typename ItemType>
    void removeNameindex(MapType &index, std::shared_ptr<ItemType> &item);
    
    template <typename MapType, typename ItemType>
    void removeNameindex(MapType &index, ItemType *item);
    
    template <typename MapType, typename ItemType>
    void updateNameindex(MapType &index, ItemType *oldItem, ItemType *newItem);
    
    template <typename MapType, typename ItemType>
    void updateNameindex2(MapType &index, std::shared_ptr<ItemType> &item, const char *oldName);
    
    
    
    template <typename MapType, typename ItemType>
    typename MapType::iterator removeFromFeatureSetList(MapType &index, const ItemType *item);
    
    template <typename MapType, typename ItemType>
    void updateFeatureSetList(MapType &index, const ItemType *oldItem, const ItemType *newItem);
    
    template <typename T, typename U>
    bool collectionFilter(Port::Collection<T> *collection, const U &value) {
        
        if (!collection || collection->size() == 0)
            return true;
        
        bool c = false;
        T valueT = value;
        collection->contains(&c, valueT);
        return c;
    }
    
    bool collectionFilter(Port::Collection<Port::String> *collection, const char *value) {
        
        if (!collection || collection->size() == 0)
            return true;
        
        bool result = false;
        AbstractFeatureDataStore2::matches(&result, *collection, value, '%');
        return result;
    }
    
    bool isValidResolutionValue(double value) {
        return value != 0 && !isnan(value);
    }
    
    bool filterMaxResolution(double storedValue, double testValue) {
        return !isValidResolutionValue(storedValue) || !isValidResolutionValue(testValue) || storedValue >= testValue;
    }
    
    bool filterMinResolution(double storedValue, double testValue) {
        return !isValidResolutionValue(storedValue) || !isValidResolutionValue(testValue) || storedValue <= testValue;
    }
    
    template <typename T, typename SetIndexType>
    bool secondaryParamsFilter(const T &feature, const SetIndexType &setIndex, const TAK::Engine::Feature::FeatureDataStore2::FeatureQueryParameters &params) {
        
        if (!::collectionFilter(params.featureNames, feature.name) ||
            !::collectionFilter(params.geometryTypes, feature.geom->getType()))
            return false;
        
        // Require a setIndex lookup
        if ((params.featureSets && params.featureSets->size()) ||
            (params.providers && params.providers->size()) ||
            (params.types && params.types->size()) ||
            isValidResolutionValue(params.minResolution) ||
            isValidResolutionValue(params.maxResolution) ||
            params.visibleOnly) {
            
            auto setIt = setIndex.find(feature.setId);
            
            if (!filterMinResolution(setIt->second.current->getMinResolution(), params.minResolution)) {
                return false;
            }
            if (!filterMaxResolution(setIt->second.current->getMaxResolution(), params.maxResolution)) {
                return false;
            }
            
            if (params.visibleOnly) {
                if ((setIt->second.visibleGeneration >= feature.visibleGeneration && !setIt->second.visible) ||
                    (feature.visibleGeneration > setIt->second.visibleGeneration && !feature.visible)) {
                    return false;
                }
            }
            
            if (!collectionFilter(params.featureSets, setIt->second.current->getName()) ||
                !collectionFilter(params.providers, setIt->second.current->getProvider()) ||
                !collectionFilter(params.types, setIt->second.current->getType())) {
                return false;
            }
        }
        
        return true;
    }
    
    template <typename T>
    bool featureSetParamsFilter(const T &featureSet, const TAK::Engine::Feature::AbstractFeatureDataStore2::FeatureSetQueryParameters &params);
    
    template <typename T>
    class RuntimeFeatureCursor2 : public FeatureCursor2 {
    public:
        RuntimeFeatureCursor2(std::vector<std::shared_ptr<T>> &&items);
        ~RuntimeFeatureCursor2() NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
        TAK::Engine::Util::TAKErr getId(int64_t *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
        Util::TAKErr getRawGeometry(RawData *value) NOTHROWS override;
        GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        Util::TAKErr getName(const char **value) NOTHROWS override;
        StyleEncoding getStyleCoding() NOTHROWS override;
        Util::TAKErr getRawStyle(RawData *value) NOTHROWS override;
        Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
        
    private:
        std::vector<std::shared_ptr<T>> items;
        typename std::vector<std::shared_ptr<T>>::iterator pos;
        Feature2 *cachedFeature2;
        uint8_t cachedFeature2Bytes[sizeof(Feature2)];
        bool beforeBegins;
    };
    
    template <typename T>
    class RuntimeFeatureSetCursor2 : public FeatureSetCursor2 {
    public:
        RuntimeFeatureSetCursor2(std::vector<std::shared_ptr<T>> &&items) NOTHROWS;
        ~RuntimeFeatureSetCursor2() NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
        TAK::Engine::Util::TAKErr get(const FeatureSet2 **featureSet) NOTHROWS override;
        
    private:
        std::vector<std::shared_ptr<T>> items;
        typename std::vector<std::shared_ptr<T>>::iterator pos;
    };
}

struct RuntimeFeatureDataStore2::FeatureSetRecord {
    FeatureSetRecord() :
    visibleGeneration(0),
    visible(true)
    { }
    
    FeatureSetRecord(std::shared_ptr<FeatureSet2> &&current) :
    current(std::move(current)),
    visibleGeneration(0),
    visible(true)
    { }
    
    inline const char *getName() const { return current->getName(); }
    inline bool operator==(const FeatureSetRecord &other) const { return current->getId() == other.current->getId(); }
    
    std::shared_ptr<FeatureSet2> current;
    std::list<const FeatureRecord *> features;
    int64_t visibleGeneration;
    bool visible;
};

struct RuntimeFeatureDataStore2::FeatureRecord {
       
    // avoids overhead of copying temp shared_ptrs
    inline FeatureRecord(int64_t id,
                         int64_t featureSetId,
                         const char *name,
                         std::shared_ptr<atakmap::feature::Style> &&style,
                         std::shared_ptr<atakmap::feature::Geometry> &&geom,
                         const AltitudeMode altitudeMode,
                         const double extrude,
                         std::shared_ptr<atakmap::util::AttributeSet> &&attrs,
                         int64_t version) :
    id(id),
    setId(featureSetId),
    style(std::move(style)),
    name(name),
    geom(std::move(geom)),
    altitudeMode(altitudeMode),
    extrude(extrude),
    attrs(std::move(attrs)),
    version(version),
    visibleGeneration(0),
    visible(true)
    { }

    inline const char *getName() const { return name; }
    
    int64_t id;
    int64_t setId;
    int64_t version;
    Port::String name;
    std::shared_ptr<atakmap::feature::Style> style;
    AltitudeMode altitudeMode;
    double extrude;
    std::shared_ptr<atakmap::feature::Geometry> geom;
    std::shared_ptr<atakmap::util::AttributeSet> attrs;
    int64_t visibleGeneration;
    bool visible;
};

RuntimeFeatureDataStore2::RuntimeFeatureDataStore2() NOTHROWS :
RuntimeFeatureDataStore2(FeatureDataStore2::MODIFY_BULK_MODIFICATIONS |
                         FeatureDataStore2::MODIFY_FEATURESET_INSERT |
                         FeatureDataStore2::MODIFY_FEATURESET_UPDATE |
                         FeatureDataStore2::MODIFY_FEATURESET_DELETE |
                         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_INSERT |
                         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_UPDATE |
                         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_DELETE |
                         FeatureDataStore2::MODIFY_FEATURESET_NAME |
                         FeatureDataStore2::MODIFY_FEATURESET_DISPLAY_THRESHOLDS |
                         FeatureDataStore2::MODIFY_FEATURE_NAME |
                         FeatureDataStore2::MODIFY_FEATURE_GEOMETRY |
                         FeatureDataStore2::MODIFY_FEATURE_STYLE |
                         FeatureDataStore2::MODIFY_FEATURE_ATTRIBUTES,
                         // visibility flags
                         FeatureDataStore2::VISIBILITY_SETTINGS_FEATURE |
                         FeatureDataStore2::VISIBILITY_SETTINGS_FEATURESET)
{ }

RuntimeFeatureDataStore2::RuntimeFeatureDataStore2(int modificationFlags, int visibilityFlags) NOTHROWS :
AbstractFeatureDataStore2(modificationFlags, visibilityFlags),
featureSpatialIndex(::featureRecordQuadtreeFunction<FeatureRecord>, -180.0, -90.0, 180.0, 90.0),
nextFeatureSetId(1),
nextFeatureId(1),
inBulkModify(false),
visibleGeneration(1)
{ }

RuntimeFeatureDataStore2::~RuntimeFeatureDataStore2() NOTHROWS
{ }

Util::TAKErr RuntimeFeatureDataStore2::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS {
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    //XXX-- avoid full copy of details?
    const FeatureRecord *record = featureIt->second.get();
    feature = FeaturePtr_const(new Feature2(record->id, record->setId, record->name, *record->geom, record->altitudeMode, record->extrude,
                                            *record->style, *record->attrs, record->version),
                               ::deleteDeleterFunc<Feature2>);
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS {
    
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::vector<std::shared_ptr<FeatureRecord>> items;
    items.reserve(this->featureIdIndex.size());
    for (auto &pair : this->featureIdIndex) {
        items.push_back(pair.second);
    }
    
    cursor = FeatureCursorPtr(new ::RuntimeFeatureCursor2<FeatureRecord>(std::move(items)),
                              ::castDeleteDeleterFunc<FeatureCursor2, ::RuntimeFeatureCursor2<FeatureRecord>>);
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS {

    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    std::vector<std::shared_ptr<FeatureRecord>> items;
    int offset = params.offset;
    size_t limit = params.limit ? params.limit : SIZE_MAX;
    
    if (params.spatialFilter) {
        //XXX-- for now use the envelope
        atakmap::feature::Envelope env = params.spatialFilter->getEnvelope();
        
        this->featureSpatialIndex.get(env.minX, env.minY, env.maxX, env.maxY,
                                      featureSpatialIndexVisitor, &items);
    }
    
    if (params.featureIds && params.featureIds->size()) {
        Port::Collections_forEach(*params.featureIds, [&](int64_t featureId) {
            auto featureIt = this->featureIdIndex.find(featureId);
            if (featureIt != this->featureIdIndex.end() &&
                ::collectionFilter(params.featureSetIds, featureIt->second->setId) &&
                ::secondaryParamsFilter(*featureIt->second, this->featureSetIdIndex, params)) {
                
                if (offset) {
                    --offset;
                } else {
                    items.push_back(featureIt->second);
                    if (items.size() == limit)
                        return Util::TE_Done;
                }
            }
            return Util::TE_Ok;
        });
    } else if (params.featureSetIds && params.featureSetIds->size()) {
        Port::Collections_forEach(*params.featureSetIds, [&](int64_t featureSetId) {
            auto featureSetIt = this->featureSetIdIndex.find(featureSetId);
            if (featureSetIt != this->featureSetIdIndex.end()) {
                for (const FeatureRecord *rec : featureSetIt->second.features) {
                    if (::secondaryParamsFilter(*rec, this->featureSetIdIndex, params)) {
                        if (offset) {
                            --offset;
                        } else {
                            items.push_back(this->featureIdIndex[rec->id]);
                            if (items.size() == limit) {
                                return Util::TE_Done;
                            }
                        }
                    }
                }
            }
            return Util::TE_Ok;
        });
    } else {
        for (auto &pair : this->featureIdIndex) {
            if (::secondaryParamsFilter(*pair.second, this->featureSetIdIndex, params)) {
                if (offset) {
                    --offset;
                } else {
                    items.push_back(pair.second);
                    if (items.size() == limit) {
                        break;
                    }
                }
            }
        }
    }
    
    // sort by ID
    std::sort(items.begin(), items.end(), [](const std::shared_ptr<FeatureRecord> &a, const std::shared_ptr<FeatureRecord> &b) {
        return a->id < b->id;
    });
    
    cursor = FeatureCursorPtr(new ::RuntimeFeatureCursor2<FeatureRecord>(std::move(items)),
                              ::castDeleteDeleterFunc<FeatureCursor2, ::RuntimeFeatureCursor2<FeatureRecord>>);
    
    return Util::TE_Ok;
}

void RuntimeFeatureDataStore2::featureSpatialIndexVisitor(std::shared_ptr<TAK::Engine::Feature::RuntimeFeatureDataStore2::FeatureRecord> *record, void *opaque) {
    //auto *items = static_cast<std::vector<std::shared_ptr<FeatureRecord>> *>(opaque);    
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeaturesCount(int *value) NOTHROWS {
    FeatureQueryParameters params;
    return this->queryFeaturesCount(value, params);
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS {
    return AbstractFeatureDataStore2::queryFeaturesCount(value, *this, params);
}

Util::TAKErr RuntimeFeatureDataStore2::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS {
    
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    auto setIt = this->featureSetIdIndex.find(featureSetId);
    if (setIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    const FeatureSet2 *set = setIt->second.current.get();
    featureSet = FeatureSetPtr_const(new FeatureSet2(*set), ::deleteDeleterFunc<FeatureSet2>);
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS {
    
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    std::vector<std::shared_ptr<FeatureSet2>> items;
    items.reserve(this->featureSetIdIndex.size());
    for (auto &pair : this->featureSetIdIndex) {
        items.push_back(pair.second.current);
    }
    
    cursor = FeatureSetCursorPtr(new ::RuntimeFeatureSetCursor2<FeatureSet2>(std::move(items)),
                                 ::castDeleteDeleterFunc<FeatureSetCursor2, ::RuntimeFeatureSetCursor2<FeatureSet2>>);
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS {

    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    std::vector<std::shared_ptr<FeatureSet2>> items;
    size_t offset = params.offset;
    const size_t limit = params.limit ? params.limit : SIZE_MAX;
    
    if (params.ids && params.ids->size()) {
        Port::Collections_forEach(*params.ids, [&](int64_t fsid) {
            auto fsIt = this->featureSetIdIndex.find(fsid);
            if (fsIt != this->featureSetIdIndex.end() &&
                ::featureSetParamsFilter(fsIt->second, params)) {
                
                if (offset) {
                    --offset;
                } else {
                    items.push_back(fsIt->second.current);
                    if (items.size() == limit)
                        return TE_Done;
                }
            }
            return Util::TE_Ok;
        });
    } else if (params.names && params.names->size()) {
        Port::Collections_forEach(*params.names, [&](Port::String &name) {
            auto fsIt = this->featureSetNameIndex.find(name.get());
            if (fsIt != this->featureSetNameIndex.end()) {
                auto listIt = fsIt->second.begin();
                while (listIt != fsIt->second.end()) {
                    if (::featureSetParamsFilter(**listIt, params)) {
                        if (offset) {
                            --offset;
                        } else {
                            items.push_back((*listIt)->current);
                            if (items.size() == limit)
                                return TE_Done;
                        }
                    }
                    ++listIt;
                }
            }
            return TE_Ok;
        });
    }else {
        items.reserve(this->featureSetIdIndex.size());
        for (auto &pair : this->featureSetIdIndex) {
            if (::featureSetParamsFilter(pair.second, params)) {
                if (offset) {
                    --offset;
                } else {
                    items.push_back(pair.second.current);
                    if (items.size() == limit)
                        break;
                }
            }
        }
    }
    
    cursor = FeatureSetCursorPtr(new ::RuntimeFeatureSetCursor2<FeatureSet2>(std::move(items)),
                                 ::castDeleteDeleterFunc<FeatureSetCursor2, ::RuntimeFeatureSetCursor2<FeatureSet2>>);
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatureSetsCount(int *value) NOTHROWS {
    
    
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS {
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS {
    
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end())
        return Util::TE_BadIndex;
    
    auto featureSetIt = this->featureSetIdIndex.find(featureIt->second->setId);
    
    if (value) {
        *value = featureIt->second->visible;
        if (featureSetIt->second.visibleGeneration > featureIt->second->visibleGeneration) {
            *value = featureSetIt->second.visible;
        }
    }
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS {
    
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    auto featureSetIt = this->featureSetIdIndex.find(setId);
    if (featureSetIt == this->featureSetIdIndex.end())
        return Util::TE_BadIndex;
    
    if (value)
        *value = featureSetIt->second.visible;
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS 
{ 
    return Util::TE_Ok; 
}

TAKErr RuntimeFeatureDataStore2::setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters& params, const bool readOnly) NOTHROWS
{
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::isFeatureSetReadOnly(bool* value, const int64_t fsid) NOTHROWS 
{
    if (value) {
        *value = false;
    }
    return Util::TE_Ok; 
}

Util::TAKErr RuntimeFeatureDataStore2::isFeatureReadOnly(bool* value, const int64_t fid) NOTHROWS 
{
    if (value) {
        *value = false;
    }
    return Util::TE_Ok; 
}

Util::TAKErr RuntimeFeatureDataStore2::isAvailable(bool *value) NOTHROWS {
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::refresh() NOTHROWS {
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::getUri(Port::String &value) NOTHROWS {
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::close() NOTHROWS {
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::beginBulkModificationImpl() NOTHROWS {
    this->inBulkModify = true;
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::endBulkModificationImpl(const bool successful) NOTHROWS {
    // XXX - rollback on unsuccessful
    
    this->inBulkModify = false;
    
    if (successful)
        this->dispatchDataStoreContentChangedNoSync(true);
    
    return Util::TE_Ok;
}

TAKErr RuntimeFeatureDataStore2::insertFeatureSet(FeatureSetPtr_const *inserted, const FeatureSet2 &featureSet) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_INSERT);
    TE_CHECKRETURN_CODE(code);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->insertFeatureSetImpl(inserted, featureSet);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

Util::TAKErr RuntimeFeatureDataStore2::insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type,
                                                            const char *name, const double minResolution, const double maxResolution) NOTHROWS {

    return this->insertFeatureSetImpl(featureSet, FeatureSet2(FEATURESET_ID_NONE, provider, type, name, minResolution, maxResolution, FEATURESET_VERSION_NONE));
}

Util::TAKErr RuntimeFeatureDataStore2::insertFeatureSetImpl(FeatureSetPtr_const *inserted, const FeatureSet2 &fs2) NOTHROWS {
    int64_t fsid = fs2.getId();
    if (fsid == FEATURESET_ID_NONE) {
        fsid = this->nextFeatureSetId;
        do {
            std::unordered_map<int64_t, FeatureSetRecord>::iterator entry;
            entry = this->featureSetIdIndex.find(++this->nextFeatureSetId);
            if (entry == this->featureSetIdIndex.end())
                break;
        } while (true);
    } else if (fsid == this->nextFeatureSetId) {
        // bump next free ID on collision
        do {
            std::unordered_map<int64_t, FeatureSetRecord>::iterator entry;
            entry = this->featureSetIdIndex.find(++this->nextFeatureSetId);
            if (entry == this->featureSetIdIndex.end())
                break;
        } while (true);
    } else {
        // detect collision with existing FID
        std::unordered_map<int64_t, FeatureSetRecord>::iterator entry;
        entry = this->featureSetIdIndex.find(fsid);
        if (entry != this->featureSetIdIndex.end())
            return TE_InvalidArg;
    }

    auto idIt = this->featureSetIdIndex.emplace(fsid,
                                                FeatureSetRecord(std::make_shared<FeatureSet2>(fsid,
                                                                                               fs2.getProvider(),
                                                                                               fs2.getType(),
                                                                                               fs2.getName(),
                                                                                               fs2.getMinResolution(),
                                                                                               fs2.getMaxResolution(),
                                                                                               (fs2.getVersion() != FEATURESET_VERSION_NONE) ? fs2.getVersion() : 1))).first;
    
    ::insertNameindex(this->featureSetNameIndex, &idIt->second);
    
    if (inserted)
        *inserted = FeatureSetPtr_const(new FeatureSet2(*idIt->second.current), ::deleteDeleterFunc<FeatureSet2>);
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS {
    
    auto idIt = this->featureSetIdIndex.find(fsid);
    if (idIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureSet2> old = idIt->second.current;
    
    idIt->second.current = std::make_shared<FeatureSet2>(fsid, old->getProvider(), old->getType(), name, old->getMinResolution(), old->getMaxResolution(), old->getVersion() + 1);
    
    auto nameIt = this->featureSetNameIndex.find(old->getName());
    auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), &idIt->second);
    if (strcmp(old->getName(), idIt->second.current->getName()) != 0) {
        nameIt->second.erase(listIt);
        ::insertNameindex(this->featureSetNameIndex, &idIt->second);
    }
    
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid,
                                                            const double minResolution, const double maxResolution) NOTHROWS {
    auto idIt = this->featureSetIdIndex.find(fsid);
    if (idIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureSet2> old = idIt->second.current;
    
    idIt->second.current = std::make_shared<FeatureSet2>(fsid, old->getProvider(), old->getType(), old->getName(), minResolution, maxResolution, old->getVersion() + 1);
    
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS {

    auto idIt = this->featureSetIdIndex.find(fsid);
    if (idIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureSet2> old = idIt->second.current;
    
    idIt->second.current = std::make_shared<FeatureSet2>(fsid, old->getProvider(), old->getType(), name, minResolution, maxResolution, old->getVersion() + 1);
    
    auto nameIt = this->featureSetNameIndex.find(old->getName());
    auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), &idIt->second);
    if (strcmp(old->getName(), idIt->second.current->getName()) != 0) {
        nameIt->second.erase(listIt);
        ::insertNameindex(this->featureSetNameIndex, &idIt->second);
    }
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS {
    
    Util::TAKErr code = this->deleteAllFeaturesImpl(fsid);
    TE_CHECKRETURN_CODE(code);
    
    auto idIt = this->featureSetIdIndex.find(fsid);
    
    const FeatureSetRecord &old = idIt->second;
    
    auto nameIt = this->featureSetNameIndex.find(old.getName());
    auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), &old);
    nameIt->second.erase(listIt);
    if (nameIt->second.size() == 0) {
        this->featureSetNameIndex.erase(nameIt);
    }
    
    this->featureSetIdIndex.erase(idIt);
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::deleteAllFeatureSetsImpl() NOTHROWS {
    this->featureNameIndex.clear();
    this->featureSetNameIndex.clear();
    this->featureIdIndex.clear();
    this->featureSetIdIndex.clear();
    this->featureSpatialIndex.clear();
    
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::insertFeature(FeaturePtr_const *inserted, const Feature2 &feature) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);
    TE_CHECKRETURN_CODE(code);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->insertFeatureImpl(inserted, feature);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

Util::TAKErr RuntimeFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name,
                                                         const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode,
                                                         const double extrude, const atakmap::feature::Style *style,
                                                         const atakmap::util::AttributeSet &attributes) NOTHROWS {
    return this->insertFeatureImpl(
        feature,
        Feature2(FEATURE_ID_NONE, fsid, name, std::move(GeometryPtr_const(&geom, Memory_leaker_const<atakmap::feature::Geometry>)),
                 altitudeMode, extrude, std::move(StylePtr_const(style, Memory_leaker_const<atakmap::feature::Style>)),
                 std::move(AttributeSetPtr_const(&attributes, Memory_leaker_const<atakmap::util::AttributeSet>)), FEATURE_VERSION_NONE));
}

Util::TAKErr RuntimeFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *inserted, const Feature2 &feature) NOTHROWS {
    
    auto setIdIt = this->featureSetIdIndex.find(feature.getFeatureSetId());
    if (setIdIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    int64_t fid = feature.getId();
    if (fid == FEATURE_ID_NONE) {
        fid = this->nextFeatureId;
        do {
            std::unordered_map<int64_t, std::shared_ptr<FeatureRecord>>::iterator entry;
            entry = this->featureIdIndex.find(++this->nextFeatureId);
            if (entry == this->featureIdIndex.end())
                break;
        } while (true);
    } else if (fid == this->nextFeatureId) {
        // bump next free ID on collision
        do {
            std::unordered_map<int64_t, std::shared_ptr<FeatureRecord>>::iterator entry;
            entry = this->featureIdIndex.find(++this->nextFeatureId);
            if (entry == this->featureIdIndex.end())
                break;
        } while (true);
    } else {
        // detect collision with existing FID
        std::unordered_map<int64_t, std::shared_ptr<FeatureRecord>>::iterator entry;
        entry = this->featureIdIndex.find(fid);
        if (entry != this->featureIdIndex.end())
            return TE_InvalidArg;
    }

    auto featureIdIt =
        this->featureIdIndex
            .emplace(fid, std::make_shared<FeatureRecord>(
                              fid, feature.getFeatureSetId(), feature.getName(),
                              std::shared_ptr<atakmap::feature::Style>(feature.getStyle() ? feature.getStyle()->clone() : nullptr),
                              std::shared_ptr<atakmap::feature::Geometry>(feature.getGeometry()->clone()), feature.getAltitudeMode(),
                              feature.getExtrude(), std::make_shared<atakmap::util::AttributeSet>(*feature.getAttributes()),
                              (feature.getVersion() != FEATURE_VERSION_NONE) ? feature.getVersion() : 1))
            .first;
    
    setIdIt->second.features.push_back(featureIdIt->second.get());
    ::insertNameindex(this->featureNameIndex, featureIdIt->second);
    this->featureSpatialIndex.add(&featureIdIt->second);
    
    if (inserted) {
        return this->getFeature(*inserted, fid);
    }
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(fid, old->setId, name, std::move(old->style), std::move(old->geom), old->altitudeMode, old->extrude,
                                                        std::move(old->attrs), old->version + 1);
    
    ::updateNameindex2(this->featureNameIndex, featureIt->second, old->getName());
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());
    
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    this->featureSpatialIndex.remove(&featureIt->second);
    
    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(fid, old->setId, old->getName(), std::move(old->style),
                                                        std::shared_ptr<atakmap::feature::Geometry>(geom.clone()), old->altitudeMode,
                                                        old->extrude, std::move(old->attrs), old->version + 1);
    
    this->featureSpatialIndex.add(&featureIt->second);
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }

    this->featureSpatialIndex.remove(&featureIt->second);

    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(fid, old->setId, old->name, std::move(old->style), std::move(old->geom), altitudeMode, extrude,
                                                        std::move(old->attrs), old->version + 1);

    this->featureSpatialIndex.add(&featureIt->second);
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());

    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(fid, old->setId, old->getName(),
                                                        std::shared_ptr<atakmap::feature::Style>(style ? style->clone() : nullptr),
                                                        std::move(old->geom), old->altitudeMode, old->extrude, std::move(old->attrs), old->version + 1);
    
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(
        fid, old->setId, old->getName(), std::move(old->style), std::move(old->geom), old->altitudeMode, old->extrude,
        std::shared_ptr<atakmap::util::AttributeSet>(new atakmap::util::AttributeSet(attributes)), old->version + 1);
    
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());
    
    this->setContentChanged();

    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }

    this->featureSpatialIndex.remove(&featureIt->second);
    
    std::shared_ptr<FeatureRecord> old = featureIt->second;
    featureIt->second = std::make_shared<FeatureRecord>(
        fid, old->setId, name, std::shared_ptr<atakmap::feature::Style>(style->clone()),
        std::shared_ptr<atakmap::feature::Geometry>(geom.clone()), old->altitudeMode, old->extrude,
        std::shared_ptr<atakmap::util::AttributeSet>(new atakmap::util::AttributeSet(attributes)), old->version + 1);
    
    this->featureSpatialIndex.add(&featureIt->second);
    ::updateNameindex2(this->featureNameIndex, featureIt->second, old->getName());
    ::updateFeatureSetList(this->featureSetIdIndex, old.get(), featureIt->second.get());

    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::deleteFeatureImpl(const int64_t fid) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    std::shared_ptr<FeatureRecord> item = featureIt->second;
    ::removeFromFeatureSetList(this->featureSetIdIndex, item.get());
    this->featureSpatialIndex.remove(&featureIt->second);
    ::removeNameindex(this->featureNameIndex, featureIt->second);
    this->featureIdIndex.erase(featureIt);
    
    this->setContentChanged();
    
    return Util::TE_Unsupported;
}

Util::TAKErr RuntimeFeatureDataStore2::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS {
    
    auto idIt = this->featureSetIdIndex.find(fsid);
    if (idIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    for (const FeatureRecord *feature : idIt->second.features) {
        auto featureIdIt = this->featureIdIndex.find(feature->id);
        this->featureSpatialIndex.remove(&featureIdIt->second);
        ::removeNameindex(this->featureNameIndex, featureIdIt->second);
        this->featureIdIndex.erase(featureIdIt);
    }
    
    idIt->second.features.clear();
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS {
    
    auto featureIt = this->featureIdIndex.find(fid);
    if (featureIt == this->featureIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    featureIt->second->visible = visible;
    featureIt->second->visibleGeneration = this->visibleGeneration++;
    
    this->setContentChanged();
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS {
    
    int64_t vg = this->visibleGeneration++;
    
    FeatureCursorPtr cursor(nullptr, nullptr);
    Util::TAKErr code = this->queryFeatures(cursor, params);
    TE_CHECKRETURN_CODE(code);
    
    while ((code = cursor->moveToNext()) == Util::TE_Ok) {
        int64_t value = 0;
        code = cursor->getId(&value);
        TE_CHECKBREAK_CODE(code);
        auto featureIt = this->featureIdIndex.find(value);
        featureIt->second->visibleGeneration = vg;
        featureIt->second->visible = visible;
    }
    
    return code == Util::TE_Done ? Util::TE_Ok : code;
}

Util::TAKErr RuntimeFeatureDataStore2::setFeatureSetVisibleImpl(const int64_t setId, const bool visible) NOTHROWS {
    
    auto featureSetIt = this->featureSetIdIndex.find(setId);
    if (featureSetIt == this->featureSetIdIndex.end()) {
        return Util::TE_BadIndex;
    }
    
    featureSetIt->second.visibleGeneration = this->visibleGeneration++;
    featureSetIt->second.visible = visible;
    
    return Util::TE_Ok;
}

Util::TAKErr RuntimeFeatureDataStore2::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS {
    
    int64_t vg = this->visibleGeneration++;
    
    FeatureSetCursorPtr cursor(nullptr, nullptr);
    Util::TAKErr code = this->queryFeatureSets(cursor, params);
    TE_CHECKRETURN_CODE(code);
    
    while ((code = cursor->moveToNext()) == Util::TE_Ok) {
        const FeatureSet2 *value = nullptr;
        code = cursor->get(&value);
        TE_CHECKBREAK_CODE(code);
        auto featureSetIt = this->featureSetIdIndex.find(value->getId());
        featureSetIt->second.visibleGeneration = vg;
        featureSetIt->second.visible = visible;
    }
    
    return code == Util::TE_Done ? Util::TE_Ok : code;
}

namespace {    
    template <typename MapType, typename ItemType>
    void insertNameindex(MapType &index, ItemType *item) {
        auto nameIt = index.find(item->getName());
        if (nameIt == index.end()) {
            nameIt = index.emplace(Port::String(item->getName()), std::list<ItemType *>()).first;
        }
        nameIt->second.push_back(item);
    }
    
    template <typename MapType, typename ItemType>
    void insertNameindex(MapType &index, std::shared_ptr<ItemType> &item) {
        auto nameIt = index.find(item->getName());
        if (nameIt == index.end()) {
            nameIt = index.emplace(Port::String(item->getName()), std::list<std::shared_ptr<ItemType> *>()).first;
        }
        nameIt->second.push_back(&item);
    }
    
    
    template <typename MapType, typename ItemType>
    void removeNameindex(MapType &index, ItemType *item) {
        auto nameIt = index.find(item->getName());
        auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), item);
        nameIt->second.erase(listIt);
        if (nameIt->second.size() == 0) {
            index.erase(nameIt);
        }
    }
    
    template <typename MapType, typename ItemType>
    void removeNameindex(MapType &index, std::shared_ptr<ItemType> &item) {
        auto nameIt = index.find(item->getName());
        auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), &item);
        nameIt->second.erase(listIt);
        if (nameIt->second.size() == 0) {
            index.erase(nameIt);
        }
    }
    
    
    template <typename MapType, typename ItemType>
    void updateNameindex(MapType &index, ItemType *oldItem, ItemType *newItem) {
        auto nameIt = index.find(oldItem->getName());
        auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), oldItem);
        if (strcmp(oldItem->getName(), newItem->getName()) == 0) {
            *listIt = newItem;
        } else {
            nameIt->second.erase(listIt);
            ::insertNameindex(index, newItem);
        }
    }
    
    template <typename MapType, typename ItemType>
    void updateNameindex2(MapType &index, std::shared_ptr<ItemType> &item, const char *oldName) {
        auto nameIt = index.find(oldName);
        auto listIt = std::find(nameIt->second.begin(), nameIt->second.end(), &item);
        nameIt->second.erase(listIt);
        ::insertNameindex(index, item);
    }
    
    template <typename MapType, typename ItemType>
    typename MapType::iterator removeFromFeatureSetList(MapType &index, const ItemType *item) {
        auto setIt = index.find(item->setId);
        auto listIt = std::find(setIt->second.features.begin(), setIt->second.features.end(), item);
        setIt->second.features.erase(listIt);
        return setIt;
    }
    
    template <typename MapType, typename ItemType>
    void updateFeatureSetList(MapType &index, const ItemType *oldItem, const ItemType *newItem) {
        auto setIt = removeFromFeatureSetList(index, oldItem);
        setIt->second.features.push_back(newItem);
    }
    
    template <typename T>
    bool featureSetParamsFilter(const T &featureSet, const TAK::Engine::Feature::AbstractFeatureDataStore2::FeatureSetQueryParameters &params) {
        
        return (!params.visibleOnly || featureSet.visible) &&
               ::collectionFilter(params.names, featureSet.current->getName()) &&
               ::collectionFilter(params.providers, featureSet.current->getProvider()) &&
               ::collectionFilter(params.types, featureSet.current->getType());
    }
    
    template <typename T>
    RuntimeFeatureCursor2<T>::RuntimeFeatureCursor2(std::vector<std::shared_ptr<T>> &&items) :
    items(std::forward<std::vector<std::shared_ptr<T>>>(items)),
    cachedFeature2(nullptr),
    beforeBegins(true)
    {
        pos = items.end();
    }
    
    template <typename T>
    RuntimeFeatureCursor2<T>::~RuntimeFeatureCursor2() NOTHROWS
    { }

    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureCursor2<T>::moveToNext() NOTHROWS {
        
        if (beforeBegins) {
            pos = items.begin();
            beforeBegins = false;
        } else {
            ++pos;
        }

        if (cachedFeature2) {
            cachedFeature2->~Feature2();
            cachedFeature2 = nullptr;
        }
        
        if (pos == items.end())
            return Util::TE_Done;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureCursor2<T>::getId(int64_t *value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value)
            *value = (*pos)->id;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureCursor2<T>::getFeatureSetId(int64_t *value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value)
            *value = (*pos)->setId;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureCursor2<T>::getVersion(int64_t *value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value)
            *value = (*pos)->version;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    Util::TAKErr RuntimeFeatureCursor2<T>::getRawGeometry(RawData *value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value) {
            value->text = nullptr;
            value->binary.value = nullptr;
            value->binary.len = 0;
            value->object = (*pos)->geom.get();
        }
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    FeatureCursor2::GeometryEncoding RuntimeFeatureCursor2<T>::getGeomCoding() NOTHROWS {
        return GeometryEncoding::GeomGeometry;
    }

    template <typename T>
    AltitudeMode RuntimeFeatureCursor2<T>::getAltitudeMode() NOTHROWS {
        if (pos == items.end()) return AltitudeMode::TEAM_ClampToGround;

        return (*pos)->altitudeMode;
    }

    template <typename T>
    double RuntimeFeatureCursor2<T>::getExtrude() NOTHROWS {
        if (pos == items.end()) return 0.0;

        return (*pos)->extrude;
    }
    
    template <typename T>
    Util::TAKErr RuntimeFeatureCursor2<T>::getName(const char **value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value)
            *value = (*pos)->name;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    FeatureCursor2::StyleEncoding RuntimeFeatureCursor2<T>::getStyleCoding() NOTHROWS {
        return StyleEncoding::StyleStyle;
    }
    
    template <typename T>
    Util::TAKErr RuntimeFeatureCursor2<T>::getRawStyle(RawData *value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value) {
            value->text = nullptr;
            value->binary.value = nullptr;
            value->binary.len = 0;
            value->object = (*pos)->style.get();
        }
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    Util::TAKErr RuntimeFeatureCursor2<T>::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;
        
        if (value)
            *value = (*pos)->attrs.get();
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    Util::TAKErr RuntimeFeatureCursor2<T>::get(const Feature2 **feature) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_BadIndex;

        if (!cachedFeature2) {
            const T *rec = (*pos).get();
            cachedFeature2 = ::new (cachedFeature2Bytes)
                Feature2(rec->id, rec->setId, rec->name, GeometryPtr(rec->geom.get(), ::noopDeleterFunc<atakmap::feature::Geometry>),
                         rec->altitudeMode, rec->extrude, StylePtr(rec->style.get(), ::noopDeleterFunc<atakmap::feature::Style>),
                         AttributeSetPtr(rec->attrs.get(), ::noopDeleterFunc<atakmap::util::AttributeSet>), rec->version);
        }
        
        if (feature)
            *feature = cachedFeature2;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    RuntimeFeatureSetCursor2<T>::RuntimeFeatureSetCursor2(std::vector<std::shared_ptr<T>> &&items) NOTHROWS
    : items(std::move(items)) {
        pos = this->items.begin();
    }
    
    template <typename T>
    RuntimeFeatureSetCursor2<T>::~RuntimeFeatureSetCursor2() NOTHROWS
    { }
    
    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureSetCursor2<T>::moveToNext() NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_Done;
        
        ++pos;
        
        return Util::TE_Ok;
    }
    
    template <typename T>
    TAK::Engine::Util::TAKErr RuntimeFeatureSetCursor2<T>::get(const FeatureSet2 **featureSet) NOTHROWS {
        
        if (pos == items.end())
            return Util::TE_Done;
        
        *featureSet = pos->get();
        
        return Util::TE_Ok;
    }

}
