#include "feature/KMLDriverDefinition2.h"

#include <set>
#include <stack>

#include <libxml/xmlreader.h>

#include <ogr_feature.h>

#include <kml/base/zip_file.h>
#include <kml/engine/kmz_file.h>

#include "feature/Style.h"
#include "port/StringBuilder.h"
#include "util/IO2.h"
#include "util/Logging.h"
#include "util/Memory.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

#define LIBKML_DRIVER_NAME "LIBKML"

namespace
{
    typedef std::map<std::string, std::string> StyleMap;
    typedef std::map<std::string, std::set<std::string>> StyleDefinitions;
    typedef std::map<std::string, std::map<std::string, std::string>> PairsMap;

    TAKErr parseKmlColor(int *value, const char *colorStr) NOTHROWS;

    TAKErr checkAtTag(xmlTextReaderPtr xmlReader, const char *test) NOTHROWS;
    TAKErr readNodeNodeText(const xmlChar **value, xmlTextReaderPtr xmlReader) NOTHROWS;
    TAKErr parseStyles(StyleMap &value, PairsMap &styleMaps, xmlTextReaderPtr parser) NOTHROWS;
    TAKErr parseStyleMap(PairsMap &styleMaps, xmlTextReaderPtr parser) NOTHROWS;
    TAKErr parseStyleMapPair(std::map<std::string, std::string> &value, xmlTextReaderPtr parser) NOTHROWS;
    TAKErr parseStyle(StyleDefinitions &value, xmlTextReaderPtr parser) NOTHROWS;
    TAKErr parseIconStyle(StyleDefinitions &value, xmlTextReaderPtr parser, const std::string &styleId) NOTHROWS;
    TAKErr parseLineStyle(StyleDefinitions &value, xmlTextReaderPtr parser, const std::string &styleId) NOTHROWS;

    int abgr2argb(int abgr);
}

KMLDriverDefinition2::KMLDriverDefinition2(const char *path) NOTHROWS :
    DefaultDriverDefinition2(LIBKML_DRIVER_NAME, "kml", 1),
    filePath(path),
    styleParsed(false)
{}

TAKErr KMLDriverDefinition2::getStyle(Port::String &value, const OGRFeature &feature, const OGRGeometry &g) NOTHROWS
{
	value = const_cast<OGRFeature&> (feature).GetStyleString();
	if (!value || !value[0])
		return DefaultDriverDefinition2::getStyle(value, feature, g);

	return getStyle(value);
}

