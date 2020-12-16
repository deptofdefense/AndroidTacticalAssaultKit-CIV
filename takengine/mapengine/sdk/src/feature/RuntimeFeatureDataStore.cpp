
#include "core/GeoPoint.h"
//TODO--#include "db/FilteredCursor.h"
#include "feature/Feature.h"
//TODO--#include "feature/FeatureCursor.h"
#include "feature/FeatureSet.h"
#include "feature/RuntimeFeatureDataStore.h"
#include "math/Rectangle.h"
#include "math/Utils.h"
#include "util/Quadtree.h"
#include "util/Distance.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "feature/Style.h"
#include "feature/AbstractFeatureDataStore2.h"


using namespace atakmap;
using namespace atakmap::util;

typedef atakmap::feature::FeatureDataStore::FeatureQueryParameters::SpatialFilter FDS_SpatialFilter;
typedef atakmap::feature::FeatureDataStore::FeatureQueryParameters::RegionFilter FDS_RegionSpatialFilter;
typedef atakmap::feature::FeatureDataStore::FeatureQueryParameters::RadiusFilter FDS_RadiusSpatialFilter;

namespace
{
    //TODO--const char *wildcardAsRegex(const char *s);
    atakmap::feature::Envelope radiusAsRegion(const atakmap::core::GeoPoint &center, double radius);

    /**************************************************************************/
    // Record Filters

    template<class T>
    class RecordFilter {
    public:
        virtual ~RecordFilter();
        virtual bool accept(T record) = 0;
    };

    template<class T>
    class IdRecordFilter : public RecordFilter<T> {
    protected :
        IdRecordFilter(const std::vector<int64_t> &ids);
    public :
        virtual ~IdRecordFilter();
    public :
        virtual bool accept(T record);
    protected :
        virtual int64_t getId(T record) = 0;
    private :
        std::vector<int64_t> ids;
    };

    template<class T>
    class WildcardsRecordFilter : public RecordFilter <T>
    {
    protected :
        WildcardsRecordFilter(const std::vector<PGSC::String> &args, char wildcardChar, bool valueIsLowercase);
    public :
        virtual ~WildcardsRecordFilter();
    protected :
        virtual const char *getValue(T record) = 0;
    public :
        virtual bool accept(T record);
    private :
        const bool valueIsLowercase;
        std::vector<std::string> startsWith;
        std::vector<std::string> endsWith;
        std::vector<std::string> literals;
        std::vector<std::string> regex;
    };

    template<class T>
    class MultiRecordFilter : public RecordFilter<T> {
    public:
        MultiRecordFilter(const std::vector<RecordFilter<T> *> &filters);
        virtual ~MultiRecordFilter();
    public:
        virtual bool accept(T record) override;
    private :
        std::vector<RecordFilter<T> *> filters;
    };

    /**************************************************************************/
    // Feature Record Filters

    class FeatureIdRecordFilter : public IdRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *> {
    public :
        FeatureIdRecordFilter(const std::vector<int64_t> &ids);
        virtual ~FeatureIdRecordFilter();
    protected :
        virtual int64_t getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class FeatureNameRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *> {
    public :
        FeatureNameRecordFilter(const std::vector<PGSC::String> &args, char wildcardChar);
        ~FeatureNameRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class VisibleOnlyFeatureRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        VisibleOnlyFeatureRecordFilter();
        virtual ~VisibleOnlyFeatureRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record);
    };

