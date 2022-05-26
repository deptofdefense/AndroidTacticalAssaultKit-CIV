#ifndef TAK_ENGINE_FEATURE_KMLPARSER_H_INCLUDED
#define TAK_ENGINE_FEATURE_KMLPARSER_H_INCLUDED

#include <vector>
#include "util/DataInput2.h"
#include "port/String.h"
#include "port/Set.h"
#include "feature/KMLModels.h"

namespace TAK {
    namespace Engine {
        namespace Feature {


            /**
             * A KML parser that is memory efficient by streaming the DOM, with the option of parsing the
             * whole current object at any point in the stream.
             */
            class ENGINE_API KMLParser {
            public:
                struct Impl;

                enum Position {
                    Nil,
                    Unrecognized,

                    Object_begin,
                    Object_end,

                    Field_name,
                    Field_address,
                    Field_visibility,
                    Field_open,
                    Field_atom_author,
                    Field_atom_link,
                    Field_xal_AddressDetails,
                    Field_phoneNumber,
                    Field_Snippet,
                    Field_description,
                    Field_styleUrl,
                    Field_Region,
                    Field_Metadata,
                    Field_ExtendedData,
                    Field_color,
                    Field_drawOrder,
                    Field_Icon,
                    Field_extrude,
                    Field_altitudeMode,
                    Field_coordinates,
                    Field_gx_altitudeOffset,
                    Field_tessellate,
                    Field_gx_drawOrder,
                    Field_outerBoundaryIs,
                    Field_innerBoundaryIs,
                    Field_Location,
                    Field_Orientation,
                    Field_Scale,
                    Field_Link,
                    Field_ResourceMap,
                    Field_when,
                    Field_gx_coord,
                    Field_gx_angles,
                    Field_gx_interpolate,
                    Field_colorMode,
                    Field_StyleSelector,
                    Field_TimePrimitive,
                    Field_Geometry,
                    Field_Point,
                    Field_MultiGeometry,
                    Field_width,
                    Field_textColor,
                    Field_text,
                    Field_scale,
                    Field_PolyStyle,
                    Field_outline,
                    Field_gx_physicalWidth,
                    Field_gx_outerWidth,
                    Field_gx_link,
                    Field_begin,
                    Field_gx_outerColor,
                    Field_heading,
                    Field_hotSpot,
                    Field_end,
                    Field_gx_author,
                    Field_altitudeOffset,
                    Field_ListStyle,
                    Field_LineStyle,
                    Field_LabelStyle,
                    Field_IconStyle,
                    Field_href,
                    Field_gx_labelVisibility,
                    Field_fill,
                    Field_bgColor,
                    Field_BalloonStyle,
                    Field_Pair,
                    Field_Style,
                    Field_key,
                    Field_coords,
                };

                KMLParser();
                ~KMLParser();

                // File
                TAK::Engine::Util::TAKErr open(TAK::Engine::Util::DataInput2 &input, const char *filePath) NOTHROWS;
                TAK::Engine::Util::TAKErr close() NOTHROWS;

                // Control
                TAK::Engine::Util::TAKErr step() NOTHROWS;
                TAK::Engine::Util::TAKErr skipToEntity(KMLEntity entity) NOTHROWS;
                TAK::Engine::Util::TAKErr finishObject() NOTHROWS;
                TAK::Engine::Util::TAKErr skip() NOTHROWS;
                TAK::Engine::Util::TAKErr enableStore(bool enable) NOTHROWS;

                // State
                Position position() const NOTHROWS;
                KMLEntity entity() const NOTHROWS;
                const KMLObject *object() const NOTHROWS;
                const KMLObject *fieldObject() const NOTHROWS;
                KMLPtr<KMLObject> fieldObjectPtr() const NOTHROWS;
                KMLPtr<KMLObject> objectPtr() const NOTHROWS;

