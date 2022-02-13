#include <jni.h>

#include "lt_fileSpec.h"
#include "lt_utilStatusStrings.h"

#include "lti_pixel.h"
#include "lti_scene.h"
#include "lti_sceneBuffer.h"
#include "lti_geoCoord.h"
#include "lti_utils.h"

#include "MrSIDImageReader.h"

#include "lti_dynamicRangeFilter.h"
#include "lti_bandSelectFilter.h"

#include <assert.h>
#include <stdlib.h>

#include <android/log.h>
#include <GLES/gl.h>

LT_USING_LIZARDTECH_NAMESPACE

#define DEBUG(...) __android_log_print(ANDROID_LOG_DEBUG, "MrSID JNI", __VA_ARGS__);

struct NativeData {
	RC<LTIImageStage> image;

	RC<LTIImageStage> reader;

	int layout;

	NativeData(void) :
			image(NULL), reader(NULL) {
	}
};

#define SetupClassInfo(obj) jclass clazz = env->GetObjectClass(obj)

#define GetJavaField(obj, type, sig, name) env->Get##type##Field((obj), env->GetFieldID(clazz, (name), (sig)))
#define SetJavaField(obj, type, sig, name, value) env->Set##type##Field((obj), env->GetFieldID(clazz, (name), (sig)), (value))

#define GetNativeData(obj) static_cast<NativeData *>(reinterpret_cast<void *>(GetJavaField((obj), Long, "J", "nativeData")))
#define SetNativeData(obj, nativeData) SetJavaField((obj), Long, "J", "nativeData", reinterpret_cast<jlong>(static_cast<void *>((nativeData))))

static jobject doGetImageInfo(JNIEnv *env, LTIImageStage *image, jobject path) {

	DEBUG("doGetImageInfo()");
	const LTIPixel &pixelProps = image->getPixelProps();

	jint width = image->getWidth();
	jint height = image->getHeight();
	jint numLevels = LTIUtils::magToLevel(image->getMinMagnification());
	jint numBands = pixelProps.getNumBands();
	jboolean hasAlphaBand = pixelProps.hasAlphaBand() || pixelProps.hasPreMultipliedAlphaBand();
	jdoubleArray geoTransform = NULL;
	jstring wkt = NULL;

	if (!image->isGeoCoordImplicit()) {
		const LTIGeoCoord &geoCoord = image->getGeoCoord();

		double trans[6] = {
				geoCoord.getXRes(),
				geoCoord.getYRot(),
				geoCoord.getXRot(),
				geoCoord.getYRes(),
				geoCoord.getX(),
				geoCoord.getY(),
		};

		geoTransform = env->NewDoubleArray(6);
		env->SetDoubleArrayRegion(geoTransform, 0, 6, trans);

		if (geoCoord.getWKT() != NULL) {
			wkt = env->NewStringUTF(geoCoord.getWKT());
		}
	}

	jclass clazz = env->FindClass("com/lizardtech/android/mrsid/MrsidImageInfo");
	if (clazz != NULL) {
		jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/io/File;IIIIZ[DLjava/lang/String;)V");
		if (constructor != NULL) {
			return env->NewObject(clazz, constructor, path, width, height, numLevels, numBands, hasAlphaBand, geoTransform, wkt);
		} else {
			DEBUG("bad MrsidImageInfo constructor");
		}
	} else {
		DEBUG("could not find com/lizardtech/android/mrsid/MrsidImageInfo");
	}
	return NULL;
}

static bool getFileSpec(JNIEnv *env, jobject file, LTFileSpec &fileSpec)
{
	//jclass clazz = env->FindClass("java/io/File");
	jclass clazz = env->GetObjectClass(file);
	if(clazz != NULL)
	{
		jmethodID getPath = env->GetMethodID(clazz, "getAbsolutePath", "()Ljava/lang/String;");
		if(getPath != NULL)
		{
			jstring abspath = (jstring)env->CallObjectMethod(file, getPath);
			if(abspath != NULL)
			{
				const char *filepath = env->GetStringUTFChars(abspath, NULL);
				if(filepath != NULL)
				{
					fileSpec = LTFileSpec(filepath, LTFileSpec::UTF8);
					env->ReleaseStringUTFChars(abspath, filepath);
					return true;
				} else {
					DEBUG("could not get the File.getAbsolutePath() string");
				}
			} else {
				DEBUG("could not call File.getAbsolutePath()");
			}
		} else {
			DEBUG("could not find File.getAbsolutePath()");
		}
	} else {
		DEBUG("could not find java/io/File");
	}
	return false;
}

