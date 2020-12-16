# These are meant to be externally overridden as needed
TAKTHIRDPARTYDIR := ../../../takthirdparty/builds/linux-amd64-release
PROTOC := $(TAKTHIRDPARTYDIR)/host-protobuf/bin/protoc
RANLIB = ranlib
# For mac
PUBINCDIR := ../include
PGSCTHREAD := $(TAKTHIRDPARTYDIR)/pgscthread

PROTOBUFDIR=protobuf
PROTOFILES=$(subst .proto,,$(subst $(PROTOBUFDIR)/,,$(wildcard $(PROTOBUFDIR)/*.proto)))
PROTOOBJS=$(foreach pf,$(PROTOFILES),$(PROTOBUFDIR)/$(pf).pb.o)
PROTOHDRS=$(foreach pf,$(PROTOFILES),$(PROTOBUFDIR)/$(pf).pb.h)

define protorule
$(PROTOBUFDIR)/$(1).pb.cc $(PROTOBUFDIR)/$(1).pb.h: $(PROTOBUFDIR)/$(1).proto
	$(PROTOC) -I=$(PROTOBUFDIR) --cpp_out=$(PROTOBUFDIR) $(PROTOBUFDIR)/$(1).proto

$(PROTOBUFDIR)/$(1).pb.o: $(PROTOBUFDIR)/$(1).pb.cc $(PROTOBUFDIR)/$(1).pb.h
endef

$(foreach pf,$(PROTOFILES),$(eval $(call protorule,$(pf))))


OBJS := cloudiomanager.o commo.o commotime.o contactmanager.o cotmessage.o   \
        cryptoutil.o                                                         \
        datagramsocketmanagement.o httpsproxy.o hwifscanner.o                \
        internalutils.o missionpackagemanager.o netsocket.o platform.o       \
        resolverqueue.o simplefileiomanager.o                                \
        streamingsocketmanagement.o takmessage.o                             \
        tcpsocketmanagement.o                                                \
        threadedhandler.o urlrequestmanager.o                                \
        plaintextfileioprovider.o fileioprovidertracker.o                    \
        $(PROTOOBJS)                                                         \

CXXFLAGS += $(COMMO_CXXFLAGS) -I$(PUBINCDIR)                                 \
            -I$(TAKTHIRDPARTYDIR)/include/libxml2                            \
            -D__STDC_FORMAT_MACROS                                           \
            -DCURL_STATICLIB                                                 \
            -I$(TAKTHIRDPARTYDIR)/include -g -Wall -Werror                   \
            -fstack-protector-all -std=c++11

.PHONY: clean all static protogen
.DEFAULT_GOAL=all
all: libcommoncommo.so
static: libcommoncommo.a
protogen: $(foreach pf,$(PROTOFILES),$(PROTOBUFDIR)/$(pf).pb.cc)

clean:
	rm -f $(OBJS) libcommoncommo.so libcommoncommo.a                     \
	      $(PROTOBUFDIR)/*.h $(PROTOBUFDIR)/*.cc

libcommoncommo.so: CXXFLAGS += -fPIC
libcommoncommo.so: $(OBJS)
	$(CXX) $(CXXFLAGS) -shared -o $@ $^ -L$(TAKTHIRDPARTYDIR)/lib        \
	        -lxml2 -lssl -lcrypto                                        \
	        -lpgscthread -lmicrohttpd -lprotobuf-lite

libcommoncommo.a: $(OBJS)
	$(AR) rc $@ $^
	$(RANLIB) $@


# Public header deps
deps-commo_h                       = $(deps-commoresult_h)                   \
                                     $(deps-commologger_h)                   \
                                     $(deps-commoutils_h)                    \
                                     $(deps-netinterface_h)                  \
                                     $(deps-missionpackage_h)                \
                                     $(deps-contactuid_h)                    \
                                     $(deps-cotmessageio_h)                  \
                                     $(deps-simplefileio_h)                  \
                                     $(deps-cloudio_h)                       \
                                     $(deps-fileioprovider_h)                \
                                     $(PUBINCDIR)/commo.h

deps-cloudio_h                     = $(deps-commoutils_h)                    \
                                     $(deps-simplefileio_h)                  \
                                     $(PUBINCDIR)/cloudio.h
deps-commologger_h                 = $(deps-commoutils_h)                    \
                                     $(PUBINCDIR)/commologger.h
deps-commoresult_h                 = $(PUBINCDIR)/commoresult.h
deps-commoutils_h                  = $(PUBINCDIR)/commoutils.h

deps-contactuid_h                  = $(deps-commoutils_h)                    \
                                     $(PUBINCDIR)/contactuid.h
deps-cotmessageio_h                = $(deps-commoutils_h)                    \
                                     $(deps-internalutils_h)                 \
                                     $(PUBINCDIR)/cotmessageio.h
deps-missionpackage_h              = $(deps-commoutils_h)                    \
                                     $(deps-contactuid_h)                    \
                                     $(deps-contactmanager_h)                \
                                     $(deps-cotmessageio_h)                  \
                                     $(deps-commoresult_h)                   \
                                     $(PUBINCDIR)/commologger.h              \
                                     $(PUBINCDIR)/missionpackage.h
deps-netinterface_h                = $(deps-commoutils_h)                    \
                                     $(PUBINCDIR)/netinterface.h
deps-simplefileio_h                = $(deps-commoutils_h)                    \
                                     $(deps-commoresult_h)                   \
                                     $(PUBINCDIR)/commologger.h              \
                                     $(PUBINCDIR)/simplefileio.h



# Impl source deps
deps-cloudiomanager_h              = $(deps-cloudio_h)                       \
                                     $(deps-urlrequestmanager_h)             \
                                     cloudiomanager.h
deps-cloudiomanager_cpp            = $(deps-cloudiomanager_h)                \
                                     cloudiomanager.cpp

deps-commo_cpp                     = $(deps-commo_h)                         \
                                     $(deps-hwifscanner_h)                   \
                                     $(deps-cotmessage_h)                    \
                                     $(deps-takmessage_h)                    \
                                     $(deps-datagramsocketmanagement_h)      \
                                     $(deps-tcpsocketmanagement_h)           \
                                     $(deps-streamingsocketmanagement_h)     \
                                     $(deps-contactmanager_h)                \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-missionpackagemanager_h)         \
                                     $(deps-httpsproxy_h)                    \
                                     $(deps-simplefileiomanager_h)           \
                                     $(deps-cloudiomanager_h)                \
                                     commo.cpp

deps-commotime_h                   = commotime.h $(deps-internalutils_h)
deps-commotime_cpp                 = $(deps-commotime_h)                     \
                                     $(deps-platform_h)                      \
                                     commotime.cpp

deps-contactmanager_h              = $(deps-commologger_h)                   \
                                     $(deps-contactuid_h)                    \
                                     $(deps-commotime)                       \
                                     $(deps-netinterface_h)                  \
                                     $(deps-netsocket_h)                     \
                                     $(deps-datagramsocketmanagement_h)      \
                                     $(deps-streamingsocketmanagement_h)     \
                                     $(deps-tcpsocketmanagement_h)           \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-takmessage_h)                    \
                                     $(deps-internalutils_h)                 \
                                     contactmanager.h
deps-contactmanager_cpp            = $(deps-contactmanager_h)                \
                                     contactmanager.cpp

deps-cotmessage_h                  = $(deps-cotmessageio_h)                  \
                                     $(deps-contactuid_h)                    \
                                     $(deps-commologger_h)                   \
                                     cotmessage.h
deps-cotmessage_cpp                = $(deps-cotmessage_h)                    \
                                     $(deps-commotime_h)                     \
                                     $(deps-internalutils_h)                 \
                                     $(deps-cryptoutil_h)                    \
                                     $(deps-missionpackage_h)                \
                                     $(deps-protobuf_h)                      \
                                     cotmessage.cpp

deps-cryptoutil_h                  = $(deps-commologger_h)                   \
                                     $(deps-internalutils_h)                 \
                                     cryptoutil.h
deps-cryptoutil_cpp                = $(deps-cryptoutil_h)                    \
                                     $(deps-internalutils_h)                 \
                                     cryptoutil.cpp

deps-datagramsocketmanagement_h    = $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-hwifscanner_h)                   \
                                     $(deps-cotmessageio_h)                  \
                                     $(deps-cotmessage_h)                    \
                                     $(deps-takmessage_h)                    \
                                     $(deps-commoresult_h)                   \
                                     $(deps-commotime_h)                     \
                                     $(deps-cryptoutil_h)                    \
                                     datagramsocketmanagement.h
deps-datagramsocketmanagement_cpp  = $(deps-datagramsocketmanagement_h)      \
                                     $(deps-internalutils_h)                 \
                                     $(deps-commologger_h)                   \
                                     datagramsocketmanagement.cpp

deps-httpsproxy_h                  = $(deps-internalutils_h)                 \
                                     $(deps-commoutils_h)                    \
                                     $(deps-commologger_h)                   \
                                     $(deps-commoresult_h)                   \
                                     $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     httpsproxy.h
deps-httpsproxy_cpp                = $(deps-httpsproxy_h)                    \
                                     $(deps-missionpackage_h)                \
                                     $(deps-commotime_h)                     \
                                     httpsproxy.cpp

deps-hwifscanner_h                 = $(deps-netinterface_h)                  \
                                     $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-commologger_h)                   \
                                     $(deps-internalutils_h)                 \
                                     $(deps-platform_h)                      \
                                     hwifscanner.h
deps-hwifscanner_cpp               = $(deps-hwifscanner_h)                   \
                                     $(deps-internalutils_h)                 \
                                     hwifscanner.cpp

deps-internalutils_h               = $(deps-commologger_h)                   \
                                     $(deps-netinterface_h)                  \
                                     $(deps-contactuid_h)                    \
                                     $(deps-commoresult_h)                   \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     internalutils.h
deps-internalutils_cpp             = $(deps-internalutils_h)                 \
                                     $(deps-platform_h)                      \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     internalutils.cpp

deps-missionpackagemanager_h       = $(deps-missionpackage_h)                \
                                     $(deps-commoresult_h)                   \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-contactmanager_h)                \
                                     $(deps-datagramsocketmanagement_h)      \
                                     $(deps-tcpsocketmanagement_h)           \
                                     $(deps-streamingsocketmanagement_h)     \
                                     $(deps-hwifscanner_h)                   \
                                     $(deps-commotime_h)                     \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     missionpackagemanager.h
deps-missionpackagemanager_cpp     = $(deps-missionpackagemanager_h)         \
                                     $(deps-fileioprovider_h)                \
                                     missionpackagemanager.cpp

deps-netsocket_h                   = $(deps-commoutils_h)                    \
                                     $(deps-netinterface_h)                  \
                                     $(deps-internalutils_h)                 \
                                     $(deps-platform_h)                      \
                                     netsocket.h
deps-netsocket_cpp                 = $(deps-netsocket_h)                     \
                                     netsocket.cpp

deps-platform_h                    = $(deps-commologger_h)                   \
                                     $(deps-internalutils_h)                 \
                                     $(deps-netinterface_h)                  \
                                     platform.h
deps-platform_cpp                  = $(deps-platform_h)                      \
                                     $(deps-internalutils_h)                 \
                                     $(deps-netsocket_h)                     \
                                     platform.cpp

deps-protobuf_h                    = $(PROTOHDRS)                            \
                                     protobuf.h

deps-resolverqueue_h               = $(deps-commologger_h)                   \
                                     $(deps-commoutils_h)                    \
                                     $(deps-netinterface_h)                  \
                                     $(deps-commotime_h)                     \
                                     $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     resolverqueue.h
deps-resolverqueue_cpp             = $(deps-resolverqueue_h)                 \
                                     $(deps-internalutils_h)                 \
                                     resolverqueue.cpp

deps-simplefileiomanager_h         = $(deps-simplefileio_h)                  \
                                     $(deps-commoresult_h)                   \
                                     $(deps-urlrequestmanager_h)             \
                                     $(deps-internalutils_h)                 \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-commologger_h)                   \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     simplefileiomanager.h
deps-simplefileiomanager_cpp       = $(deps-simplefileiomanager_h)           \
                                     $(deps-internalutils_h)                 \
                                     $(deps-platform_h)                      \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     simplefileiomanager.cpp

deps-streamingsocketmanagement_h   = $(deps-commoutils_h)                    \
                                     $(deps-netinterface_h)                  \
                                     $(deps-commologger_h)                   \
                                     $(deps-commoresult_h)                   \
                                     $(deps-commotime_h)                     \
                                     $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-cotmessage_h)                    \
                                     $(deps-resolverqueue_h)                 \
                                     $(deps-internalutils_h)                 \
                                     streamingsocketmanagement.h
deps-streamingsocketmanagement_cpp = $(deps-streamingsocketmanagement_h)     \
                                     $(deps-takmessage_h)                    \
                                     streamingsocketmanagement.cpp

deps-takmessage_h                  = $(deps-cotmessage_h)                    \
                                     $(deps-commologger_h)                   \
                                     $(deps-internalutils_h)                 \
                                     takmessage.h
deps-takmessage_cpp                = $(deps-takmessage_h)                    \
                                     $(deps-internalutils_h)                 \
                                     $(deps-protobuf_h)                      \
                                     takmessage.cpp

deps-tcpsocketmanagement_h         = $(deps-netsocket_h)                     \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-cotmessage_h)                    \
                                     $(deps-commoresult_h)                   \
                                     $(deps-commologger_h)                   \
                                     $(deps-commotime_h)                     \
                                     $(deps-resolverqueue_h)                 \
                                     $(deps-cryptoutil_h)                    \
                                     tcpsocketmanagement.h
deps-tcpsocketmanagement_cpp       = $(deps-tcpsocketmanagement_h)           \
                                     $(deps-takmessage_h)                    \
                                     $(deps-internalutils_h)                 \
                                     tcpsocketmanagement.cpp

deps-threadedhandler_h             = $(deps-commoutils_h)                    \
                                     threadedhandler.h
deps-threadedhandler_cpp           = $(deps-threadedhandler_h)               \
                                     threadedhandler.cpp

deps-urlrequestmanager_h           = $(deps-simplefileio_h)                  \
                                     $(deps-commoresult_h)                   \
                                     $(deps-internalutils_h)                 \
                                     $(deps-threadedhandler_h)               \
                                     $(deps-commologger_h)                   \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     urlrequestmanager.h
deps-urlrequestmanager_cpp         = $(deps-urlrequestmanager_h)             \
                                     $(deps-internalutils_h)                 \
                                     $(deps-platform_h)                      \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-fileioprovidertracker_h)         \
                                     urlrequestmanager.cpp

deps-fileioprovider_h              = $(PUBINCDIR)/fileioprovider.h

deps-plaintextfileioprovider_h     = $(deps-fileioprovider_h)                \
                                     plaintextfileioprovider.h

deps-plaintextfileioprovider_cpp   = $(deps-plaintextfileioprovider_h)       \
                                     plaintextfileioprovider.cpp

deps-fileioprovidertracker_h       = $(deps-commoutils_h)                    \
                                     $(deps-fileioprovider_h)                \
                                     $(deps-plaintextfileioprovider_h)       \
                                     fileioprovidertracker.h

deps-fileioprovidertracker_cpp     = $(deps-fileioprovidertracker_h)         \
                                     fileioprovidertracker.cpp

cloudiomanager.o:                    $(deps-cloudiomanager_cpp)
commo.o:                             $(deps-commo_cpp)
commotime.o:                         $(deps-commotime_cpp)
contactmanager.o:                    $(deps-contactmanager_cpp)
cotmessage.o:                        $(deps-cotmessage_cpp)
cryptoutil.o:                        $(deps-cryptoutil_cpp)
datagramsocketmanagement.o:          $(deps-datagramsocketmanagement_cpp)
httpsproxy.o:                        $(deps-httpsproxy_cpp)
hwifscanner.o:                       $(deps-hwifscanner_cpp)
internalutils.o:                     $(deps-internalutils_cpp)
missionpackagemanager.o:             $(deps-missionpackagemanager_cpp)
netsocket.o:                         $(deps-netsocket_cpp)
platform.o:                          $(deps-platform_cpp)
resolverqueue.o:                     $(deps-resolverqueue_cpp)
simplefileiomanager.o:               $(deps-simplefileiomanager_cpp)
streamingsocketmanagement.o:         $(deps-streamingsocketmanagement_cpp)
takmessage.o:                        $(deps-takmessage_cpp)
tcpsocketmanagement.o:               $(deps-tcpsocketmanagement_cpp)
threadedhandler.o:                   $(deps-threadedhandler_cpp)
urlrequestmanager.o:                 $(deps-urlrequestmanager_cpp)
plaintextfileioprovider.o:           $(deps-plaintextfileioprovider_cpp)
fileioprovidertracker.o:             $(deps-fileioprovidertracker_cpp)
