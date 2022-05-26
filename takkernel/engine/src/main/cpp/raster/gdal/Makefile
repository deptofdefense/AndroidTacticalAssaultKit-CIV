PROJ =			../../..
HERE =			src/raster/gdal
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

GDAL_INTERFACE =	$(GDAL_INCLS:%=$(INTERFACE_DEST)/%)

GDAL_INCLS =		GdalDatasetProjection.h \
			GdalLayerInfo.h \
			GdalLibrary.h \
			RapidPositioningControlB.h

GDAL_OBJS =		# Objects are included in raster library

GDAL_JNI_OBJS =		

GDAL_LIB =		

GDAL_JNI_LIB =		

INTERFACES =		$(GDAL_INTERFACE)

OBJS =			$(GDAL_OBJS) \
			$(GDAL_JNI_OBJS)

LIBS =			$(GDAL_LIB) \
			$(GDAL_JNI_LIB)

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

$(GDAL_LIB):		$(GDAL_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(GDAL_JNI_LIB):	$(GDAL_JNI_OBJS) $(GDAL_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
