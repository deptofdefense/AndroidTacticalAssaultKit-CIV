#include "formats/pfps/FalconViewFeatureDataSource.h"

#include <chrono>
#include <ctime>

#include "core/GeoPoint2.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "db/Database2.h"
#include "db/DatabaseFactory.h"
#include "db/Query.h"
#include "feature/FeatureDefinition2.h"
#include "feature/Geometry.h"
#include "feature/GeometryCollection.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/Style.h"
#include "formats/msaccess/MsAccessDatabaseFactory.h"
#include "math/Point2.h"
#include "math/Vector4.h"
#include "util/GeomagneticField.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"

#define TAG "FalconViewFeatureDataSource"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::MsAccess;
using namespace TAK::Engine::Formats::PFPS;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

#define COLUMN_DRW_NUM 0u
#define COLUMN_DRW_TYPE 1u
#define COLUMN_DRW_DATA 2u

#define COLUMN_LPT_ID 0u
#define COLUMN_LPT_DESC 1u
#define COLUMN_LPT_LAT 2u
#define COLUMN_LPT_LNG 3u
#define COLUMN_LPT_ELEV 4u
#define COLUMN_LPT_ICON 13u

#define SIN35 0.573576

#define LPT_ICON_URI "asset://lpticons/"
#define DEFAULT_LPT_ICON "blue local"
#define FALCONVIEW_PROVIDER "falconview"

const double MAX_RESOLUTION = 0.0;

namespace {
enum class DataType {
    Type = 1,
    MoveTo = 3,
    LineTo = 4,
    Center = 5,
    Text = 6,
    Font = 8,
    Tooltip = 9,
    Help = 10,
    Comment = 11,
    Color = 12,
    TextParam = 13,
    LinkName = 14,
    LineParam = 15,
    FixText = 16,
    FixBearing = 17,
    Font2 = 18,
    TextParam2 = 19,
    Color2 = 20,
    LabelColor = 20,
    Color3 = 21,
    BullseyeLabelColor = 21,
    LinkArrayName = 22,
    LabelParam = 23,
    Label = 24,
    LabelOffset = 25,
    LabelFontName = 26,
    Group = 27,
    PictureOffset = 28,
    PictureScreenXY = 29,
    PictureDispName = 30,
    PictureSrcName = 31,
    LLGeo = 32,
    URGeo = 33,
    PictureSize = 34,
    MapSource = 35,
    MapSeries = 36,
    MapScale = 37,
    MapZoom = 38,
    ImgOffset = 39
};

enum class ShapeType {
    Polyline = 1,
    Ellipse = 3,
    Text = 4,
    Bullseye = 6,
    Rectangle = 7,
    Axis = 8,
};

// Currently only support for Basic/Solid Fill
enum class FillStyle {
    None = 0,
    HorizontalStripe = 1,
    VerticalStripe = 2,
    BackDiagonalStripe = 3,
    ForwardDiagonalStripe = 4,
    CrossHatch = 5,
    DiagonalCrossHatch = 6,
    Solid = 7,
    Shaded = 8
};

enum class LineStyle {
    Solid = 0,          // Solid
    Dot = 1,            // Dashes??? Always 1 pixel thick, no outline
    Dash = 2,           // Dots!?!?! Always 1 pixel thick, no outline
    DashDot = 3,        // Invisible
    DashDotDot = 4,     // Invisible
    Railroad = 101,     //-+-+-+-+-+-
    PowerLine = 102,    //-T-T-T-T-T-
    ZigZag = 103,       // Sawtooth wave
    ArrowStart = 104,   //<----------
    ArrowEnd = 105,     //---------->
    FebaStart = 106,    // Up triangles on a line
    FebaEnd = 107,      // Down triangles on a line
    FlotStart = 108,    // Up bumps on a line
    FlotEnd = 109,      // Down bumps on a line
    Dash2 = 113,        // Actual dashes
    DashDot2 = 114,     //-�-�-�-�-�-�
    LongDash = 115,     // Actual long dashes
    TMark = 116,        // Solid?
    Diamond = 117,      //..........
    Arrow = 118,        //->->->->->
    BorderStart = 121,  // Line with up hash marks
    BorderEnd = 122,    // Line with down hash marks
    Notched = 123,      // Square wave
    Flot2Start = 124,   // Arches
    Flot2End = 125,     // Bowls
    Flot3Start = 126,   // Dotted Arches
    Flot3End = 127,     // Dotted Bowls
    Feba2Start = 128,   // Up triangles
    Feba2End = 129,     // Down triangles
    Wire = 130,         //-X-X-X-X-X-
    Arrow2Start = 131,  //<-<-<-<-<-<-
    Arrow2End = 132,    //->->->->->->
    Xor = 998,
    None = 999
};

enum class LineType { Simple = 1, Rhumb = 2, Great = 3 };

enum class Anchor {
    LowerLeft = '1',
    UpperLeft = '2',
    LowerCenter = '3',
    UpperCenter = '5',
    LowerRight = '6',
    UpperRight = '8',
    CenterLeft = 'A',
    CenterRight = 'B',
    CenterCenter = 'C'
};

class FalconViewDrwFileFeatureDefinition : public FeatureDefinition2 {
   public:
    FalconViewDrwFileFeatureDefinition(const String &name, GeometryPtr geometry, StylePtr style,
                                       std::unique_ptr<atakmap::util::AttributeSet> attributes) NOTHROWS;
    TAKErr getRawGeometry(RawData *value) NOTHROWS override;
    GeometryEncoding getGeomCoding() NOTHROWS override;
    AltitudeMode getAltitudeMode() NOTHROWS override;
    double getExtrude() NOTHROWS override;
    TAKErr getName(const char **value) NOTHROWS override;
    StyleEncoding getStyleCoding() NOTHROWS override;
    TAKErr getRawStyle(RawData *value) NOTHROWS override;
    TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
    TAKErr get(const Feature2 **feature) NOTHROWS override;

   private:
    String name_;
    GeometryPtr geometry_;
    StylePtr style_;
    std::unique_ptr<atakmap::util::AttributeSet> attributes_;
    FeaturePtr_const feature_;
};

class FalconViewDrwFileContent : public FeatureDataSource2::Content {
   public:
    FalconViewDrwFileContent(DatabasePtr &&database, TAK::Engine::Port::String name) NOTHROWS;
    ~FalconViewDrwFileContent() NOTHROWS override;

   public:
    const char *getType() const NOTHROWS override;
    const char *getProvider() const NOTHROWS override;
    TAKErr moveToNextFeature() NOTHROWS override;
    TAKErr moveToNextFeatureSet() NOTHROWS override;
    TAKErr get(FeatureDefinition2 **feature) const NOTHROWS override;
    TAKErr getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS override;
    TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS override;
    TAKErr getMinResolution(double *value) const NOTHROWS override;
    TAKErr getMaxResolution(double *value) const NOTHROWS override;
    TAKErr getVisible(bool *visible) const NOTHROWS override;

