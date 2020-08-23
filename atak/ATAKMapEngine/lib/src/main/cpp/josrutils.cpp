#include "jglmapview.h"
#include "ogr_spatialref.h"
#include "ogr_api.h"
#include "ogr_core.h"

namespace {

/******************************************************************************/
// MACROS

#define GET_BUFFER_POINTER(t, buffer) \
        reinterpret_cast<t *>(env->GetDirectBufferAddress(buffer))

#define TO_RADIANS(v) ((v)*(M_PI/180.0))

#define JLONG_TO_INTPTR(arg)    (intptr_t)(arg)

#define INTPTR_TO_JLONG(arg)    (jlong)(intptr_t)(arg);

/******************************************************************************/
// TYPEDEFS

typedef struct coordinate_transform_t {
    void *opaque;

    void (*destroy)(void *opaque);
    int (*transform)(void *opaque, const double srcX, const double srcY, double *dstX, double *dstY);
} coordinate_transform_t;

/******************************************************************************/
// GLOBAL FUNCTIONS

void destroyOgrCoordinateTransform(void *opaque)
{
    OGRCoordinateTransformation *coordTransform = (OGRCoordinateTransformation *)opaque;
    OGRCoordinateTransformation::DestroyCT(coordTransform);
}

int transformOgrCoordinateTransform(void *opaque, const double srcX, const double srcY, double *dstX, double *dstY)
{
    OGRCoordinateTransformation *coordTransform = (OGRCoordinateTransformation *)opaque;
    *dstX = srcX;
    *dstY = srcY;
    coordTransform->Transform(1, dstX, dstY, NULL);
    // XXX - check returns
    return 1;
}

int transformWgs84(void *opaque, const double srcX, const double srcY, double *dstX, double *dstY)
{
    *dstX = srcX;
    *dstY = srcY;
    return 1;
}

int transformWebMercator(void *opaque, const double srcX, const double srcY, double *dstX, double *dstY)
{
    const double a = 6378137.0;

    *dstX = a * TO_RADIANS(srcX);
    *dstY = a * log(tan(M_PI / 4.0
                    + TO_RADIANS(srcY) / 2.0));

    return 1;
}

int createCoordTransform(coordinate_transform_t **transform, const int dstSrid)
{
    *transform = NULL;

    OGRSpatialReference dstSrs;
    OGRErr err = dstSrs.importFromEPSG(dstSrid);
    if(err != OGRERR_NONE)
        return -1;
    OGRSpatialReference wgs84Srs;
    err = wgs84Srs.importFromEPSG(4326);
    if(err != OGRERR_NONE)
        return -2;
    OGRCoordinateTransformation *impl =  OGRCreateCoordinateTransformation(&wgs84Srs, &dstSrs);
    if(!impl)
        return -3;

    *transform = new coordinate_transform_t();
    (*transform)->opaque = impl;
    (*transform)->destroy = destroyOgrCoordinateTransform;

    switch(dstSrid) {
        case 4326 : // wgs84
            (*transform)->transform = transformWgs84;
            break;
        case 3857 : // web-mercator
        case 900913 :
            (*transform)->transform = transformWebMercator;
            break;
        default :
            (*transform)->transform = transformOgrCoordinateTransform;
            break;
    }

    return 0;
}

void destroyCoordTransform(coordinate_transform_t *transform)
{
    if(transform->destroy)
        transform->destroy(transform->opaque);
    delete transform;
}

/**
 * Transforms a point from the source coordinate space to the destination
 * coordinate space.
 *
 * @param matrix    the 3x3 projective matrix
 * @param srcX      The x-coordinate for the source point
 * @param srcY      The y-coordinate for the source point
 * @param dstX      Returns the x-coordinate for the destination point
 * @param dstY      Returns the y-coordinate for the destination point
 */
void transform(const jdouble *matrix, const double srcX, const double srcY, double *dstX, double *dstY)
{
    double dstW;

    *dstX = srcX * matrix[0] + srcY * matrix[1] + matrix[2];
    *dstY = srcX * matrix[3] + srcY * matrix[4] + matrix[5];
    dstW = srcX * matrix[6] + srcY * matrix[7] + matrix[8];

    *dstX = (*dstX) / dstW;
    *dstY = (*dstY) / dstW;
}

template<typename T>
void forwardSRSImpl(coordinate_transform_t *transform, const T *src, jfloat *dst, const int count)
{
    void *opaque = transform->opaque;

    int idx = 0;
    double x, y;
    for(int i = 0; i < count; i++) {
        transform->transform(opaque, src[idx], src[idx+1], &x, &y);

        dst[idx++] = (float)x;
        dst[idx++] = (float)y;
    }
}

template<typename T>
void forwardSRSImpl(coordinate_transform_t *coordTransform, const jdouble *matrix, const T *src, jfloat *dst, const int count)
{
    void *opaque = coordTransform->opaque;

    int idx = 0;
    double x, y;
    for(int i = 0; i < count; i++) {
        coordTransform->transform(opaque, src[idx], src[idx+1], &x, &y);

        transform(matrix, x, y, &x, &y);

        dst[idx++] = (float)x;
        dst[idx++] = (float)y;
    }
}


coordinate_transform_t *createProjectionImpl
  (const int dstSrid)
{
    coordinate_transform_t *retval = NULL;
    if(createCoordTransform(&retval, dstSrid) != 0) {
        if(retval)
            destroyCoordTransform(retval);
        return NULL;
    }
    return retval;
}

};

