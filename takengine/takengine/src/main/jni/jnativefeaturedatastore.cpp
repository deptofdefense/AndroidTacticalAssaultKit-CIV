#include "jnativefeaturedatastore.h"

#include <list>

#include <feature/Feature2.h>
#include <feature/FeatureCursor2.h>
#include <feature/FeatureDataStore2.h>
#include <feature/FeatureDefinition2.h>
#include <feature/FeatureSetCursor2.h>
#include <feature/Geometry.h>
#include <feature/Geometry2.h>
#include <feature/GeometryFactory.h>
#include <feature/LegacyAdapters.h>
#include <feature/ParseGeometry.h>
#include <feature/Style.h>
#include <util/AttributeSet.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIIntArray.h"
#include "interop/JNILongArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/JNINotifyCallback.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::util;

using namespace TAKEngineJNI::Interop;

namespace
{
    class CallbackForwarder : public FeatureDataStore2::OnDataStoreContentChangedListener
    {
    public :
        CallbackForwarder(JNIEnv *env, jobject impl) NOTHROWS;
        ~CallbackForwarder() NOTHROWS;
    public :
        void onDataStoreContentChanged(FeatureDataStore2 &dataStore) NOTHROWS;
    private :
        jobject impl;
    };

    TAKErr AttributeSet_merge(AttributeSet &target, const AttributeSet &update)
    {
        try {
            std::vector<const char *> keys = update.getAttributeNames();
            for(std::size_t i = 0u; i < keys.size(); i++) {
                const char *key = keys[i];
                switch(update.getAttributeType(key)) {
                    case AttributeSet::INT :
                        target.setInt(key, update.getInt(key));
                        break;
                    case AttributeSet::LONG :
                        target.setLong(key, update.getLong(key));
                        break;
                    case AttributeSet::DOUBLE :
                        target.setDouble(key, update.getDouble(key));
                        break;
                    case AttributeSet::STRING :
                        target.setString(key, update.getString(key));
                        break;
                    case AttributeSet::BLOB :
                        target.setBlob(key, update.getBlob(key));
                        break;
                    case AttributeSet::INT_ARRAY :
                        target.setIntArray(key, update.getIntArray(key));
                        break;
                    case AttributeSet::LONG_ARRAY :
                        target.setLongArray(key, update.getLongArray(key));
                        break;
                    case AttributeSet::DOUBLE_ARRAY :
                        target.setDoubleArray(key, update.getDoubleArray(key));
                        break;
                    case AttributeSet::STRING_ARRAY :
                        target.setStringArray(key, update.getStringArray(key));
                        break;
                    case AttributeSet::BLOB_ARRAY :
                        target.setBlobArray(key, update.getBlobArray(key));
                        break;
                    case AttributeSet::ATTRIBUTE_SET :
                        {
                            AttributeSet nested(target.getAttributeSet(key));
                            TAKErr code = AttributeSet_merge(nested, update.getAttributeSet(key));
                            TE_CHECKRETURN_CODE(code);
                            target.setAttributeSet(key, nested);
                        }
                        break;
                    default :
                        return TE_InvalidArg;
                }
            }
        } catch(...) {
            return TE_Err;
        }
    }