   private:
    TAKErr parsePolyline() NOTHROWS;
    TAKErr parseEllipse() NOTHROWS;
    TAKErr parseText() NOTHROWS;
    TAKErr parseBullseye() NOTHROWS;
    TAKErr parseRectangle() NOTHROWS;
    TAKErr parseAxis() NOTHROWS;
    void parseGeneral(const DataType &data_type, std::unique_ptr<atakmap::util::AttributeSet> &attributes) NOTHROWS;
    void parseColor(int *fore_color_rgb, int *back_color_rgb) NOTHROWS;
    TAKErr parseLatitudeLongitudeFromString(double *latitude, double *longitude) NOTHROWS;
    TAKErr getRgbColorFromColorData(int *rgb, int color) NOTHROWS;
    atakmap::feature::LineString createLineString(const std::vector<GeoPoint2> &geo_points);
    atakmap::feature::Geometry *createGeometry(const std::vector<GeoPoint2> &geo_points, bool closed);
    atakmap::feature::Style *createStyle(LineStyle line_style, FillStyle fill_style, int stroke_rgb, int fill_rgb, float stroke_width,
                                         bool closed, bool include_label = false);
    atakmap::feature::Style *createStrokeStyle(LineStyle line_style, int stroke_rgb, float stroke_width);
    std::string getData() NOTHROWS;

   private:
    bool complete_;
    int feature_set_count_;
    DatabasePtr database_;
    QueryPtr cursor_;
    std::string feature_name_;
    std::string feature_set_name_;
    int64_t item_num_;
    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> feature_definition_;
};

class FalconViewLptFeatureDefinition : public FeatureDefinition2 {
   public:
    FalconViewLptFeatureDefinition(const String &id, const String &desc, double lat, double lng, int elev, const String &icon) NOTHROWS;
    TAKErr getRawGeometry(RawData *value) NOTHROWS override;
    GeometryEncoding getGeomCoding() NOTHROWS override;
    AltitudeMode getAltitudeMode() NOTHROWS override;
    double getExtrude() NOTHROWS override;
    TAKErr getName(const char **value) NOTHROWS override;
    StyleEncoding getStyleCoding() NOTHROWS override;
    TAKErr getRawStyle(RawData *value) NOTHROWS override;
    TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
    TAKErr get(const Feature2 **feature) NOTHROWS override;

   private:
    String getIconUri(const String &icon_name) NOTHROWS;

   private:
    String name_;
    GeometryPtr geometry_;
    StylePtr style_;
    atakmap::util::AttributeSet attributes_;
    FeaturePtr_const feature_;
};

class FalconViewLptContent : public FeatureDataSource2::Content {
   public:
    FalconViewLptContent(DatabasePtr &&database, TAK::Engine::Port::String name) NOTHROWS;
    ~FalconViewLptContent() NOTHROWS override;

   public:
    const char *getType() const NOTHROWS override;
    const char *getProvider() const NOTHROWS override;
    TAKErr moveToNextFeature() NOTHROWS override;
    TAKErr moveToNextFeatureSet() NOTHROWS override;
    TAKErr get(FeatureDefinition2 **feature) const NOTHROWS override;
    TAKErr getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS override;
    TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS override;
    TAKErr getMinResolution(double *value) const NOTHROWS override;
    TAKErr getMaxResolution(double *value) const NOTHROWS override;
    TAKErr getVisible(bool *visible) const NOTHROWS override;

   private:
    int feature_set_count_;
    DatabasePtr database_;
    QueryPtr cursor_;
    TAK::Engine::Port::String feature_set_name_;
    std::unique_ptr<FalconViewLptFeatureDefinition, void (*)(const FalconViewLptFeatureDefinition *)> feature_definition_;
};
}  // namespace

FalconViewFeatureDataSource::FalconViewFeatureDataSource() NOTHROWS {}

TAKErr FalconViewFeatureDataSource::parse(FeatureDataSource2::ContentPtr &content, const char *file) NOTHROWS {
    if (!file) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, TAG "Received NULL filePath");
        return TE_InvalidArg;
    }

    Port::String ext;
    TAKErr code = IO_getExt(ext, file);
    TE_CHECKRETURN_CODE(code);

    int lpt = -1;
    int drw = -1;
    String_compareIgnoreCase(&lpt, ext, ".lpt");
    String_compareIgnoreCase(&drw, ext, ".drw");
    const bool is_lpt = (lpt == 0);
    const bool is_drw = (drw == 0);

    if (is_drw || is_lpt) {
        DatabasePtr db(nullptr, nullptr);
        DatabaseInformation info(file, nullptr, DATABASE_OPTIONS_READONLY);
        code = MsAccessDatabaseFactory_create(db, info);
        if (code != TE_Ok) return code;

        const char *uri;
        code = info.getUri(&uri);
        TE_CHECKRETURN_CODE(code);
        Port::String name;
        code = IO_getName(name, uri);
        TE_CHECKRETURN_CODE(code);

        if (is_drw) {
            content = FeatureDataSource2::ContentPtr(new FalconViewDrwFileContent(std::move(db), name),
                                                     Memory_deleter_const<FeatureDataSource2::Content, FalconViewDrwFileContent>);
            return TE_Ok;
        } else if (is_lpt) {
            content = FeatureDataSource2::ContentPtr(new FalconViewLptContent(std::move(db), name),
                                                     Memory_deleter_const<FeatureDataSource2::Content, FalconViewLptContent>);
            return TE_Ok;
        }
    }

    return TE_Err;
}

const char *FalconViewFeatureDataSource::getName() const NOTHROWS { return FALCONVIEW_PROVIDER; }

int FalconViewFeatureDataSource::parseVersion() const NOTHROWS { return 1; }

