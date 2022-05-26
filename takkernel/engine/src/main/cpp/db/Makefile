PROJ =			../..
HERE =			src/db
include	$(PROJ)/Common.mk

SPATIALITE ?=		$(WS)/SpatiaLite/build_$(LIB_OS)-$(LIB_ARCH)/built

INCLUDES +=		-I$(SPATIALITE)/include

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH) \
			-L$(SPATIALITE)/lib

DB_INTERFACE =		$(DB_INCLS:%=$(INTERFACE_DEST)/%)

DB_INCLS =		CatalogDatabase.h \
			Cursor.h \
			Database.h \
			DatabaseWrapper.h \
			DataStore.h \
			DB_Error.h \
			SpatiaLiteDB.h \
			Statement.h \
			WhereClauseBuilder.h

DB_OBJS =		CatalogDatabase.o \
			Database.o \
			DatabaseWrapper.o \
			SpatiaLiteDB.o \
			WhereClauseBuilder.o

DB_JNI_OBJS =		

DB_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineDB$(SHLIB_EXT)

DB_JNI_LIB =		

INTERFACES =		$(DB_INTERFACE)

OBJS =			$(DB_OBJS) \
			$(DB_JNI_OBJS)

LIBS =			$(DB_LIB) \
			$(DB_JNI_LIB)

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

$(DB_LIB):		$(DB_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		 -lspatialite -lsqlite3 -lThreads

$(DB_JNI_LIB):		$(DB_JNI_OBJS) $(DB_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
