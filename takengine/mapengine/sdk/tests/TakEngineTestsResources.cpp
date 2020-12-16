
#include "pch.h"
#include <Windows.h>
#include <sstream>
#include "TakEngineTestsResources.h"

using namespace TAK::Engine::Tests;

std::string getModulePath() {
    HMODULE h;
    static CHAR c;
    GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, &c, &h);
    CHAR p[_MAX_PATH + 1];
    GetModuleFileNameA(h, p, _MAX_PATH + 1);
    std::string modulePath = p;
    size_t pos = modulePath.find_last_of('\\', modulePath.size() - 1);
    return modulePath.substr(0, pos);
}

std::string TAK::Engine::Tests::getResource(const char *name) {
    std::stringstream ss;
    ss << getModulePath() << "\\takenginetests_resources\\" << name;
    return ss.str();
}