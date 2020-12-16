PROJ =			../..
HERE =			src/raster
include	$(PROJ)/Common.mk

GDAL ?=			$(WS)/pgsc/.agdalstage-$(LIB_OS)-$(LIB_ARCH)

INCLUDES +=		-I$(GDAL)/include

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH) \
			-L$(GDAL)/lib

RAST_INTERFACE =	$(RAST_INCLS:%=$(INTERFACE_DEST)/%)

RAST_INCLS =		AbstractDataStoreRasterLayer.h \
			AbstractRasterLayer.h \
			AutoSelectService.h \
			DatasetDescriptor.h \
			DatasetRasterLayerImpl.h \
			DataStoreRasterLayer.h \
			ImageDatasetDescriptor.h \
			LayerDatabase.h \
			LocalRasterDataStore.h \
			MosaicDatasetDescriptor.h \
			PersistentRasterDataStore.h \
			PrecisionRasterDataAccess.h \
			RasterDataAccess.h \
			RasterDataStore.h \
			RasterLayer.h

RAST_OBJS =		AbstractDataStoreRasterLayer.o \
			AbstractRasterLayer.o \
			apass/ApassLayerInfoSpi.o \
			AutoSelectService.o \
			DatasetDescriptor.o \
			gdal/GdalDatasetProjection.o \
			gdal/GdalLayerInfo.o \
			gdal/GdalLibrary.o \
			gdal/RapidPositioningControlB.o \
			ImageDatasetDescriptor.o \
			LayerDatabase.o \
			LocalRasterDataStore.o \
			mosaic/ATAKMosaicDatabase.o \
			mosaic/MosaicDatabase.o \
			mosaic/MosaicUtils.o \
			MosaicDatasetDescriptor.o \
			osm/OSMUtils.o \
			pfi/PFI.o \
			pfi/PFIBlockHeader.o \
			pfi/PFIStructure.o \
			pfi/PFIUtils.o \
			pfps/PfpsLayerInfoSpi.o \
			pfps/PfpsMapTypeFrame.o \
			pfps/PfpsUtils.o \
			PersistentRasterDataStore.o \
			tilereader/TileReader.o

RAST_JNI_OBJS =		

RAST_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineRaster$(SHLIB_EXT)

RAST_JNI_LIB =		

INTERFACES =		$(RAST_INTERFACE)

OBJS =			$(RAST_OBJS) \
			$(RAST_JNI_OBJS)

LIBS =			$(RAST_LIB) \
			$(RAST_JNI_LIB)

SUBDIRS =		apass \
			gdal \
			mosaic \
			osm \
			pfi \
			pfps \
			tilereader

SUBCLEAN =		$(SUBDIRS:%=clean-%)

SUBINSTALL =		$(SUBDIRS:%=install-%)

SUBINTERFACES =		$(SUBDIRS:%=interfaces-%)

SUBLIBS =		$(SUBDIRS:%=libs-%)

.PHONY:			all $(SUBDIRS) \
			clean $(SUBCLEAN) \
			install $(SUBINSTALL) \
			interfaces $(SUBINTERFACES) \
			libs $(SUBLIBS)
     
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

$(RAST_LIB):		$(RAST_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		-lMapEngineFeature -lMapEngineDB -lMapEngineUtil -lMapEngineCore \
		-lMapEngineMath -lgdal -lThreads

$(RAST_JNI_LIB):	$(RAST_JNI_OBJS) $(RAST_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:			$(SUBCLEAN)
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs $(SUBINSTALL)

interfaces:		$(INTERFACES) $(SUBINTERFACES)

libs:			$(LIBS) $(SUBLIBS)

$(SUBDIRS):
	$(MAKE) -C $@

$(SUBCLEAN):
	- $(MAKE) -C $(@:clean-%=%) clean

$(SUBINSTALL):
	- $(MAKE) -C $(@:install-%=%) install

$(SUBINTERFACES):
	- $(MAKE) -C $(@:interfaces-%=%) interfaces

$(SUBLIBS):
	- $(MAKE) -C $(@:libs-%=%) libs
