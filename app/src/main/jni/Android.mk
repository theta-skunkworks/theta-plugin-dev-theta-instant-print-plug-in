LOCAL_PATH := $(call my-dir)
PROJECT_ROOT := $(LOCAL_PATH)/../../../..

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
include $(PROJECT_ROOT)/opencv/native/jni/OpenCV.mk

LOCAL_MODULE := imgconv
LOCAL_SRC_FILES := imgconv.cpp
include $(BUILD_SHARED_LIBRARY)
