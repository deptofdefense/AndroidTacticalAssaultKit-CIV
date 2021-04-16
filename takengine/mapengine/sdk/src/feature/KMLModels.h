#ifndef TAK_ENGINE_FEATURE_KMLMODELS_H_INCLUDED
#define TAK_ENGINE_FEATURE_KMLMODELS_H_INCLUDED

#include <vector>
#include <deque>
#include <list>
#include <string>
#include "util/Memory.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {

            class KMLParser;

            enum {
                L0Shift = 5,
                L0Mask = 0x1f,

                L1Shift = 10,
                L1Mask = 0x1f,
            };

            enum KMLEntity {
                
                // L0
                KMLEntity_None,
                KMLEntity_DOM,
                KMLEntity_Feature,
                KMLEntity_Geometry,
                KMLEntity_Link,
                KMLEntity_Orientation,
                KMLEntity_Location,
                KMLEntity_Scale,
                KMLEntity_StyleSelector,
                KMLEntity_TimePrimitive,
                KMLEntity_AbstractView,
                KMLEntity_Region,
                KMLEntity_Lod,
                KMLEntity_LatLonBox,
                KMLEntity_LatLongAltBox,
                KMLEntity_GxLatLonQuad,
                KMLEntity_SubStyle,
                KMLEntity_GxTourPrimitive,
                KMLEntity_GxPlayList,
                KMLEntity_StyleMapPair,
                KMLEntity_gxCoord,

                // L1
                KMLEntity_Icon = KMLEntity_Link + (1 << L0Shift),

                KMLEntity_GxTour = KMLEntity_Feature + (1 << L0Shift),
                KMLEntity_NetworkLink = KMLEntity_Feature + (2 << L0Shift),
                KMLEntity_Placemark = KMLEntity_Feature + (3 << L0Shift),
                KMLEntity_Overlay = KMLEntity_Feature + (4 << L0Shift),
                KMLEntity_Container = KMLEntity_Feature + (5 << L0Shift),

                KMLEntity_Point = KMLEntity_Geometry + (1 << L0Shift),
                KMLEntity_LineString = KMLEntity_Geometry + (2 << L0Shift),
                KMLEntity_LinearRing = KMLEntity_Geometry + (3 << L0Shift),
                KMLEntity_Polygon = KMLEntity_Geometry + (4 << L0Shift),
                KMLEntity_MultiGeometry = KMLEntity_Geometry + (5 << L0Shift),
                KMLEntity_Model = KMLEntity_Geometry + (6 << L0Shift),
                KMLEntity_gxTrack = KMLEntity_Geometry + (7 << L0Shift),
                KMLEntity_GxMultiTrack = KMLEntity_Geometry + (8 << L0Shift),

                KMLEntity_Style = KMLEntity_StyleSelector + (1 << L0Shift),
                KMLEntity_StyleMap = KMLEntity_StyleSelector + (2 << L0Shift),

                KMLEntity_TimeSpan = KMLEntity_TimePrimitive + (1 << L0Shift),
                KMLEntity_TimeStamp = KMLEntity_TimePrimitive + (2 << L0Shift),

                KMLEntity_Camera = KMLEntity_AbstractView + (1 << L0Shift),
                KMLEntity_LookAt = KMLEntity_AbstractView + (1 << L0Shift),

                KMLEntity_BalloonStyle = KMLEntity_SubStyle + (1 << L0Shift),
                KMLEntity_ListStyle = KMLEntity_SubStyle + (2 << L0Shift),
                KMLEntity_ColorStyle = KMLEntity_SubStyle + (3 << L0Shift),

                KMLEntity_GxAnimatedUpdate = KMLEntity_GxTourPrimitive + (1 << L0Shift),
                KMLEntity_GxFlyTo = KMLEntity_GxTourPrimitive + (2 << L0Shift),
                KMLEntity_GxSoundCue = KMLEntity_GxTourPrimitive + (3 << L0Shift),
                KMLEntity_GxTourControl = KMLEntity_GxTourPrimitive + (4 << L0Shift),
                KMLEntity_GxWait = KMLEntity_GxTourPrimitive + (5 << L0Shift),

                // L2
                KMLEntity_PhotoOverlay = KMLEntity_Overlay + (1 << L1Shift),
                KMLEntity_ScreenOverlay = KMLEntity_Overlay + (2 << L1Shift),
                KMLEntity_GroundOverlay = KMLEntity_Overlay + (3 << L1Shift),

                KMLEntity_Folder = KMLEntity_Container + (1 << L1Shift),
                KMLEntity_Document = KMLEntity_Container + (2 << L1Shift),

                KMLEntity_LineStyle = KMLEntity_ColorStyle + (1 << L1Shift),
                KMLEntity_PolyStyle = KMLEntity_ColorStyle + (2 << L1Shift),
                KMLEntity_IconStyle = KMLEntity_ColorStyle + (3 << L1Shift),
                KMLEntity_LabelStyle = KMLEntity_ColorStyle + (4 << L1Shift)
            };

            enum KMLItemIconMode {
                KMLItemIconMode_open,
                KMLItemIconMode_error,
                KMLItemIconMode_fetching0,
                KMLItemIconMode_fetching1,
                KMLItemIconMode_fetching2
            };

            enum KMLColorMode {
                KMLColorMode_default = 0,
                KMLColorMode_random,
            };

            enum KMLAltitudeMode {
                KMLAltitudeMode_clampToGround = 0,
                KMLAltitudeMode_relativeToGround,
                KMLAltitudeMode_absolute
            };

            struct KMLCoordinates {

                KMLCoordinates()
                    : dim(0) {}

                int get_dim() const { return dim; }

                inline std::vector<double> values_2d() const {
                    std::vector<double> res;
                    res.reserve((values.size() / 3) * 2);
                    for (size_t i = 0; i < values.size(); i += 3) {
                        res.push_back(values[i]);
                        res.push_back(values[i + 1]);
                    }
                    return res;
                }

                int dim;
                std::vector<double> values;
            };

            struct KMLVec2 {

                KMLVec2()
                    : x(0.0), y(0.0),
                    xunits(0), yunits(0)
                {}

                double x, y;
                int xunits, yunits;
            };

            template <typename T, T def = 0>
            struct KMLIntegral {

                KMLIntegral() : value(def), exists(false) {}
                KMLIntegral(const T &v) : value(v), exists(false) {}

                KMLIntegral(const KMLIntegral &) = default;
                KMLIntegral &operator=(const KMLIntegral &) = default;

                KMLIntegral operator=(T v) {
                    value = v;
                    exists = true;
                    return *this;
                }

                operator T() const {
                    return value;
                }

                bool is_specified() const {
                    return exists;
                }

                T value;
                bool exists;
            };

            struct KMLFloat {

                KMLFloat() : value(0.0), exists(false) {}
                KMLFloat(double v) : value(v), exists(false) {}

                KMLFloat(const KMLFloat &) = default;
                KMLFloat &operator=(const KMLFloat &) = default;

                KMLFloat &operator=(double v) {
                    value = v;
                    exists = true;
                    return *this;
                }

                operator double() const {
                    return value;
                }

                bool is_specified() const {
                    return exists;
                }

                double value;
                bool exists;
            };

            template <typename T>
            struct KMLValue {

                KMLValue() : value(), exists(false) {}
                KMLValue(const T &v) : value(v), exists(false) {}

                KMLValue(const KMLValue &) = default;
                KMLValue(KMLValue &&) = default;
                KMLValue &operator=(KMLValue &&) = default;
                KMLValue &operator=(const KMLValue &) = default;

                KMLValue &operator=(const T &v) {
                    value = v;
                    exists = true;
                    return *this;
                }

                KMLValue &operator=(T &&v) {
                    value = std::move(v);
                    exists = true;
                    return *this;
                }

                bool is_specified() const {
                    return exists;
                }

                operator T() const {
                    return value;
                }
                
                T value;
                bool exists;
            };

            struct ENGINE_API KMLObject {

                KMLObject(const char *objName, KMLEntity entity);

                virtual ~KMLObject();

                virtual TAK::Engine::Util::TAKErr parse(KMLParser &parser) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr parseAttrs(KMLParser &parser) NOTHROWS;

                inline const char *get_id() const { return id.c_str(); }
                inline const bool has_id() const { return id != ""; }
                inline const char *get_entity_name() const { return objectName; }
                inline KMLEntity get_entity() const { return entity; }

                inline bool is_container() const {
                    return entity == KMLEntity_Document ||
                        entity == KMLEntity_Folder;
                }

                int refs;
            private:
                KMLEntity entity;
                std::string id;
                const char *objectName;
            };

            template <typename T>
            class KMLPtr {
            public:
                KMLPtr() : obj(nullptr) { }
                KMLPtr(T *o) : obj(o) { }
                template <typename U> KMLPtr(U * o) : obj(o) { }
                KMLPtr(const KMLPtr &o) : obj(o.obj) { if (obj) obj->refs++; }
                template <typename U> KMLPtr(const KMLPtr<U> &o) : obj(o.get()) { if (obj) obj->refs++; }
                KMLPtr(KMLPtr &&o) NOTHROWS : obj(o.obj) { o.obj = nullptr; }
                template <typename U> KMLPtr(KMLPtr<U> &&o) : obj(o.get()) { o.release(); }

                ~KMLPtr() { reset(); }

                T *operator->() const { return obj; }
                T &operator*() const { return *obj; }

                KMLPtr &operator=(T *o) {
                    if (o) o->refs++;
                    reset();
                    obj = o;
                    return *this;
                }

                KMLPtr &operator=(const KMLPtr &o) {
                    if (o.obj) o.obj->refs++;
                    reset();
                    obj = o.obj;
                    return *this;
                }

                template <typename U>
                KMLPtr &operator=(const KMLPtr<U> &o) {
                    if (o.get()) o.get()->refs++;
                    reset();
                    obj = o.get();
                    return *this;
                }

                KMLPtr &operator=(KMLPtr &&o) NOTHROWS {
                    reset();
                    obj = o.obj;
                    o.obj = nullptr;
                    return *this;
                }

                template <typename U>
                KMLPtr &operator=(KMLPtr<U> &&o) {
                    reset();
                    obj = o.get();
                    o.release();
                    return *this;
                }

                void reset() { 
                    if (obj && --obj->refs == 0)
                        delete obj;
                    obj = nullptr; 
                }

                void release() {
                    obj = nullptr;
                }

                T *get() const { return obj; }

                operator bool() const { return obj != nullptr; }

                template <typename U>
                KMLPtr<U> as() const {
                    if (obj) obj->refs++;
                    return KMLPtr<U>(static_cast<U *>(obj));
                }

            private:
                T *obj;
            };

            template <typename T>
            struct KMLList {

                KMLList() {}

                KMLList(KMLList &&temp)
                    : values(std::move(temp.values))
                {}

                size_t num_items() const {
                    return values.size();
                }

                const T &operator[](size_t i) const {
                    return *static_cast<const T *>(values[i].get());
                }

                std::vector<KMLPtr<KMLObject>> values;
            };