    class RegionSpatialFeatureRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        RegionSpatialFeatureRecordFilter(FDS_RegionSpatialFilter *filter);
        virtual ~RegionSpatialFeatureRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record);
    private :
        atakmap::feature::Envelope roi;
    };

    class RadiusSpatialFeatureRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        RadiusSpatialFeatureRecordFilter(FDS_RadiusSpatialFilter *filter);
        virtual ~RadiusSpatialFeatureRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record);
    private :
        atakmap::feature::Envelope roi;
    };

    class ProviderFeatureRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        ProviderFeatureRecordFilter(const std::vector<PGSC::String> &providers, char wildcardChar);
        virtual ~ProviderFeatureRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class TypeFeatureRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        TypeFeatureRecordFilter(const std::vector<PGSC::String> &providers, char wildcardChar);
        virtual ~TypeFeatureRecordFilter();
    protected :
        const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class SetIdFeatureRecordFilter : public IdRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        SetIdFeatureRecordFilter(const std::vector<int64_t> &ids);
    protected :
        int64_t getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class SetNameFeatureRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        SetNameFeatureRecordFilter(const std::vector<PGSC::String> &featureSets, char wildcardChar);
        ~SetNameFeatureRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) override;
    };

    class ResolutionFeatureRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *>
    {
    public :
        ResolutionFeatureRecordFilter(double minResolution, double maxResolution);
        virtual ~ResolutionFeatureRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record);
    private :
        const double minResolution;
        const double maxResolution;
    };

    /**************************************************************************/
    // Feature Record Filters

    class FeatureSetIdRecordFilter : public IdRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *>
    {
    public :
        FeatureSetIdRecordFilter(const std::vector<int64_t> &ids);
        virtual ~FeatureSetIdRecordFilter();
    protected :
        int64_t getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) override;
    };

    class FeatureSetNameRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *>
    {
    public :
        FeatureSetNameRecordFilter(const std::vector<PGSC::String> &args, char wildcardChar);
        virtual ~FeatureSetNameRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) override;
    };

    class VisibleOnlyFeatureSetRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *> {
    public :
        VisibleOnlyFeatureSetRecordFilter();
        virtual ~VisibleOnlyFeatureSetRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record);
    };

    class ProviderFeatureSetRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *>
    {
    public :
        ProviderFeatureSetRecordFilter(const std::vector<PGSC::String> &providers, char wildcardChar);
        virtual ~ProviderFeatureSetRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) override;
    };

    class TypeFeatureSetRecordFilter : public WildcardsRecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *>
    {
    public :
        TypeFeatureSetRecordFilter(const std::vector<PGSC::String> &providers, char wildcardChar);
        ~TypeFeatureSetRecordFilter();
    protected :
        virtual const char *getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) override;
    };

    class ResolutionFeatureSetRecordFilter : public RecordFilter<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *>
    {
    public :
        ResolutionFeatureSetRecordFilter(double minResolution, double maxResolution);
        virtual ~ResolutionFeatureSetRecordFilter();
    public :
        virtual bool accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record);
    private :
        const double minResolution;
        const double maxResolution;
    };

    /**************************************************************************/
    //

    template<class T>
    class RecordCursor : public atakmap::db::Cursor {
    public:
        virtual ~RecordCursor();
        virtual T get() const = 0;
        virtual bool moveToNext() throw(CursorError) = 0;
        
        virtual Blob getBlob (std::size_t column) const throw (CursorError);
        virtual std::size_t getColumnCount() const;
        virtual std::size_t getColumnIndex (const char* columnName) const throw (CursorError);
        
        virtual
        const char*
        getColumnName (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        std::vector<const char*>
        getColumnNames ()
        const;
        
        virtual
        double
        getDouble (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        int
        getInt (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        int64_t
        getLong (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        const char*
        getString (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        FieldType
        getType (std::size_t column)
        const
        throw (CursorError);
        
        virtual
        bool
        isNull (std::size_t column)
        const
        throw (CursorError);
    };

    class FeatureCursorImpl : public atakmap::feature::FeatureDataStore::FeatureCursor {
    public :
        FeatureCursorImpl(RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *> *impl);
        virtual ~FeatureCursorImpl();
    public :
        virtual atakmap::feature::Feature *get() const throw (CursorError) override;
    };

    class FeatureSetCursorImpl : public atakmap::feature::FeatureDataStore::FeatureSetCursor {
    public :
        FeatureSetCursorImpl(RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *> *impl);
        virtual ~FeatureSetCursorImpl();
    public :
        virtual atakmap::feature::FeatureSet *get() const throw (CursorError) override;
    };

    template<class Container>
    class RecordIteratorCursor : public RecordCursor<typename Container::value_type> {
    public :
        typedef typename Container::value_type value_type;
        typedef typename Container::iterator iterator;
        
        RecordIteratorCursor() : cur(NULL) { }
        virtual ~RecordIteratorCursor();

        void reset(Container &swapContainer);
        
    public :
        virtual value_type get() const override;
    public : // Cursor
        virtual void close();
        virtual bool isClosed();
        virtual bool moveToNext() throw (atakmap::db::Cursor::CursorError) override;
    private:
        value_type cur;
        Container container;
        iterator pos;
    };

    template<typename T>
    class FilteredRecordIteratorCursor: public atakmap::db::FilteredCursor,
                                        public RecordCursor<T>
    {
    public:
        FilteredRecordIteratorCursor(RecordCursor<T> *cursor,
                                     RecordFilter<T> *filter);
        
        virtual ~FilteredRecordIteratorCursor() throw();
    public: // RecordCursor
        virtual bool accept() const override;
        virtual T get() const override;
        virtual bool moveToNext() throw (atakmap::db::Cursor::CursorError) override;
    private:
        PGSC::RefCountableIndirectPtr<RecordFilter<T>> recordFilter;
    };

#ifdef MSVC
    inline int strcasecmp(const char* lhs, const char* rhs)
    {
        return PGSC::strcasecmp(lhs, rhs);
    }
#endif

    static int nextOid = 1;
    TAK::Engine::Thread::Mutex oidMutex;

}

using namespace atakmap::feature;
using namespace atakmap::core;
using namespace atakmap::util;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

RuntimeFeatureDataStore::RuntimeFeatureDataStore()
: FeatureDataStore(// modificationFlags
    FeatureDataStore::MODIFY_BULK_MODIFICATIONS |
    FeatureDataStore::MODIFY_FEATURESET_INSERT |
    FeatureDataStore::MODIFY_FEATURESET_UPDATE |
    FeatureDataStore::MODIFY_FEATURESET_DELETE |
    FeatureDataStore::MODIFY_FEATURESET_FEATURE_INSERT |
    FeatureDataStore::MODIFY_FEATURESET_FEATURE_UPDATE |
    FeatureDataStore::MODIFY_FEATURESET_FEATURE_DELETE |
    FeatureDataStore::MODIFY_FEATURESET_NAME |
    FeatureDataStore::MODIFY_FEATURESET_DISPLAY_THRESHOLDS |
    FeatureDataStore::MODIFY_FEATURE_NAME |
    FeatureDataStore::MODIFY_FEATURE_GEOMETRY |
    FeatureDataStore::MODIFY_FEATURE_STYLE |
    FeatureDataStore::MODIFY_FEATURE_ATTRIBUTES,
    // visibility flags
    FeatureDataStore::VISIBILITY_SETTINGS_FEATURE |
    FeatureDataStore::VISIBILITY_SETTINGS_FEATURESET) {
    init();
}

RuntimeFeatureDataStore::RuntimeFeatureDataStore(int mf, int vf)
: FeatureDataStore(mf, vf) {
    init();
}

void RuntimeFeatureDataStore::init() {
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, getMutex());
        if (code != TE_Ok)
            throw std::runtime_error("RuntimeFeatureDataStore::init: Failed to acquire lock");
        this->oid = nextOid++;
    }

    // XXX - bother using a node limit ???
    featureSpatialIndex = new Quadtree<RuntimeFeatureDataStore::FeatureRecord>(FeatureRecordQuadtreeFunction, -180.0, -90.0, 180.0, 90.0);

    inBulkModify = false;
    nextFeatureId = 1;
    nextFeatureSetId = 1;
}

RuntimeFeatureDataStore::~RuntimeFeatureDataStore() { }

Feature *RuntimeFeatureDataStore::getFeature(int64_t fid) {
    auto it = this->featureIdIndex.find(fid);
    if (it != this->featureIdIndex.end()) {
        return it->second.feature.get();
    }
    return NULL;
}

FeatureDataStore::FeatureCursor *RuntimeFeatureDataStore::queryFeatures(const FeatureDataStore::FeatureQueryParameters &params) const {
    
    // XXX - return collection based on sorting
    //retval = gcnew System::Collections::template::HashSet<FeatureRecord *>();
    std::vector<const FeatureRecord *> retval;
    
    const size_t totalFeatures = featureIdIndex.size();
    size_t recordsFeatureIds = totalFeatures;
    size_t recordsSet = totalFeatures + featureSetIdIndex.size();
    size_t recordsSpatial = totalFeatures;
    size_t recordsVisual = totalFeatures + featureSetIdIndex.size();
    size_t recordsGeometry = totalFeatures + this->featureGeometryTypeIndex.size();

    bool applyProviders = (params.providers.size());
    bool applyTypes = (params.types.size());
    bool applySetIds = (params.featureSetIDs.size());
    bool applySetNames = (params.featureSetNames.size());
    bool applyIds = (params.IDs.size());
    bool applyNames = (params.names.size());
    bool applySpatial = (params.spatialFilter.get());
    bool applyResolution = (!std::isnan(params.minResolution) || !std::isnan(params.maxResolution));
    bool applyVisible = params.visibleOnly;

    if (applyIds)
        recordsFeatureIds = params.IDs.size();

    if (applyProviders || applyTypes || applySetIds || applySetNames || applyResolution) {
        if (params.featureSetIDs.size() > 0)
            recordsSet = params.featureSetIDs.size();
        else
            recordsSet = this->featureSetIdIndex.size();

        for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
            const FeatureSetRecord *record = &it->second;
            if (applySetIds) {
                if (std::find(params.featureSetIDs.begin(), params.featureSetIDs.end(), record->fsid) == params.featureSetIDs.end())
                    continue;
            }
            if (applyTypes) {
                /*for (auto it = params.types.begin(); it != params.types.end(); ++it) {
                    bool matched = false;
                    TAK::Engine::Util::TAKErr code = TAK::Engine::Feature::AbstractFeatureDataStore2::matches(&matched, *it, record->featureSet->getName(), '%');
                    if (matched) {
                        
                    }
                }*/
                // XXX -
            }
            if (applyProviders) {
                // XXX - 
            }
            if (applySetNames) {
                // XXX - 
            }
            if (applyResolution) {
                // XXX - 
            }
//???            recordsSet += record->featureSet->;
        }
    }

    if (applySpatial) {
        FDS_RegionSpatialFilter * region;
        if ((region = dynamic_cast<FDS_RegionSpatialFilter *>(params.spatialFilter.get())) != nullptr) {
            recordsSpatial = this->featureSpatialIndex->size(region->upperLeft.longitude,
                region->lowerRight.latitude,
                region->lowerRight.longitude,
                region->upperLeft.latitude);
        }
    }
    if (applyVisible) {
        if (params.featureSetIDs.size())
            recordsVisual = params.featureSetIDs.size();
        else
            recordsVisual = this->featureSetIdIndex.size();
        
        for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
            const FeatureSetRecord *record = &it->second;
            if (params.featureSetIDs.size() > 0 && std::find(params.featureSetIDs.begin(), params.featureSetIDs.end(), record->fsid) == params.featureSetIDs.end())
                continue;
            if (record->visible && record->visibilityDeviations.size() == 0)
                recordsVisual += record->features.size();
            else if (record->visible && record->visibilityDeviations.size() > 0)
                recordsVisual += record->features.size() - record->visibilityDeviations.size();
            else if (!record->visible && record->visibilityDeviations.size() > 0)
                recordsVisual += record->visibilityDeviations.size();

        }
    }
    if (params.geometryTypes.size() > 0) {
        recordsGeometry += params.geometryTypes.size();
        for (auto it = params.geometryTypes.begin(); it != params.geometryTypes.end(); ++it) {
            auto fIt = this->featureGeometryTypeIndex.find(it->get()->getType());
            if (fIt != this->featureGeometryTypeIndex.end()) {
                recordsGeometry += fIt->second.size();
            }
        }
    }
    

    const size_t minRecords = math::min<size_t>(
        recordsFeatureIds,
        recordsSet,
        recordsSpatial,
        std::min(recordsVisual, recordsGeometry));

    // construct the result set containing the minimum number of
    // pre-filtered records using applicable indices
    if (recordsFeatureIds == minRecords) {
        if (params.IDs.size() == 0) {
            // brute force
            for (auto it = this->featureIdIndex.begin(); it != this->featureIdIndex.end(); ++it) {
                retval.push_back(&it->second);
            }
        }
        else {
            for (auto it = params.IDs.begin(); it != params.IDs.end(); ++it) {
                auto fIt = this->featureIdIndex.find(*it);
                if (fIt != this->featureIdIndex.end()) {
                    retval.push_back(&fIt->second);
                }
            }
        }
        applyIds = false;
    }
    else if (recordsSet == minRecords) {
        if (params.featureSetIDs.size() > 0) {
            const FeatureSetRecord *record;
            for (auto it = params.featureSetIDs.begin(); it != params.featureSetIDs.end(); ++it) {
                auto fIt = this->featureSetIdIndex.find(*it);
                if (fIt != this->featureSetIdIndex.end()) {
                    record = &fIt->second;
                    retval.insert(retval.end(), record->features.begin(), record->features.end());
                }
            }
            applySetIds = false;
        }
        else {
            // XXX - brute force
            for (auto it = this->featureIdIndex.begin(); it != this->featureIdIndex.end(); ++it) {
                retval.push_back(&it->second);
            }
        }
    }
    else if (recordsSpatial == minRecords) {
        if (FDS_RegionSpatialFilter *region = dynamic_cast<FDS_RegionSpatialFilter *>(params.spatialFilter.get())) {
            this->featureSpatialIndex->get(region->upperLeft.longitude,
                region->lowerRight.latitude,
                region->lowerRight.longitude,
                region->upperLeft.latitude,
                QuadtreePushBackVisitor<std::vector<FeatureRecord *>>::invoke, &retval);
            applySpatial = false;
        }
        else if (FDS_RadiusSpatialFilter *radius = dynamic_cast<FDS_RadiusSpatialFilter *>(params.spatialFilter.get())) {
            Envelope region = radiusAsRegion(radius->point, radius->radius);
            this->featureSpatialIndex->get(region.minX,
                region.minY,
                region.maxX,
                region.maxY,
                QuadtreePushBackVisitor<std::vector<FeatureRecord *>>::invoke, &retval);
            applySpatial = false;
        }
        else {
            // XXX - brute force
            for (auto it = this->featureIdIndex.begin(); it != this->featureIdIndex.end(); ++it) {
                retval.push_back(&it->second);
            }
        }
    }
    else if (recordsVisual == minRecords) {
        if (params.featureSetIDs.size() > 0) {
            for (auto it = params.featureSetIDs.begin(); it != params.featureSetIDs.end(); ++it) {
                auto fsIt = this->featureSetIdIndex.find(*it);
                if (fsIt != this->featureSetIdIndex.end()) {
                    continue;
                }
                
                const FeatureSetRecord *record = &fsIt->second;
                if (record->visible && record->visibilityDeviations.size() == 0) {
                    retval.insert(retval.end(), record->features.begin(), record->features.end());
                }
                else if (record->visible && record->visibilityDeviations.size() > 0) {
                    for (auto fIt = record->features.begin(); fIt != record->features.end(); ++fIt) {
                        if (std::find(record->visibilityDeviations.begin(), record->visibilityDeviations.end(), (*fIt)->fid) ==
                            record->visibilityDeviations.end()) {
                            retval.push_back(*fIt);
                        }
                    }
                }
                else if (!record->visible && record->visibilityDeviations.size() > 0) {
                    for (auto idIt = record->visibilityDeviations.begin(); idIt != record->visibilityDeviations.end(); ++idIt) {
                        retval.push_back(&this->featureIdIndex.find(*idIt)->second);
                    }
                }
            }
        }
        else {
            for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
                auto fsIt = this->featureSetIdIndex.find(it->first);
                if (fsIt != this->featureSetIdIndex.end()) {
                    continue;
                }
                
                const FeatureSetRecord *record = &fsIt->second;
                if (record->visible && record->visibilityDeviations.size() == 0) {
                    retval.insert(retval.end(), record->features.begin(), record->features.end());
                }
                else if (record->visible && record->visibilityDeviations.size() > 0) {
                    for (auto fIt = record->features.begin(); fIt != record->features.end(); ++fIt) {
                        if (std::find(record->visibilityDeviations.begin(), record->visibilityDeviations.end(), (*fIt)->fid) ==
                            record->visibilityDeviations.end()) {
                            retval.push_back(*fIt);
                        }
                    }
                }
                else if (!record->visible && record->visibilityDeviations.size() > 0) {
                    for (auto idIt = record->visibilityDeviations.begin(); idIt != record->visibilityDeviations.end(); ++idIt) {
                        retval.push_back(&this->featureIdIndex.find(*idIt)->second);
                    }
                }
            }
        }
        applyVisible = false;
    }
    else if (recordsGeometry == minRecords) {
        for (auto it = params.geometryTypes.begin(); it != params.geometryTypes.end(); ++it) {
            auto fIt = this->featureGeometryTypeIndex.find(it->get()->getType());
            if (fIt == this->featureGeometryTypeIndex.end()) {
                continue;
            }
            retval.insert(retval.end(), fIt->second.begin(), fIt->second.end());
        }
    }
    else {
        // brute force
        for (auto it = this->featureIdIndex.begin(); it != this->featureIdIndex.end(); ++it) {
            retval.push_back(&it->second);
        }
    }

    //System::Collections::template::List<RecordFilter<FeatureRecord *> *> *filters = gcnew System::Collections::template::List<RecordFilter<FeatureRecord *> *>();
    std::vector<RecordFilter<const FeatureRecord *> *> filters;
    
    if (applyProviders)
        filters.push_back(new ProviderFeatureRecordFilter(params.providers, '%'));
    if (applyTypes)
        filters.push_back(new TypeFeatureRecordFilter(params.types, '%'));
    if (applySetIds)
        filters.push_back(new SetIdFeatureRecordFilter(params.featureSetIDs));
    if (applySetNames)
        filters.push_back(new SetNameFeatureRecordFilter(params.featureSetNames, '%'));
    if (applyIds)
        filters.push_back(new FeatureIdRecordFilter(params.IDs));
    if (applyNames)
        filters.push_back(new FeatureNameRecordFilter(params.names, '%'));
    if (applySpatial) {
        if (FDS_RegionSpatialFilter *region = dynamic_cast<FDS_RegionSpatialFilter *>(params.spatialFilter.get()))
            filters.push_back(new RegionSpatialFeatureRecordFilter(region));
        if (FDS_RadiusSpatialFilter *radius = dynamic_cast<FDS_RadiusSpatialFilter *>(params.spatialFilter.get()))
            filters.push_back(new RadiusSpatialFeatureRecordFilter(radius));
    }
    if (applyResolution)
        filters.push_back(new ResolutionFeatureRecordFilter(params.minResolution, params.maxResolution));
    if (applyVisible)
        filters.push_back(new VisibleOnlyFeatureRecordFilter());

    /*Console::WriteLine("Query Features (" + retval->Count + ":" + this->featureIdIndex->Count + ")");

    Console::WriteLine("applyProviders=" + applyProviders);
    Console::WriteLine("applyTypes=" + applyTypes);
    Console::WriteLine("applySetIds=" + applySetIds);
    Console::WriteLine("applySetNames=" + applySetNames);
    Console::WriteLine("applyIds=" + applyIds);
    Console::WriteLine("applyNames=" + applyNames);
    Console::WriteLine("applySpatial=" + applySpatial);
    Console::WriteLine("applyResolution=" + applyResolution);
    Console::WriteLine("applyVisible=" + applyVisible);*/

    RecordCursor<const FeatureRecord *> *cursor = NULL;
    
    RecordIteratorCursor<std::vector<const FeatureRecord *>> *iterCursor = new RecordIteratorCursor<std::vector<const FeatureRecord *>>();
    iterCursor->reset(retval);
    cursor = iterCursor;
    
