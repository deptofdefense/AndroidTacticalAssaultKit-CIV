#include "com_iai_pri_PRIJNI.h"

#include <iostream>
#include <vector>

#include "PRI.h"
#include "PRIJNIUtility.h"
#include "InputStreamReader.h"

using namespace prijni;

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    imageToGround
 * Signature: (J[Lcom/iai/pri/PRIImagePoint;)[Lcom/iai/pri/PRIGroundPoint;
 */
JNIEXPORT jobjectArray JNICALL Java_com_iai_pri_PRIJNI_imageToGround
  (JNIEnv *env, jclass o, jlong pointer, jobjectArray imagePoints){

    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);

    // Convert all of the Java objects into their C++ equivalents
    std::vector<PixelCoordinate> inputCoords;
    int imagePointListLength = env->GetArrayLength(imagePoints);
    for(int i = 0; i < imagePointListLength; i++){
          jobject point = env->GetObjectArrayElement(imagePoints, i);

          PixelCoordinate unwrappedCoord(
                    getDoubleField(env, &point, &priImagePointClass, "sample"),
                    getDoubleField(env, &point, &priImagePointClass, "line"));
          inputCoords.push_back(unwrappedCoord);
    }

    std::vector<ErrorCoordinate> outputCoords;
    // Perform imageToGround on all inputs.
    for(int i = 0; i < inputCoords.size(); i++){
        try{
            outputCoords.push_back(pri->imageToGround(inputCoords.at(i)));
        }catch(...){
            outputCoords.push_back(ErrorCoordinate(-DBL_MAX,-DBL_MAX,-DBL_MAX));
        }
    }

    return createGroundPointArray(env, outputCoords);
}

void groundToImage(JNIEnv *env, PRI* pri,
                        std::vector<PixelCoordinate>& outputCoords,
                        jobjectArray& groundPoints){

}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    groundToImage
 * Signature: (J[Lcom/iai/pri/PRIGroundPoint;)[Lcom/iai/pri/PRIImagePoint;
 */
JNIEXPORT jobjectArray JNICALL Java_com_iai_pri_PRIJNI_groundToImage
  (JNIEnv *env, jclass o, jlong pointer, jobjectArray groundPoints){

    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);

    // Convert all of the Java objects into their C++ equivalents
    std::vector<ErrorCoordinate> inputCoords;
    int groundPointListLength = env->GetArrayLength(groundPoints);
    for(int i = 0; i < groundPointListLength; i++){
        jobject point = env->GetObjectArrayElement(groundPoints, i);

        ErrorCoordinate unwrappedCoord(
            getDoubleField(env, &point, &priGroundPointClass, "lat"),
            getDoubleField(env, &point, &priGroundPointClass, "lon"),
            getDoubleField(env, &point, &priGroundPointClass, "eleMeters"),
            getDoubleField(env, &point, &priGroundPointClass, "le90Meters"),
            getDoubleField(env, &point, &priGroundPointClass, "ce90Meters"));
        inputCoords.push_back(unwrappedCoord);
    }

    // Perform groundToImage on all inputs.
    std::vector<PixelCoordinate> outputCoords;
    for(int i = 0; i < inputCoords.size(); i++){
        try{
            outputCoords.push_back(pri->groundToImage(inputCoords.at(i)));
        } catch(...){
            outputCoords.push_back(PixelCoordinate(-DBL_MAX, -DBL_MAX));
        }
    }

    return createImagePointArray(env, outputCoords);
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetTopToShadowTip
 * Signature: (JLcom/iai/pri/PRIImagePoint;Lcom/iai/pri/PRIImagePoint;)Lcom/iai/pri/PRIGroundPointWithHeightOffset;
 */
JNIEXPORT jobject JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetTopToShadowTip__JLcom_iai_pri_PRIImagePoint_2Lcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong pointer, jobject topImageCoord, jobject tipImageCoord) {
    
    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);
    PixelCoordinate top(getDoubleField(env, &topImageCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &topImageCoord, &priImagePointClass, "line"));

    PixelCoordinate tip(getDoubleField(env, &tipImageCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &tipImageCoord, &priImagePointClass, "line"));
                        
    // jdouble height = pri->getHeightOffsetTopToShadowTip(top, tip);
    DecimalCoordinate dc = pri->getHeightOffsetTopToShadowTip(top, tip);
    return createGroundPoint(env, dc); //height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetTopToShadowTip
 * Signature: (JLcom/iai/pri/PRIImagePoint;JLcom/iai/pri/PRIImagePoint;)Lcom/iai/pri/PRIGroundPointWithHeightOffset;
 */
JNIEXPORT jobject JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetTopToShadowTip__JLcom_iai_pri_PRIImagePoint_2JLcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong topCoordPriPtr, jobject topImageCoord, jlong shadowTipPriPtr, jobject tipImageCoord)
{
  
    PRI* topCoordPri = JLONG_TO_INTPTR(PRI, topCoordPriPtr);
    PRI* shadowTipCoordPri = JLONG_TO_INTPTR(PRI, shadowTipPriPtr);
    
    PixelCoordinate top(getDoubleField(env, &topImageCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &topImageCoord, &priImagePointClass, "line"));

    PixelCoordinate tip(getDoubleField(env, &tipImageCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &tipImageCoord, &priImagePointClass, "line"));
                        
    //jdouble height = topCoordPri->getHeightOffsetTopToShadowTip(top, shadowTipCoordPri, tip);
    DecimalCoordinate dc = topCoordPri->getHeightOffsetTopToShadowTip(top, shadowTipCoordPri, tip);
    return createGroundPoint(env, dc); // height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetBaseToShadowTip
 * Signature: (JLcom/iai/pri/PRIImagePoint;Lcom/iai/pri/PRIImagePoint;)D
 */
JNIEXPORT jdouble JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetBaseToShadowTip__JLcom_iai_pri_PRIImagePoint_2Lcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong pointer, jobject baseCoord, jobject shadowTipCoord)
{
    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);
    
    PixelCoordinate base(getDoubleField(env, &baseCoord, &priImagePointClass, "sample"),
                         getDoubleField(env, &baseCoord, &priImagePointClass, "line"));
    PixelCoordinate shadowTip(getDoubleField(env, &shadowTipCoord, &priImagePointClass, "sample"),
                              getDoubleField(env, &shadowTipCoord, &priImagePointClass, "line"));
                              
    jdouble height = pri->getHeightOffsetBaseToShadowTip(base, shadowTip);
    return height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetBaseToShadowTip
 * Signature: (JLcom/iai/pri/PRIImagePoint;JLcom/iai/pri/PRIImagePoint;)D
 */
JNIEXPORT jdouble JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetBaseToShadowTip__JLcom_iai_pri_PRIImagePoint_2JLcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong baseCoordPriPtr, jobject baseCoord, jlong shadowTipCoordPriPtr, jobject shadowTipCoord)
{
    PRI* baseCoordPri = JLONG_TO_INTPTR(PRI, baseCoordPriPtr);
    PRI* shadowTipCoordPri = JLONG_TO_INTPTR(PRI, shadowTipCoordPriPtr);
    
    PixelCoordinate base(getDoubleField(env, &baseCoord, &priImagePointClass, "sample"),
                         getDoubleField(env, &baseCoord, &priImagePointClass, "line"));
    PixelCoordinate shadowTip(getDoubleField(env, &shadowTipCoord, &priImagePointClass, "sample"),
                              getDoubleField(env, &shadowTipCoord, &priImagePointClass, "line"));
                              
    jdouble height = baseCoordPri->getHeightOffsetBaseToShadowTip(base, shadowTipCoordPri, shadowTip);
    return height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetBaseToTop
 * Signature: (JLcom/iai/pri/PRIImagePoint;Lcom/iai/pri/PRIImagePoint;)D
 */
JNIEXPORT jdouble JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetBaseToTop__JLcom_iai_pri_PRIImagePoint_2Lcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong pointer, jobject baseCoord, jobject topCoord) 
{
    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);
    
    PixelCoordinate base(getDoubleField(env, &baseCoord, &priImagePointClass, "sample"),
                         getDoubleField(env, &baseCoord, &priImagePointClass, "line"));
    PixelCoordinate top(getDoubleField(env, &topCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &topCoord, &priImagePointClass, "line"));
                              
    jdouble height = pri->getHeightOffsetBaseToTop(base, top);
    return height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getHeightOffsetBaseToTop
 * Signature: (JLcom/iai/pri/PRIImagePoint;JLcom/iai/pri/PRIImagePoint;)D
 */