static jobject doOpenImage(JNIEnv *env, jobject file, RC<MrSIDImageReader> &mrsid) {

	DEBUG("doOpenImage()");
	LT_STATUS sts;
	if (mrsid != NULL) {

		LTFileSpec fileSpec;
		if(getFileSpec(env, file, fileSpec))
		{
			if (LT_SUCCESS(sts = mrsid->initialize(fileSpec, true))) {
				return doGetImageInfo(env, mrsid, file);
			} else {
				// TODO: throw ???? error (look up status code)
				// or log an error
			}
		} else {
			// TODO: throw bad arg
		}
	} else {
		// TODO: throw out of memory
	}
	return NULL;
}

/*
 * Class:     com_lizardtech_android_mrsid_MrsidImageReaderImp
 * Method:    getImageInfo
 * Signature: (Ljava/io/File;)Lcom/lizardtech/android/mrsid/MrsidMapData;
 */
extern "C" JNIEXPORT jobject JNICALL Java_com_lizardtech_android_mrsid_MrsidImageReaderImp_getImageInfo(JNIEnv *env, jclass clazz, jobject file) {

	DEBUG("getImageInfo()");
	RC<MrSIDImageReader> mrsid;
	return doOpenImage(env, file, mrsid);
}

/*
 * Class:     com_lizardtech_android_mrsid_MrsidImageReaderImp
 * Method:    init
 * Signature: (Ljava/io/File;)Lcom/lizardtech/android/mrsid/MrsidMapData;
 */
extern "C" JNIEXPORT jobject JNICALL Java_com_lizardtech_android_mrsid_MrsidImageReaderImp_init(JNIEnv *env, jobject obj, jobject file) {

	LT_STATUS sts;
	DEBUG("init()");
	SetupClassInfo(obj);
	jobject imageInfo = NULL;
	RC<MrSIDImageReader> mrsid;
	if ((imageInfo = doOpenImage(env, file, mrsid)) != NULL) {
		NativeData *self = new NativeData;
		SetNativeData(obj, self);

		self->reader = mrsid;
		self->image = mrsid;


		if (self->image->getNoDataPixel() != NULL && !self->image->getPixelProps().hasAlphaBand()) {
			// the public sdk does not have LTIAddAlphaFilter
//			sts = LT_STS_OutOfMemory;
//			RC<LTIAddAlphaFilter> filter;
//			if(filter != NULL && LT_SUCCESS(sts = filter->initialize(self->image)) && LT_SUCCESS(sts = filter->setMode(LTIAddAlphaFilter::FromNoDataThreshold))) {
//				self->image = filter;
//			} else {
//			}
		}

		if (self->image->getPixelProps().getDataType() != LTI_DATATYPE_UINT8 || !self->image->isNaturalDynamicRange()) {
			sts = LT_STS_OutOfMemory;

			const LTIPixel &drmin = self->image->getMinDynamicRange();
			const LTIPixel &drmax = self->image->getMaxDynamicRange();

			RC<LTIDynamicRangeFilter> filter;
			if(filter != NULL && LT_SUCCESS(sts = filter->initialize(self->image, &drmin,  &drmax, LTI_DATATYPE_UINT8))) {
				self->image = filter;
			} else {
				// error
			}
		}
		{
			const LTIPixel &pixelProps = self->image->getPixelProps();
			lt_uint16 numBands = pixelProps.getNumBands();
			lt_uint16 bandMap[4] = { 0, 1, 2, numBands - 1 };
			LTIColorSpace colorspace = LTI_COLORSPACE_INVALID;
			if (pixelProps.getNumBandsWithoutAlpha() < 3) {
				if (pixelProps.hasAlphaBand()) {
					bandMap[1] = numBands - 1;
					numBands = 2;
					self->layout = GL_LUMINANCE_ALPHA;
					colorspace = LTI_COLORSPACE_GRAYSCALEA_PM;
				} else {
					numBands = 1;
					self->layout = GL_LUMINANCE;
					colorspace = LTI_COLORSPACE_GRAYSCALE;
				}
			} else {
				if (pixelProps.hasAlphaBand()) {
					numBands = 4;
					self->layout = GL_RGBA;
					colorspace = LTI_COLORSPACE_RGBA_PM;
				} else {
					numBands = 3;
					self->layout = GL_RGB;
					colorspace = LTI_COLORSPACE_RGB;
				}
			}

			if (numBands != pixelProps.getNumBands()) {
				sts = LT_STS_OutOfMemory;
				RC<LTIBandSelectFilter> filter;
				if(filter != NULL && LT_SUCCESS(sts = filter->initialize(self->image, bandMap, numBands, colorspace))) {
					self->image = filter;
				} else {
					// error
				}
			}
		}
	}
	return imageInfo;
}

