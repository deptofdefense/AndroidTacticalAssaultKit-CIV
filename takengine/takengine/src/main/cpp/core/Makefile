PROJ =			../..
HERE =			src/core
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

CORE_INTERFACE =	$(CORE_INCLS:%=$(INTERFACE_DEST)/%)

CORE_INCLS =		AbstractLayer.h \
			AtakMapController.h \
			AtakMapView.h \
			Datum.h \
			Ellipsoid.h \
			GeoPoint.h \
			Layer.h \
			MapCamera.h \
			MapSceneModel.h \
			MultiLayer.h \
			MultiLayerImpl.h \
			Projection.h \
			ProjectionFactory.h \
			ProjectionSpi.h \
			ProxyLayer.h \
			ProxyLayerImpl.h \
			Service.h \
			ServiceManagerImpl.h

CORE_OBJS =		AbstractLayer.o \
			AtakMapController.o \
			AtakMapView.o \
			Datum.o \
			Ellipsoid.o \
			MapSceneModel.o \
			MultiLayerImpl.o \
			ProjectionFactory.o \
			ProxyLayerImpl.o \
			ServiceManagerImpl.o

CORE_JNI_OBJS =		

CORE_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineCore$(SHLIB_EXT)

CORE_JNI_LIB =		

INTERFACES =		$(CORE_INTERFACE)

OBJS =			$(CORE_OBJS) \
			$(CORE_JNI_OBJS)

LIBS =			$(CORE_LIB) \
			$(CORE_JNI_LIB)

.PHONY:			all \
			clean \
			install \
			interfaces \
			libs
     
all:			interfaces \
			libs

$(INTERFACE_DEST):
	mkdir -p $@

$(LIB_DEST):
	mkdir -p $@

$(INTERFACE_DEST)/%.hh:	%.hh	| $(INTERFACE_DEST)
	- cd $(INTERFACE_DEST) && $(INCLUDE_CP) $(^:%=$(PROJ)/$(HERE)/%) .

$(INTERFACE_DEST)/%.h:	%.h	| $(INTERFACE_DEST)
	- cd $(INTERFACE_DEST) && $(INCLUDE_CP) $(^:%=$(PROJ)/$(HERE)/%) .

$(CORE_LIB):		$(CORE_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		-lMapEngineMath -lThreads

$(CORE_JNI_LIB):	$(CORE_JNI_OBJS) $(CORE_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)