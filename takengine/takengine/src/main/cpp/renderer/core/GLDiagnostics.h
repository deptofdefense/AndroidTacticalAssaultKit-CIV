//
// Created by GeoDev on 12/15/2020.
//

#ifndef TAK_ENGINE_RENDERER_CORE_GLDIAGNOSTICS_H
#define TAK_ENGINE_RENDERER_CORE_GLDIAGNOSTICS_H

#include <chrono>
#include <map>

#include "port/Platform.h"
#include "port/String.h"
#include "renderer/core/GLGlobe.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLDiagnostics
                {
                private :
                    struct Diagnostic
                    {
                        int64_t timerStart;
                        std::size_t count{0u};
                        int64_t duration{0LL};
                        std::string name;
                        std::map<std::string, struct Diagnostic> children;
                    };
                public :
                    GLDiagnostics() NOTHROWS;
                    ~GLDiagnostics() NOTHROWS;
                public :
                    void eventTick() NOTHROWS;
                    double eventFramerate() const NOTHROWS;
                    void flush(GLGlobe &view) const NOTHROWS;
                public :
                    Util::TAKErr push(const char *name) NOTHROWS;
                    Util::TAKErr pop() NOTHROWS;
                    void reset()  NOTHROWS;
                private :
                    static void flushDiagnostics(GLGlobe &view, const Diagnostic &m, const std::size_t indent, const int64_t total) NOTHROWS;
                private :
                    Diagnostic root;
                    std::vector<Diagnostic *> profile;

                    int64_t targetMillisPerFrame;
                    int64_t timeCall;
                    int64_t lastCall;
                    int64_t currCall;
                    int64_t lastReport;
                    int64_t count;
                    double currentFramerate;
                };
            }
        }
    }
}
#endif //ATAK_GLDIAGNOSTICS_H