/*
 * Class:     com_lizardtech_android_mrsid_MrsidImageReaderImp
 * Method:    destroy
 * Signature: ()V
 */
extern "C" JNIEXPORT void JNICALL Java_com_lizardtech_android_mrsid_MrsidImageReaderImp_destroy(JNIEnv *env, jobject obj) {

	SetupClassInfo(obj);
	delete GetNativeData(obj);
	SetNativeData(obj, NULL);
}

/*
 * Class:     com_lizardtech_android_mrsid_MrsidImageReaderImp
 * Method:    decode
 * Signature: (DDDDI)Lcom/lizardtech/android/mrsid/MrsidTileData;
 */
extern "C" JNIEXPORT jobject JNICALL Java_com_lizardtech_android_mrsid_MrsidImageReaderImp_decode(JNIEnv *env, jobject obj, jdouble xmin, jdouble xmax, jdouble ymin, jdouble ymax, jint level) {

	LT_STATUS sts;
	SetupClassInfo(obj);
	NativeData *self = GetNativeData(obj);
	if (self != NULL) {
		DEBUG("decode(%f => %f, %f => %f @ %d) begin", xmin, xmax, ymin, ymax, level);

		const double mag = LTIUtils::levelToMag(level);
		lt_uint32 levelWidth, levelHeight;
		self->image->getDimsAtMag(mag, levelWidth, levelHeight);

		xmin = LT_MAX(floor(xmin), 0);
		xmax = LT_MIN(ceil(xmax), levelWidth);

		ymin = LT_MAX(floor(ymin), 0);
		ymax = LT_MIN(ceil(ymax), levelHeight);

		if (xmin < xmax && ymin < ymax) {
			LTIScene scene(xmin, ymin, xmax - xmin, ymax - ymin, mag);
			const lt_uint32 width = scene.getNumCols();
			const lt_uint32 height = scene.getNumRows();

			const LTIPixel &pixelProps = self->image->getPixelProps();
			lt_uint16 numBands = pixelProps.getNumBands();

			LTISceneBuffer buffer(pixelProps, width, height, NULL);
			if (LT_SUCCESS(sts = self->image->read(scene, buffer))) {

				jclass clazz = env->FindClass("com/lizardtech/android/mrsid/MrsidTileData");
				if(clazz != NULL) {
					jmethodID constructor = env->GetMethodID(clazz, "<init>", "(IIIII)V");
					if(constructor != NULL) {
						// 4 byte align the pixels for OpenGL
						const jint rowbytes = (sizeof(lt_uint8) * width * numBands + 0x3) & ~0x3;
						jobject tileData = env->NewObject(clazz, constructor, width, height, self->layout, GL_UNSIGNED_BYTE, rowbytes * height);
						if(tileData != NULL) {
							jobject pixelBuffer = GetJavaField(tileData, Object, "Ljava/nio/ByteBuffer;", "pixels");
							if(pixelBuffer != NULL) {
								void *pixels = env->GetDirectBufferAddress(pixelBuffer);
								if(pixels != NULL) {
									for (jint k = 0; k < numBands; k += 1) {
										lt_uint8 *src = static_cast<lt_uint8 *>(buffer.getBandData(k));
										lt_uint8 *dst = static_cast<lt_uint8 *>(pixels) + k;

										for(jint j = 0; j < height; j += 1, dst += rowbytes) {
											for(jint i = 0; i < width * numBands; i += numBands, src += 1)
												dst[i] = *src;
										}
									}
									// env->ReleaseDirectBufferAddress(pixelBuffer, pixels);
									return tileData;
								} else {
									DEBUG("could not get the pixel data");
								}
							} else {
								DEBUG("could not get the pixel buffer");
							}
						} else {
							DEBUG("could not create TileData");
						}
					} else {
						DEBUG("bad MrsidTileData constructor");
					}
				} else {
					DEBUG("could not find com/lizardtech/android/mrsid/MrsidTileData");
				}
			} else {
				// TODO: throw bad decode
				DEBUG("decode(%f => %f, %f => %f @ %d) => error %d (%s)", xmin, xmax, ymin, ymax, level, sts, getRawStatusString(sts));
			}
		} else {
			DEBUG("decode(%f => %f, %f => %f @ %d) => empty scene", xmin, xmax, ymin, ymax, level);
		}
	} else {
		// TODO: throw bad state
		DEBUG("decode(%f => %f, %f => %f @ %d) => bad state", xmin, xmax, ymin, ymax, level);
	}

	return NULL;
}
