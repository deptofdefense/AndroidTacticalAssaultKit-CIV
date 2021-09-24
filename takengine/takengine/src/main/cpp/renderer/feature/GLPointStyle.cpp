
#include "feature/Style.h"
#include "renderer/feature/GLPointStyle.h"

using namespace atakmap::renderer::feature;

GLPointStyle::GLPointStyle(const atakmap::feature::PointStyle *style)
: GLStyle(style) { }

GLPointStyle::~GLPointStyle() { }