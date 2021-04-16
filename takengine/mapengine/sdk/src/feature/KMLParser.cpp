
#include <string.h>
#include <vector>
#include <libxml/xmlreader.h>
#include "feature/KMLParser.h"
#include "port/STLSetAdapter.h"
#include "util/Memory.h"
#include "port/String.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

//
// KMLParser def
//

KMLParser::XMLReader::~XMLReader()
{}

struct XMLReaderImpl : public KMLParser::XMLReader {

    XMLReaderImpl(TAK::Engine::Util::DataInput2 &input, const char *filePath)
        : input(input), xmlReader(nullptr) {
    
        xmlTextReaderPtr reader = xmlReaderForIO(inputRead, inputClose, &input, filePath, nullptr, 0);
        if (!reader)
            throw std::runtime_error("could not open xml reader");

        xmlReader = reader;
    }

    ~XMLReaderImpl() override {
        if (xmlReader)
            xmlFreeTextReader(xmlReader);
    }

    xmlTextReaderPtr xmlReader;
    TAK::Engine::Util::DataInput2 &input;

    static int XMLCALL inputRead(void *context, char *buffer, int len) {
        auto *input = static_cast<TAK::Engine::Util::DataInput2 *>(context);
        size_t numRead = 0;
        input->read(reinterpret_cast<uint8_t *>(buffer), &numRead, len);
        return static_cast<int>(numRead);
    }

    static int XMLCALL inputClose(void *context) {
        return 0;
    }
};

KMLParser::~KMLParser() 
{}

TAKErr KMLParser::readXML() NOTHROWS {
    
    if (!reader)
        return TE_IllegalState;

    int result = xmlTextReaderRead(static_cast<XMLReaderImpl *>(reader.get())->xmlReader);
    if (result == 0) {
        return TE_EOF;
    }

    if (result != 1)
        return TE_IO;

    return TE_Ok;
}

int KMLParser::typeXML() const NOTHROWS {
    return ::xmlTextReaderNodeType(static_cast<XMLReaderImpl *>(reader.get())->xmlReader);
}

bool KMLParser::isEmptyTag() const NOTHROWS {
    return ::xmlTextReaderIsEmptyElement(static_cast<XMLReaderImpl *>(reader.get())->xmlReader) != 0;
}

const char *KMLParser::valueXML() const NOTHROWS {
    return (const char *)::xmlTextReaderConstValue(static_cast<XMLReaderImpl *>(reader.get())->xmlReader);
}

const char *KMLParser::nameXML() const NOTHROWS {
    return (const char *)::xmlTextReaderConstName(static_cast<XMLReaderImpl *>(reader.get())->xmlReader);
}

bool KMLParser::atTag(const char *tag) const NOTHROWS {
    const char *name = nameXML();
    if (!name)
        return false;

    return strcmp(name, tag) == 0;
}

bool KMLParser::atEndTag() const NOTHROWS {
    int type = typeXML();
    return type == XML_READER_TYPE_END_ELEMENT ||
        (type == XML_READER_TYPE_ELEMENT && isEmptyTag());
}

bool KMLParser::atEndTag(const char *tag) const NOTHROWS {
    return atEndTag() && atTag(tag);
}

bool KMLParser::atBeginTag() const NOTHROWS {
    int type = typeXML();
    return type == XML_READER_TYPE_ELEMENT;
}

bool KMLParser::atBeginTag(const char *name) const NOTHROWS {
    return atBeginTag() && atTag(name);
}

TAKErr KMLParser::skipTag() NOTHROWS {
    scratchText = nameXML();
    return readToTagEnd(scratchText.c_str());
}

TAKErr KMLParser::readToTag() NOTHROWS {

    for (;;) {
        TAKErr code = readXML();
        TE_CHECKRETURN_CODE(code);

        int type = typeXML();
        if (type == XML_READER_TYPE_ELEMENT ||
            type == XML_READER_TYPE_END_ELEMENT) {
            this->tagName = nameXML();
            return TE_Ok;
        }
    }
}