TAKErr KMLDriverDefinition2::getStyle(Port::String &value) NOTHROWS
{
    // check to see if the style is a link into the style table, if so look up
    // the entry
	if (value[0] == '@' || value[0] == '#')
    {
        // GDAL failed to parse the styles, we will need to parse ourselves
        if (!styleParsed)
        {
            xmlTextReaderPtr xmlReader(nullptr);

            array_ptr<char> xmlBytes;
            if (strstr(filePath, ".kmz")) {
#ifdef __MSC_VER
                try {
                    kmlengine::KmzFile *fz = kmlengine::KmzFile::OpenFromFile(filePath);
                    if (fz) {
                        kmlengine::KmzFilePtr kmz(fz);
                        std::string doc;
                        if (kmz->ReadKml(&doc)) {
                            xmlBytes.reset(new char[doc.length()]);
                            memcpy(xmlBytes.get(), doc.data(), doc.length());
                            xmlReader = xmlReaderForMemory(xmlBytes.get(), doc.length(), NULL, NULL, 0);
                        }
                    }
                } catch (...) {}
#else
                do {
                    std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> doc(nullptr, nullptr);
                    std::size_t docLen;
                    TAKErr code(TE_Ok);

                    // TODO: find "default" KML file, may not be "doc.kml"
                    code = IO_readZipEntry(doc, &docLen, filePath, "doc.kml");
                    TE_CHECKBREAK_CODE(code);

                    // convert to string
                    // XXX - character encoding
                    xmlBytes.reset(new char[docLen]);
                    memcpy(xmlBytes.get(), doc.get(), docLen);

                    // create reader from KML content
                    xmlReader = xmlReaderForMemory(xmlBytes.get(), static_cast<int>(docLen), nullptr, nullptr, 0);
                } while(false);
#endif
            } else {
                xmlReader = xmlReaderForFile(filePath, nullptr, 0);
            }

            if (xmlReader) {
                parseStyles(styles, styleMaps, xmlReader);
                xmlFreeTextReader(xmlReader);
            }
            styleParsed = true;
        }

        PairsMap::iterator styleMapEntry;
        styleMapEntry = styleMaps.find(value.get() + 1);
        if (styleMapEntry != styleMaps.end())
        {
            std::map<std::string, std::string>::iterator pairEntry;
            pairEntry = styleMapEntry->second.find("normal");
            if (pairEntry == styleMapEntry->second.end())
                pairEntry = styleMapEntry->second.begin();
            if (pairEntry != styleMapEntry->second.end())
            {
                // should be prefixed with '#', so we'll pass the substring
                // operation below
                value = pairEntry->second.c_str();
            }
        }

        StyleMap::iterator styleEntry;
        styleEntry = styles.find(value.get() + 1);
        if (styleEntry != styles.end())
        {
            value = styleEntry->second.c_str();
        }
    }

#ifdef __ANDROID__
    {
        const std::size_t len = strlen(value);
        for(std::size_t i = 0u; i < len; i++)
            if(value[i] == '\\')
                value[i] = '/';
    }
#endif

    //
    // If the Feature is in a KMZ, check for embedded icons and expand the icon
    // URI to an absolute path if appropriate.
    //
    
    if (TAK::Engine::Port::String_endsWith(filePath, "kmz"))
      {
        char* symbolStart(std::strstr(value.get(), "SYMBOL("));

        if (symbolStart)
        {
            char* idStart(std::strstr(symbolStart + 7, "id:"));

            if (idStart)
            {
                // Move past "id:"
                idStart += 3;

                char* idValueStart(idStart);
                char* idValueEnd(nullptr);

                // Is the id: param's value a string?
                // If so, find the end of said string
                if (*idValueStart == '"')
                {
                    idValueEnd = std::strchr(idValueStart + 1, '"');
                }

                // Only consider string-based id values as no others can contain paths
                // that we would need to parse and potentially modify. Non-strings
                // pass through unmodified
                if (idValueEnd) {
                    // Split the input at the double quote that begins the value string
                    *idValueStart = '\0';

                    // Move past the " to start of actual string value
                    idValueStart++;
                    
                    // Set up output buffer...
                    StringBuilder stringBuilder;

                    // Begin with everything from input up to our value string
                    stringBuilder << value << '"';

                    // The id value string can be a comma-delimited set of IDs.
                    // Unfortunately the spec is not clear on if or how values of said ids can have
                    // commas within their values (some sort of escape mechanism?) so we assume
                    // with this that no commas are embedded in the actual values.
                    // Split the ID string on any commas and individually consider each one.
                    bool firstIdValue = true;
                    std::stringstream wholeParam(std::string(idValueStart, idValueEnd));
                    std::string oneIdValue;
                    while (std::getline(wholeParam, oneIdValue, ',')) {
                        if (!firstIdValue)
                            stringBuilder << ",";
                        // Scan the value for a colon.
                        // If the value has no colon, it is considered relative and
                        // we prepend the kmz protocol and file path.
                        // If it has a colon, consider it absolute (ie, http://some.where/file.xyz)
                        // and do not update the value
                        if (oneIdValue.find(':') == std::string::npos)
                            stringBuilder << "zip://" << filePath << "!/";
                        stringBuilder << oneIdValue.c_str();
                        firstIdValue = false;
                    }

                    // Finally, append the closing quote and everything after
                    stringBuilder << idValueEnd;
                    value = stringBuilder.c_str();
                }
            }
        }
    }

    return TE_Ok;
}

bool KMLDriverDefinition2::layerNameIsPath() const NOTHROWS
{
    return true;
}

TAKErr KMLDriverDefinition2::createDefaultPointStyle(Port::String &value) const NOTHROWS
{
    value ="SYMBOL(id:http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png,c:#FFFFFFFF)";
    return TE_Ok;
}

TAKErr KMLDriverDefinition2::Spi::create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS
{
    value = OGRDriverDefinition2Ptr(new KMLDriverDefinition2(path), Memory_deleter_const<OGRDriverDefinition2, KMLDriverDefinition2>);
    return TE_Ok;
}

const char *KMLDriverDefinition2::Spi::getType() const NOTHROWS
{
    return LIBKML_DRIVER_NAME;
}

namespace
{

    TAKErr parseKMLColor(int *value, const char *colorStr)
    {
        if (!colorStr)
            return TE_InvalidArg;

        if (colorStr[0] == '#')
            colorStr++;

        try {
            unsigned int v;
            std::stringstream ss;
            ss << std::hex << colorStr;
            ss >> v;
            *value = abgr2argb(v);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }

    TAKErr checkAtTag(xmlTextReaderPtr xmlReader, const char *test) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const xmlChar *tagName = xmlTextReaderConstName(xmlReader);
        if (!tagName)
            return TE_InvalidArg;
        if (xmlStrcasecmp(tagName, BAD_CAST test))
            return TE_InvalidArg;
        return code;
    }

