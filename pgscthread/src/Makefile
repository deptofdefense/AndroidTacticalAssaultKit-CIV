OBJ=.o
PLATFORM_IMPL_OBJ = impl/ThreadImpl_libpthread.o

include deps.mak

CXXFLAGS += $(PGSCTHREAD_CXXFLAGS) -I$(PUBINCDIR) -I.                        \
            -g -Wall -Werror                                                 \
            -std=c++11                                                       \
            -fstack-protector-all

.PHONY: clean all static
all: libpgscthread.so
static: libpgscthread.a

clean:
	rm -f $(OBJS) libpgscthread.so libpgscthread.a

libpgscthread.so: CXXFLAGS += -fPIC 
libpgscthread.so: $(OBJS)
	$(CXX) $(CXXFLAGS) -shared -o $@ $^

libpgscthread.a: $(OBJS)
	$(AR) rc $@ $^
	$(RANLIB) $@



