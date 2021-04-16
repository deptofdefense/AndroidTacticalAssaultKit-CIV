
#include <algorithm>
#include <libxml/xmlreader.h>
#include "feature/KMLModels.h"
#include "feature/KMLParser.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

#undef KML_OBJ_BP
#undef KML_BASE_BP
#undef KML_MID_BP

KMLObject::KMLObject(const char *n, KMLEntity e)
    : entity(e),
    objectName(n),
    refs(1)
{}

KMLObject::~KMLObject() {}

TAKErr KMLObject::parseAttrs(KMLParser &parser) NOTHROWS {
    parser.parseAttrText("id", this->id);
    return TE_Ok;
}

#define KML_OBJ_BP_N(n, b, name) \
    KML##n::KML##n() : KML##b(name, KMLEntity_##n) {} \
    KML##n::~KML##n() {} \
    TAKErr KML##n::parse(KMLParser &p) NOTHROWS { return p.parse##n(*this); }

#define KML_OBJ_BP(n, b) KML_OBJ_BP_N(n, b, #n)

#define KML_BASE_BP(n, b) \
    KML##n::KML##n(const char *objName, KMLEntity entity) \
            : KML##b(objName, entity) {} \
    KML##n::~KML##n() {}

#define KML_MID_BP(n, b) \
    KML##n::KML##n() : KML##b(#n, KMLEntity_##n) {} \
    KML##n::KML##n(const char *objName, KMLEntity entity) \
            : KML##b(objName, entity) {} \
    KML##n::~KML##n() {} \
    TAKErr KML##n::parse(KMLParser &p) NOTHROWS { return p.parse##n(*this); }



KML_BASE_BP(Geometry, Object);
KML_OBJ_BP(MultiGeometry, Geometry);
KML_OBJ_BP(Point, Geometry);
KML_OBJ_BP(LinearRing, Geometry);
KML_OBJ_BP(Polygon, Geometry);
KML_OBJ_BP_N(gxTrack, Geometry, "gx:Track");
KML_OBJ_BP(Model, Geometry);
KML_OBJ_BP(LineString, Geometry);
KML_BASE_BP(TimePrimitive, Object);
KML_OBJ_BP(TimeSpan, TimePrimitive);
KML_MID_BP(Link, Object);
KML_OBJ_BP(Icon, Link);
KML_BASE_BP(SubStyle, Object);
KML_BASE_BP(ColorStyle, SubStyle);
KML_OBJ_BP(IconStyle, ColorStyle);
KML_OBJ_BP(LabelStyle, ColorStyle);
KML_OBJ_BP(LineStyle, ColorStyle);
KML_OBJ_BP(PolyStyle, ColorStyle);
KML_OBJ_BP(BalloonStyle, SubStyle);
KML_OBJ_BP(ListStyle, SubStyle);
KML_BASE_BP(StyleSelector, Object);
KML_OBJ_BP(Style, StyleSelector);
KML_OBJ_BP(StyleMap, StyleSelector);
KML_OBJ_BP_N(StyleMapPair, Object, "Pair");
KML_BASE_BP(Feature, Object);
KML_OBJ_BP(Placemark, Feature);
KML_BASE_BP(Container, Feature);
KML_OBJ_BP(Document, Container);
KML_OBJ_BP(Folder, Container);
KML_OBJ_BP_N(DOM, Object, "kml");