    TAKErr parseStyles(StyleMap &value, PairsMap &styleMaps, xmlTextReaderPtr parser) NOTHROWS
    {
        TAKErr code(TE_Ok);

        StyleDefinitions styleDefs;

        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
            {
                const xmlChar *nodeName = xmlTextReaderConstName(parser);
                if (xmlStrcasecmp(nodeName, BAD_CAST "Style") == 0) {
                    code = parseStyle(styleDefs, parser);
                    TE_CHECKBREAK_CODE(code);
                } else if (xmlStrcasecmp(nodeName, BAD_CAST "StyleMap") == 0) {
                    code = parseStyleMap(styleMaps, parser);
                    TE_CHECKBREAK_CODE(code);
                }
                break;
            }
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (true);
        TE_CHECKRETURN_CODE(code);

        StyleDefinitions::iterator def;
        for (def = styleDefs.begin(); def != styleDefs.end(); def++)
        {
            auto style = def->second.begin();
            if (def->second.size() == 1) {
                value[def->first] = *style;
            } else {
                std::ostringstream strm;
                strm << *style;
                style++;
                for (; style != def->second.end(); style++) {
                    strm << ';';
                    strm << *style;
                }
                value[def->first] = strm.str();
            }
        }
        return code;
    }

    TAKErr parseStyleMap(PairsMap &styleMaps, xmlTextReaderPtr parser) NOTHROWS
    {
        TAKErr code(TE_Ok);

        code = checkAtTag(parser, "StyleMap");
        TE_CHECKRETURN_CODE(code);

        std::stack<std::string> tagStack;
        tagStack.push((const char *)xmlTextReaderConstName(parser));

        const char *idAttr = (const char *)xmlTextReaderGetAttribute(parser, BAD_CAST "id");
        // no ID attribute, skip
        if (!idAttr)
            return code;
        std::string styleMapId(idAttr);
        std::map<std::string, std::string> pairs;
        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
            {
                const xmlChar *nodeName = xmlTextReaderConstName(parser);
                if (xmlStrcasecmp(nodeName, BAD_CAST "Pair") == 0) {
                    code = parseStyleMapPair(pairs, parser);
                    TE_CHECKBREAK_CODE(code);
                } else if(!xmlTextReaderIsEmptyElement(parser)) {
                    tagStack.push((const char *)nodeName);
                }
                break;
            }
            case XML_READER_TYPE_END_ELEMENT:
                tagStack.pop();
                break;
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (!tagStack.empty());
        TE_CHECKRETURN_CODE(code);

        PairsMap::iterator entry;
        entry = styleMaps.find(styleMapId);
        if (entry != styleMaps.end()) {
            // add the parsed pairs to the existing entry
            std::map<std::string, std::string>::iterator pairEntry;
            for (pairEntry = pairs.begin(); pairEntry != pairs.end(); pairEntry++)
                entry->second[pairEntry->first] = pairEntry->second;
        }
        else {
            styleMaps[styleMapId] = pairs;
        }

        return code;
    }

