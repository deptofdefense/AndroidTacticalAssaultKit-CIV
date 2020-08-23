#include <cmath> // NAN

#include "feature/FeatureDataStore2.h"

#include "port/Set.h"
#include "port/STLSetAdapter.h"
#include "port/STLVectorAdapter.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    template<class T>
    TAKErr addAll(Collection<T> &sink, Collection<T> &src);
}

FeatureDataStore2::~FeatureDataStore2() NOTHROWS
{}

FeatureDataStore2::FeatureQueryParameters::FeatureQueryParameters() NOTHROWS :
    providers(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    types(new STLSetAdapter<String, StringLess>()), //Port::Collection<Port::String> * const
    featureSets(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    featureSetIds(new STLSetAdapter<int64_t>()), // Port::Collection<int64_t> * const 
    featureNames(new STLSetAdapter<String, StringLess>()), //Port::Collection<Port::String> * const
    featureIds(new STLSetAdapter<int64_t>()), // Port::Collection<int64_t> * const 
    spatialFilter(nullptr, nullptr), // atakmap::feature::UniqueGeometryPtr 
    minResolution(NAN), // double 
    maxResolution(NAN), // double 
    visibleOnly(false), // bool 
    geometryTypes(new STLSetAdapter<atakmap::feature::Geometry::Type>()), // Port::Collection<atakmap::feature::Geometry::Type> * const 
    //atakmap::feature:: attributes(NULL),
    ops(new STLVectorAdapter<SpatialOp>()), // Port::Collection<SpatialOp> * const 
    order(new STLVectorAdapter<Order>()), // Port::Collection<Order> * const 
    ignoredFields(0),
    limit(0),
    offset(0)
{}

FeatureDataStore2::FeatureQueryParameters::FeatureQueryParameters(const FeatureDataStore2::FeatureQueryParameters &other) NOTHROWS :
    providers(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    types(new STLSetAdapter<String, StringLess>()), //Port::Collection<Port::String> * const
    featureSets(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    featureSetIds(new STLSetAdapter<int64_t>()), // Port::Collection<int64_t> * const 
    featureNames(new STLSetAdapter<String, StringLess>()), //Port::Collection<Port::String> * const
    featureIds(new STLSetAdapter<int64_t>()), // Port::Collection<int64_t> * const 
    spatialFilter(other.spatialFilter.get() ? other.spatialFilter->clone() : nullptr, atakmap::feature::destructGeometry), // atakmap::feature::UniqueGeometryPtr 
    minResolution(other.minResolution), // double 
    maxResolution(other.maxResolution), // double 
    visibleOnly(other.visibleOnly), // bool 
    geometryTypes(new STLSetAdapter<atakmap::feature::Geometry::Type>()), // Port::Collection<atakmap::feature::Geometry::Type> * const 
    //atakmap::feature:: attributes(NULL),
    ops(new STLVectorAdapter<SpatialOp>()), // Port::Collection<SpatialOp> * const 
    order(new STLVectorAdapter<Order>()), // Port::Collection<Order> * const 
    ignoredFields(other.ignoredFields),
    limit(other.limit),
    offset(other.offset)
{
    TAKErr code;

    code = addAll(*providers, *other.providers);
    code = addAll(*types, *other.types);
    code = addAll(*featureSets, *other.featureSets);
    code = addAll(*featureSetIds, *other.featureSetIds);
    code = addAll(*featureNames, *other.featureNames);
    code = addAll(*featureIds, *other.featureIds);
    code = addAll(*geometryTypes, *other.geometryTypes);
    code = addAll(*ops, *other.ops);
    code = addAll(*order, *other.order);
}

FeatureDataStore2::FeatureQueryParameters::~FeatureQueryParameters() NOTHROWS
{
#define DELETE_STRING_SET(param) \
    Memory_deleter_const<Collection<String>, STLSetAdapter<String, StringLess>>(param);
#define DELETE_LONG_SET(param) \
    Memory_deleter_const<Collection<int64_t>, STLSetAdapter<int64_t>>(param);

    DELETE_STRING_SET(providers);
    DELETE_STRING_SET(types);
    DELETE_STRING_SET(featureSets);
    DELETE_LONG_SET(featureSetIds);
    DELETE_STRING_SET(featureNames);
    DELETE_LONG_SET(featureIds);
#undef DELETE_LONG_SET
#undef DELETE_STRING_SET

    Memory_deleter_const<Collection<atakmap::feature::Geometry::Type>, STLSetAdapter<atakmap::feature::Geometry::Type>>(geometryTypes);
    Memory_deleter_const<Collection<SpatialOp>, STLVectorAdapter<SpatialOp>>(ops);
    Memory_deleter_const<Collection<Order>, STLVectorAdapter<Order>>(order);
}

FeatureDataStore2::FeatureQueryParameters &FeatureDataStore2::FeatureQueryParameters::operator=(const FeatureDataStore2::FeatureQueryParameters &other) NOTHROWS
{
    if (other.spatialFilter.get())
        this->spatialFilter = GeometryPtr(other.spatialFilter->clone(), atakmap::feature::destructGeometry);
    else
        this->spatialFilter.reset();
    this->minResolution = other.minResolution;
    this->maxResolution = other.maxResolution;
    this->visibleOnly = other.visibleOnly;
    this->ignoredFields = other.ignoredFields;
    this->limit = other.limit;
    this->offset = other.offset;

#define COLLECTION_SET(n) \
    this->n->clear(); \
    addAll(*this->n, *other.n);

    COLLECTION_SET(providers);
    COLLECTION_SET(types);
    COLLECTION_SET(featureSets);
    COLLECTION_SET(featureSetIds);
    COLLECTION_SET(featureNames);
    COLLECTION_SET(featureIds);
    COLLECTION_SET(geometryTypes);
    COLLECTION_SET(ops);
    COLLECTION_SET(order);

#undef COLLECTION_SET

    return *this;
}

FeatureDataStore2::FeatureSetQueryParameters::FeatureSetQueryParameters() NOTHROWS :
    providers(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    types(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    names(new STLSetAdapter<String, StringLess>()), // Port::Collection<Port::String> * const
    ids(new STLSetAdapter<int64_t>()), // Port::Collection<int64_t> * const 
    visibleOnly(false),
    limit(0),
    offset(0)
{}

FeatureDataStore2::FeatureSetQueryParameters::~FeatureSetQueryParameters() NOTHROWS
{
#define DELETE_STRING_SET(param) \
    Memory_deleter_const<Collection<String>, STLSetAdapter<String, StringLess>>(param);
#define DELETE_LONG_SET(param) \
    Memory_deleter_const<Collection<int64_t>, STLSetAdapter<int64_t>>(param);

    DELETE_STRING_SET(providers);
    DELETE_STRING_SET(types);
    DELETE_STRING_SET(names);
    DELETE_LONG_SET(ids);
#undef DELETE_LONG_SET
#undef DELETE_STRING_SET
}

FeatureDataStore2::OnDataStoreContentChangedListener::~OnDataStoreContentChangedListener() NOTHROWS
{}

namespace
{
    template<class T>
    TAKErr addAll(Collection<T> &sink, Collection<T> &src)
    {
        TAKErr code(TE_Ok);
        if (!src.empty()) {
            typename Collection<T>::IteratorPtr iter(nullptr, nullptr);
            T val;

            code = src.iterator(iter);
            TE_CHECKRETURN_CODE(code);
            do {
                code = iter->get(val);
                if (code != TE_Ok)
                    break;
                code = sink.add(val);
                if (code != TE_Ok)
                    break;
                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
        }
        return code;
    }
}
