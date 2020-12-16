
#include "feature/KMLFeatureDataSource2.h"
#include <deque>
#include <regex>
#include "feature/Geometry.h"
#include "feature/GeometryCollection.h"
#include "feature/KMLParser.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "feature/Style.h"
#include "port/STLVectorAdapter.h"
#include "port/String.h"
#include "util/IO2.h"
#include "util/Memory.h"

#include "ogr_core.h"

#define DefaultPointStyle                                    \
    "SYMBOL(id:http://maps.google.com/mapfiles/kml/pushpin/" \
    "ylw-pushpin.png,c:#FFFFFFFF)"
#define DefaultLineColor 0xFF000000
#define DefaultSymbolColor 0xFFFFFFFF
#define STYLE_MAP_PAIR_NORMAL_TYPE 0
#define STYLE_MAP_PAIR_HIGHLIGHT_TYPE 1
#define STYLE_HEADER_SYMBOL_POS 0
/*const char* STYLE_HEADER_SYMBOL = "#";

const char* URL = "http";
const char* URL_CAP = "HTTP";*/

const double MAX_RESOLUTION = 0.0;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

#define KML_FULL_3D 1

namespace {

struct Container;

atakmap::feature::Style *convertStyleSelector(
    const KMLStyleSelector &styleSelector, const std::string &file,
    const Container *container);
atakmap::feature::Style *convertStyleUrl(const char *styleUrl,
                                         const Container *container);
atakmap::feature::Style *convertStyle(const KMLStyle &style,
                                      const std::string &file);
atakmap::feature::Style *convertStyleMap(const KMLStyleMap &style,
                                         const std::string &file,
                                         const Container *container);
atakmap::feature::Style *convertIconStyle(const KMLIconStyle &kmlIconStyle,
                                          const std::string &file);
atakmap::feature::Style *convertLineStyle(const KMLLineStyle &kmlLineStyle);
atakmap::feature::Style *convertFillStyle(const KMLPolyStyle &kmlPolyStyle);
atakmap::feature::Geometry *convertGeometry(const KMLGeometry &kmlGeometry);
atakmap::feature::Point *convertPoint(const KMLPoint &kmlGeometry);
atakmap::feature::GeometryCollection *convertGxTrack(
    const KMLgxTrack &kmlGeometry);
atakmap::feature::GeometryCollection *convertMultiGeometry(
    const KMLMultiGeometry &kmlMultiGeom);
atakmap::feature::Polygon *convertPolygon(const KMLPolygon &kmlPolygon);
atakmap::feature::LineString *convertCoordinates(
    const KMLCoordinates &kmlCoordinates);
atakmap::feature::LineString *convertLinearRing(
    const KMLLinearRing &kmlLinearRing);
atakmap::feature::LineString *convertLineString(
    const KMLLineString &kmlLineString);
atakmap::feature::Point *convertModel(const KMLModel &kmlModel);
bool determineAltMode(const KMLGeometry &geom, KMLAltitudeMode &mode);
void determineExtrude(const KMLGeometry &geom, double &extrude);

void convertMultiGeometryAddToGeometryCollection(
    atakmap::feature::GeometryCollection &collection,
    const KMLMultiGeometry &kmlMultiGeom);

class KMLPlacemarkFeatureDef : public FeatureDefinition2 {
   public:
    KMLPlacemarkFeatureDef(KMLPtr<KMLPlacemark> &&pacemark,
                           const std::string &file);
    virtual ~KMLPlacemarkFeatureDef() NOTHROWS;
    TAKErr getRawGeometry(RawData *value) NOTHROWS override;
    GeometryEncoding getGeomCoding() NOTHROWS override;
    AltitudeMode getAltitudeMode() NOTHROWS override;
    double getExtrude() NOTHROWS override;
    TAKErr getName(const char **value) NOTHROWS override;
    StyleEncoding getStyleCoding() NOTHROWS override;
    TAKErr getRawStyle(RawData *value) NOTHROWS override;
    TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
    TAKErr get(const Feature2 **feature) NOTHROWS override;

    TAKErr applyStyle() NOTHROWS;
    TAKErr createAttrs() NOTHROWS;
    TAKErr applyGeom() NOTHROWS;

    std::string file;
    Container *container;
    KMLPtr<KMLPlacemark> placemark;
    std::unique_ptr<atakmap::feature::Style> style;
    StyleEncoding styleEncoding;
    std::unique_ptr<atakmap::feature::Geometry> geometry;
    AltitudeMode altitudeMode;
    double extrude;
    std::unique_ptr<atakmap::util::AttributeSet> attrs;
};

struct Container {
    Container(const char *defName, const char *id)
        : name(defName),
          id(id ? id : ""),
          parent(nullptr),
          hasPlacemarks(false),
          hasNestedPlacemarks(false),
          visible(true),
          finished(false) {}

    virtual ~Container();

    void getFullName(std::ostringstream &ss) const;

    std::string id;
    std::string name;
    std::vector<std::unique_ptr<atakmap::feature::Style>> styles;

    Container *parent;

    std::deque<std::unique_ptr<KMLPlacemarkFeatureDef>> featureDefs;

    virtual atakmap::feature::Style *createSharedStyle(const char *name_val) const;

    bool visible;
    bool hasPlacemarks;
    bool hasNestedPlacemarks;
    bool finished;
};

struct Folder : public Container {
    Folder() : Container("Folder", nullptr) {}

    Folder(const char *id) : Container("Folder", id) {}

    ~Folder() override;
};

struct Document : public Container {
    Document(const char *name, const char *id) : Container(name, id) {}

    Document(const char *id) : Container("Document", id) {}

    ~Document() override;