    TAKErr parseStyleMapPair(std::map<std::string, std::string> &value, xmlTextReaderPtr parser) NOTHROWS
    {
        TAKErr code(TE_Ok);

        code = checkAtTag(parser, "Pair");
        TE_CHECKRETURN_CODE(code);

        std::stack<std::string> tagStack;
        tagStack.push((const char *)xmlTextReaderConstName(parser));

        Port::String key;
        Port::String styleUrl;
        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
                if (!xmlTextReaderIsEmptyElement(parser))
                    tagStack.push((const char *)xmlTextReaderConstName(parser));
                break;
            case XML_READER_TYPE_END_ELEMENT:
                tagStack.pop();
                break;
            case XML_READER_TYPE_TEXT: {
                if (tagStack.empty())
                    return TE_IllegalState;

                std::string inTag = tagStack.top();
                if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "key") == 0) {
                    key = (const char *)xmlTextReaderValue(parser);
                } else if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "styleUrl") == 0) {
                    styleUrl = (const char *)xmlTextReaderValue(parser);
                }
                break;
            }
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (!tagStack.empty());

        if (!key || !styleUrl)
            return TE_Ok;

        value[key.get()] = styleUrl;
        return code;
    }

    TAKErr parseStyle(StyleDefinitions &value, xmlTextReaderPtr parser) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = checkAtTag(parser, "Style");
        TE_CHECKRETURN_CODE(code);

        std::stack<std::string> tagStack;
        tagStack.push((const char *)xmlTextReaderConstName(parser));

        const xmlChar *xstyleId = xmlTextReaderGetAttribute(parser, BAD_CAST "id");
        if (!xstyleId)
        {
            Logger::log(Logger::Warning, "No style id");
            return TE_Ok;
        }

        std::string styleId((const char *)xstyleId);
        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
            {
                const xmlChar *nodeName = xmlTextReaderConstName(parser);
                if (xmlStrcasecmp(nodeName, BAD_CAST "IconStyle") == 0) {
                    code = parseIconStyle(value, parser, styleId);
                    TE_CHECKBREAK_CODE(code);
                } else if (xmlStrcasecmp(nodeName, BAD_CAST "LineStyle") == 0) {
                    code = parseLineStyle(value, parser, styleId);
                    TE_CHECKBREAK_CODE(code);
                }
                // XXX - other styles
                else if (!xmlTextReaderIsEmptyElement(parser)) {
                    tagStack.push((const char *)nodeName);
                }
                break;
            }
            case XML_READER_TYPE_END_ELEMENT:
                tagStack.pop();
                break;
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (!tagStack.empty());
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    TAKErr parseIconStyle(StyleDefinitions &value, xmlTextReaderPtr parser, const std::string &styleId) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = checkAtTag(parser, "IconStyle");
        TE_CHECKRETURN_CODE(code);

        std::stack<std::string> tagStack;
        tagStack.push((const char *)xmlTextReaderConstName(parser));

        bool inIcon = false;
        Port::String href;
        float scale = 1.0;
        int color = -1;
        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
            {
                if (!xmlTextReaderIsEmptyElement(parser)) {
                    const xmlChar *nodeName = xmlTextReaderConstName(parser);
                    tagStack.push((const char *)nodeName);
                    if (xmlStrcasecmp(nodeName, BAD_CAST "Icon") == 0)
                        inIcon = true;
                }
                break;
            }
            case XML_READER_TYPE_END_ELEMENT:
            {
                std::string inTag = tagStack.top();
                if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "Icon") == 0)
                    inIcon = false;
                tagStack.pop();
                break;
            }
            case XML_READER_TYPE_TEXT:
            {
                if (tagStack.empty())
                    return TE_IllegalState;

                std::string inTag = tagStack.top();
                if (inIcon && TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "href") == 0) {
                    href = (const char *)xmlTextReaderValue(parser);
                } else if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "scale") == 0) {
                    scale = static_cast<float>(atof((const char *)xmlTextReaderValue(parser)));
                } else if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "color") == 0) {
                    parseKMLColor(&color, (const char *)xmlTextReaderValue(parser));
                }
                break;
            }
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (!tagStack.empty());

        if (!href)
            return TE_Ok;

        try {
            Port::String styleStr;
            atakmap::feature::IconPointStyle(color, href, scale).toOGR(styleStr);
            value[styleId].insert(styleStr.get());
        } catch (std::invalid_argument &) {
            code = TE_Err;
        }
        return code;
    }

    TAKErr parseLineStyle(StyleDefinitions &value, xmlTextReaderPtr parser, const std::string &styleId) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = checkAtTag(parser, "LineStyle");
        TE_CHECKRETURN_CODE(code);

        std::stack<std::string> tagStack;
        tagStack.push((const char *)xmlTextReaderConstName(parser));

        int color = -1;
        float width = 1;
        int success;
        do {
            success = xmlTextReaderRead(parser);
            if (success == 0)
                break;
            else if (success != 1)
                return TE_Err;

            int nodeType = xmlTextReaderNodeType(parser);
            switch (nodeType) {
            case XML_READER_TYPE_ELEMENT:
                if (!xmlTextReaderIsEmptyElement(parser))
                    tagStack.push((const char *)xmlTextReaderName(parser));
                break;
            case XML_READER_TYPE_END_ELEMENT:
                tagStack.pop();
                break;
            case XML_READER_TYPE_TEXT: {
                if (tagStack.empty())
                    return TE_IllegalState;

                std::string inTag = tagStack.top();
                if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "color") == 0) {
                    parseKMLColor(&color, (const char *)xmlTextReaderValue(parser));
                } else if (TAK::Engine::Port::String_strcasecmp(inTag.c_str(), "width") == 0) {
                    width = static_cast<float>(atof((const char *)xmlTextReaderValue(parser)));
                }
                break;
            }
            default:
                break;
            }
            TE_CHECKBREAK_CODE(code);
        } while (!tagStack.empty());

        Port::String styleStr;

        atakmap::feature::BasicStrokeStyle(color, width).toOGR(styleStr);
        value[styleId].insert(styleStr.get());
        return code;
    }

    int abgr2argb(int abgr)
    {
        return (abgr&0xFF000000) |
               ((abgr&0xFF)<<16) |
               (abgr&0x0000FF00) |
               ((abgr&0x00FF0000)>>16);
    }
}