namespace {
FalconViewDrwFileContent::FalconViewDrwFileContent(DatabasePtr &&database, TAK::Engine::Port::String name) NOTHROWS
    : complete_(false),
      feature_set_count_(0),
      database_(std::move(database)),
      cursor_(nullptr, nullptr),
      feature_set_name_(name),
      item_num_(0),
      feature_definition_(nullptr, nullptr) {
    database_->query(cursor_, "select * from Main");
    cursor_->moveToNext();
}

FalconViewDrwFileContent::~FalconViewDrwFileContent() NOTHROWS {}

const char *FalconViewDrwFileContent::getType() const NOTHROWS { return "drw"; }
const char *FalconViewDrwFileContent::getProvider() const NOTHROWS { return FALCONVIEW_PROVIDER; }

TAKErr FalconViewDrwFileContent::moveToNextFeature() NOTHROWS {
    if (complete_) return TE_Done;

    TAKErr code = cursor_->getLong(&item_num_, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    int64_t item_num(0);
    do {
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);

        std::string data = getData();

        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            case DataType::Type: {
                if (data.length() < 2) return TE_Err;
                std::string shape_type_string = data.substr(0, 2);
                const auto fv_shape_type = static_cast<ShapeType>(atoi(shape_type_string.c_str()));
                switch (fv_shape_type) {
                    case ShapeType::Axis:
                        code = parseAxis();
                        goto feature_complete;
                        break;
                    case ShapeType::Bullseye:
                        code = parseBullseye();
                        goto feature_complete;
                        break;
                    case ShapeType::Ellipse:
                        code = parseEllipse();
                        goto feature_complete;
                        break;
                    case ShapeType::Polyline:
                        code = parsePolyline();
                        goto feature_complete;
                        break;
                    case ShapeType::Rectangle:
                        code = parseRectangle();
                        goto feature_complete;
                        break;
                    case ShapeType::Text:
                        code = parseText();
                        goto feature_complete;
                        break;
                }
            } break;
            default:
                code = cursor_->moveToNext();
                TE_CHECKBREAK_CODE(code);
                code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
                TE_CHECKBREAK_CODE(code);
                break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

feature_complete:
    if (code == TE_Done) {
        complete_ = true;
        return TE_Ok;
    }
    return code;
}

TAKErr FalconViewDrwFileContent::parsePolyline() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    std::string initial_data = getData();
    if (initial_data.length() < 13) return TE_Err;
    const float stroke_width = (float)atof(initial_data.substr(2, 2).c_str());
    const LineStyle line_style = static_cast<LineStyle>(atoi(initial_data.substr(4, 3).c_str()));
    const FillStyle fill_style = static_cast<FillStyle>(atoi(initial_data.substr(7, 1).c_str()));
    LineType line_type = static_cast<LineType>(atoi(initial_data.substr(8, 1).c_str()));
    bool has_label = initial_data[12] == 'Y';
    bool closed_poly = initial_data[11] == 'Y';

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    std::vector<GeoPoint2> geo_points;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    if (attributes.get() == nullptr) return TE_OutOfMemory;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;
        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                parseGeneral(fv_data_type, attributes);
                break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::MoveTo:
            case DataType::LineTo: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    geo_points.push_back(GeoPoint2(lat, lng));
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    if (geo_points.empty()) return TE_InvalidArg;

    if (closed_poly) {
        if (!(geo_points[0] == geo_points[geo_points.size() - 1])) geo_points.push_back(geo_points[0]);
    }

    atakmap::feature::Geometry *geometry = createGeometry(geo_points, closed_poly);
    atakmap::feature::Style *style = createStyle(line_style, fill_style, fore_color_rgb, back_color_rgb, stroke_width, closed_poly);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewDrwFileContent::parseEllipse() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    std::string initial_data = getData();
    if (initial_data.length() < 19) return TE_Err;
    const float stroke_width = (float)atof(initial_data.substr(2, 2).c_str());
    const FillStyle fill_style = static_cast<FillStyle>(atoi(initial_data.substr(5, 1).c_str()));
    const double azimuth = atof(initial_data.substr(6, 3).c_str());
    double major_axis = atof(initial_data.substr(9, 5).c_str());
    double minor_axis = atof(initial_data.substr(14, 5).c_str());
    major_axis = major_axis > 0 ? major_axis * 1000 : 1;
    minor_axis = minor_axis > 0 ? minor_axis * 1000 : 1;

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    GeoPoint2 center_point;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    if (attributes.get() == nullptr) return TE_OutOfMemory;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                parseGeneral(fv_data_type, attributes);
                break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::Center: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    center_point.latitude = lat;
                    center_point.longitude = lng;
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    std::vector<GeoPoint2> geo_points;
    const double arc = M_PI * 2;
    const double maxe = std::max(major_axis, minor_axis);
    const double mine = std::min(major_axis, minor_axis);
    const double tessellation_factor = std::max(360, (int)(40.0 * (maxe / mine)));
    double step = arc / tessellation_factor;  // 1/40

    for (double t = 0; t < arc; t += step) {
        double x = major_axis * cos(t);
        double y = minor_axis * sin(t);
        double r = (major_axis * minor_axis) / sqrt(x * x + y * y);
        geo_points.push_back(GeoPoint2_pointAtDistance(center_point, azimuth + (t * 57.295779513082320876798154814105), r, false));
    }

    atakmap::feature::Geometry *geometry;
    bool include_label = false;
    if (feature_name_.length() > 0) {
        include_label = true;

        auto *multi_geometry = new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::Dimension::_2D);
        multi_geometry->add(createGeometry(geo_points, true));
        multi_geometry->add(new atakmap::feature::Point(center_point.longitude, center_point.latitude));
        geometry = multi_geometry;
    } else {
        geometry = createGeometry(geo_points, true);
    }

    atakmap::feature::Style *style =
        createStyle(LineStyle::Solid, fill_style, fore_color_rgb, back_color_rgb, stroke_width, true, include_label);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewDrwFileContent::parseText() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    GeoPoint2 center_point;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    float text_size(0.0f);
    bool absolute_rotation(false);
    float rotation(0.0f);
    auto horizontal_alignment(atakmap::feature::LabelPointStyle::HorizontalAlignment::H_CENTER);
    auto vertical_alignment(atakmap::feature::LabelPointStyle::VerticalAlignment::V_CENTER);
    atakmap::feature::LabelPointStyle::Style font_style = (atakmap::feature::LabelPointStyle::Style)0x0;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                break;
            case DataType::Text: {
                std::string data = getData();
                if (feature_name_.length() == 0)
                    feature_name_ = data;
                else
                    feature_name_ += data;
            } break;
            case DataType::TextParam: {
                std::string data = getData();
                if (data.length() < 23) return TE_Err;
                // text_size = atof(data.substr(0, 3).c_str());
                auto anchor = static_cast<Anchor>(data[7]);
                rotation = (float)atof(data.substr(8, 3).c_str());
                font_style = (atakmap::feature::LabelPointStyle::Style)atoi(data.substr(20, 2).c_str());
                absolute_rotation = data[22] == 'Y';
                switch (anchor) {
                    case Anchor::CenterCenter:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::H_CENTER;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::V_CENTER;
                        break;
                    case Anchor::CenterLeft:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::LEFT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::V_CENTER;
                        break;
                    case Anchor::CenterRight:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::RIGHT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::V_CENTER;
                        break;
                    case Anchor::LowerCenter:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::H_CENTER;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::BELOW;
                        break;
                    case Anchor::LowerLeft:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::LEFT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::BELOW;
                        break;
                    case Anchor::LowerRight:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::RIGHT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::BELOW;
                        break;
                    case Anchor::UpperCenter:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::H_CENTER;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::ABOVE;
                        break;
                    case Anchor::UpperLeft:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::LEFT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::ABOVE;
                        break;
                    case Anchor::UpperRight:
                        horizontal_alignment = atakmap::feature::LabelPointStyle::HorizontalAlignment::RIGHT;
                        vertical_alignment = atakmap::feature::LabelPointStyle::VerticalAlignment::ABOVE;
                        break;
                }
            } break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::Center: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    center_point.latitude = lat;
                    center_point.longitude = lng;
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    const double arc = 360;
    auto *geometry = new atakmap::feature::Point(center_point.longitude, center_point.latitude);

