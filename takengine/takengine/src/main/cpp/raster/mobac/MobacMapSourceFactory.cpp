
// libxml2
#include <libxml/xmlreader.h>

#include <sstream>

#include "raster/mobac/MobacMapSourceFactory.h"

#include "feature/Envelope.h"
//#include "private/Util.h"
#include "raster/mobac/CustomMobacMapSource.h"
#include "raster/mobac/CustomWmsMobacMapSource.h"
#include "raster/mobac/CustomMultiLayerMobacMapSource.h"
//#include "raster/mobac/CustomWmsMobacMapSource_CLI.h"
#include "util/IO.h"
#include "util/Logging.h"
#include "core/ProjectionFactory.h"
#include "core/Projection.h"

#include <cmath>

using namespace atakmap::raster::mobac;
using namespace atakmap::util;

namespace
{
    
    MobacMapSource *parseXmlMapSource(const char *f) /*throws IOException*/;
    MobacMapSource *parseEncryptedXmlMapSource(const char *f) /*throws IOException*/;
    MobacMapSource *parseXmlMapSource(xmlTextReaderPtr xmlReader) /*throws IOException*/;
    MobacMapSource *parseCustomMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/;
    MobacMapSource *parseCustomMultiLayerMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/;
    MobacMapSource *parseCustomWmsMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/;
    void checkAtTag(xmlTextReaderPtr xmlReader, const char *tagName) /*throws XmlPullParserException*/;
    void decode(const uint8_t *src, uint8_t *dst, size_t len);

#ifdef MSVC
    inline int strcasecmp(const char* lhs, const char* rhs)
    {
        return PGSC::strcasecmp(lhs, rhs);
    }
#endif

}

MobacMapSourceFactory::MobacMapSourceFactory()
{}

MobacMapSource *MobacMapSourceFactory::create(const char *f) /*throws IOException*/
{
    //TODO(bergeron)-- more robust checks
    const char *ext = strrchr(f, '.');
    if (strcasecmp(ext, ".xml") == 0) {
        return parseXmlMapSource(f);
    } else if (strcasecmp(ext, ".xmle") ==0) {
        return parseEncryptedXmlMapSource(f);
    }
    return nullptr;
}

MobacMapSource *MobacMapSourceFactory::create(const char *f, const MobacMapSource::Config &config) /*throws IOException*/
{
    MobacMapSource *mmc = create(f);
    if (mmc != nullptr)
        mmc->setConfig(config);
    return mmc;
}


namespace
{
    MobacMapSource *parseXmlMapSource(const char *f) /*throws IOException*/ {
        xmlTextReaderPtr xmlReader = xmlReaderForFile(f, NULL, 0);
        if (!xmlReader) {
            return NULL;
        }
        MobacMapSource *mms = parseXmlMapSource(xmlReader);
        xmlFreeTextReader(xmlReader);
        return mms;
    }
    
