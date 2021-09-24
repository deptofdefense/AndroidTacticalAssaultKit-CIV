PROJ =			../../..
HERE =			src/raster/tilereader
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

TILE_INTERFACE =	$(TILE_INCLS:%=$(INTERFACE_DEST)/%)

TILE_INCLS =		TileReader.h

TILE_OBJS =		# Objects are included in raster library

TILE_JNI_OBJS =		

TILE_LIB =		

TILE_JNI_LIB =		

INTERFACES =		$(TILE_INTERFACE)

OBJS =			$(TILE_OBJS) \
			$(TILE_JNI_OBJS)

LIBS =			$(TILE_LIB) \
			$(TILE_JNI_LIB)

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

$(TILE_LIB):		$(TILE_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(TILE_JNI_LIB):	$(TILE_JNI_OBJS) $(TILE_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