    atakmap::feature::Style *style = new atakmap::feature::LabelPointStyle(
        feature_name_.c_str(), fore_color_rgb, back_color_rgb, atakmap::feature::LabelPointStyle::ScrollMode::DEFAULT, nullptr, text_size,
        font_style, 0.0f, 0.0f, horizontal_alignment, vertical_alignment, rotation, absolute_rotation);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewDrwFileContent::parseBullseye() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    std::string initial_data = getData();
    if (initial_data.length() < 23) return TE_Err;
    const float stroke_width = (float)atof(initial_data.substr(2, 2).c_str());
    const int ring_count = atoi(initial_data.substr(4, 2).c_str());
    const double radial_spacing = atof(initial_data.substr(6, 2).c_str());
    const double ring_spacing = atof(initial_data.substr(8, 5).c_str()) * 1000;
    const bool use_magvar = initial_data[19] == 'Y';
    double azimuth = use_magvar ? atof(initial_data.substr(13, 5).c_str()) : 0.0;
    const bool show_outline = initial_data[20] == 'Y';
    const int flags = initial_data[22] - '0';
    const bool show_radials = (flags & 0x01) != 0;
    const bool show_rings = (flags & 0x02) != 0;
    const bool minimized = (flags & 0x04) != 0;

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    GeoPoint2 center_point;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    if (attributes.get() == nullptr) return TE_OutOfMemory;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                parseGeneral(fv_data_type, attributes);
                break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::Center: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    center_point.latitude = lat;
                    center_point.longitude = lng;
                    if (!use_magvar) {
                        double mag_decl;
                        auto now = std::chrono::system_clock::now();
                        std::time_t now_c = std::chrono::system_clock::to_time_t(now);
                        struct tm *tm = std::localtime(&now_c);
                        if (GeomagneticField_getDeclination(&mag_decl, center_point, tm->tm_year, tm->tm_mon, tm->tm_mday) == TE_Ok)
                            azimuth = mag_decl;
                    }
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    // Build Rings
    const double arc = 360;
    auto *multi_geometry = new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::Dimension::_2D);
    for (int i = (ring_count - 1); i >= 0; i--) {
        std::vector<GeoPoint2> geo_points;
        const double range = ring_spacing * (i + 1);
        const double step = arc / (32 * (i + 1));

        for (double t = 0; t < arc; t += step) {
            double bearing = t + azimuth;
            geo_points.push_back(GeoPoint2_pointAtDistance(center_point, bearing, range, false));
        }

        multi_geometry->add(createGeometry(geo_points, true));
    }

    // Build Cardinals
    // Arrow
    const double range = ring_spacing * ring_count;
    const double arrow_range = range * 1.2;
    auto end_point = GeoPoint2_pointAtDistance(center_point, azimuth, range, false);
    auto arrow_tip_point = GeoPoint2_pointAtDistance(center_point, azimuth, arrow_range, false);
    const double arrow_length = arrow_range - range;

    std::vector<GeoPoint2> arrow_points;
    arrow_points.push_back(end_point);
    arrow_points.push_back(arrow_tip_point);
    multi_geometry->add(createGeometry(arrow_points, false));
    arrow_points.clear();

    arrow_points.push_back(GeoPoint2_pointAtDistance(arrow_tip_point, azimuth + 235, arrow_length, false));
    arrow_points.push_back(arrow_tip_point);
    multi_geometry->add(createGeometry(arrow_points, false));
    arrow_points.clear();

    arrow_points.push_back(GeoPoint2_pointAtDistance(arrow_tip_point, azimuth - 235, arrow_length, false));
    arrow_points.push_back(arrow_tip_point);
    multi_geometry->add(createGeometry(arrow_points, false));
    arrow_points.clear();

    if (show_radials) {
        for (int i = 0; i < 4; i++) {
            std::vector<GeoPoint2> radial_points;
            radial_points.push_back(center_point);
            radial_points.push_back(GeoPoint2_pointAtDistance(center_point, 90 * i + azimuth, range, false));
            multi_geometry->add(createGeometry(radial_points, false));
        }
    }

    // Cross
    for (int i = 0; i < 4; i++) {
        std::vector<GeoPoint2> cross_points;
        cross_points.push_back(center_point);
        cross_points.push_back(GeoPoint2_pointAtDistance(center_point, 90 * i + azimuth, range * 0.1, false));
        multi_geometry->add(createGeometry(cross_points, false));
    }

    // Radials
    if (radial_spacing > 0) {
        for (int i = 0; i < 360; i += static_cast<int>(radial_spacing)) {
            if (i == 0 || i == 90 || i == 180 || i == 270) continue;
            std::vector<GeoPoint2> radial_points;
            radial_points.push_back(center_point);
            radial_points.push_back(GeoPoint2_pointAtDistance(center_point, i + azimuth, range, false));
            multi_geometry->add(createGeometry(radial_points, false));
        }
    }

    if (feature_name_.length() == 0 && attributes->containsAttribute("tooltip")) {
        feature_name_ = attributes->getString("tooltip");
    }

    multi_geometry->add(new atakmap::feature::Point(center_point.longitude, center_point.latitude));

    atakmap::feature::Style *style = createStyle(LineStyle::Solid, FillStyle::None, fore_color_rgb, back_color_rgb, stroke_width, true);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(multi_geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewDrwFileContent::parseRectangle() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    std::string initial_data = getData();
    if (initial_data.length() < 19) return TE_Err;
    const float stroke_width = (float)atof(initial_data.substr(2, 2).c_str());
    const FillStyle fill_style = static_cast<FillStyle>(atoi(initial_data.substr(5, 1).c_str()));
    const double azimuth = atof(initial_data.substr(6, 3).c_str());
    const double length = atof(initial_data.substr(9, 5).c_str()) * 1000;
    const double width = atof(initial_data.substr(14, 5).c_str()) * 1000;

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    GeoPoint2 center_point;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    if (attributes.get() == nullptr) return TE_OutOfMemory;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                parseGeneral(fv_data_type, attributes);
                break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::Center: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    center_point.latitude = lat;
                    center_point.longitude = lng;
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    double hw = width / 2;
    double hl = length / 2;
    double r = sqrt((hw * hw) + (hl * hl));
    double ang = atan(hw / hl) * (180 / M_PI);

    std::vector<GeoPoint2> geo_points;
    geo_points.push_back(GeoPoint2_pointAtDistance(center_point, azimuth - ang, r, false));
    geo_points.push_back(GeoPoint2_pointAtDistance(center_point, azimuth + 180 + ang, r, false));
    geo_points.push_back(GeoPoint2_pointAtDistance(center_point, azimuth + 180 - ang, r, false));
    geo_points.push_back(GeoPoint2_pointAtDistance(center_point, azimuth + ang, r, false));

    atakmap::feature::Geometry *geometry;
    bool include_label = false;
    if (feature_name_.length() > 0) {
        include_label = true;

        auto *multi_geometry = new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::Dimension::_2D);
        multi_geometry->add(createGeometry(geo_points, true));
        multi_geometry->add(new atakmap::feature::Point(center_point.longitude, center_point.latitude));
        geometry = multi_geometry;
    } else {
        geometry = createGeometry(geo_points, true);
    }

    atakmap::feature::Style *style =
        createStyle(LineStyle::Solid, fill_style, fore_color_rgb, back_color_rgb, stroke_width, true, include_label);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewDrwFileContent::parseAxis() NOTHROWS {
    TAKErr code;
    int64_t item_num(0);

    code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
    TE_CHECKRETURN_CODE(code);

    auto initial_data = getData();
    if (initial_data.length() < 12) return TE_Err;
    const float stroke_width = (float)atof(initial_data.substr(2, 2).c_str());
    const FillStyle fill_style = static_cast<FillStyle>(atoi(initial_data.substr(5, 1).c_str()));
    const double width_ratio = atof(initial_data.substr(6, 5).c_str());
    const bool cross = initial_data[11] == 'Y';

    int fore_color_rgb = -1;
    int back_color_rgb = -1;
    feature_name_ = std::string("");
    GeoPoint2 start_point;
    GeoPoint2 end_point;
    std::unique_ptr<atakmap::util::AttributeSet> attributes(new (std::nothrow) atakmap::util::AttributeSet());
    if (attributes.get() == nullptr) return TE_OutOfMemory;

    do {
        code = cursor_->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = cursor_->getLong(&item_num, COLUMN_DRW_NUM);
        TE_CHECKBREAK_CODE(code);
        if (item_num != item_num_) break;

        int data_type;
        cursor_->getInt(&data_type, COLUMN_DRW_TYPE);
        TE_CHECKBREAK_CODE(code);
        const auto fv_data_type = static_cast<DataType>(data_type);
        switch (fv_data_type) {
            default:
                parseGeneral(fv_data_type, attributes);
                break;
            case DataType::Color: {
                parseColor(&fore_color_rgb, &back_color_rgb);
            } break;
            case DataType::MoveTo: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    start_point.latitude = lat;
                    start_point.longitude = lng;
                }
            } break;
            case DataType::LineTo: {
                double lat, lng;
                if (parseLatitudeLongitudeFromString(&lat, &lng) == TE_Ok) {
                    end_point.latitude = lat;
                    end_point.longitude = lng;
                }
            } break;
        }

    } while (code == TE_Ok && item_num_ == item_num);

    GeoPoint2 ll(start_point);
    if (TE_ISNAN(ll.altitude)) ll.altitude = 0;
    ll.altitude += 100;
    const double angle = GeoPoint2_bearing(start_point, end_point, false);
    const double range = GeoPoint2_distance(start_point, end_point, false);
    const double width = range * width_ratio;

    Projection2Ptr ecef(nullptr, nullptr);
    TAKErr code2 = ProjectionFactory3_create(ecef, 4978);
    TE_CHECKRETURN_CODE(code2);

    atakmap::feature::Geometry *geometry = nullptr;
    std::vector<GeoPoint2> geo_points;
    {
        Point2<double> pos_pt, tgt_pt, up_pt;
        ecef->forward(&pos_pt, start_point);
        ecef->forward(&tgt_pt, end_point);
        ecef->forward(&up_pt, ll);
        Vector4<double> pos(pos_pt.x, pos_pt.y, pos_pt.z);
        Vector4<double> tgt(tgt_pt.x, tgt_pt.y, tgt_pt.z);
        Vector4<double> dir(0, 0, 0);
        tgt.subtract(&pos, &dir);
        const double len = dir.length();
        const double head_length = len * 0.25;  // width / SIN35;
        const double shaft_length = len - head_length;
        dir.multiply((1 / len), &dir);
        Vector4<double> north(0, 0, 1);
        Vector4<double> east(0, 0, 0);
        north.cross(&pos, &east);
        Vector4<double> up(up_pt.x, up_pt.y, up_pt.z);
        up.subtract(&pos, &up);
        east.normalize(&east);
        up.normalize(&up);
        up.cross(&east, &north);
        Vector4<double> perp(0, 0, 0);
        up.cross(&dir, &perp);

        GeoPoint2 geo_scratch;
        Vector4<double> scratch(perp);
        Vector4<double> scratch2(0, 0, 0);

#define ADD_AXIS_POINT(dir_multiplier, perp_multiplier)                           \
    scratch = Vector4<double>(dir);                                               \
    scratch.multiply(dir_multiplier, &scratch);                                   \
    scratch2 = Vector4<double>(perp);                                             \
    scratch2.multiply(perp_multiplier, &scratch2);                                \
    scratch.add(&pos, &scratch);                                                  \
    scratch.add(&scratch2, &scratch);                                             \
    ecef->inverse(&geo_scratch, Point2<double>(scratch.x, scratch.y, scratch.z)); \
    geo_points.push_back(geo_scratch);

#define ADD_AXIS_POINT2(vector, multiplier)                                       \
    scratch = Vector4<double>(vector);                                            \
    scratch.multiply(multiplier, &scratch);                                       \
    scratch.add(&pos, &scratch);                                                  \
    ecef->inverse(&geo_scratch, Point2<double>(scratch.x, scratch.y, scratch.z)); \
    geo_points.push_back(geo_scratch);

        if (cross) {
            auto *multi_geometry = new atakmap::feature::GeometryCollection(atakmap::feature::Geometry::Dimension::_2D);

            // Left side, pos->tgt
            ADD_AXIS_POINT2(perp, width);
            ADD_AXIS_POINT((shaft_length * 0.25), (width * 0.7));
            ADD_AXIS_POINT((shaft_length * 0.5), (width * 0.5));
            ADD_AXIS_POINT2(dir, (shaft_length * 0.75));

            // Right side, tgt->pos
            ADD_AXIS_POINT((shaft_length * 0.5), (width * -0.5));
            ADD_AXIS_POINT((shaft_length * 0.25), (width * -0.7))
            ADD_AXIS_POINT2(perp, (-width));

            GeometryPtr line_geometry(createGeometry(geo_points, true), atakmap::feature::destructGeometry);
            multi_geometry->add(line_geometry.get());

            // Head
            geo_points.clear();

            ADD_AXIS_POINT2(dir, (shaft_length * 0.75));
            ADD_AXIS_POINT((shaft_length), (-width * 0.5));
            ADD_AXIS_POINT((shaft_length), (-width));
            ADD_AXIS_POINT2(dir, (len));
            ADD_AXIS_POINT((shaft_length), (width));
            ADD_AXIS_POINT((shaft_length), (width * 0.5));
            ADD_AXIS_POINT2(dir, (shaft_length * 0.75));

            line_geometry.reset(createGeometry(geo_points, true));
            multi_geometry->add(line_geometry.get());
            geometry = multi_geometry;
        } else {
            // Left side, pos->tgt
            ADD_AXIS_POINT2(perp, (width));
            ADD_AXIS_POINT((shaft_length * 0.25), (width * 0.7));
            ADD_AXIS_POINT((shaft_length * 0.5), (width * 0.5));

            // Head
            ADD_AXIS_POINT((shaft_length), (width * 0.5));
            ADD_AXIS_POINT((shaft_length), (width));
            ADD_AXIS_POINT2(dir, (len));
            ADD_AXIS_POINT((shaft_length), (-width));
            ADD_AXIS_POINT((shaft_length), (width * -0.5));

            // Right side, tgt->pos
            ADD_AXIS_POINT((shaft_length * 0.5), (width * -0.5));
            ADD_AXIS_POINT((shaft_length * 0.25), (width * -0.7));
            ADD_AXIS_POINT2(perp, (-width));

            geometry = createGeometry(geo_points, true);
        }

#undef ADD_AXIS_POINT
#undef ADD_AXIS_POINT2
    }
    atakmap::feature::Style *style = createStyle(LineStyle::Solid, fill_style, fore_color_rgb, back_color_rgb, stroke_width, true);

    std::unique_ptr<FalconViewDrwFileFeatureDefinition, void (*)(const FalconViewDrwFileFeatureDefinition *)> result(
        new FalconViewDrwFileFeatureDefinition(feature_name_.c_str(),
                                               GeometryPtr(geometry, Memory_deleter_const<atakmap::feature::Geometry>),
                                               StylePtr(style, Memory_deleter_const<atakmap::feature::Style>), std::move(attributes)),
        Memory_deleter_const<FalconViewDrwFileFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

void FalconViewDrwFileContent::parseGeneral(const DataType &data_type, std::unique_ptr<atakmap::util::AttributeSet> &attributes) NOTHROWS {
    switch (data_type) {
        case DataType::Label: {
            std::string data = getData();
            feature_name_ = data;
        } break;
        case DataType::Tooltip: {
            std::string data = getData();
            if (attributes->containsAttribute("tooltip")) {
                const char *existing = attributes->getString("tooltip");
                std::string combined(existing);
                combined += data;
                attributes->setString("tooltip", combined.c_str());
            } else {
                attributes->setString("tooltip", data.c_str());
            }
        } break;
        default:
            break;
    }
}

void FalconViewDrwFileContent::parseColor(int *fore_color_rgb, int *back_color_rgb) NOTHROWS {
    std::string data = getData();
    if (data.length() < 6) return;
    int fore_color_value = atoi(data.substr(0, 3).c_str());
    if (getRgbColorFromColorData(fore_color_rgb, fore_color_value) != TE_Ok) *fore_color_rgb = 0xFF000000;
    int back_color_value = atoi(data.substr(3, 3).c_str());
    if (getRgbColorFromColorData(back_color_rgb, back_color_value) != TE_Ok) *back_color_rgb = 0xFFFFFFFF;
}

TAKErr FalconViewDrwFileContent::parseLatitudeLongitudeFromString(double *latitude, double *longitude) NOTHROWS {
    std::string data = getData();
    std::string lower_data = atakmap::util::toLowerCase(data);
    auto idxe = lower_data.find('e');
    if (idxe != std::string::npos) lower_data = lower_data.replace(idxe, 1, " ");
    auto idxw = lower_data.find('w');
    if (idxw != std::string::npos) lower_data = lower_data.replace(idxw, 1, " ");
    auto idxn = lower_data.find('n');
    if (idxn != std::string::npos) lower_data = lower_data.replace(idxn, 1, " ");
    auto idxs = lower_data.find('s');
    if (idxs != std::string::npos) lower_data = lower_data.replace(idxs, 1, " ");

    auto string_tokens = atakmap::util::splitString(lower_data, " ", true);

    if (string_tokens.size() < 2) return TE_InvalidArg;

    double lat = atof(string_tokens[0].c_str());
    double lng = atof(string_tokens[1].c_str());

    if (idxe != std::string::npos) {
        if (idxn != std::string::npos && idxn > idxe) {
            double temp = lat;
            lat = lng;
            lng = temp;
        }
        if (idxs != std::string::npos && idxs > idxe) {
            double temp = lat;
            lat = lng;
            lng = temp;
        }
        lng = std::abs(lng);
    }
    if (idxw != std::string::npos) {
        if (idxn != std::string::npos && idxn > idxw) {
            double temp = lat;
            lat = lng;
            lng = temp;
        }
        if (idxs != std::string::npos && idxs > idxw) {
            double temp = lat;
            lat = lng;
            lng = temp;
        }
        lng = -std::abs(lng);
    }
    if (idxn != std::string::npos) lat = std::abs(lat);
    if (idxs != std::string::npos) lat = -std::abs(lat);
    *latitude = lat;
    *longitude = lng;

    return TE_Ok;
}

TAKErr FalconViewDrwFileContent::getRgbColorFromColorData(int *rgb, int color) NOTHROWS {
    switch (color) {
        case 0:  // Black
            *rgb = 0xFF000000;
            return TE_Ok;
        case 1:  // Maroon
            *rgb = 0xFF800000;
            return TE_Ok;
        case 2:  // Green
            *rgb = 0xFF008000;
            return TE_Ok;
        case 3:  // Olive
            *rgb = 0xFF808000;
            return TE_Ok;
        case 4:  // Navy
            *rgb = 0xFF000080;
            return TE_Ok;
        case 5:  // Purple
            *rgb = 0xFF800080;
            return TE_Ok;
        case 6:  // Teal
            *rgb = 0xFF008080;
            return TE_Ok;
        case 7:  // Silver
            *rgb = 0xFFC0C0C0;
            return TE_Ok;
        case 8:  // Light Green
            *rgb = 0xFFC0DCC0;
            return TE_Ok;
        case 9:  // Light Blue
            *rgb = 0xFFA6CAF0;
            return TE_Ok;
        case 246:  // Light Yellow
            *rgb = 0xFFFFFBF0;
            return TE_Ok;
        case 247:  // Light Gray
            *rgb = 0xFFA0A0A4;
            return TE_Ok;
        case 248:  // Gray
            *rgb = 0xFF808080;
            return TE_Ok;
        case 249:  // Red
            *rgb = 0xFFFF0000;
            return TE_Ok;
        case 250:  // Green
            *rgb = 0xFF00FF00;
            return TE_Ok;
        case 251:  // Yellow
            *rgb = 0xFFFFFF00;
            return TE_Ok;
        case 252:  // Blue
            *rgb = 0xFF0000FF;
            return TE_Ok;
        case 253:  // Magenta
            *rgb = 0xFFFF00FF;
            return TE_Ok;
        case 254:  // Cyan
            *rgb = 0xFF00FFFF;
            return TE_Ok;
        case 255:  // White
            *rgb = 0xFFFFFFFF;
            return TE_Ok;
        default:
            return TE_InvalidArg;
    }
}

atakmap::feature::LineString FalconViewDrwFileContent::createLineString(const std::vector<GeoPoint2> &geo_points) {
    atakmap::feature::LineString line_string(atakmap::feature::Geometry::Dimension::_2D);
    for (auto geo_point : geo_points) {
        line_string.addPoint(geo_point.longitude, geo_point.latitude);
    }
    return line_string;
}

atakmap::feature::Geometry *FalconViewDrwFileContent::createGeometry(const std::vector<GeoPoint2> &geo_points, bool closed) {
    atakmap::feature::Geometry *geometry;
    const atakmap::feature::LineString line_string = createLineString(geo_points);
    if (closed) {
        auto *polygon = new atakmap::feature::Polygon(atakmap::feature::Geometry::Dimension::_2D);
        polygon->addRing(line_string);
        geometry = polygon;
    } else {
        geometry = new atakmap::feature::LineString(line_string);
    }
    return geometry;
}

atakmap::feature::Style *FalconViewDrwFileContent::createStyle(LineStyle line_style, FillStyle fill_style, int stroke_rgb, int fill_rgb,
                                                               float stroke_width, bool closed, bool include_label) {
    atakmap::feature::Style *style;
    if (closed && fill_style != FillStyle::None) {
        std::vector<atakmap::feature::Style *> styles;
        int fill_rgb_alpha(fill_rgb);
        switch (fill_style) {
            case FillStyle::HorizontalStripe:
            case FillStyle::VerticalStripe:
            case FillStyle::BackDiagonalStripe:
            case FillStyle::ForwardDiagonalStripe:
                fill_rgb_alpha = fill_rgb & 0x20FFFFFF;
                break;
            case FillStyle::CrossHatch:
                fill_rgb_alpha = fill_rgb & 0x3CFFFFFF;
                break;
            case FillStyle::DiagonalCrossHatch:
                fill_rgb_alpha = fill_rgb & 0x40FFFFFF;
                break;
            case FillStyle::Shaded:
                fill_rgb_alpha = fill_rgb & 0x80FFFFFF;
                break;
            case FillStyle::Solid:
            default:
                fill_rgb_alpha = fill_rgb;
                break;
        }
        styles.push_back(new atakmap::feature::BasicFillStyle(fill_rgb_alpha));
        styles.push_back(createStrokeStyle(line_style, stroke_rgb, stroke_width));
        if (include_label)
            styles.push_back(new atakmap::feature::LabelPointStyle(feature_name_.c_str(), 0xFFFFFFFF, 0x00000000,
                                                                   atakmap::feature::LabelPointStyle::ScrollMode::DEFAULT));
        style = new atakmap::feature::CompositeStyle(styles);
    } else {
        style = createStrokeStyle(line_style, stroke_rgb, stroke_width);
    }
    return style;
}

atakmap::feature::Style *FalconViewDrwFileContent::createStrokeStyle(LineStyle line_style, int stroke_rgb, float stroke_width) {
    atakmap::feature::Style *style;
    switch (line_style) {
        case LineStyle::Dot:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x0FFF, stroke_rgb, stroke_width);
            break;
        case LineStyle::Dash:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x0F0F, stroke_rgb, stroke_width);
            break;
        case LineStyle::DashDot2:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x2727, stroke_rgb, stroke_width);
            break;
        case LineStyle::Dash2:
        case LineStyle::Flot3Start:
        case LineStyle::Flot3End:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x7777, stroke_rgb, stroke_width);
            break;
        case LineStyle::LongDash:
        case LineStyle::Feba2End:
        case LineStyle::Feba2Start:
        case LineStyle::Flot2End:
        case LineStyle::Flot2Start:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x3F3F, stroke_rgb, stroke_width);
            break;
        case LineStyle::Diamond:
            style = new atakmap::feature::PatternStrokeStyle(1, 0x5555, stroke_rgb, stroke_width);
            break;
        default:
            style = new atakmap::feature::BasicStrokeStyle(stroke_rgb, stroke_width);
            break;
    }
    return style;
}

