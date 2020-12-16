PROJ =			../..
HERE =			src/spi
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

SPI_INTERFACE =		$(SPI_INCLS:%=$(INTERFACE_DEST)/%)

SPI_INCLS =		ServiceProvider.h \
			ServiceProviderRegistry.h \
			ServiceProviderRegistryDetails.h

SPI_OBJS =		

SPI_JNI_OBJS =		

SPI_LIB =		

SPI_JNI_LIB =		

INTERFACES =		$(SPI_INTERFACE)

OBJS =			$(SPI_OBJS) \
			$(SPI_JNI_OBJS)

LIBS =			$(SPI_LIB) \
			$(SPI_JNI_LIB)

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

$(SPI_LIB):		$(SPI_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		 -lThreads

$(SPI_JNI_LIB):		$(SPI_JNI_OBJS) $(SPI_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)