
#include "feature/Style.h"

#include "renderer/feature/GLStrokeStyle.h"

using namespace atakmap::renderer::feature;

GLStrokeStyle::GLStrokeStyle(const atakmap::feature::StrokeStyle *style)
: GLStyle(style) { }

GLStrokeStyle::~GLStrokeStyle() { }