JNIEXPORT jlong JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_createProjection
  (JNIEnv *env, jclass clazz, jint srid)
{
    return INTPTR_TO_JLONG(createProjectionImpl(srid));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_destroyProjection
  (JNIEnv *env, jclass clazz, jlong nativePtr)
{
    coordinate_transform_t *transform = (coordinate_transform_t *)JLONG_TO_INTPTR(nativePtr);
    destroyCoordTransform(transform);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__ILjava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__ILjava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__I_3DLjava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jdoubleArray jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jdouble *matrix = env->GetDoubleArrayElements(jpostTransform, NULL);
    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, matrix, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    env->ReleaseDoubleArrayElements(jpostTransform, matrix, JNI_ABORT);

    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__I_3DLjava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jdoubleArray jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jdouble *matrix = env->GetDoubleArrayElements(jpostTransform, NULL);
    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, matrix, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    env->ReleaseDoubleArrayElements(jpostTransform, matrix, JNI_ABORT);

    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__ILjava_nio_DoubleBuffer_2Ljava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jobject jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jdouble *matrix = GET_BUFFER_POINTER(jdouble, jpostTransform);
    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, matrix, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__ILjava_nio_DoubleBuffer_2Ljava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jint srid, jobject jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    jdouble *matrix = GET_BUFFER_POINTER(jdouble, jpostTransform);
    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    int err;

    err = 0;

    coordinate_transform_t *coordTransform = createProjectionImpl(srid);
    if(!coordTransform) {
        err = -1;
        goto done;
    }

    forwardSRSImpl(coordTransform, matrix, src, dst, count);
    destroyCoordTransform(coordTransform);

done :
    return err;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__JLjava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jtransformPtr, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;

    forwardSRSImpl(coordTransform, src, dst, count);

    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__JLjava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jtransformPtr, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;

    forwardSRSImpl(coordTransform, src, dst, count);

    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__J_3DLjava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jtransformPtr, jdoubleArray jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    jdouble *matrix = env->GetDoubleArrayElements(jpostTransform, NULL);

    forwardSRSImpl(coordTransform, matrix, src, dst, count);

    env->ReleaseDoubleArrayElements(jpostTransform, matrix, JNI_ABORT);

    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__J_3DLjava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jtransformPtr, jdoubleArray jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    jdouble *matrix = env->GetDoubleArrayElements(jpostTransform, NULL);

    forwardSRSImpl(coordTransform, matrix, src, dst, count);

    env->ReleaseDoubleArrayElements(jpostTransform, matrix, JNI_ABORT);

    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplF__JLjava_nio_DoubleBuffer_2Ljava_nio_FloatBuffer_2ILjava_nio_FloatBuffer_2II
    (JNIEnv *env, jclass clazz, jlong jtransformPtr, jobject jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jfloat *src = GET_BUFFER_POINTER(jfloat, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    jdouble *matrix = GET_BUFFER_POINTER(jdouble, jpostTransform);

    forwardSRSImpl(coordTransform, matrix, src, dst, count);

    return 0;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLMapView_00024OsrUtils_forwardImplD__JLjava_nio_DoubleBuffer_2Ljava_nio_DoubleBuffer_2ILjava_nio_FloatBuffer_2II
  (JNIEnv *env, jclass clazz, jlong jtransformPtr, jobject jpostTransform, jobject jsrc, jint srcOff, jobject jdst, jint dstOff, jint count)
{
    coordinate_transform_t *coordTransform = (coordinate_transform_t *)JLONG_TO_INTPTR(jtransformPtr);

    jdouble *src = GET_BUFFER_POINTER(jdouble, jsrc) + srcOff;
    jfloat *dst = GET_BUFFER_POINTER(jfloat, jdst) + dstOff;
    jdouble *matrix = GET_BUFFER_POINTER(jdouble, jpostTransform);

    forwardSRSImpl(coordTransform, matrix, src, dst, count);

    return 0;
}
