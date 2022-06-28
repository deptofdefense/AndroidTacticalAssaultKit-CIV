#ifndef PRIJNI_UTILITY_H
#define PRIJNI_UTILITY_H

#include <jni.h>

#include <string>
#include <vector>
#include "ErrorCoordinate.h"
#include "PixelCoordinate.h"
#include "PRI.h"

#define INTPTR_TO_JLONG(a)           (jlong)(intptr_t)(a)
#define JLONG_TO_INTPTR(type, var)   (type *)(intptr_t)(var)

extern jclass priGroundPointClass;
extern jclass priImagePointClass;
extern jclass priCornersClass;

extern jmethodID priGroundPointConstructor;
extern jmethodID priImagePointConstructor;
extern jmethodID priCornersConstructor;

extern jclass ElevationSegmentData_class;
extern jmethodID ElevationSegmentData_ctor;

extern jclass ElevationPoint_class;
extern jmethodID ElevationPoint_ctor;
extern jfieldID ElevationPoint_line;
extern jfieldID ElevationPoint_sample;
extern jfieldID ElevationPoint_elevation;
extern jfieldID ElevationPoint_ce90;
extern jfieldID ElevationPoint_le90;

namespace prijni {

std::string getStringVariable(JNIEnv *, jstring*);
double getDoubleField(JNIEnv *env, jobject* object, jclass* cls, const char* name);
jobjectArray createGroundPointArray(JNIEnv *env,
                                std::vector<iai::ErrorCoordinate> &coords);
jobject createCornersObject(JNIEnv *env, int width, int height,
                                std::vector<iai::ErrorCoordinate> &cornerCoords);
jobjectArray createImagePointArray(JNIEnv *env,
                                std::vector<iai::PixelCoordinate> &coords);
jobject createGroundPoint(JNIEnv *env, DecimalCoordinate &dc);

} //namespace prijni

#endif // PRIJNI_UTILITY_H
