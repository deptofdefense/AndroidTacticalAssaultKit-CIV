#include "jgeometry.h"

#include <memory>
#include <sstream>

#include <feature/Feature2.h>
#include <feature/Geometry2.h>
#include <feature/GeometryCollection2.h>
#include <feature/GeometryFactory.h>
#include <feature/LegacyAdapters.h>
#include <feature/LineString2.h>
#include <feature/Point2.h>
#include <feature/Polygon2.h>
#include <util/DataOutput2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIDoubleArray.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

/*****************************************************************************/
// Geometry

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getTEGC_1Point
  (JNIEnv *env, jclass clazz)
{
    return TEGC_Point;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getTEGC_1LineString
  (JNIEnv *env, jclass clazz)
{
    return TEGC_LineString;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getTEGC_1Polygon
  (JNIEnv *env, jclass clazz)
{
    return TEGC_Polygon;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getTEGC_1GeometryCollection
  (JNIEnv *env, jclass clazz)
{
    return TEGC_GeometryCollection;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_destroy
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<Geometry2>(env, jpointer);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_equals
  (JNIEnv *env, jclass clazz, jlong aptr, jlong bptr)
{
    Geometry2 *a = JLONG_TO_INTPTR(Geometry2, aptr);
    Geometry2 *b = JLONG_TO_INTPTR(Geometry2, bptr);

    if(a->getDimension() != b->getDimension())
        return false;
    if(a->getClass() != b->getClass())
        return false;
    return *a == *b;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_clone
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    if(!ptr)
        return NULL;
    const Geometry2 *geom = JLONG_TO_INTPTR(Geometry2, ptr);
    Geometry2Ptr retval(NULL, NULL);
    TAKErr code = Geometry_clone(retval, *geom);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getEnvelope
  (JNIEnv *env, jclass clazz, jobject jpointer, jdoubleArray jmbb)
{
    TAKErr code(TE_Ok);

    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Envelope2 envelope;
    code = geom->getEnvelope(&envelope);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;

    jdouble *mbb = env->GetDoubleArrayElements(jmbb, NULL);
    mbb[0] = envelope.minX;
    mbb[1] = envelope.minY;
    mbb[2] = envelope.minZ;
    mbb[3] = envelope.maxX;
    mbb[4] = envelope.maxY;
    mbb[5] = envelope.maxZ;
    env->ReleaseDoubleArrayElements(jmbb, mbb, 0);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getDimension
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    return geom->getDimension();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_setDimension
  (JNIEnv *env, jclass clazz, jobject jpointer, jint dim)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code = geom->setDimension(dim);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_getGeometryClass
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    return geom->getClass();
}

/*****************************************************************************/
// Point

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1create__DD
  (JNIEnv *env, jclass clazz, jdouble x, jdouble y)
{
    Geometry2Ptr cpoint(new Point2(x, y), Memory_deleter_const<Geometry2, Point2>);
    return NewPointer(env, std::move(cpoint));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1create__DDD
  (JNIEnv *env, jclass clazz, jdouble x, jdouble y, jdouble z)
{
    Geometry2Ptr cpoint(new Point2(x, y, z), Memory_deleter_const<Geometry2, Point2>);
    return NewPointer(env, std::move(cpoint));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1getX
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    const Point2 &point = static_cast<Point2 &>(*geom);
    return point.x;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1getY
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    const Point2 &point = static_cast<Point2 &>(*geom);
    return point.y;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1getZ
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    const Point2 &point = static_cast<Point2 &>(*geom);
    return point.z;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1set__Lcom_atakmap_interop_Pointer_2DD
  (JNIEnv *env, jclass clazz, jobject jpointer, jdouble x, jdouble y)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Point2 &point = static_cast<Point2 &>(*geom);
    point.x = x;
    point.y = y;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Point_1set__Lcom_atakmap_interop_Pointer_2DDD
  (JNIEnv *env, jclass clazz, jobject jpointer, jdouble x, jdouble y, jdouble z)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    Point2 &point = static_cast<Point2 &>(*geom);
    point.x = x;
    point.y = y;
    point.z = z;
}

/*****************************************************************************/
// Linestring

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1create
  (JNIEnv *env, jclass clazz, jint dim)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr clinestring(new LineString2(), Memory_deleter_const<Geometry2, LineString2>);
    code = clinestring->setDimension(dim);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(clinestring));
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1getNumPoints
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }

    LineString2 &linestring = static_cast<LineString2 &>(*geom);
    return linestring.getNumPoints();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1isClosed
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    LineString2 &linestring = static_cast<LineString2 &>(*geom);
    bool retval;
    code = linestring.isClosed(&retval);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1getX
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    LineString2 &linestring = static_cast<LineString2 &>(*geom);
    double retval;
    code = linestring.getX(&retval, idx);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1getY
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    LineString2 &linestring = static_cast<LineString2 &>(*geom);
    double retval;
    code = linestring.getY(&retval, idx);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1getZ
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx)
{
    Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
    if(!geom) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    LineString2 &linestring = static_cast<LineString2 &>(*geom);
    double retval;
    code = linestring.getZ(&retval, idx);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return retval;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1addPoint__Lcom_atakmap_interop_Pointer_2DD
  (JNIEnv *env, jclass clazz, jobject jpointer, jdouble x, jdouble y)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.addPoint(x, y);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1addPoint__Lcom_atakmap_interop_Pointer_2DDD
  (JNIEnv *env, jclass clazz, jobject jpointer, jdouble x, jdouble y, jdouble z)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.addPoint(x, y, z);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1addPoints
  (JNIEnv *env, jclass clazz, jobject jpointer, jdoubleArray jarr, jint off, jint count, jint stride)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  JNIDoubleArray arr(*env, jarr, JNI_ABORT);
  jdouble *pts = arr;

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.addPoints(reinterpret_cast<double *>(pts)+off, count, stride);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1setX
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx, jdouble x)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.setX(idx, x);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1setY
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx, jdouble y)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.setY(idx, y);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Linestring_1setZ
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx, jdouble z)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  TAKErr code(TE_Ok);
  LineString2 &linestring = static_cast<LineString2 &>(*geom);
  code = linestring.setZ(idx, z);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return;
}

