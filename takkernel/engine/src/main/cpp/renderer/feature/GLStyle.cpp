
#include "feature/Style.h"

#include "renderer/feature/GLStyle.h"

using namespace atakmap::feature;

using namespace atakmap::renderer::feature;

GLStyle::GLStyle(const Style *style)
: style(style) { }

GLStyle::~GLStyle() { }