#if 0
    if (filters->Count == 1)
        cursor = gcnew FilteredRecordIteratorCursor<const FeatureRecord *>(cursor, filters[0]);
    else
#endif
    if (filters.size() >= 1)
        cursor = new FilteredRecordIteratorCursor<const FeatureRecord *>(cursor,
                                                                   new MultiRecordFilter<const FeatureRecord *>(filters));
    FeatureCursorImpl *result = new FeatureCursorImpl(cursor);
    
    // Skip to offset
    for (size_t c = params.resultOffset; c > 0; --c) {
        if (!result->moveToNext())
            break;
    }
    
    return result;
}


size_t RuntimeFeatureDataStore::queryFeaturesCount(const FeatureDataStore::FeatureQueryParameters &params) const {
    size_t retval = 0;
    std::unique_ptr<FeatureCursor> result(this->queryFeatures(params));
    while (result->moveToNext())
        retval++;
    return retval;
}

FeatureSet *RuntimeFeatureDataStore::getFeatureSet(int64_t featureSetId) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::getFeatureSet: Failed to acquire lock");
    
    auto it = this->featureSetIdIndex.find(featureSetId);
    if (it != this->featureSetIdIndex.end()) {
        return it->second.featureSet;
    }
    
    return NULL;
}

template <typename Container, typename T>
inline bool contains(const Container &c, const T &v) {
    return std::find(c.begin(), c.end(), v) != c.end();
}