/*****************************************************************************/
// Polygon

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1create
  (JNIEnv *env, jclass clazz, jint dim)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cpolygon(new Polygon2(), Memory_deleter_const<Geometry2, Polygon2>);
    code = cpolygon->setDimension(dim);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(cpolygon));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1setExteriorRing
  (JNIEnv *env, jclass clazz, jobject jpolyptr, jobject jringptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpolyptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  LineString2 *ring = static_cast<LineString2 *>(Pointer_get<Geometry2>(env, jringptr));
  if(!ring) {
    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    return NULL;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  TAKErr code(TE_Ok);
  std::shared_ptr<LineString2> retval;
  code = polygon.getExteriorRing(retval);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  retval->clear();
  code = retval->setDimension(ring->getDimension());
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;
  if(retval->getDimension() == 2) {
      for(std::size_t i = 0u; i < ring->getNumPoints(); i++) {
          Point2 p(0, 0);
          code = ring->get(&p, i);
          TE_CHECKBREAK_CODE(code);
          code = retval->addPoint(p.x, p.y);
          TE_CHECKBREAK_CODE(code);
      }
      if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
  } else if(retval->getDimension() == 3) {
      for(std::size_t i = 0u; i < ring->getNumPoints(); i++) {
        Point2 p(0, 0, 0);
        code = ring->get(&p, i);
        TE_CHECKBREAK_CODE(code);
        code = retval->addPoint(p.x, p.y, p.z);
        TE_CHECKBREAK_CODE(code);
      }
      if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
  } else {
      ATAKMapEngineJNI_checkOrThrow(env, TE_IllegalState);
      return NULL;
  }

  std::shared_ptr<Geometry2> bretval = std::static_pointer_cast<Geometry2>(retval);
  return NewPointer(env, bretval);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1addInteriorRing
  (JNIEnv *env, jclass clazz, jobject jpolyptr, jobject jringptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpolyptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  LineString2 *ring = static_cast<LineString2 *>(Pointer_get<Geometry2>(env, jringptr));
  if(!ring) {
    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    return NULL;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  const std::size_t ringIdx = polygon.getNumInteriorRings();

  TAKErr code(TE_Ok);
  code = polygon.addInteriorRing(*ring);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return NULL;

  std::shared_ptr<LineString2> retval;
  code = polygon.getInteriorRing(retval, ringIdx);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  std::shared_ptr<Geometry2> bretval = std::static_pointer_cast<Geometry2>(retval);
  return NewPointer(env, bretval);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1removeInteriorRing
  (JNIEnv *env, jclass clazz, jobject jpolyptr, jobject jringptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpolyptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return false;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);

  LineString2 *ring = static_cast<LineString2 *>(Pointer_get<Geometry2>(env, jringptr));
  if(!ring) {
    return false;
  }

  TAKErr code(TE_Ok);
  code = polygon.removeInteriorRing(*ring);
  if(code == TE_Ok)
    return true;
  else if(code == TE_InvalidArg)
    return false;

  ATAKMapEngineJNI_checkOrThrow(env, code);
  return false;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1containsInteriorRing
  (JNIEnv *env, jclass clazz, jobject jpolyptr, jobject jringptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpolyptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return false;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);

  LineString2 *ring = static_cast<LineString2 *>(Pointer_get<Geometry2>(env, jringptr));

  TAKErr code(TE_Ok);
  const std::size_t numInteriorRings = polygon.getNumInteriorRings();
  for(std::size_t i = 0u; i < numInteriorRings; i++) {
    std::shared_ptr<LineString2> test;
    code = polygon.getInteriorRing(test, i);
    TE_CHECKBREAK_CODE(code);

    if(test.get() == ring)
      return true;
  }
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return false;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1clear
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  polygon.clear();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1clearInteriorRings
  (JNIEnv * env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  TAKErr code(TE_Ok);
  const std::size_t numInteriorRings = polygon.getNumInteriorRings();
  for(std::size_t i = 0u; i < numInteriorRings; i++) {
    code = polygon.removeInteriorRing(i);
    TE_CHECKBREAK_CODE(code);
  }
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1getExteriorRing
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  TAKErr code(TE_Ok);
  std::shared_ptr<LineString2> ring;
  code = polygon.getExteriorRing(ring);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  std::shared_ptr<Geometry2> bretval = std::static_pointer_cast<Geometry2>(ring);
  return NewPointer(env, bretval);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1getNumInteriorRings
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return 0;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  return polygon.getNumInteriorRings();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_Polygon_1getInteriorRing
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  Polygon2 &polygon = static_cast<Polygon2 &>(*geom);
  TAKErr code(TE_Ok);
  std::shared_ptr<LineString2> ring;
  code = polygon.getInteriorRing(ring, idx);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  std::shared_ptr<Geometry2> bretval = std::static_pointer_cast<Geometry2>(ring);
  return NewPointer(env, bretval);
}

/*****************************************************************************/
// GeometryCollection

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1create
  (JNIEnv *env, jclass clazz, jint dim)
{
    TAKErr code(TE_Ok);
    Geometry2Ptr cgeom(new GeometryCollection2(), Memory_deleter_const<Geometry2, GeometryCollection2>);
    code = cgeom->setDimension(dim);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(cgeom));
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1add
  (JNIEnv *env, jclass clazz, jobject jcollectionptr, jobject jchildptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jcollectionptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  Geometry2 *child = Pointer_get<Geometry2>(env, jchildptr);
  if(!child) {
    ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
    return NULL;
  }

  GeometryCollection2 &collection = static_cast<GeometryCollection2 &>(*geom);
  const std::size_t childIdx = collection.getNumGeometries();

  TAKErr code(TE_Ok);
  code = collection.addGeometry(*child);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
      return NULL;

  std::shared_ptr<Geometry2> retval;
  code = collection.getGeometry(retval, childIdx);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  return NewPointer(env, retval);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1remove
  (JNIEnv *env, jclass clazz, jobject jcollectionptr, jobject jchildptr)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jcollectionptr);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return false;
  }

  GeometryCollection2 &collection = static_cast<GeometryCollection2 &>(*geom);

  Geometry2 *child = Pointer_get<Geometry2>(env, jchildptr);
  if(!child) {
    return false;
  }

  TAKErr code(TE_Ok);
  code = collection.removeGeometry(*child);
  if(code == TE_Ok)
    return true;
  else if(code == TE_InvalidArg)
    return false;

  ATAKMapEngineJNI_checkOrThrow(env, code);
  return false;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1getNumChildren
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return 0;
  }

  GeometryCollection2 &collection = static_cast<GeometryCollection2 &>(*geom);
  return collection.getNumGeometries();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1getChild
  (JNIEnv *env, jclass clazz, jobject jpointer, jint idx)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return NULL;
  }

  GeometryCollection2 &collection = static_cast<GeometryCollection2 &>(*geom);
  TAKErr code(TE_Ok);
  std::shared_ptr<Geometry2> child;
  code = collection.getGeometry(child, idx);
  if(ATAKMapEngineJNI_checkOrThrow(env, code))
    return NULL;

  return NewPointer(env, child);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_Geometry_GeometryCollection_1clear
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
  Geometry2 *geom = Pointer_get<Geometry2>(env, jpointer);
  if(!geom) {
      ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
      return;
  }

  GeometryCollection2 &collection = static_cast<GeometryCollection2 &>(*geom);
  collection.clear();
}

