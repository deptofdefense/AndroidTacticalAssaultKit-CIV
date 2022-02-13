PROJ =			../..
HERE =			src/feature
include	$(PROJ)/Common.mk

# Fix this GDAL stuff once we fix how GDAL is installed.
GDAL ?=			$(WS)/gdal

INCLUDES +=		-I$(GDAL)/include

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH) \
			-L$(GDAL)/lib

FEAT_INTERFACE =	$(FEAT_INCLS:%=$(INTERFACE_DEST)/%)

FEAT_INCLS =		DataSourceFeatureDataStore.h \
			DefaultDriverDefinition.h \
			DefaultSchemaDefinition.h \
			DrawingTool.h \
			Envelope.h \
			Feature.h \
			FeatureCatalogDatabase.h \
			FeatureDatabase.h \
			FeatureDataSource.h \
			FeatureDataStore.h \
			FeatureLayer.h \
			FeatureSet.h \
			FeatureStyle.h \
			Geometry.h \
			GeometryCollection.h \
			GPX_DriverDefinition.h \
			KML_DriverDefinition.h \
			LineString.h \
			MutableFeatureDataStore.h \
			OGR_DriverDefinition.h \
			OGR_FeatureDataSource.h \
			OGR_SchemaDefinition.h \
			ParseGeometry.h \
			PersistentDataSourceFeatureDataStore.h \
			Point.h \
			Polygon.h \
			Route.h \
			ShapefileDriverDefinition.h \
			SimpleSchemaDefinition.h \
			Style.h \
			VertexInfo.h

FEAT_OBJS =		DataSourceFeatureDataStore.o \
			DefaultDriverDefinition.o \
			DefaultSchemaDefinition.o \
			DrawingTool.o \
			FeatureCatalogDatabase.o \
			FeatureDatabase.o \
			FeatureDataSource.o \
			FeatureDataStore.o \
			FeatureLayer.o \
			FeatureStyle.o \
			GeometryCollection.o \
			KML_DriverDefinition.o \
			LineString.o \
			MutableFeatureDataStore.o \
			OGR_DriverDefinition.o \
			OGR_FeatureDataSource.o \
			OGR_SchemaDefinition.o \
			ParseGeometry.o \
			PersistentDataSourceFeatureDataStore.o \
			Polygon.o \
			Route.o \
			SimpleSchemaDefinition.o \
			Style.o \
			VertexInfo.o

FEAT_JNI_OBJS =		

FEAT_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineFeature$(SHLIB_EXT)

FEAT_JNI_LIB =		

INTERFACES =		$(FEAT_INTERFACE)

OBJS =			$(FEAT_OBJS) \
			$(FEAT_JNI_OBJS)

LIBS =			$(FEAT_LIB) \
			$(FEAT_JNI_LIB)

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

$(FEAT_LIB):		$(FEAT_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		-lMapEngineDB -lMapEngineUtil -lMapEngineCore -lMapEngineMath \
		-lgdal \
		-lThreads

$(FEAT_JNI_LIB):	$(FEAT_JNI_OBJS) $(FEAT_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