                // Content
                TAK::Engine::Util::TAKErr parseAttrText(const char *name, std::string &text) NOTHROWS;
                TAK::Engine::Util::TAKErr parseText(std::string &text) NOTHROWS;
                TAK::Engine::Util::TAKErr parseBool(bool &text) NOTHROWS;
                TAK::Engine::Util::TAKErr parseColor(uint32_t &color) NOTHROWS;
                TAK::Engine::Util::TAKErr parseColorMode(KMLColorMode &mode) NOTHROWS;
                TAK::Engine::Util::TAKErr parseFloat(double &val) NOTHROWS;
                TAK::Engine::Util::TAKErr parseVec2(KMLVec2 &vec2) NOTHROWS;
                TAK::Engine::Util::TAKErr parseAltitudeMode(KMLAltitudeMode &mode) NOTHROWS;
                TAK::Engine::Util::TAKErr parseCoordinates(KMLCoordinates &coords) NOTHROWS;
                TAK::Engine::Util::TAKErr parseStyleState(KMLStyleState &parsed_state) NOTHROWS;
                TAK::Engine::Util::TAKErr parseLocation(KMLLocation &location) NOTHROWS;
                TAK::Engine::Util::TAKErr parseOrientation(KMLOrientation &orientation) NOTHROWS;
                TAK::Engine::Util::TAKErr parseScale(KMLScale &scale) NOTHROWS;
                TAK::Engine::Util::TAKErr parseAlias(KMLAlias &alias) NOTHROWS;
                TAK::Engine::Util::TAKErr parseResourceMap(KMLResourceMap &resourceMap) NOTHROWS;
                TAK::Engine::Util::TAKErr parseExtendedData(KMLExtendedData &extendedData) NOTHROWS;
                TAK::Engine::Util::TAKErr parseData(KMLData &data) NOTHROWS;
                TAK::Engine::Util::TAKErr parsegxcoord(std::vector<KMLgxcoord> &coord) NOTHROWS;

                // SubStyle
                TAK::Engine::Util::TAKErr parseColorStyle(KMLColorStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseBalloonStyle(KMLBalloonStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseIconStyle(KMLIconStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseLineStyle(KMLLineStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseListStyle(KMLListStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseLabelStyle(KMLLabelStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parsePolyStyle(KMLPolyStyle &obj) NOTHROWS;
                
                // StyleSelector
                TAK::Engine::Util::TAKErr parseStyle(KMLStyle &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseStyleMap(KMLStyleMap &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseStyleMapPair(KMLStyleMapPair &obj) NOTHROWS;

                // Geometry
                TAK::Engine::Util::TAKErr parseLinearRing(KMLLinearRing &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parsePolygon(KMLPolygon &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseMultiGeometry(KMLMultiGeometry &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseModel(KMLModel &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseLineString(KMLLineString &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parsegxTrack(KMLgxTrack &obj) NOTHROWS;

                // TimePrimitive
                TAK::Engine::Util::TAKErr parseTimeSpan(KMLTimeSpan &obj) NOTHROWS;

                // Link -- Icon
                TAK::Engine::Util::TAKErr parseLink(KMLLink &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseIcon(KMLIcon &obj) NOTHROWS;

                TAK::Engine::Util::TAKErr parseObject(KMLObject &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseGeometry(KMLGeometry &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parsePoint(KMLPoint &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseContainer(KMLContainer &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parsePlacemark(KMLPlacemark &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseFeature(KMLFeature &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseDocument(KMLDocument &obj) NOTHROWS;
                TAK::Engine::Util::TAKErr parseFolder(KMLFolder &obj) NOTHROWS;

                TAK::Engine::Util::TAKErr parseDOM(KMLDOM &obj) NOTHROWS;

            public:
                TAK::Engine::Util::TAKErr stepImpl() NOTHROWS;
                TAK::Engine::Util::TAKErr readToTagEnd(const char *name) NOTHROWS;
                TAK::Engine::Util::TAKErr readToTag() NOTHROWS;
                TAK::Engine::Util::TAKErr skipTag() NOTHROWS;
                bool atTag(const char *name) const NOTHROWS;
                bool atBeginTag() const NOTHROWS;
                bool atBeginTag(const char *name) const NOTHROWS;
                bool atEndTag() const NOTHROWS;
                bool atEndTag(const char *name) const NOTHROWS;

                struct XMLReader {
                    virtual ~XMLReader();
                };

                TAK::Engine::Util::TAKErr readXML() NOTHROWS;
                int typeXML() const NOTHROWS;
                const char *valueXML() const NOTHROWS;
                const char *nameXML() const NOTHROWS;
                bool isEmptyTag() const NOTHROWS;

            private:
                std::unique_ptr<XMLReader> reader;

            private:
                std::vector<KMLPtr<KMLObject>> stack;
                KMLPtr<KMLObject> fObj;
                Position state;
                std::string scratchText;
                std::string tagName;
                bool store;
            };

        }
    }
}

#endif