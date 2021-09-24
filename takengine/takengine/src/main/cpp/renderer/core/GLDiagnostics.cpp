//
// Created by GeoDev on 12/15/2020.
//

#include <chrono>
#include <algorithm>

#include "renderer/core/GLDiagnostics.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Util;

namespace
{
    int64_t System_nanoTime() NOTHROWS
    {
        static const auto up = std::chrono::high_resolution_clock::now();
        const auto now = std::chrono::high_resolution_clock::now();
        const auto zero = std::chrono::nanoseconds::min();
        return std::chrono::duration_cast<std::chrono::nanoseconds>(now-up).count();
    }
}

GLDiagnostics::GLDiagnostics() NOTHROWS
{
    profile.push_back(&root);
}
GLDiagnostics::~GLDiagnostics() NOTHROWS
{}

void GLDiagnostics::eventTick() NOTHROWS
{
    const int64_t tick = TAK::Engine::Port::Platform_systime_millis();
    if (count == 0) {
        timeCall = tick;
    } else if (count > 1000) {
        currentFramerate = (1000000.0 / (double)(tick - timeCall));
        timeCall = tick;
        count = 0;
        lastReport = timeCall;
    } else if ((tick - lastReport) > 1000) {
        currentFramerate = ((count * 1000.0) / (double)(tick - timeCall));
        lastReport = tick;

        if ((tick - timeCall) > 5000) {
            timeCall = tick;
            count = 0;
            lastReport = timeCall;
        }
    }
    count++;

    // slows the pipeline down to effect the desired frame rate
    currCall = tick;
    lastCall = currCall;
}
double GLDiagnostics::eventFramerate() const NOTHROWS
{
    return currentFramerate;
}
void GLDiagnostics::flush(GLGlobe &view) const NOTHROWS
{
    for(const auto &m : root.children)
        flushDiagnostics(view, m.second, 0u, m.second.duration);
}
TAKErr GLDiagnostics::push(const char *name) NOTHROWS
{
    if(!name)
        return TE_InvalidArg;
    // push the diagnostic onto the stack
    Diagnostic *m;
    auto e = profile.back()->children.find(name);
    if(e == profile.back()->children.end()) {
        Diagnostic d;
        d.name = name;
        profile.back()->children[name] = d;
        m = &profile.back()->children[name];
    } else {
        m = &e->second;
    }
    profile.push_back(m);

    // start the timer
    m->timerStart = System_nanoTime();
    return TE_Ok;
}
TAKErr GLDiagnostics::pop() NOTHROWS
{
    if(profile.size() <= 1u)
        return TE_IllegalState;

    // stop the timer and increment the count
    {
        auto timerStop = System_nanoTime();
        profile.back()->duration += (timerStop-profile.back()->timerStart);
        profile.back()->count++;
    }
    // pop the diagnostic off the stack
    profile.pop_back();
    return TE_Ok;
}
void GLDiagnostics::reset()  NOTHROWS
{
    // recursively reset all diagnostics
    struct Resetter
    {
        void operator()(Diagnostic &d) NOTHROWS
        {
            d.count = 0u;
            d.duration = 0LL;
            for(auto &c : d.children)
                (*this)(c.second);
        }
    };
    Resetter r;
    r(*profile[0u]);
}

void GLDiagnostics::flushDiagnostics(GLGlobe &view, const Diagnostic &m, const std::size_t indentCount, const int64_t total) NOTHROWS
{
    char indent[33u];
    memset(indent, ' ', 33u);
    indent[std::min(indentCount, (std::size_t)32u)] = '\0';

    char msg[512];
    const int result = snprintf(msg, 512, "%s%s count %3u duration %7dus %03.1lf", indent, m.name.c_str(), (unsigned)m.count, (unsigned)(m.duration/1000LL), (double)m.duration/(double)total*100.0);
    if(result > 0)
        view.addRenderDiagnosticMessage(msg);
    // recurse over children
    for(const auto &c : m.children)
        flushDiagnostics(view, c.second, indentCount+2u, total);
}