    atakmap::feature::Style *createSharedStyle(const char *name_val) const override;

    std::map<std::string, std::unique_ptr<atakmap::feature::Style>>
        sharedStyles;
    std::map<std::string, KMLPtr<KMLStyleMap>> unresolvedStyleMaps;
};

class KMLContent2 : public FeatureDataSource2::Content {
   public:
    explicit KMLContent2(const char *file);
    ~KMLContent2() NOTHROWS override;
    const char *getType() const NOTHROWS override;
    const char *getProvider() const NOTHROWS override;
    TAK::Engine::Util::TAKErr moveToNextFeature() NOTHROWS override;
    TAK::Engine::Util::TAKErr moveToNextFeatureSet() NOTHROWS override;
    TAK::Engine::Util::TAKErr get(
        FeatureDefinition2 **feature) const NOTHROWS override;
    TAK::Engine::Util::TAKErr getFeatureSetName(
        TAK::Engine::Port::String &name) const NOTHROWS override;
    TAK::Engine::Util::TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS override;
    TAK::Engine::Util::TAKErr getMinResolution(
        double *value) const NOTHROWS override;
    TAK::Engine::Util::TAKErr getMaxResolution(
        double *value) const NOTHROWS override;
    TAK::Engine::Util::TAKErr getVisible(bool *visible) const NOTHROWS override;

    inline bool hasFeatureSet() const {
        return this->containerQueue.size() > 0;
    }
    inline bool hasFeature() const {
        return hasFeatureSet() &&
               this->containerQueue.front()->featureDefs.size() > 0;
    }
    inline bool hasGeometry() const {
        if (!hasFeature()) return false;
        
        auto featureDef = containerQueue.front()->featureDefs.front().get();

        if (!featureDef->geometry) featureDef->applyGeom();

        return featureDef->geometry.get() != nullptr;
    }

    TAKErr open() NOTHROWS;
    TAKErr close() NOTHROWS;
    TAKErr performFirstPass() NOTHROWS;
    TAKErr stepSecondPass() NOTHROWS;

    TAKErr handleFirstPassStyleSelector(
        const KMLPtr<KMLStyleSelector> &styleSelector,
        Container *container) NOTHROWS;

    bool isKMZ() const { return file != kmlFile; }

   private:
    TAK::Engine::Util::TAKErr moveToNextFeatureInner() NOTHROWS;

    DataInput2Ptr inputPtr;
    std::string file;
    std::string kmlFile;
    KMLParser parser;

    std::vector<std::unique_ptr<Container>> flattenedContainers;

    size_t nextForwardContainerIndex;
    size_t forwardContainerIndex;

    OGREnvelope datasetMBR;

    Container *forwardContainer;
    std::deque<Container *> containerQueue;

    bool atFrontContainer;
    bool atFrontFeature;
};

}  // namespace

KMLFeatureDataSource2::KMLFeatureDataSource2() NOTHROWS {}

KMLFeatureDataSource2::~KMLFeatureDataSource2() NOTHROWS {}

TAKErr KMLFeatureDataSource2::parse(ContentPtr &content,
                                    const char *file) NOTHROWS {
    TAKErr code(TE_Ok);
    std::unique_ptr<KMLContent2> kmlContent;

    TE_BEGIN_TRAP() { kmlContent.reset(new KMLContent2(file)); }
    TE_END_TRAP(code);

    if (code != TE_Ok) goto legacyFallback;

    TE_CHECKRETURN_CODE(code);

    code = kmlContent->performFirstPass();

    if (code != TE_Ok) goto legacyFallback;

    TE_CHECKRETURN_CODE(code);

    code = kmlContent->open();

    if (code != TE_Ok) goto legacyFallback;

    TE_CHECKRETURN_CODE(code);

    content = ContentPtr(
        kmlContent.release(),
        Memory_deleter_const<KMLFeatureDataSource2::Content, KMLContent2>);
    return code;

legacyFallback:
    return legacyDataSource.parse(content, file);
}

const char *KMLFeatureDataSource2::getName() const NOTHROWS { return "KML"; }

int KMLFeatureDataSource2::parseVersion() const NOTHROWS { return 1; }

//
// KMLPlacemarkFeatureDef
//

KMLPlacemarkFeatureDef::KMLPlacemarkFeatureDef(KMLPtr<KMLPlacemark> &&placemark,
                                               const std::string &file)
    : placemark(std::move(placemark)),
      file(file),
      container(nullptr),
      altitudeMode(AltitudeMode::TEAM_ClampToGround),
      extrude(0.0),
      styleEncoding(StyleStyle) {}

KMLPlacemarkFeatureDef::~KMLPlacemarkFeatureDef() NOTHROWS {}

TAKErr KMLPlacemarkFeatureDef::getRawGeometry(RawData *value) NOTHROWS {
    TAKErr code(TE_Ok);

    if (!this->geometry) {
        code = this->applyGeom();
        TE_CHECKRETURN_CODE(code);
    }

    value->object = this->geometry.get();
    return code;
}

FeatureDefinition2::GeometryEncoding KMLPlacemarkFeatureDef::getGeomCoding()
    NOTHROWS {
    return GeomGeometry; }

AltitudeMode KMLPlacemarkFeatureDef::getAltitudeMode() NOTHROWS { 
    return this->altitudeMode;
}

double KMLPlacemarkFeatureDef::getExtrude() NOTHROWS { 
    return this->extrude;
}

TAKErr KMLPlacemarkFeatureDef::getName(const char **value) NOTHROWS {
    *value = this->placemark->name.value.c_str();
    return TE_Ok;
}