FeatureDataStore::FeatureSetCursor *RuntimeFeatureDataStore::queryFeatureSets(const FeatureDataStore::FeatureSetQueryParameters &params) const {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::queryFeatureSets: Failed to acquire lock");
    
    std::vector<const FeatureSetRecord *> retval;

    const size_t totalSets = this->featureSetIdIndex.size();
    size_t recordsSetIds = totalSets;
    size_t recordsSetNames = totalSets;
    size_t recordsVisual = totalSets;

    bool applyProviders = (params.providers.size() > 0);
    bool applyTypes = (params.types.size() > 0);
    bool applyIds = (params.IDs.size() > 0);
    bool applyNames = (params.names.size() > 0);
    bool applyVisible = params.visibleOnly;

    if (applyIds)
        recordsSetIds = params.IDs.size();

    if (applyNames) {
        // XXX - use name index
    }
    if (applyVisible) {
        if (params.IDs.size() > 0) {
            recordsVisual = params.IDs.size();
        }
        else {
            recordsVisual = this->featureSetIdIndex.size();
        }
        
        for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
            const FeatureSetRecord *record = &it->second;
            if (params.IDs.size() > 0 && !contains(params.IDs, record->fsid)) {
                continue;
            }
            
            if (record->visible && record->visibilityDeviations.size() == 0) {
                recordsVisual += record->features.size();
            }
            else if (record->visible && record->visibilityDeviations.size() > 0) {
                recordsVisual += record->features.size() - record->visibilityDeviations.size();
            }
            else if (!record->visible && record->visibilityDeviations.size() > 0)
                recordsVisual += record->visibilityDeviations.size();
        }
    }


    const size_t minRecords = math::min<size_t>(
        recordsSetIds,
        recordsSetNames,
        recordsVisual);

    // construct the result set containing the minimum number of
    // pre-filtered records using applicable indices
    if (recordsSetIds == minRecords) {
        if (params.IDs.size() == 0) {
            // brute force
            for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
                retval.push_back(&it->second);
            }
        }
        else {
            for (auto it = params.IDs.begin(); it != params.IDs.end(); ++it) {
                int64_t fid = *it;
                auto fsIt = this->featureSetIdIndex.find(fid);
                if (fsIt != this->featureSetIdIndex.end()) {
                    retval.push_back(&fsIt->second);
                }
            }
        }
        applyIds = false;
    }
    else if (recordsSetNames == minRecords) {
        // XXX - brute force
        for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
            retval.push_back(&it->second);
        }
    }
    else if (recordsVisual == minRecords) {
        if (params.IDs.size() > 0) {
            for (auto it = params.IDs.begin(); it != params.IDs.end(); ++it) {
                int64_t fsid = *it;
                auto fsIt = this->featureSetIdIndex.find(fsid);
                if (fsIt == this->featureSetIdIndex.end() || !fsIt->second.visible) {
                    continue;
                }
                retval.push_back(&fsIt->second);
            }
        } else {
            for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
                retval.push_back(&it->second);
            }
        }
        applyVisible = false;
    }
    else {
        // XXX - brute force
        for (auto it = this->featureSetIdIndex.begin(); it != this->featureSetIdIndex.end(); ++it) {
            retval.push_back(&it->second);
        }
    }

    std::vector<RecordFilter<const FeatureSetRecord *> *> filters;
    
    if (applyProviders)
        filters.push_back(new ProviderFeatureSetRecordFilter(params.providers, '%'));
    if (applyTypes)
        filters.push_back(new TypeFeatureSetRecordFilter(params.types, '%'));
    if (applyIds)
        filters.push_back(new FeatureSetIdRecordFilter(params.IDs));
    if (applyNames)
        filters.push_back(new FeatureSetNameRecordFilter(params.names, '%'));
    if (applyVisible)
        filters.push_back(new VisibleOnlyFeatureSetRecordFilter());

    RecordIteratorCursor<std::vector<const FeatureSetRecord *>> *containerCursor = new RecordIteratorCursor<std::vector<const FeatureSetRecord *>>();
    containerCursor->reset(retval);
    RecordCursor<const FeatureSetRecord *> *cursor = containerCursor;
    if (filters.size() == 1)
        cursor = new FilteredRecordIteratorCursor<const FeatureSetRecord *>(cursor, filters[0]);
    else if (filters.size() > 1)
        cursor = new FilteredRecordIteratorCursor<const FeatureSetRecord *>(cursor, new MultiRecordFilter<const FeatureSetRecord *>(filters));
    return new FeatureSetCursorImpl(cursor);
}

size_t RuntimeFeatureDataStore::queryFeatureSetsCount(const FeatureDataStore::FeatureSetQueryParameters &params) const {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::queryFeatureSetsCount: Failed to acquire lock");
    std::unique_ptr<FeatureSetCursor> cursor(this->queryFeatureSets(params));
    int retval = 0;
    while (cursor->moveToNext())
        retval++;
    return retval;
}

bool RuntimeFeatureDataStore::isInBulkModification() const {
    return inBulkModify;
}

bool RuntimeFeatureDataStore::isFeatureVisible(int64_t featureID) const {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::isFeatureVisible: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(featureID);
    if (it == this->featureIdIndex.end()) {
        throw std::logic_error("No such feature Set");
    }
    
    FeatureSetRecord *set = it->second.set;
    auto vdIt = std::find(set->visibilityDeviations.begin(), set->visibilityDeviations.end(), featureID);
    if (vdIt != set->visibilityDeviations.end()) {
        return !it->second.set->visible;
    }
    
    return it->second.set->visible;
}

bool RuntimeFeatureDataStore::isFeatureSetVisible(int64_t setId) const {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::isFeatureSetVisible: Failed to acquire lock");
    
    auto it = this->featureSetIdIndex.find(setId);
    if (it == this->featureSetIdIndex.end()) {
        throw std::logic_error("No such Feature Set");
    }
    return it->second.visible || it->second.visibilityDeviations.size() > 0;
}

bool RuntimeFeatureDataStore::isAvailable() const {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::isAvailable: Failed to acquire lock");
    return (this->featureIdIndex.size() > 0);
}

void RuntimeFeatureDataStore::refresh() { }

const char *RuntimeFeatureDataStore::getURI() const {
    std::ostringstream ss;
    ss << "RuntimeFeatureDataStore::" << this->oid;
    uri = ss.str();
    return uri.c_str();
}

void RuntimeFeatureDataStore::dispose() {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::dispose: Failed to acquire lock");
    
    this->featureSpatialIndex->clear();
    this->featureIdIndex.clear();
    this->featureNameIndex.clear();
    this->featureGeometryTypeIndex.clear();
    this->featureIdToFeatureSetId.clear();
    this->featureSetIdIndex.clear();
    this->featureSetNameIndex.clear();
}

void RuntimeFeatureDataStore::setFeatureVisibleImpl(int64_t fid, bool visible) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::setFeatureVisibleImpl: Failed to acquire lock");
    
    int64_t fsid;
    auto it = this->featureIdToFeatureSetId.find(fid);
    if (it == this->featureIdToFeatureSetId.end()) {
        throw std::invalid_argument("fid");
    }
    
    fsid = it->second;
    auto fsIt = this->featureSetIdIndex.find(fsid);
    if (fsIt == this->featureSetIdIndex.end()) {
        throw std::runtime_error("internal inconsistency: fsid not found");
    }
    FeatureSetRecord *record = &fsIt->second;

    auto vdIt = std::find(record->visibilityDeviations.begin(), record->visibilityDeviations.end(), fid);
    if (record->visible == visible && vdIt != record->visibilityDeviations.end()) {
        // feature is no longer deviant
        record->visibilityDeviations.erase(vdIt);
    
    }
    else if (record->visible != visible) {
        // mark this feature as deviant
        record->visibilityDeviations.push_back(fid);

        // if all features' visibility deviate from the set, reset the set's
        // visibility
        if (record->visibilityDeviations.size() == record->features.size()) {
            record->visibilityDeviations.clear();
            record->visible = visible;
        }
    }
    // else: requested visibility matches visibility of set
    this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::setFeatureSetVisibleImpl(int64_t setId, bool visible) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::setFeatureSetVisibleImpl: Failed to acquire lock");
    auto it = this->featureSetIdIndex.find(setId);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("setId");
    }
    FeatureSetRecord *record = &it->second;
    record->visible = visible;
    record->visibilityDeviations.clear();
}