    TAKErr FeatureDataStore2_updateFeature(FeatureDataStore2 &dataStore, const int64_t fid, const int fields, const char *name, const Geometry *geom, const Style *style, const AttributeSet *attrs, const int attrUpdateType)
    {
        switch(fields) {
            case 0 :
                return TE_Ok;
            case FeatureDataStore2::FeatureQueryParameters::GeometryField :
                if(!geom)
                    return TE_InvalidArg;
                return dataStore.updateFeature(fid, *geom);
            case FeatureDataStore2::FeatureQueryParameters::StyleField :
                return dataStore.updateFeature(fid, style);
            case FeatureDataStore2::FeatureQueryParameters::AttributesField :
                {
                    std::unique_ptr<AttributeSet> attrsCleaner;
                    if(!attrs) {
                        // add/replace with empty is identity
                        if(attrUpdateType == FeatureDataStore2::UPDATE_ATTRIBUTESET_ADD_OR_REPLACE)
                            return TE_Ok;
                        attrsCleaner.reset(new AttributeSet());
                        attrs = attrsCleaner.get();
                    }
                    if(attrUpdateType == FeatureDataStore2::UPDATE_ATTRIBUTESET_SET) {
                        return dataStore.updateFeature(fid, *attrs);
                    } else if(attrUpdateType == FeatureDataStore2::UPDATE_ATTRIBUTESET_ADD_OR_REPLACE) {
                        // get source attributes
                        FeatureDataStore2::FeatureQueryParameters params;
                        TAKErr code = params.featureIds->add(fid);
                        TE_CHECKRETURN_CODE(code);
                        params.ignoredFields = FeatureDataStore2::FeatureQueryParameters::NameField|FeatureDataStore2::FeatureQueryParameters::GeometryField|FeatureDataStore2::FeatureQueryParameters::StyleField;
                        params.limit = 1;

                        FeatureCursorPtr result(NULL, NULL);
                        code = dataStore.queryFeatures(result, params);
                        TE_CHECKRETURN_CODE(code);

                        code = result->moveToNext();
                        if(code == TE_Done)
                            return TE_InvalidArg;
                        TE_CHECKRETURN_CODE(code);

                        //  merge attributes
                        const AttributeSet *src;
                        code = result->getAttributes(&src);
                        TE_CHECKRETURN_CODE(code);

                        // add or replace with empty source data
                        if(!src)
                            return dataStore.updateFeature(fid, *attrs);

                        AttributeSet mergeResult(*src);
                        code = AttributeSet_merge(mergeResult, *attrs);
                        TE_CHECKRETURN_CODE(code);

                        return dataStore.updateFeature(fid, mergeResult);
                    } else {
                        return TE_InvalidArg;
                    }
                }
            case FeatureDataStore2::FeatureQueryParameters::NameField :
                return dataStore.updateFeature(fid, name);
            case FeatureDataStore2::FeatureQueryParameters::GeometryField|FeatureDataStore2::FeatureQueryParameters::StyleField|FeatureDataStore2::FeatureQueryParameters::AttributesField|FeatureDataStore2::FeatureQueryParameters::NameField :
                {
                    if(!geom)
                        return TE_InvalidArg;

                    std::unique_ptr<AttributeSet> attrsCleaner;
                    if(!attrs) {
                        attrsCleaner.reset(new AttributeSet());
                        attrs = attrsCleaner.get();
                    }
                    return dataStore.updateFeature(fid, name, *geom, style, *attrs);
                }
            default :
                {
                    // make sure there are no unrecognized flags
                    if((~(FeatureDataStore2::FeatureQueryParameters::GeometryField|FeatureDataStore2::FeatureQueryParameters::StyleField|FeatureDataStore2::FeatureQueryParameters::AttributesField|FeatureDataStore2::FeatureQueryParameters::NameField))&fields)
                        return TE_InvalidArg;

                    // XXX - not efficient -- mask off each field
                    TAKErr code(TE_Ok);
                    code = FeatureDataStore2_updateFeature(dataStore, fid, fields&FeatureDataStore2::FeatureQueryParameters::GeometryField, name, geom, style, attrs, attrUpdateType);
                    TE_CHECKRETURN_CODE(code);
                    code = FeatureDataStore2_updateFeature(dataStore, fid, fields&FeatureDataStore2::FeatureQueryParameters::StyleField, name, geom, style, attrs, attrUpdateType);
                    TE_CHECKRETURN_CODE(code);
                    code = FeatureDataStore2_updateFeature(dataStore, fid, fields&FeatureDataStore2::FeatureQueryParameters::AttributesField, name, geom, style, attrs, attrUpdateType);
                    TE_CHECKRETURN_CODE(code);
                    code = FeatureDataStore2_updateFeature(dataStore, fid, fields&FeatureDataStore2::FeatureQueryParameters::NameField, name, geom, style, attrs, attrUpdateType);
                    TE_CHECKRETURN_CODE(code);

                    return code;
                }
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    if(!jpointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Pointer_destruct_iface<FeatureDataStore2>(env, jpointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureQueryParameters_1create
  (JNIEnv *env, jclass clazz)
{
    std::unique_ptr<FeatureDataStore2::FeatureQueryParameters, void(*)(const FeatureDataStore2::FeatureQueryParameters *)> cparams(new FeatureDataStore2::FeatureQueryParameters(), Memory_deleter_const<FeatureDataStore2::FeatureQueryParameters>);
    return NewPointer(env, std::move(cparams));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureQueryParameters_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    if(!jpointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Pointer_destruct<FeatureDataStore2::FeatureQueryParameters>(env, jpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureQueryParameters_1setFeatureSetFilter
  (JNIEnv *env, jclass clazz, jlong ptr, jlongArray jfsidsArr, jobjectArray jnamesArr, jobjectArray jtypesArr, jobjectArray jprovidersArr, jdouble minResolution, jdouble maxResolution, jboolean visibleOnly, jint limit, jint offset)
{
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, ptr);

    TAKErr code(TE_Ok);
    if(jfsidsArr) {
        JNILongArray fsids(*env, jfsidsArr, JNI_ABORT);
        if(fsids.length()) {
            for(std::size_t i = 0u; i < fsids.length(); i++) {
                code = params->featureSetIds->add(fsids[i]);
                TE_CHECKBREAK_CODE(code);
            }
        } else {
            code = params->featureSetIds->add(FeatureDataStore2::FEATURESET_ID_NONE);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
#define ADAPT_JSTRING_ARRAY(jarr, carr) \
    if(jarr) { \
        const std::size_t length = env->GetArrayLength(jarr); \
        for(std::size_t i = 0u; i < length; i++) { \
            jstring jname = (jstring)env->GetObjectArrayElement(jarr, i); \
            JNIStringUTF name(*env, jname); \
            code = params->carr->add(name.get()); \
            TE_CHECKBREAK_CODE(code); \
        } \
        if(ATAKMapEngineJNI_checkOrThrow(env, code)) \
            return; \
    }

    ADAPT_JSTRING_ARRAY(jnamesArr, featureSets);
    ADAPT_JSTRING_ARRAY(jprovidersArr, providers);
    ADAPT_JSTRING_ARRAY(jtypesArr, types);
#undef ADAPT_JSTRING_ARRAY

    params->minResolution = minResolution;
    params->maxResolution = maxResolution;
    params->limit = limit;
    params->offset = offset;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureQueryParameters_1set
  (JNIEnv *env, jclass clazz, jlong ptr, jlongArray jfidsArr, jobjectArray jnamesArr, jintArray jgeomTypesArr, jboolean visibleOnly, jlong spatialFilterPtr, jlong minimumTimestamp, jlong maximumTimestamp, jint ignoredFeatureProperties, jint numSpatialOps, jintArray spatialOpTypesArr, jdoubleArray spatialOpArgsArr, jint numOrders, jintArray orderTypesArr, jdoubleArray orderArgsArr, jint limit, jint offset, jlong timeout)
{
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, ptr);
    
    TAKErr code(TE_Ok);
    if(jfidsArr) {
        JNILongArray fids(*env, jfidsArr, JNI_ABORT);
        if(fids.length()) {
            for (std::size_t i = 0u; i < fids.length(); i++) {
                code = params->featureIds->add(fids[i]);
                TE_CHECKBREAK_CODE(code);
            }
        } else {
            code = params->featureIds->add(FeatureDataStore2::FEATURE_ID_NONE);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    if(jnamesArr) {
        const std::size_t length = env->GetArrayLength(jnamesArr);
        for(std::size_t i = 0u; i < length; i++) {
            jstring jname = (jstring)env->GetObjectArrayElement(jnamesArr, i);
            JNIStringUTF name(*env, jname);
            code = params->featureNames->add(name.get());
            TE_CHECKBREAK_CODE(code);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    if(jgeomTypesArr) {
        JNIIntArray geomTypes(*env, jgeomTypesArr, JNI_ABORT);
        for(std::size_t i = 0u; i < geomTypes.length(); i++) {
            code = params->geometryTypes->add((atakmap::feature::Geometry::Type)geomTypes[i]);
            TE_CHECKBREAK_CODE(code);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    params->visibleOnly = visibleOnly;
    if(spatialFilterPtr) {
        code = LegacyAdapters_adapt(params->spatialFilter, *JLONG_TO_INTPTR(Geometry2, spatialFilterPtr));
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
    params->ignoredFields = ignoredFeatureProperties;
    if(numSpatialOps) {
        if(!spatialOpTypesArr) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        JNIIntArray spatialOpTypes(*env, spatialOpTypesArr, JNI_ABORT);
        if(numSpatialOps > spatialOpTypes.length()) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        if(!spatialOpArgsArr) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        JNIDoubleArray spatialOpArgs(*env, spatialOpArgsArr, JNI_ABORT);
        if(numSpatialOps > spatialOpArgs.length()) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }

        for(std::size_t i = 0u; i < numSpatialOps; i++) {
            typedef FeatureDataStore2::FeatureQueryParameters::SpatialOp::Type SpatialOpType;
            FeatureDataStore2::FeatureQueryParameters::SpatialOp op;
            op.type = (SpatialOpType)spatialOpTypes[i];
            switch(op.type) {
                case SpatialOpType::Buffer :
                    op.args.buffer.distance = spatialOpArgs[i];
                    break;
                case SpatialOpType::Simplify :
                    op.args.simplify.distance = spatialOpArgs[i];
                    break;
                default :
                    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                    return;
            }
            code = params->ops->add(op);
            if(ATAKMapEngineJNI_checkOrThrow(env, code))
                return;
        }
    }
    if(numOrders) {
        if(!orderTypesArr) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        JNIIntArray orderTypes(*env, orderTypesArr, JNI_ABORT);
        if(numOrders > orderTypes.length()) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        if(!orderArgsArr) {
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return;
        }
        JNIDoubleArray orderArgs(*env, orderArgsArr, JNI_ABORT);
        std::size_t orderArgIdx = 0u;
        for(std::size_t i = 0u; i < numSpatialOps; i++) {
            typedef FeatureDataStore2::FeatureQueryParameters::Order::Type OrderType;
            FeatureDataStore2::FeatureQueryParameters::Order order;
            order.type = (OrderType)orderTypes[i];
            switch(order.type) {
                case OrderType::Resolution :
                case OrderType::FeatureSet :
                case OrderType::FeatureName :
                case OrderType::FeatureId :
                case OrderType::GeometryType :
                    break;
                case OrderType::Distance :
                    if(orderArgIdx+3u > orderArgs.length()) {
                        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                        return;
                    }
                    order.args.distance.x = orderArgs[orderArgIdx++];
                    order.args.distance.y = orderArgs[orderArgIdx++];
                    order.args.distance.z = orderArgs[orderArgIdx++];
                    break;
                default :
                    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                    return;
            }
            code = params->order->add(order);
            if(ATAKMapEngineJNI_checkOrThrow(env, code))
                return;
        }
    }
    params->limit = limit;
    params->offset = offset;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureSetQueryParameters_1create
  (JNIEnv *env, jclass clazz)
{
    std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters, void(*)(const FeatureDataStore2::FeatureSetQueryParameters *)> params(new FeatureDataStore2::FeatureSetQueryParameters(), Memory_deleter_const<FeatureDataStore2::FeatureSetQueryParameters>);
    return NewPointer(env, std::move(params));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureSetQueryParameters_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    if(!jpointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Pointer_destruct_iface<FeatureDataStore2::FeatureSetQueryParameters>(env, jpointer);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_FeatureSetQueryParameters_1set
  (JNIEnv *env, jclass clazz, jlong ptr, jlongArray jfsidsArr, jobjectArray jnamesArr, jobjectArray jtypesArr, jobjectArray jprovidersArr, jdouble minResolution, jdouble maxResolution, jboolean visibleOnly, jint limit, jint offset)
{
    FeatureDataStore2::FeatureSetQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureSetQueryParameters, ptr);

    TAKErr code(TE_Ok);
    if(jfsidsArr) {
        JNILongArray fsids(*env, jfsidsArr, JNI_ABORT);
        if(fsids.length()) {
            for(std::size_t i = 0u; i < fsids.length(); i++) {
                code = params->ids->add(fsids[i]);
                TE_CHECKBREAK_CODE(code);
            }
        } else {
            code = params->ids->add(FeatureDataStore2::FEATURESET_ID_NONE);
        }
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }
#define ADAPT_JSTRING_ARRAY(jarr, carr) \
    if(jarr) { \
        const std::size_t length = env->GetArrayLength(jarr); \
        for(std::size_t i = 0u; i < length; i++) { \
            jstring jname = (jstring)env->GetObjectArrayElement(jarr, i); \
            JNIStringUTF name(*env, jname); \
            code = params->carr->add(name.get()); \
            TE_CHECKBREAK_CODE(code); \
        } \
        if(ATAKMapEngineJNI_checkOrThrow(env, code)) \
            return; \
    }

    ADAPT_JSTRING_ARRAY(jnamesArr, names);
    ADAPT_JSTRING_ARRAY(jprovidersArr, providers);
    ADAPT_JSTRING_ARRAY(jtypesArr, types);
#undef ADAPT_JSTRING_ARRAY

    // XXX - not in current API
    //params->minGsd = minResolution;
    //params->maxGsd = maxResolution;

    params->visibleOnly = visibleOnly;
    params->limit = limit;
    params->offset = offset;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_queryFeatures
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    FeatureCursorPtr result(NULL, NULL);
    code = dataStore->queryFeatures(result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(result));
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_queryFeaturesCount
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    int result;
    code = dataStore->queryFeaturesCount(&result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return result;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_queryFeatureSets
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    FeatureDataStore2::FeatureSetQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureSetQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureSetQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    FeatureSetCursorPtr result(NULL, NULL);
    code = dataStore->queryFeatureSets(result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(result));
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_queryFeatureSetsCount
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    FeatureDataStore2::FeatureSetQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureSetQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureSetQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    int result;
    code = dataStore->queryFeatureSetsCount(&result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return result;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_insertFeature__JJJLjava_lang_String_2ILjava_lang_Object_2ILjava_lang_Object_2JJJ
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jlong fid, jstring name, jint geomCoding, jobject rawGeom, jint styleCoding, jobject rawStyle, jlong attrsPtr, jlong timestamp, jlong version)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }

    TAKErr code(TE_Ok);

    // XXX - defer supported to implementation after interface update
    if(fid != FeatureDataStore2::FEATURE_ID_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    if(version != FeatureDataStore2::FEATURE_VERSION_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    if(timestamp != TE_TIMESTAMP_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }

    JNIStringUTF cname(*env, name);

    if(!rawGeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    GeometryPtr_const cgeom(NULL, NULL);
    switch(geomCoding) {
        case FeatureDefinition2::GeomGeometry :
            {
                // REMEMBER: Java always deals in Geometry2
                Geometry2 *cgeom2 = Pointer_get<Geometry2>(env, rawGeom);
                if(!cgeom2) {
                    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                    return FeatureDataStore2::FEATURE_ID_NONE;
                }
                code = LegacyAdapters_adapt(cgeom, *cgeom2);
                if(ATAKMapEngineJNI_checkOrThrow(env, code))
                    return FeatureDataStore2::FEATURE_ID_NONE;
            }
            break;
        case FeatureDefinition2::GeomWkb :
            {
                JNIByteArray jwkb(*env, (jbyteArray)rawGeom, JNI_ABORT);
                Geometry2Ptr cgeom2(NULL, NULL);
                const jbyte *wkb = jwkb;
                code = GeometryFactory_fromWkb(cgeom2, reinterpret_cast<const uint8_t *>(wkb), jwkb.length());
                if(ATAKMapEngineJNI_checkOrThrow(env, code))
                    return FeatureDataStore2::FEATURE_ID_NONE;
                code = LegacyAdapters_adapt(cgeom, *cgeom2);
                if(ATAKMapEngineJNI_checkOrThrow(env, code))
                    return FeatureDataStore2::FEATURE_ID_NONE;
            }
            break;
        case FeatureDefinition2::GeomBlob :
            {
                JNIByteArray jblob(*env, (jbyteArray)rawGeom, JNI_ABORT);
                Geometry2Ptr cgeom2(NULL, NULL);
                const jbyte *blob = jblob;
                code = GeometryFactory_fromSpatiaLiteBlob(cgeom2, reinterpret_cast<const uint8_t *>(blob), jblob.length());
                if(ATAKMapEngineJNI_checkOrThrow(env, code))
                    return FeatureDataStore2::FEATURE_ID_NONE;
                code = LegacyAdapters_adapt(cgeom, *cgeom2);
                if(ATAKMapEngineJNI_checkOrThrow(env, code))
                    return FeatureDataStore2::FEATURE_ID_NONE;
            }
            break;
        case FeatureDefinition2::GeomWkt :
            {
                JNIStringUTF wkt(*env, (jstring)rawGeom);
                try {
                    cgeom = GeometryPtr_const(parseWKT(wkt), destructGeometry);
                } catch(...) {
                    if(ATAKMapEngineJNI_checkOrThrow(env, TE_Err))
                        return FeatureDataStore2::FEATURE_ID_NONE;
                }
            }
            break;
        default :
            ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
            return FeatureDataStore2::FEATURE_ID_NONE;
    }

    StylePtr_const cstyle(NULL, NULL);
    if(rawStyle) {
        switch(styleCoding) {
            case FeatureDefinition2::StyleStyle :
                cstyle = StylePtr_const(Pointer_get<Style>(env, rawStyle), Memory_leaker_const<Style>);
                break;
            case FeatureDefinition2::StyleOgr :
                {
                    JNIStringUTF ogr(*env, (jstring)rawStyle);
                    try {
                        cstyle = StylePtr_const(Style::parseStyle(ogr), Style::destructStyle);
                    } catch(...) {
                        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
                        return FeatureDataStore2::FEATURE_ID_NONE;
                    }
                }
                break;
            default :
                ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
                return FeatureDataStore2::FEATURE_ID_NONE;
        }
    }

    AttributeSetPtr_const cattrs(NULL, NULL);
    if(attrsPtr)
        cattrs = AttributeSetPtr_const(JLONG_TO_INTPTR(AttributeSet, attrsPtr), Memory_leaker_const<AttributeSet>);
    else
        cattrs = AttributeSetPtr_const(new AttributeSet(), Memory_deleter_const<AttributeSet>);

    FeaturePtr_const inserted(NULL, NULL);
    code = dataStore->insertFeature(&inserted, fsid, cname, *cgeom, AltitudeMode::TEAM_ClampToGround, 0.0, cstyle.get(), *cattrs);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURE_ID_NONE;
    return inserted->getId();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_insertFeature__JJ
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong featurePtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    Feature2 *feature = JLONG_TO_INTPTR(Feature2, featurePtr);
    if(!feature) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }

    // XXX - defer supported to implementation after interface update
    if(feature->getId() != FeatureDataStore2::FEATURE_ID_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    if(feature->getVersion() != FeatureDataStore2::FEATURE_VERSION_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    if(feature->getTimestamp() != TE_TIMESTAMP_NONE) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Unsupported);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }

    const atakmap::feature::Geometry *geom = feature->getGeometry();
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_ID_NONE;
    }
    const atakmap::util::AttributeSet *attrs = feature->getAttributes();
    std::unique_ptr<atakmap::util::AttributeSet> attrsCleaner;
    if(!attrs) {
        attrsCleaner.reset(new atakmap::util::AttributeSet());
        attrs = attrsCleaner.get();
    }

    TAKErr code(TE_Ok);
    FeaturePtr_const inserted(NULL, NULL);
    code = dataStore->insertFeature(&inserted, feature->getFeatureSetId(), feature->getName(), *geom, AltitudeMode::TEAM_ClampToGround, 0.0, feature->getStyle(), *attrs);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURE_ID_NONE;

    return inserted->getId();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_insertFeatureSet
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jstring jname, jstring jprovider, jstring jtype, jdouble minRes, jdouble maxRes, jlong version)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURESET_ID_NONE;
    }

    JNIStringUTF name(*env, jname);
    JNIStringUTF provider(*env, jprovider);
    JNIStringUTF type(*env, jtype);

    TAKErr code(TE_Ok);
    FeatureSetPtr_const inserted(NULL, NULL);
    code = dataStore->insertFeatureSet(&inserted, provider, type, name, minRes, maxRes);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURESET_ID_NONE;

    return inserted->getId();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_updateFeature
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fid, jint fields, jstring jname, jlong geomPtr, jlong stylePtr, jlong attrsPtr, jint attrUpdateType)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    }

    TAKErr code(TE_Ok);

    JNIStringUTF name(*env, jname);

    // REMEMBER: Java always passes in Geometry2
    GeometryPtr_const cgeom(NULL, NULL);
    if(geomPtr) {
        Geometry2 *cgeom2 = JLONG_TO_INTPTR(Geometry2, geomPtr);
        code = LegacyAdapters_adapt(cgeom, *cgeom2);
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return;
    }

    code = FeatureDataStore2_updateFeature(*dataStore, fid, fields, name, cgeom.get(), JLONG_TO_INTPTR(Style, stylePtr), JLONG_TO_INTPTR(AttributeSet, attrsPtr), attrUpdateType);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_updateFeatureSet__JJLjava_lang_String_2DD
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jstring jname, jdouble minRes, jdouble maxRes)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    JNIStringUTF name(*env, jname);

    TAKErr code(TE_Ok);
    code = dataStore->updateFeatureSet(fsid, name, minRes, maxRes);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_updateFeatureSet__JJLjava_lang_String_2
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jstring jname)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    JNIStringUTF name(*env, jname);

    TAKErr code(TE_Ok);
    code = dataStore->updateFeatureSet(fsid, name);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_updateFeatureSet__JJDD
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jdouble minRes, jdouble maxRes)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = dataStore->updateFeatureSet(fsid, minRes, maxRes);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_deleteFeature
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong fid)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = dataStore->deleteFeature(fid);
    if(code == TE_InvalidArg)
        code = TE_Ok;

    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_deleteFeatures
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    FeatureCursorPtr result(NULL, NULL);
    code = dataStore->queryFeatures(result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<int64_t> fids;
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        int64_t fid;
        code = result->getId(&fid);
        TE_CHECKBREAK_CODE(code);

        fids.push_back(fid);
    } while(true);
    if(code == TE_Done)
        code = TE_Ok;
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<int64_t>::iterator it;
    for(it = fids.begin(); it != fids.end(); it++) {
        code = dataStore->deleteFeature(*it);
        if(code == TE_InvalidArg)
            code = TE_Ok;
        TE_CHECKBREAK_CODE(code);
    }
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_deleteFeatureSet
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong fsid)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    code = dataStore->deleteFeatureSet(fsid);
    if(code == TE_InvalidArg)
        code = TE_Ok;

    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_deleteFeatureSets
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    FeatureDataStore2::FeatureSetQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureSetQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureSetQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    FeatureSetCursorPtr result(NULL, NULL);
    code = dataStore->queryFeatureSets(result, *params);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<int64_t> fsids;
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        const FeatureSet2 *fs;
        code = result->get(&fs);
        TE_CHECKBREAK_CODE(code);

        fsids.push_back(fs->getId());
    } while(true);
    if(code == TE_Done)
        code = TE_Ok;
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    std::list<int64_t>::iterator it;
    for(it = fsids.begin(); it != fsids.end(); it++) {
        code = dataStore->deleteFeatureSet(*it);
        if(code == TE_InvalidArg)
            code = TE_Ok;
        TE_CHECKBREAK_CODE(code);
    }
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_setFeatureVisible
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fid, jboolean visible)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    TAKErr code(TE_Ok);
    code = dataStore->setFeatureVisible(fid, visible);
    if(code != TE_InvalidArg && ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_setFeaturesVisible
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong paramsPtr, jboolean visible)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    FeatureDataStore2::FeatureQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureQueryParameters, paramsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    code = dataStore->setFeaturesVisible(*params, visible);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_setFeatureSetVisible
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid, jboolean visible)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    TAKErr code(TE_Ok);
    code = dataStore->setFeatureSetVisible(fsid, visible);
    if(code != TE_InvalidArg && ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_setFeatureSetsVisible
  (JNIEnv *env, jclass clazz, jlong dataStorePtr, jlong queryParamsPtr, jboolean visible)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, dataStorePtr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    FeatureDataStore2::FeatureSetQueryParameters *params = JLONG_TO_INTPTR(FeatureDataStore2::FeatureSetQueryParameters, queryParamsPtr);
    std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters> paramsCleaner;
    if(!params) {
        paramsCleaner.reset(new FeatureDataStore2::FeatureSetQueryParameters());
        params = paramsCleaner.get();
    }
    TAKErr code(TE_Ok);
    code = dataStore->setFeatureSetsVisible(*params, visible);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_hasTimeReference
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    return false;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMinimumTimestamp
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
    return TE_TIMESTAMP_NONE;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMaximumTimestamp
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
    return TE_TIMESTAMP_NONE;
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getUri
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    TAKErr code(TE_Ok);
    TAK::Engine::Port::String curi;
    code = dataStore->getUri(curi);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!curi)
        return NULL;
    return env->NewStringUTF(curi);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_supportsExplicitIDs
  (JNIEnv *, jclass, jlong)
{
    // XXX -
    return false;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getModificationFlags
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    TAKErr code(TE_Ok);
    int flags;
    code = dataStore->getModificationFlags(&flags);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return flags;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getVisibilityFlags
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    TAKErr code(TE_Ok);
    int flags;
    code = dataStore->getVisibilitySettingsFlags(&flags);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return flags;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_hasCache
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
    return false;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_clearCache
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getCacheSize
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
    return 0LL;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_acquireModifyLock
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean bulkModification)
{
    // XXX -
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_releaseModifyLock
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    // XXX -
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_addOnDataStoreContentChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jobject jnotifyCallback)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!dataStore) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    if(!jnotifyCallback) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    std::unique_ptr<FeatureDataStore2::OnDataStoreContentChangedListener, void(*)(const FeatureDataStore2::OnDataStoreContentChangedListener *)> clistener(new CallbackForwarder(env, jnotifyCallback), Memory_deleter_const<FeatureDataStore2::OnDataStoreContentChangedListener, CallbackForwarder>);
    code = dataStore->addOnDataStoreContentChangedListener(clistener.get());
    if(ATAKMapEngineJNI_checkOrThrow(env, code)) {
        return NULL;
    }

    return NewPointer(env, std::move(clistener));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_removeOnDataStoreContentChangedListener
  (JNIEnv *env, jclass clazz, jlong ptr, jobject jnotifyCallbackPointer)
{
    FeatureDataStore2 *dataStore = JLONG_TO_INTPTR(FeatureDataStore2, ptr);
    if(!jnotifyCallbackPointer) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    FeatureDataStore2::OnDataStoreContentChangedListener *clistener = Pointer_get<FeatureDataStore2::OnDataStoreContentChangedListener>(env, jnotifyCallbackPointer);
    if(dataStore)
        code = dataStore->removeOnDataStoreContentChangedListener(clistener);
    if(ATAKMapEngineJNI_checkOrThrow(env, code)) {
        return;
    }

    Pointer_destruct_iface<FeatureDataStore2::OnDataStoreContentChangedListener>(env, jnotifyCallbackPointer);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFEATURE_1ID_1NONE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FEATURE_ID_NONE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFEATURESET_1ID_1NONE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FEATURESET_ID_NONE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFEATURE_1VERSION_1NONE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FEATURE_VERSION_NONE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFEATURESET_1VERSION_1NONE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FEATURESET_VERSION_NONE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getVISIBILITY_1SETTINGS_1FEATURE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::VISIBILITY_SETTINGS_FEATURE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getVISIBILITY_1SETTINGS_1FEATURESET
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::VISIBILITY_SETTINGS_FEATURESET;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1BULK_1MODIFICATIONS
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_BULK_MODIFICATIONS;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1INSERT
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_INSERT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1UPDATE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_UPDATE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1DELETE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_DELETE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1FEATURE_1INSERT
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_FEATURE_INSERT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1FEATURE_1UPDATE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_FEATURE_UPDATE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1FEATURE_1DELETE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_FEATURE_DELETE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1NAME
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_NAME;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURESET_1DISPLAY_1THRESHOLDS
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURESET_DISPLAY_THRESHOLDS;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURE_1NAME
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURE_NAME;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURE_1GEOMETRY
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURE_GEOMETRY;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURE_1STYLE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURE_STYLE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getMODIFY_1FEATURE_1ATTRIBUTES
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::MODIFY_FEATURE_ATTRIBUTES;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getTIMESTAMP_1NONE
  (JNIEnv *env, jclass clazz)
{
    return TE_TIMESTAMP_NONE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFIELD_1GEOMETRY
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::GeometryField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFIELD_1NAME
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::NameField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFIELD_1ATTRIBUTES
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::AttributesField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFIELD_1STYLE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::StyleField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getUPDATE_1ATTRIBUTES_1ADD_1OR_1REPLACE
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::UPDATE_ATTRIBUTESET_ADD_OR_REPLACE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getUPDATE_1ATTRIBUTES_1SET
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::UPDATE_ATTRIBUTESET_SET;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1IgnoreFields_1GeometryField
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::GeometryField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1IgnoreFields_1StyleField
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::StyleField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1IgnoreFields_1AttributesField
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::AttributesField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1IgnoreFields_1NameField
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::NameField;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1Resolution
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::Resolution;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1FeatureSet
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::FeatureSet;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1FeatureName
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::FeatureName;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1FeatureId
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::FeatureId;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1Distance
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::Distance;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1Order_1Type_1GeometryType
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::Order::GeometryType;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1SpatialOp_1Type_1Buffer
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::SpatialOp::Buffer;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureDataStore2_getFeatureQueryParamters_1SpatialOp_1Type_1Simplify
  (JNIEnv *env, jclass clazz)
{
    return FeatureDataStore2::FeatureQueryParameters::SpatialOp::Simplify;
}

namespace
{
    CallbackForwarder::CallbackForwarder(JNIEnv *env_, jobject impl_) NOTHROWS :
        impl(env_->NewGlobalRef(impl_))
    {}
    CallbackForwarder::~CallbackForwarder() NOTHROWS
    {
        if(impl) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
    void CallbackForwarder::onDataStoreContentChanged(FeatureDataStore2 &dataStore) NOTHROWS
    {
        if(!impl)
            return;
        const TAKErr code = JNINotifyCallback_eventOccurred(impl);
        if(code == TE_Done) {
            LocalJNIEnv env;
            env->DeleteGlobalRef(impl);
            impl = NULL;
        }
    }
}