std::string FalconViewDrwFileContent::getData() NOTHROWS {
    const char *data_string;
    TAKErr code = cursor_->getString(&data_string, COLUMN_DRW_DATA);
    if (code == TE_Ok) {
        std::string data(data_string);
        return data;
    }

    return std::string("");
}

TAKErr FalconViewDrwFileContent::moveToNextFeatureSet() NOTHROWS {
    // RWI - Only 1 Feature Set
    if (feature_set_count_ == 0) {
        feature_set_count_++;
        return TE_Ok;
    }
    return TE_Done;
}

TAKErr FalconViewDrwFileContent::get(FeatureDefinition2 **feature) const NOTHROWS {
    *feature = feature_definition_.get();
    return TE_Ok;
}
TAKErr FalconViewDrwFileContent::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS {
    name = feature_set_name_.c_str();
    return TE_Ok;
}
TAKErr FalconViewDrwFileContent::getFeatureSetVisible(bool *visible) const NOTHROWS {
    *visible = true;
    return TE_Ok;
}
TAKErr FalconViewDrwFileContent::getMinResolution(double *value) const NOTHROWS {
    *value = 156543.034;
    return TE_Ok;
}
TAKErr FalconViewDrwFileContent::getMaxResolution(double *value) const NOTHROWS {
    *value = MAX_RESOLUTION;
    return TE_Ok;
}
TAKErr FalconViewDrwFileContent::getVisible(bool *visible) const NOTHROWS {
    *visible = true;
    return TE_Ok;
}

