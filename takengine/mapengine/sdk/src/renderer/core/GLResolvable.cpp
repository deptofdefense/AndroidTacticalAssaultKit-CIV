
#include "renderer/core/GLResolvable.h"

using namespace TAK::Engine::Renderer::Core;

GLResolvable::~GLResolvable() NOTHROWS
{}

const char *GLResolvable::getNameForState(State state) NOTHROWS
{
    const char *s;
    switch (state) {
      case State::UNRESOLVED:
        s = "UNRESOLVED";
        break;
      case State::RESOLVING:
        s = "RESOLVING";
        break;
      case State::RESOLVED:
        s = "RESOLVED";
        break;
      case State::UNRESOLVABLE:
        s = "UNRESOLVABLKE";
        break;
      case State::SUSPENDED:
        s = "SUSPENDED";
        break;
      default:
        s = "STATE_INVALID";
        break;
    }
    return s;
}