void RuntimeFeatureDataStore::beginBulkModificationImpl() {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::beginBulkModification: Failed to acquire lock");
    this->inBulkModify = true;
}

void RuntimeFeatureDataStore::endBulkModificationImpl(bool successful)
{
    // XXX - rollback on unsuccessful

    this->inBulkModify = false;

    if (successful)
        this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::dispatchDataStoreContentChangedNoSync()
{
    if (!this->inBulkModify) {
        this->FeatureDataStore::notifyContentListeners();
    }
}

FeatureSet *RuntimeFeatureDataStore::insertFeatureSetImpl(const char *provider, const char *type, const char *name, double minResolution, double maxResolution, bool returnRef) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::insertFeatureSetImpl: Failed to acquire lock");

    FeatureSet *retval = new FeatureSet(provider, type, name, minResolution, maxResolution);
    FeatureSetRecord record(this->nextFeatureSetId++, retval);
    this->adopt(*retval, record.fsid, record.version);

    FeatureSetIDIndexMap::iterator instanceIt = this->featureSetIdIndex.insert(std::make_pair(record.fsid, record)).first;
    
    //TODO-- to lower
    auto it = this->featureSetNameIndex.find(name);
    if (it == this->featureSetNameIndex.end()) {
        it = this->featureSetNameIndex.insert(std::pair<std::string, std::vector<FeatureSetRecord *>>(name, std::vector<FeatureSetRecord *>())).first;
    }
    
    it->second.push_back(&instanceIt->second);
    
    this->dispatchDataStoreContentChangedNoSync();

    if (returnRef)
        return retval;
    else
        return nullptr;
}

void RuntimeFeatureDataStore::updateFeatureSetImpl(int64_t fsid, const char *name) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureSetImpl: Failed to acquire lock");
    
    auto it = this->featureSetIdIndex.find(fsid);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("No such Feature Set");
    }
    
    FeatureSetRecord *record = &it->second;
    
    this->updateRecordNoSync(fsid,
        record,
        name,
        record->featureSet->getMinResolution(),
        record->featureSet->getMaxResolution());
}

void RuntimeFeatureDataStore::updateFeatureSetImpl(int64_t fsid, double minResolution, double maxResolution) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureSetImpl: Failed to acquire lock");
    
    auto it = this->featureSetIdIndex.find(fsid);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("No such Feature Set");
    }
    
    FeatureSetRecord *record = &it->second;

    this->updateRecordNoSync(fsid,
        record,
        record->featureSet->getName(),
        minResolution,
        maxResolution);
}

void RuntimeFeatureDataStore::updateFeatureSetImpl(int64_t fsid, const char *name, double minResolution, double maxResolution) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureSetImpl: Failed to acquire lock");
    
    auto it = this->featureSetIdIndex.find(fsid);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("No such Feature Set");
    }
    
    FeatureSetRecord *record = &it->second;
    
    this->updateRecordNoSync(fsid,
        record,
        name,
        minResolution,
        maxResolution);
}

void RuntimeFeatureDataStore::updateRecordNoSync(int64_t fsid, FeatureSetRecord *record, const char *name, double minResolution, double maxResolution) {
    const bool updateNameIndex = (strcasecmp(name, record->featureSet->getName()) != 0);

    // remove any invalid index entries
    if (updateNameIndex) {
        
        auto it = this->featureSetNameIndex.find(record->featureSet->getName());
        if (it->second.size() == 1) {
            this->featureSetNameIndex.erase(it);
        } else {
            auto subIt = std::find(it->second.begin(), it->second.end(), record);
            it->second.erase(subIt);
        }
    }

    // update the FeatureSet record
    record->featureSet = new FeatureSet(record->featureSet->getProvider(),
        record->featureSet->getType(),
        name,
        minResolution,
        maxResolution);
    record->version++;
    this->adopt(*record->featureSet, fsid, record->version);

    // update any invalid index entries
    if (updateNameIndex) {
        auto it = this->featureSetNameIndex.find(name);
        if (it == this->featureSetNameIndex.end()) {
            it = this->featureSetNameIndex.insert(std::pair<std::string, std::vector<FeatureSetRecord *>>(name, std::vector<FeatureSetRecord *>())).first;
        }
        it->second.push_back(record);
    }

    this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::deleteFeatureSetImpl(int64_t fsid) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::deleteFeatureSetImpl: Failed to acquire lock");
    
    // XXX - probably could do something more efficient
    const bool wasInBulk = this->inBulkModify;
    this->inBulkModify = true;
    this->deleteAllFeaturesImplNoSync(fsid);
    auto it = this->featureSetIdIndex.find(fsid);
    FeatureSetRecord *record = &it->second;
    this->featureSetIdIndex.erase(it);
    
    auto fslIt = this->featureSetNameIndex.find(record->featureSet->getName());
    auto fslsIt = std::find(fslIt->second.begin(), fslIt->second.end(), record);
    fslIt->second.erase(fslsIt);
    if (fslIt->second.size() == 0) {
        this->featureSetNameIndex.erase(fslIt);
    }
    this->inBulkModify = wasInBulk;
    this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::deleteAllFeatureSetsImpl() {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::deleteAllFeatureSetsImpl: Failed to acquire lock");
    
    this->featureSpatialIndex->clear();
    this->featureIdIndex.clear();
    this->featureNameIndex.clear();
    this->featureGeometryTypeIndex.clear();
    this->featureIdToFeatureSetId.clear();
    this->featureSetIdIndex.clear();
    this->featureSetNameIndex.clear();

    this->dispatchDataStoreContentChangedNoSync();
}

Feature *RuntimeFeatureDataStore::insertFeatureImpl(int64_t fsid, const char *name, Geometry *geom, Style *style, const AttributeSet &attributes, bool returnRef) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::insertFeatureImpl: Failed to acquire lock");
    
    FeatureSetRecord *set = nullptr;
    
    auto it = this->featureSetIdIndex.find(fsid);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("No such FeatureSet");
    }
    
    set = &it->second;
    
    Feature *retval = new Feature(name, geom->clone(), style->clone(), attributes);
    FeatureRecord record(set, this->nextFeatureId++, retval);
    this->adopt(*retval, fsid, record.fid, record.version);

    auto instanceIt = this->featureIdIndex.insert(std::make_pair(record.fid, record)).first;

    auto fsniIt = this->featureNameIndex.find(name);
    std::vector<FeatureRecord *> *featureList = nullptr;
    if (fsniIt != this->featureNameIndex.end()) {
        featureList = &fsniIt->second;
    } else {
        featureList = &this->featureNameIndex.insert(std::pair<std::string, std::vector<FeatureRecord *>>(name, std::vector<FeatureRecord *>())).first->second;
    }
    featureList->push_back(&instanceIt->second);

    auto fgtiIt = this->featureGeometryTypeIndex.find(record.feature->getGeometry().getType());
    featureList = NULL;
    if (fgtiIt != this->featureGeometryTypeIndex.end()) {
        featureList = &fgtiIt->second;
    } else {
        featureList = &this->featureGeometryTypeIndex.insert(std::pair<Geometry::Type, std::vector<FeatureRecord *>>(record.feature->getGeometry().getType(),
                                                                                                                     std::vector<FeatureRecord *>())).first->second;
    }
    featureList->push_back(&instanceIt->second);
    
    this->featureSpatialIndex->add(&instanceIt->second);
    this->featureIdToFeatureSetId[record.fid] = fsid;
    record.set->features.push_back(&instanceIt->second);

    this->dispatchDataStoreContentChangedNoSync();

    if (returnRef)
        return retval;
    else
        return nullptr;
}