FalconViewDrwFileFeatureDefinition::FalconViewDrwFileFeatureDefinition(const String &name, GeometryPtr geometry, StylePtr style,
                                                                       std::unique_ptr<atakmap::util::AttributeSet> attributes) NOTHROWS
    : name_(name),
      geometry_(std::move(geometry)),
      style_(std::move(style)),
      feature_(nullptr, nullptr),
      attributes_(std::move(attributes)) {}

TAKErr FalconViewDrwFileFeatureDefinition::getRawGeometry(RawData *value) NOTHROWS {
    value->object = geometry_.get();
    return TE_Ok;
}

FeatureDefinition2::GeometryEncoding FalconViewDrwFileFeatureDefinition::getGeomCoding() NOTHROWS { return GeomGeometry; }

AltitudeMode FalconViewDrwFileFeatureDefinition::getAltitudeMode() NOTHROWS { return AltitudeMode::TEAM_ClampToGround; }

double FalconViewDrwFileFeatureDefinition::getExtrude() NOTHROWS { return 0.0; }

TAKErr FalconViewDrwFileFeatureDefinition::getName(const char **value) NOTHROWS {
    *value = name_;
    return TE_Ok;
}

FeatureDefinition2::StyleEncoding FalconViewDrwFileFeatureDefinition::getStyleCoding() NOTHROWS { return StyleStyle; }

