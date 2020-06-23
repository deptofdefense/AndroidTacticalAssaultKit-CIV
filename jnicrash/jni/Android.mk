LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

TTP := ../../libunwindstack-ndk

LOCAL_MODULE := unw
LOCAL_SRC_FILES := ../libunwind/$(TARGET_ARCH_ABI)/libunwindstack.a

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := lzma
LOCAL_SRC_FILES := ../libunwind/$(TARGET_ARCH_ABI)/lzma/liblzma.a

include $(PREBUILT_STATIC_LIBRARY)



include $(CLEAR_VARS)

LOCAL_MODULE := jnicrash

LOCAL_ARM_MODE := arm
LOCAL_CPPFLAGS := -O0 -fno-rtti 

LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/$(TTP)/include

LOCAL_STATIC_LIBRARIES := unw lzma
LOCAL_LDLIBS :=  -lz -llog

LOCAL_CPPFLAGS +=-fno-strict-overflow -fstack-protector-all -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2
LOCAL_LDFLAGS := -Wl,-z,relro -Wl,-z,now

LOCAL_SRC_FILES := $(notdir $(wildcard $(LOCAL_PATH)/*.cpp))

include $(BUILD_SHARED_LIBRARY)
