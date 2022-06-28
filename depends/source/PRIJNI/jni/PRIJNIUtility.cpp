#include "PRIJNIUtility.h"

using namespace std;
using namespace iai;


jclass priGroundPointClass;
jclass priImagePointClass;
jclass priCornersClass;

jmethodID priGroundPointConstructor;
jmethodID priImagePointConstructor;
jmethodID priCornersConstructor;

jclass ElevationSegmentData_class;
jmethodID ElevationSegmentData_ctor;

jclass ElevationPoint_class;
jmethodID ElevationPoint_ctor;
jfieldID ElevationPoint_line;
jfieldID ElevationPoint_sample;
jfieldID ElevationPoint_elevation;
jfieldID ElevationPoint_ce90;
jfieldID ElevationPoint_le90;

// JNI Initialization Functions

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv* env;
	if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK){
		return JNI_ERR;
	}

	priGroundPointClass = (jclass) env->NewWeakGlobalRef(
								env->FindClass("com/iai/pri/PRIGroundPoint"));
	if(!priGroundPointClass){
		return JNI_ERR;
	}

	priImagePointClass = (jclass) env->NewWeakGlobalRef(
								env->FindClass("com/iai/pri/PRIImagePoint"));
	if(!priImagePointClass){
		return JNI_ERR;
	}

	priCornersClass = (jclass) env->NewWeakGlobalRef(
								env->FindClass("com/iai/pri/PRICorners"));
	if(!priCornersClass){
		return JNI_ERR;
	}

	priGroundPointConstructor = env->GetMethodID(priGroundPointClass,
												"<init>", "(DDDDD)V");
	if(!priGroundPointConstructor){
		return JNI_ERR;
	}

	priImagePointConstructor = env->GetMethodID(priImagePointClass,
												"<init>", "(DD)V");
	if(!priImagePointConstructor){
		return JNI_ERR;
	}

	priCornersConstructor = env->GetMethodID(priCornersClass,
		"<init>", "(IILcom/iai/pri/PRIGroundPoint;Lcom/iai/pri/PRIGroundPoint;Lcom/iai/pri/PRIGroundPoint;Lcom/iai/pri/PRIGroundPoint;)V");
	if(!priCornersConstructor){
		return JNI_ERR;
	}

    ElevationSegmentData_class = env->FindClass("com/iai/pri/ElevationSegmentData");
    if(!ElevationSegmentData_class)
        return JNI_ERR;
    ElevationSegmentData_class = (jclass)env->NewWeakGlobalRef(ElevationSegmentData_class);

    ElevationSegmentData_ctor = env->GetMethodID(ElevationSegmentData_class, "<init>", "(J)V");
    if(!ElevationSegmentData_ctor)
        return JNI_ERR;

    ElevationPoint_class = env->FindClass("com/iai/pri/ElevationPoint");
    if(!ElevationPoint_class)
        return JNI_ERR;
    ElevationPoint_class = (jclass)env->NewWeakGlobalRef(ElevationPoint_class);

    ElevationPoint_ctor = env->GetMethodID(ElevationPoint_class, "<init>", "()V");
    if(!ElevationPoint_ctor)
        return JNI_ERR;

    ElevationPoint_line = env->GetFieldID(ElevationPoint_class, "line", "I");
    if(!ElevationPoint_line)
        return JNI_ERR;
    ElevationPoint_sample = env->GetFieldID(ElevationPoint_class, "sample", "I");
    if(!ElevationPoint_sample)
        return JNI_ERR;
    ElevationPoint_elevation = env->GetFieldID(ElevationPoint_class, "elevation", "D");
    if(!ElevationPoint_elevation)
        return JNI_ERR;
    ElevationPoint_ce90 = env->GetFieldID(ElevationPoint_class, "ce90", "D");
    if(!ElevationPoint_ce90)
        return JNI_ERR;
    ElevationPoint_le90 = env->GetFieldID(ElevationPoint_class, "le90", "D");
    if(!ElevationPoint_le90)
        return JNI_ERR;

	return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved){
	JNIEnv* env;
	if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK){
		return;
	}

	env->DeleteWeakGlobalRef(priGroundPointClass);
	env->DeleteWeakGlobalRef(priImagePointClass);
	env->DeleteWeakGlobalRef(priCornersClass);
	env->DeleteWeakGlobalRef(ElevationSegmentData_class);
	env->DeleteWeakGlobalRef(ElevationPoint_class);
}


