PROJ =			../..
HERE =			src/math
include	$(PROJ)/Common.mk

LDFLAGS +=		-L$(LIB_DEST)/$(LIB_PATH) \
			-L$(PGSC_UTILS)/shlib/$(LIB_OS)/$(LIB_ARCH)

MATH_INTERFACE =	$(MATH_INCLS:%=$(INTERFACE_DEST)/%)

MATH_INCLS =		Ellipsoid.h \
			Frustum.h \
			GeometryModel.h \
			Matrix.h \
			Plane.h \
			Point.h \
			Ray.h \
			Rectangle.h \
			Sphere.h \
			Utils.h \
			Vector.h

MATH_OBJS =		Ellipsoid.o \
			Frustum.o \
			Matrix.o \
			Plane.o \
			Sphere.o

MATH_JNI_OBJS =		

MATH_LIB =		$(LIB_DEST)/$(LIB_PATH)/$(LIB_PREFIX)MapEngineMath$(SHLIB_EXT)

MATH_JNI_LIB =		

INTERFACES =		$(MATH_INTERFACE)

OBJS =			$(MATH_OBJS) \
			$(MATH_JNI_OBJS)

LIBS =			$(MATH_LIB) \
			$(MATH_JNI_LIB)

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

$(MATH_LIB):		$(MATH_OBJS)	| $(LIB_DEST)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ -lThreads

$(MATH_JNI_LIB):	$(MATH_JNI_OBJS) $(MATH_LIB)
	- mkdir -p $(@D)
	$(CXX)	-o $@ $(SHLIB_FLAGS) $(LDFLAGS) $^ $(JVM_LIBS)

clean:
	- rm -r $(INTERFACES)
	- rm -f $(OBJS)
	- rm -f $(LIBS)

install:		interfaces libs

interfaces:		$(INTERFACES)

libs:			$(LIBS)