TAKErr KMLParser::readToTagEnd(const char *tag) NOTHROWS {
    int depth = 0;

    if (atEndTag(tag))
        return TE_Ok;

    for (;;) {
        TAKErr code = readXML();
        TE_CHECKRETURN_CODE(code);

        if (atBeginTag())
            ++depth;

        if (depth == 0 && atEndTag(tag))
            return TE_Ok;

        if (atEndTag())
            --depth;
    }
}

TAKErr KMLParser::parseText(std::string &text) NOTHROWS {

    int nt = typeXML();
    if (this->atEndTag()) {
        text = "";
        return TE_Ok;
    }

    TAKErr code = readXML();
    TE_CHECKRETURN_CODE(code);

    nt = typeXML();
    if (nt == XML_READER_TYPE_SIGNIFICANT_WHITESPACE) {
        code = readXML();
        TE_CHECKRETURN_CODE(code);
        nt = typeXML();
    }
    
    text = "";
    while (nt == XML_READER_TYPE_TEXT || nt == XML_READER_TYPE_CDATA) {
        text += valueXML();
        code = readXML();
        TE_CHECKBREAK_CODE(code);
        nt = typeXML();
    }

    if (atBeginTag())
        code = this->skipTag();

    return code;
}

TAKErr KMLParser::parseBool(bool &result) NOTHROWS {

    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

	int cmp = -1;
	TAK::Engine::Port::String_compareIgnoreCase(&cmp, scratchText.c_str(), "true");

    result = (strcmp("1", scratchText.c_str()) == 0 || cmp == 0);
    return TE_Ok;
}

TAKErr KMLParser::parseColor(uint32_t &color) NOTHROWS {

    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

    char *end = nullptr;
    color = strtoul(scratchText.c_str(), &end, 16);

    if (end == scratchText.c_str())
        return TE_Err;

    return end != scratchText.c_str() ? TE_Ok : TE_Err;
}

TAKErr KMLParser::parseColorMode(KMLColorMode &mode) NOTHROWS {

    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

    if (scratchText == "default")
        mode = KMLColorMode_default;
    else if (scratchText == "random")
        mode = KMLColorMode_random;
    else
        return TE_Unsupported;

    return TE_Ok;
}

TAKErr KMLParser::parseFloat(double &val) NOTHROWS {

    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

    char *end = nullptr;
    val = strtod(scratchText.c_str(), &end);


    if (end == scratchText.c_str())
        return TE_Err;

    return end != scratchText.c_str() ? TE_Ok : TE_Err;
}

TAKErr KMLParser::parseAttrText(const char *name, std::string &dst) NOTHROWS {
    char *attr = (char *)xmlTextReaderGetAttribute(static_cast<XMLReaderImpl *>(reader.get())->xmlReader, BAD_CAST name);
    if (attr) {
        dst = attr;
        xmlFree(attr);
        return TE_Ok;
    }
    return TE_Unsupported;
}

TAKErr stringToFloat(const char *str, double &value) NOTHROWS {
    char *end = nullptr;
    value = strtod(str, &end);


    if (end == str)
        return TE_Err;

    return str != end ? TE_Ok : TE_Err;
}

TAKErr KMLParser::parseVec2(KMLVec2 &vec2) NOTHROWS {
    vec2 = KMLVec2();

    TAKErr code = parseAttrText("x", scratchText);
    TE_CHECKRETURN_CODE(code);
    code = stringToFloat(scratchText.c_str(), vec2.x);
    TE_CHECKRETURN_CODE(code);

    code = parseAttrText("y", scratchText);
    TE_CHECKRETURN_CODE(code);
    code = stringToFloat(scratchText.c_str(), vec2.y);
    TE_CHECKRETURN_CODE(code);

    return TE_Ok;
}

TAKErr KMLParser::parseAltitudeMode(KMLAltitudeMode &mode) NOTHROWS {

    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

    if (scratchText == "absolute")
        mode = KMLAltitudeMode_absolute;
    else if (scratchText == "clampToGround" || scratchText == "clampToSeaFloor" || scratchText == "clampedToGround")
        mode = KMLAltitudeMode_clampToGround;
    else if (scratchText == "relativeToGround" || scratchText == "relativeToSeaFloor")
        mode = KMLAltitudeMode_relativeToGround;
    else
        return TE_Unsupported;

    return TE_Ok;
}