void RuntimeFeatureDataStore::updateFeatureImpl(int64_t fid, const char *name) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureImpl: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature ID");
    }
    
    FeatureRecord *record = &it->second;
    
    this->updateRecordNoSync(fid,
        record,
        name,
        record->feature->getGeometry(),
        record->feature->getStyle(),
        record->feature->getAttributes());
}

void RuntimeFeatureDataStore::updateFeatureImpl(int64_t fid, const Geometry &geom) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureImpl: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature ID");
    }
    
    FeatureRecord *record = &it->second;
 
    {
    // hold onto the ref until after the update
    PGSC::RefCountablePtr<atakmap::feature::Feature> exitingFeatureRef = record->feature;
    
    this->updateRecordNoSync(fid,
        record,
        record->feature->getName(),
        geom,
        record->feature->getStyle(),
        record->feature->getAttributes());
    }
}

void RuntimeFeatureDataStore::updateFeatureImpl(int64_t fid, const Style &style) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureImpl: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature ID");
    }
    
    FeatureRecord *record = &it->second;
    
    this->updateRecordNoSync(fid,
        record,
        record->feature->getName(),
        record->feature->getGeometry(),
        &style,
        record->feature->getAttributes());
}

void RuntimeFeatureDataStore::updateFeatureImpl(int64_t fid, const util::AttributeSet &attributes) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureImpl: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature ID");
    }
    
    FeatureRecord *record = &it->second;

    this->updateRecordNoSync(fid,
        record,
        record->feature->getName(),
        record->feature->getGeometry(),
        record->feature->getStyle(),
        attributes);
}

void RuntimeFeatureDataStore::updateFeatureImpl(int64_t fid, const char *name, const Geometry &geom, const Style &style, const util::AttributeSet &attributes) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::updateFeatureImpl: Failed to acquire lock");
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature ID");
    }
    
    FeatureRecord *record = &it->second;
    
    this->updateRecordNoSync(fid, record, name, geom, &style, attributes);
}

void RuntimeFeatureDataStore::updateRecordNoSync(int64_t fid, FeatureRecord *record, const char *name, const Geometry &geom, const Style *style, const AttributeSet &attributes)  {
    
    const bool updateNameIndex = strcasecmp(name, record->feature->getName()) != 0;
    const bool updateGeometryTypeIndex = (geom.getType() != record->feature->getGeometry().getType());

    // remove any invalid index entries
    if (updateNameIndex) {
        
        auto it = this->featureNameIndex.find(record->feature->getName());
        if (it->second.size() == 1) {
            this->featureNameIndex.erase(it);
        } else {
            auto fIt = std::find(it->second.begin(), it->second.end(), record);
            it->second.erase(fIt);
        }
    }
    
    if (updateGeometryTypeIndex) {
        
        auto it = this->featureGeometryTypeIndex.find(record->feature->getGeometry().getType());
        if (it->second.size() == 1) {
            this->featureGeometryTypeIndex.erase(it);
        } else {
            auto fIt = std::find(it->second.begin(), it->second.end(), record);
            it->second.erase(fIt);
        }
    }
    
    record->feature = PGSC::RefCountablePtr<atakmap::feature::Feature>(new Feature(name, geom.clone(), style->clone(), attributes));
    record->version++;
    this->adopt(*record->feature, record->set->fsid, fid, record->version);

    // update indices

    // update any invalid index entries
    /*if (updateNameIndex) {
        System::Collections::template::ICollection<FeatureRecord *> *features = nullptr;
        if (!this->featureNameIndex->TryGetValue(name->ToLower(), features)) {
            features = gcnew System::Collections::template::HashSet<FeatureRecord *>();
            this->featureNameIndex[name->ToLower()] = features;
        }
        features->Add(record);
    }
    if (updateGeometryTypeIndex) {
        System::Collections::template::ICollection<FeatureRecord *> *features = nullptr;
        if (!this->featureGeometryTypeIndex->TryGetValue(geom->getGeomClass(), features)) {
            features = gcnew System::Collections::template::HashSet<FeatureRecord *>();
            this->featureGeometryTypeIndex[geom->getGeomClass()] = features;
        }
        features->Add(record);
    }*/
    this->featureSpatialIndex->refresh(record);

    this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::deleteFeatureImpl(int64_t fid) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::deleteFeatureImpl: Failed to acquire lock");
    
    this->deleteFeatureImplNoSync(fid, true);
}

void RuntimeFeatureDataStore::deleteFeatureImplNoSync(int64_t fid, bool removeFromSet) {
    FeatureRecord *record = nullptr;
    
    auto it = this->featureIdIndex.find(fid);
    if (it == this->featureIdIndex.end()) {
        throw std::invalid_argument("No such Feature");
    }
    
    record = &it->second;
    
    this->featureSpatialIndex->remove(record);
    
    auto fnIt = this->featureNameIndex.find(record->feature->getName());
    auto fnvIt = std::find(fnIt->second.begin(), fnIt->second.end(), record);
    fnIt->second.erase(fnvIt);
    if (fnIt->second.size() == 0) {
        this->featureNameIndex.erase(fnIt);
    }
    
    auto fgtIt = this->featureGeometryTypeIndex.find(record->feature->getGeometry().getType());
    auto fgtvIt = std::find(fgtIt->second.begin(), fgtIt->second.end(), record);
    fgtIt->second.erase(fgtvIt);
    if (fgtIt->second.size() == 0) {
        this->featureGeometryTypeIndex.erase(fgtIt);
    }
    
    this->featureIdToFeatureSetId.erase(fid);
    
    if (removeFromSet) {
        auto fIt = std::find(record->set->features.begin(), record->set->features.end(), record);
        record->set->features.erase(fIt);
    }
    
    this->featureIdIndex.erase(it);

    this->dispatchDataStoreContentChangedNoSync();
}

struct SafetyToggle {
    SafetyToggle(bool &flag, bool dto)
    : flag(flag), dto(dto) { }
    ~SafetyToggle() { flag = dto; }
    bool &flag;
    bool dto;
};

void RuntimeFeatureDataStore::deleteAllFeaturesImplNoSync(int64_t fsid) {
    this->inBulkModify = true;
    SafetyToggle safety(this->inBulkModify, this->inBulkModify);
    
    auto it = this->featureSetIdIndex.find(fsid);
    if (it == this->featureSetIdIndex.end()) {
        throw std::invalid_argument("No such Feature Set");
    }
    
    FeatureSetRecord *record = &it->second;
    
    for (auto fIt = record->features.begin(); fIt != record->features.end(); ++fIt) {
        this->deleteFeatureImplNoSync((*fIt)->fid, false);
    }
    
    record->features.clear();
    record->visibilityDeviations.clear();
    
    this->dispatchDataStoreContentChangedNoSync();
}

void RuntimeFeatureDataStore::deleteAllFeaturesImpl(int64_t fsid) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw std::runtime_error("RuntimeFeatureDataStore::deleteAllFeaturesImpl: Failed to acquire lock");
    deleteAllFeaturesImplNoSync(fsid);
    

}

/**************************************************************************/

RuntimeFeatureDataStore::FeatureRecord::FeatureRecord(RuntimeFeatureDataStore::FeatureSetRecord *s, int64_t id, atakmap::feature::Feature *f) :
    fid(id),
    set(s),
    feature(f),
    visible(true),
    version(1LL)
{}

RuntimeFeatureDataStore::FeatureRecord::~FeatureRecord()
{}


RuntimeFeatureDataStore::FeatureSetRecord::FeatureSetRecord(int64_t id, FeatureSet *fs) :
    fsid(id),
    featureSet(fs),
    visible(true),
    version(1) { }

RuntimeFeatureDataStore::FeatureSetRecord::~FeatureSetRecord() {}

void RuntimeFeatureDataStore::FeatureRecordQuadtreeFunction(const FeatureRecord &object, atakmap::math::Point<double> &min, atakmap::math::Point<double> &max) {
    Envelope mbb = object.feature->getGeometry().getEnvelope();
    min.x = mbb.minX;
    min.y = mbb.minY;
    max.x = mbb.maxX;
    max.y = mbb.maxY;
}


namespace
{
    inline std::string tolower(const std::string &str) {
        std::string result = str;
        std::transform(str.begin(), str.end(), result.begin(), ::tolower);
        return result;
    }

    /**************************************************************************/
    // Record Filters

