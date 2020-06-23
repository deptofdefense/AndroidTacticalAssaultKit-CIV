LOCAL_PATH := $(call my-dir)

LOCAL_SHORT_COMMANDS := true
TTP_DIST_DIR := $(LOCAL_PATH)/../../../../../../takthirdparty/builds/android-$(TARGET_ARCH_ABI)-release

include $(CLEAR_VARS)
LOCAL_MODULE 				:= sqlcipher
LOCAL_SRC_FILES 			:= $(TTP_DIST_DIR)/lib/libsqlite3.a
LOCAL_EXPORT_C_INCLUDES := $(TTP_DIST_DIR)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 				:= openssl
LOCAL_SRC_FILES 			:= $(TTP_DIST_DIR)/lib/libssl.a
LOCAL_EXPORT_C_INCLUDES := $(TTP_DIST_DIR)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE 				:= opensslcrypto
LOCAL_SRC_FILES 			:= $(TTP_DIST_DIR)/lib/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(TTP_DIST_DIR)/include
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE 			:= certdb
LOCAL_MODULE_FILENAME 	:= libcertdb
LOCAL_CPP_EXTENSION 	:= .cc
LOCAL_SRC_FILES 		:= $(wildcard $(LOCAL_PATH)/*.c)
LOCAL_C_INCLUDES 		:= $(LOCAL_PATH) $(MY_SUBDIRS:%=$(LOCAL_PATH)/include/%)
LOCAL_CFLAGS            := -std=c99
LOCAL_CPP_FEATURES 		:= exceptions
LOCAL_STATIC_LIBRARIES 	:= sqlcipher openssl opensslcrypto
LOCAL_LDLIBS 			:= -llog

##  EXPORTS for dependent modules

LOCAL_EXPORT_C_INCLUDES :=	$(LOCAL_PATH)/include \
				$(MY_SUBDIRS:%=$(LOCAL_PATH)/include/%)
LOCAL_EXPORT_LDLIBS :=		$(LOCAL_LDLIBS)

include $(BUILD_SHARED_LIBRARY)