TAKErr KMLParser::parseStyleState(KMLStyleState &parsed_state) NOTHROWS {
    TAKErr code = parseText(scratchText);
    TE_CHECKRETURN_CODE(code);

    if (scratchText == "normal")
        parsed_state = KMLStyleState_normal;
    else if (scratchText == "highlight")
        parsed_state = KMLStyleState_highlight;
    else
        return TE_Unsupported;

    return TE_Ok;
}

TAKErr KMLParser::parseCoordinates(KMLCoordinates &coords) NOTHROWS {

    std::string coordsText;
    TAKErr code = parseText(coordsText);
    TE_CHECKRETURN_CODE(code);

    const char *pos = coordsText.c_str();
    const char *end = coordsText.c_str() + coordsText.length();
    char *delim = nullptr;
    
    double c[3] = { 0, 0, 0 };
    int ci = 0;

    while (pos != end) {
        switch (*pos) {
        case ',':
            if (ci == 2) {
                coords.values.push_back(c[0]);
                coords.values.push_back(c[1]);
                coords.values.push_back(c[2]);
                c[0] = c[1] = c[2] = 0.0;
                ci = 0;
            } else {
                ++ci;
            }
            ++pos;
            break;
        case ' ':
        case '\t':
        case '\r':
        case '\n':
            ++pos;
            if (ci > 0) {
                coords.values.push_back(c[0]);
                coords.values.push_back(c[1]);
                coords.values.push_back(c[2]);
                c[0] = c[1] = c[2] = 0.0;
                ci = 0;
            }
            break;
        default: {
            double v = strtod(pos, &delim);
            if (pos == delim)
                ++pos;
            else
                pos = delim;
            if (ci < 3)
                c[ci] = v;
            coords.dim = std::max(ci + 1, coords.dim);
            break;
        }
        }
    }

    if (ci > 0) {
        coords.values.push_back(c[0]);
        coords.values.push_back(c[1]);
        coords.values.push_back(c[2]);
    }
    
    return code;
}