JNIEXPORT jdouble JNICALL Java_com_iai_pri_PRIJNI_getHeightOffsetBaseToTop__JLcom_iai_pri_PRIImagePoint_2JLcom_iai_pri_PRIImagePoint_2
  (JNIEnv *env, jclass o, jlong baseCoordPriPtr, jobject baseCoord, jlong topCoordPriPtr, jobject topCoord)
{
    PRI* baseCoordPri = JLONG_TO_INTPTR(PRI, baseCoordPriPtr);
    PRI* topCoordPri = JLONG_TO_INTPTR(PRI, topCoordPriPtr);
    
    PixelCoordinate base(getDoubleField(env, &baseCoord, &priImagePointClass, "sample"),
                         getDoubleField(env, &baseCoord, &priImagePointClass, "line"));
    PixelCoordinate top(getDoubleField(env, &topCoord, &priImagePointClass, "sample"),
                        getDoubleField(env, &topCoord, &priImagePointClass, "line"));
                        
    jdouble height = baseCoordPri->getHeightOffsetBaseToTop(base, topCoordPri, top);
    return height;
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    getCorners
 * Signature: (J)Lcom/iai/pri/PRICorners;
 */
JNIEXPORT jobject JNICALL Java_com_iai_pri_PRIJNI_getCorners
  (JNIEnv *env, jclass o, jlong pointer){

    PRI* pri = JLONG_TO_INTPTR(PRI, pointer);

    int numLines = pri->getHeight();
    int numSamples = pri->getWidth();

    std::vector<ErrorCoordinate> outputCoords;
    try{
        outputCoords.push_back(pri->imageToGround(PixelCoordinate(0,0)));
        outputCoords.push_back(pri->imageToGround(PixelCoordinate(numSamples, 0)));
        outputCoords.push_back(pri->imageToGround(PixelCoordinate(numSamples, numLines)));
        outputCoords.push_back(pri->imageToGround(PixelCoordinate(0, numLines)));
    } catch(...){
        return NULL;
    }

    return createCornersObject(env, pri->getWidth(), pri->getHeight(), outputCoords);
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    isPRI
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_iai_pri_PRIJNI_isPRI__Ljava_lang_String_2
  (JNIEnv *env, jclass o, jstring path){
    string unwrappedPath = getStringVariable(env, &path);

    return PRI::isPRI(unwrappedPath.c_str());
}

jlong isPRIStream(JNIEnv* env, jobject* inputStream){
    // FIXME - reader needs to be heap allocated!!!
#if 0
    InputStreamReader reader(env, inputStream);
    PRI* pri = new PRI(&reader);

    if(!pri->getIsInitialized()){
        delete pri;
        return (jlong) -1;
    }

    return INTPTR_TO_JLONG(pri);
#else
    return -1LL;
#endif
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    isPRI
 * Signature: (Ljava/io/InputStream;)J
 */
JNIEXPORT jlong JNICALL Java_com_iai_pri_PRIJNI_isPRI__Ljava_io_InputStream_2
  (JNIEnv *env, jclass o, jobject stream){

    return isPRIStream(env, &stream);
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    loadImage
 * Signature: (Ljava/io/InputStream;)J
 */
JNIEXPORT jlong JNICALL Java_com_iai_pri_PRIJNI_loadImage__Ljava_io_InputStream_2
  (JNIEnv *env, jclass o, jobject inputStream){

    return isPRIStream(env, &inputStream);
}

/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    loadImage
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_iai_pri_PRIJNI_loadImage__Ljava_lang_String_2
  (JNIEnv *env, jclass o, jstring path){
    string unwrappedPath = getStringVariable(env, &path);

    if(!PRI::isPRI(unwrappedPath.c_str()))
        return (jlong)-1LL;

    PRI* pri = new PRI(unwrappedPath.c_str(), false);
    return INTPTR_TO_JLONG(pri);
}


/*
 * Class:     com_iai_pri_PRIJNI
 * Method:    clearImageCache
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_iai_pri_PRIJNI_clearImageCache
  (JNIEnv *env, jclass o, jlong pointer){
      if(pointer == -1LL){
          return;
      }

      PRI* pri = JLONG_TO_INTPTR(PRI, pointer);
      delete pri;
}

JNIEXPORT jdouble JNICALL Java_com_iai_pri_PRIJNI_getElevation
  (JNIEnv *env, jclass clazz, jlong pointer, double x, double y)
{
    if(pointer == -1LL)
        return NAN;

    PRI *pri = JLONG_TO_INTPTR(PRI, pointer);
    PixelCoordinate pixel(x, y);
    return pri->getElevation(pixel);
}

JNIEXPORT jint JNICALL Java_com_iai_pri_PRIJNI_getNumElevationSegments
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    if(pointer == -1LL)
        return 0;

    PRI *pri = JLONG_TO_INTPTR(PRI, pointer);
    return pri->getElevationSegmentDataCount();
}

JNIEXPORT jobject JNICALL Java_com_iai_pri_PRIJNI_getElevationSegmentData
  (JNIEnv *env, jclass clazz, jlong pointer, jint index)
{
    if(pointer == -1LL)
        return NULL;

    PRI *pri = JLONG_TO_INTPTR(PRI, pointer);
    if(index < 0 || index >= pri->getElevationSegmentDataCount())
        return NULL;
    return env->NewObject(ElevationSegmentData_class, ElevationSegmentData_ctor, INTPTR_TO_JLONG(&pri->getElevationSegmentData(index)));
}

