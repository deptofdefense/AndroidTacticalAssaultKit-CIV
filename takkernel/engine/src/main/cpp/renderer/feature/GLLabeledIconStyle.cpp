
#include "renderer/feature/GLLabeledIconStyle.h"

#include "renderer/feature/GLGeometry.h"

using namespace atakmap::feature;
using namespace atakmap::renderer::feature;

GLLabeledIconStyle::GLLabeledIconStyle(const atakmap::feature::CompositeStyle *style,
                                       const atakmap::feature::IconPointStyle *iconStyle,
                                       const atakmap::feature::LabelPointStyle *labelStyle)
: GLCompositeStyle(style), iconStyle(iconStyle), labelStyle(labelStyle) { }

GLLabeledIconStyle::~GLLabeledIconStyle() { }

GLLabeledIconStyle::Spi::~Spi() { }

GLStyle *GLLabeledIconStyle::Spi::create(const atakmap::renderer::feature::GLStyleSpiArg &object) {

    const Style *s = object.style;
    const Geometry * const g = object.geometry;
    if (s == nullptr || g == nullptr)
        return nullptr;

    const CompositeStyle *cs = dynamic_cast<const CompositeStyle *>(s);
    if (cs == nullptr)
        return nullptr;

    if (cs->getStyleCount() != 2)
        return nullptr;

    IconPointStyle *is = static_cast<IconPointStyle *>(cs->findStyle<IconPointStyle>().get());
    LabelPointStyle *ls = static_cast<LabelPointStyle *>(cs->findStyle<LabelPointStyle>().get());
    if (is == nullptr || ls == nullptr)
        return nullptr;

    return new GLLabeledIconStyle(cs, is, ls);
}

GLStyleSpi *GLLabeledIconStyle::getSpi() {
    static Spi spi;
    return &spi;
}