TAKErr KMLParser::parseLocation(KMLLocation &location) NOTHROWS {

    TAKErr code(TE_Ok);

    location.latitude = 0;
    location.longitude = 0;
    location.altitude = 0;

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("Location")) {
            break;
        } else if (atBeginTag("longitude")) {
            code = this->parseFloat(location.longitude);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("latitude")) {
            code = this->parseFloat(location.latitude);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("altitude")) {
            code = this->parseFloat(location.altitude);
            TE_CHECKRETURN_CODE(code);
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseOrientation(KMLOrientation &orientation) NOTHROWS {

    TAKErr code(TE_Ok);

    orientation.heading = 0;
    orientation.tilt = 0;
    orientation.roll = 0;

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("Orientation")) {
            break;
        } else if (atBeginTag("heading")) {
            code = this->parseFloat(orientation.heading);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("tilt")) {
            code = this->parseFloat(orientation.tilt);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("roll")) {
            code = this->parseFloat(orientation.roll);
            TE_CHECKRETURN_CODE(code);
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseScale(KMLScale &scale) NOTHROWS {

    TAKErr code(TE_Ok);

    scale.x = 0;
    scale.y = 0;
    scale.z = 0;

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("Scale")) {
            break;
        } else if (atBeginTag("x")) {
            code = this->parseFloat(scale.x);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("y")) {
            code = this->parseFloat(scale.y);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("z")) {
            code = this->parseFloat(scale.z);
            TE_CHECKRETURN_CODE(code);
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseAlias(KMLAlias &alias) NOTHROWS {
    TAKErr code(TE_Ok);

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("Alias")) {
            break;
        } else if (atBeginTag("targetHref")) {
            code = this->parseText(alias.targetHref.value);
            TE_CHECKRETURN_CODE(code);
        } else if (atBeginTag("sourceHref")) {
            code = this->parseText(alias.sourceHref.value);
            TE_CHECKRETURN_CODE(code);
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseResourceMap(KMLResourceMap &resourceMap) NOTHROWS {
    TAKErr code(TE_Ok);

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("ResourceMap")) {
            break;
        } else if (atBeginTag("Alias")) {
            KMLAlias alias;
            code = this->parseAlias(alias);
            TE_CHECKRETURN_CODE(code);
            resourceMap.Alias.push_back(std::move(alias));
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseExtendedData(KMLExtendedData &extendedData) NOTHROWS {
    TAKErr code(TE_Ok);

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("ExtendedData")) {
            break;
        } else if (atBeginTag("Data")) {
            KMLData data;
            code = this->parseData(data);
            TE_CHECKRETURN_CODE(code);
            extendedData.Data.push_back(std::move(data));
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

TAKErr KMLParser::parseData(KMLData &data) NOTHROWS {
    TAKErr code(TE_Ok);

    code = this->parseAttrText("name", data.name.value);
    if (code == TE_Ok)
        data.name.exists = true;

    if (code != TE_Unsupported)
        TE_CHECKRETURN_CODE(code);

    code = TE_Ok;

    for (;;) {
        code = this->readToTag();
        TE_CHECKRETURN_CODE(code);

        if (atEndTag("Data")) {
            break;
        } else if (atBeginTag("value")) {
            code = this->parseText(data.value.value);
            TE_CHECKRETURN_CODE(code);
            data.value.exists = true;
        } else if (this->atBeginTag()) {
            this->skipTag();
        }
    }

    return TE_Ok;
}

bool isWhite(char ch) {
    return ch == ' ' ||
        ch == '\r' ||
        ch == '\n' ||
        ch == '\t';
}

TAKErr KMLParser::parsegxcoord(std::vector<KMLgxcoord> &coords) NOTHROWS {

    TAKErr code = parseText(scratchText);

    const char *pos = scratchText.c_str();
    const char *end = pos + scratchText.length();

    double vals[3] = { 0.0, 0.0, 0.0 };
    int vi = 0;

    while (pos != end && vi < 3) {
        char *delim = nullptr;
        vals[vi++] = strtod(pos, &delim);
        if (pos == delim)
            return TE_Err;

        pos = delim;
        
        while (isWhite(*pos))
            ++pos;
    }

    KMLgxcoord coord;
    coord.lng = vals[0];
    coord.lat = vals[1];
    coord.alt = vals[2];

    TE_BEGIN_TRAP() {
        coords.push_back(coord);
    } TE_END_TRAP(code);

    return code;
}

template <typename T>
struct InitStrategy {
    template <typename U>
    static KMLPtr<T> init(KMLPtr<U> &ptr, bool storeEnabled) {
        KMLPtr<T> r(new T());
        if (storeEnabled)
            ptr = r;
        return r;
    }

    template <typename U>
    static KMLPtr<T> init(KMLList<U> &list, bool storeEnabled) {
        KMLPtr<T> r(new T());
        if (storeEnabled)
            list.values.push_back(r);
        return r;
    }

    template <typename U>
    static KMLPtr<T> init(std::deque<KMLPtr<U>> &queue, bool storeEnabled) {
        KMLPtr<T> r(new T());
        if (storeEnabled)
            queue.push_back(r);
        return r;
    }
};

#define PUSH_OBJECT(t) \
    TE_BEGIN_TRAP() { \
        stack.push_back(KMLPtr<KMLObject>(new KML##t())); \
        code = stack.back()->parseAttrs(*this); \
    } TE_END_TRAP(code) \
    TE_CHECKRETURN_CODE(code); \
    state = KMLParser::Object_begin; \

#define POP_OBJECT() \
    stack.pop_back();

#define POP_FIELD() \
    fObj = stack.back(); \
    POP_OBJECT();

#define PARSE_PARENT(n) \
    TAKErr code = parse##n(obj); \
    if (code != TE_Unsupported) \
        return code;

#define PARSE_FIELD(n, t) \
    if (atBeginTag(#n)) { \
        code = parse##t(obj.n.value); \
        if (code != TE_Unsupported) { \
            TE_CHECKRETURN_CODE(code); \
            obj.n.exists = true; \
            state = Field_##n; \
        } \
        code = readToTagEnd(#n); \
        TE_CHECKRETURN_CODE(code); \
        return TE_Ok; \
    }

#define BEGIN_OBJECT(n, t) \
    if (atBeginTag(#t)) { \
        TE_BEGIN_TRAP() { \
            stack.push_back(InitStrategy<KML##t>::init(obj.n, store)); \
            code = stack.back()->parseAttrs(*this); \
        } TE_END_TRAP(code) \
        TE_CHECKRETURN_CODE(code); \
        state = KMLParser::Object_begin; \
        return TE_Ok; \
    }

#define PARSE_OBJECT_NOFIELD_IMPL(n, t) \
    TE_BEGIN_TRAP() { \
        stack.push_back(InitStrategy<KML##t>::init(obj.n, store)); \
        code = stack.back()->parseAttrs(*this); \
    } TE_END_TRAP(code) \
    TE_CHECKRETURN_CODE(code); \
    bool storeBackup = store; \
    enableStore(true); \
    code = finishObject(); \
    TE_CHECKRETURN_CODE(code); \
    enableStore(storeBackup); \
    POP_FIELD();

#define PARSE_OBJECT_IMPL(n, t) \
    PARSE_OBJECT_NOFIELD_IMPL(n, t) \
    state = KMLParser::Field_##n;

#define PARSE_OBJECT(n, t) \
    if (atBeginTag(#t)) { \
        PARSE_OBJECT_IMPL(n, t); \
        return TE_Ok; \
    }

#define PARSE_NS_OBJECT(n, ns, t) \
    if (atBeginTag(#ns ":" #t)) { \
        PARSE_OBJECT_IMPL(n, ns##t); \
        return TE_Ok; \
    }

#define PARSE_OBJECT_ALT(n, t) \
    if (atBeginTag(#n)) { \
        PARSE_OBJECT_IMPL(n, t); \
        return TE_Ok; \
    }


#define PARSE_OBJECT_NOFIELD(n, t) \
    if (atBeginTag(#t)) { \
        PARSE_OBJECT_NOFIELD_IMPL(n, t); \
        return TE_Ok; \
    }

#define PARSE_NS_FIELD3(ns, n, t)           \
    if (atBeginTag(#ns ":" #n)) {           \
        code = parse##t(obj.n.value);       \
        if (code != TE_Unsupported) {       \
            TE_CHECKRETURN_CODE(code);      \
            obj.n.exists = true;            \
            state = Field_##n;              \
        }                                   \
        code = readToTagEnd(#ns ":" #n);    \
        TE_CHECKRETURN_CODE(code);          \
        return TE_Ok;                       \
    }

#define PARSE_NS_FIELD2(n, ns, t) \
    if (atBeginTag(#ns ":" #t)) { \
        code = parse##ns##t(obj.n); \
        TE_CHECKRETURN_CODE(code); \
        code = readToTagEnd(#ns ":" #t); \
        TE_CHECKRETURN_CODE(code); \
        state = Field_##n; \
        return TE_Ok; \
    }

#define PARSE_NS_FIELD(ns, n, t) \
    if (atBeginTag(#n)) { \
        code = parse##t(obj.##ns##_##n.value); \
        TE_CHECKRETURN_CODE(code); \
        code = readToTagEnd(#ns ":" #n); \
        TE_CHECKRETURN_CODE(code); \
        state = Field_##ns##_##n; \
        return TE_Ok; \
    }

#define PARSE_INST_OBJECT(n, t) \
    if (atBeginTag(#n)) { \
        code = readToTag(); \
        TE_CHECKRETURN_CODE(code); \
        if (atBeginTag(#t)) { \
            PARSE_OBJECT_IMPL(n, t); \
            code = readToTagEnd(#n); \
        } else { code = TE_Unsupported; } \
    }

#define PARSE_ABSTRACT_FIELD(n, t) \
    if (atBeginTag(#t)) { \
        PUSH_OBJECT(t); \
        code = finishObject(); \
        if (code == TE_Ok) { \
            obj.n.apply(stack.back()); \
            POP_OBJECT(); \
            state = KMLParser::Field_##n; \
            return TE_Ok; \
        } \
        TE_CHECKRETURN_CODE(code); \
    }

TAKErr KMLParser::parseObject(KMLObject &obj) NOTHROWS {
    if (atEndTag(obj.get_entity_name())) {
        state = Object_end;
        return TE_Ok;
    }
    return TE_Unsupported;
}

TAKErr KMLParser::parseGeometry(KMLGeometry &obj) NOTHROWS {
    PARSE_PARENT(Object);
    return code;
}

TAKErr KMLParser::parsePoint(KMLPoint &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_FIELD(extrude, Bool);
    PARSE_FIELD(altitudeMode, AltitudeMode);
    PARSE_NS_FIELD3(gx, altitudeMode, AltitudeMode);
    PARSE_FIELD(coordinates, Coordinates);
    return code;
}

TAKErr KMLParser::parseLinearRing(KMLLinearRing &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_NS_FIELD(gx, altitudeOffset, Float);
    PARSE_FIELD(extrude, Bool);
    PARSE_FIELD(tessellate, Bool);
    PARSE_FIELD(altitudeMode, AltitudeMode);
    PARSE_NS_FIELD3(gx, altitudeMode, AltitudeMode);
    PARSE_FIELD(coordinates, Coordinates);

    return code;
}

TAKErr KMLParser::parsePolygon(KMLPolygon &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_FIELD(extrude, Bool);
    PARSE_FIELD(tessellate, Bool);
    PARSE_FIELD(altitudeMode, AltitudeMode);
    PARSE_NS_FIELD3(gx, altitudeMode, AltitudeMode);
    PARSE_INST_OBJECT(outerBoundaryIs, LinearRing);
    PARSE_INST_OBJECT(innerBoundaryIs, LinearRing);
    return code;
}

#define PARSE_GEOM_OBJECTS() \
    PARSE_OBJECT(Geometry, Point); \
    PARSE_OBJECT(Geometry, Polygon); \
    PARSE_OBJECT(Geometry, MultiGeometry); \
    PARSE_OBJECT(Geometry, LineString); \
    PARSE_OBJECT(Geometry, Model); \
    PARSE_OBJECT(Geometry, LinearRing); \
    PARSE_OBJECT(Geometry, LineString); \
    PARSE_NS_OBJECT(Geometry, gx, Track);

TAKErr KMLParser::parseMultiGeometry(KMLMultiGeometry &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_GEOM_OBJECTS();
    return code;
}

TAKErr KMLParser::parseModel(KMLModel &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_FIELD(altitudeMode, AltitudeMode);
    PARSE_NS_FIELD3(gx, altitudeMode, AltitudeMode);
    PARSE_FIELD(Location, Location);
    PARSE_FIELD(Orientation, Orientation);
    PARSE_FIELD(Scale, Scale);
    PARSE_OBJECT(Link, Link);
    PARSE_FIELD(ResourceMap, ResourceMap);
    return code;
}

TAKErr KMLParser::parseLineString(KMLLineString &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_FIELD(altitudeMode, AltitudeMode);
    PARSE_NS_FIELD3(gx, altitudeMode, AltitudeMode);
    PARSE_FIELD(extrude, Bool);
    PARSE_FIELD(tessellate, Bool);
    PARSE_FIELD(coordinates, Coordinates);
    return code;
}

TAKErr KMLParser::parsegxTrack(KMLgxTrack &obj) NOTHROWS {
    PARSE_PARENT(Geometry);
    PARSE_NS_FIELD2(coords, gx, coord);
    return code;
}

TAKErr KMLParser::parseTimeSpan(KMLTimeSpan &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(begin, Text);
    PARSE_FIELD(end, Text);
    return code;
}

TAKErr KMLParser::parseBalloonStyle(KMLBalloonStyle &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(bgColor, Color);
    PARSE_FIELD(textColor, Color);
    PARSE_FIELD(text, Text);
    return code;
}

TAKErr KMLParser::parseListStyle(KMLListStyle &obj) NOTHROWS {
    PARSE_PARENT(Object);
    return code;
}

TAKErr KMLParser::parseColorStyle(KMLColorStyle &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(color, Color);
    PARSE_FIELD(colorMode, ColorMode);
    return code;
}


TAKErr KMLParser::parseIconStyle(KMLIconStyle &obj) NOTHROWS {
    PARSE_PARENT(ColorStyle)
    PARSE_FIELD(scale, Float);
    PARSE_FIELD(heading, Float);
    BEGIN_OBJECT(Icon, Icon);
    PARSE_FIELD(hotSpot, Vec2);
    return code;
}

TAKErr KMLParser::parseLabelStyle(KMLLabelStyle &obj) NOTHROWS {
    PARSE_PARENT(ColorStyle)
    PARSE_FIELD(scale, Float);
    return code;
}

TAKErr KMLParser::parseLineStyle(KMLLineStyle &obj) NOTHROWS {
    PARSE_PARENT(ColorStyle)
    PARSE_FIELD(width, Float);
    PARSE_NS_FIELD(gx, outerColor, Color);
    PARSE_NS_FIELD(gx, outerWidth, Float);
    PARSE_NS_FIELD(gx, physicalWidth, Float);
    PARSE_NS_FIELD(gx, labelVisibility, Bool);

    return code;
}

TAKErr KMLParser::parsePolyStyle(KMLPolyStyle &obj) NOTHROWS {
    PARSE_PARENT(ColorStyle)
    PARSE_FIELD(fill, Bool);
    PARSE_FIELD(outline, Bool);
    return code;
}

TAKErr KMLParser::parseLink(KMLLink &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(href, Text);
    return code;
}

TAKErr KMLParser::parseIcon(KMLIcon &obj) NOTHROWS {
    PARSE_PARENT(Link);
    //TODO--
    return code;
}

TAKErr KMLParser::parseStyle(KMLStyle &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_OBJECT(BalloonStyle, BalloonStyle);
    PARSE_OBJECT(ListStyle, ListStyle);
    PARSE_OBJECT(LineStyle, LineStyle);
    PARSE_OBJECT(PolyStyle, PolyStyle);
    PARSE_OBJECT(IconStyle, IconStyle);
    PARSE_OBJECT(LabelStyle, LabelStyle);
    return code;
}

TAKErr KMLParser::parseStyleMap(KMLStyleMap &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_OBJECT_ALT(Pair, StyleMapPair);
    return code;
}

TAKErr KMLParser::parseStyleMapPair(KMLStyleMapPair &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(key, StyleState);
    PARSE_OBJECT(Style, Style);
    PARSE_FIELD(styleUrl, Text);
    return code;
}


TAKErr KMLParser::parseFeature(KMLFeature &obj) NOTHROWS {
    PARSE_PARENT(Object);
    PARSE_FIELD(name, Text);
    PARSE_FIELD(visibility, Bool);
    PARSE_FIELD(open, Bool);
    PARSE_FIELD(address, Text);
    PARSE_FIELD(phoneNumber, Text);
    PARSE_FIELD(Snippet, Text);
    PARSE_FIELD(description, Text);
    PARSE_FIELD(styleUrl, Text);
    PARSE_NS_FIELD(atom, author, Text);
    PARSE_NS_FIELD(atom, link, Text);
    PARSE_OBJECT(StyleSelector, Style);
    PARSE_OBJECT(StyleSelector, StyleMap);
    PARSE_OBJECT(TimePrimitive, TimeSpan);
    PARSE_FIELD(ExtendedData, ExtendedData);
    return code;
}

TAKErr KMLParser::parsePlacemark(KMLPlacemark &obj) NOTHROWS {
    PARSE_PARENT(Feature);
    PARSE_GEOM_OBJECTS();
    return code;
}

TAKErr KMLParser::parseContainer(KMLContainer &obj) NOTHROWS {
    PARSE_PARENT(Feature);
    BEGIN_OBJECT(Features, Document);
    BEGIN_OBJECT(Features, Folder);
    BEGIN_OBJECT(Features, Placemark);
    return code;
}

TAKErr KMLParser::parseDocument(KMLDocument &obj) NOTHROWS {
    PARSE_PARENT(Container);
    return code;
}

TAKErr KMLParser::parseFolder(KMLFolder &obj) NOTHROWS {
    PARSE_PARENT(Container);
    return code;
}

TAK::Engine::Util::TAKErr KMLParser::parseDOM(KMLDOM &obj) NOTHROWS {
    TAKErr code = parseObject(obj);
    if (code == TE_Ok && this->state == Object_end) {
        return TE_Done;
    }
    if (code != TE_Unsupported)
        return code;
    BEGIN_OBJECT(Root, Document);
    BEGIN_OBJECT(Root, Folder);
	BEGIN_OBJECT(Root, Placemark);
    return code;
}

TAKErr KMLParser::finishObject() NOTHROWS {

    size_t depth = stack.size();
    KMLEntity ety = entity();

	// ends right now
	const KMLObject *object = this->object();
	if (object && this->atEndTag(object->get_entity_name())) {
		state = Object_end;
		return TE_Ok;
	}

    TAKErr code;
    do {
        code = stepImpl();
        if (code == TE_Unsupported) {
            code = skipTag();
            TE_CHECKRETURN_CODE(code);
            code = TE_Ok;
        }

        // found the end
        if (entity() == ety && depth == stack.size() && state == Object_end)
            break;

    } while (code == TE_Ok);

    return code;

}

TAKErr KMLParser::skip() NOTHROWS {

    // skipping a field is a noop-- it is already read
    if (state >= Field_name) {
        return TE_Ok;
    }

    TAKErr code(TE_Ok);

    if (state == Unrecognized) {
        code = skipTag();
        TE_CHECKRETURN_CODE(code);
        state = Nil;
    } else if (state == Object_begin && object()) {
        code = readToTagEnd(object()->get_entity_name());
        TE_CHECKRETURN_CODE(code);
        state = Object_end;
    }

    return code;
}

TAKErr KMLParser::open(TAK::Engine::Util::DataInput2 &input, const char *filePath) NOTHROWS {
    close();
    TAKErr code = TE_Ok;
    TE_BEGIN_TRAP() {
        reader.reset(new XMLReaderImpl(input, filePath));
        this->store = true;
    } TE_END_TRAP(code);
    TE_CHECKRETURN_CODE(code);
    return code;
}

TAK::Engine::Util::TAKErr KMLParser::close() NOTHROWS {
    reader.reset();
    stack.clear();
    stack.shrink_to_fit();
    scratchText.clear();
    scratchText.shrink_to_fit();
    this->state = Nil;
    this->store = false;
    this->fObj.reset();
    return TE_Ok;
}

KMLParser::KMLParser()
    : state(Nil),
    store(true)
{ }

TAKErr KMLParser::step() NOTHROWS {

    TAKErr code = stepImpl();
    if (code == TE_Unsupported) {
        this->state = Unrecognized;
        // RWI - We don't currently support the Schema tag, but the old Kml Parser does
        if (this->tagName == "Schema") return code;
        return TE_Ok;
    }

    return code;
}

TAKErr KMLParser::stepImpl() NOTHROWS {
    if (!reader)
        return TE_IllegalState;

    TAKErr code(TE_Ok);

    if (state == Object_end)
        POP_OBJECT();

    if (state == Object_begin && atEndTag(stack.back()->get_entity_name()))
        return stack.back()->parse(*this);

    fObj.reset();
    state = Nil;

    code = readToTag();
    TE_CHECKRETURN_CODE(code);

    if (stack.size() == 0) {
        if (atBeginTag("kml")) {
            PUSH_OBJECT(DOM);
            return code;
        } else if (atBeginTag("Folder")) {
            PUSH_OBJECT(Folder);
            return code;
        } else if (atBeginTag("Document")) {
            PUSH_OBJECT(Document);
            return code;
        } else {
            return TE_Err;
        }
    }

    return stack.back()->parse(*this);
}

TAK::Engine::Util::TAKErr KMLParser::enableStore(bool enable) NOTHROWS {

    if (!reader)
        return TE_IllegalState;

    this->store = enable;
    return TE_Ok;
}

TAKErr KMLParser::skipToEntity(KMLEntity entity) NOTHROWS {
    
    return TE_Unsupported;

}

KMLParser::Position KMLParser::position() const NOTHROWS {
    return state;
}

KMLEntity KMLParser::entity() const NOTHROWS {
    if (stack.size() && stack.back().get())
        return stack.back()->get_entity();
    return KMLEntity_None;
}

const KMLObject *KMLParser::object() const NOTHROWS {
    if (stack.size() && stack.back().get())
        return stack.back().get();
    return nullptr;
}

KMLPtr<KMLObject> KMLParser::objectPtr() const NOTHROWS {
    if (stack.size() && stack.back().get())
        return stack.back();
    return KMLPtr<KMLObject>();
}

const KMLObject *KMLParser::fieldObject() const NOTHROWS {
    return fObj.get();
}

KMLPtr<KMLObject> KMLParser::fieldObjectPtr() const NOTHROWS {
    return fObj;
}