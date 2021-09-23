PROJ =			../../..
HERE =			src/raster/mosaic
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

MOSAIC_INTERFACE =	$(MOSAIC_INCLS:%=$(INTERFACE_DEST)/%)

MOSAIC_INCLS =		ATAKMosaicDatabase.h \
			MosaicDatabase.h \
			MosaicUtils.h

MOSAIC_OBJS =		# Objects are included in raster library.

MOSAIC_JNI_OBJS =		

MOSAIC_LIB =		

MOSAIC_JNI_LIB =		

INTERFACES =		$(MOSAIC_INTERFACE)

OBJS =			$(MOSAIC_OBJS) \
			$(MOSAIC_JNI_OBJS)

LIBS =			$(MOSAIC_LIB) \
			$(MOSAIC_JNI_LIB)

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

$(MOSAIC_LIB):		$(MOSAIC_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(MOSAIC_JNI_LIB):	$(MOSAIC_JNI_OBJS) $(MOSAIC_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
