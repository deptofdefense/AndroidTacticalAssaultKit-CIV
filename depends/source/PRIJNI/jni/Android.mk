LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := pri
LOCAL_SRC_FILES := $(LOCAL_PATH)/../pri-android/android-$(TARGET_ARCH_ABI)-release/lib/libpri.so
LOCAL_EXPORT_C_INCLUDES := \
  ../pri-android/android-$(TARGET_ARCH_ABI)-release/include/pri/coord \
  ../pri-android/android-$(TARGET_ARCH_ABI)-release/include/pri/PaganAPI \
  ../pri-android/android-$(TARGET_ARCH_ABI)-release/include/pri/newmat \
  ../pri-android/android-$(TARGET_ARCH_ABI)-release/include/pri/nitf \
  ../pri-android/android-$(TARGET_ARCH_ABI)-release/include/pri/utils

include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -Duse_namespace -DCOMMONCPP_COORD_EXPORTS
LOCAL_MODULE := prijni
LOCAL_LDLIBS := -lz -llog

LOCAL_CPP_FEATURES := exceptions

LOCAL_SRC_FILES := PRIJNIUtility.cpp \
                   com_iai_pri_PRIJNI.cpp \
                   com_iai_pri_ElevationSegmentData.cpp \
                   InputStreamReader.cpp

LOCAL_SHARED_LIBRARIES := pri


include $(BUILD_SHARED_LIBRARY)