FeatureDefinition2::StyleEncoding KMLPlacemarkFeatureDef::getStyleCoding()
    NOTHROWS {
    if (!this->style && this->styleEncoding == StyleStyle) {
        applyStyle();
    }

    return this->styleEncoding;
}

TAKErr KMLPlacemarkFeatureDef::getRawStyle(RawData *value) NOTHROWS {
    TAKErr code(TE_Ok);

    if (!this->style && this->styleEncoding == StyleStyle) {
        code = applyStyle();
    }

    if (code != TE_Ok) return code;

    if (styleEncoding == StyleStyle)
        value->object = this->style.get();
    else if (styleEncoding == StyleOgr)
        value->text = DefaultPointStyle;
    else
        return TE_IllegalState;

    return code;
}

TAKErr KMLPlacemarkFeatureDef::getAttributes(
    const atakmap::util::AttributeSet **value) NOTHROWS {
    if (!this->attrs) {
        this->createAttrs();
    }

    *value = this->attrs.get();
    return TE_Ok;
}

TAKErr KMLPlacemarkFeatureDef::get(const Feature2 **feature) NOTHROWS {
    return TE_Unsupported;
}

TAKErr KMLPlacemarkFeatureDef::createAttrs() NOTHROWS {
    this->attrs.reset(new (std::nothrow) atakmap::util::AttributeSet());
    if (!this->attrs) return TE_OutOfMemory;

    if (this->placemark->description.is_specified()) {
        this->attrs->setString("description",
                               this->placemark->description.value.c_str());
    }
    if (this->placemark->visibility.is_specified()) {
        this->attrs->setInt("visibility", this->placemark->visibility ? 1 : 0);
    }
    if (this->placemark->ExtendedData.is_specified()) {
        for (size_t i = 0; i < this->placemark->ExtendedData.value.Data.size();
             ++i) {
            if (this->placemark->ExtendedData.value.Data[i]
                    .name.is_specified() &&
                this->placemark->ExtendedData.value.Data[i]
                    .value.is_specified()) {
                this->attrs->setString(
                    this->placemark->ExtendedData.value.Data[i]
                        .name.value.c_str(),
                    this->placemark->ExtendedData.value.Data[i]
                        .value.value.c_str());
            }
        }
    }
    return TE_Ok;
}

TAKErr KMLPlacemarkFeatureDef::applyStyle() NOTHROWS {
    TAKErr code(TE_Ok);

    TE_BEGIN_TRAP() {
        // gather up all styles
        std::vector<std::unique_ptr<atakmap::feature::Style>> gatheredStyles;

        for (size_t i = 0; i < placemark->StyleSelector.num_items(); ++i) {
            std::unique_ptr<atakmap::feature::Style> style_ptr(convertStyleSelector(
                placemark->StyleSelector[i], this->file, container));
            if (style_ptr) {
                gatheredStyles.push_back(std::move(style_ptr));
            }
        }
        if (placemark->styleUrl.is_specified()) {
            std::unique_ptr<atakmap::feature::Style> style_ptr(
                convertStyleUrl(placemark->styleUrl.value.c_str(), container));
            if (style_ptr) {
                gatheredStyles.push_back(std::move(style_ptr));
            }
        }

        Container *node = this->container;
        while (node) {
            for (auto &node_style : node->styles) {
                gatheredStyles.push_back(
                    std::unique_ptr<atakmap::feature::Style>(node_style->clone()));
            }
            node = node->parent;
        }

        if (gatheredStyles.size() == 1) {
            this->style = std::move(gatheredStyles[0]);
            this->styleEncoding = StyleStyle;
        } else if (gatheredStyles.size() > 1) {
            std::vector<atakmap::feature::Style *> transferStyles;
            transferStyles.reserve(gatheredStyles.size());
            for (size_t i = 0; i < gatheredStyles.size(); ++i) {
                transferStyles.push_back(gatheredStyles[i].get());
                gatheredStyles[i].release();
            }

            this->style.reset(
                new atakmap::feature::CompositeStyle(transferStyles));
            this->styleEncoding = StyleStyle;
        } else {
            this->styleEncoding = StyleOgr;
        }
    }
    TE_END_TRAP(code);

    return code;
}

TAKErr KMLPlacemarkFeatureDef::applyGeom() NOTHROWS {
    TAKErr code(TE_Ok);

    TE_BEGIN_TRAP() {
        if (this->placemark->Geometry) {
            this->geometry.reset(convertGeometry(*this->placemark->Geometry));

            if (this->geometry.get() != nullptr) {
                KMLAltitudeMode altMode;
                if (determineAltMode(*this->placemark->Geometry, altMode)) {
                    switch (altMode) {
                        case KMLAltitudeMode::KMLAltitudeMode_clampToGround:
                            this->altitudeMode = AltitudeMode::TEAM_ClampToGround;
                            break;
                        case KMLAltitudeMode::KMLAltitudeMode_absolute:
                            this->altitudeMode = AltitudeMode::TEAM_Absolute;
                            break;
                        case KMLAltitudeMode::KMLAltitudeMode_relativeToGround:
                            this->altitudeMode = AltitudeMode::TEAM_Relative;
                            break;
                    }
                }

                determineExtrude(*this->placemark->Geometry, this->extrude);
            }
        }
    }
    TE_END_TRAP(code);

    return code;
}

//
// KMLContent2
//

KMLContent2::KMLContent2(const char *file)
    : file(file),
      inputPtr(nullptr, nullptr),
      forwardContainerIndex(0),
      nextForwardContainerIndex(0),
      forwardContainer(nullptr),
      atFrontContainer(false),
      atFrontFeature(false) {}

KMLContent2::~KMLContent2() {}

