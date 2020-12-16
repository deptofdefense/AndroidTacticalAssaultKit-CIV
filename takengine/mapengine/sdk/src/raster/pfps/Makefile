PROJ =			../../..
HERE =			src/raster/pfps
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

PFPS_INTERFACE =	$(PFPS_INCLS:%=$(INTERFACE_DEST)/%)

PFPS_INCLS =		PfpsLayerInfoSpi.h \
			PfpsMapType.h \
			PfpsMapTypeFrame.h \
			PfpsUtils.h

PFPS_OBJS =		# Objects are included in raster library

PFPS_JNI_OBJS =		

PFPS_LIB =		

PFPS_JNI_LIB =		

INTERFACES =		$(PFPS_INTERFACE)

OBJS =			$(PFPS_OBJS) \
			$(PFPS_JNI_OBJS)

LIBS =			$(PFPS_LIB) \
			$(PFPS_JNI_LIB)

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

$(PFPS_LIB):		$(PFPS_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(PFPS_JNI_LIB):	$(PFPS_JNI_OBJS) $(PFPS_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