namespace prijni {


// Unwraps a String Variable and returns a std::string equivalent.
string getStringVariable(JNIEnv *env, jstring* stringobject){
	const char* value = env->GetStringUTFChars(*stringobject, NULL);
	if(value == NULL){
		return NULL;
	}

	string str(value);
	env->ReleaseStringUTFChars(*stringobject, value);
	return str;
}

// Unwraps a double variable and returns a regular c++ double.
double getDoubleField(JNIEnv *env,
							jobject* object, jclass* cls, const char* name){
	jfieldID fid = env->GetFieldID(*cls, name, "D");

	if(fid == NULL){
		return -999.0;
	}

	jdouble jvalue = env->GetDoubleField(*object,fid);

	return jvalue;
}

// Convert an array of C++ ErrorCoordinates into Java PRIGroundPoints to be
// returned over the JNI interface.
jobjectArray createGroundPointArray(JNIEnv *env, vector<ErrorCoordinate> &coords){
	// Create a new java array to store the result and fill it with Java
	// objects containing the same data as the points passed as an argument.
	jobjectArray returnArray = env->NewObjectArray(
                                        (jsize) coords.size(),
                                        priGroundPointClass, NULL);
	if(returnArray == NULL){
		return NULL;
	}

	for(int i = 0; i < coords.size(); i++){
		if(coords.at(i).getLat() == -DBL_MAX ||
				coords.at(i).getLon() == -DBL_MAX ||
				coords.at(i).getElev() == -DBL_MAX){
		    env->SetObjectArrayElement(returnArray, i , NULL);
			continue;
		}

        jobject wrapped = env->NewObject(priGroundPointClass,
								priGroundPointConstructor,
                                coords.at(i).getLat(), coords.at(i).getLon(),
                                coords.at(i).getElev(),
                                coords.at(i).getCE(),
                                coords.at(i).getLE());
		env->SetObjectArrayElement(returnArray, i, wrapped);
	}

    return returnArray;
}

jobject createCornersObject(JNIEnv *env, int width, int height,
								vector<ErrorCoordinate> &cornerCoords){
	// Convert the corners
	jobject jul = env->NewObject(priGroundPointClass, priGroundPointConstructor,
							cornerCoords.at(0).getLat(),
							cornerCoords.at(0).getLon(),
							cornerCoords.at(0).getElev(),
							cornerCoords.at(0).getCE(),
							cornerCoords.at(0).getLE());

	jobject jur = env->NewObject(priGroundPointClass, priGroundPointConstructor,
							cornerCoords.at(1).getLat(),
							cornerCoords.at(1).getLon(),
							cornerCoords.at(1).getElev(),
							cornerCoords.at(1).getCE(),
							cornerCoords.at(1).getLE());

	jobject jlr = env->NewObject(priGroundPointClass, priGroundPointConstructor,
							cornerCoords.at(2).getLat(),
							cornerCoords.at(2).getLon(),
							cornerCoords.at(2).getElev(),
							cornerCoords.at(2).getCE(),
							cornerCoords.at(2).getLE());

	jobject jll = env->NewObject(priGroundPointClass, priGroundPointConstructor,
							cornerCoords.at(3).getLat(),
							cornerCoords.at(3).getLon(),
							cornerCoords.at(3).getElev(),
							cornerCoords.at(3).getCE(),
							cornerCoords.at(3).getLE());

	jobject result = env->NewObject(priCornersClass, priCornersConstructor,
							width, height, jul, jur, jlr, jll);

	return result;
}

// Convert an array of C++ PixelCoordinates into Java PRIImagePoints to be
// passed over the JNI interface.
jobjectArray createImagePointArray(JNIEnv *env, vector<PixelCoordinate> &coords){
    // Create a java array of PRIGroundPoints and fill it with Java objects
	// containing the same data as the points passed as an argument.
    jobjectArray returnArray = env->NewObjectArray(
                                        (jsize) coords.size(),
                                        priImagePointClass, NULL);
    if(returnArray == NULL){
        return NULL;
    }

    for(int i = 0; i < coords.size(); i++){
		if(coords.at(i).getY() == -DBL_MAX ||
			coords.at(i).getX() == -DBL_MAX){
			env->SetObjectArrayElement(returnArray, i , NULL);
		}

        jobject wrapped = env->NewObject(priImagePointClass,
								priImagePointConstructor,
                                coords.at(i).getY(), coords.at(i).getX());
        env->SetObjectArrayElement(returnArray, i, wrapped);
    }

    return returnArray;
}

jobject createGroundPoint(JNIEnv *env, DecimalCoordinate &dc) {

    jobject jGp = env->NewObject(priGroundPointClass, priGroundPointConstructor,
							     dc.getLat(), dc.getLon(), dc.getElev(),
							     0.0, 0.0);
    return jGp;
}

} // namespace prijni