TAKErr FalconViewDrwFileFeatureDefinition::getRawStyle(RawData *value) NOTHROWS {
    value->object = style_.get();
    return TE_Ok;
}

TAKErr FalconViewDrwFileFeatureDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS {
    *value = attributes_.get();
    return TE_Ok;
}

TAKErr FalconViewDrwFileFeatureDefinition::get(const Feature2 **feature) NOTHROWS {
    feature_.reset();
    TAKErr code = Feature_create(feature_, *this);
    TE_CHECKRETURN_CODE(code);

    *feature = feature_.get();
    return TE_Ok;
}

FalconViewLptContent::FalconViewLptContent(DatabasePtr &&database, TAK::Engine::Port::String name) NOTHROWS
    : feature_set_count_(0),
      database_(std::move(database)),
      cursor_(nullptr, nullptr),
      feature_set_name_(name),
      feature_definition_(nullptr, nullptr) {
    database_->query(cursor_, "select * from Points");
}

FalconViewLptContent::~FalconViewLptContent() NOTHROWS {}

const char *FalconViewLptContent::getType() const NOTHROWS { return "lpt"; }
const char *FalconViewLptContent::getProvider() const NOTHROWS { return FALCONVIEW_PROVIDER; }

TAKErr FalconViewLptContent::moveToNextFeature() NOTHROWS {
    TAKErr code = cursor_->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const char *id;
    cursor_->getString(&id, COLUMN_LPT_ID);
    TE_CHECKRETURN_CODE(code);
    String string_id(id);

    String string_desc("");
    const char *desc;
    code = cursor_->getString(&desc, COLUMN_LPT_DESC);
    if (code == TE_Ok) {
        string_desc = String(desc);
    }

    double lat;
    code = cursor_->getDouble(&lat, COLUMN_LPT_LAT);
    TE_CHECKRETURN_CODE(code);

    double lng;
    code = cursor_->getDouble(&lng, COLUMN_LPT_LNG);
    TE_CHECKRETURN_CODE(code);

    int elev;
    code = cursor_->getInt(&elev, COLUMN_LPT_ELEV);
    TE_CHECKRETURN_CODE(code);

    const char *icon;
    code = cursor_->getString(&icon, COLUMN_LPT_ICON);
    TE_CHECKRETURN_CODE(code);
    String string_icon(icon);

    std::unique_ptr<FalconViewLptFeatureDefinition, void (*)(const FalconViewLptFeatureDefinition *)> result(
        new FalconViewLptFeatureDefinition(string_id, string_desc, lat, lng, elev, string_icon),
        Memory_deleter_const<FalconViewLptFeatureDefinition>);

    feature_definition_ = std::move(result);

    return code;
}