bool hasExt(const char *file, const char *matchExt) {
    const char *ext = strrchr(file, '.');
    if (!ext) return false;

    int cmp = -1;
    String_compareIgnoreCase(&cmp, ext, matchExt);
    return cmp == 0;
}

bool isKML(const char *file) { return hasExt(file, ".kml"); }

TAKErr KMLContent2::open() NOTHROWS {
    String workingStr;
    TAKErr code = IO_getName(workingStr, file.c_str());
    TE_CHECKRETURN_CODE(code);

    code = IO_getExt(workingStr, nullptr, workingStr.get());
    TE_CHECKRETURN_CODE(code);

    int kmz = -1;
    int kml = -1;
    String_compareIgnoreCase(&kmz, workingStr, ".kmz");
    String_compareIgnoreCase(&kml, workingStr, ".kml");

    if (kmz == 0) {
        std::vector<TAK::Engine::Port::String> kmlFiles;
        TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> adapter(
            kmlFiles);
        code = IO_listFilesV(adapter, file.c_str(),
                             TAK::Engine::Util::TELFM_ImmediateFiles, isKML);
        TE_CHECKRETURN_CODE(code);

        if (kmlFiles.size() == 0) return TE_Unsupported;

        kmlFile = kmlFiles.front();

    } else if (kml == 0) {
        kmlFile = file;
    }

    if (kmlFile == "") return TE_Unsupported;

    code = IO_openFileV(inputPtr, kmlFile.c_str());
    TE_CHECKRETURN_CODE(code);

    return parser.open(*inputPtr, kmlFile.c_str());
}

TAKErr KMLContent2::close() NOTHROWS {
    parser.close();
    inputPtr->close();
    inputPtr.reset();
    return TE_Ok;
}

TAKErr KMLContent2::performFirstPass() NOTHROWS {
    TAKErr code = this->open();
    TE_CHECKRETURN_CODE(code);

    // Do not store parsed object in parent objects (i.e. pure streaming mode)
    parser.enableStore(false);
    std::vector<Container *> stack;

    TAK::Engine::Port::String fileName;
    code = IO_getName(fileName, file.c_str());
    TE_CHECKRETURN_CODE(code);

    // skip to document
    for (;;) {
        code = parser.step();

        if (code != TE_Ok) break;

        switch (parser.position()) {
            case KMLParser::Object_begin:

                switch (parser.object()->get_entity()) {
                    case KMLEntity_Document:
                        flattenedContainers.push_back(
                            std::unique_ptr<Container>(new Document(
                                fileName.get(), parser.object()->get_id())));
                        if (stack.size())
                            flattenedContainers.back()->parent = stack.back();
                        stack.push_back(flattenedContainers.back().get());
                        break;

                    case KMLEntity_Folder:
                        flattenedContainers.push_back(
                            std::unique_ptr<Container>(
                                new Folder(parser.object()->get_id())));
                        if (stack.size())
                            flattenedContainers.back()->parent = stack.back();
                        stack.push_back(flattenedContainers.back().get());
                        break;

                    case KMLEntity_Placemark:
                        if (stack.size()) stack.back()->hasPlacemarks = true;
                        code = parser.skip();
                        TE_CHECKRETURN_CODE(code);
                        break;

                        // Don't skip these, but don't do anything either
                    case KMLEntity_DOM:
                        break;

                    default:
                        code = parser.skip();
                        TE_CHECKRETURN_CODE(code);
                        break;
                }

                break;

            case KMLParser::Object_end:
                if (parser.object()->is_container()) {
                    Container *node = stack.back();
                    if (node->parent)
                        node->parent->hasNestedPlacemarks =
                            (node->hasPlacemarks || node->hasNestedPlacemarks);
                    stack.pop_back();
                }
                break;

            case KMLParser::Field_name:
                if (parser.object()->is_container()) {
                    const auto *feature =
                        static_cast<const KMLFeature *>(parser.object());
                    if (feature->name.value != "") {
                        stack.back()->name = feature->name.value;
                    }
                }
                break;

            case KMLParser::Field_visibility: {
                const auto *feature =
                    static_cast<const KMLFeature *>(parser.object());
                stack.back()->visible = !feature->visibility.is_specified() ||
                    feature->visibility;
            } break;

            case KMLParser::Field_StyleSelector:
                code = handleFirstPassStyleSelector(
                    parser.fieldObjectPtr().as<KMLStyleSelector>(),
                    stack.size() ? stack.back() : nullptr);
                TE_CHECKRETURN_CODE(code);
                break;

            case KMLParser::Unrecognized:
                code = parser.skip();
                TE_CHECKRETURN_CODE(code);
                break;
        }
    }

    this->close();

    return code == TE_Done ? TE_Ok : TE_Err;
}

