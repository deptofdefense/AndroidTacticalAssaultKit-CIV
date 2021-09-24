PROJ =			../../..
HERE =			src/raster/osm
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

OSM_INTERFACE =		$(OSM_INCLS:%=$(INTERFACE_DEST)/%)

OSM_INCLS =		OSMUtils.h

OSM_OBJS =		# Objects are included in raster library

OSM_JNI_OBJS =		

OSM_LIB =		

OSM_JNI_LIB =		

INTERFACES =		$(OSM_INTERFACE)

OBJS =			$(OSM_OBJS) \
			$(OSM_JNI_OBJS)

LIBS =			$(OSM_LIB) \
			$(OSM_JNI_LIB)

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

$(OSM_LIB):		$(OSM_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(OSM_JNI_LIB):		$(OSM_JNI_OBJS) $(OSM_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
