#include "jgeometryfactory.h"

#include <feature/GeometryFactory.h>
#include <util/DataInput2.h>
#include <util/DataOutput2.h>
#include <interop/java/JNILocalRef.h>
#include <feature/Point2.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIIntArray.h"
#include "interop/feature/Interop.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    class ENGINE_API NullDataOutput : public DataOutput2
    {
        public:
            NullDataOutput() NOTHROWS;

            virtual TAKErr close() NOTHROWS;

            virtual TAKErr write(const uint8_t *buf, const std::size_t len) NOTHROWS;
            virtual TAKErr writeByte(const uint8_t value) NOTHROWS;
            virtual TAKErr skip(const std::size_t n) NOTHROWS;

            std::size_t getNumWritten() NOTHROWS;
            TAKErr reset() NOTHROWS;
        private:
            std::size_t written;
    };
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_computeSpatiaLiteBlobSize
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    NullDataOutput output;
    code = GeometryFactory_toSpatiaLiteBlob(output, *cgeom, 4326);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return output.getNumWritten();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_toSpatiaLiteBlob__JI_3BIIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jint srid, jbyteArray mbuf, jint off, jint len, jboolean be)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    JNIByteArray jbuf(*env, mbuf, 0);
    MemoryOutput2 output;
    code = output.open(jbuf.get<uint8_t>()+off, len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    // XXX - need to add endian support
    code = GeometryFactory_toSpatiaLiteBlob(output, *cgeom, srid);
    if(code != TE_Ok) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    std::size_t remaining;
    code = output.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return len-remaining;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_toSpatiaLiteBlob__JIJIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jint srid, jlong bufPtr, jint len, jboolean be)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    MemoryOutput2 output;
    code = output.open(JLONG_TO_INTPTR(uint8_t, bufPtr), len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    // XXX - need to add endian support
    code = GeometryFactory_toSpatiaLiteBlob(output, *cgeom, srid);
    if(code != TE_Ok) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    std::size_t remaining;
    code = output.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return len-remaining;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_parseSpatiaLiteBlob___3BII_3I_3I
  (JNIEnv *env, jclass clazz, jbyteArray mbuf, jint off, jint len, jintArray msrid, jintArray mnumRead)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom(nullptr, nullptr);
    int srid;
    JNIByteArray jbuf(*env, mbuf, 0);
    MemoryInput2 input;
    code = input.open(jbuf.get<const uint8_t>(), len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    code = GeometryFactory_fromSpatiaLiteBlob(cgeom, &srid, input);
    if(code != TE_Ok)
        return NULL;
    std::size_t remaining;
    code = input.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cgeom.get())
        return NULL;
    JNIIntArray jnumRead(*env, mnumRead, 0);
    jnumRead[0] = (len-remaining);
    JNIIntArray jsrid(*env, msrid, 0);
    jsrid[0] = srid;

    return Feature::Interop_create(env, *cgeom);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_parseSpatiaLiteBlob__JI_3I_3I
  (JNIEnv *env, jclass clazz, jlong ptr, jint len, jintArray msrid, jintArray mnumRead)
{
    TAKErr code(TE_Ok);
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Geometry2Ptr cgeom(nullptr, nullptr);
    int srid;
    MemoryInput2 input;
    code = input.open(JLONG_TO_INTPTR(uint8_t, ptr), len);
    if(code != TE_Ok)
        return NULL;
    code = GeometryFactory_fromSpatiaLiteBlob(cgeom, &srid, input);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    std::size_t remaining;
    code = input.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cgeom.get())
        return NULL;
    JNIIntArray jnumRead(*env, mnumRead, 0);
    jnumRead[0] = (len-remaining);
    JNIIntArray jsrid(*env, msrid, 0);
    jsrid[0] = srid;

    return Feature::Interop_create(env, *cgeom);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_computeWkbSize
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    NullDataOutput output;
    code = GeometryFactory_toWkb(output, *cgeom);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return output.getNumWritten();
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_toWkb__J_3BIIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jbyteArray mbuf, jint off, jint len, jboolean be)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    JNIByteArray jbuf(*env, mbuf, 0);
    MemoryOutput2 output;
    code = output.open(jbuf.get<uint8_t>()+off, len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    code = GeometryFactory_toWkb(output, *cgeom, be ? TE_BigEndian : TE_LittleEndian);
    if(code != TE_Ok) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    std::size_t remaining;
    code = output.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return len-remaining;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_toWkb__JJIZ
  (JNIEnv *env, jclass clazz, jlong ptr, jlong bufPtr, jint len, jboolean be)
{
    TAKErr code(TE_Ok);
    Geometry2 *cgeom = JLONG_TO_INTPTR(Geometry2, ptr);
    if(!cgeom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    MemoryOutput2 output;
    code = output.open(JLONG_TO_INTPTR(uint8_t, bufPtr), len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    code = GeometryFactory_toWkb(output, *cgeom, be ? TE_BigEndian : TE_LittleEndian);
    if(code != TE_Ok) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    std::size_t remaining;
    code = output.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0;
    return len-remaining;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_parseWkb___3BII_3I
  (JNIEnv *env, jclass clazz, jbyteArray mbuf, jint off, jint len, jintArray mnumRead)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom(nullptr, nullptr);
    JNIByteArray jbuf(*env, mbuf, 0);
    MemoryInput2 input;
    code = input.open(jbuf.get<const uint8_t>(), len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    code = GeometryFactory_fromWkb(cgeom, input);
    if(code != TE_Ok)
        return NULL;
    std::size_t remaining;
    code = input.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cgeom.get())
        return NULL;
    JNIIntArray jnumRead(*env, mnumRead, 0);
    jnumRead[0] = (len-remaining);

    return Feature::Interop_create(env, *cgeom);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_parseWkb__JI_3I
  (JNIEnv *env, jclass clazz, jlong ptr, jint len, jintArray mnumRead)
{
    TAKErr code(TE_Ok);
    if(!ptr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Geometry2Ptr cgeom(nullptr, nullptr);
    int srid;
    MemoryInput2 input;
    code = input.open(JLONG_TO_INTPTR(uint8_t, ptr), len);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    code = GeometryFactory_fromWkb(cgeom, input);
    if(code != TE_Ok)
        return NULL;
    std::size_t remaining;
    code = input.remaining(&remaining);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    if(!cgeom.get())
        return NULL;
    JNIIntArray jnumRead(*env, mnumRead, 0);
    jnumRead[0] = (len-remaining);

    return Feature::Interop_create(env, *cgeom);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_createEllipse__JDDDI
        (JNIEnv *env, jclass clazz, jlong locationPtr, jdouble orientation, jdouble major, jdouble minor, jint algoPtr)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);

    const TAK::Engine::Feature::Point2 *cgeomFeatlocationPtr = JLONG_TO_INTPTR(
            TAK::Engine::Feature::Point2, locationPtr);
    TAK::Engine::Math::Point2<double> *cgeomlocationPtr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatlocationPtr->x, cgeomFeatlocationPtr->y, cgeomFeatlocationPtr->z);

    TAK::Engine::Renderer::Algorithm alg = TAK::Engine::Renderer::Tessellate_WGS84Algorithm();
    if (algoPtr == 0)
    {
        alg = TAK::Engine::Renderer::Tessellate_CartesianAlgorithm();
    }
    code = GeometryFactory_createEllipse(cgeom2Value, *cgeomlocationPtr, orientation, major, minor, alg);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);

}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_createEllipse__DDDDI
  (JNIEnv *env, jclass clazz, jdouble minX, jdouble minY, jdouble maxX, jdouble maxY, jint algoPtr)
  {
       TAKErr code(TE_Ok);
        Geometry2Ptr cgeom2Value(nullptr, nullptr);

        TAK::Engine::Feature::Envelope2 *cgeomEnvelope =
              new TAK::Engine::Feature::Envelope2(minX, minY, maxX, maxY);

        TAK::Engine::Renderer::Algorithm alg = TAK::Engine::Renderer::Tessellate_WGS84Algorithm();
        if (algoPtr == 0)
        {
            alg = TAK::Engine::Renderer::Tessellate_CartesianAlgorithm();
        }
        code = GeometryFactory_createEllipse(cgeom2Value, *cgeomEnvelope, alg);
        if(code != TE_Ok)
            return NULL;

        if(!cgeom2Value.get())
            return NULL;


        return Feature::Interop_create(env, *cgeom2Value);
  }

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_createRectangle__JJI
        (JNIEnv *env, jclass clazz, jlong corner1Ptr, jlong corner2Ptr, jint algoPtr)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);

    const TAK::Engine::Feature::Point2 *cgeomFeatcorner1Ptr = JLONG_TO_INTPTR(
            TAK::Engine::Feature::Point2, corner1Ptr);
    TAK::Engine::Math::Point2<double> *cgeomcorner1Ptr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatcorner1Ptr->x, cgeomFeatcorner1Ptr->y, cgeomFeatcorner1Ptr->z);

    const TAK::Engine::Feature::Point2 *cgeomFeatcorner2Ptr = JLONG_TO_INTPTR(TAK::Engine::Feature::Point2, corner2Ptr);
    TAK::Engine::Math::Point2<double> *cgeomcorner2Ptr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatcorner2Ptr->x, cgeomFeatcorner2Ptr->y, cgeomFeatcorner2Ptr->z);

    TAK::Engine::Renderer::Algorithm alg = TAK::Engine::Renderer::Tessellate_WGS84Algorithm();
    if (algoPtr == 0)
    {
        alg = TAK::Engine::Renderer::Tessellate_CartesianAlgorithm();
    }
    code = GeometryFactory_createRectangle(cgeom2Value, *cgeomcorner1Ptr, *cgeomcorner2Ptr, alg);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_createRectangle__JJJI
        (JNIEnv *env, jclass clazz, jlong point1Ptr, jlong point2Ptr, jlong point3Ptr, jint algoPtr)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);

    const TAK::Engine::Feature::Point2 *cgeomFeatpoint1Ptr = JLONG_TO_INTPTR(
            TAK::Engine::Feature::Point2, point1Ptr);
    TAK::Engine::Math::Point2<double> *cgeompoint1Ptr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatpoint1Ptr->x, cgeomFeatpoint1Ptr->y, cgeomFeatpoint1Ptr->z);
    const TAK::Engine::Feature::Point2 *cgeomFeatpoint2Ptr = JLONG_TO_INTPTR(TAK::Engine::Feature::Point2, point2Ptr);
    TAK::Engine::Math::Point2<double> *cgeompoint2Ptr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatpoint2Ptr->x, cgeomFeatpoint2Ptr->y, cgeomFeatpoint2Ptr->z);

    const TAK::Engine::Feature::Point2 *cgeomFeatpoint3Ptr = JLONG_TO_INTPTR(TAK::Engine::Feature::Point2, point3Ptr);
    TAK::Engine::Math::Point2<double> *cgeompoint3Ptr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatpoint3Ptr->x, cgeomFeatpoint3Ptr->y, cgeomFeatpoint3Ptr->z);

    TAK::Engine::Renderer::Algorithm alg = TAK::Engine::Renderer::Tessellate_WGS84Algorithm();
    if (algoPtr == 0)
    {
        alg = TAK::Engine::Renderer::Tessellate_CartesianAlgorithm();
    }
    code = GeometryFactory_createRectangle(cgeom2Value, *cgeompoint1Ptr, *cgeompoint2Ptr, *cgeompoint3Ptr, alg);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_createRectangle__JDDDI
        (JNIEnv *env, jclass clazz, jlong locationPtr, jdouble orientation, jdouble length, jdouble width, jint algoPtr)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);

    const TAK::Engine::Feature::Point2 *cgeomFeatlocationPtr = JLONG_TO_INTPTR(TAK::Engine::Feature::Point2, locationPtr);
    TAK::Engine::Math::Point2<double> *cgeomlocationPtr =
            new TAK::Engine::Math::Point2<double>(cgeomFeatlocationPtr->x, cgeomFeatlocationPtr->y, cgeomFeatlocationPtr->z);

    TAK::Engine::Renderer::Algorithm alg = TAK::Engine::Renderer::Tessellate_WGS84Algorithm();
    if (algoPtr == 0)
    {
        alg = TAK::Engine::Renderer::Tessellate_CartesianAlgorithm();
    }
    code = GeometryFactory_createRectangle(cgeom2Value, *cgeomlocationPtr, orientation, length, width, alg);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_extrudeConstant
        (JNIEnv *env, jclass clazz, jlong ptr, jdouble extrude, jint hint)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);
    Geometry2 *cgeomSrc = JLONG_TO_INTPTR(Geometry2, ptr);

    if(!cgeomSrc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    code = GeometryFactory_extrude(cgeom2Value, *cgeomSrc, extrude, hint);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_GeometryFactory_extrudePerVertex
        (JNIEnv *env, jclass clazz, jlong ptr, jdoubleArray extrude, jint hint)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom2Value(nullptr, nullptr);
    Geometry2 *cgeomSrc = JLONG_TO_INTPTR(Geometry2, ptr);

    if(!cgeomSrc) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    if(!extrude) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIDoubleArray jarr(*env, extrude, JNI_ABORT);
    code = GeometryFactory_extrude(cgeom2Value, *cgeomSrc, jarr.get<const double>(), jarr.length(), hint);
    if(code != TE_Ok)
        return NULL;

    if(!cgeom2Value.get())
        return NULL;

    return Feature::Interop_create(env, *cgeom2Value);
}

namespace
{
    NullDataOutput::NullDataOutput() NOTHROWS :
        written(0u)
    {}
    TAKErr NullDataOutput::close() NOTHROWS
    {
        return TE_Ok;
    }
    TAKErr NullDataOutput::write(const uint8_t *buf, const std::size_t len) NOTHROWS
    {
        written += len;
        return TE_Ok;
    }
    TAKErr NullDataOutput::writeByte(const uint8_t value) NOTHROWS
    {
        written++;
        return TE_Ok;
    }
    TAKErr NullDataOutput::skip(const std::size_t n) NOTHROWS
    {
        written += n;
        return TE_Ok;
    }
    std::size_t NullDataOutput::getNumWritten() NOTHROWS
    {
        return written;
    }
    TAKErr NullDataOutput::reset() NOTHROWS
    {
        written = 0u;
        return TE_Ok;
    }
}