    template<class T>
    IdRecordFilter<T>::IdRecordFilter(const std::vector<int64_t> &_ids) {
        this->ids = _ids;
    }

    template<class T>
    IdRecordFilter<T>::~IdRecordFilter()
    {}

    template<class T>
    bool IdRecordFilter<T>::accept(T record) {
        return contains(this->ids, this->getId(record));
    }

    template<class T>
    WildcardsRecordFilter<T>::WildcardsRecordFilter(const std::vector<PGSC::String> &args, char wildcardChar, bool lower) :
        valueIsLowercase(lower) {
        for (const PGSC::String &str : args) {
            std::string arg = (const char *)str;
            const size_t firstIdx = arg.find(wildcardChar);
            if (firstIdx == std::string::npos) {
                this->literals.push_back(tolower(arg));
            }
            else if (arg.rfind(wildcardChar) != firstIdx) {
                std::string l = tolower(arg);
                //TODO--this->regex.push_back(wildcardAsRegex(l.c_str()));
            }
            else if (firstIdx == 0) {
                std::string l = tolower(arg).substr(1);
                this->endsWith.push_back(l);
            }
            else if (firstIdx == arg.length() - 1) {
                std::string l = tolower(arg).substr(0, arg.size() - 1);
                this->startsWith.push_back(l);
            }
            else {
                throw std::invalid_argument("args");
            }
        }
    }

    template<class T>
    WildcardsRecordFilter<T>::~WildcardsRecordFilter()
    {}

    template<class T>
    bool WildcardsRecordFilter<T>::accept(T record) {
        std::string value = this->getValue(record);
        if (!this->valueIsLowercase)
            value = tolower(value);

        if (contains(this->literals, value))
            return true;
        
        for (std::string &s : this->startsWith) {
            if (value.find(s) == 0)
                return true;
        }
        
        for (std::string &s : this->endsWith) {
            if (value.find(s) == value.length() - s.length())
                return true;
        }
        
        /*TODO--for each(System::String *s in this->regex)
            if (System::Text::RegularExpressions::Regex::IsMatch(value, s))
                return true;*/
        
        return false;
    }

    template<class T>
    MultiRecordFilter<T>::MultiRecordFilter(const std::vector<RecordFilter<T> *> &_filters)
    : filters(_filters) { }

    template<class T>
    MultiRecordFilter<T>::~MultiRecordFilter() {
        for (size_t i = 0; i < this->filters.size(); ++i) {
            delete this->filters[i];
        }
    }

    template<class T>
    bool MultiRecordFilter<T>::accept(T record) {
        for (RecordFilter<T> *filter : this->filters)
            if (!filter->accept(record))
                return false;
        return true;
    }


    /**************************************************************************/
    // Feature Record Filters

    FeatureIdRecordFilter::FeatureIdRecordFilter(const std::vector<int64_t> &ids) :
        IdRecordFilter(ids)
    {}

    FeatureIdRecordFilter::~FeatureIdRecordFilter()
    {}

    int64_t FeatureIdRecordFilter::getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        return record->fid;
    }

    FeatureNameRecordFilter::FeatureNameRecordFilter(const std::vector<PGSC::String> &args, char wildcardChar)
    : WildcardsRecordFilter(args, wildcardChar, false)
    {}

    FeatureNameRecordFilter::~FeatureNameRecordFilter()
    {}

    const char *FeatureNameRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record)
    {
        return record->feature->getName();
    }

    VisibleOnlyFeatureRecordFilter::VisibleOnlyFeatureRecordFilter()
    {}

    VisibleOnlyFeatureRecordFilter::~VisibleOnlyFeatureRecordFilter()
    {}

    bool VisibleOnlyFeatureRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
#if 0
        if (!record->set->visible && contains(record->set->visibilityDeviations, record->fid))
            return true;
        return ((record->set->visibilityDeviations.size() < 1) || !contains(record->set->visibilityDeviations, record->fid));
#else
        if (contains(record->set->visibilityDeviations, record->fid))
            return !record->set->visible;
        else
            return record->set->visible;