    MobacMapSource *parseEncryptedXmlMapSource(const char *f) /*throws IOException*/ {
        
        unsigned long fileSize = atakmap::util::getFileSize(f);
        
        // don't really ever expect this
        if (fileSize > INT_MAX) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "encrypted mobac source too large '%s'", f);
            return nullptr;
        }
        
        int intFileSize = fileSize & INT_MAX;
        
        atakmap::util::FileInput input;
        input.open(f);
        
        std::vector<uint8_t> decodeBuffer(intFileSize - 1);
        {
            std::vector<uint8_t> encodedBuffer(intFileSize);
            input.readFully(encodedBuffer.data(), intFileSize);
            input.close();
            decode(encodedBuffer.data(), decodeBuffer.data(), intFileSize);
        }
        
        xmlTextReaderPtr xmlReader = xmlReaderForMemory((const char *)decodeBuffer.data(), (int)decodeBuffer.size(), f, NULL, 0);
        MobacMapSource *mms = parseXmlMapSource(xmlReader);
        xmlFreeTextReader(xmlReader);
        return mms;
    }
    
    MobacMapSource *parseXmlMapSource(xmlTextReaderPtr xmlReader) /*throws IOException*/ {
            MobacMapSource *retval = NULL;
            int readResult;
            while ((readResult = xmlTextReaderRead(xmlReader)) == 1) {
            
                const xmlChar *name = xmlTextReaderConstName(xmlReader);
                if (xmlStrEqual(name, BAD_CAST "customMapSource")) {
                    retval = parseCustomMapSource(xmlReader);
                } else if (xmlStrEqual(name, BAD_CAST "customWmsMapSource")) {
                    retval = parseCustomWmsMapSource(xmlReader);
                } else if (xmlStrEqual(name, BAD_CAST "customMultiLayerMapSource")) {
                    retval = parseCustomMultiLayerMapSource(xmlReader);
                }
            }
            return retval;
    }
    
    const xmlChar *readNodeNodeText(xmlTextReaderPtr xmlReader) {
        // TODO(bergeronj)-- more checks
        if (xmlTextReaderRead(xmlReader) != 1) {
            throw std::invalid_argument("unexpected error");
        }
        
        int nodeType = xmlTextReaderNodeType(xmlReader);
        if (nodeType != XML_READER_TYPE_TEXT &&
            nodeType != XML_READER_TYPE_SIGNIFICANT_WHITESPACE &&
            nodeType != XML_READER_TYPE_END_ELEMENT) {
            throw std::invalid_argument("unexpected node type");
        }
        
        return xmlTextReaderConstValue(xmlReader);
    }
    
    std::vector<std::string> parseServerParts(const char *serverPartsValue) {
        std::vector<std::string> v;
        if (serverPartsValue) {
            std::stringstream ss;
            bool needCommit = true;
            for (const char *p = serverPartsValue; *p; ++p) {
                if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r') {
                    if (needCommit) {
                        v.push_back(ss.str());
                        ss.clear();
                        ss.str("");
                        needCommit = false;
                    }
                } else {
                    ss << *p;
                    needCommit = true;
                }
            }
            
            if (needCommit) {
                v.push_back(ss.str());
            }
        }
        return v;
    }
    
    MobacMapSource *parseCustomMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/ {
        checkAtTag(xmlReader, "customMapSource");
        
        int minZoom = 0;
        int maxZoom = -1;
        PGSC::String name;
        PGSC::String url;
        PGSC::String type;
        std::vector<const char *> serverParts;
        std::vector<std::string> serverPartsCleaup;
        bool invertYAxis = false;
        int backgroundColor = 0;
        do {
            if (xmlTextReaderRead(xmlReader) == -1)
                return NULL;
            
            int nodeType = xmlTextReaderNodeType(xmlReader);
            const xmlChar *inNode;
            
            switch (nodeType) {
                case XML_READER_TYPE_ELEMENT:
                    inNode = xmlTextReaderConstName(xmlReader);
                    if (xmlStrEqual(inNode, BAD_CAST "name")) {
                        name = (const char *)readNodeNodeText(xmlReader);
                    } else if (xmlStrEqual(inNode, BAD_CAST "url")) {
                        url = (const char *)readNodeNodeText(xmlReader);
                    } else if (xmlStrEqual(inNode, BAD_CAST "minZoom")) {
                        minZoom = atoi((const char *)readNodeNodeText(xmlReader));
                    } else if (xmlStrEqual(inNode, BAD_CAST "maxZoom")) {
                        maxZoom = atoi((const char *)readNodeNodeText(xmlReader));
                    } else if (xmlStrEqual(inNode, BAD_CAST "tileType")) {
                        type = (const char *)readNodeNodeText(xmlReader);
                    } else if (xmlStrEqual(inNode, BAD_CAST "serverParts")) {
                        serverPartsCleaup = parseServerParts((const char *)readNodeNodeText(xmlReader));
                        for (int i = 0; i < serverPartsCleaup.size(); ++i) {
                            serverParts.push_back(serverPartsCleaup[i].c_str());
                        }
                    } else if (xmlStrEqual(inNode, BAD_CAST "invertYCoordinate")) {
                        invertYAxis = xmlStrEqual(readNodeNodeText(xmlReader), BAD_CAST "true");
                    } else if (xmlStrEqual(inNode, BAD_CAST "backgroundColor")) {
                        //TODO(bergeronj)--
                    }
            }
            
            
        } while (xmlTextReaderDepth(xmlReader) > 0);
        
        if (!name || !url || maxZoom == -1)
            return NULL;
        
        return new CustomMobacMapSource(name,
                                        256,
                                        minZoom,
                                        maxZoom,
                                        type,
                                        url,
                                        serverParts.size() > 0 ? &serverParts[0] : NULL,
                                        serverParts.size(),
                                        backgroundColor,
                                        invertYAxis);
    }
    
    MobacMapSource *parseCustomMultiLayerMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/
    {
        checkAtTag(xmlReader, "customMultiLayerMapSource");
        
        /*TODO(bergeronj)--const char *name = nullptr;
        std::vector<MobacMapSource *> layers;
        int backgroundColor = 0;
        //array<float> ^layersAlpha = nullptr;
        std::vector<float> layersAlpha;
        
        do {
            if (xmlTextReaderRead(xmlReader) != 1) {
                throw std::invalid_argument("unexpected error"); //gcnew Exception("Unexpected end of document.");
            }
            
            switch (parser->NodeType) {
                case XmlNodeType::Element:
                    if (tagStack.Count < 1)
                        throw gcnew Exception("Illegal State");
                    
                    if (tagStack.Peek()->Equals("layers")) {
                        if (parser->Name->Equals("customMapSource"))
                            layers.Add(parseCustomMapSource(parser));
                        else if (parser->Name->Equals("customWmsMapSource"))
                            layers.Add(parseCustomWmsMapSource(parser));
                        else if (parser->Name->Equals("customMultiLayerMapSource"))
                            layers.Add(parseCustomMultiLayerMapSource(parser));
                    }
                    else {
                        if (!parser->IsEmptyElement)
                            tagStack.Push(parser->Name);
                    }
                    break;
                case XmlNodeType::EndElement:
                    tagStack.Pop();
                    break;
                case XmlNodeType::Text: {
                    if (tagStack.Count < 1)
                        throw gcnew Exception("Illegal State");
                    const char * const inTag = tagStack.Peek();
                    if (inTag->Equals("name")) {
                        name = parser->Value;
                    }
                    else if (inTag->Equals("backgroundColor")) {
                        if (Regex::IsMatch(parser->Value, "\\#[0-9A-Fa-f]"))
                            backgroundColor = Convert::ToInt32(parser->Value->Substring(1), 16);
                    }
                    else if (inTag->Equals("layersAlpha")) {
                        array<Char> ^chars = { ' ' };
                        array<const char *> ^splits = parser->Value->Split(chars);
                        layersAlpha = gcnew array<float>(splits->Length);
                        bool allValid = true;
                        for (int i = 0; i < splits->Length; i++) {
                            allValid &= Regex::IsMatch(splits[i], "\\d+(\\.\\d+)?");
                            if (!allValid)
                                break;
                            layersAlpha[i] = (float)Double::Parse(splits[i]);
                        }
                        if (!allValid)
                            layersAlpha = nullptr;
                    }
                    break;
                }
                default:
                    break;
            }
        } while (tagStack.Count > 0);
        
        if (name == nullptr || (layersAlpha != nullptr && layersAlpha->Length != layers.Count))
            throw gcnew Exception();
        
        return gcnew CustomMultiLayerMobacMapSource(name,
                                                    Collection::ToArray(%layers),
                                                    layersAlpha,
                                                    backgroundColor);*/
        return NULL;
    }
    
    MobacMapSource *parseCustomWmsMapSource(xmlTextReaderPtr xmlReader) /*throws XmlPullParserException, IOException*/
    {
        checkAtTag(xmlReader, "customWmsMapSource");
        
        std::vector<std::string> tagStack;
        const char *elemName = (const char *)xmlTextReaderConstName(xmlReader);
        tagStack.push_back(std::string(elemName));
        
        int minZoom = -1;
        int maxZoom = -1;
        int backgroundColor = 0;
        std::string url;
        int srid = 4326;
        std::string layers;
        std::string style;
        std::string additionalParameters;
        std::string version;
        std::string name;
        std::string tileFormat;
        std::string coordinateSystem;
        
        double north = NAN;
        double south = NAN;
        double east = NAN;
        double west = NAN;
        
        bool touchedFourCorners[4] = { false, false, false, false };
        
        do {
            
            if (xmlTextReaderRead(xmlReader) == -1)
                return NULL;
            
            int nodeType = xmlTextReaderNodeType(xmlReader);
            
            switch (nodeType) {
                case XML_READER_TYPE_ELEMENT:
                    if (!xmlTextReaderIsEmptyElement(xmlReader)) {
                        tagStack.push_back(std::string((const char *)xmlTextReaderConstName(xmlReader)));
                    }
                    break;
                case XML_READER_TYPE_END_ELEMENT:
                    tagStack.pop_back();
                    break;
                case XML_READER_TYPE_TEXT:
                    if (tagStack.back() == "minZoom") {
                        minZoom = atoi((const char *)xmlTextReaderConstValue(xmlReader));
                    }
                    else if (tagStack.back() == "maxZoom") {
                        maxZoom = atoi((const char *)xmlTextReaderConstValue(xmlReader));
                    }
                    else if (tagStack.back() == "backgroundColor") {
                      //  if (Regex::IsMatch(parser->Value, "\\#[0-9A-Fa-f]"))
                      //      backgroundColor = System::Convert::ToInt32(parser->Value->Substring(1), 16);
                        const char *value = (const char *)xmlTextReaderConstValue(xmlReader);
                        if (value && *value == '#') {
                            backgroundColor = static_cast<int>(strtol(value + 1, nullptr, 16));
                        }
                    }
                    else if (tagStack.back() == "url") {
                        //TODO-- url = System::Web::HttpUtility::HtmlDecode(parser->Value);
                        url = (const char *)xmlTextReaderConstValue(xmlReader);
                    }
                    else if (tagStack.back() == "coordinatesystem") {
                        coordinateSystem = (const char *)xmlTextReaderConstValue(xmlReader);
                        
                        /*TODO-- if (Regex::IsMatch(parser->Value, "EPSG\\:\\d+")) {
                            array<Char> ^chars = { ':' };
                            srid = System::Int32::Parse(parser->Value->Split(chars)[1]);
                        }*/
                    }
                    else if (tagStack.back() == "layers") {
                        layers = (const char *)xmlTextReaderConstValue(xmlReader);//parser->Value;
                    }
                    else if (tagStack.back() == "styles") {
                        style = (const char *)xmlTextReaderConstValue(xmlReader);
                    }
                    else if (tagStack.back() == "additionalparameters"
                             || tagStack.back() == "aditionalparameters") {
                    //TODO--    additionalParameters = System::Web::HttpUtility::HtmlDecode(parser->Value);
                        const char *ap = (const char *)xmlTextReaderConstValue(xmlReader);
                        additionalParameters = ap;
                    }
                    else if (tagStack.back() == "name") {
                        name = (const char *)xmlTextReaderConstValue(xmlReader);
                    }
                    else if (tagStack.back() == "tileType") {
                        tileFormat = (const char *)xmlTextReaderConstValue(xmlReader);
                        tileFormat = toUpperCase(tileFormat);
                    }
                    else if (tagStack.back() == "version") {
                        version = (const char *)xmlTextReaderConstValue(xmlReader);
                    }
                    else if (tagStack.back() == "north") {
                        const char *value = (const char *)xmlTextReaderConstValue(xmlReader);
                        north = strtod(value, nullptr);
                        touchedFourCorners[0] = true;
                    }
                    else if (tagStack.back() == "south") {
                        const char *value = (const char *)xmlTextReaderConstValue(xmlReader);
                        south = strtod(value, nullptr);
                        touchedFourCorners[1] = true;
                    }
                    else if (tagStack.back() == "east") {
                        const char *value = (const char *)xmlTextReaderConstValue(xmlReader);
                        east = strtod(value, nullptr);
                        touchedFourCorners[2] = true;
                    }
                    else if (tagStack.back() == "west") {
                        const char *value = (const char *)xmlTextReaderConstValue(xmlReader);
                        west = strtod(value, nullptr);
                        touchedFourCorners[3] = true;
                    }
                    break;
                default:
                    break;
            }
        } while (tagStack.size() > 0);
        
        /*TODO--if (name == nullptr || url == nullptr || layers == nullptr || maxZoom == -1 || tileFormat == nullptr)
            throw gcnew Exception("customWmsMapSource definition does not contain required elements.");*/
        
        if (coordinateSystem.size() > 5 &&
            coordinateSystem.compare(0, 5, "EPSG:") == 0) {
            
            coordinateSystem.erase(coordinateSystem.begin(), coordinateSystem.begin() + 5);
            int parsedSrid = atoi(coordinateSystem.c_str());
            if (!parsedSrid) {
                return nullptr;
            }
            srid = parsedSrid;
        }
        
        if (!touchedFourCorners[0] &&
            !touchedFourCorners[1] &&
            !touchedFourCorners[2] &&
            !touchedFourCorners[3]) {
            
            atakmap::core::Projection *proj = atakmap::core::ProjectionFactory::getProjection(srid);
            if (proj) {
                south = proj->getMinLatitude();
                north = proj->getMaxLatitude();
                west = proj->getMinLongitude();
                east = proj->getMaxLongitude();
            } else {
                north = 90.0;
                south = -90.0;
                east = 180.0;
                west = -180.0;
            }
        }
        
        if (!isnan(north) &&
            !isnan(east) &&
            !isnan(south) &&
            !isnan(west)) {

            atakmap::feature::Envelope bounds(west, south, 0, east, north, 0);
            
            return new CustomWmsMobacMapSource(name.c_str(),
                                               srid,
                                               256,
                                               minZoom,
                                               maxZoom,
                                               tileFormat.c_str(),
                                               url.c_str(),
                                               layers.c_str(),
                                               style.c_str(),
                                               version.c_str(),
                                               additionalParameters.c_str(),
                                               backgroundColor,
                                               &bounds);
        }
        return NULL;
    }
    
    void checkAtTag(xmlTextReaderPtr xmlReader, const char *tagName) /*throws XmlPullParserException*/ {
        if (!xmlStrEqual(xmlTextReaderConstName(xmlReader), BAD_CAST tagName)) {
            throw std::invalid_argument("illegal state");
        }
    }
    
    /*************************************************************************/
    
    typedef struct fib_state_t {
        int f;
        int fb;
    } fib_state_t;
    
    int next(fib_state_t *s) {
        const int r = s->f;
        s->f += s->fb;
        s->fb = r;
        return r;
    }
    
    void nth(fib_state_t *state, int n) {
        state->f = 1;
        state->fb = 0;
        
        for (int i = 0; i < n; i++)
            next(state);
    }
    
    class FibLeak {
    public:
        FibLeak(int n) {
            nth(&state, n);
            current = 0;
        }
        
        ~FibLeak() {}
        
        int leak() {
            if (!current) {
                current = next(&state);
            }
            const int r = current % 10;
            current /= 10;
            return r;
        }
    private:
        fib_state_t state;
        int current;
    };
    
    const int TOGGLE_MASKS[] = { 0x00, 0x01, 0x02, 0x04,
        0x08, 0x10, 0x20, 0x80 };
    
    void decode(const uint8_t *src, uint8_t *dst, size_t len)
    {
        FibLeak leak(src[0] & 0xFF);
        int shift;
        int toggle;
        int s;
        for (size_t i = 0; i < len - 1; i++) {
            s = src[i + 1] & 0xFF;
            shift = (leak.leak() & 0x07);
            toggle = (leak.leak() & 0x07);
            s ^= TOGGLE_MASKS[toggle];
            s = ((s << shift) | (s >> (8 - shift))) & 0xFF;
            dst[i] = (uint8_t)s;
        }
    }
}

