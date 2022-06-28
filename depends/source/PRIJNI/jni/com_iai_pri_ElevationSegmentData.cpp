#include "com_iai_pri_ElevationSegmentData.h"

#include "PRI.h"
#include "ElevationManager.h"
#include "PRIJNIUtility.h"

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getSource
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_iai_pri_ElevationSegmentData_getSource
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    switch(data->dataSource) {
#define DATASOURCE_CASE(x) \
    case DataSource::x : return com_iai_pri_ElevationSegmentData_DATASOURCE_##x;

        DATASOURCE_CASE(DTED0)
        DATASOURCE_CASE(DTED1)
        DATASOURCE_CASE(DTED2)
        DATASOURCE_CASE(SRTM)
        DATASOURCE_CASE(DPPDB)
        DATASOURCE_CASE(LIDAR)
        DATASOURCE_CASE(DPSS)
        DATASOURCE_CASE(GXP)
        DATASOURCE_CASE(PLATES)
        DATASOURCE_CASE(UNKNOWN)
#undef DATASOURCE_CASE
        default :
            return com_iai_pri_ElevationSegmentData_DATASOURCE_UNKNOWN;

    }
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getDatum
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_iai_pri_ElevationSegmentData_getDatum
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    switch(data->elevationDatum) {
#define ELEVDATUM_CASE(x) \
    case ElevDatum::x : return com_iai_pri_ElevationSegmentData_ELEVATIONDATUM_##x;

        ELEVDATUM_CASE(HAE)
        ELEVDATUM_CASE(MSL84)
        ELEVDATUM_CASE(MSL96)
        ELEVDATUM_CASE(MSL08)
        ELEVDATUM_CASE(AGL)
        ELEVDATUM_CASE(UNKNOWN)
#undef ELEVDATUM_CASE
        default :
            return com_iai_pri_ElevationSegmentData_ELEVATIONDATUM_UNKNOWN;

    }
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getUnits
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_iai_pri_ElevationSegmentData_getUnits
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    switch(data->elevationUnits) {
#define ELEVUNITS_CASE(x) \
    case ElevUnits::x : return com_iai_pri_ElevationSegmentData_ELEVUNITS_##x;

        ELEVUNITS_CASE(METERS)
        ELEVUNITS_CASE(FEET)
        ELEVUNITS_CASE(DECIMETERS)
        ELEVUNITS_CASE(UNKNOWN)
#undef ELEVUNITS_CASE
        default :
            return com_iai_pri_ElevationSegmentData_ELEVUNITS_UNKNOWN;

    }
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getCompression
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_iai_pri_ElevationSegmentData_getCompression
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    switch(data->dataCompression) {
#define DATACOMPRESSION_CASE(x) \
    case DataCompression::x : return com_iai_pri_ElevationSegmentData_DATACOMPRESSION_##x;

        DATACOMPRESSION_CASE(GZIP)
        DATACOMPRESSION_CASE(NOCOMPRESSION)
        DATACOMPRESSION_CASE(UNKNOWN)
#undef DATACOMPRESSION_CASE
        default :
            return com_iai_pri_ElevationSegmentData_DATACOMPRESSION_UNKNOWN;

    }
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getNumPoints
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_iai_pri_ElevationSegmentData_getNumPoints
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    return data->getNumberofPoints();
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getPoint
 * Signature: (JI)Lcom/iai/pri/ElevationPoint;
 */
JNIEXPORT jobject JNICALL Java_com_iai_pri_ElevationSegmentData_getPoint
  (JNIEnv *env, jclass clazz, jlong pointer, int index)
{
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    ElevationPoint &point = data->getPoint(index);
    jobject retval = env->NewObject(ElevationPoint_class, ElevationPoint_ctor);
    env->SetIntField(retval, ElevationPoint_line, point.line);
    env->SetIntField(retval, ElevationPoint_sample, point.sample);
    env->SetDoubleField(retval, ElevationPoint_elevation, point.elevation);
    env->SetDoubleField(retval, ElevationPoint_ce90, point.ce90);
    env->SetDoubleField(retval, ElevationPoint_le90, point.le90);
    return retval;
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getPoints
 * Signature: (J[D)V
 */
JNIEXPORT void JNICALL Java_com_iai_pri_ElevationSegmentData_getPoints__J_3D
  (JNIEnv *env, jclass clazz, jlong pointer, jdoubleArray jarr)
{
    jdouble *arr = env->GetDoubleArrayElements(jarr, NULL);
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    for(int i = 0; i < data->getNumberofPoints(); i++) {
        ElevationPoint &point = data->getPoint(i);
        *arr++ = point.sample;
        *arr++ = point.line;
        *arr++ = point.elevation;
    }
    env->ReleaseDoubleArrayElements(jarr, arr, 0);
}

/*
 * Class:     com_iai_pri_ElevationSegmentData
 * Method:    getPoints
 * Signature: (J[F)V
 */
JNIEXPORT void JNICALL Java_com_iai_pri_ElevationSegmentData_getPoints__J_3F
  (JNIEnv *env, jclass clazz, jlong pointer, jfloatArray jarr)
{
    jfloat *arr = env->GetFloatArrayElements(jarr, NULL);
    ElevationSegmentData *data = JLONG_TO_INTPTR(ElevationSegmentData, pointer);
    for(int i = 0; i < data->getNumberofPoints(); i++) {
        ElevationPoint &point = data->getPoint(i);
        *arr++ = point.sample;
        *arr++ = point.line;
        *arr++ = point.elevation;
    }
    env->ReleaseFloatArrayElements(jarr, arr, 0);
}