#endif
    }

    RegionSpatialFeatureRecordFilter::RegionSpatialFeatureRecordFilter(FDS_RegionSpatialFilter *filter)
    {
        this->roi = atakmap::feature::Envelope(filter->upperLeft.longitude,
            filter->lowerRight.latitude,
            0.0,
            filter->lowerRight.longitude,
            filter->upperLeft.latitude,
            0.0);
    }

    RegionSpatialFeatureRecordFilter::~RegionSpatialFeatureRecordFilter()
    {}

    bool RegionSpatialFeatureRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        atakmap::feature::Envelope featureMbb = record->feature->getGeometry().getEnvelope();

        return atakmap::math::Rectangle<double>::intersects(this->roi.minX, this->roi.minY,
            this->roi.maxX, this->roi.maxY,
            featureMbb.minX, featureMbb.minY,
            featureMbb.maxX, featureMbb.maxY);
    }

    RadiusSpatialFeatureRecordFilter::RadiusSpatialFeatureRecordFilter(FDS_RadiusSpatialFilter *filter)
    {
        this->roi = radiusAsRegion(filter->point, filter->radius);
    }

    RadiusSpatialFeatureRecordFilter::~RadiusSpatialFeatureRecordFilter()
    {}

    bool RadiusSpatialFeatureRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        
        atakmap::feature::Envelope featureMbb = record->feature->getGeometry().getEnvelope();

        return atakmap::math::Rectangle<double>::intersects(this->roi.minX, this->roi.minY,
            this->roi.maxX, this->roi.maxY,
            featureMbb.minX, featureMbb.minY,
            featureMbb.maxX, featureMbb.maxY);
    }

    ProviderFeatureRecordFilter::ProviderFeatureRecordFilter(const std::vector<PGSC::String> &providers, char wildcardChar)
    : WildcardsRecordFilter(providers, wildcardChar, false) { }

    ProviderFeatureRecordFilter::~ProviderFeatureRecordFilter() { }

    const char *ProviderFeatureRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record)
    {
        return record->set->featureSet->getProvider();
    }

    TypeFeatureRecordFilter::TypeFeatureRecordFilter(const std::vector<PGSC::String> &providers, char wcc)
    : WildcardsRecordFilter(providers, wcc, false) { }

    TypeFeatureRecordFilter::~TypeFeatureRecordFilter() { }

    const char *TypeFeatureRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        return record->set->featureSet->getType();
    }


    SetIdFeatureRecordFilter::SetIdFeatureRecordFilter(const std::vector<int64_t> &_ids) :
        IdRecordFilter(_ids)
    {}

    int64_t SetIdFeatureRecordFilter::getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        return record->set->fsid;
    }

    SetNameFeatureRecordFilter::SetNameFeatureRecordFilter(const std::vector<PGSC::String> &featureSets, char wcc) :
        WildcardsRecordFilter(featureSets, wcc, false)
    {}

    SetNameFeatureRecordFilter::~SetNameFeatureRecordFilter()
    {}

    const char *SetNameFeatureRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        return record->set->featureSet->getName();
    }


    ResolutionFeatureRecordFilter::ResolutionFeatureRecordFilter(double minRes, double maxRes) :
        minResolution(minRes),
        maxResolution(maxRes)
    {}

    ResolutionFeatureRecordFilter::~ResolutionFeatureRecordFilter()
    {}

    bool ResolutionFeatureRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *record) {
        if (!std::isnan(this->minResolution) && (record->set->featureSet->getMaxResolution() > this->maxResolution))
            return false;
        if (!std::isnan(this->maxResolution) && (record->set->featureSet->getMinResolution() < this->minResolution))
            return false;
        return true;
    }

    /**************************************************************************/
    // Feature Record Filters

    FeatureSetIdRecordFilter::FeatureSetIdRecordFilter(const std::vector<int64_t> &_ids) :
        IdRecordFilter(_ids)
    {}

    FeatureSetIdRecordFilter::~FeatureSetIdRecordFilter()
    {}

    int64_t FeatureSetIdRecordFilter::getId(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        return record->fsid;
    }

    FeatureSetNameRecordFilter::FeatureSetNameRecordFilter(const std::vector<PGSC::String> &args, char wcc) :
        WildcardsRecordFilter(args, wcc, false)
    {}

    FeatureSetNameRecordFilter::~FeatureSetNameRecordFilter()
    {}

    const char *FeatureSetNameRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        return record->featureSet->getName();
    }

    VisibleOnlyFeatureSetRecordFilter::VisibleOnlyFeatureSetRecordFilter()
    {}

    VisibleOnlyFeatureSetRecordFilter::~VisibleOnlyFeatureSetRecordFilter()
    {}

    bool VisibleOnlyFeatureSetRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        return record->visible;
    }

    ProviderFeatureSetRecordFilter::ProviderFeatureSetRecordFilter(const std::vector<PGSC::String> &providers, char wcc) :
        WildcardsRecordFilter(providers, wcc, false)
    {}

    ProviderFeatureSetRecordFilter::~ProviderFeatureSetRecordFilter()
    {}

    const char *ProviderFeatureSetRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        return record->featureSet->getProvider();
    }

    TypeFeatureSetRecordFilter::TypeFeatureSetRecordFilter(const std::vector<PGSC::String> &providers, char wcc) :
        WildcardsRecordFilter(providers, wcc, false)
    {}

    TypeFeatureSetRecordFilter::~TypeFeatureSetRecordFilter()
    {}

    const char *TypeFeatureSetRecordFilter::getValue(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        return record->featureSet->getType();
    }

    ResolutionFeatureSetRecordFilter::ResolutionFeatureSetRecordFilter(double minRes, double maxRes) :
        minResolution(minRes),
        maxResolution(maxRes)
    {}

    ResolutionFeatureSetRecordFilter::~ResolutionFeatureSetRecordFilter()
    {}

    bool ResolutionFeatureSetRecordFilter::accept(const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *record) {
        if (!std::isnan(this->minResolution) && (record->featureSet->getMaxResolution() > this->maxResolution))
            return false;
        if (!std::isnan(this->maxResolution) && (record->featureSet->getMinResolution() < this->maxResolution))
            return false;
        return true;
    }

    /**************************************************************************/
    //

    FeatureCursorImpl::FeatureCursorImpl(RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *> *impl)
    : FeatureCursor(impl) {}

    FeatureCursorImpl::~FeatureCursorImpl()
    {}

    atakmap::feature::Feature *FeatureCursorImpl::get() const throw (CursorError) {
        return dynamic_cast<RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureRecord *> *>(&this->getSubject())->get()->feature.get();
    }

    FeatureSetCursorImpl::FeatureSetCursorImpl(RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *> *impl) :
        FeatureSetCursor(impl)
    {}

    FeatureSetCursorImpl::~FeatureSetCursorImpl()
    {}

    atakmap::feature::FeatureSet *FeatureSetCursorImpl::get() const throw (CursorError) {
        return dynamic_cast<RecordCursor<const atakmap::feature::RuntimeFeatureDataStore::FeatureSetRecord *> *>(&this->getSubject())->get()->featureSet;
    }
    
    template <typename T>
    atakmap::db::Cursor::Blob RecordCursor<T>::getBlob (std::size_t column) const throw (CursorError) {
        return Blob(NULL, NULL);
    }
    
    template <typename T>
    std::size_t RecordCursor<T>::getColumnCount() const {
        // TODO--
        return 0;
    }
    
    template <typename T>
    std::size_t RecordCursor<T>::getColumnIndex (const char* columnName) const throw (CursorError) {
        // TODO--
        return 0;
    }
    
    template <typename T>
    inline const char* RecordCursor<T>::getColumnName (std::size_t column) const throw (CursorError) {
        // TODO--
        return NULL;
    }
    
    template <typename T>
    std::vector<const char*> RecordCursor<T>::getColumnNames () const {
        // TODO--
        return std::vector<const char *>();
    }
    
    template <typename T>
    double RecordCursor<T>::getDouble (std::size_t column) const throw (CursorError) {
        // TODO--
        return NAN;
    }
    
    template <typename T>
    int RecordCursor<T>::getInt(std::size_t column) const throw (CursorError) {
        // TODO--
        return 0;
    }
    
    template <typename T>
    int64_t RecordCursor<T>::getLong(std::size_t column) const throw (CursorError) {
        //TODO--
        return 0;
    }
    
    template <typename T>
    const char* RecordCursor<T>::getString(std::size_t column) const throw (CursorError) {
        //TODO--
        return NULL;
    }
    
    template <typename T>
    atakmap::db::Cursor::FieldType RecordCursor<T>::getType (std::size_t column) const throw (CursorError) {
        //TODO--
        return atakmap::db::Cursor::NULL_FIELD;
    }
    
    template <typename T>
    bool RecordCursor<T>::isNull (std::size_t column) const throw (CursorError) {
        //TODO--
        return false;
    }

    template <typename T>
    RecordCursor<T>::~RecordCursor() { }
    
    /*template<class Container>
    RecordIteratorCursor<Container>::RecordIteratorCursor(Container &init) {
        this->container.swap(init);
        pos = this->container.begin();
    }*/

    template<class Container>
    RecordIteratorCursor<Container>::~RecordIteratorCursor() { }

    template<class Container>
    typename RecordIteratorCursor<Container>::value_type RecordIteratorCursor<Container>::get() const {
        return this->cur;
    }

    template<class Container>
    bool RecordIteratorCursor<Container>::moveToNext() throw (atakmap::db::Cursor::CursorError) {
        cur = NULL;
        if (this->pos != this->container.end()) {
            cur = *this->pos;
            ++this->pos;
            return true;
        }
        return false;
    }

    template<class Container>
    void RecordIteratorCursor<Container>::close() {}

    template<class Container>
    bool RecordIteratorCursor<Container>::isClosed() {
        return false;
    }
    
    template <typename Container>
    void RecordIteratorCursor<Container>::reset(Container &swapContainer) {
        this->container.swap(swapContainer);
        this->pos = container.begin();
        this->cur = NULL;
    }

    template <typename T>
    RecordFilter<T>::~RecordFilter() { }
    
    /*************************************************************************/
    // Filtered Record Iterator Cursor

    template<class T>
    FilteredRecordIteratorCursor<T>::FilteredRecordIteratorCursor(RecordCursor<T> *cursor,
                                                                  RecordFilter<T> *filter)
    : FilteredCursor(cursor), recordFilter(filter) { }

    template<class T>
    FilteredRecordIteratorCursor<T>::~FilteredRecordIteratorCursor() throw() { }

    template<class T>
    bool FilteredRecordIteratorCursor<T>::accept() const {
        T record = this->get();
        return this->recordFilter->accept(record);
    }

    template<class T>
    T FilteredRecordIteratorCursor<T>::get() const {
        return dynamic_cast<RecordCursor<T> *>(&this->getSubject())->get();
    }
    
    template <typename T>
    bool FilteredRecordIteratorCursor<T>::moveToNext() throw(atakmap::db::Cursor::CursorError) {
        return this->FilteredCursor::moveToNext();
    }
    
    /*****************************************************************************/

    /*System::String *wildcardAsRegex(System::String *s) {
        System::Text::StringBuilder *retval = gcnew System::Text::StringBuilder();
        char c;
        for (int i = 0; i < s->Length; i++) {
            c = s[i];
            switch (c) {
            case '%':
                // XXX - check for escape?
                retval->Append(".*");
                break;
            case '*':
            case '\\':
            case '[':
            case ']':
            case '^':
            case '$':
            case '?':
            case '.':
            case '{':
            case '}':
            case '+':
            case '|':
            case '(':
            case ')':
            case '-':
            case ':':
            case '!':
            case '&':
                retval->Append('\\');
            default:
                retval->Append(c);
            }
        }
        return retval->ToString();
    }*/

    atakmap::feature::Envelope radiusAsRegion(const atakmap::core::GeoPoint &center, double radius) {
        
        GeoPoint north, east, south, west;
        
        distance::pointAtRange(center, radius, 0.0, north);
        distance::pointAtRange(center, radius, 90.0, east);
        distance::pointAtRange(center, radius, 180.0, south);
        distance::pointAtRange(center, radius, 210.0, west);
        
        return atakmap::feature::Envelope(atakmap::math::min<double>(north.longitude, east.longitude, south.longitude, west.longitude),
            atakmap::math::min<double>(north.latitude, east.latitude, south.latitude, west.latitude),
            0.0,
            atakmap::math::max<double>(north.longitude, east.longitude, south.longitude, west.longitude),
            atakmap::math::max<double>(north.latitude, east.latitude, south.latitude, west.latitude),
            0.0);
    }
}
