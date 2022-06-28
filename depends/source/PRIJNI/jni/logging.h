/*
 * logging.h
 *
 *  Created on: Dec 1, 2016
 *      Author: stengolics
 */

#ifndef JNI_LOGGING_H_
#define JNI_LOGGING_H_

#include <android/log.h>

#define LOG_TAG "PRI_JNI_DEBUG"

#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#endif /* JNI_LOGGING_H_ */
