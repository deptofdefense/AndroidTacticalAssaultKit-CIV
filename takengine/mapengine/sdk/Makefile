PROJ =			.
HERE =			
include	$(PROJ)/Common.mk

ifeq (${NATIVE_OS},windows)

OS_SPECIFIC_LIBS =	-lpthread -lstdc++ -lavifil32 -lavicap32 \
			-lws2_32 -lmsvfw32 -lcomctl32 -lole32

endif

SUBDIRS =		src/math \
			src/spi \
			src/core \
			src/util \
			src/db \
			src/feature \
			src/raster

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

clean:			$(SUBCLEAN)
	- rm -rf include lib

install:		$(SUBINSTALL)

interfaces:		$(SUBINTERFACES)

libs:			$(SUBLIBS)

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