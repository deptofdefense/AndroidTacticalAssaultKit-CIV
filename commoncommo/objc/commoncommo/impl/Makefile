# These are meant to be externally overridden as needed
TAKTHIRDPARTYDIR := ../../../raster/takthirdparty/builds/linux-amd64-release
RANLIB = ranlib

COREINCDIR := ../../../core/include
PUBINCDIR := ../

OBJS := commo_objc.o commologgerimpl.o contactuidimpl.o cotmessageioimpl.o   \
        missionpackageimpl.o netinterfaceimpl.o simplefileioimpl.o           \

CXXFLAGS += -std=c++11 -fobjc-arc                                            \
            $(COMMO_CXXFLAGS) -I$(COREINCDIR) -I$(PUBINCDIR)                 \
            -Wall -Werror                                                    \
            -fstack-protector-all

.PHONY: clean all static
all: libcommoncommoobjc.dylib
static: libcommoncommoobjc.a

clean:
	rm -f $(OBJS) libcommoncommoobjc.dylib libcommoncommoobjc.a

CURL_CONF := $(TAKTHIRDPARTYDIR)/bin/curl-config
commo_extra_libs=-lxml2 -lssl -lcrypto $(shell $(CURL_CONF) --libs) -lprotobuf-lite -lmicrohttpd -liconv

libcommoncommoobjc.dylib: CXXFLAGS += -fPIC 
libcommoncommoobjc.dylib: $(OBJS)
	$(CXX) $(CXXFLAGS) -shared -o $@ $^ -L$(TAKTHIRDPARTYDIR)/lib        \
	        -lcommoncommo $(commo_extra_libs)                            \

libcommoncommoobjc.a: $(OBJS)
	$(AR) rc $@ $^
	$(RANLIB) $@

%.o: %.mm
	$(CXX) -c $(CXXFLAGS) $(CPPFLAGS) -o $@ $<

deps-core                          = $(wildcard $(COREINCDIR)/*.h)

# Public header deps
deps-commo_h                       = $(deps-commologger_h)                   \
                                     $(deps-netinterface_h)                  \
                                     $(deps-contactuid_h)                    \
                                     $(deps-commoresult_h)                   \
                                     $(deps-cotmessageio_h)                  \
                                     $(deps-missionpackage_h)                \
                                     $(deps-simplefileio_h)                  \
                                     $(deps-core)                            \
                                     $(PUBINCDIR)/commo_objc.h

deps-commologger_h                 = $(deps-core)                            \
                                     $(PUBINCDIR)/commologger_objc.h
deps-commoresult_h                 = $(deps-core)                            \
                                     $(PUBINCDIR)/commoresult_objc.h
deps-commoutils_h                  = $(deps-core)                            \
                                     $(PUBINCDIR)/commoutils_objc.h

deps-contactuid_h                  = $(deps-core)                            \
                                     $(PUBINCDIR)/contactuid_objc.h
deps-cotmessageio_h                = $(deps-core)                            \
                                     $(PUBINCDIR)/cotmessageio_objc.h
deps-missionpackage_h              = $(deps-core)                            \
                                     $(deps-cotmessageio_h)                  \
                                     $(PUBINCDIR)/missionpackage_objc.h
deps-netinterface_h                = $(deps-core)                            \
                                     $(PUBINCDIR)/netinterface_objc.h
deps-simplefileio_h                = $(deps-core)                            \
                                     $(PUBINCDIR)/simplefileio_objc.h



# Impl source deps
deps-commo_objc_mm                 = $(deps-commo_h)                         \
                                     $(deps-commologgerimpl_h)               \
                                     $(deps-netinterfaceimpl_h)              \
                                     $(deps-cotmessageioimpl_h)              \
                                     $(deps-contactuidimpl_h)                \
                                     $(deps-missionpackageimpl_h)            \
                                     $(deps-simplefileioimpl_h)              \
                                     commo_objc.mm

deps-commologgerimpl_h             = $(deps-commologger_h)                   \
                                     commologgerimpl.h
deps-commologgerimpl_mm            = $(deps-commologgerimpl_h)               \
                                     commologgerimpl.mm

deps-contactuidimpl_h              = $(deps-contactuid_h)                    \
                                     contactuidimpl.h
deps-contactuidimpl_mm             = $(deps-contactuidimpl_h)                \
                                     contactuidimpl.mm

deps-cotmessageioimpl_h            = $(deps-cotmessageio_h)                  \
                                     cotmessageioimpl.h
deps-cotmessageioimpl_mm           = $(deps-cotmessageioimpl_h)              \
                                     cotmessageioimpl.mm

deps-missionpackageimpl_h          = $(deps-missionpackage_h)                \
                                     missionpackageimpl.h
deps-missionpackageimpl_mm         = $(deps-missionpackageimpl_h)            \
                                     missionpackageimpl.mm

deps-simplefileioimpl_h            = $(deps-simplefileio_h)                  \
                                     simplefileioimpl.h
deps-simplefileioimpl_mm           = $(deps-simplefileioimpl_h)              \
                                     simplefileioimpl.mm

deps-netinterfaceimpl_h            = $(deps-netinterface_h)                  \
                                     netinterfaceimpl.h
deps-netinterfaceimpl_mm           = $(deps-netinterfaceimpl_h)              \
                                     netinterfaceimpl.mm

deps-cotmessageioimpl_h            = $(deps-cotmessageio_h)                  \
                                     cotmessageioimpl.h
deps-cotmessageioimpl_mm           = $(deps-cotmessageioimpl_h)              \
                                     cotmessageioimpl.mm

commo_objc.o:                        $(deps-commo_objc_mm)
commologgerimpl.o:                   $(deps-commologgerimpl_mm)
contactuidimpl.o:                    $(deps-contactuidimpl_mm)
cotmessageioimpl.o:                  $(deps-cotmessageioimpl_mm)
missionpackageimpl.o:                $(deps-missionpackageimpl_mm)
netinterfaceimpl.o:                  $(deps-netinterfaceimpl_mm)
simplefileioimpl.o:                  $(deps-simplefileioimpl_mm)