TAKErr KMLContent2::stepSecondPass() NOTHROWS {
    TAKErr code(TE_Ok);

    bool seeking = true;
    do {
        code = parser.step();

        if (code != TE_Ok) break;

        switch (parser.position()) {
            case KMLParser::Object_begin:
                if (parser.object()->is_container()) {
                    forwardContainerIndex = nextForwardContainerIndex++;
                    Container *container =
                        flattenedContainers[forwardContainerIndex].get();
                    this->forwardContainer = container;
                } else if (parser.object()->get_entity() ==
                           KMLEntity_Placemark) {
                    parser.enableStore(true);
                }
                break;  // case KMLParser::Object_begin

            case KMLParser::Object_end:
                if (this->flattenedContainers.empty()) {
                    TAK::Engine::Port::String fileName;
                    code = IO_getName(fileName, file.c_str());
                    flattenedContainers.push_back(std::unique_ptr<Container>(new Document(fileName.get(), "")));
                    forwardContainerIndex = nextForwardContainerIndex++;
                    Container *container = flattenedContainers[forwardContainerIndex].get();
                    this->forwardContainer = container;
                }
                if (parser.object()->is_container()) {
                    Container *container = this->forwardContainer;
                    container->finished = true;
                    this->forwardContainer = container->parent;

                    // certainly no more Placemarks and ready for processing
                    if (std::find(containerQueue.begin(), containerQueue.end(),
                                  container) == containerQueue.end()) {
                        containerQueue.push_back(container);
                    }
                } else if (parser.object()->get_entity() ==
                           KMLEntity_Placemark) {
                    parser.enableStore(false);
                    TE_BEGIN_TRAP() {
                        Container *container = this->forwardContainer;
                        std::unique_ptr<KMLPlacemarkFeatureDef> featureDef(
                            new KMLPlacemarkFeatureDef(
                                parser.objectPtr().as<KMLPlacemark>(),
                                this->file));
                        featureDef->container = container;
                        container->featureDefs.push_back(std::move(featureDef));

                        // certainly has Placemark and ready for processing
                        if (std::find(containerQueue.begin(),
                                      containerQueue.end(),
                                      container) == containerQueue.end()) {
                            containerQueue.push_back(container);
                        }

                        seeking = false;
                    }
                    TE_END_TRAP(code);
                    TE_CHECKRETURN_CODE(code);
                }

                break;  // case KMLParser::Object_end

            case KMLParser::Unrecognized:
                code = parser.skip();
                TE_CHECKRETURN_CODE(code);
                break;
        }

    } while (seeking);

    return code;
}

TAKErr KMLContent2::handleFirstPassStyleSelector(
    const KMLPtr<KMLStyleSelector> &styleSelector,
    Container *container) NOTHROWS {
    TAKErr code(TE_Ok);

    // Only care about Document level styles with ids (i.e. kml "shared styles")
    if (parser.object()->get_entity() == KMLEntity_Document &&
        styleSelector->has_id()) {
        TE_BEGIN_TRAP() {
            std::unique_ptr<atakmap::feature::Style> style(
                convertStyleSelector(*styleSelector, this->file, container));
            auto *doc = static_cast<Document *>(container);
            if (style) {
                doc->sharedStyles.insert(
                    std::make_pair(styleSelector->get_id(), std::move(style)));
            } else if (styleSelector->get_entity() == KMLEntity_StyleMap) {
                doc->unresolvedStyleMaps.insert(std::make_pair(
                    styleSelector->get_id(), styleSelector.as<KMLStyleMap>()));
            }
            // else-- will get picked up when default style is applied
        }
        TE_END_TRAP(code);
    } else if (parser.object()->get_entity() == KMLEntity_Folder) {
        TE_BEGIN_TRAP() {
            std::unique_ptr<atakmap::feature::Style> style(
                convertStyleSelector(*styleSelector, this->file, container));
            if (style) {
                container->styles.push_back(std::move(style));
            }
        }
        TE_END_TRAP(code);
    }

    return code;
}

const char *KMLContent2::getType() const NOTHROWS { return "kml"; }

const char *KMLContent2::getProvider() const NOTHROWS { return "KML"; }

TAKErr KMLContent2::moveToNextFeature() NOTHROWS {
    TAKErr code;

    do {
        code = this->moveToNextFeatureInner();
    } while (code == TE_Ok && !this->hasGeometry());

    return code;
}

TAKErr KMLContent2::moveToNextFeatureInner() NOTHROWS {
    TAKErr code(TE_Ok);

    if (!atFrontContainer) return TE_IllegalState;

    if (atFrontFeature && this->containerQueue.front()->featureDefs.size()) {
        this->containerQueue.front()->featureDefs.pop_front();
    }
    atFrontFeature = false;

    while (this->containerQueue.front()->featureDefs.size() == 0 &&
           !this->containerQueue.front()->finished) {
        code = this->stepSecondPass();
        if (code == TE_Done) break;
        TE_CHECKRETURN_CODE(code);
    }

    if (this->containerQueue.front()->featureDefs.size() > 0) {
        atFrontFeature = true;
    } else {
        code = TE_Done;
    }

    return code;
}

TAKErr KMLContent2::moveToNextFeatureSet() NOTHROWS {
    TAKErr code(TE_Ok);

    parser.enableStore(false);

    if (atFrontContainer && this->containerQueue.size()) {
        this->containerQueue.pop_front();
    }
    atFrontContainer = false;

    while (this->containerQueue.size() == 0) {
        code = this->stepSecondPass();
        if (code == TE_Done || code == TE_EOF) break;
        TE_CHECKRETURN_CODE(code);
    }

    if (this->containerQueue.size() > 0) {
        atFrontContainer = true;
    } else {
        code = TE_Done;
    }

    return code;
}

TAKErr KMLContent2::get(FeatureDefinition2 **feature) const NOTHROWS {
    if (!hasFeature()) return TE_IllegalState;

    *feature = const_cast<KMLPlacemarkFeatureDef *>(
        containerQueue.front()->featureDefs.front().get());
    return TE_Ok;
}

TAKErr KMLContent2::getFeatureSetName(
    TAK::Engine::Port::String &name) const NOTHROWS {
    if (!hasFeatureSet()) return TE_IllegalState;

    std::ostringstream ss;
    containerQueue.front()->getFullName(ss);
    name = ss.str().c_str();
    return TE_Ok;
}

TAKErr KMLContent2::getMinResolution(double *value) const NOTHROWS {
    *value = 156543.034;
    return TE_Ok;
}

TAKErr KMLContent2::getMaxResolution(double *value) const NOTHROWS {
    *value = MAX_RESOLUTION;
    return TE_Ok;
}

