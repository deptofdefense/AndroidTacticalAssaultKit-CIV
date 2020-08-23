# For mac
#PGSCUTILS_LIBDIR := shlib/macosx/x86_64
PUBINCDIR := ../../core/include
TAKTHIRDPARTYDIR := ../../../takthirdparty/builds/linux-amd64-release
PGSCTHREADDIR := $(TAKTHIRDPARTYDIR)/pgscthread
CURL_CONF := $(TAKTHIRDPARTYDIR)/bin/curl-config

OBJS := commotest.o

CXXFLAGS += -I$(PUBINCDIR) -I$(PGSCTHREADDIR)/include                        \
            -I$(TAKTHIRDPARTYDIR)/include                                    \
            -I$(TAKTHIRDPARTYDIR)/include/libxml2 -fPIC -g -DCURL_STATICLIB  \
            -D__STDC_FORMAT_MACROS                                           \
            -Wall -Werror -std=c++11

commotest: $(OBJS)
	g++ -o $@ $^ -L../../core/impl -L$(TAKTHIRDPARTYDIR)/lib -lcommoncommo -lprotobuf-lite -lpgscthread -lxml2 -lssl -lcrypto -lmicrohttpd -ldl -lpthread $(shell $(CURL_CONF) --libs)  -liconv

takproto: takproto.o
	g++ -o $@ $^ -L../../core/impl -L$(TAKTHIRDPARTYDIR)/lib -lcommoncommo -lprotobuf-lite -lpgscthread -lxml2 -lssl -lcrypto -lmicrohttpd -ldl -lpthread $(shell $(CURL_CONF) --libs)  -liconv

commotest.o: commotest.h commotest.cpp
takproto.o: takproto.cpp

.PHONY: clean all
clean:
	rm -f $(OBJS) commotest takproto takproto.o

all: commotest takproto

%.cpp:
	@[ -f "$@" ] && touch "$@"
%.h:
	@[ -f "$@" ] && touch "$@"