TAKErr FalconViewLptContent::moveToNextFeatureSet() NOTHROWS {
    // RWI - Only 1 Feature Set
    if (feature_set_count_ == 0) {
        feature_set_count_++;
        return TE_Ok;
    }
    return TE_Done;
}

TAKErr FalconViewLptContent::get(FeatureDefinition2 **feature) const NOTHROWS {
    *feature = feature_definition_.get();
    return TE_Ok;
}
TAKErr FalconViewLptContent::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS {
    name = feature_set_name_;
    return TE_Ok;
}
TAKErr FalconViewLptContent::getFeatureSetVisible(bool *visible) const NOTHROWS {
    *visible = true;
    return TE_Ok;
}
TAKErr FalconViewLptContent::getMinResolution(double *value) const NOTHROWS {
    *value = 156543.034;
    return TE_Ok;
}
TAKErr FalconViewLptContent::getMaxResolution(double *value) const NOTHROWS {
    *value = MAX_RESOLUTION;
    return TE_Ok;
}
TAKErr FalconViewLptContent::getVisible(bool *visible) const NOTHROWS {
    *visible = true;
    return TE_Ok;
}

FalconViewLptFeatureDefinition::FalconViewLptFeatureDefinition(const String &id, const String &desc, double lat, double lng, int elev,
                                                               const String &icon) NOTHROWS
    : name_(id),
      geometry_(new atakmap::feature::Point(lng, lat, elev), atakmap::feature::destructGeometry),
      style_(new atakmap::feature::IconPointStyle(-1, getIconUri(icon)), atakmap::feature::Style::destructStyle),
      attributes_(),
      feature_(nullptr, nullptr) {
    attributes_.setString("description", desc);
}

TAKErr FalconViewLptFeatureDefinition::getRawGeometry(RawData *value) NOTHROWS {
    value->object = geometry_.get();
    return TE_Ok;
}

FeatureDefinition2::GeometryEncoding FalconViewLptFeatureDefinition::getGeomCoding() NOTHROWS { return GeomGeometry; }

AltitudeMode FalconViewLptFeatureDefinition::getAltitudeMode() NOTHROWS { return AltitudeMode::TEAM_Absolute; }

double FalconViewLptFeatureDefinition::getExtrude() NOTHROWS { return -1; }

TAKErr FalconViewLptFeatureDefinition::getName(const char **value) NOTHROWS {
    *value = name_;
    return TE_Ok;
}

FeatureDefinition2::StyleEncoding FalconViewLptFeatureDefinition::getStyleCoding() NOTHROWS { return StyleEncoding::StyleStyle; }

TAKErr FalconViewLptFeatureDefinition::getRawStyle(RawData *value) NOTHROWS {
    value->object = style_.get();
    return TE_Ok;
}

TAKErr FalconViewLptFeatureDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS {
    *value = &attributes_;
    return TE_Ok;
}

TAKErr FalconViewLptFeatureDefinition::get(const Feature2 **feature) NOTHROWS {
    feature_.reset();
    TAKErr code = Feature_create(feature_, *this);
    TE_CHECKRETURN_CODE(code);

    *feature = feature_.get();

    return TE_Ok;
}

String FalconViewLptFeatureDefinition::getIconUri(const String &icon_name) NOTHROWS {
    std::string path(LPT_ICON_URI);
    path += "lpt_";
    if (strlen(icon_name) == 0)
        path += DEFAULT_LPT_ICON;
    else
        path += icon_name;
    std::transform(path.begin(), path.end(), path.begin(), ::tolower);
    std::replace(path.begin(), path.end(), ' ', '_');
    std::replace(path.begin(), path.end(), '-', '_');
    path += ".png";

    return String(path.c_str());
}

}  // namespace