TAKErr KMLContent2::getFeatureSetVisible(bool *visible) const NOTHROWS {
    if (!hasFeatureSet()) return TE_IllegalState;

    *visible = containerQueue.front()->visible;
    return TE_Ok;
}

TAKErr KMLContent2::getVisible(bool *visible) const NOTHROWS {
    if (!hasFeatureSet()) return TE_IllegalState;

    const KMLPlacemark &pm = *containerQueue.front()->featureDefs.front().get()->placemark.get();
    *visible = (!pm.visibility.is_specified() || pm.visibility.value);
    return TE_Ok;
}

namespace {
Container::~Container() {}

void Container::getFullName(std::ostringstream &ss) const {
    if (this->parent) {
        this->parent->getFullName(ss);
        ss << "/";
    }
    ss << this->name;
}

Folder::~Folder() {}

Document::~Document() {}

atakmap::feature::Style *Container::createSharedStyle(const char *name_val) const {
    return parent ? parent->createSharedStyle(name_val) : nullptr;
}

atakmap::feature::Style *Document::createSharedStyle(const char *name_val) const {
    auto it = this->sharedStyles.find(name_val);
    if (it != this->sharedStyles.end()) {
        return it->second->clone();
    }
    auto unIt = this->unresolvedStyleMaps.find(name_val);
    if (unIt != this->unresolvedStyleMaps.end()) {
        return convertStyleMap(*unIt->second, "", this);
    }
    return parent ? parent->createSharedStyle(name_val) : nullptr;
}

atakmap::feature::Style *convertStyleSelector(
    const KMLStyleSelector &kmlStyleSelector, const std::string &file,
    const Container *container) {
    switch (kmlStyleSelector.get_entity()) {
        case KMLEntity_Style:
            return convertStyle(static_cast<const KMLStyle &>(kmlStyleSelector),
                                file);
        case KMLEntity_StyleMap:
            return convertStyleMap(
                static_cast<const KMLStyleMap &>(kmlStyleSelector), file,
                container);
    }

    return nullptr;
}

atakmap::feature::Style *convertStyleUrl(const char *styleUrl,
                                         const Container *container) {
    if (*styleUrl == '#') {
        return container->createSharedStyle(styleUrl + 1);
    } else {
        // TODO-- external
    }

    return nullptr;
}

atakmap::feature::Style *convertStyle(const KMLStyle &kmlStyle,
                                      const std::string &file) {
    atakmap::feature::CompositeStyle *compositeStyle = nullptr;
    std::vector<atakmap::feature::Style *> styleVector;

    // create the icon style
    if (kmlStyle.IconStyle) {
        atakmap::feature::Style *style =
            convertIconStyle(*kmlStyle.IconStyle, file);
        if (style != nullptr) styleVector.push_back(style);
    }

    // NOTE: for the outline/fill properties:
    // if 'has_xxx' is 'true', the corresponding field has been explicitly
    // set -- evaluate 'get_xxx' to see if enabled or not.  if 'has_xxx'
    // is 'false', the field is not explicitly set and is interpreted as
    // 'true' by default.

    // create the line style. if there's a polystyle and outline is
    // disabled, don't include the line style.
    if (kmlStyle.LineStyle &&
        (!kmlStyle.PolyStyle || !kmlStyle.PolyStyle->outline.is_specified() ||
         kmlStyle.PolyStyle->outline)) {
        atakmap::feature::Style *style = convertLineStyle(*kmlStyle.LineStyle);
        if (style != nullptr) styleVector.push_back(style);
    }

    // create the fill style
    if (kmlStyle.PolyStyle && kmlStyle.PolyStyle->color &&
        (!kmlStyle.PolyStyle->fill.is_specified() ||
         kmlStyle.PolyStyle->fill)) {
        atakmap::feature::Style *style = convertFillStyle(*kmlStyle.PolyStyle);
        if (style != nullptr) styleVector.push_back(style);
    }

    // TODO: support other kml styles
    /*if (kmlStyle->has_labelstyle())
    kmlStyle->get_labelstyle();
    if (kmlStyle->has_liststyle())
    kmlStyle->get_liststyle();
    if (kmlStyle->has_balloonstyle())
    kmlStyle->get_balloonstyle();*/

    if (styleVector.size() == 1) return styleVector[0];

    if (!styleVector.empty())
        compositeStyle = new atakmap::feature::CompositeStyle(styleVector);

    return compositeStyle;
}

atakmap::feature::Style *convertStyleMap(const KMLStyleMap &styleMap,
                                         const std::string &file,
                                         const Container *container) {
    // find normal
    for (size_t i = 0; i < styleMap.Pair.size(); ++i) {
        if (styleMap.Pair[i]->key == KMLStyleState_normal) {
            const KMLStyleMapPair *pair = styleMap.Pair[i].get();
            if (pair->Style) {
                return convertStyle(*pair->Style, file);
            } else if (pair->styleUrl.is_specified() && container) {
                return convertStyleUrl(pair->styleUrl.value.c_str(), container);
            }

            break;
        }
    }

    return nullptr;
}

uint32_t abgrToargb(uint32_t color) {
	return (color & 0xff000000) | ((color & 0x00ff0000) >> 16) |
		(color & 0x0000ff00) | ((color & 0x000000ff) << 16);
}

atakmap::feature::Style *convertIconStyle(const KMLIconStyle &kmlIconStyle,
                                          const std::string &file) {
    atakmap::feature::IconPointStyle *iconStyle = nullptr;
    // icon style must have either a color or and icon uri to be valid
    const bool hasIcon = kmlIconStyle.Icon.get() != nullptr;

    if (kmlIconStyle.color.is_specified() || hasIcon) {
        unsigned int color = 0;  // 0xAARRGGBB
        double scale = 1;
        double rotation = 0;
        std::string iconURI = "http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png";

        color = (kmlIconStyle.color.is_specified() ? abgrToargb(kmlIconStyle.color)
                                                   : DefaultSymbolColor);
        if (kmlIconStyle.scale.is_specified()) scale = kmlIconStyle.scale;

        if (kmlIconStyle.heading.is_specified())
            rotation = kmlIconStyle.heading;

        if (hasIcon) {
            iconURI = kmlIconStyle.Icon->href;
            if (!iconURI.empty()) {
                // test if this is an Url address
                const bool isAbsolute =
                    (iconURI.find("://") !=
                     std::string::npos) ||  // absolute URI
                    (iconURI[0] == '/') ||  // absolute path on UNIX
                    std::regex_match(
                        iconURI,
                        std::regex("^[a-zA-Z]\\:")) ||  // windows drive
                    std::regex_match(
                        iconURI, std::regex("^\\\\"));  // windows network drive
                if (!isAbsolute) {
                    // TODO: test if file exists in archive for KMZ
                    std::ostringstream strm;
                    if (hasExt(file.c_str(), ".kmz")) {
                        strm << "zip://" << file << "!/";
                    } else {
                        TAK::Engine::Port::String parentDir;
                        if (IO_getParentFile(parentDir, file.c_str()) == TE_Ok)
                            strm << "file://" << parentDir << '/';
                    }
                    strm << iconURI;

                    iconURI = strm.str();
                }
            }
        }

        iconStyle = new atakmap::feature::IconPointStyle(
            color, iconURI.c_str(), static_cast<float>(scale),
            atakmap::feature::IconPointStyle::H_CENTER,
            atakmap::feature::IconPointStyle::V_CENTER,
            static_cast<float>(rotation), true);
    }

    return iconStyle;
}

atakmap::feature::Style *convertLineStyle(const KMLLineStyle &kmlLineStyle) {
    atakmap::feature::BasicStrokeStyle *lineStyle = nullptr;

    uint32_t color = 0;
    double width = 1;

    color = DefaultLineColor;
    if (kmlLineStyle.color.is_specified()) {
        color = abgrToargb(kmlLineStyle.color);
    }

    if (kmlLineStyle.width.is_specified()) width = kmlLineStyle.width;

    lineStyle = new atakmap::feature::BasicStrokeStyle(
        color, static_cast<float>(width));

    return lineStyle;
}

atakmap::feature::Style *convertFillStyle(const KMLPolyStyle &kmlPolyStyle) {
    atakmap::feature::BasicFillStyle *fillStyle = nullptr;
    if (kmlPolyStyle.color.is_specified()) {
        fillStyle = new atakmap::feature::BasicFillStyle(
            abgrToargb(kmlPolyStyle.color));
    }
    return fillStyle;
}

atakmap::feature::Geometry *convertGeometry(const KMLGeometry &kmlGeometry) {
    switch (kmlGeometry.get_entity()) {
        case KMLEntity_Point:
            return convertPoint(static_cast<const KMLPoint &>(kmlGeometry));
        case KMLEntity_MultiGeometry:
            return convertMultiGeometry(
                static_cast<const KMLMultiGeometry &>(kmlGeometry));
        case KMLEntity_Polygon:
            return convertPolygon(static_cast<const KMLPolygon &>(kmlGeometry));
        case KMLEntity_LinearRing:
            return convertLinearRing(
                static_cast<const KMLLinearRing &>(kmlGeometry));
        case KMLEntity_LineString:
            return convertLineString(
                static_cast<const KMLLineString &>(kmlGeometry));
        case KMLEntity_Model:
            return convertModel(static_cast<const KMLModel &>(kmlGeometry));
        case KMLEntity_gxTrack:
            return convertGxTrack(static_cast<const KMLgxTrack &>(kmlGeometry));
        case KMLEntity_GxMultiTrack:
            break;
    }

    return nullptr;
}

atakmap::feature::Point *convertPoint(const KMLPoint &kmlPoint) {
    if (kmlPoint.coordinates.is_specified() &&
        kmlPoint.coordinates.value.values.size() > 0) {
        double x = kmlPoint.coordinates.value.values[0];
        double y = kmlPoint.coordinates.value.values[1];

#if KML_FULL_3D
        double z = kmlPoint.coordinates.value.values[2];
        return new atakmap::feature::Point(x, y, z);
#else
        return new atakmap::feature::Point(x, y);
#endif
    }

    return nullptr;
}

atakmap::feature::GeometryCollection *convertGxTrack(
    const KMLgxTrack &kmlTrack) {
    std::unique_ptr<atakmap::feature::GeometryCollection> geom(
        new atakmap::feature::GeometryCollection(
            atakmap::feature::Geometry::_3D));
    for (size_t i = 0; i < kmlTrack.coords.size(); ++i) {
        atakmap::feature::Point point(kmlTrack.coords[i].lng,
                                      kmlTrack.coords[i].lat,
                                      kmlTrack.coords[i].alt);

        geom->add(point);
    }

    return geom.release();
}

atakmap::feature::Polygon *convertPolygon(const KMLPolygon &kmlPolygon) {
    if (kmlPolygon.outerBoundaryIs) {
        std::unique_ptr<atakmap::feature::LineString> outer(
            convertLinearRing(*kmlPolygon.outerBoundaryIs));

#if KML_FULL_3D
        std::unique_ptr<atakmap::feature::Polygon> poly(
            new atakmap::feature::Polygon(atakmap::feature::Geometry::_3D));
#else
        std::unique_ptr<atakmap::feature::Polygon> poly(
            new atakmap::feature::Polygon(atakmap::feature::Geometry::_2D));
#endif
        poly->addRing(*outer);

        for (size_t i = 0; i < kmlPolygon.innerBoundaryIs.num_items(); ++i) {
            std::unique_ptr<atakmap::feature::LineString> inner(
                convertLinearRing(kmlPolygon.innerBoundaryIs[i]));
            poly->addRing(*inner);
        }

        return poly.release();
    }

    return nullptr;
}

atakmap::feature::Point *convertModel(const KMLModel &kmlModel) {
    if (kmlModel.Location.is_specified()) {
        double x = kmlModel.Location.value.longitude;
        double y = kmlModel.Location.value.latitude;

#if KML_FULL_3D
        double z = kmlModel.Location.value.altitude;
        return new atakmap::feature::Point(x, y, z);
#else
        return new atakmap::feature::Point(x, y);
#endif
    }

    return nullptr;
}

atakmap::feature::LineString *convertLinearRing(
    const KMLLinearRing &kmlLinearRing) {
    return convertCoordinates(kmlLinearRing.coordinates);
}

atakmap::feature::LineString *convertLineString(
    const KMLLineString &kmlLineString) {
    return convertCoordinates(kmlLineString.coordinates);
}

atakmap::feature::LineString *convertCoordinates(
    const KMLCoordinates &kmlCoordinates) {

    if (kmlCoordinates.values.size() == 0)
        return nullptr;

#if KML_FULL_3D
    std::unique_ptr<atakmap::feature::LineString> lineString(
        new atakmap::feature::LineString(atakmap::feature::Geometry::_3D));
    const std::vector<double> &values = kmlCoordinates.values;
    lineString->addPoints(values.data(), values.data() + values.size(),
                          atakmap::feature::Geometry::_3D);
#else
    std::unique_ptr<atakmap::feature::LineString> lineString(
        new atakmap::feature::LineString(atakmap::feature::Geometry::_2D));
    std::vector<double> values = kmlCoordinates.values_2d();
    lineString->addPoints(values.data(), values.data() + values.size(),
                          atakmap::feature::Geometry::_2D);
#endif
    return lineString.release();
}

atakmap::feature::GeometryCollection *convertMultiGeometry(
    const KMLMultiGeometry &kmlMultiGeom) {
    // flatten all inner geometry into one collection
#if KML_FULL_3D
    std::unique_ptr<atakmap::feature::GeometryCollection> collection(
        new atakmap::feature::GeometryCollection(
            atakmap::feature::Geometry::_3D));
#else
    std::unique_ptr<atakmap::feature::GeometryCollection> collection(
        new atakmap::feature::GeometryCollection(
            atakmap::feature::Geometry::_2D));
#endif
    convertMultiGeometryAddToGeometryCollection(*collection, kmlMultiGeom);
    if (collection.get() && collection->size() > 0)
        return collection.release();
    else {
        return nullptr;
    }
}

void convertMultiGeometryAddToGeometryCollection(
    atakmap::feature::GeometryCollection &collection,
    const KMLMultiGeometry &kmlMultiGeom) {
    for (size_t i = 0; i < kmlMultiGeom.Geometry.num_items(); ++i) {
        const KMLGeometry &kmlChildGeometry = kmlMultiGeom.Geometry[i];
        if (kmlChildGeometry.get_entity() == KMLEntity_MultiGeometry) {
            convertMultiGeometryAddToGeometryCollection(
                collection,
                static_cast<const KMLMultiGeometry &>(kmlChildGeometry));
        } else {
            collection.add(convertGeometry(kmlChildGeometry));
        }
    }
}

bool determineAltMode(const KMLGeometry &geom, KMLAltitudeMode &geomAltMode) {
    bool result = true;

    switch (geom.get_entity()) {
        case KMLEntity_Point:
            geomAltMode = static_cast<const KMLPoint &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLPoint &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        case KMLEntity_LineString:
            geomAltMode = static_cast<const KMLLineString &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLLineString &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        case KMLEntity_LinearRing:
            geomAltMode = static_cast<const KMLLinearRing &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLLinearRing &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        case KMLEntity_Polygon:
            geomAltMode = static_cast<const KMLPolygon &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLPolygon &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        case KMLEntity_MultiGeometry: {
            const auto &multiGeom =
                static_cast<const KMLMultiGeometry &>(geom);
            for (size_t i = 0; i < multiGeom.Geometry.num_items(); ++i) {
                if (determineAltMode(multiGeom.Geometry[i], geomAltMode))
                    return true;
            }
        } break;

        case KMLEntity_Model:
            geomAltMode = static_cast<const KMLModel &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLModel &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        case KMLEntity_gxTrack:
            geomAltMode = static_cast<const KMLgxTrack &>(geom).altitudeMode.exists
                              ? static_cast<KMLAltitudeMode>(static_cast<const KMLgxTrack &>(geom).altitudeMode)
                              : KMLAltitudeMode::KMLAltitudeMode_clampToGround;
            break;

        default:
            result = false;
            break;
    }

    return result;
}

void determineExtrude(const KMLGeometry &geom, double &extrude) {
    bool shouldExtrude = false;
    switch (geom.get_entity()) {
        case KMLEntity_Point:
            shouldExtrude = static_cast<const KMLPoint &>(geom).extrude.exists ? static_cast<bool>(static_cast<const KMLPoint &>(geom).extrude) : false;
            break;
    }
    // RWI - Currently only supporting extrude for points. -1 Means extrude from point's altitude down to terrain
    extrude = shouldExtrude ? -1 : 0.0;
}
}  // namespace