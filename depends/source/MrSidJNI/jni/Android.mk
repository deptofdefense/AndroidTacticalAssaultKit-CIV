LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
DSDK            := mrsid/

LOCAL_MODULE    := ltidsdk 
LOCAL_SRC_FILES := $(DSDK)/lib/$(TARGET_ARCH_ABI)/libltidsdk.so

include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := MrsidLibrary
LOCAL_SRC_FILES := MrsidLibrary.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(DSDK)/include
LOCAL_CFLAGS := -fstack-protector-all

LOCAL_SHARED_LIBRARIES = ltidsdk
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