#define KML_OBJ_BP(n, b) \
    KML##n(); \
    KML##n(KML##n &&) = default; \
    KML##n &operator=(KML##n &&t) NOTHROWS = default; \
    virtual ~KML##n() NOTHROWS; \
    virtual TAK::Engine::Util::TAKErr parse(KMLParser &impl) NOTHROWS;

#define KML_BASE_BP(n, b) \
    KML##n(const char *objName, KMLEntity entity); \
    KML##n(KML##n &&) = default; \
    KML##n &operator=(KML##n &&t) = default; \
    virtual ~KML##n();

#define KML_MID_BP(n, b) \
    KML_BASE_BP(n, b) \
    KML##n(); \
    virtual TAK::Engine::Util::TAKErr parse(KMLParser &impl) NOTHROWS;
	
            
            struct KMLGeometry : public KMLObject {
                KML_BASE_BP(Geometry, Object);
            };

            struct KMLMultiGeometry : public KMLGeometry {
                KML_OBJ_BP(MultiGeometry, Geometry);

                KMLList<KMLGeometry> Geometry;
            };

            struct KMLPoint : public KMLGeometry {
                KML_OBJ_BP(Point, Geometry);

                KMLIntegral<bool> extrude;
                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                KMLValue<KMLCoordinates> coordinates;
            };

            struct KMLLinearRing : public KMLGeometry {
                KML_OBJ_BP(LinearRing, Geometry);

                KMLFloat gx_altitudeOffset;
                KMLValue<bool> extrude;
                KMLValue<bool> tessellate;
                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                KMLValue<KMLCoordinates> coordinates;
            };

            struct KMLLineString : public KMLGeometry {
                KML_OBJ_BP(LineString, Geometry);

                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                KMLValue<bool> extrude;
                KMLValue<bool> tessellate;
                KMLValue<KMLCoordinates> coordinates;
            };

            struct KMLPolygon : public KMLGeometry {
                KML_OBJ_BP(Polygon, Geometry);

                KMLValue<bool> extrude;
                KMLValue<bool> tessellate;
                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                KMLPtr<KMLLinearRing> outerBoundaryIs;
                KMLList<KMLLinearRing> innerBoundaryIs;
            };

            struct KMLLocation {
                double longitude;
                double latitude;
                double altitude;
            };

            struct KMLOrientation {
				KMLOrientation() : heading(0.0), tilt(0.0), roll(0.0) {}
                double heading;
                double tilt;
                double roll;
            };

            struct KMLScale {
				KMLScale() : x(1.0), y(1.0), z(1.0) {}
                double x;
                double y;
                double z;
            };

            struct KMLAlias {
                KMLValue<std::string> targetHref;
                KMLValue<std::string> sourceHref;
            };

            struct KMLResourceMap {
                std::deque<KMLAlias> Alias;
            };

			struct KMLLink : public KMLObject {
				KML_MID_BP(Link, Object);

				KMLValue<std::string> href;
			};

            struct KMLModel : public KMLGeometry {
                KML_OBJ_BP(Model, Geometry);

                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                KMLValue<KMLLocation> Location;
                KMLValue<KMLOrientation> Orientation;
                KMLValue<KMLScale> Scale;
                KMLPtr<KMLLink> Link;
                KMLValue<KMLResourceMap> ResourceMap;
            };

            struct KMLgxcoord {
                double lat, lng, alt;
            };

            struct KMLgxAngles {
                double heading, tilt, roll;
            };

            struct KMLgxTrack : public KMLGeometry {
                KML_OBJ_BP(gxTrack, Geometry);

                KMLIntegral<KMLAltitudeMode, KMLAltitudeMode_clampToGround> altitudeMode;
                std::vector<KMLgxcoord> coords;
                KMLValue<KMLgxAngles> gxAngles;
            };

            struct KMLTimePrimitive : public KMLObject {
                KML_BASE_BP(TimePrimitive, Object);
            };

            struct KMLTimeSpan : public KMLTimePrimitive {
                KML_OBJ_BP(TimeSpan, TimePrimitive);

                KMLValue<std::string> begin;
                KMLValue<std::string> end;
            };

            struct KMLIcon : public KMLLink {
                KML_OBJ_BP(Icon, Link);
            };

            struct KMLSubStyle : public KMLObject {
                KML_BASE_BP(SubStyle, Object);
            };

            struct KMLColorStyle : public KMLSubStyle {
                KML_BASE_BP(ColorStyle, SubStyle);

                KMLIntegral<uint32_t, 0xffffffff> color;
                KMLIntegral<KMLColorMode, KMLColorMode_default> colorMode;
            };

            struct KMLIconStyle : public KMLColorStyle {
                KML_OBJ_BP(IconStyle, ColorStyle);

                KMLFloat scale;
                KMLFloat heading;
                KMLPtr<KMLIcon> Icon;
                KMLValue<KMLVec2> hotSpot;
            };

            struct KMLLabelStyle : public KMLColorStyle {
                KML_OBJ_BP(LabelStyle, ColorStyle);

                KMLFloat scale;
            };

            struct KMLLineStyle : public KMLColorStyle {
                KML_OBJ_BP(LineStyle, ColorStyle);

                KMLFloat width;
                KMLFloat gx_outerWidth;
                KMLFloat gx_physicalWidth;
                KMLIntegral<uint32_t> gx_outerColor;
                KMLIntegral<bool> gx_labelVisibility;
            };

            struct KMLPolyStyle : public KMLColorStyle {
                KML_OBJ_BP(PolyStyle, ColorStyle);

                KMLIntegral<bool> fill;
                KMLIntegral<bool> outline;
            };

            struct KMLBalloonStyle : public KMLSubStyle {
                KML_OBJ_BP(BalloonStyle, SubStyle);

                KMLValue<std::string> text;
                KMLIntegral<uint32_t> bgColor;
                KMLIntegral<uint32_t> textColor;
                KMLIntegral<int> displayMode;
            };


            struct KMLListStyle : public KMLSubStyle {
                KML_OBJ_BP(ListStyle, SubStyle);

                KMLIntegral<int> listItemType;
                KMLIntegral<uint32_t> bgColor;

                struct ItemIconImpl {
                    int state;
                    std::string href;
                };

                KMLValue<ItemIconImpl> ItemIcon;
            };

            struct KMLStyleSelector : public KMLObject {
                KML_BASE_BP(StyleSelector, Object);
            };

            struct KMLStyle : public KMLStyleSelector {
                KML_OBJ_BP(Style, StyleSelector);

                KMLPtr<KMLIconStyle> IconStyle;
                KMLPtr<KMLLabelStyle> LabelStyle;
                KMLPtr<KMLLineStyle> LineStyle;
                KMLPtr<KMLPolyStyle> PolyStyle;
                KMLPtr<KMLBalloonStyle> BalloonStyle;
                KMLPtr<KMLListStyle> ListStyle;
            };

            enum KMLStyleState {
                KMLStyleState_unknown,
                KMLStyleState_normal,
                KMLStyleState_highlight
            };

            struct KMLStyleMapPair : public KMLObject {
                KML_OBJ_BP(StyleMapPair, Object)
                KMLIntegral<KMLStyleState, KMLStyleState_unknown> key;
                KMLValue<std::string> styleUrl;
                KMLPtr<KMLStyle> Style;
            };

            struct KMLStyleMap : public KMLStyleSelector {
                KML_OBJ_BP(StyleMap, StyleSelector);
            
                std::deque<KMLPtr<KMLStyleMapPair>> Pair;
            };

            struct KMLData {
                KMLValue<std::string> name;
                KMLValue<std::string> value;
                KMLValue<std::string> displayName;
            };

            struct KMLExtendedData {
                std::deque<KMLData> Data;
            };

            struct KMLFeature : public KMLObject {
                KML_BASE_BP(Feature, Object);

                KMLValue<std::string> name;
                KMLIntegral<bool> visibility;
                KMLIntegral<bool> open;
                KMLValue<std::string> address;
                KMLValue<std::string> xal_AddressDetails;
                KMLValue<std::string> phoneNumber;
                KMLValue<std::string> Snippet;
                KMLValue<std::string> description;
                KMLValue<std::string> atom_author;
                KMLValue<std::string> atom_link;
                KMLValue<std::string> styleUrl;
                KMLValue<KMLExtendedData> ExtendedData;
                KMLList<KMLStyleSelector> StyleSelector;
                KMLPtr<KMLTimePrimitive> TimePrimitive;
            };

            struct KMLPlacemark : public KMLFeature {
                KML_OBJ_BP(Placemark, Feature);

                KMLPtr<KMLGeometry> Geometry;
            };

            struct KMLContainer : public KMLFeature {
                KML_BASE_BP(Container, Feature);

                std::deque<KMLPtr<KMLFeature>> Features;
            };

            struct KMLDocument : public KMLContainer {
                KML_OBJ_BP(Document, Container);
            };

            struct KMLFolder : public KMLContainer {
                KML_OBJ_BP(Folder, Container);
            };

            struct ENGINE_API KMLDOM : public KMLObject {
                KML_OBJ_BP(DOM, Object);

                KMLPtr<KMLObject> Root;
            };
        }
    }
}

#endif