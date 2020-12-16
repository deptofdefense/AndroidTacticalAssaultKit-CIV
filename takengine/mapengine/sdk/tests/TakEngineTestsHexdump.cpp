
#include "pch.h"
#include "TakEngineTestsHexdump.h"

extern "C" {
    #include "hexdump.h"
}

#include <sstream>

using namespace TAK::Engine::Tests;


void HexdumpLogger::HexdumpDeleter::operator()(hexdump *ptr) const NOTHROWS
{
    hxd_close(ptr);
}

HexdumpLogger::HexdumpLogger() NOTHROWS
{
    hxd_error_t hexerr(0);
    hexDump.reset(hxd_open(&hexerr));
    if (0 != hexerr) {
        //Logger::WriteMessage(hxd_strerror(hexerr));
        hexDump.reset();
        return;
    }
}

void HexdumpLogger::log(const char* message, void *buf, const size_t &len) NOTHROWS
{
    int error(0);
    error = hxd_compile(hexDump.get(), HEXDUMP_C, HXD_NATIVE);
    if (0 != error) {
        //Logger::WriteMessage(hxd_strerror(error));
        return;
    }

    if ((error = hxd_write(hexDump.get(), buf, len))) {
        //Logger::WriteMessage(hxd_strerror(error));
        return;
    }

    std::ostringstream os;
    if (message)
        os << message << std::endl;
    else
        os << std::endl;

    const size_t obufSize(1024);
    char obuf[obufSize];
    int bytes(0);
    while (bytes = hxd_read(hexDump.get(), obuf, obufSize))
        os.write(obuf, bytes);

    if ((error = hxd_flush(hexDump.get()))) {
        //Logger::WriteMessage(hxd_strerror(error));
        return;
    }

    while (bytes = hxd_read(hexDump.get(), obuf, obufSize))
        os.write(obuf, bytes);

    //Logger::WriteMessage(os.str().c_str());
}
