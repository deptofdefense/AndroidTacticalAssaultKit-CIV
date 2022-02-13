PROJ =			../../..
HERE =			src/raster/apass
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

APASS_INTERFACE =	$(APASS_INCLS:%=$(INTERFACE_DEST)/%)

APASS_INCLS =		ApassLayerInfoSpi.h

APASS_OBJS =		# Objects are included in raster library

APASS_JNI_OBJS =		

APASS_LIB =		

APASS_JNI_LIB =		

INTERFACES =		$(APASS_INTERFACE)

OBJS =			$(APASS_OBJS) \
			$(APASS_JNI_OBJS)

LIBS =			$(APASS_LIB) \
			$(APASS_JNI_LIB)

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

$(APASS_LIB):		$(APASS_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(APASS_JNI_LIB):	$(APASS_JNI_OBJS) $(APASS_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
