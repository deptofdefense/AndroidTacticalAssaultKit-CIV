
#include "renderer/feature/GLFillStyle.h"

using namespace atakmap::renderer::feature;

GLFillStyle::~GLFillStyle() { }

GLFillStyle::GLFillStyle(const atakmap::feature::FillStyle *style)
: GLStyle(style) { }