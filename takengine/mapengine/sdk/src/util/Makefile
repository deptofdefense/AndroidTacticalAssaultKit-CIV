PROJ =			../..
HERE =			src/util
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

UTIL_INTERFACE =	$(UTIL_INCLS:%=$(INTERFACE_DEST)/%)

UTIL_INCLS =		AttributeSet.h \
			Disposable.h \
			Distance.h \
			IO.h \
			Logging.h

UTIL_OBJS =		Distance.o \
			IO.o \
			Logging.o

UTIL_JNI_OBJS =		

UTIL_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineUtil$(SHLIB_EXT)

UTIL_JNI_LIB =		

INTERFACES =		$(UTIL_INTERFACE)

OBJS =			$(UTIL_OBJS) \
			$(UTIL_JNI_OBJS)

LIBS =			$(UTIL_LIB) \
			$(UTIL_JNI_LIB)

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

$(UTIL_LIB):		$(UTIL_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ \
		 -lThreads

$(UTIL_JNI_LIB):	$(UTIL_JNI_OBJS) $(UTIL_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)